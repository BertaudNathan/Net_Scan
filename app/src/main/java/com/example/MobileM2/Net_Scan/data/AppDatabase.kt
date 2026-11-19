package com.example.MobileM2.Net_Scan.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
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

/** Enregistrement d'un parcours : seulement des positions, aucune image. */
@Entity(
    tableName = "recordings",
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
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val startedAt: Long,
    /** Null si l'écran a été quitté sans arrêter l'enregistrement. */
    val endedAt: Long?,
)

/** Point de trajectoire, en mètres dans le repère ARCore de son enregistrement. */
@Entity(
    tableName = "recording_points",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordingId")],
)
data class RecordingPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val segment: Int,
    val x: Float,
    val z: Float,
)

/** Case de la carte d'occupation d'un enregistrement, avec son score (log-odds). */
@Entity(
    tableName = "recording_cells",
    primaryKeys = ["recordingId", "cellCol", "cellRow"],
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecordingCellEntity(
    val recordingId: Long,
    val cellCol: Int,
    val cellRow: Int,
    val logOdds: Float,
)

@Dao
interface RecordingDao {
    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Insert
    suspend fun insertPoints(points: List<RecordingPointEntity>)

    @Query("UPDATE recordings SET endedAt = :endedAt WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long)

    @Query(
        "SELECT * FROM recordings WHERE visitId = :visitId AND EXISTS " +
            "(SELECT 1 FROM recording_points WHERE recording_points.recordingId = recordings.id) " +
            "ORDER BY startedAt DESC, id DESC LIMIT 1",
    )
    fun observeLatestWithPoints(visitId: Long): Flow<RecordingEntity?>

    @Query("SELECT * FROM recording_points WHERE recordingId = :recordingId ORDER BY id")
    fun observePoints(recordingId: Long): Flow<List<RecordingPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCells(cells: List<RecordingCellEntity>)

    @Query("SELECT * FROM recording_cells WHERE recordingId = :recordingId")
    fun observeCells(recordingId: Long): Flow<List<RecordingCellEntity>>
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
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("visitId"), Index("recordingId")],
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
    val recordingId: Long? = null,
    /** Position sur la carte de l'enregistrement, absente si le suivi n'en a donné aucune. */
    val mapX: Float? = null,
    val mapZ: Float? = null,
)

@Dao
interface NetScanDao {
    @Insert
    suspend fun insert(scan: NetScanEntity): Long

    @Query("SELECT * FROM net_scans WHERE visitId = :visitId ORDER BY startedAt DESC, id DESC")
    fun observeForVisit(visitId: Long): Flow<List<NetScanEntity>>

    @Query(
        "SELECT * FROM net_scans WHERE recordingId = :recordingId AND mapX IS NOT NULL AND mapZ IS NOT NULL " +
            "ORDER BY startedAt, id",
    )
    fun observePlaced(recordingId: Long): Flow<List<NetScanEntity>>
}

@Entity(tableName = "app_preferences")
data class PreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val theme: String = "SYSTEM",
    @ColumnInfo(defaultValue = "RIGHT") val handedness: String = "RIGHT",
)

@Dao
interface PreferencesDao {
    @Query("SELECT * FROM app_preferences WHERE id = 1")
    fun observePreferences(): Flow<PreferencesEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(preferences: PreferencesEntity)

    @Query("UPDATE app_preferences SET theme = :theme WHERE id = 1")
    suspend fun updateTheme(theme: String)

    @Query("UPDATE app_preferences SET handedness = :handedness WHERE id = 1")
    suspend fun updateHandedness(handedness: String)

    @Transaction
    suspend fun saveTheme(theme: String) {
        insertIfMissing(PreferencesEntity())
        updateTheme(theme)
    }

    @Transaction
    suspend fun saveHandedness(handedness: String) {
        insertIfMissing(PreferencesEntity())
        updateHandedness(handedness)
    }
}

/**
 * Version 1 : visites (table `scans`). Version 2 : deux branches ont chacune produit une version 2
 * différente (préférences d'un côté, visites renommées et scans réseau de l'autre). La version 3
 * réunit les deux.
 *
 * Ensuite, deux branches ont chacune produit une version 4 différente : la préférence gaucher/droitier
 * d'un côté ; les parcours ARCore et la position des scans sur leur carte de l'autre, puis, en
 * version 5, la carte d'occupation (sol vu, murs). Une version 6 existe aussi sur les appareils qui
 * ont essayé le mode manuel. La version 7 réunit tout : chaque migration à partir de la version 3
 * vérifie ce qui existe déjà et ajoute seulement ce qui manque, quelle que soit la branche d'origine.
 */
@Database(
    entities = [
        VisitEntity::class,
        NetScanEntity::class,
        PreferencesEntity::class,
        RecordingEntity::class,
        RecordingPointEntity::class,
        RecordingCellEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun visitDao(): VisitDao
    abstract fun netScanDao(): NetScanDao
    abstract fun preferencesDao(): PreferencesDao
    abstract fun recordingDao(): RecordingDao

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

        val MIGRATION_3_4 = completeSchema(3, 4)
        val MIGRATION_4_5 = completeSchema(4, 5)
        val MIGRATION_5_6 = completeSchema(5, 6)
        val MIGRATION_6_7 = completeSchema(6, 7)

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        /**
         * Une version 4, 5 ou 6 peut venir de l'une ou l'autre branche : on ajoute ce qui manque,
         * sans jamais rien recréer ni effacer.
         */
        private fun completeSchema(from: Int, to: Int) = object : Migration(from, to) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("app_preferences", "handedness")) {
                    db.execSQL("ALTER TABLE `app_preferences` ADD COLUMN `handedness` TEXT NOT NULL DEFAULT 'RIGHT'")
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`visitId` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, " +
                        "FOREIGN KEY(`visitId`) REFERENCES `visits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_visitId` ON `recordings` (`visitId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`recordingId` INTEGER NOT NULL, `segment` INTEGER NOT NULL, `x` REAL NOT NULL, `z` REAL NOT NULL, " +
                        "FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recording_points_recordingId` ON `recording_points` (`recordingId`)",
                )
                if (!db.hasColumn("net_scans", "recordingId")) {
                    // SQLite accepte une clé étrangère ajoutée par ALTER TABLE tant que sa valeur par défaut est NULL.
                    db.execSQL(
                        "ALTER TABLE `net_scans` ADD COLUMN `recordingId` INTEGER " +
                            "REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
                    )
                }
                if (!db.hasColumn("net_scans", "mapX")) db.execSQL("ALTER TABLE `net_scans` ADD COLUMN `mapX` REAL")
                if (!db.hasColumn("net_scans", "mapZ")) db.execSQL("ALTER TABLE `net_scans` ADD COLUMN `mapZ` REAL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_net_scans_recordingId` ON `net_scans` (`recordingId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_cells` (`recordingId` INTEGER NOT NULL, " +
                        "`cellCol` INTEGER NOT NULL, `cellRow` INTEGER NOT NULL, `logOdds` REAL NOT NULL, " +
                        "PRIMARY KEY(`recordingId`, `cellCol`, `cellRow`), FOREIGN KEY(`recordingId`) " +
                        "REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
            }
        }

        private const val CREATE_PREFERENCES =
            "CREATE TABLE IF NOT EXISTS `app_preferences` (`id` INTEGER NOT NULL, `theme` TEXT NOT NULL, PRIMARY KEY(`id`))"
        /** Table des scans réseau telle qu'en version 3 ; la version 4 y ajoute des colonnes. */
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

        private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
            query("SELECT 1 FROM pragma_table_info(?) WHERE name = ?", arrayOf(table, column)).use { it.moveToFirst() }

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
