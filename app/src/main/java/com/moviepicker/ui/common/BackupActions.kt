package com.moviepicker.ui.common

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.moviepicker.container
import com.moviepicker.data.BackupException
import com.moviepicker.data.ImportResult
import kotlinx.coroutines.launch

class BackupActions(
    val export: () -> Unit,
    val import: () -> Unit,
    val busy: Boolean,
)

/** Кнопки «Сохранить в файл» и «Восстановить из файла» через системный выбор файла. */
@Composable
fun rememberBackupActions(onImported: (ImportResult) -> Unit = {}): BackupActions {
    val context = LocalContext.current
    val backup = context.container.backup
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { backup.export(uri) }
                .onSuccess { toast("Сохранено: $it фильмов и сериалов") }
                .onFailure { toast(it.message ?: "Не удалось сохранить") }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { backup.import(uri) }
                .onSuccess { result ->
                    toast("Восстановлено: ${result.applied} из ${result.total}")
                    onImported(result)
                }
                .onFailure { e ->
                    toast(if (e is BackupException) e.message!! else "Не удалось прочитать файл")
                }
            busy = false
        }
    }

    return BackupActions(
        export = { exportLauncher.launch(backup.suggestedFileName()) },
        import = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
        busy = busy,
    )
}
