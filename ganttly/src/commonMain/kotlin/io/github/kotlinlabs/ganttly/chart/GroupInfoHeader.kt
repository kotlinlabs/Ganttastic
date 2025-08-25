package io.github.kotlinlabs.ganttly.chart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kotlinlabs.ganttly.styles.GanttTheme

/**
 * Displays group information as a header showing all task groups with their color indicators and counts.
 *
 * @param groupInfo Map of group names to their assigned colors
 * @param taskCountProvider Function that returns the number of tasks for a specific group
 * @param modifier Modifier for the composable
 */
@Composable
fun GroupInfoHeader(
    groupInfo: Map<String, Color>,
    taskCountProvider: (String) -> Int,
    modifier: Modifier = Modifier
) {
    val theme = GanttTheme.current
    Column(
        modifier = modifier
            .padding(12.dp)
            .wrapContentHeight()
    ) {
        Text(
            theme.naming.taskGroups,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (groupInfo.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                groupInfo.forEach { (group, color) ->
                    Surface(
                        color = color.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(theme.styles.groupTagShape),
                        border = BorderStroke(theme.styles.groupTagBorderWidth, color.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = theme.styles.groupTagPadding, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color, shape = CircleShape)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = group,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            val taskCount = taskCountProvider(group)
                            Text(
                                text = "($taskCount)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                theme.naming.noGroupsMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}