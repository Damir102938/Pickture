package com.moviepicker.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Шапка корневого экрана: золотой надстрочник и заголовок капслоком. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = "Pickture · афиша",
    subtitle: String? = null,
    inset: Dp = 16.dp,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(start = inset, end = inset / 2, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (kicker != null) Text(kicker.uppercase(), style = PkType.kicker, color = Brand.Gold)
            Text(title.uppercase(), style = PkType.screenTitle, color = Brand.Text)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.TextMuted)
            }
        }
        trailing?.invoke(this)
    }
}

/** Заголовок раздела: «01  ЦИФРОВЫЕ РЕЛИЗЫ - - - - ВСЕ →». */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    number: Int? = null,
    subtitle: String? = null,
    onAll: (() -> Unit)? = null,
    inset: Dp = 16.dp,
) {
    Column(modifier.fillMaxWidth().padding(start = inset, end = if (onAll != null) (inset - 10.dp).coerceAtLeast(0.dp) else inset)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (number != null) {
                Text("%02d".format(number), style = PkType.number, color = Brand.Accent)
                Spacer(Modifier.width(8.dp))
            }
            Text(title.uppercase(), style = PkType.section, color = Brand.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
            Perforation(Modifier.weight(1f))
            if (onAll != null) {
                Spacer(Modifier.width(2.dp))
                TextAction("Все →", onAll)
            }
        }
        if (subtitle != null) {
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.TextMuted,
                modifier = Modifier.padding(start = if (number != null) 25.dp else 0.dp).offset(y = (-6).dp),
            )
        }
    }
}

/** Шапка вложенного экрана: жетон «назад», название и пунктир снизу. */
@Composable
fun PicktureTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                BackButton(onBack)
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.width(4.dp))
            }
            Text(
                title.uppercase(), style = PkType.barTitle, color = Brand.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            actions?.invoke(this)
        }
        Perforation(Modifier.padding(horizontal = 12.dp))
    }
}

data class NavEntry(val label: String, val icon: ImageVector)

/**
 * Нижняя навигация — билетная лента. Золотой «штамп» переезжает к выбранной вкладке
 * с пружинкой и покачиванием, иконка выбранной вкладки подпрыгивает.
 */
@Composable
fun TicketNavBar(
    items: List<NavEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(TicketShape(notchRadius = 7.dp, cornerRadius = 10.dp))
            .background(Brand.Ticket),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
            val itemWidth = maxWidth / items.size
            StampIndicator(selected, itemWidth)
        }
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            items.forEachIndexed { i, item ->
                NavStampItem(item, i == selected, Modifier.weight(1f)) { onSelect(i) }
            }
        }
    }
}

@Composable
private fun StampIndicator(selected: Int, itemWidth: Dp) {
    val x by animateDpAsState(
        itemWidth * selected.coerceAtLeast(0),
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "stampX",
    )
    // Покачивание при переезде: штамп наклоняется по ходу движения и возвращается к −3°
    val tilt = remember { Animatable(-3f) }
    var previous by remember { mutableIntStateOf(selected) }
    LaunchedEffect(selected) {
        val dir = if (selected > previous) 1 else -1
        previous = selected
        tilt.animateTo(-3f + 9f * dir, tween(110))
        tilt.animateTo(-3f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
    }
    if (selected < 0) return
    Box(
        Modifier
            .offset(x = x)
            .width(itemWidth)
            .fillMaxHeight()
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .graphicsLayer { rotationZ = tilt.value }
            .border(1.5.dp, Brand.Gold, RoundedCornerShape(5.dp)),
    )
}

@Composable
private fun NavStampItem(item: NavEntry, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color by animateColorAsState(if (active) Brand.Gold else Brand.TextMuted, tween(200), label = "navColor")
    val bounce = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) coroutineScope {
            launch {
                bounce.snapTo(0f)
                bounce.animateTo(-5f, tween(110))
                bounce.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    Column(
        modifier.fillMaxHeight().pressClickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, null, tint = color, modifier = Modifier.size(20.dp).graphicsLayer { translationY = bounce.value * density })
        Spacer(Modifier.height(3.dp))
        Text(item.label.uppercase(), style = PkType.tiny, color = color, maxLines = 1)
    }
}
