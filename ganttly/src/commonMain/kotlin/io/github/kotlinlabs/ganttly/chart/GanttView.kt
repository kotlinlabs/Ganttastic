package io.github.kotlinlabs.ganttly.chart

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kotlinlabs.ganttly.models.Debouncer
import io.github.kotlinlabs.ganttly.models.GanttTask
import io.github.kotlinlabs.ganttly.models.TaskHoverInfo
import io.github.kotlinlabs.ganttly.models.TimelineViewInfo


const val DEFAULT_TASK_LIST_WIDTH_DP = 220
const val DEFAULT_ROW_HEIGHT_DP = 36 // Slightly smaller for more tasks
const val DEFAULT_HEADER_HEIGHT_DP = 40

const val DEFAULT_HEADER_EXPANDED_HEIGHT_DP = 250

@Composable
fun GanttChartView(
    state: GanttChartState,
    modifier: Modifier = Modifier,
    taskListWidth: Dp = DEFAULT_TASK_LIST_WIDTH_DP.dp,
    rowHeight: Dp = DEFAULT_ROW_HEIGHT_DP.dp,
    headerHeight: Dp = DEFAULT_HEADER_HEIGHT_DP.dp,
    showTaskList: Boolean = true,
    hoverDelay: Long = 150,
    headerContent: @Composable (() -> Unit)? = null,
    headerMaxHeight: Dp = DEFAULT_HEADER_EXPANDED_HEIGHT_DP.dp
) {
    // Add hover state
    var hoveredTaskInfo by remember { mutableStateOf<TaskHoverInfo?>(null) }
    val hoverDebouncer = remember { Debouncer(hoverDelay) }

    // Create separate scroll states for each component
    val taskListState = rememberLazyListState()
    val chartGridState = rememberLazyListState()

    // Calculate header visibility based on scroll position
    val headerCollapseFraction by remember {
        derivedStateOf {
            // When first item is visible and not scrolled, header should be fully expanded (0.0f)
            // As the first item scrolls up, header should collapse (towards 1.0f)
            val firstVisibleItemIndex = chartGridState.firstVisibleItemIndex
            val firstVisibleItemOffset = chartGridState.firstVisibleItemScrollOffset

            if (firstVisibleItemIndex == 0) {
                // Map the scroll offset to a 0.0 - 1.0 range (0 = fully expanded, 1 = fully collapsed)
                // Assuming we want to collapse over the first 200 pixels of scrolling
                val collapseTresholdPx = 200f
                (firstVisibleItemOffset / collapseTresholdPx).coerceIn(0f, 1f)
            } else {
                // If we're past the first item, header is fully collapsed
                1.0f
            }
        }
    }

    // Calculate the actual header height based on the collapse fraction
    val headerCurrentHeight = with(LocalDensity.current) {
        lerp(headerMaxHeight.toPx(), 0f, headerCollapseFraction).toDp()
    }

    // Synchronize the two scroll states
    LaunchedEffect(taskListState) {
        snapshotFlow { taskListState.firstVisibleItemIndex to taskListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                // When taskList scrolls, update the chart grid
                if (chartGridState.firstVisibleItemIndex != index ||
                    chartGridState.firstVisibleItemScrollOffset != offset
                ) {
                    chartGridState.scrollToItem(index, offset)
                }
            }
    }

    LaunchedEffect(chartGridState) {
        snapshotFlow { chartGridState.firstVisibleItemIndex to chartGridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                // When chart grid scrolls, update the taskList
                if (taskListState.firstVisibleItemIndex != index ||
                    taskListState.firstVisibleItemScrollOffset != offset
                ) {
                    taskListState.scrollToItem(index, offset)
                }
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Collapsible header - only show if headerContent is provided
        headerContent?.let {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerCurrentHeight)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    .alpha(1f - headerCollapseFraction) // Fade out as it collapses
            ) {
                // Actually render the header content
                headerContent()
            }
        }

        // Main chart area
        Row(modifier = Modifier.weight(1f)) {
            if (showTaskList) {
                TaskListPanel(
                    tasks = state.tasks,
                    width = taskListWidth,
                    rowHeight = rowHeight,
                    headerHeight = headerHeight,
                    listState = taskListState,
                    modifier = Modifier.fillMaxHeight()
                )

                HorizontalDivider(
                    modifier = Modifier.fillMaxHeight().width(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Draw the chart content
                TimelinePanel(
                    state = state,
                    rowHeight = rowHeight,
                    headerHeight = headerHeight,
                    hoveredTaskInfo = hoveredTaskInfo,
                    listState = chartGridState,
                    onTaskHover = { taskInfo ->
                        if (taskInfo != null) {
                            hoverDebouncer.debounce {
                                hoveredTaskInfo = taskInfo
                            }
                        } else {
                            hoverDebouncer.cancel()
                            hoveredTaskInfo = null
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Draw the tooltip
                hoveredTaskInfo?.let { info ->
                    TaskTooltip(
                        task = state.tasks.first { it.id == info.taskId },
                        position = info.position,
                        allTasks = state.tasks,
                        layoutInfo = chartGridState.layoutInfo
                    )
                }
            }
        }
    }
}


// Helper function to interpolate between two values based on a fraction
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

@Composable
fun TaskListPanel(
    tasks: List<GanttTask>,
    width: Dp,
    rowHeight: Dp,
    headerHeight: Dp,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
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
                "Tasks",
                style = MaterialTheme.typography.labelSmall
            )
        }

        // This Box will contain the scrollable content
        // Use weight(1f) to ensure it takes all remaining space
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // <-- This is crucial
        ) {
            // LazyColumn needs to fill its parent container
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    count = tasks.size,
                    key = { index -> tasks[index].id }
                ) { index ->
                    val task = tasks[index]
                    TaskNameCell(
                        taskName = task.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )
                }
            }
        }
    }
}

@Composable
fun TaskNameCell(taskName: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            taskName,
            style = MaterialTheme.typography.bodySmall, // Smaller text for task list
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
    listState: LazyListState,
    onTaskHover: (TaskHoverInfo?) -> Unit,
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

        // Scrollable content
        Box(
            modifier = Modifier
                .weight(1f) // <-- This is crucial
                .fillMaxWidth()
        ) {
            TaskBarsAndDependenciesGrid(
                tasks = state.tasks,
                timelineViewInfo = timelineViewInfo,
                rowHeight = rowHeight,
                hoveredTaskInfo = hoveredTaskInfo,
                listState = listState,
                onTaskHover = onTaskHover,
                modifier = Modifier.fillMaxSize() // <-- Use fillMaxSize to fill parent
            )
        }
    }
}

@Composable
fun SimpleTimelineHeader(
    timelineViewInfo: TimelineViewInfo,
    headerHeight: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(headerHeight)
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        // Start time label
        Text(
            text = timelineViewInfo.headerCells.firstOrNull()?.label ?: "",
            style = MaterialTheme.typography.labelSmall,
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
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // End time label
        Text(
            text = timelineViewInfo.headerCells.lastOrNull()?.label ?: "",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        )
    }
}
