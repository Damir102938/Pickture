package com.moviepicker.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moviepicker.data.BackupException
import com.moviepicker.data.local.Settings
import com.moviepicker.data.remote.KeyCheck
import com.moviepicker.data.remote.TmdbLinks
import com.moviepicker.desktop.BuildInfo
import com.moviepicker.desktop.DesktopContainer
import com.moviepicker.domain.Genres
import com.moviepicker.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

// ---------- Настройки ----------

@Composable
fun SettingsScreen(c: DesktopContainer, nav: Navigator) {
    val settings by c.settings.settings.collectAsState(Settings())
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(32.dp).widthIn(max = 760.dp)) {
            Text("Настройки", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

            Header("Ключ TMDB")
            val key = settings.tmdbApiKey
            Text(if (key.isBlank()) "Ключ не задан" else "Сохранён ключ ${key.take(4)}…${key.takeLast(4)}")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { nav.open(Screen.ApiKey) }) { Text("Изменить ключ") }

            Divider()
            Header("Вкус")
            val genreNames = Genres.selectable.filter { it.movieId in settings.favoriteGenres }.map { it.name }
            Text("Любимые жанры: " + if (genreNames.isEmpty()) "не выбраны" else genreNames.joinToString(", "))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { nav.open(Screen.Genres) }) { Text("Выбрать любимые жанры") }
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                "Учитывать любимые жанры и стартовые оценки",
                "Если выключить, рекомендации будут строиться только на лайках и дизлайках.",
                settings.useOnboarding,
            ) { scope.launch { c.settings.setUseOnboarding(it) } }

            Divider()
            Header("Резервная копия")
            Text(
                "Файл совместим с Android-версией: можно перенести оценки с телефона на компьютер и обратно.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val file = chooseFile("Сохранить резервную копию", FileDialog.SAVE, c.backup.suggestedFileName())
                        ?: return@OutlinedButton
                    scope.launch {
                        message = runCatching {
                            val (json, count) = c.backup.exportJson()
                            withContext(Dispatchers.IO) { file.writeText(json) }
                            "Сохранено: $count фильмов и сериалов"
                        }.getOrElse { "Не удалось сохранить: ${it.message}" }
                    }
                }) { Text("Сохранить в файл") }
                OutlinedButton(onClick = {
                    val file = chooseFile("Восстановить из резервной копии", FileDialog.LOAD, null) ?: return@OutlinedButton
                    scope.launch {
                        message = runCatching {
                            val text = withContext(Dispatchers.IO) { file.readText() }
                            val result = c.backup.importJson(text)
                            "Восстановлено: ${result.applied} из ${result.total}"
                        }.getOrElse { if (it is BackupException) it.message else "Не удалось прочитать файл" }
                    }
                }) { Text("Восстановить") }
            }

            Divider()
            Header("Обновления")
            Text("Версия ${BuildInfo.VERSION}. При запуске Pickture проверяет новые версии на GitHub.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !checkingUpdate, onClick = {
                    checkingUpdate = true
                    scope.launch {
                        message = runCatching { c.updates.check(BuildInfo.VERSION) }.fold(
                            onSuccess = { info ->
                                if (info == null) "У вас последняя версия ${BuildInfo.VERSION}"
                                else {
                                    openUrl(info.desktopZip?.url ?: info.pageUrl)
                                    "Скачивается версия ${info.version}. Распакуйте архив поверх старой папки."
                                }
                            },
                            onFailure = { "Не удалось проверить обновления" },
                        )
                        checkingUpdate = false
                    }
                }) { Text("Проверить обновления") }
                TextButton(onClick = { openUrl(UpdateChecker.RELEASES_PAGE) }) { Text("Страница релизов") }
            }

            Divider()
            Header("Данные")
            Text("Папка с данными: ${c.dataDir.absolutePath}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirmReset = true }) { Text("Сбросить все оценки и списки") }

            Divider()
            Text(
                "Данные о фильмах и сериалах предоставлены TMDB.\n" +
                    "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("ОК") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить всё?") },
            text = { Text("Удалятся все лайки, дизлайки и список «Хочу посмотреть». Отменить нельзя.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        c.repository.clearAll()
                        c.recommendations.invalidate()
                    }
                }) { Text("Сбросить") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
        )
    }
}

private fun chooseFile(title: String, mode: Int, suggestedName: String?): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    if (suggestedName != null) dialog.file = suggestedName
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

@Composable
private fun Header(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(vertical = 20.dp))

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------- Ключ TMDB ----------

@Composable
fun ApiKeyScreen(c: DesktopContainer, onSaved: () -> Unit, onBack: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var key by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offerSaveAnyway by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { key = c.settings.current().tmdbApiKey }

    fun save(value: String) = scope.launch {
        c.settings.setTmdbApiKey(value)
        c.recommendations.invalidate()
        onSaved()
    }

    fun check() {
        val value = key.trim()
        if (value.isEmpty()) {
            error = "Вставьте ключ"
            return
        }
        checking = true
        error = null
        offerSaveAnyway = false
        scope.launch {
            when (val r = c.keyValidator.check(value)) {
                KeyCheck.Valid -> save(value)
                KeyCheck.Invalid -> error = "TMDB не принял этот ключ. Проверьте, что скопировали его целиком."
                is KeyCheck.Failed -> {
                    error = r.message
                    offerSaveAnyway = true
                }
            }
            checking = false
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).padding(32.dp).verticalScroll(rememberScrollState())) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Icon(Icons.Filled.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            Text("Ключ TMDB", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pickture берёт данные о фильмах из TMDB — крупной открытой базы кино. " +
                    "Нужен личный ключ: он бесплатный, а получить его можно за пару минут.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Step(1, "Зарегистрируйтесь на TMDB, если ещё нет аккаунта") {
                        Link("Регистрация на themoviedb.org") { openUrl(TmdbLinks.SIGN_UP) }
                    }
                    Step(2, "Откройте страницу API и создайте ключ (бесплатный план Developer)") {
                        Link("Открыть страницу ключей TMDB") { openUrl(TmdbLinks.API_SETTINGS) }
                    }
                    Step(3, "Скопируйте «API Key» или «API Read Access Token» и вставьте ниже")
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    error = null
                    offerSaveAnyway = false
                },
                label = { Text("Ключ TMDB") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.text?.let { key = it.trim() } }) {
                        Icon(Icons.Filled.ContentPaste, "Вставить из буфера")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = ::check, enabled = !checking) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Проверить и сохранить")
                }
                if (offerSaveAnyway) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { save(key.trim()) }) { Text("Сохранить без проверки") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Ключ хранится только на этом компьютере и отправляется только в TMDB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Step(number: Int, text: String, action: (@Composable () -> Unit)? = null) {
    Row {
        Text("$number.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
        Column {
            Text(text)
            action?.invoke()
        }
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Text(text)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
    }
}

// ---------- Любимые жанры (стартовый экран на ПК) ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenresScreen(c: DesktopContainer, firstRun: Boolean, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(Unit) { selected = c.settings.current().favoriteGenres }

    fun finish(genres: Set<Int>) = scope.launch {
        c.settings.completeOnboarding(genres)
        c.recommendations.invalidate()
        onDone()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).padding(40.dp)) {
            Text("Что вы любите смотреть?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Выберите жанры, и рекомендации появятся сразу. Дальше ставьте лайки фильмам — подборка станет точнее. " +
                    "Выбор можно изменить в настройках.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Genres.selectable.forEach { g ->
                    FilterChip(
                        selected = g.movieId in selected,
                        onClick = { selected = if (g.movieId in selected) selected - g.movieId else selected + g.movieId },
                        label = { Text(g.name) },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (firstRun) TextButton(onClick = { finish(emptySet()) }) { Text("Пропустить") }
                else TextButton(onClick = onDone) { Text("Отмена") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { finish(selected) }) { Text("Готово") }
            }
        }
    }
}
