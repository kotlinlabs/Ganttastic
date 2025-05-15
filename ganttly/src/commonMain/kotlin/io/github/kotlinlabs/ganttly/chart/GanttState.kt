package io.github.kotlinlabs.ganttly.chart

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.kotlinlabs.ganttly.models.GanttTask
import io.github.kotlinlabs.ganttly.models.TimelineHeaderCell
import io.github.kotlinlabs.ganttly.models.TimelineViewInfo
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun generateSimpleTimelineHeader(
    viewStart: Instant,
    viewEnd: Instant,
    totalChartWidthPx: Float,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<TimelineHeaderCell> {
    // Early return for invalid inputs
    if (totalChartWidthPx <= 0) return emptyList()

    val totalDurationSeconds = viewStart.until(viewEnd, DateTimeUnit.SECOND, timeZone)
    if (totalDurationSeconds <= 0) return emptyList()

    // Format the start and end times
    val startDateTime = viewStart.toLocalDateTime(timeZone)
    val endDateTime = viewEnd.toLocalDateTime(timeZone)

    val startLabel = formatDateTime(startDateTime)
    val endLabel = formatDateTime(endDateTime)

    // Create just two cells - start and end
    return listOf(
        TimelineHeaderCell(
            label = startLabel,
            startDate = viewStart,
            endDate = viewStart, // Point in time
            widthPx = 0f  // Zero width since it's just a marker
        ),
        TimelineHeaderCell(
            label = endLabel,
            startDate = viewEnd,
            endDate = viewEnd, // Point in time
            widthPx = 0f  // Zero width since it's just a marker
        )
    )
}

// Helper to format the datetime in a consistent way
fun formatDateTime(dateTime: LocalDateTime): String {
    return "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}


class GanttChartState(
    initialTasks: List<GanttTask>,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) {
    var tasks by mutableStateOf(initialTasks)

    // The overall earliest start and latest end of all tasks
    private val projectStartDate by derivedStateOf { tasks.minOfOrNull { it.startDate } ?: Clock.System.now() }
    private val projectEndDate by derivedStateOf { tasks.maxOfOrNull { it.endDate } ?: (projectStartDate + 1.hours) }

    // The current viewport for the timeline
    var viewStartDate by mutableStateOf(projectStartDate)
        private set
    var viewEndDate by mutableStateOf(projectEndDate)
        private set

    // This will be set by the UI based on available width
    var chartWidthPx by mutableStateOf(0f)

    val timelineViewInfo by derivedStateOf {
        val totalViewDuration = viewStartDate.until(viewEndDate, DateTimeUnit.SECOND, timeZone).seconds
        val pxPerSec = if (totalViewDuration.inWholeSeconds > 0 && chartWidthPx > 0) {
            (chartWidthPx / totalViewDuration.inWholeSeconds).toDouble()
        } else {
            1.0 // Default to 1 pixel per second if duration is zero or width not set
        }

        TimelineViewInfo(
            viewStartDate = viewStartDate,
            viewEndDate = viewEndDate,
            totalViewDuration = totalViewDuration,
            pixelsPerSecond = pxPerSec,
            headerCells = generateSimpleTimelineHeader(
                viewStart = viewStartDate,
                viewEnd = viewEndDate,
                totalChartWidthPx = chartWidthPx,
                timeZone = timeZone
            )
        )
    }

    fun updateVisibleDateRange(start: Instant, end: Instant) {
        // Add validation: end > start, constrain to project boundaries or allow free pan
        viewStartDate = start
        viewEndDate = end
    }

    fun fitTasksToView() {
        viewStartDate = projectStartDate
        viewEndDate = projectEndDate
        // pixelsPerSecond will be recalculated by timelineViewInfo
    }

    // Add more methods to manipulate state, e.g., for panning, specific zooming
    // For now, we fit all tasks.
    init {
        fitTasksToView()
    }
}


fun createSampleGanttState(): GanttChartState {
    val now = Clock.System.now()
    val tz = TimeZone.currentSystemDefault()

    val taskStartBaseline = now.toLocalDateTime(tz).let {
        Instant.parse("${it.date}T${it.hour.toString().padStart(2, '0')}:00:00Z") // Start of current hour UTC
    }

    val tasks = listOf(
        GanttTask(
            "1", "Design Phase",
            taskStartBaseline.plus(15.minutes),
            duration = 2.hours + 30.minutes,
            progress = 0.75f,
            color = Color(0xFF2196F3) // Blue
        ),
        GanttTask(
            "2", "Development - Feature A",
            (taskStartBaseline.plus(15.minutes) + (2.hours + 30.minutes)).plus(30.minutes), // Starts 30m after task 1 ends
            duration = 3.hours,
            progress = 0.2f,
            dependencies = listOf("1"),
            color = Color(0xFF4CAF50) // Green
        ),
        GanttTask(
            "3", "Testing - Feature A",
            ((taskStartBaseline.plus(15.minutes) + (2.hours + 30.minutes)).plus(30.minutes) + 3.hours).plus(15.minutes), // Starts 15m after task 2 ends
            duration = 1.hours + 45.minutes,
            progress = 0.0f,
            dependencies = listOf("2", "1"),
            color = Color(0xFFFF9800) // Orange
        ),
        GanttTask(
            "4", "Documentation",
            taskStartBaseline.plus(1.hours), // Independent task
            duration = 5000.seconds, // Approx 1h 23m
            progress = 0.1f,
            color = Color(0xFF9C27B0) // Purple
        ),
        GanttTask(
            "5", "Short Task",
            taskStartBaseline.plus(3.hours),
            duration = 90.seconds,
            progress = 0.5f,
            color = Color.Gray
        )
    )
    return GanttChartState(tasks, timeZone = tz)
}