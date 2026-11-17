package com.example.MobileM2.Net_Scan.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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

@Database(entities = [VisitEntity::class, NetScanEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun visitDao(): VisitDao
    abstract fun netScanDao(): NetScanDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "net-scan.db",
                )
                    // Prototype : les visites existantes peuvent être effacées lors d'un changement de schéma.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
