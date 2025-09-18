@file:OptIn(ExperimentalTime::class)

package io.github.kotlinlabs.ganttly.chart

import TaskBarsAndDependenciesGrid
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
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
import io.github.kotlinlabs.ganttly.models.TimelineHoverInfo
import io.github.kotlinlabs.ganttly.models.TimelineViewInfo
import kotlin.time.Duration.Companion.seconds
import io.github.kotlinlabs.ganttly.styles.GanttTheme
import io.github.kotlinlabs.ganttly.styles.GanttThemeConfig
import io.github.kotlinlabs.ganttly.styles.ProvideGanttTheme
import io.github.kotlinlabs.ganttly.styles.TaskGroupColorCoordinator
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime


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
    ganttTheme: GanttThemeConfig = GanttTheme.current,
    enableSmartOrdering: Boolean = true
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
        
        // Apply smart ordering setting
        LaunchedEffect(enableSmartOrdering) {
            state.enableSmartOrdering = enableSmartOrdering
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

        // Add state for vertical hover line on timeline header
        var timelineHoverInfo by remember { mutableStateOf<TimelineHoverInfo?>(null) }
        val timelineHoverDebouncer = remember { Debouncer(100) } // Debounce to smooth exit

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

        // Create scroll state for the content areas only
        val scrollState = rememberScrollState()

        Box(modifier = modifier.fillMaxSize().testTag(ganttChartTestTag)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Fixed Headers Row - stays at top, doesn't scroll
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (showTaskList) {
                        // Task List Header - Fixed
                        TaskListHeader(
                            width = taskListWidth,
                            headerHeight = headerHeight
                        )

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(headerHeight)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        )
                    }

                    // Timeline Header - Fixed
                    SimpleTimelineHeader(
                        timelineViewInfo = state.timelineViewInfo,
                        headerHeight = headerHeight,
                        onTimelineHover = { hoverInfo -> 
                            if (hoverInfo != null) {
                                // Immediately show the line for responsiveness
                                timelineHoverInfo = hoverInfo
                            } else {
                                // Debounce the hide to prevent flickering
                                timelineHoverDebouncer.debounce {
                                    timelineHoverInfo = null
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onSizeChanged { newSize ->
                                state.chartWidthPx = newSize.width.toFloat()
                            }
                    )
                }

                // Scrollable Content Row - this scrolls under the fixed headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    if (showTaskList) {
                        // Task List Content - Scrollable
                        TaskListContent(
                            tasks = state.tasks,
                            width = taskListWidth,
                            rowHeight = rowHeight,
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
                        // Timeline Content - Scrollable
                        TaskBarsAndDependenciesGrid(
                            tasks = state.tasks,
                            timelineViewInfo = state.timelineViewInfo,
                            rowHeight = rowHeight,
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

            // Draw vertical hover line across entire chart when hovering over timeline header
            timelineHoverInfo?.let { hoverInfo ->
                TimelineVerticalHoverLine(
                    xPosition = hoverInfo.xPosition,
                    time = hoverInfo.time,
                    headerHeight = headerHeight,
                    showTaskList = showTaskList,
                    taskListWidth = if (showTaskList) taskListWidth + 1.dp else 0.dp
                )
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
fun TaskListHeader(
    width: Dp,
    headerHeight: Dp,
    modifier: Modifier = Modifier
) {
    val theme = GanttTheme.current
    Box(
        modifier = modifier
            .width(width)
            .height(headerHeight)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            theme.naming.taskListHeader,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun TaskListContent(
    tasks: List<GanttTask>,
    width: Dp,
    rowHeight: Dp,
    onToggleTaskExpansion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.width(width)) {
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

@Composable
fun TaskListPanel(
    tasks: List<GanttTask>,
    width: Dp,
    rowHeight: Dp,
    headerHeight: Dp,
    onToggleTaskExpansion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep this for backward compatibility, but it's now split into Header + Content
    Column(modifier = modifier.width(width)) {
        TaskListHeader(
            width = width,
            headerHeight = headerHeight
        )
        TaskListContent(
            tasks = tasks,
            width = width,
            rowHeight = rowHeight,
            onToggleTaskExpansion = onToggleTaskExpansion
        )
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


// TimelinePanel is now split into SimpleTimelineHeader (fixed) and TaskBarsAndDependenciesGrid (scrollable)
// This function is kept for backward compatibility but is no longer used in the main layout


@Composable
fun SimpleTimelineHeader(
    timelineViewInfo: TimelineViewInfo,
    headerHeight: Dp,
    onTimelineHover: (TimelineHoverInfo?) -> Unit = {},
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
            .pointerInput(timelineViewInfo) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Move -> {
                                val position = event.changes.first().position
                                // Calculate time at hover position
                                val timeAtPosition = calculateTimeAtPosition(
                                    xPosition = position.x,
                                    timelineViewInfo = timelineViewInfo
                                )
                                onTimelineHover(
                                    TimelineHoverInfo(
                                        xPosition = position.x,
                                        time = timeAtPosition
                                    )
                                )
                            }
                            PointerEventType.Exit -> {
                                onTimelineHover(null)
                            }
                        }
                    }
                }
            }
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
 * Calculates the time at a given X position in the timeline
 */
fun calculateTimeAtPosition(xPosition: Float, timelineViewInfo: TimelineViewInfo): String {
    // Calculate the time offset from the start of the timeline
    val secondsFromStart = xPosition / timelineViewInfo.pixelsPerSecond
    val timeAtPosition = timelineViewInfo.viewStartDate + secondsFromStart.toLong().seconds
    
    // Convert to local time and format as hh:mm
    val localTime = timeAtPosition.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${localTime.hour.toString().padStart(2, '0')}:${localTime.minute.toString().padStart(2, '0')}"
}

/**
 * Draws a vertical hover line across the entire chart with time tooltip
 */
@Composable
fun TimelineVerticalHoverLine(
    xPosition: Float,
    time: String,
    headerHeight: Dp,
    showTaskList: Boolean,
    taskListWidth: Dp
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val headerHeightPx = with(density) { headerHeight.toPx() }
    val taskListWidthPx = with(density) { taskListWidth.toPx() }
    val adjustedXPosition = if (showTaskList) xPosition + taskListWidthPx else xPosition
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    
    // Get theme colors outside of Canvas context
    val tooltipBackgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
    val tooltipTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
    val textStyle = androidx.compose.material3.MaterialTheme.typography.labelMedium
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Draw the vertical line using Canvas
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Draw solid vertical line (more visible than dotted)
            val lineColor = Color.Red.copy(alpha = 0.8f) // Red color for better visibility
            val strokeWidth = 2.dp.toPx() // Thicker line
            
            // Draw solid line from header bottom to chart bottom
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(adjustedXPosition, headerHeightPx),
                end = androidx.compose.ui.geometry.Offset(adjustedXPosition, size.height),
                strokeWidth = strokeWidth,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(10f, 5f), // 10px dash, 5px gap
                    0f
                )
            )
        }
        
        // Draw time tooltip directly on the Canvas to avoid any pointer interference
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Draw the tooltip background and text directly on canvas
            val tooltipX = adjustedXPosition - 25.dp.toPx()
            val tooltipY = headerHeightPx - 35.dp.toPx()
            val tooltipWidth = 50.dp.toPx()
            val tooltipHeight = 24.dp.toPx()
            
            // Draw tooltip background
            drawRoundRect(
                color = tooltipBackgroundColor,
                topLeft = androidx.compose.ui.geometry.Offset(tooltipX, tooltipY),
                size = androidx.compose.ui.geometry.Size(tooltipWidth, tooltipHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
            )
            
            // Draw tooltip shadow/border for better visibility
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.1f),
                topLeft = androidx.compose.ui.geometry.Offset(tooltipX - 1.dp.toPx(), tooltipY - 1.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(tooltipWidth + 2.dp.toPx(), tooltipHeight + 2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }
        
        // Draw text using a separate Canvas that doesn't interfere with positioning
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val finalTextStyle = textStyle.copy(
                color = tooltipTextColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
            
            val textLayoutResult = textMeasurer.measure(time, style = finalTextStyle)
            val textX = adjustedXPosition - (textLayoutResult.size.width / 2f)
            val textY = headerHeightPx - 35.dp.toPx() + (24.dp.toPx() - textLayoutResult.size.height) / 2f
            
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = androidx.compose.ui.geometry.Offset(textX, textY)
            )
        }
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
