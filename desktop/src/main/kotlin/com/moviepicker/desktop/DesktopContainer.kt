package com.moviepicker.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.moviepicker.data.BackupService
import com.moviepicker.data.TitleRepository
import com.moviepicker.data.local.SettingsStore
import com.moviepicker.data.local.UserTitleEntity
import com.moviepicker.data.local.UserTitleStore
import com.moviepicker.data.remote.ApiKeyHolder
import com.moviepicker.data.remote.TmdbApi
import com.moviepicker.data.remote.TmdbKeyValidator
import com.moviepicker.domain.RecommendationEngine
import com.moviepicker.domain.TasteProfile
import com.moviepicker.domain.TitleKey
import com.moviepicker.domain.UserMark
import com.moviepicker.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Все синглтоны версии для ПК. Данные лежат в %APPDATA%\Pickture.
 * Для разработки: PICKTURE_DATA_DIR — другая папка данных, PICKTURE_TMDB_KEY — запасной ключ.
 */
class DesktopContainer {
    val dataDir: File = (
        System.getenv("PICKTURE_DATA_DIR")?.let(::File)
            ?: File(System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home")), "Pickture")
        ).apply { mkdirs() }

    private val devKey: String = System.getenv("PICKTURE_TMDB_KEY").orEmpty().trim()
    private fun effectiveKey(userKey: String) = userKey.ifBlank { devKey }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(
        PreferenceDataStoreFactory.create(produceFile = { File(dataDir, "settings.preferences_pb") })
    )

    val apiKey = ApiKeyHolder()
    private val api = TmdbApi.create(
        keyProvider = {
            apiKey.key.ifBlank { runBlocking { effectiveKey(settings.current().tmdbApiKey) }.also { apiKey.key = it } }
        },
    )
    val keyValidator = TmdbKeyValidator()

    val repository = TitleRepository(api, JsonUserTitleStore(File(dataDir, "titles.json")), settings, appScope)
    val recommendations = RecommendationEngine(repository, settings)
    val backup = BackupService(repository, settings, recommendations)
    val updates = UpdateChecker()

    val hasApiKey: StateFlow<Boolean?> = settings.settings
        .map { effectiveKey(it.tmdbApiKey).isNotBlank() }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    val tasteProfile: StateFlow<TasteProfile> =
        combine(repository.observeAll(), settings.settings) { entities, s -> TasteProfile.build(entities, s) }
            .stateIn(appScope, SharingStarted.Eagerly, TasteProfile.EMPTY)

    val marks: StateFlow<Map<TitleKey, UserMark>> =
        repository.observeMarks().stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    init {
        appScope.launch { settings.settings.collect { apiKey.key = effectiveKey(it.tmdbApiKey) } }
    }
}

/** Отметки пользователя в JSON-файле (на Android вместо этого Room). */
class JsonUserTitleStore(private val file: File) : UserTitleStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(UserTitleEntity.serializer())
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    private fun key(type: String, id: Int) = "$type:$id"

    private fun load(): Map<String, UserTitleEntity> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString(serializer, file.readText())
    }.getOrDefault(emptyList()).associateBy { key(it.type, it.id) }

    private fun sorted(map: Map<String, UserTitleEntity>) = map.values.sortedByDescending { it.updatedAt }

    override suspend fun get(type: String, id: Int) = state.value[key(type, id)]
    override fun observe(type: String, id: Int): Flow<UserTitleEntity?> =
        state.map { it[key(type, id)] }.distinctUntilChanged()
    override fun observeAll(): Flow<List<UserTitleEntity>> = state.map(::sorted)
    override suspend fun getAll() = sorted(state.value)
    override suspend fun upsert(entity: UserTitleEntity) = update { it + (key(entity.type, entity.id) to entity) }
    override suspend fun delete(type: String, id: Int) = update { it - key(type, id) }
    override suspend fun clear() = update { emptyMap() }

    private suspend fun update(change: (Map<String, UserTitleEntity>) -> Map<String, UserTitleEntity>) =
        mutex.withLock {
            val next = change(state.value)
            state.value = next
            withContext(Dispatchers.IO) {
                // Пишем во временный файл и подменяем — файл не побьётся при сбое
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json.encodeToString(serializer, sorted(next)))
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }
            Unit
        }
}
