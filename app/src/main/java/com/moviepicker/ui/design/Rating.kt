package com.moviepicker.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviepicker.domain.RatingScale
import java.time.LocalDate
import java.util.Locale
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration

/** Цвет оценки: 7+ — зелёный, 5–6 — янтарный, ниже — приглушённый. */
fun ratingColor(rating: Int): Color = when {
    rating >= 7 -> Brand.MatchHigh
    rating >= 5 -> Brand.MatchMid
    else -> Brand.MatchLow
}

/**
 * Выбор оценки 1..10 — два ряда «мест в зале». Повторное нажатие снимает оценку.
 * Под рядами — словесная расшифровка («Отлично»).
 */
@Composable
fun RatingPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        listOf(1..5, 6..10).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { value ->
                    RatingSeat(
                        value = value,
                        color = ratingColor(if (selected > 0) selected else value),
                        active = selected > 0 && value <= selected,
                        current = value == selected,
                        onClick = { onSelect(if (value == selected) 0 else value) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(
            if (selected > 0) "$selected из 10 · ${RatingScale.label(selected)}" else "Без оценки — можно поставить позже",
            style = PkType.label,
            color = if (selected > 0) ratingColor(selected) else Brand.TextMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RatingSeat(value: Int, color: Color, active: Boolean, current: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val bg by animateColorAsState(
        when {
            current -> color
            active -> color.copy(alpha = 0.22f)
            else -> Color.Transparent
        },
        tween(160), label = "seat",
    )
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier
            .aspectRatio(1.15f)
            .clip(shape)
            .then(if (active) Modifier.border(1.dp, color, shape) else Modifier.dashedBorder(Brand.Perforation, 1.dp, 5.dp))
            .background(bg)
            .pressClickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Оценка $value" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.toString(),
            style = PkType.number.copy(fontSize = 16.sp),
            color = when {
                current -> Brand.Background
                active -> Brand.Text
                else -> Brand.TextMuted
            },
        )
    }
}

/** Маленький значок оценки на постере и в дневнике: «8». */
@Composable
fun RatingBadge(rating: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val color = ratingColor(rating)
    Box(
        modifier
            .size(if (large) 40.dp else 24.dp)
            .clip(RoundedCornerShape(if (large) 6.dp else 4.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, color, RoundedCornerShape(if (large) 6.dp else 4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(rating.toString(), style = PkType.number.copy(fontSize = if (large) 18.sp else 12.sp), color = color)
    }
}

/** Поле даты в стиле билета; по нажатию открывается календарь. */
@Composable
fun DateField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Brand.Perforation, RoundedCornerShape(6.dp))
            .pressClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Event, null, tint = Brand.Gold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = PkType.tiny, color = Brand.TextMuted)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Brand.Text)
        }
        Text("ИЗМЕНИТЬ", style = PkType.tiny, color = Brand.Accent)
    }
}

/** Календарь в цветах билета; будущие даты выбрать нельзя. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * DAY_MS,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / DAY_MS <= today.toEpochDay()
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    val colors = DatePickerDefaults.colors(
        containerColor = Brand.Ticket,
        titleContentColor = Brand.TextMuted,
        headlineContentColor = Brand.Text,
        weekdayContentColor = Brand.TextMuted,
        navigationContentColor = Brand.Text,
        yearContentColor = Brand.Text,
        currentYearContentColor = Brand.Gold,
        selectedYearContainerColor = Brand.Gold,
        selectedYearContentColor = Brand.OnGold,
        dayContentColor = Brand.Text,
        selectedDayContainerColor = Brand.Gold,
        selectedDayContentColor = Brand.OnGold,
        todayContentColor = Brand.Gold,
        todayDateBorderColor = Brand.Gold,
        dividerColor = Brand.Perforation,
    )
    // Приложение целиком на русском — календарь тоже, даже если язык системы другой
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextAction("Готово", {
                state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / DAY_MS)) }
                onDismiss()
            }, color = Brand.Gold)
        },
        dismissButton = { TextAction("Отмена", onDismiss, color = Brand.TextMuted) },
        colors = colors,
    ) {
        // Окно диалога задаёт свою конфигурацию, поэтому язык подменяем уже внутри него
        val ruConfig = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag("ru")) }
        CompositionLocalProvider(LocalConfiguration provides ruConfig) {
            DatePicker(
                state = state,
                colors = colors,
                showModeToggle = false,
                title = { Text("ДАТА ПРОСМОТРА", style = PkType.label, color = Brand.TextMuted, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            )
        }
    }
}

private const val DAY_MS = 24 * 60 * 60 * 1000L
