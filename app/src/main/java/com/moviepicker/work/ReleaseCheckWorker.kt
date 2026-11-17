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
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка дважды в сутки:
 *  - вышел ли фильм или новый сезон из «Хочу посмотреть»;
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
        val today = LocalDate.now()
        for (entity in repository.allMarked().filter { it.inWatchlist }) {
            if (!entity.featuresLoaded) {
                // Сначала запоминаем текущее состояние, иначе уведомим о том, что уже вышло
                repository.loadFeatures(entity.type, entity.id)
                continue
            }
            val key = TitleKey(entity.mediaType, entity.id)
            val details = repository.details(key)
            when (entity.mediaType) {
                MediaType.MOVIE -> {
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
                    val aired = last.date?.isAfter(today) != true
                    if (aired && last.season > entity.lastKnownSeason) {
                        val text = if (last.season <= 1) "Вышел сериал «${entity.name}»"
                        else "Вышел ${last.season}-й сезон «${entity.name}»"
                        Notifications.showWatchlist(
                            applicationContext, key, text, "Сериал из списка «Хочу посмотреть»"
                        )
                        repository.updateEntity(entity.copy(lastKnownSeason = last.season))
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
