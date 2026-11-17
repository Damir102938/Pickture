package com.moviepicker.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.moviepicker.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    /** APK скачан, но Android ещё не разрешил приложению устанавливать обновления. */
    data class NeedsPermission(val info: UpdateInfo, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/** Обновление «внутри приложения»: свежий APK берётся из последнего релиза на GitHub. */
class AppUpdater(context: Context, private val checker: UpdateChecker) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var checkedThisLaunch = false

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Автоматическая проверка — один раз за запуск, ошибки молча игнорируются. */
    suspend fun checkOnLaunch() {
        if (checkedThisLaunch) return
        checkedThisLaunch = true
        val info = runCatching { checker.check(currentVersion) }.getOrNull() ?: return
        if (info.apk != null) _state.value = UpdateState.Available(info)
    }

    /** Ручная проверка из настроек — показывает результат в любом случае. */
    suspend fun checkManually() {
        _state.value = UpdateState.Checking
        _state.value = runCatching { checker.check(currentVersion) }.fold(
            onSuccess = { info ->
                when {
                    info == null -> UpdateState.UpToDate
                    info.apk == null -> UpdateState.Error("В релизе ${info.version} нет APK")
                    else -> UpdateState.Available(info)
                }
            },
            onFailure = { UpdateState.Error("Не удалось проверить обновления. Проверьте интернет.") },
        )
    }

    suspend fun download(info: UpdateInfo) {
        val asset = info.apk ?: return
        _state.value = UpdateState.Downloading(info, 0f)
        val target = File(appContext.cacheDir, "updates/Pickture-${info.version}.apk")
        runCatching {
            checker.download(asset, target) { p -> _state.value = UpdateState.Downloading(info, p) }
        }.onSuccess { install(info, it) }
            .onFailure { _state.value = UpdateState.Error("Не удалось скачать обновление: ${it.message}") }
    }

    fun install(info: UpdateInfo, file: File) {
        val pm = appContext.packageManager
        if (!pm.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(info, file)
            openInstallPermissionSettings()
            return
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        _state.value = UpdateState.Idle
    }

    /** Открывает системный экран «Установка неизвестных приложений» для Pickture. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
