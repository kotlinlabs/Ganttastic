import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kotlinlabs.ganttly.models.GanttTask
import io.github.kotlinlabs.ganttly.models.TaskHoverInfo
import io.github.kotlinlabs.ganttly.models.TimelineViewInfo
import io.github.kotlinlabs.ganttly.styles.GanttTheme
import io.github.kotlinlabs.ganttly.styles.GanttThemeConfig
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.until
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TaskBarsAndDependenciesGrid(
    tasks: List<GanttTask>,
    timelineViewInfo: TimelineViewInfo,
    rowHeight: Dp,
    hoveredTaskInfo: TaskHoverInfo?,
    listState: LazyListState,
    onTaskHover: (TaskHoverInfo?) -> Unit,
    onToggleTaskExpansion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Add test tags for UI testing
    val taskGridTestTag = "task_bars_and_dependencies_grid"
    val taskGridListTestTag = "task_grid_list"
    val theme: GanttThemeConfig = GanttTheme.current

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val taskTextStyle = MaterialTheme.typography.labelSmall.copy(color = LocalContentColor.current)

    val taskVerticalPositions = remember { mutableStateMapOf<String, Float>() }
    val arrowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    var chartWidthPx by remember { mutableStateOf(0f) }
    val pointerPositionState = remember { mutableStateOf<Offset?>(null) }

    // Create a composition key that will force pointer input recomposition
    // This needs to be updated whenever the task list changes (e.g. expand/collapse)
    val pointerInputKey = remember { mutableStateOf(0) }

    // Keep track of expanded/collapsed state to reset pointer input
    val expandedTaskIds = remember { mutableStateOf(tasks.filter { it.isExpanded }.map { it.id }.toSet()) }

    // Update pointer input key when task expansion state changes
    LaunchedEffect(expandedTaskIds.value) {
        pointerInputKey.value++
    }

    // Update expanded task set when tasks change
    LaunchedEffect(tasks) {
        val newExpandedTaskIds = tasks.filter { it.isExpanded }.map { it.id }.toSet()
        if (newExpandedTaskIds != expandedTaskIds.value) {
            expandedTaskIds.value = newExpandedTaskIds
        }
    }

    // Update the key whenever the list scrolls to force pointerInput to re-initialize
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect {
                // Increment the key to force recomposition of pointerInput
                pointerInputKey.value++
            }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .testTag(taskGridTestTag)
            .onSizeChanged { size ->
                chartWidthPx = size.width.toFloat()
            }
            .pointerInput(pointerInputKey.value) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointerType = event.type

                        when (pointerType) {
                            PointerEventType.Move -> {
                                val position = event.changes.first().position
                                pointerPositionState.value = position

                                // Improved task hit testing
                                val hoveredTask = findTaskAtPosition(
                                    position = position,
                                    tasks = tasks,
                                    timelineViewInfo = timelineViewInfo,
                                    rowHeightPx = rowHeightPx,
                                    listState = listState
                                )

                                if (hoveredTask != null) {
                                    onTaskHover(
                                        TaskHoverInfo(
                                            taskId = hoveredTask.id,
                                            position = position
                                        )
                                    )
                                } else {
                                    onTaskHover(null)
                                }

                            }

                            PointerEventType.Press -> {
                                val position = event.changes.first().position
                                val clickedTask = findTaskAtPosition(
                                    position = position,
                                    tasks = tasks,
                                    timelineViewInfo = timelineViewInfo,
                                    rowHeightPx = rowHeightPx,
                                    listState = listState
                                )

                                if (clickedTask != null) {
                                    // Immediately update to force pointer input refresh
                                    if (clickedTask.hasChildren) {
                                        expandedTaskIds.value = if (clickedTask.isExpanded) {
                                            expandedTaskIds.value - clickedTask.id
                                        } else {
                                            expandedTaskIds.value + clickedTask.id
                                        }
                                    }
                                    // Trigger the callback
                                    onToggleTaskExpansion(clickedTask.id)
                                }
                            }

                            PointerEventType.Exit -> {
                                pointerPositionState.value = null
                                onTaskHover(null)
                            }
                        }
                    }
                }
            }
    ) {
        // Use the shared LazyListState for synchronized scrolling
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(taskGridListTestTag),
            userScrollEnabled = true,
        ) {
            items(
                count = tasks.size,
                key = { index -> tasks[index].id }
            ) { index ->
                val task = tasks[index]
                // Create row background
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(rowHeight)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        .testTag("task_bar_row_${task.id}")
                )

                // Store positions
                val taskTopY = index * rowHeightPx
                taskVerticalPositions[task.id] = taskTopY + rowHeightPx / 2
            }
        }

        // This Canvas is drawn on top of the LazyColumn
        Canvas(modifier = Modifier.matchParentSize()) {
            val visibleItemInfo = listState.layoutInfo.visibleItemsInfo
            val firstVisibleItemIndex = visibleItemInfo.firstOrNull()?.index ?: 0
            val lastVisibleItemIndex = visibleItemInfo.lastOrNull()?.index ?: tasks.lastIndex

            clipRect(0f, 0f, chartWidthPx, size.height) {
                // Draw Dependency Arrows FIRST - only between visible items
                // This ensures arrows appear behind task bars
                for (i in firstVisibleItemIndex..lastVisibleItemIndex) {
                    if (i < tasks.size) {
                        val currentTask = tasks[i]
                        val currentItemInfo = visibleItemInfo.find { it.index == i }

                        if (currentItemInfo != null) {
                            val currentTaskTopY = currentItemInfo.offset.toFloat()

                            // For each dependency this task has
                            for (parentTaskId in currentTask.dependencies) {
                                // Find the parent task
                                val parentTaskIndex = tasks.indexOfFirst { it.id == parentTaskId }

                                if (parentTaskIndex != -1) { // Only check if parent exists, not if it's visible
                                    val parentTask = tasks[parentTaskIndex]

                                    // Calculate parent task position regardless of visibility
                                    val parentTaskTopY = if (parentTaskIndex >= firstVisibleItemIndex &&
                                        parentTaskIndex <= lastVisibleItemIndex
                                    ) {
                                        // Parent is visible, get actual position
                                        val parentItemInfo = visibleItemInfo.find { it.index == parentTaskIndex }
                                        parentItemInfo?.offset?.toFloat() ?: 0f
                                    } else {
                                        // Parent is not visible, calculate position relative to view
                                        if (parentTaskIndex < firstVisibleItemIndex) {
                                            // Parent is above the visible area
                                            0f - ((firstVisibleItemIndex - parentTaskIndex) * rowHeightPx)
                                        } else {
                                            // Parent is below the visible area
                                            size.height + ((parentTaskIndex - lastVisibleItemIndex) * rowHeightPx)
                                        }
                                    }

                                    // Calculate X coordinates
                                    val parentTaskStartX = (timelineViewInfo.viewStartDate.until(
                                        parentTask.startDate, DateTimeUnit.SECOND, TimeZone.UTC
                                    ) * timelineViewInfo.pixelsPerSecond).toFloat()

                                    val parentTaskWidthPx = (parentTask.duration.inWholeSeconds *
                                            timelineViewInfo.pixelsPerSecond).toFloat()

                                    // Parent task bottom center X
                                    val parentTaskBottomCenterX = parentTaskStartX + (parentTaskWidthPx / 2)

                                    // Parent task bottom Y
                                    val barHeight = rowHeightPx * 0.7f
                                    val parentTaskBottomY =
                                        parentTaskTopY + (rowHeightPx - barHeight) / 2 + barHeight

                                    // Current (dependent) task's center start
                                    val currentTaskStartX = (timelineViewInfo.viewStartDate.until(
                                        currentTask.startDate, DateTimeUnit.SECOND, TimeZone.UTC
                                    ) * timelineViewInfo.pixelsPerSecond).toFloat()

                                    // Current task center Y
                                    val currentTaskCenterY = currentTaskTopY + (rowHeightPx / 2)

                                    // Draw arrow from parent's bottom center to current task's center start
                                    drawDependencyArrow(
                                        startX = parentTaskBottomCenterX,
                                        startY = parentTaskBottomY,
                                        endX = currentTaskStartX,
                                        endY = currentTaskCenterY,
                                        arrowColor = arrowColor,
                                        theme = theme,
                                    )
                                }
                            }
                        }
                    }
                }

                // Draw Task Bars AFTER arrows - only for visible items
                // This ensures task bars appear on top of arrows
                for (i in firstVisibleItemIndex..lastVisibleItemIndex) {
                    if (i < tasks.size) {
                        val task = tasks[i]

                        // Calculate the actual Y position based on the visible item info
                        val itemInfo = visibleItemInfo.find { it.index == i }
                        if (itemInfo != null) {
                            val taskTopY = itemInfo.offset.toFloat()

                            // Draw task bar
                            drawTaskBar(
                                drawScope = this,
                                task = task,
                                taskTopY = taskTopY,
                                rowHeightPx = rowHeightPx,
                                timelineViewInfo = timelineViewInfo,
                                textMeasurer = textMeasurer,
                                textStyle = taskTextStyle,
                                chartWidthPx = chartWidthPx,
                                isHovered = hoveredTaskInfo?.taskId == task.id,
                                theme = theme,
                                onSurfaceColor = arrowColor,
                                borderWidthHovered = with(density) { 1.5.dp.toPx() },
                                borderWidthNormal = with(density) { 1.dp.toPx() },
                                taskBarTextPaddingPx = with(density) { theme.styles.taskBarTextPadding.toPx() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Finds a task at the given position, accounting for task collapsing/expanding
 */
private fun findTaskAtPosition(
    position: Offset,
    tasks: List<GanttTask>,
    timelineViewInfo: TimelineViewInfo,
    rowHeightPx: Float,
    listState: LazyListState
): GanttTask? {
    // Get information about visible items
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return null

    // Find which visible item was clicked
    for (itemInfo in visibleItems) {
        val itemTop = itemInfo.offset
        val itemBottom = itemTop + itemInfo.size

        // Check if the click Y position is within this item
        if (position.y >= itemTop && position.y <= itemBottom) {
            val task = tasks.getOrNull(itemInfo.index) ?: continue

            // Now check if X position is within the task bar
            val taskStartOffsetSeconds = timelineViewInfo.viewStartDate.until(
                task.startDate, DateTimeUnit.SECOND, TimeZone.UTC
            )

            val taskX = (taskStartOffsetSeconds * timelineViewInfo.pixelsPerSecond).toFloat()
            val taskWidthPx = (task.effectiveDuration.inWholeSeconds *
                    timelineViewInfo.pixelsPerSecond).toFloat()

            // Check if position is within task bar bounds
            if (position.x >= taskX && position.x <= taskX + taskWidthPx) {
                return task
            }

            break // We found the row but not on the task bar
        }
    }

    return null
}


// Modified drawTaskBar function with simpler implementation for larger +/- symbols
fun drawTaskBar(
    drawScope: DrawScope,
    task: GanttTask,
    taskTopY: Float,
    timelineViewInfo: TimelineViewInfo,
    rowHeightPx: Float,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    chartWidthPx: Float,
    isHovered: Boolean = false,
    theme: GanttThemeConfig,
    onSurfaceColor: Color,
    borderWidthHovered: Float,
    borderWidthNormal: Float,
    taskBarTextPaddingPx: Float
) {
    drawScope.apply {
        // Calculate task position and dimensions
        val taskStartOffsetSeconds = timelineViewInfo.viewStartDate.until(
            task.startDate, DateTimeUnit.SECOND, TimeZone.UTC
        )

        val taskX = (taskStartOffsetSeconds * timelineViewInfo.pixelsPerSecond).toFloat()
        val taskWidthPx = (task.effectiveDuration.inWholeSeconds *
                timelineViewInfo.pixelsPerSecond).toFloat()

        // Use theme values for calculations
        val barHeight = rowHeightPx * theme.styles.taskBarHeight
        val barTopY = taskTopY + (rowHeightPx - barHeight) / 2
        val barCenterY = barTopY + barHeight / 2

        // Use different styles for parent vs child tasks
        val cornerRadius = if (task.hasChildren) {
            // Parent tasks (more square)
            CornerRadius(barHeight * 0.15f)
        } else {
            // Child tasks (more rounded)
            CornerRadius(barHeight * theme.styles.taskBarCornerRadius)
        }

        // Get colors from theme
        val backgroundColor = theme.colors.taskBarBackground(task.color, isHovered)
        val borderColor = theme.colors.taskBarBorder(task.color, isHovered)
        val progressColor = theme.colors.taskBarProgress(task.color, isHovered)

        val borderWidth = if (isHovered) borderWidthHovered else borderWidthNormal

        if (isHovered) {
            // Draw subtle glow/shadow around the bar
            drawRoundRect(
                color = task.color.copy(alpha = 0.2f),
                topLeft = Offset(taskX - 2, barTopY - 2),
                size = Size(taskWidthPx + 4, barHeight + 4),
                cornerRadius = CornerRadius(cornerRadius.x + 2, cornerRadius.y + 2)
            )
        }

        // Draw main bar background
        drawRoundRect(
            color = backgroundColor,
            topLeft = Offset(taskX, barTopY),
            size = Size(taskWidthPx, barHeight),
            cornerRadius = cornerRadius
        )

        // Draw progress fill
        if (task.progress > 0f) {
            val progressWidth = taskWidthPx * task.progress.coerceIn(0f, 1f)

            drawRoundRect(
                color = progressColor,
                topLeft = Offset(taskX, barTopY),
                size = Size(progressWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }

        // Draw border with hover effect
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(taskX, barTopY),
            size = Size(taskWidthPx, barHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = borderWidth)
        )

        // Prepare text content
        val taskName = task.name

        // Prepare text content - for parent tasks, we'll leave space for the indicator
        val indicator = if (task.hasChildren) {
            if (task.isExpanded) "-" else "+"
        } else null

        // Measure the text and indicator (if present)
        val textLayoutResult = textMeasurer.measure(taskName, style = textStyle)
        val textHeight = textLayoutResult.size.height
        val textWidth = textLayoutResult.size.width.toFloat()

        // Additional width needed for indicator
        val indicatorWidth = if (indicator != null) {
            val indicatorStyle = textStyle.copy(fontSize = textStyle.fontSize * 1.5f)
            val indicatorLayoutResult = textMeasurer.measure(indicator, style = indicatorStyle)
            indicatorLayoutResult.size.width.toFloat() + taskBarTextPaddingPx
        } else 0f

        // Available width for text inside the bar (accounting for padding and indicator)
        val availableWidthInside = taskWidthPx - (2 * taskBarTextPaddingPx) - indicatorWidth

        // Space available to the left of the task bar (be cautious of edge cases)
        val availableWidthLeft = taskX - taskBarTextPaddingPx

        // Space available to the right of the task bar
        val availableWidthRight = chartWidthPx - (taskX + taskWidthPx) - taskBarTextPaddingPx

        // Determine text placement strategy
        val textPlacement = when {
            // Strategy 1: Inside the bar if it fits with padding
            textWidth + indicatorWidth <= availableWidthInside -> TextPlacement.INSIDE_CENTERED

            // Strategy 2: Right of the bar if it fits
            textWidth + indicatorWidth <= availableWidthRight -> TextPlacement.RIGHT

            // Strategy 3: Left of the bar if it fits
            textWidth + indicatorWidth <= availableWidthLeft -> TextPlacement.LEFT

            // Strategy 4: Default to inside with truncation
            else -> TextPlacement.INSIDE_CENTERED
        }

        // Calculate text position based on placement strategy
        val textY = barTopY + (barHeight - textHeight) / 2

        val textX = when (textPlacement) {
            TextPlacement.INSIDE_CENTERED -> {
                // Center text inside bar
                taskX + (taskWidthPx - textWidth) / 2
            }
            TextPlacement.RIGHT -> {
                // Place text to the right of the bar
                taskX + taskWidthPx + taskBarTextPaddingPx + indicatorWidth
            }
            TextPlacement.LEFT -> {
                // Place text to the left of the bar
                taskX - textWidth - taskBarTextPaddingPx - indicatorWidth
            }
        }

        // Draw the text
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(textX, textY)
        )

        // Draw expansion indicator (+ or -) for parent tasks
        if (indicator != null) {
            val indicatorStyle = textStyle.copy(
                fontSize = textStyle.fontSize * 2.0f  // Make it 100% larger
            )

            val indicatorLayoutResult = textMeasurer.measure(indicator, style = indicatorStyle)
            val indicatorHeight = indicatorLayoutResult.size.height



            // Center vertically
            val indicatorY = barTopY + (barHeight - indicatorHeight) / 2

            // Draw the indicator
            drawText(
                textLayoutResult = indicatorLayoutResult,
                topLeft = Offset(textX - (indicatorWidth), indicatorY)
            )
        }
    }
}

fun DrawScope.drawDependencyArrow(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    arrowColor: Color,
    theme: GanttThemeConfig
) {
    val path = Path()
    path.moveTo(startX, startY)
    path.lineTo(startX, endY)
    path.lineTo(endX, endY)

    // Get theme values
    val lineColor = theme.colors.dependencyArrowColor(arrowColor)
    val strokeWidth = theme.styles.dependencyArrowWidth.toPx()
    val arrowHeadSize = theme.styles.dependencyArrowHeadSize.toPx()
    val cornerRadius = theme.styles.dependencyArrowCornerRadius.toPx()

    // Draw the path with rounded corner at the elbow
    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.cornerPathEffect(cornerRadius)
        )
    )

    // Draw arrowhead
    val arrowAngle = 0.0 // Horizontal angle
    val arrowPath = Path().apply {
        moveTo(endX, endY)
        lineTo(
            endX - arrowHeadSize * cos(arrowAngle - PI / 6).toFloat(),
            endY - arrowHeadSize * sin(arrowAngle - PI / 6).toFloat()
        )
        moveTo(endX, endY)
        lineTo(
            endX - arrowHeadSize * cos(arrowAngle + PI / 6).toFloat(),
            endY - arrowHeadSize * sin(arrowAngle + PI / 6).toFloat()
        )
    }

    drawPath(arrowPath, color = lineColor, style = Stroke(width = strokeWidth))
}


// Enum for text placement strategies
private enum class TextPlacement {
    INSIDE_CENTERED,  // Inside the bar, centered
    LEFT,             // To the left of the bar
    RIGHT             // To the right of the bar
}
