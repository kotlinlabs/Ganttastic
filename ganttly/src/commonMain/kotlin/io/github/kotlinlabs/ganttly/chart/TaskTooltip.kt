package io.github.kotlinlabs.ganttly.chart

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.kotlinlabs.ganttly.chart.icons.CircleIcon
import io.github.kotlinlabs.ganttly.models.GanttTask
import io.github.kotlinlabs.ganttly.styles.GanttTheme
import kotlin.time.Duration

@Composable
fun TaskTooltip(
    task: GanttTask,
    position: Offset,
    allTasks: List<GanttTask>,
    interactionSource: MutableInteractionSource
) {
    val subTaskCount = task.children.size
    val subTasksComplete = task.children.count { it.progress >= 1.0f }
    val theme = GanttTheme.current

    // Fixed size approximation for tooltip (can be refined)
    val tooltipWidth = 300
    val tooltipHeight = 250
    val padding = 10

    // Position tooltip with much larger offset to prevent hover interference
    // Similar to GroupInfoHeader's successful positioning strategy
    val tooltipPosition = IntOffset(
        x = (position.x + padding + 80).toInt(), // Much larger horizontal offset
        y = (position.y + padding - 50).toInt()  // Much larger vertical offset above cursor
    )

    TooltipPopup(
        position = tooltipPosition,
    ) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .widthIn(min = 200.dp, max = 300.dp)
                .hoverable(interactionSource)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Task name
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Task details
                Row {
                    Text(
                        text = "Duration: ",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = formatDuration(task.duration),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Progress
                if (theme.styles.showTaskProgress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Progress: ",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )
                        Text(
                            text = " ${(task.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Subtask info (if present)
                if (task.hasChildren) {
                    HorizontalDivider(
                        Modifier.padding(vertical = 4.dp),
                        DividerDefaults.Thickness,
                        DividerDefaults.color
                    )
                    Text(
                        text = "${theme.naming.subtasks}: $subTasksComplete/$subTaskCount complete",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Subtask breakdown
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        task.children.forEach { subtask ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircleIcon(
                                    filled = subtask.progress >= 1.0f,
                                    tint = if (subtask.progress >= 1.0f)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )

                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = subtask.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Dependencies
                if (task.dependencies.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = DividerDefaults.Thickness,
                        color = DividerDefaults.color
                    )
                    Text(
                        text = "Dependencies:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        task.dependencies.forEach { depId ->
                            val depTask = allTasks.firstOrNull { it.id == depId }
                            if (depTask != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = depTask.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TooltipPopup(
    position: IntOffset,
    content: @Composable () -> Unit
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = position,
        onDismissRequest = { },
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
//            excludeFromSystemGesture = true
        )
    ) {
        Box(
            // Important: this ensures the popup doesn't interfere with mouse events
            modifier = Modifier.semantics {
                // Disable semantics entirely
                contentDescription = ""
                disabled()
            }
        ) {
            content()
        }
    }
}

/**
 * Formats a duration into a human-readable format
 */
/**
 * Format a duration with customizable style.
 *
 * @param duration The duration to format
 * @param style The formatting style to use
 * @return A formatted string representation of the duration
 */
fun formatDuration(duration: Duration, style: DurationFormatStyle = DurationFormatStyle.FULL): String {
    // Extract hours, minutes, seconds
    val hours = duration.inWholeHours
    val minutes = (duration.inWholeMinutes % 60)
    val seconds = (duration.inWholeSeconds % 60)

    return when (style) {
        DurationFormatStyle.COMPACT -> {
            // For compact display like "2h 30m 15s"
            buildString {
                if (hours > 0) append("${hours}h ")
                if (minutes > 0 || hours > 0) append("${minutes}m ")
                append("${seconds}s")
            }.trim()
        }

        DurationFormatStyle.DIGITAL -> {
            // For digital clock format like "02:30:15"
            buildString {
                if (hours > 0) {
                    append(hours.toString().padStart(2, '0'))
                    append(":")
                }
                append(minutes.toString().padStart(2, '0'))
                append(":")
                append(seconds.toString().padStart(2, '0'))
            }
        }

        DurationFormatStyle.FULL -> {
            // For full text format like "2 hours 30 minutes 15 seconds"
            buildString {
                // Add hours if present
                if (hours > 0) {
                    append("$hours hour")
                    if (hours != 1L) append("s")

                    // Only add separator if there are minutes or seconds to follow
                    if (minutes > 0 || seconds > 0) append(" ")
                }

                // Add minutes if present
                if (minutes > 0) {
                    append("$minutes minute")
                    if (minutes != 1L) append("s")

                    // Only add separator if there are seconds to follow
                    if (seconds > 0) append(" ")
                }

                // Add seconds if present or if no hours and minutes (show at least something)
                if (seconds > 0 || (hours == 0L && minutes == 0L)) {
                    append("$seconds second")
                    if (seconds != 1L) append("s")
                }
            }
        }
    }
}

enum class DurationFormatStyle {
    COMPACT,   // 2h 30m 15s
    DIGITAL,   // 02:30:15
    FULL       // 2 hours 30 minutes 15 seconds
}