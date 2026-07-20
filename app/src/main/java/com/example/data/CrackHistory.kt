package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "crack_history")
data class CrackHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val configName: String,
    val inputType: String,
    val minLength: Int,
    val maxLength: Int,
    val testedCount: Long,
    val success: Boolean,
    val foundPassword: String?,
    val durationSec: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface CrackHistoryDao {
    @Query("SELECT * FROM crack_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<CrackHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: CrackHistoryItem)

    @Query("DELETE FROM crack_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)

    @Query("DELETE FROM crack_history")
    suspend fun clearHistory()
}

@Database(entities = [CrackHistoryItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun crackHistoryDao(): CrackHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "auto_input_tester_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class CrackHistoryRepository(private val crackHistoryDao: CrackHistoryDao) {
    val allHistory: Flow<List<CrackHistoryItem>> = crackHistoryDao.getAllHistory()

    suspend fun insert(item: CrackHistoryItem) = crackHistoryDao.insertHistory(item)

    suspend fun delete(id: Int) = crackHistoryDao.deleteHistoryById(id)

    suspend fun clearAll() = crackHistoryDao.clearHistory()
}
