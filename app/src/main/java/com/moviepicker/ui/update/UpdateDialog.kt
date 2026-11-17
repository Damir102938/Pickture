package com.moviepicker.ui.update

import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.PkType
import com.moviepicker.ui.design.TextAction
import com.moviepicker.ui.design.TicketDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moviepicker.container
import com.moviepicker.update.UpdateState
import kotlinx.coroutines.launch

/** Проверяет обновление при запуске и показывает диалог, если вышла новая версия. */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val updater = context.container.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { updater.checkOnLaunch() }

    when (val s = state) {
        is UpdateState.Available -> TicketDialog(
            title = "Доступна версия ${s.info.version}",
            onDismiss = updater::dismiss,
            confirm = { TextAction("Обновить", { scope.launch { updater.download(s.info) } }, color = Brand.Gold) },
            dismiss = { TextAction("Позже", updater::dismiss, color = Brand.TextMuted) },
        ) {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text("У вас ${updater.currentVersion}. Обновление скачается и установится поверх — оценки сохранятся.")
                if (s.info.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("ЧТО НОВОГО", style = PkType.label, color = Brand.Gold)
                    Text(s.info.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        is UpdateState.Downloading -> TicketDialog(
            title = "Скачиваем ${s.info.version}",
            onDismiss = {},
            confirm = null,
        ) {
            Column {
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Brand.Gold,
                    trackColor = Brand.Perforation,
                )
                Spacer(Modifier.height(8.dp))
                Text("${(s.progress * 100).toInt()}%", style = PkType.number, color = Brand.Gold)
            }
        }
        is UpdateState.NeedsPermission -> TicketDialog(
            title = "Нужно разрешение",
            onDismiss = updater::dismiss,
            confirm = { TextAction("Установить", { updater.install(s.info, s.file) }, color = Brand.Gold) },
            dismiss = { TextAction("Позже", updater::dismiss, color = Brand.TextMuted) },
        ) {
            Text(
                "Включите для Pickture «Разрешить из этого источника» в открывшихся настройках, " +
                    "вернитесь сюда и нажмите «Установить»."
            )
        }
        is UpdateState.UpToDate -> {
            LaunchedEffect(s) {
                Toast.makeText(context, "У вас последняя версия ${updater.currentVersion}", Toast.LENGTH_SHORT).show()
                updater.dismiss()
            }
        }
        is UpdateState.Error -> {
            LaunchedEffect(s) {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                updater.dismiss()
            }
        }
        UpdateState.Idle, UpdateState.Checking -> Unit
    }
}
