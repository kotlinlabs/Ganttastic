package io.github.kotlinlabs.ganttly.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.kotlinlabs.ganttly.models.GanttTask
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration


@Composable
fun TaskTooltip(
    task: GanttTask,
    position: Offset,
    allTasks: List<GanttTask>,
    layoutInfo: LazyListLayoutInfo? = null // We'll use this to calculate positioning
) {
    val tooltipWidth = 250.dp
    val tooltipSpacing = 12.dp // Space between cursor and tooltip

    val density = LocalDensity.current

    // Convert to pixels for calculations
    val tooltipWidthPx = with(density) { tooltipWidth.toPx() }
    val tooltipHeightPx = 180f // Estimated height in pixels
    val tooltipSpacingPx = with(density) { tooltipSpacing.toPx() }

    // Get header height (estimate if not available)
    val headerHeightPx = with(density) { DEFAULT_HEADER_HEIGHT_DP.dp.toPx() }

    // Use the layout info to get actual viewport size
    val viewportHeight = layoutInfo?.viewportSize?.height?.toFloat() ?: 600f
    val viewportWidth = layoutInfo?.viewportEndOffset?.toFloat() ?: 1000f

    // Calculate tooltip position with smart placement
    val tooltipPos = calculateTooltipPosition(
        cursorPos = position,
        tooltipWidthPx = tooltipWidthPx,
        tooltipHeightPx = tooltipHeightPx,
        spacingPx = tooltipSpacingPx,
        headerHeightPx = headerHeightPx,
        windowWidth = viewportWidth,
        windowHeight = viewportHeight
    )

    val xPos = with(density) { tooltipPos.x.toDp() }
    val yPos = with(density) { tooltipPos.y.toDp() }

    // Format dates for display
    val startTimeFormatted = formatTaskDate(task.startDate)
    val endTimeFormatted = formatTaskDate(task.endDate)
    val durationFormatted = formatDuration(task.duration)

    // Get dependency tasks
    val dependencyTasks = task.dependencies.mapNotNull { depId ->
        allTasks.find { it.id == depId }
    }

    Box(
        modifier = Modifier
            .width(tooltipWidth)
            .absoluteOffset(x = xPos, y = yPos)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(8.dp),
                spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            .padding(12.dp)
            .zIndex(10f) // Ensure tooltip is above other elements
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Task name with color indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(task.color, shape = CircleShape)
                        .border(0.5.dp, task.color.copy(alpha = 0.5f), shape = CircleShape)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Time details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // Start time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Start:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = startTimeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // End time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "End:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = endTimeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Duration:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = durationFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Progress bar
                if (task.progress > 0f) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Progress:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        LinearProgressIndicator(
                            progress = { task.progress },
                            color = task.color,
                            trackColor = task.color.copy(alpha = 0.2f),
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${(task.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Dependencies
                if (dependencyTasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Dependencies:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // List at most 2 dependencies to keep tooltip compact
                    Column {
                        dependencyTasks.take(2).forEach { depTask ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(depTask.color, shape = CircleShape)
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                Text(
                                    text = depTask.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // If more dependencies exist
                        if (dependencyTasks.size > 2) {
                            Text(
                                text = "... and ${dependencyTasks.size - 2} more",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper function to format Instant to a readable date-time string
fun formatTaskDate(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}:${local.second.toString().padStart(2, '0')}"
}

// Helper function to calculate tooltip position
private fun calculateTooltipPosition(
    cursorPos: Offset,
    tooltipWidthPx: Float,
    tooltipHeightPx: Float,
    spacingPx: Float,
    headerHeightPx: Float,
    windowWidth: Float,
    windowHeight: Float
): Offset {
    // First try below the cursor
    var xPos = cursorPos.x + spacingPx
    var yPos = cursorPos.y + spacingPx

    // Check if tooltip would extend beyond right edge
    if (xPos + tooltipWidthPx > windowWidth) {
        xPos = cursorPos.x - tooltipWidthPx - spacingPx
    }

    // Check if tooltip would extend beyond left edge
    if (xPos < 0) {
        xPos = 0f
    }

    // Check if tooltip would extend beyond bottom edge
    if (yPos + tooltipHeightPx > windowHeight) {
        // Try above the cursor instead
        yPos = cursorPos.y - tooltipHeightPx - spacingPx

        // If still not fitting (would go above header), position at right of cursor
        if (yPos < headerHeightPx) {
            // If we can't place it above, put it at the top of the view but below header
            yPos = headerHeightPx + spacingPx
        }
    }

    return Offset(xPos, yPos)
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