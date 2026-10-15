package com.itmda3b33.eats.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itmda3b33.eats.ui.theme.LocalDarkTheme

/** Fork, "eats" and knife in a row. Used at the top of the auth screens. */
@Composable
fun EatsWordmark(modifier: Modifier = Modifier, height: Dp = 40.dp) {
    val accent = MaterialTheme.colorScheme.primary
    val textColor = if (LocalDarkTheme.current) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground
    Row(
        modifier = modifier.semantics { contentDescription = "eats" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(height * 0.35f),
    ) {
        Canvas(Modifier.width(height * 0.25f).height(height)) { drawFork(accent) }
        Text(
            text = "eats",
            color = textColor,
            fontSize = (height.value * 0.9f).sp,
            fontWeight = FontWeight.Black,
        )
        Canvas(Modifier.width(height * 0.22f).height(height)) { drawKnife(accent) }
    }
}

/** A plate with "eats" on it between a fork and knife, with steam. Used on the splash and home screens. */
@Composable
fun EatsPlateLogo(modifier: Modifier = Modifier, plateSize: Dp = 120.dp) {
    val dark = LocalDarkTheme.current
    val rim = MaterialTheme.colorScheme.primary
    val plate = if (dark) Color(0xFFA78BFA) else Color(0xFFFB923C)
    val utensils = if (dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
    val ink = Color(0xFF111111)

    Row(
        modifier = modifier.semantics { contentDescription = "eats logo" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(plateSize * 0.15f),
    ) {
        Canvas(Modifier.width(plateSize * 0.2f).height(plateSize * 0.8f)) { drawFork(utensils) }
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(plateSize)) {
                val radius = size.minDimension / 2
                drawCircle(rim, radius = radius * 0.86f, center = center.copy(y = center.y + radius * 0.1f))
                drawCircle(plate, radius = radius * 0.72f, center = center.copy(y = center.y + radius * 0.1f))
                drawCircle(ink, radius = radius * 0.72f, center = center.copy(y = center.y + radius * 0.1f), style = Stroke(width = radius * 0.03f))
                drawSteam(rim, radius)
            }
            Text(
                text = "eats",
                color = ink,
                fontSize = (plateSize.value * 0.24f).sp,
                fontWeight = FontWeight.Black,
            )
        }
        Canvas(Modifier.width(plateSize * 0.18f).height(plateSize * 0.8f)) { drawKnife(utensils) }
    }
}

private fun DrawScope.drawFork(color: Color) {
    val w = size.width
    val h = size.height
    val tine = w * 0.2f
    // Three tines and the neck.
    for (i in 0..2) {
        drawRoundRect(color, topLeft = Offset(i * (w - tine) / 2, 0f), size = Size(tine, h * 0.3f), cornerRadius = CornerRadius(tine / 2))
    }
    drawRoundRect(color, topLeft = Offset(0f, h * 0.22f), size = Size(w, h * 0.12f), cornerRadius = CornerRadius(w * 0.2f))
    // Handle.
    drawRoundRect(color, topLeft = Offset(w * 0.35f, h * 0.3f), size = Size(w * 0.3f, h * 0.7f), cornerRadius = CornerRadius(w * 0.15f))
}

private fun DrawScope.drawKnife(color: Color) {
    val w = size.width
    val h = size.height
    val blade = Path().apply {
        moveTo(w * 0.15f, 0f)
        cubicTo(w * 1.1f, h * 0.05f, w * 1.05f, h * 0.4f, w * 0.85f, h * 0.5f)
        lineTo(w * 0.15f, h * 0.5f)
        close()
    }
    drawPath(blade, color)
    drawRoundRect(color, topLeft = Offset(w * 0.15f, h * 0.45f), size = Size(w * 0.6f, h * 0.55f), cornerRadius = CornerRadius(w * 0.3f))
}

private fun DrawScope.drawSteam(color: Color, radius: Float) {
    val stroke = Stroke(width = radius * 0.05f)
    for (i in -1..1) {
        val x = center.x + i * radius * 0.18f
        val top = center.y - radius
        val path = Path().apply {
            moveTo(x, top - radius * 0.02f)
            cubicTo(x - radius * 0.08f, top + radius * 0.04f, x + radius * 0.08f, top + radius * 0.1f, x, top + radius * 0.16f)
        }
        drawPath(path, color, style = stroke)
    }
}
