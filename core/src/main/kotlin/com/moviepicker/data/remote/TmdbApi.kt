package com.moviepicker.data.remote

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
        const val DEFAULT_API_URL = "https://api.themoviedb.org/3/"
        private val API_KEY_REGEX = Regex("""api_key=[^&\s]+""")

        /**
         * @param keyProvider текущий ключ TMDB (вводится пользователем в приложении)
         * @param logger если задан — короткий лог запросов (ключ скрывается)
         */
        fun create(
            baseUrl: String = DEFAULT_API_URL,
            keyProvider: () -> String,
            logger: ((String) -> Unit)? = null,
        ): TmdbApi {
            val authInterceptor = Interceptor { chain ->
                val original = chain.request()
                val key = keyProvider().trim()
                val urlBuilder = original.url.newBuilder()
                if (original.url.queryParameter("language") == null) {
                    urlBuilder.addQueryParameter("language", LANGUAGE)
                }
                val requestBuilder = original.newBuilder()
                applyKey(key, urlBuilder, requestBuilder)
                chain.proceed(requestBuilder.url(urlBuilder.build()).build())
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .apply {
                    if (logger != null) {
                        val logging = HttpLoggingInterceptor { message ->
                            logger(message.replace(API_KEY_REGEX, "api_key=***"))
                        }
                        logging.level = HttpLoggingInterceptor.Level.BASIC
                        addInterceptor(logging)
                    }
                }
                .build()

            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                explicitNulls = false
            }

            return Retrofit.Builder()
                .baseUrl(normalize(baseUrl))
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TmdbApi::class.java)
        }

        /** Длинный Read Access Token (JWT) передаётся заголовком, короткий API key — параметром. */
        internal fun applyKey(key: String, url: okhttp3.HttpUrl.Builder, request: okhttp3.Request.Builder) {
            if (key.isEmpty()) return
            if (key.startsWith("eyJ")) request.header("Authorization", "Bearer $key")
            else url.addQueryParameter("api_key", key)
        }

        internal fun normalize(url: String) = if (url.endsWith("/")) url else "$url/"
    }
}
