package com.moviepicker.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Room-реализация [UserTitleStore]. */
@Dao
interface UserTitleDao : UserTitleStore {
    @Query("SELECT * FROM user_titles WHERE type = :type AND id = :id")
    override suspend fun get(type: String, id: Int): UserTitleEntity?

    @Query("SELECT * FROM user_titles WHERE type = :type AND id = :id")
    override fun observe(type: String, id: Int): Flow<UserTitleEntity?>

    @Query("SELECT * FROM user_titles ORDER BY updatedAt DESC")
    override fun observeAll(): Flow<List<UserTitleEntity>>

    @Query("SELECT * FROM user_titles ORDER BY updatedAt DESC")
    override suspend fun getAll(): List<UserTitleEntity>

    @Upsert
    override suspend fun upsert(entity: UserTitleEntity)

    @Query("DELETE FROM user_titles WHERE type = :type AND id = :id")
    override suspend fun delete(type: String, id: Int)

    @Query("DELETE FROM user_titles")
    override suspend fun clear()
}

@Database(entities = [UserTitleEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userTitles(): UserTitleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "movie_picker.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
