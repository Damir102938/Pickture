package com.moviepicker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SwitchDefaults
import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.Perforation
import com.moviepicker.ui.design.PicktureTopBar
import com.moviepicker.ui.design.PkType
import com.moviepicker.ui.design.TextAction
import com.moviepicker.ui.design.TicketDialog
import com.moviepicker.ui.design.TicketOutlineButton
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

@Composable
fun SettingsScreen(onBack: () -> Unit, onEditOnboarding: () -> Unit, onEditApiKey: () -> Unit) {
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

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PicktureTopBar("Настройки", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Header(1, "Ключ TMDB")
            val key = settings.tmdbApiKey
            Text(
                if (key.isBlank()) "Ключ не задан"
                else "Сохранён ключ ${key.take(4)}…${key.takeLast(4)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            TicketOutlineButton("Изменить ключ", onEditApiKey, Modifier.padding(horizontal = 16.dp))

            Divider()
            Header(2, "Уведомления")
            SwitchRow(
                title = "Присылать уведомления",
                description = "Когда фильм из «Хочу посмотреть» выходит онлайн или в кино, у сериала выходит " +
                    "новый сезон, а также о свежих релизах, похожих на ваши любимые",
                checked = settings.notificationsEnabled,
                onChange = { enabled ->
                    scope.launch { container.settings.setNotificationsEnabled(enabled) }
                    if (enabled && Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            if (settings.notificationsEnabled) {
                SwitchRow(
                    title = "Каждая новая серия",
                    description = "Для сериалов из «Хочу посмотреть» и просмотренных, которым вы поставили 7+ или лайк",
                    checked = settings.notifyEpisodes,
                    onChange = { scope.launch { container.settings.setNotifyEpisodes(it) } },
                )
                Spacer(Modifier.height(4.dp))
                TicketOutlineButton(
                    "Проверить сейчас",
                    onClick = {
                        ReleaseCheckWorker.runNow(context)
                        Toast.makeText(context, "Проверяем новинки…", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Divider()
            Header(3, "Стартовые предпочтения")
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
                color = Brand.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            TicketOutlineButton("Изменить жанры и фильмы", onEditOnboarding, Modifier.padding(horizontal = 16.dp))

            Divider()
            Header(4, "Резервная копия")
            Text(
                "Оценки и списки хранятся только на этом телефоне и пропадут при удалении приложения. " +
                    "Сохраните их в файл, например в Telegram или на Google Диск, и восстановите после переустановки.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TicketOutlineButton("Сохранить в файл", backupActions.export, enabled = !backupActions.busy)
                TicketOutlineButton("Восстановить", backupActions.import, enabled = !backupActions.busy)
            }

            Divider()
            Header(5, "Данные")
            TicketOutlineButton(
                "Сбросить все оценки и списки", { confirmReset = true },
                Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error,
            )

            Divider()
            Header(6, "Обновления")
            Text(
                "Версия ${container.updater.currentVersion}. Новые версии выходят на GitHub — " +
                    "приложение проверяет их при запуске и предлагает обновиться.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            TicketOutlineButton(
                "Проверить обновления",
                onClick = { scope.launch { container.updater.checkManually() } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Divider()
            Text(
                "Данные о фильмах и сериалах предоставлены TMDB.\n" +
                    "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.TextMuted,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (confirmReset) {
        TicketDialog(
            title = "Сбросить всё?",
            onDismiss = { confirmReset = false },
            confirm = {
                TextAction("Сбросить", color = MaterialTheme.colorScheme.error, onClick = {
                    confirmReset = false
                    scope.launch {
                        container.repository.clearAll()
                        container.recommendations.invalidate()
                    }
                })
            },
            dismiss = { TextAction("Отмена", { confirmReset = false }) },
        ) {
            Text("Удалятся все лайки, дизлайки и список «Хочу посмотреть». Отменить нельзя.")
        }
    }
}

@Composable
private fun Divider() {
    Perforation(Modifier.padding(horizontal = 16.dp, vertical = 18.dp))
}

@Composable
private fun Header(number: Int, text: String) {
    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("%02d".format(number), style = PkType.number, color = Brand.Accent)
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = PkType.section, color = Brand.Text)
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Brand.TextMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Brand.Gold,
                checkedThumbColor = Brand.OnGold,
                uncheckedTrackColor = Brand.Ticket,
                uncheckedBorderColor = Brand.Perforation,
                uncheckedThumbColor = Brand.TextMuted,
            ),
        )
    }
}
