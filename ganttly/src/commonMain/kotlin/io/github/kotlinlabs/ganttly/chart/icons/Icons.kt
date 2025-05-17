package io.github.kotlinlabs.ganttly.chart.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun CircleIcon(filled: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .then(
                if (filled) {
                    Modifier.background(tint, CircleShape)
                } else {
                    Modifier
                        .border(1.5.dp, tint, CircleShape)
                        .background(Color.Transparent, CircleShape)
                }
            )
    )
}

@Composable
fun ArrowDownIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = size.width / 8

        // Draw down arrow (vertical line with angled lines at bottom)
        drawLine(
            color = color,
            start = Offset(width / 2, height / 4),
            end = Offset(width / 2, height * 3 / 4),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Left angle
        drawLine(
            color = color,
            start = Offset(width / 2, height * 3 / 4),
            end = Offset(width / 4, height / 2),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Right angle
        drawLine(
            color = color,
            start = Offset(width / 2, height * 3 / 4),
            end = Offset(width * 3 / 4, height / 2),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun ArrowRightIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = size.width / 8

        // Draw right arrow (horizontal line with angled lines at right)
        drawLine(
            color = color,
            start = Offset(width / 4, height / 2),
            end = Offset(width * 3 / 4, height / 2),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Top angle
        drawLine(
            color = color,
            start = Offset(width * 3 / 4, height / 2),
            end = Offset(width / 2, height / 4),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Bottom angle
        drawLine(
            color = color,
            start = Offset(width * 3 / 4, height / 2),
            end = Offset(width / 2, height * 3 / 4),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
