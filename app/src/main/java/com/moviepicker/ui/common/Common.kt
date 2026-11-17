package com.moviepicker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviepicker.AppContainer
import com.moviepicker.container
import com.moviepicker.domain.MediaType
import com.moviepicker.domain.Title
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Error(val message: String) : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
}

/** ViewModel, получающий зависимости из [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

fun Throwable.userMessage(): String = when (this) {
    is HttpException -> when (code()) {
        401 -> "TMDB отклонил ключ (401). Проверьте TMDB_API_KEY в local.properties."
        404 -> "Не найдено (404)."
        429 -> "Слишком много запросов к TMDB, попробуйте через минуту."
        else -> "Ошибка сервера TMDB (${code()})."
    }
    is IOException -> "Нет связи с TMDB. Проверьте интернет; в некоторых регионах TMDB открывается только через VPN."
    else -> localizedMessage ?: "Что-то пошло не так"
}

private val ruLocale = Locale.forLanguageTag("ru")
private val fullDate = DateTimeFormatter.ofPattern("d MMMM yyyy", ruLocale)
private val shortDate = DateTimeFormatter.ofPattern("d MMM", ruLocale)

fun LocalDate.formatFull(): String = format(fullDate)

/** Подпись под карточкой: для будущих релизов — дата, для вышедших — год. */
fun Title.subtitle(): String {
    val today = LocalDate.now()
    val d = date
    val datePart = when {
        d == null -> "Дата неизвестна"
        d.isAfter(today) -> if (d.year == today.year) d.format(shortDate) else d.format(fullDate)
        else -> d.year.toString()
    }
    return if (type == MediaType.TV) "$datePart · сериал" else datePart
}
