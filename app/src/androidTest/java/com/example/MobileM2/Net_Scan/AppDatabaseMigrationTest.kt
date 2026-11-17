package com.example.MobileM2.Net_Scan

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MobileM2.Net_Scan.data.AppDatabase
import com.example.MobileM2.Net_Scan.data.RoomNetScanRepository
import com.example.MobileM2.Net_Scan.data.RoomThemeRepository
import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanSummary
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import com.example.MobileM2.Net_Scan.domain.ThemeMode
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Les deux branches ont chacune produit une version 2 : la version 3 doit accepter les deux sans perte. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private var db: AppDatabase? = null

    @Before fun setup() { databaseName = "migration-test-${UUID.randomUUID()}.db" }

    @After fun tearDown() {
        db?.close()
        context.deleteDatabase(databaseName)
    }

    private fun createVersion2(statements: List<String>) {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { sqlite: SQLiteDatabase ->
            statements.forEach(sqlite::execSQL)
            sqlite.version = 2
        }
    }

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .addMigrations(*AppDatabase.MIGRATIONS)
        .build()
        .also { db = it }

    @Test fun preferencesVersion2KeepsVisitsAndTheme() = runBlocking {
        createVersion2(
            listOf(
                "CREATE TABLE scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, building TEXT NOT NULL, client TEXT NOT NULL, address TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                "CREATE TABLE app_preferences (id INTEGER NOT NULL, theme TEXT NOT NULL, PRIMARY KEY(id))",
                "INSERT INTO scans (building, client, address, createdAt) VALUES ('Magasin', 'Client', 'Adresse', 42)",
                "INSERT INTO app_preferences (id, theme) VALUES (1, 'DARK')",
            ),
        )
        val database = open()
        val visit = database.visitDao().observeAll().first().single()
        assertEquals("Magasin", visit.building)
        assertEquals(ThemeMode.DARK, RoomThemeRepository(database.preferencesDao()).observeTheme().first())

        RoomNetScanRepository(database.netScanDao()).save(netScan(visit.id))
        assertEquals(1, database.netScanDao().observeForVisit(visit.id).first().size)
    }

    @Test fun netScanVersion2KeepsVisitsAndNetScans() = runBlocking {
        createVersion2(
            listOf(
                "CREATE TABLE visits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, building TEXT NOT NULL, client TEXT NOT NULL, address TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                "CREATE TABLE net_scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, visitId INTEGER NOT NULL, startedAt INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, timerSeconds INTEGER, operatorName TEXT, technology TEXT NOT NULL, level TEXT NOT NULL, rsrpMedianDbm REAL, sinrMedianDb REAL, sampleCount INTEGER NOT NULL, band INTEGER, cellId INTEGER, FOREIGN KEY(visitId) REFERENCES visits(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                "CREATE INDEX index_net_scans_visitId ON net_scans (visitId)",
                "INSERT INTO visits (id, building, client, address, createdAt) VALUES (5, 'Entrepôt', '', '', 42)",
                "INSERT INTO net_scans (visitId, startedAt, durationSeconds, timerSeconds, operatorName, technology, level, rsrpMedianDbm, sinrMedianDb, sampleCount, band, cellId) VALUES (5, 1, 30, 30, 'Orange', 'LTE', 'MEDIUM', -95.0, 6.0, 30, 20, 1234)",
            ),
        )
        val database = open()
        assertEquals("Entrepôt", database.visitDao().observeAll().first().single().building)
        assertEquals("MEDIUM", database.netScanDao().observeForVisit(5).first().single().level)
        assertEquals(ThemeMode.SYSTEM, RoomThemeRepository(database.preferencesDao()).observeTheme().first())
    }

    private fun netScan(visitId: Long) = NetScan(
        visitId = visitId,
        startedAt = 1,
        durationSeconds = 20,
        mode = NetScanMode.Timer(20),
        operatorName = "Orange",
        summary = NetScanSummary(SignalLevel.HIGH, NetworkTechnology.LTE, -75.0, 15.0, 20, 20, 1),
    )
}
