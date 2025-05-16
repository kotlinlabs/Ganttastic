package io.github.kotlinlabs.ganttly.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import io.github.kotlinlabs.ganttly.models.TaskHoverInfo
import io.github.kotlinlabs.ganttly.models.TimelineViewInfo
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import io.github.kotlinlabs.ganttly.models.GanttTask
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
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val taskTextStyle = MaterialTheme.typography.labelSmall.copy(color = LocalContentColor.current)

    val taskVerticalPositions = remember { mutableStateMapOf<String, Float>() }
    val arrowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    var chartWidthPx by remember { mutableStateOf(0f) }
    val pointerPositionState = remember { mutableStateOf<Offset?>(null) }

    // Keep track of the visible items - this is crucial for scrolling
    val visibleItems = remember { mutableStateListOf<Int>() }

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
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointerType = event.type

                        when (pointerType) {
                            PointerEventType.Move -> {
                                val position = event.changes.first().position
                                pointerPositionState.value = position

                                // Check if pointer is over any task
                                val hoveredTask = findTaskAtPosition(
                                    position = position,
                                    tasks = tasks,
                                    timelineViewInfo = timelineViewInfo,
                                    rowHeightPx = rowHeightPx,
                                    lazyListState = listState
                                )

                                if (hoveredTask != null) {
                                    onTaskHover(TaskHoverInfo(
                                        taskId = hoveredTask.id,
                                        position = position
                                    ))
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

                            val taskStartOffsetSeconds = timelineViewInfo.viewStartDate.until(
                                task.startDate, DateTimeUnit.SECOND, TimeZone.UTC
                            )

                            val taskX = (taskStartOffsetSeconds * timelineViewInfo.pixelsPerSecond).toFloat()
                            val taskWidthPx = (task.duration.inWholeSeconds * timelineViewInfo.pixelsPerSecond).toFloat()

                            // Draw task bar
                            drawTaskBar(
                                drawScope = this,
                                task = task,
                                taskX = taskX,
                                taskTopY = taskTopY,
                                taskWidthPx = taskWidthPx,
                                rowHeightPx = rowHeightPx,
                                textMeasurer = textMeasurer,
                                textStyle = taskTextStyle,
                                chartWidthPx = chartWidthPx,
                                isHovered = hoveredTaskInfo?.taskId == task.id
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
                                    parentTaskIndex <= lastVisibleItemIndex) {

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
                                        val parentTaskBottomY = parentTaskTopY + (rowHeightPx - barHeight) / 2 + barHeight

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
                                            strokeWidth = 2.0f.dp.value,
                                            arrowHeadSize = 7.0f.dp.value
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
    val scrollOffset = lazyListState.firstVisibleItemScrollOffset
    val firstVisibleIndex = lazyListState.firstVisibleItemIndex

    // Calculate which row we're in based on Y position
    val rowIndex = ((position.y + scrollOffset) / rowHeightPx).toInt()
    val adjustedRowIndex = firstVisibleIndex + rowIndex

    if (adjustedRowIndex < 0 || adjustedRowIndex >= tasks.size) return null

    val task = tasks[adjustedRowIndex]

    // Calculate task boundaries
    val taskStartOffsetSeconds = timelineViewInfo.viewStartDate.until(
        task.startDate, DateTimeUnit.SECOND, TimeZone.UTC
    )

    val taskX = (taskStartOffsetSeconds * timelineViewInfo.pixelsPerSecond).toFloat()
    val taskWidthPx = (task.duration.inWholeSeconds * timelineViewInfo.pixelsPerSecond).toFloat()
    val barHeight = rowHeightPx * 0.7f
    val barTopY = (adjustedRowIndex * rowHeightPx) - scrollOffset + (rowHeightPx - barHeight) / 2

    // Check if position is within task bar
    return if (position.x >= taskX &&
        position.x <= taskX + taskWidthPx &&
        position.y >= barTopY &&
        position.y <= barTopY + barHeight) {
        task
    } else {
        null
    }
}

fun drawTaskBar(
    drawScope: DrawScope,
    task: GanttTask,
    taskX: Float,
    taskTopY: Float,
    taskWidthPx: Float,
    rowHeightPx: Float,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    chartWidthPx: Float,
    isHovered: Boolean = false
) {
    drawScope.apply {
        val barHeight = rowHeightPx * 0.7f // Make bar slightly smaller than row
        val barTopY = taskTopY + (rowHeightPx - barHeight) / 2
        val cornerRadius = CornerRadius(barHeight * 0.2f)

        val backgroundColor = if (isHovered) {
            task.color.copy(alpha = 0.5f) // Higher opacity when hovered
        } else {
            task.color.copy(alpha = 0.3f) // Normal transparency
        }

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
            color = backgroundColor, // Lighter background
            topLeft = Offset(taskX, barTopY),
            size = Size(taskWidthPx, barHeight),
            cornerRadius = cornerRadius
        )

        // Draw progress fill
        if (task.progress > 0f) {
            val progressWidth = taskWidthPx * task.progress.coerceIn(0f, 1f)
            val progressColor = if (isHovered) {
                task.color.copy(alpha = 0.9f) // More vibrant when hovered
            } else {
                task.color
            }

            drawRoundRect(
                color = progressColor,
                topLeft = Offset(taskX, barTopY),
                size = Size(progressWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }


        // Draw border with hover effect
        drawRoundRect(
            color = task.color,
            topLeft = Offset(taskX, barTopY),
            size = Size(taskWidthPx, barHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = borderWidth)
        )


        // Task Name Text Placement
        val paddingPx = 4.dp.toPx()
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
    strokeWidth: Float = 2.0f.dp.value,
    arrowHeadSize: Float = 7.0f.dp.value
) {
    val path = Path()

    // Always start from the beginning point
    path.moveTo(startX, startY)

    // Vertical line down/up to the end Y coordinate
    path.lineTo(startX, endY)

    // Horizontal line directly to the end point
    path.lineTo(endX, endY)

    // Draw the path with rounded corner at the elbow
    drawPath(
        path = path,
        color = arrowColor,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.cornerPathEffect(4.dp.toPx())
        )
    )

    // Draw arrowhead - pointing horizontally from left to right
    val arrowAngle = 0.0 // Horizontal angle (in radians)

    val arrowPath = Path().apply {
        moveTo(endX, endY)
        lineTo(
            endX - arrowHeadSize * cos(arrowAngle - PI / 6).toFloat(),
            endY - arrowHeadSize * sin(arrowAngle - PI / 6).toFloat()
        )
        moveTo(endX, endY) // Go back to the tip
        lineTo(
            endX - arrowHeadSize * cos(arrowAngle + PI / 6).toFloat(),
            endY - arrowHeadSize * sin(arrowAngle + PI / 6).toFloat()
        )
    }

    drawPath(arrowPath, color = arrowColor, style = Stroke(width = strokeWidth))
}