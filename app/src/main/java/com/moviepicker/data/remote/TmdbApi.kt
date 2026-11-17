package com.moviepicker.data.remote

import android.util.Log
import com.moviepicker.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.util.concurrent.TimeUnit

/** Документация: https://developer.themoviedb.org/reference */
interface TmdbApi {

    @GET("trending/{type}/{window}")
    suspend fun trending(
        @Path("type") type: String,
        @Path("window") window: String = "week",
        @Query("page") page: Int = 1,
    ): PagedResponse<TitleDto>

    @GET("discover/{type}")
    suspend fun discover(
        @Path("type") type: String,
        @QueryMap params: Map<String, String>,
    ): PagedResponse<TitleDto>

    @GET("tv/on_the_air")
    suspend fun tvOnTheAir(@Query("page") page: Int = 1): PagedResponse<TitleDto>

    @GET("{type}/{id}")
    suspend fun details(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("append_to_response") append: String? = null,
        @Query("include_video_language") videoLanguages: String? = "ru,en,null",
        @Query("language") language: String? = null,
    ): DetailsDto

    @GET("{type}/{id}/recommendations")
    suspend fun recommendations(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
    ): PagedResponse<TitleDto>

    @GET("{type}/{id}/videos")
    suspend fun videos(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("include_video_language") videoLanguages: String = "ru,en,null",
    ): VideosDto

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): PagedResponse<TitleDto>

    companion object {
        const val LANGUAGE = "ru-RU"
        private val API_KEY_REGEX = Regex("api_key=[^&\\s]+")

        val hasApiKey: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

        fun create(debug: Boolean): TmdbApi {
            val key = BuildConfig.TMDB_API_KEY
            val authInterceptor = Interceptor { chain ->
                val original = chain.request()
                val urlBuilder = original.url.newBuilder()
                if (original.url.queryParameter("language") == null) {
                    urlBuilder.addQueryParameter("language", LANGUAGE)
                }
                val requestBuilder = original.newBuilder()
                // Длинный Read Access Token (JWT) передаётся заголовком, короткий API key параметром.
                if (key.startsWith("eyJ")) {
                    requestBuilder.header("Authorization", "Bearer $key")
                } else {
                    urlBuilder.addQueryParameter("api_key", key)
                }
                chain.proceed(requestBuilder.url(urlBuilder.build()).build())
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .apply {
                    if (debug) {
                        val logger = HttpLoggingInterceptor { message ->
                            Log.d("TMDB", message.replace(API_KEY_REGEX, "api_key=***"))
                        }
                        logger.level = HttpLoggingInterceptor.Level.BASIC
                        addInterceptor(logger)
                    }
                }
                .build()

            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                explicitNulls = false
            }

            return Retrofit.Builder()
                .baseUrl(BuildConfig.TMDB_API_URL.let { if (it.endsWith("/")) it else "$it/" })
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TmdbApi::class.java)
        }
    }
}
