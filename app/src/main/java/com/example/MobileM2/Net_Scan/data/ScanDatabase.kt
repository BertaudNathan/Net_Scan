package com.example.MobileM2.Net_Scan.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Database(entities = [ScanEntity::class, PreferencesEntity::class], version = 2, exportSchema = false)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun preferencesDao(): PreferencesDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_preferences` (`id` INTEGER NOT NULL, `theme` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        @Volatile private var instance: ScanDatabase? = null

        fun getInstance(context: Context): ScanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    "net-scan.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
