package com.egrmeister.aquashelf.ui.shelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.egrmeister.aquashelf.domain.entries.ContainerType
import com.egrmeister.aquashelf.ui.theme.ShelfColors

/**
 * Stylized vector containers drawn with native shapes. Every logged icon is drawn FILLED: it means
 * an amount was recorded, not that a physical container is full. Icon height is the same for all
 * amounts, so it never implies a proportional measurement.
 */
@Composable
fun ContainerIcon(type: ContainerType, modifier: Modifier = Modifier, height: Dp = 56.dp) {
    Canvas(modifier.size(width = height * 0.78f, height = height)) {
        when (type) {
            ContainerType.GLASS -> drawGlass()
            ContainerType.BOTTLE -> drawBottle()
            ContainerType.CUSTOM -> drawVessel()
        }
    }
}

private fun DrawScope.strokeWidth() = (size.minDimension * 0.045f).coerceAtLeast(1.5f)

private fun DrawScope.drawGlass() {
    val w = size.width
    val h = size.height
    val top = h * 0.12f
    val bottom = h * 0.98f
    val inset = w * 0.16f
    val outline = Path().apply {
        moveTo(w * 0.06f, top)
        lineTo(w * 0.94f, top)
        lineTo(w - inset, bottom)
        lineTo(inset, bottom)
        close()
    }
    drawPath(outline, ShelfColors.GlassLight)
    // Water body: from 25% down to the base, following the tapered sides.
    val waterTop = top + (bottom - top) * 0.22f
    val t = (waterTop - top) / (bottom - top)
    val leftAtWater = w * 0.06f + (inset - w * 0.06f) * t
    val rightAtWater = w * 0.94f - (w * 0.94f - (w - inset)) * t
    val water = Path().apply {
        moveTo(leftAtWater, waterTop)
        lineTo(rightAtWater, waterTop)
        lineTo(w - inset, bottom)
        lineTo(inset, bottom)
        close()
    }
    drawPath(water, ShelfColors.GlassFill)
    // Highlight
    drawLine(
        color = ShelfColors.GlassLight,
        start = Offset(leftAtWater + w * 0.12f, waterTop + h * 0.08f),
        end = Offset(inset + w * 0.1f, bottom - h * 0.1f),
        strokeWidth = strokeWidth() * 1.4f,
    )
    drawPath(outline, ShelfColors.GlassLine, style = Stroke(width = strokeWidth(), join = StrokeJoin.Round))
}

private fun DrawScope.drawBottle() {
    val w = size.width
    val h = size.height
    val sw = strokeWidth()
    val capW = w * 0.34f
    val capLeft = (w - capW) / 2f
    val cap = RoundRect(capLeft, 0f, capLeft + capW, h * 0.1f, CornerRadius(sw, sw))
    drawPath(Path().apply { addRoundRect(cap) }, ShelfColors.BottleLine)

    val neckW = w * 0.28f
    val neckLeft = (w - neckW) / 2f
    val shoulder = h * 0.3f
    val left = w * 0.1f
    val right = w * 0.9f
    val bottom = h * 0.98f
    val radius = w * 0.12f
    val body = Path().apply {
        moveTo(neckLeft, h * 0.1f)
        lineTo(neckLeft + neckW, h * 0.1f)
        lineTo(neckLeft + neckW, h * 0.18f)
        cubicTo(neckLeft + neckW, h * 0.24f, right, h * 0.22f, right, shoulder)
        lineTo(right, bottom - radius)
        quadraticTo(right, bottom, right - radius, bottom)
        lineTo(left + radius, bottom)
        quadraticTo(left, bottom, left, bottom - radius)
        lineTo(left, shoulder)
        cubicTo(left, h * 0.22f, neckLeft, h * 0.24f, neckLeft, h * 0.18f)
        close()
    }
    drawPath(body, ShelfColors.BottleFill)
    // Label band and highlight
    drawRect(
        color = ShelfColors.BottleLight,
        topLeft = Offset(left + sw, h * 0.52f),
        size = Size(right - left - 2 * sw, h * 0.16f),
        alpha = 0.75f,
    )
    drawLine(
        color = ShelfColors.BottleLight,
        start = Offset(left + w * 0.12f, shoulder + h * 0.04f),
        end = Offset(left + w * 0.12f, h * 0.48f),
        strokeWidth = sw * 1.4f,
    )
    drawPath(body, ShelfColors.BottleLine, style = Stroke(width = sw, join = StrokeJoin.Round))
}

/** Neutral vessel (a simple jar) for Custom entries. */
private fun DrawScope.drawVessel() {
    val w = size.width
    val h = size.height
    val sw = strokeWidth()
    val rimTop = h * 0.14f
    val rim = RoundRect(w * 0.16f, rimTop, w * 0.84f, rimTop + h * 0.1f, CornerRadius(sw * 1.5f, sw * 1.5f))
    val body = Path().apply {
        moveTo(w * 0.2f, rimTop + h * 0.1f)
        lineTo(w * 0.8f, rimTop + h * 0.1f)
        cubicTo(w * 0.98f, h * 0.36f, w * 0.98f, h * 0.9f, w * 0.78f, h * 0.98f)
        lineTo(w * 0.22f, h * 0.98f)
        cubicTo(w * 0.02f, h * 0.9f, w * 0.02f, h * 0.36f, w * 0.2f, rimTop + h * 0.1f)
        close()
    }
    drawPath(body, ShelfColors.VesselFill)
    drawLine(
        color = ShelfColors.VesselLight,
        start = Offset(w * 0.28f, h * 0.42f),
        end = Offset(w * 0.28f, h * 0.78f),
        strokeWidth = sw * 1.4f,
    )
    drawPath(body, ShelfColors.VesselLine, style = Stroke(width = sw, join = StrokeJoin.Round))
    val rimPath = Path().apply { addRoundRect(rim) }
    drawPath(rimPath, ShelfColors.VesselLight)
    drawPath(rimPath, ShelfColors.VesselLine, style = Stroke(width = sw, join = StrokeJoin.Round))
}
