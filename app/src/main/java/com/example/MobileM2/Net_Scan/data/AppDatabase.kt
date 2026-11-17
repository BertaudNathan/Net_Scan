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
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val building: String,
    val client: String,
    val address: String,
    val createdAt: Long,
)

@Dao
interface VisitDao {
    @Query("SELECT * FROM scans ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<VisitEntity>>

    @Insert
    suspend fun insert(visit: VisitEntity)
}

@Database(entities = [VisitEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun visitDao(): VisitDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "net-scan.db",
                ).build().also { instance = it }
            }
    }
}
