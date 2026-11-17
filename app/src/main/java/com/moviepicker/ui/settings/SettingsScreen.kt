package com.moviepicker.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moviepicker.container
import com.moviepicker.data.local.Settings
import com.moviepicker.domain.Genres
import com.moviepicker.ui.common.rememberBackupActions
import com.moviepicker.work.Notifications
import com.moviepicker.work.ReleaseCheckWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onEditOnboarding: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(Settings())
    val entities by container.repository.observeAll().collectAsStateWithLifecycle(emptyList())
    var confirmReset by remember { mutableStateOf(false) }
    val backupActions = rememberBackupActions()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "Разрешите уведомления в настройках Android", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Настройки") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Header("Уведомления")
            SwitchRow(
                title = "Присылать уведомления",
                description = "Когда выходит фильм или новый сезон из «Хочу посмотреть», " +
                    "а также о свежих релизах, похожих на ваши любимые",
                checked = settings.notificationsEnabled,
                onChange = { enabled ->
                    scope.launch { container.settings.setNotificationsEnabled(enabled) }
                    if (enabled && Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            if (settings.notificationsEnabled) {
                OutlinedButton(
                    onClick = {
                        ReleaseCheckWorker.runNow(context)
                        Toast.makeText(context, "Проверяем новинки…", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Проверить сейчас") }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Header("Стартовые предпочтения")
            SwitchRow(
                title = "Учитывать в рекомендациях",
                description = "Жанры и фильмы, отмеченные на стартовом экране. Если выключить, " +
                    "рекомендации будут строиться только на ваших лайках и дизлайках.",
                checked = settings.useOnboarding,
                onChange = { scope.launch { container.settings.setUseOnboarding(it) } },
            )
            val genreNames = Genres.selectable.filter { it.movieId in settings.favoriteGenres }.map { it.name }
            val onboardingLikes = entities.count { it.fromOnboarding && it.reaction > 0 }
            Text(
                buildString {
                    append("Жанры: ")
                    append(if (genreNames.isEmpty()) "не выбраны" else genreNames.joinToString(", "))
                    append("\nФильмов отмечено: $onboardingLikes")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onEditOnboarding, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Изменить жанры и фильмы")
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Header("Резервная копия")
            Text(
                "Оценки и списки хранятся только на этом телефоне и пропадут при удалении приложения. " +
                    "Сохраните их в файл, например в Telegram или на Google Диск, и восстановите после переустановки.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = backupActions.export, enabled = !backupActions.busy) {
                    Text("Сохранить в файл")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = backupActions.import, enabled = !backupActions.busy) {
                    Text("Восстановить")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Header("Данные")
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Сбросить все оценки и списки")
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(
                "Данные о фильмах и сериалах предоставлены TMDB.\n" +
                    "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
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
                        container.repository.clearAll()
                        container.recommendations.invalidate()
                    }
                }) { Text("Сбросить") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
