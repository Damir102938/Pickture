package com.moviepicker.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Цвет оценки: 7+ — зелёный, 5–6 — янтарный, ниже — приглушённый. */
fun ratingColor(rating: Int): Color = when {
    rating >= 7 -> Brand.MatchHigh
    rating >= 5 -> Brand.MatchMid
    else -> Brand.MatchLow
}

/**
 * Уточнение оценки: один ряд «мест в зале» из [range] (после лайка 6–10, после «Не моё» 1–5).
 * Повторное нажатие на выбранное место снимает уточнение.
 */
@Composable
fun RatingRow(range: IntRange, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        range.forEach { value ->
            RatingSeat(
                value = value,
                color = ratingColor(if (selected > 0) selected else value),
                active = selected in range && value <= selected,
                current = value == selected,
                onClick = { onSelect(if (value == selected) 0 else value) },
                modifier = Modifier.weight(1f),
            )
        }
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
            .aspectRatio(1.6f)
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
