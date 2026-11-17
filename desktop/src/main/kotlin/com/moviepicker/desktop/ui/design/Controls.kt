package com.moviepicker.desktop.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Главная кнопка — золотой билет с вырезами по бокам. */
@Composable
fun MarqueeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(TicketShape(notchRadius = 5.dp, cornerRadius = 6.dp))
            .background(Brand.Gold)
            .pressClickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            MarqueeLoader(color = Brand.OnGold, dot = 5.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = Brand.OnGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = PkType.label, color = Brand.OnGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Второстепенная кнопка — контурный билет. */
@Composable
fun TicketOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Brand.Accent,
) {
    Row(
        modifier
            .heightIn(min = 40.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .pressClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), style = PkType.label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Текстовое действие: «ВСЕ →», «ПОВТОРИТЬ». */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Brand.Accent,
    trailingIcon: ImageVector? = null,
) {
    Row(
        modifier
            .heightIn(min = 40.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(4.dp))
            .pressClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = PkType.label, color = color)
        if (trailingIcon != null) {
            Spacer(Modifier.width(4.dp))
            Icon(trailingIcon, null, tint = color, modifier = Modifier.size(15.dp))
        }
    }
}

/** Переключатель вариантов — полоса билета, выбранная часть «прокомпостирована» золотом. */
@Composable
fun TicketToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Brand.Perforation, RoundedCornerShape(6.dp)),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            val bg by animateColorAsState(if (active) Brand.Gold else Color.Transparent, tween(220), label = "toggleBg")
            val fg by animateColorAsState(if (active) Brand.OnGold else Brand.TextMuted, tween(220), label = "toggleFg")
            if (i > 0) VerticalPerforation(inset = 6.dp)
            Box(
                Modifier.weight(1f).fillMaxHeight().background(bg)
                    .pressClickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = PkType.label, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Метка-«место в зале» для жанров и сортировки (вместо FilterChip). */
@Composable
fun SeatTag(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) Brand.Indigo.copy(alpha = 0.28f) else Color.Transparent, tween(180), label = "seatBg")
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier
            .height(36.dp)
            .clip(shape)
            .then(if (selected) Modifier.border(1.5.dp, Brand.Accent, shape) else Modifier.dashedBorder(Brand.Perforation, 1.dp, 4.dp))
            .background(bg)
            .pressClickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = Brand.Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = PkType.label.copy(letterSpacing = PkType.label.letterSpacing * 0.5f), color = if (selected) Brand.Text else Brand.TextMuted)
    }
}

/** Наклонная печать: «СОВПАДЕНИЕ 90%». При появлении «пробивается» на бумагу. */
@Composable
fun Stamp(text: String, color: Color, modifier: Modifier = Modifier, angle: Float = -4f) {
    val scale = remember { Animatable(1.35f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(120))
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value; rotationZ = angle }
            .border(1.5.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text.uppercase(), style = PkType.label, color = color)
    }
}

/** «Пробивание» штампа при выборе: короткая пружина масштаба и наклона. */
@Composable
private fun stampPunch(selected: Boolean): Pair<Float, Float> {
    val scale = remember { Animatable(1f) }
    val angle = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            scale.snapTo(1.18f)
            angle.snapTo(-6f)
            coroutineScope {
                launch { scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
                launch { angle.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
            }
        }
    }
    return scale.value to angle.value
}

/** Кнопка оценки в карточке фильма — отрывной талон с подписью. */
@Composable
fun StampButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (scale, angle) = stampPunch(selected)
    val tint = if (selected) color else Brand.Text
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .heightIn(min = 60.dp)
            .clip(shape)
            .then(if (selected) Modifier.border(1.5.dp, color, shape).background(color.copy(alpha = 0.12f)) else Modifier.dashedBorder(Brand.Perforation, 1.dp, 6.dp))
            .pressClickable(role = Role.Checkbox, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp).graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = angle })
        Spacer(Modifier.height(4.dp))
        Text(label.uppercase(), style = PkType.tiny, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Компактная кнопка оценки для списков — квадратный талон с иконкой. */
@Composable
fun StampIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Brand.Text,
) {
    Box(
        modifier
            .size(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .dashedBorder(Brand.Perforation, 1.dp, 6.dp)
            .pressClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = color, modifier = Modifier.size(18.dp))
    }
}

/** Кнопка «Назад» — квадратный жетон. [onImage] — поверх картинки (тёмная подложка). */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier, onImage: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(38.dp)
            .clip(shape)
            .then(
                if (onImage) Modifier.background(Color.Black.copy(alpha = 0.55f))
                else Modifier.border(1.dp, Brand.Perforation, shape)
            )
            .pressClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Загрузка — бегущие огоньки, как на вывеске кинотеатра. */
@Composable
fun MarqueeLoader(modifier: Modifier = Modifier, color: Color = Brand.Gold, dot: Dp = 8.dp, count: Int = 5) {
    val transition = rememberInfiniteTransition(label = "marquee")
    val phase by transition.animateFloat(
        0f, count.toFloat(),
        infiniteRepeatable(tween(count * 160, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(dot * 0.6f), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            // Яркость огонька падает по мере удаления от «бегущей» точки
            val d = ((phase - i + count) % count)
            val a = (1f - d / count).coerceIn(0.2f, 1f)
            Box(Modifier.size(dot).alpha(a).clip(CircleShape).background(color))
        }
    }
}

/** Диалог в форме билета. */
@Composable
fun TicketDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: (@Composable () -> Unit)?,
    dismiss: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TicketShape(notchRadius = 9.dp, cornerRadius = 12.dp, notchOffset = 61.dp),
        containerColor = Brand.Ticket,
        title = {
            Column {
                Text(title.uppercase(), style = PkType.barTitle, color = Brand.Text)
                Spacer(Modifier.height(14.dp))
                Perforation()
            }
        },
        text = content,
        confirmButton = { confirm?.invoke() },
        dismissButton = dismiss,
    )
}

/** Поля билета: «ГОД 2014», «ХРОН. 2:49». */
@Composable
fun TicketField(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Brand.Text) {
    Column(
        modifier
            .border(1.dp, Brand.Perforation, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label.uppercase(), style = PkType.tiny, color = Brand.TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, maxLines = 1)
    }
}


/** Цвета текстовых полей в стиле билета: золотая рамка в фокусе, «бумажный» фон. */
@Composable
fun ticketFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand.Gold,
    unfocusedBorderColor = Brand.Perforation,
    cursorColor = Brand.Gold,
    focusedLabelColor = Brand.Gold,
    focusedContainerColor = Brand.Ticket,
    unfocusedContainerColor = Brand.Ticket,
)
