package com.example.MobileM2.Net_Scan.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val building: String,
    val client: String,
    val address: String,
    val createdAt: Long,
)

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ScanEntity>>

    @Insert
    suspend fun insert(scan: ScanEntity)
}

@Database(entities = [ScanEntity::class], version = 1, exportSchema = false)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile private var instance: ScanDatabase? = null

        fun getInstance(context: Context): ScanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    "net-scan.db",
                ).build().also { instance = it }
            }
    }
}
