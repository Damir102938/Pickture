package com.moviepicker.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Database(entities = [UserTitleEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userTitles(): UserTitleDao

    companion object {
        /** 0.6: дневник просмотров (просмотрено, оценка, дата) и последняя известная серия. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_titles ADD COLUMN watched INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_titles ADD COLUMN userRating INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_titles ADD COLUMN watchedAt TEXT")
                db.execSQL("ALTER TABLE user_titles ADD COLUMN lastKnownEpisode INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Отметки пользователя нельзя терять: при обновлении схемы — только миграции, без пересоздания базы
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "movie_picker.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
