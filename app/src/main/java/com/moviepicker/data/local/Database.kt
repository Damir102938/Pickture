package com.moviepicker.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.Reaction
import com.moviepicker.domain.Title
import com.moviepicker.domain.parseDate
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Всё, что пользователь отметил: лайк/дизлайк, «хочу посмотреть».
 * Хранится снимок карточки (чтобы списки работали без сети) и признаки для рекомендаций.
 * Сериализуется целиком в файл резервной копии.
 */
@Serializable
@Entity(tableName = "user_titles", primaryKeys = ["type", "id"])
data class UserTitleEntity(
    val id: Int,
    val type: String,
    val name: String,
    val posterPath: String?,
    val backdropPath: String?,
    val date: String?,
    val rating: Double,
    val voteCount: Int,
    val genreIds: String,
    val reaction: Int = 0,
    val inWatchlist: Boolean = false,
    /** Отмечено на стартовом экране (можно отключить в настройках). */
    val fromOnboarding: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    // Признаки для рекомендаций, подгружаются из деталей
    val featuresLoaded: Boolean = false,
    val keywordIds: String = "",
    val castIds: String = "",
    val directorIds: String = "",
    // Состояние уведомлений: 0 — не было, 1 — сообщили о премьере, 2 — о цифровом релизе
    val releaseNotifyStage: Int = 0,
    /** Последний вышедший сезон сериала, о котором пользователь уже знает. */
    val lastKnownSeason: Int = 0,
) {
    val mediaType: MediaType get() = MediaType.fromPath(type) ?: MediaType.MOVIE
    val reactionValue: Reaction get() = Reaction.of(reaction)

    fun toTitle() = Title(
        id = id,
        type = mediaType,
        name = name,
        originalName = null,
        overview = "",
        posterPath = posterPath,
        backdropPath = backdropPath,
        date = parseDate(date),
        rating = rating,
        voteCount = voteCount,
        popularity = 0.0,
        genreIds = genreIds.toIntList(),
    )

    companion object {
        fun from(title: Title) = UserTitleEntity(
            id = title.id,
            type = title.type.path,
            name = title.name,
            posterPath = title.posterPath,
            backdropPath = title.backdropPath,
            date = title.date?.toString(),
            rating = title.rating,
            voteCount = title.voteCount,
            genreIds = title.genreIds.joinToString(","),
        )
    }
}

fun String.toIntList(): List<Int> = split(',').mapNotNull { it.trim().toIntOrNull() }

@Dao
interface UserTitleDao {
    @Query("SELECT * FROM user_titles WHERE type = :type AND id = :id")
    suspend fun get(type: String, id: Int): UserTitleEntity?

    @Query("SELECT * FROM user_titles WHERE type = :type AND id = :id")
    fun observe(type: String, id: Int): Flow<UserTitleEntity?>

    @Query("SELECT * FROM user_titles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<UserTitleEntity>>

    @Query("SELECT * FROM user_titles ORDER BY updatedAt DESC")
    suspend fun getAll(): List<UserTitleEntity>

    @Upsert
    suspend fun upsert(entity: UserTitleEntity)

    @Query("DELETE FROM user_titles WHERE type = :type AND id = :id")
    suspend fun delete(type: String, id: Int)

    /** Убирает стартовые лайки, которые пользователь потом не подтверждал вручную. */
    @Query("DELETE FROM user_titles WHERE fromOnboarding = 1 AND inWatchlist = 0")
    suspend fun deleteOnboardingLikes()

    @Query("DELETE FROM user_titles")
    suspend fun clear()
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
