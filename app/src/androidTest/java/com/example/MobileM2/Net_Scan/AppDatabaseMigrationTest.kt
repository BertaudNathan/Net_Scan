package com.example.MobileM2.Net_Scan

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MobileM2.Net_Scan.data.AppDatabase
import com.example.MobileM2.Net_Scan.data.RoomNetScanRepository
import com.example.MobileM2.Net_Scan.data.RoomPreferencesRepository
import com.example.MobileM2.Net_Scan.data.RoomRecordingRepository
import com.example.MobileM2.Net_Scan.data.VisitEntity
import com.example.MobileM2.Net_Scan.domain.FloorPoint
import com.example.MobileM2.Net_Scan.domain.GridCell
import com.example.MobileM2.Net_Scan.domain.MapMeasurement
import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanSummary
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.domain.Handedness
import com.example.MobileM2.Net_Scan.domain.TrackPoint
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Les deux branches ont chacune produit une version 2 : la version 3 doit accepter les deux sans perte.
 * La version 4 ajoute les parcours ARCore et la position des scans réseau.
 */
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

    private fun createVersion2(statements: List<String>) = createVersion(2, statements)

    private fun createVersion(version: Int, statements: List<String>) {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { sqlite: SQLiteDatabase ->
            statements.forEach(sqlite::execSQL)
            sqlite.version = version
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
        val preferences = RoomPreferencesRepository(database.preferencesDao()).observePreferences().first()
        assertEquals(ThemeMode.DARK, preferences.theme)
        assertEquals(Handedness.RIGHT, preferences.handedness)

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
        val preferences = RoomPreferencesRepository(database.preferencesDao()).observePreferences().first()
        assertEquals(ThemeMode.SYSTEM, preferences.theme)
        assertEquals(Handedness.RIGHT, preferences.handedness)
    }

    @Test fun version3PreservesVisitsScansAndThemeAndDefaultsToRightHanded() = runBlocking {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE visits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, building TEXT NOT NULL, client TEXT NOT NULL, address TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            sqlite.execSQL("CREATE TABLE net_scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, visitId INTEGER NOT NULL, startedAt INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, timerSeconds INTEGER, operatorName TEXT, technology TEXT NOT NULL, level TEXT NOT NULL, rsrpMedianDbm REAL, sinrMedianDb REAL, sampleCount INTEGER NOT NULL, band INTEGER, cellId INTEGER, FOREIGN KEY(visitId) REFERENCES visits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            sqlite.execSQL("CREATE INDEX index_net_scans_visitId ON net_scans (visitId)")
            sqlite.execSQL("CREATE TABLE app_preferences (id INTEGER NOT NULL, theme TEXT NOT NULL, PRIMARY KEY(id))")
            sqlite.execSQL("INSERT INTO visits (id, building, client, address, createdAt) VALUES (9, 'Boutique', '', '', 42)")
            sqlite.execSQL("INSERT INTO net_scans (visitId, startedAt, durationSeconds, timerSeconds, operatorName, technology, level, rsrpMedianDbm, sinrMedianDb, sampleCount, band, cellId) VALUES (9, 1, 30, 30, 'Orange', 'LTE', 'MEDIUM', -95.0, 6.0, 30, 20, 1234)")
            sqlite.execSQL("INSERT INTO app_preferences (id, theme) VALUES (1, 'DARK')")
            sqlite.version = 3
        }
        val database = open()
        val repo = RoomPreferencesRepository(database.preferencesDao())
        assertEquals("Boutique", database.visitDao().observeAll().first().single().building)
        assertEquals("Orange", RoomNetScanRepository(database.netScanDao()).observeForVisit(9).first().single().operatorName)
        assertEquals(ThemeMode.DARK, repo.observePreferences().first().theme)
        assertEquals(Handedness.RIGHT, repo.observePreferences().first().handedness)
        repo.setHandedness(Handedness.LEFT)
        assertEquals(ThemeMode.DARK, repo.observePreferences().first().theme)
        repo.setTheme(ThemeMode.LIGHT)
        assertEquals(Handedness.LEFT, repo.observePreferences().first().handedness)
        database.close()
        val reopened = open()
        assertEquals("Orange", RoomNetScanRepository(reopened.netScanDao()).observeForVisit(9).first().single().operatorName)
        assertEquals(Handedness.LEFT, RoomPreferencesRepository(reopened.preferencesDao()).observePreferences().first().handedness)
    }

    @Test fun version3KeepsNetScansAndGainsRecordingsAndRoomMaps() = runBlocking {
        createVersion(
            3,
            listOf(
                "CREATE TABLE visits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, building TEXT NOT NULL, client TEXT NOT NULL, address TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                "CREATE TABLE net_scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, visitId INTEGER NOT NULL, startedAt INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, timerSeconds INTEGER, operatorName TEXT, technology TEXT NOT NULL, level TEXT NOT NULL, rsrpMedianDbm REAL, sinrMedianDb REAL, sampleCount INTEGER NOT NULL, band INTEGER, cellId INTEGER, FOREIGN KEY(visitId) REFERENCES visits(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                "CREATE INDEX index_net_scans_visitId ON net_scans (visitId)",
                "CREATE TABLE app_preferences (id INTEGER NOT NULL, theme TEXT NOT NULL, PRIMARY KEY(id))",
                "INSERT INTO visits (id, building, client, address, createdAt) VALUES (9, 'Atelier', '', '', 42)",
                "INSERT INTO net_scans (visitId, startedAt, durationSeconds, timerSeconds, operatorName, technology, level, rsrpMedianDbm, sinrMedianDb, sampleCount, band, cellId) VALUES (9, 1, 30, 30, 'Orange', 'LTE', 'LOW', -105.0, -2.0, 30, 20, 1234)",
            ),
        )
        val database = open()
        val old = database.netScanDao().observeForVisit(9).first().single()
        assertEquals("LOW", old.level)
        assertNull(old.recordingId)
        assertNull(old.mapX)

        val recordings = RoomRecordingRepository(database.recordingDao(), database.netScanDao())
        val recordingId = recordings.start(visitId = 9, startedAt = 50)
        recordings.addPoints(recordingId, listOf(TrackPoint(0, FloorPoint(0f, 0f)), TrackPoint(0, FloorPoint(1f, 0f))))
        RoomNetScanRepository(database.netScanDao()).save(
            netScan(9).copy(recordingId = recordingId, position = FloorPoint(1f, 0f)),
        )
        recordings.saveCells(recordingId, mapOf(GridCell(1, 2) to 4f, GridCell(0, 0) to -1f))
        recordings.saveCells(recordingId, mapOf(GridCell(0, 0) to -2f))
        val latest = recordings.observeLatest(9).first()!!
        assertEquals(mapOf(GridCell(1, 2) to 4f, GridCell(0, 0) to -2f), latest.cells)
        assertEquals(listOf(listOf(FloorPoint(0f, 0f), FloorPoint(1f, 0f))), latest.segments)
        assertEquals(listOf(MapMeasurement(FloorPoint(1f, 0f), SignalLevel.HIGH)), latest.measurements)
    }

    @Test fun mainVersion4KeepsLeftHandednessAndGainsTheMap() = runBlocking {
        // Version 4 de main : préférence gaucher/droitier, sans parcours ARCore.
        createVersion(
            4,
            V3_TABLES + listOf(
                "CREATE TABLE app_preferences (id INTEGER NOT NULL, theme TEXT NOT NULL, handedness TEXT NOT NULL DEFAULT 'RIGHT', PRIMARY KEY(id))",
                "INSERT INTO visits (id, building, client, address, createdAt) VALUES (4, 'Dépôt', '', '', 42)",
                "INSERT INTO app_preferences (id, theme, handedness) VALUES (1, 'LIGHT', 'LEFT')",
            ),
        )
        val database = open()
        val preferences = RoomPreferencesRepository(database.preferencesDao()).observePreferences().first()
        assertEquals(Handedness.LEFT, preferences.handedness)
        assertEquals(ThemeMode.LIGHT, preferences.theme)
        val recordings = RoomRecordingRepository(database.recordingDao(), database.netScanDao())
        val walk = recordings.start(visitId = 4, startedAt = 5)
        recordings.addPoints(walk, listOf(TrackPoint(0, FloorPoint(0f, 0f))))
        recordings.saveCells(walk, mapOf(GridCell(0, -2) to 4f))
        assertEquals(mapOf(GridCell(0, -2) to 4f), recordings.observeLatest(4).first()!!.cells)
    }

    @Test fun arcoreVersion5KeepsTheWalkAndGainsHandedness() = runBlocking {
        // Version 5 de la branche ARCore : parcours, carte d'occupation, scans placés ; pas de préférence gaucher.
        createVersion(
            5,
            listOf(
                "CREATE TABLE visits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, building TEXT NOT NULL, client TEXT NOT NULL, address TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                "CREATE TABLE recordings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, visitId INTEGER NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, FOREIGN KEY(visitId) REFERENCES visits(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                "CREATE INDEX index_recordings_visitId ON recordings (visitId)",
                "CREATE TABLE recording_points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, recordingId INTEGER NOT NULL, segment INTEGER NOT NULL, x REAL NOT NULL, z REAL NOT NULL, FOREIGN KEY(recordingId) REFERENCES recordings(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                "CREATE INDEX index_recording_points_recordingId ON recording_points (recordingId)",
                "CREATE TABLE recording_cells (recordingId INTEGER NOT NULL, cellCol INTEGER NOT NULL, cellRow INTEGER NOT NULL, logOdds REAL NOT NULL, PRIMARY KEY(recordingId, cellCol, cellRow), FOREIGN KEY(recordingId) REFERENCES recordings(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                "CREATE TABLE net_scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, visitId INTEGER NOT NULL, startedAt INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, timerSeconds INTEGER, operatorName TEXT, technology TEXT NOT NULL, level TEXT NOT NULL, rsrpMedianDbm REAL, sinrMedianDb REAL, sampleCount INTEGER NOT NULL, band INTEGER, cellId INTEGER, recordingId INTEGER REFERENCES recordings(id) ON UPDATE NO ACTION ON DELETE SET NULL, mapX REAL, mapZ REAL, FOREIGN KEY(visitId) REFERENCES visits(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                "CREATE INDEX index_net_scans_visitId ON net_scans (visitId)",
                "CREATE INDEX index_net_scans_recordingId ON net_scans (recordingId)",
                "CREATE TABLE app_preferences (id INTEGER NOT NULL, theme TEXT NOT NULL, PRIMARY KEY(id))",
                "INSERT INTO visits (id, building, client, address, createdAt) VALUES (5, 'Couloir', '', '', 42)",
                "INSERT INTO recordings (id, visitId, startedAt, endedAt) VALUES (1, 5, 10, 20)",
                "INSERT INTO recording_points (recordingId, segment, x, z) VALUES (1, 0, 0.0, 0.0), (1, 0, 0.0, -2.0)",
                "INSERT INTO recording_cells (recordingId, cellCol, cellRow, logOdds) VALUES (1, -1, -3, 4.0)",
                "INSERT INTO net_scans (visitId, startedAt, durationSeconds, timerSeconds, operatorName, technology, level, rsrpMedianDbm, sinrMedianDb, sampleCount, band, cellId, recordingId, mapX, mapZ) VALUES (5, 15, 30, 30, 'Orange', 'LTE', 'LOW', -105.0, -1.0, 30, 20, 1, 1, 0.0, -2.0)",
                "INSERT INTO app_preferences (id, theme) VALUES (1, 'DARK')",
            ),
        )
        val database = open()
        val preferences = RoomPreferencesRepository(database.preferencesDao()).observePreferences().first()
        assertEquals(Handedness.RIGHT, preferences.handedness)
        assertEquals(ThemeMode.DARK, preferences.theme)
        val walk = RoomRecordingRepository(database.recordingDao(), database.netScanDao()).observeLatest(5).first()!!
        assertEquals(listOf(listOf(FloorPoint(0f, 0f), FloorPoint(0f, -2f))), walk.segments)
        assertEquals(mapOf(GridCell(-1, -3) to 4f), walk.cells)
        assertEquals(SignalLevel.LOW, walk.measurements.single().level)
        // L'historique de main retrouve la mesure, avec sa place sur la carte.
        assertEquals(FloorPoint(0f, -2f), RoomNetScanRepository(database.netScanDao()).observeForVisit(5).first().single().position)
    }

    @Test fun anEmptyRecordingDoesNotHideThePreviousWalk() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().also { db = it }
        val visitId = database.visitDao().insert(VisitEntity(building = "Hall", client = "", address = "", createdAt = 1))
        val recordings = RoomRecordingRepository(database.recordingDao(), database.netScanDao())
        val walked = recordings.start(visitId, startedAt = 10)
        recordings.addPoints(walked, listOf(TrackPoint(0, FloorPoint(2f, 3f))))
        recordings.start(visitId, startedAt = 20)
        assertEquals(walked, recordings.observeLatest(visitId).first()!!.id)
    }

    private companion object {
        /** Tables communes de la version 3 : visites et scans réseau. */
        val V3_TABLES = listOf(
            "CREATE TABLE visits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, building TEXT NOT NULL, client TEXT NOT NULL, address TEXT NOT NULL, createdAt INTEGER NOT NULL)",
            "CREATE TABLE net_scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, visitId INTEGER NOT NULL, startedAt INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, timerSeconds INTEGER, operatorName TEXT, technology TEXT NOT NULL, level TEXT NOT NULL, rsrpMedianDbm REAL, sinrMedianDb REAL, sampleCount INTEGER NOT NULL, band INTEGER, cellId INTEGER, FOREIGN KEY(visitId) REFERENCES visits(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX index_net_scans_visitId ON net_scans (visitId)",
        )
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
