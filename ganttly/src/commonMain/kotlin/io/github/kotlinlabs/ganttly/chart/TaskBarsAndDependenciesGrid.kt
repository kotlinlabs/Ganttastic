package io.github.kotlinlabs.ganttly.chart

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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
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
    modifier: Modifier = Modifier
) {
    val theme: GanttThemeConfig = GanttTheme.current

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val taskTextStyle = MaterialTheme.typography.labelSmall.copy(color = LocalContentColor.current)

    val taskVerticalPositions = remember { mutableStateMapOf<String, Float>() }
    val arrowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    var chartWidthPx by remember { mutableStateOf(0f) }
    val pointerPositionState = remember { mutableStateOf<Offset?>(null) }
    val pointerInputKey = remember { mutableStateOf(0) }

    // Keep track of the visible items - this is crucial for scrolling
    val visibleItems = remember { mutableStateListOf<Int>() }

    // Update the key whenever the list scrolls to force pointerInput to re-initialize
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect {
                // Increment the key to force recomposition of pointerInput
                pointerInputKey.value++
            }
    }

    // Update visible items when the list scrolls
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.map { it.index }
        }.collect { indices ->
            visibleItems.clear()
            visibleItems.addAll(indices)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
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
                                println("Pointer position: $position")
                                // Check if pointer is over any task
                                val hoveredTask = findTaskAtPosition(
                                    position = position,
                                    tasks = tasks,
                                    timelineViewInfo = timelineViewInfo,
                                    rowHeightPx = rowHeightPx,
                                    lazyListState = listState
                                )

                                if (hoveredTask != null) {
                                    println("Hovering over task: ${hoveredTask.name}")
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
            modifier = Modifier.fillMaxSize(),
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
                // Draw Task Bars - only for visible items
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
                            )
                        }
                    }
                }

                // Draw Dependency Arrows - only between visible items
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

                                if (parentTaskIndex != -1 &&
                                    parentTaskIndex >= firstVisibleItemIndex &&
                                    parentTaskIndex <= lastVisibleItemIndex
                                ) {

                                    val parentTask = tasks[parentTaskIndex]
                                    val parentItemInfo = visibleItemInfo.find { it.index == parentTaskIndex }

                                    if (parentItemInfo != null) {
                                        val parentTaskTopY = parentItemInfo.offset.toFloat()

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
                }
            }
        }
    }
}

// Helper function to find a task at a specific position
fun findTaskAtPosition(
    position: Offset,
    tasks: List<GanttTask>,
    timelineViewInfo: TimelineViewInfo,
    rowHeightPx: Float,
    lazyListState: LazyListState
): GanttTask? {
    // Get visible items info directly from the LazyListState
    val visibleItemsInfo = lazyListState.layoutInfo.visibleItemsInfo

    // If there are no visible items, return null
    if (visibleItemsInfo.isEmpty()) return null

    // Find which visible item contains the y-coordinate of the pointer
    for (itemInfo in visibleItemsInfo) {
        val itemIndex = itemInfo.index
        val itemTopY = itemInfo.offset.toFloat()
        val itemBottomY = itemTopY + rowHeightPx

        // Check if the position's Y coordinate is within this item's bounds
        if (position.y >= itemTopY && position.y <= itemBottomY) {
            if (itemIndex < 0 || itemIndex >= tasks.size) continue

            val task = tasks[itemIndex]

            // Calculate task bar boundaries within this item
            val taskStartOffsetSeconds = timelineViewInfo.viewStartDate.until(
                task.startDate, DateTimeUnit.SECOND, TimeZone.UTC
            )

            val taskX = (taskStartOffsetSeconds * timelineViewInfo.pixelsPerSecond).toFloat()
            val taskWidthPx = (task.duration.inWholeSeconds * timelineViewInfo.pixelsPerSecond).toFloat()

            // Calculate the height and position of the actual task bar within the row
            val barHeight = rowHeightPx * 0.7f
            val barTopY = itemTopY + (rowHeightPx - barHeight) / 2

            // Check if position is within task bar
            if (position.x >= taskX &&
                position.x <= taskX + taskWidthPx &&
                position.y >= barTopY &&
                position.y <= barTopY + barHeight
            ) {
                return task
            }
        }
    }

    return null
}

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
    theme: GanttThemeConfig

) {

    drawScope.apply {
        // Use theme values for calculations
        val taskStartOffsetSeconds = timelineViewInfo.viewStartDate.until(
            task.startDate, DateTimeUnit.SECOND, TimeZone.UTC
        )

        val taskX = (taskStartOffsetSeconds * timelineViewInfo.pixelsPerSecond).toFloat()
        val taskWidthPx = (task.effectiveDuration.inWholeSeconds *
                timelineViewInfo.pixelsPerSecond).toFloat()

        // Rest of the drawing code remains the same...
        // Use theme values for calculations
        val barHeight = rowHeightPx * theme.styles.taskBarHeight
        val barTopY = taskTopY + (rowHeightPx - barHeight) / 2

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


        val borderWidth = if (isHovered) 1.5.dp.toPx() else 1.dp.toPx()

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

        // Task Name Text Placement
        val paddingPx = theme.styles.taskBarTextPadding.toPx()
        val textLayoutResult = textMeasurer.measure(task.name, style = textStyle)
        val textWidth = textLayoutResult.size.width
        val textHeight = textLayoutResult.size.height

        val textY = barTopY + (barHeight - textHeight) / 2

        // Option 1: Center if fits
        if (textWidth < taskWidthPx - (2 * paddingPx)) {
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(taskX + (taskWidthPx - textWidth) / 2, textY)
            )
        } else {
            // Option 2: Right of bar if fits in chart
            val spaceRightOfBar = chartWidthPx - (taskX + taskWidthPx + paddingPx)
            if (textWidth < spaceRightOfBar) {
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(taskX + taskWidthPx + paddingPx, textY)
                )
            } else {
                // Option 3: Left of bar if fits in chart (and enough space before task starts)
                val spaceLeftOfBar = taskX - paddingPx
                if (textWidth < spaceLeftOfBar) {
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(taskX - textWidth - paddingPx, textY)
                    )
                }
                // Option 4 (Fallback): Truncated inside the bar on the left (Canvas doesn't auto-truncate)
                // For simplicity, we'll let it overflow if it doesn't fit left/right.
                // A more complex solution would clip the text or draw it truncated.
                else if (taskWidthPx > paddingPx * 2) { // Only draw inside if bar has some width
                    // This will just draw, potentially overflowing. Proper truncation on canvas is harder.
                    clipRect(taskX + paddingPx, barTopY, taskX + taskWidthPx - paddingPx, barTopY + barHeight) {
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(taskX + paddingPx, textY)
                        )
                    }
                }
            }
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

fun DrawScope.drawParentChildConnectors(
    parent: GanttTask,
    children: List<GanttTask>,
    timelineViewInfo: TimelineViewInfo,
    rowPositions: Map<String, Float>, // Map of task IDs to Y positions
    theme: GanttThemeConfig
) {
    if (children.isEmpty() || !parent.isExpanded) return

    val lineColor = theme.colors.dependencyArrowColor(Color.Gray).copy(alpha = 0.3f)
    val lineWidth = theme.styles.dependencyArrowWidth.toPx() * 0.5f

    // Calculate parent's position
    val parentStartX = (timelineViewInfo.viewStartDate.until(
        parent.startDate, DateTimeUnit.SECOND, TimeZone.UTC
    ) * timelineViewInfo.pixelsPerSecond).toFloat()

    val parentY = rowPositions[parent.id] ?: return

    // Draw connections to each child
    children.forEach { child ->
        val childY = rowPositions[child.id] ?: return@forEach

        val childStartX = (timelineViewInfo.viewStartDate.until(
            child.startDate, DateTimeUnit.SECOND, TimeZone.UTC
        ) * timelineViewInfo.pixelsPerSecond).toFloat()

        // Draw a subtle vertical line connecting parent to child
        drawLine(
            color = lineColor,
            start = Offset(parentStartX, parentY),
            end = Offset(parentStartX, childY),
            strokeWidth = lineWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f), 0f)
        )

        // Draw a subtle horizontal line from parent vertical line to child start
        drawLine(
            color = lineColor,
            start = Offset(parentStartX, childY),
            end = Offset(childStartX, childY),
            strokeWidth = lineWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f), 0f)
        )
    }
}
