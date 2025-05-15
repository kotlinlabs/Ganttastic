package io.github.kotlinlabs.ganttly.chart

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.kotlinlabs.ganttly.models.GanttTask
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

@Composable
fun TaskTooltip(
    task: GanttTask,
    position: Offset,
    modifier: Modifier = Modifier,
    allTasks: List<GanttTask> = emptyList()
) {
    // For viewport boundary detection
    val density = LocalDensity.current
    val tooltipWidth = 220.dp

    // Dynamic height based on number of dependencies
    val baseHeight = 180.dp
    val dependencyHeight = if (task.dependencies.isNotEmpty())
        (20.dp * task.dependencies.size.coerceAtMost(4) + 20.dp) else 0.dp
    val tooltipHeight = baseHeight + dependencyHeight

    val tooltipWidthPx = with(density) { tooltipWidth.toPx() }
    val tooltipHeightPx = with(density) { tooltipHeight.toPx() }

    // Get viewport size
    var viewportSize by remember { mutableStateOf(Size.Zero) }

    // Position calculations
    val cursorOffset = 12.dp
    val cursorOffsetPx = with(density) { cursorOffset.toPx() }

    // Calculate available space in different directions
    val spaceToRight = viewportSize.width - position.x
    val spaceToLeft = position.x
    val spaceAbove = position.y
    val spaceBelow = viewportSize.height - position.y

    // Determine best placement
    val tooltipX = with(density) {
        when {
            // If there's enough space to the right, place tooltip to the right of cursor
            spaceToRight >= tooltipWidthPx + cursorOffsetPx -> {
                position.x + cursorOffsetPx
            }
            // Otherwise, place to the left if there's space
            spaceToLeft >= tooltipWidthPx + cursorOffsetPx -> {
                position.x - tooltipWidthPx - cursorOffsetPx
            }
            // Fallback: center tooltip on cursor, may extend beyond edges
            else -> {
                position.x - (tooltipWidthPx / 2)
            }
        }.toDp()
    }

    val tooltipY = with(density) {
        when {
            // If there's enough space above, place tooltip above cursor
            spaceAbove >= tooltipHeightPx + cursorOffsetPx -> {
                position.y - tooltipHeightPx - cursorOffsetPx
            }
            // Otherwise, place below if there's space
            spaceBelow >= tooltipHeightPx + cursorOffsetPx -> {
                position.y + cursorOffsetPx
            }
            // Fallback: center tooltip vertically on cursor
            else -> {
                position.y - (tooltipHeightPx / 2)
            }
        }.toDp()
    }

    // Create a map of dependency IDs to task names
    val dependencyNames = remember(task.dependencies, allTasks) {
        task.dependencies.mapNotNull { depId ->
            allTasks.find { it.id == depId }?.name
        }
    }

    // Outer box for measuring viewport size
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewportSize = Size(size.width.toFloat(), size.height.toFloat())
            }
            .zIndex(100f)
    ) {
        // Tooltip surface
        Surface(
            modifier = modifier
                .width(tooltipWidth)
                .offset(x = tooltipX, y = tooltipY),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = task.color
                )

                Spacer(Modifier.height(8.dp))

                // Duration row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Duration",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = formatDuration(task.duration),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Start",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDateTime(task.startDate.toLocalDateTime(TimeZone.currentSystemDefault())),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "End",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDateTime(task.endDate.toLocalDateTime(TimeZone.currentSystemDefault())),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${(task.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Progress bar
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = task.color
                )

                if (dependencyNames.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Dependencies",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Bulleted list of dependencies
                    Column(
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        dependencyNames.forEach { name ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(end = 4.dp, top = 0.dp)
                                )
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats a duration into a human-readable format
 * - For durations < 1 minute: shows "X seconds"
 * - For durations >= 1 minute and <= 59 minutes: shows in mm:ss format
 * - For durations >= 1 hour shows in hh:mm:ss format
 */
fun formatDuration(duration: Duration): String {
    // For durations less than a minute
    if (duration.inWholeMinutes < 1) {
        val seconds = duration.inWholeSeconds
        return "$seconds second${if (seconds != 1L) "s" else ""}"
    }

    // Extract hours, minutes, seconds
    val hours = duration.inWholeHours
    val minutes = (duration.inWholeMinutes % 60)
    val seconds = (duration.inWholeSeconds % 60)

    return buildString {
        // For durations between 1 minute and 59 minutes: mm:ss format
        if (hours == 0L) {
            append(minutes.toString().padStart(2, '0'))
            append(":")
            append(seconds.toString().padStart(2, '0'))
        }
        // For durations 1 hour or more: hh:mm:ss format
        else {
            append(hours.toString().padStart(2, '0'))
            append(":")
            append(minutes.toString().padStart(2, '0'))
            append(":")
            append(seconds.toString().padStart(2, '0'))
        }
    }
}