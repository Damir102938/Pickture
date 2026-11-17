package com.moviepicker.desktop.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val RailItemHeight = 68.dp
private val LogoHeight = 96.dp

/**
 * Боковая навигация ПК — вертикальный билет: сверху корешок с логотипом,
 * ниже вкладки. Золотой штамп переезжает к выбранной вкладке с пружинкой и покачиванием.
 */
@Composable
fun TicketRail(items: List<NavEntry>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(104.dp)
            .fillMaxHeight()
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
            .clip(TicketShape(notchRadius = 7.dp, cornerRadius = 10.dp, notchOffset = LogoHeight))
            .background(Brand.Ticket),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.height(LogoHeight).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource("pickture.png"), null, Modifier.size(40.dp))
            Spacer(Modifier.height(6.dp))
            Text("PICKTURE", style = PkType.kicker.copy(letterSpacing = PkType.kicker.letterSpacing * 0.6f), color = Brand.Gold)
        }
        Perforation(inset = 8.dp)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth()) {
            RailStamp(selected)
            Column {
                items.forEachIndexed { i, item -> RailItem(item, i == selected) { onSelect(i) } }
            }
        }
    }
}

@Composable
private fun RailStamp(selected: Int) {
    val y by animateDpAsState(
        RailItemHeight * selected.coerceAtLeast(0),
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "railY",
    )
    val tilt = remember { Animatable(-3f) }
    var previous by remember { mutableIntStateOf(selected) }
    LaunchedEffect(selected) {
        val dir = if (selected > previous) 1 else -1
        previous = selected
        tilt.animateTo(-3f + 8f * dir, tween(110))
        tilt.animateTo(-3f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
    }
    if (selected < 0) return
    Box(
        Modifier
            .offset(y = y)
            .fillMaxWidth()
            .height(RailItemHeight)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .graphicsLayer { rotationZ = tilt.value }
            .border(1.5.dp, Brand.Gold, RoundedCornerShape(5.dp)),
    )
}

@Composable
private fun RailItem(item: NavEntry, active: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (active) Brand.Gold else Brand.TextMuted, tween(200), label = "railColor")
    val bounce = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) coroutineScope {
            launch {
                bounce.snapTo(0f)
                bounce.animateTo(-4f, tween(110))
                bounce.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().height(RailItemHeight).pressClickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, null, tint = color, modifier = Modifier.size(22.dp).graphicsLayer { translationY = bounce.value * density })
        Spacer(Modifier.height(4.dp))
        Text(item.label.uppercase(), style = PkType.tiny, color = color, maxLines = 1)
    }
}
