package com.moviepicker.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android-обёртка над [BackupService]: читает и пишет файл, выбранный через системный диалог. */
class BackupManager(context: Context, private val service: BackupService) {
    private val resolver = context.applicationContext.contentResolver

    fun suggestedFileName(): String = service.suggestedFileName()

    /** Возвращает количество сохранённых тайтлов. */
    suspend fun export(uri: Uri): Int {
        val (json, count) = service.exportJson()
        withContext(Dispatchers.IO) {
            val stream = resolver.openOutputStream(uri, "wt") ?: throw BackupException("Не удалось открыть файл для записи")
            stream.use { it.write(json.toByteArray()) }
        }
        return count
    }

    suspend fun import(uri: Uri): ImportResult {
        val text = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } ?: throw BackupException("Не удалось открыть файл")
        return service.importJson(text)
    }
}
