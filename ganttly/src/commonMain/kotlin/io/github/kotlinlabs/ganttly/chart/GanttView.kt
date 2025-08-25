package io.github.kotlinlabs.ganttly.chart

import TaskBarsAndDependenciesGrid
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.kotlinlabs.ganttly.chart.icons.ArrowDownIcon
import io.github.kotlinlabs.ganttly.chart.icons.ArrowRightIcon
import io.github.kotlinlabs.ganttly.models.Debouncer
import io.github.kotlinlabs.ganttly.models.GanttTask
import io.github.kotlinlabs.ganttly.models.TaskHoverInfo
import io.github.kotlinlabs.ganttly.models.TimelineViewInfo
import io.github.kotlinlabs.ganttly.styles.GanttTheme
import io.github.kotlinlabs.ganttly.styles.GanttThemeConfig
import io.github.kotlinlabs.ganttly.styles.ProvideGanttTheme
import io.github.kotlinlabs.ganttly.styles.TaskGroupColorCoordinator


const val DEFAULT_TASK_LIST_WIDTH_DP = 220
const val DEFAULT_ROW_HEIGHT_DP = 36 // Slightly smaller for more tasks
const val DEFAULT_HEADER_HEIGHT_DP = 40

@Composable
fun GanttChartView(
    state: GanttChartState,
    modifier: Modifier = Modifier,
    taskListWidth: Dp = DEFAULT_TASK_LIST_WIDTH_DP.dp,
    rowHeight: Dp = DEFAULT_ROW_HEIGHT_DP.dp,
    headerHeight: Dp = DEFAULT_HEADER_HEIGHT_DP.dp,
    showTaskList: Boolean = true,
    hoverDelay: Long = 150,
    ganttTheme: GanttThemeConfig = GanttTheme.current
) {
    ProvideGanttTheme(ganttTheme) {
        val currentThemeColors = GanttTheme.current.colors

        // Apply theme colors whenever the theme changes
        LaunchedEffect(currentThemeColors) {
            // Reset the color coordinator when theme changes
            TaskGroupColorCoordinator.reset()

            // Apply the new theme colors to the tasks
            state.applyThemeColors(currentThemeColors)
        }

        // Define test tags for UI testing
        val ganttChartTestTag = "gantt_chart_view"
        val taskListTestTag = "task_list_panel"
        val timelinePanelTestTag = "timeline_panel"

        // Add hover state for task tooltips with improved dual hover detection
        var hoveredTaskInfo by remember { mutableStateOf<TaskHoverInfo?>(null) }
        var isTaskBarHovered by remember { mutableStateOf(false) }
        val hoverDebouncer = remember { Debouncer(hoverDelay) }
        val taskTooltipInteractionSource = remember { MutableInteractionSource() }
        val isTaskTooltipHovered by taskTooltipInteractionSource.collectIsHoveredAsState()
        val tooltipHideDebouncer = remember { Debouncer(300) }

        // Handle task tooltip visibility - show when either task bar or tooltip is hovered
        LaunchedEffect(isTaskBarHovered, isTaskTooltipHovered, hoveredTaskInfo) {
            val shouldShow = isTaskBarHovered || isTaskTooltipHovered
            if (shouldShow && hoveredTaskInfo != null) {
                tooltipHideDebouncer.cancel()
                // Tooltip stays visible
            } else if (!shouldShow && hoveredTaskInfo != null) {
                tooltipHideDebouncer.debounce {
                    hoveredTaskInfo = null
                    isTaskBarHovered = false
                }
            }
        }

        // Add state for legends button hover with popup area detection
        val legendsInteractionSource = remember { MutableInteractionSource() }
        val popupInteractionSource = remember { MutableInteractionSource() }
        val isButtonHovered by legendsInteractionSource.collectIsHoveredAsState()
        val isPopupHovered by popupInteractionSource.collectIsHoveredAsState()
        var isLegendsHovered by remember { mutableStateOf(false) }
        val legendsDebouncer = remember { Debouncer(150) }

        // Handle hover state - show when either button or popup is hovered
        LaunchedEffect(isButtonHovered, isPopupHovered) {
            val shouldShow = isButtonHovered || isPopupHovered
            if (shouldShow) {
                legendsDebouncer.cancel()
                isLegendsHovered = true
            } else {
                legendsDebouncer.debounce {
                    isLegendsHovered = false
                }
            }
        }

        // Create scroll state for the entire view
        val scrollState = rememberScrollState()

        Box(modifier = modifier.fillMaxSize().testTag(ganttChartTestTag)) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                // Main chart area - remove weight to allow natural height
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (showTaskList) {
                        TaskListPanel(
                            tasks = state.tasks,
                            width = taskListWidth,
                            rowHeight = rowHeight,
                            headerHeight = headerHeight,
                            onToggleTaskExpansion = { taskId -> state.toggleTaskExpansion(taskId) },
                            modifier = Modifier.testTag(taskListTestTag)
                        )

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        // Draw the chart content
                        TimelinePanel(
                            state = state,
                            rowHeight = rowHeight,
                            headerHeight = headerHeight,
                            hoveredTaskInfo = hoveredTaskInfo,
                            onTaskHover = { taskInfo ->
                                if (taskInfo != null) {
                                    isTaskBarHovered = true
                                    hoverDebouncer.debounce {
                                        hoveredTaskInfo = taskInfo
                                    }
                                } else {
                                    hoverDebouncer.cancel()
                                    isTaskBarHovered = false
                                }
                            },
                            onToggleTaskExpansion = { taskId -> state.toggleTaskExpansion(taskId) },
                            modifier = Modifier.fillMaxWidth().testTag(timelinePanelTestTag)
                        )

                        // Draw the tooltip
                        hoveredTaskInfo?.let { info ->
                            TaskTooltip(
                                task = state.tasks.first { it.id == info.taskId },
                                position = info.position,
                                allTasks = state.tasks,
                                interactionSource = taskTooltipInteractionSource
                            )
                        }
                    }
                }
            }

            // Sticky "Show Legends" button in top right corner
            FloatingActionButton(
                onClick = { /* No action needed, only hover */ },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                interactionSource = legendsInteractionSource
            ) {
                // Custom info icon using Canvas
                val iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                Canvas(
                    modifier = Modifier.size(24.dp)
                ) {
                    drawInfoIcon(iconColor)
                }
            }

            // Legends popup on hover
            if (isLegendsHovered) {
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(-32, 70), // Position further away from button
                    onDismissRequest = { /* Don't dismiss on request for hover behavior */ },
                    properties = PopupProperties(
                        focusable = false,
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    )
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .wrapContentHeight()
                            .fillMaxWidth(0.33f) // Occupy one third of screen width
                            .hoverable(popupInteractionSource)
                    ) {
                        GroupInfoHeader(
                            groupInfo = state.getGroupInfo(),
                            taskCountProvider = { group -> state.tasks.count { it.group == group } }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun TaskListPanel(
    tasks: List<GanttTask>,
    width: Dp,
    rowHeight: Dp,
    headerHeight: Dp,
    onToggleTaskExpansion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = GanttTheme.current
    Column(modifier = modifier.width(width)) {
        // Header box - fixed at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                theme.naming.taskListHeader,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Regular Column with all tasks rendered
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            tasks.forEach { task ->
                TaskNameCell(
                    task = task,
                    onToggleExpand = onToggleTaskExpansion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
            }
        }
    }
}


@Composable
fun TaskNameCell(
    task: GanttTask,
    onToggleExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Add test tag for UI testing
    val taskCellTestTag = "task_cell_${task.id}"
    val indentSize = 16.dp

    Row(
        modifier = modifier.testTag(taskCellTestTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indentation based on level
        if (task.level > 0) {
            Spacer(modifier = Modifier.width(indentSize * task.level))
        }

        // Expand/collapse icon (only for tasks with children)
        if (task.hasChildren) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onToggleExpand(task.id) }
                    .padding(2.dp)
            ) {
                if (task.isExpanded) {
                    ArrowDownIcon(
                        modifier = Modifier.matchParentSize(),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    ArrowRightIcon(
                        modifier = Modifier.matchParentSize(),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

        } else {
            // Space for alignment if no children
            Spacer(modifier = Modifier.width(20.dp))
        }

        // Task name
        Text(
            text = task.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
fun TimelinePanel(
    state: GanttChartState,
    rowHeight: Dp,
    headerHeight: Dp,
    hoveredTaskInfo: TaskHoverInfo?,
    onTaskHover: (TaskHoverInfo?) -> Unit,
    onToggleTaskExpansion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val timelineViewInfo = state.timelineViewInfo

    Column(
        modifier = modifier.onSizeChanged { newSize ->
            state.chartWidthPx = newSize.width.toFloat()
        }
    ) {
        // Fixed header
        SimpleTimelineHeader(
            timelineViewInfo = timelineViewInfo,
            headerHeight = headerHeight,
            modifier = Modifier.fillMaxWidth()
        )

        // Content area - let it size naturally based on content
        TaskBarsAndDependenciesGrid(
            tasks = state.tasks,
            timelineViewInfo = timelineViewInfo,
            rowHeight = rowHeight,
            hoveredTaskInfo = hoveredTaskInfo,
            onTaskHover = onTaskHover,
            onToggleTaskExpansion = onToggleTaskExpansion,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
fun SimpleTimelineHeader(
    timelineViewInfo: TimelineViewInfo,
    headerHeight: Dp,
    modifier: Modifier = Modifier
) {
    // Add test tag for UI testing
    val timelineHeaderTestTag = "timeline_header"
    val theme = GanttTheme.current
    val borderColor = theme.colors.timelineHeaderBorderColor(MaterialTheme.colorScheme.outline)
    Box(
        modifier = modifier
            .height(headerHeight)
            .fillMaxWidth()
            .border(theme.styles.timelineHeaderBorderWidth, borderColor)
            .testTag(timelineHeaderTestTag)
    ) {
        // Start time label
        Text(
            text = timelineViewInfo.headerCells.firstOrNull()?.label ?: "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
        )

        // Center - Total Duration label
        Row(
            modifier = Modifier
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total Duration - ${formatDuration(timelineViewInfo.totalViewDuration)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // End time label
        Text(
            text = timelineViewInfo.headerCells.lastOrNull()?.label ?: "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        )
    }
}

/**
 * Draws a simple info icon (circle with 'i')
 */
fun DrawScope.drawInfoIcon(color: Color) {
    val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
    val radius = size.minDimension / 2 * 0.8f

    // Draw circle outline
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
    )

    // Draw the 'i' dot
    drawCircle(
        color = color,
        radius = 1.5.dp.toPx(),
        center = androidx.compose.ui.geometry.Offset(center.x, center.y - radius * 0.3f)
    )

    // Draw the 'i' stem
    val stemWidth = 2.dp.toPx()
    val stemHeight = radius * 0.6f
    drawRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(
            center.x - stemWidth / 2,
            center.y - radius * 0.05f
        ),
        size = androidx.compose.ui.geometry.Size(stemWidth, stemHeight)
    )
}
