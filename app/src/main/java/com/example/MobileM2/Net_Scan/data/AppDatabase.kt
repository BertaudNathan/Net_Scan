package com.example.MobileM2.Net_Scan.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "visits")
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val building: String,
    val client: String,
    val address: String,
    val createdAt: Long,
)

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE id = :id")
    fun observeById(id: Long): Flow<VisitEntity?>

    @Insert
    suspend fun insert(visit: VisitEntity): Long
}

/** Scan réseau terminé. Les énumérations sont stockées par leur nom. */
@Entity(
    tableName = "net_scans",
    foreignKeys = [
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("visitId")],
)
data class NetScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val startedAt: Long,
    val durationSeconds: Int,
    /** Durée du minuteur, ou null pour un chrono libre. */
    val timerSeconds: Int?,
    val operatorName: String?,
    val technology: String,
    val level: String,
    val rsrpMedianDbm: Double?,
    val sinrMedianDb: Double?,
    val sampleCount: Int,
    val band: Int?,
    val cellId: Long?,
)

@Dao
interface NetScanDao {
    @Insert
    suspend fun insert(scan: NetScanEntity): Long

    @Query("SELECT * FROM net_scans WHERE visitId = :visitId ORDER BY startedAt DESC, id DESC")
    fun observeForVisit(visitId: Long): Flow<List<NetScanEntity>>
}

@Entity(tableName = "app_preferences")
data class PreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val theme: String,
)

@Dao
interface PreferencesDao {
    @Query("SELECT * FROM app_preferences WHERE id = 1")
    fun observePreferences(): Flow<PreferencesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(preferences: PreferencesEntity)
}

/**
 * Version 1 : visites (table `scans`). Version 2 : deux branches ont chacune produit une version 2
 * différente (préférences d'un côté, visites renommées et scans réseau de l'autre). La version 3
 * réunit les deux ; sa migration accepte l'une ou l'autre des versions 2.
 */
@Database(
    entities = [VisitEntity::class, NetScanEntity::class, PreferencesEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun visitDao(): VisitDao
    abstract fun netScanDao(): NetScanDao
    abstract fun preferencesDao(): PreferencesDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_PREFERENCES)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (db.hasTable("scans")) db.execSQL("ALTER TABLE `scans` RENAME TO `visits`")
                db.execSQL(CREATE_NET_SCANS)
                db.execSQL(CREATE_NET_SCANS_VISIT_INDEX)
                db.execSQL(CREATE_PREFERENCES)
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        private const val CREATE_PREFERENCES =
            "CREATE TABLE IF NOT EXISTS `app_preferences` (`id` INTEGER NOT NULL, `theme` TEXT NOT NULL, PRIMARY KEY(`id`))"
        private const val CREATE_NET_SCANS =
            "CREATE TABLE IF NOT EXISTS `net_scans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`visitId` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, " +
                "`timerSeconds` INTEGER, `operatorName` TEXT, `technology` TEXT NOT NULL, `level` TEXT NOT NULL, " +
                "`rsrpMedianDbm` REAL, `sinrMedianDb` REAL, `sampleCount` INTEGER NOT NULL, `band` INTEGER, " +
                "`cellId` INTEGER, FOREIGN KEY(`visitId`) REFERENCES `visits`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        private const val CREATE_NET_SCANS_VISIT_INDEX =
            "CREATE INDEX IF NOT EXISTS `index_net_scans_visitId` ON `net_scans` (`visitId`)"

        private fun SupportSQLiteDatabase.hasTable(name: String): Boolean =
            query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "net-scan.db",
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
