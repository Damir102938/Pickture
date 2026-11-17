package com.moviepicker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Где получить ключ TMDB — показывается рядом с полем ввода. */
object TmdbLinks {
    const val API_SETTINGS = "https://www.themoviedb.org/settings/api"
    const val SIGN_UP = "https://www.themoviedb.org/signup"
}

/** Текущий ключ. Обновляется из настроек; читается при каждом запросе к TMDB. */
class ApiKeyHolder(@Volatile var key: String = "") {
    val hasKey: Boolean get() = key.isNotBlank()
}

sealed interface KeyCheck {
    data object Valid : KeyCheck
    data object Invalid : KeyCheck
    data class Failed(val message: String) : KeyCheck
}

/** Проверяет ключ одним лёгким запросом, прежде чем сохранить его. */
class TmdbKeyValidator(private val baseUrl: String = TmdbApi.DEFAULT_API_URL) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun check(rawKey: String): KeyCheck = withContext(Dispatchers.IO) {
        val key = rawKey.trim()
        if (key.length < 20) return@withContext KeyCheck.Invalid
        val url = (TmdbApi.normalize(baseUrl) + "configuration").toHttpUrl().newBuilder()
        val request = Request.Builder()
        TmdbApi.applyKey(key, url, request)
        try {
            client.newCall(request.url(url.build()).build()).execute().use { response ->
                when {
                    response.isSuccessful -> KeyCheck.Valid
                    response.code == 401 -> KeyCheck.Invalid
                    else -> KeyCheck.Failed("TMDB ответил с ошибкой ${response.code}")
                }
            }
        } catch (e: IOException) {
            KeyCheck.Failed("Нет связи с TMDB. Проверьте интернет; в некоторых регионах нужен VPN.")
        }
    }
}
