package com.moviepicker.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moviepicker.AppContainer
import com.moviepicker.container
import com.moviepicker.data.FeedSection
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.TitleKey
import com.moviepicker.domain.tasteWeight
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка дважды в сутки:
 *  - вышел ли фильм онлайн, новый сезон или новая серия из «Хочу посмотреть» (и просмотренных сериалов);
 *  - появились ли свежие релизы, хорошо совпадающие со вкусом.
 */
class ReleaseCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val settings = container.settings.current()
        if (!settings.notificationsEnabled || container.hasApiKey.value != true) return Result.success()
        if (!Notifications.canPost(applicationContext)) return Result.success()

        return try {
            checkWatchlist(container)
            checkNewForYou(container)
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: HttpException) {
            Result.retry()
        }
    }

    private suspend fun checkWatchlist(container: AppContainer) {
        val repository = container.repository
        val notifyEpisodes = container.settings.current().notifyEpisodes
        val today = LocalDate.now()
        // Фильмы и сериалы из «Хочу посмотреть», а также сериалы, которые понравились
        val tracked = repository.allMarked().filter {
            it.inWatchlist || (it.mediaType == MediaType.TV && it.tasteWeight() > 0)
        }
        for (entity in tracked) {
            if (!entity.featuresLoaded) {
                // Сначала запоминаем текущее состояние, иначе уведомим о том, что уже вышло
                repository.loadFeatures(entity.type, entity.id)
                continue
            }
            val key = TitleKey(entity.mediaType, entity.id)
            val details = repository.details(key)
            when (entity.mediaType) {
                MediaType.MOVIE -> {
                    // Цифровой релиз — тип 4 в release_dates TMDB, прокат — типы 2 и 3
                    val digitalOut = details.digitalDate?.let { !it.isAfter(today) } == true
                    val theatricalOut = details.theatricalDate?.let { !it.isAfter(today) } == true
                    if (digitalOut && entity.releaseNotifyStage < 2) {
                        Notifications.showWatchlist(
                            applicationContext, key,
                            "«${entity.name}» вышел онлайн",
                            "Фильм из списка «Хочу посмотреть» стал доступен в цифровом релизе",
                        )
                        repository.updateEntity(entity.copy(releaseNotifyStage = 2))
                    } else if (theatricalOut && entity.releaseNotifyStage < 1) {
                        Notifications.showWatchlist(
                            applicationContext, key,
                            "Премьера: «${entity.name}»",
                            "Фильм из списка «Хочу посмотреть» вышел в прокат",
                        )
                        repository.updateEntity(entity.copy(releaseNotifyStage = 1))
                    }
                }
                MediaType.TV -> {
                    val last = details.lastEpisode ?: continue
                    if (last.date?.isAfter(today) == true) continue
                    val from = if (entity.inWatchlist) "Сериал из списка «Хочу посмотреть»" else "Сериал, который вам понравился"
                    val known = entity.lastKnownEpisode
                    when {
                        last.season > entity.lastKnownSeason -> {
                            val text = if (last.season <= 1) "Вышел сериал «${entity.name}»"
                            else "Вышел ${last.season}-й сезон «${entity.name}»"
                            Notifications.showWatchlist(applicationContext, key, text, from)
                            repository.updateEntity(entity.copy(lastKnownSeason = last.season, lastKnownEpisode = last.code))
                        }
                        // Запись из прошлой версии: просто запоминаем, на какой серии сериал сейчас
                        known == 0 -> repository.updateEntity(entity.copy(lastKnownEpisode = last.code))
                        last.code > known -> {
                            if (notifyEpisodes) {
                                val sameSeason = last.season == known / 1000
                                val fresh = if (sameSeason) last.number - known % 1000 else 1
                                val title = if (fresh > 1) "Новые серии «${entity.name}»" else "Новая серия «${entity.name}»"
                                val episode = "${last.season} сезон, ${last.number} серия" +
                                    (last.name?.takeIf { it.isNotBlank() && !it.startsWith("Эпизод") && !it.startsWith("Episode") }
                                        ?.let { " — «$it»" } ?: "")
                                Notifications.showWatchlist(applicationContext, key, title, "$episode. $from")
                            }
                            repository.updateEntity(entity.copy(lastKnownEpisode = last.code))
                        }
                    }
                }
            }
        }
    }

    private suspend fun checkNewForYou(container: AppContainer) {
        val profile = container.recommendations.profile()
        if (profile.isEmpty) return
        val repository = container.repository
        val alreadyNotified = container.settings.notifiedNewTitles().toSet()

        val matches = (repository.feed(FeedSection.DIGITAL).items + repository.feed(FeedSection.NEW_SERIES).items)
            .filter { it.key !in profile.excluded }
            .filter { "${it.type.path}:${it.id}" !in alreadyNotified }
            .mapNotNull { title -> profile.matchPercent(title)?.let { title to it } }
            .filter { (_, match) -> match >= MATCH_THRESHOLD }
            .sortedByDescending { (title, match) -> match * 1000 + title.popularity }
        if (matches.isEmpty()) return

        val top = matches.take(3)
        val text = top.joinToString(", ") { (title, match) -> "«${title.name}» — $match%" }
        Notifications.showNewForYou(
            applicationContext,
            if (top.size == 1) "Новинка для вас" else "Новинки для вас",
            text,
            top.singleOrNull()?.first?.key,
        )
        // Запоминаем все подходящие, чтобы в следующий раз сообщать только о действительно новом
        container.settings.addNotifiedNewTitles(matches.map { (t, _) -> "${t.type.path}:${t.id}" })
    }

    companion object {
        private const val PERIODIC = "release_check"
        private const val ONE_TIME = "release_check_now"
        private const val MATCH_THRESHOLD = 80

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReleaseCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Разовая проверка прямо сейчас (кнопка в настройках). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReleaseCheckWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
        }

        private fun networkConstraints() =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
