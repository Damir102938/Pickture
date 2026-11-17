package com.moviepicker.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)

data class ReleaseAsset(val name: String, val url: String, val size: Long)

/** Свежий релиз на GitHub. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apk: ReleaseAsset?,
    val desktopZip: ReleaseAsset?,
)

/**
 * Проверка обновлений по релизам GitHub: тег вида `v0.4.0`,
 * в релизе лежат `Pickture-*.apk` и `Pickture-desktop-*.zip`.
 */
class UpdateChecker(private val repo: String = DEFAULT_REPO) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Возвращает информацию о релизе, если он новее [currentVersion], иначе null. */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Pickture-App")
            .build()
        val release = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub: ${response.code}")
            json.decodeFromString(GhRelease.serializer(), response.body!!.string())
        }
        if (release.draft || release.prerelease) return@withContext null
        val version = release.tagName.removePrefix("v")
        if (!isNewer(version, currentVersion)) return@withContext null
        fun asset(predicate: (String) -> Boolean) = release.assets
            .firstOrNull { predicate(it.name.lowercase()) }
            ?.let { ReleaseAsset(it.name, it.downloadUrl, it.size) }
        UpdateInfo(
            version = version,
            notes = plainText(release.body.orEmpty()),
            pageUrl = release.htmlUrl,
            apk = asset { it.endsWith(".apk") },
            desktopZip = asset { it.contains("desktop") && it.endsWith(".zip") },
        )
    }

    /** Скачивает файл с отчётом о прогрессе (0..1). */
    suspend fun download(asset: ReleaseAsset, target: File, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".part")
            val request = Request.Builder().url(asset.url).header("User-Agent", "Pickture-App").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Загрузка: ${response.code}")
                val body = response.body!!
                val total = body.contentLength().takeIf { it > 0 } ?: asset.size
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("Не удалось сохранить файл")
            target
        }

    companion object {
        const val DEFAULT_REPO = "Damir102938/Pickture"
        const val RELEASES_PAGE = "https://github.com/$DEFAULT_REPO/releases/latest"

        /** Описание релиза пишется в Markdown — для диалога в приложении убираем разметку. */
        fun plainText(markdown: String): String = markdown
            .lines()
            .joinToString("\n") { line ->
                line.trim()
                    .replace(Regex("""^#+\s*"""), "")
                    .replace(Regex("""^[-*]\s+"""), "• ")
                    .replace("**", "")
                    .replace("`", "")
            }
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

        /** Сравнение версий вида 1.2.3 по числам. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
