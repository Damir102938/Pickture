package com.moviepicker.desktop.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Форма кинобилета: скруглённый прямоугольник с двумя полукруглыми вырезами
 * по линии отрыва.
 *
 * @param vertical `false` — линия отрыва горизонтальная, вырезы на левом и правом краях;
 *   `true` — линия вертикальная, вырезы сверху и снизу.
 * @param notchOffset где проходит линия отрыва: от верха (или от левого края) либо,
 *   при [fromEnd], от низа (от правого края). `null` — посередине.
 */
class TicketShape(
    private val notchRadius: Dp = 6.dp,
    private val cornerRadius: Dp = 8.dp,
    private val notchOffset: Dp? = null,
    private val fromEnd: Boolean = false,
    private val vertical: Boolean = false,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { notchRadius.toPx() }
        val c = with(density) { cornerRadius.toPx() }
        val base = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(c))) }
        val length = if (vertical) size.width else size.height
        val at = notchOffset?.let { with(density) { it.toPx() } }?.let { if (fromEnd) length - it else it } ?: (length / 2)
        val notches = Path().apply {
            if (vertical) {
                addOval(Rect(Offset(at, 0f), r))
                addOval(Rect(Offset(at, size.height), r))
            } else {
                addOval(Rect(Offset(0f, at), r))
                addOval(Rect(Offset(size.width, at), r))
            }
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, base, notches))
    }
}

private fun dash(density: Float) = PathEffect.dashPathEffect(floatArrayOf(5f * density, 4f * density))

/** Горизонтальная линия перфорации. [inset] — отступ от краёв, чтобы линия не заходила в вырезы. */
@Composable
fun Perforation(modifier: Modifier = Modifier, color: Color = Brand.Perforation, inset: Dp = 0.dp) {
    Canvas(modifier.fillMaxWidth().height(1.5.dp)) {
        val i = inset.toPx()
        drawLine(
            color, Offset(i, size.height / 2), Offset(size.width - i, size.height / 2),
            strokeWidth = size.height, pathEffect = dash(density),
        )
    }
}

/** Вертикальная линия перфорации. */
@Composable
fun VerticalPerforation(modifier: Modifier = Modifier, color: Color = Brand.Perforation, inset: Dp = 0.dp) {
    Canvas(modifier.fillMaxHeight().width(1.5.dp)) {
        val i = inset.toPx()
        drawLine(
            color, Offset(size.width / 2, i), Offset(size.width / 2, size.height - i),
            strokeWidth = size.width, pathEffect = dash(density),
        )
    }
}

/** Пунктирная рамка, как у отрывного талона. */
fun Modifier.dashedBorder(color: Color, width: Dp = 1.dp, cornerRadius: Dp = 6.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(width = w, pathEffect = dash(density)),
    )
}

/** Нажатие с лёгким «продавливанием» — как будто компостируешь билет. */
fun Modifier.pressClickable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), enabled = enabled, role = role, onClick = onClick)
}
