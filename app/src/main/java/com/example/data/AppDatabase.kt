package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "queue_items")
data class QueueItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String,
    val clientName: String,
    var status: String, // "WAITING", "PROCESSING", "SENT", "ERROR"
    var transcription: String = "",
    val mode: String, // "LOCAL", "SERVER"
    var errorMessage: String? = null,
    val audioDurationMs: Long = 0,
    val isDiarized: Boolean = false
)

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY timestamp DESC")
    fun getAllItems(): Flow<List<QueueItem>>

    @Query("SELECT * FROM queue_items WHERE id = :id")
    suspend fun getItemById(id: Int): QueueItem?

    @Query("SELECT * FROM queue_items WHERE status = 'WAITING' OR status = 'ERROR' ORDER BY timestamp ASC")
    suspend fun getPendingItems(): List<QueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: QueueItem): Long

    @Update
    suspend fun updateItem(item: QueueItem)

    @Delete
    suspend fun deleteItem(item: QueueItem)

    @Query("DELETE FROM queue_items WHERE id = :id")
    suspend fun deleteItemById(id: Int)
}

@Database(entities = [QueueItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "transcriber_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
