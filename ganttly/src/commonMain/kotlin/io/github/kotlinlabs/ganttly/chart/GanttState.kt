package io.github.kotlinlabs.ganttly.chart

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.kotlinlabs.ganttly.models.GanttTask
import io.github.kotlinlabs.ganttly.models.TimelineHeaderCell
import io.github.kotlinlabs.ganttly.models.TimelineViewInfo
import io.github.kotlinlabs.ganttly.styles.GanttColors
import io.github.kotlinlabs.ganttly.styles.TaskGroupColorCoordinator
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

    private val _originalTasks = mutableStateOf(initialTasks)
    private val _tasks = mutableStateOf(initialTasks)

    var tasks: List<GanttTask>
        get() = _tasks.value
        set(value) {
            _originalTasks.value = value
            // We'll update the colors when the theme is available
        }


    /**
     * Apply theme colors to tasks. This should be called whenever the theme changes.
     */
    fun applyThemeColors(themeColors: GanttColors) {
        val originalTasks = _originalTasks.value

        // Extract all unique groups
        val uniqueGroups = originalTasks
            .mapNotNull { task -> task.group.ifEmpty { null } }
            .distinct()

        // Apply group colors to tasks
        _tasks.value = originalTasks.map { task ->
            TaskGroupColorCoordinator.applyGroupColorToTask(task, uniqueGroups, themeColors)
        }
    }

    // Get a summary of groups and their colors - for the group info header
    fun getGroupInfo(): Map<String, Color> {
        val result = mutableMapOf<String, Color>()

        tasks.forEach { task ->
            if (task.group.isNotEmpty() && !result.containsKey(task.group)) {
                result[task.group] = task.color
            }
        }

        return result
    }



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
        // Calculate the total duration from the earliest to the latest task
        val totalViewDuration = viewStartDate.until(viewEndDate, DateTimeUnit.SECOND, timeZone).seconds

        // Calculate pixels per second to fit exactly in the available width
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

    // Create 30 tasks with dependencies and groups
    val tasks = listOf(
        // Phase 1 - Planning & Research
        GanttTask(
            "1", "1. Project Planning",
            taskStartBaseline,
            duration = 2.hours,
            progress = 1.0f,
            group = "Planning"
        ),
        GanttTask(
            "2", "2. Requirements Analysis",
            taskStartBaseline.plus(2.hours),
            duration = 3.hours,
            progress = 0.9f,
            dependencies = listOf("1"),
            group = "Planning"
        ),
        GanttTask(
            "3", "3. Market Research",
            taskStartBaseline.plus(3.hours),
            duration = 4.hours,
            progress = 0.8f,
            dependencies = listOf("1"),
            group = "Planning"
        ),
        GanttTask(
            "4", "4. Stakeholder Meeting",
            taskStartBaseline.plus(5.hours),
            duration = 1.hours + 30.minutes,
            progress = 1.0f,
            dependencies = listOf("2", "3"),
            group = "Planning"
        ),

        // Phase 2 - Design
        GanttTask(
            "5", "5. Architecture Design",
            taskStartBaseline.plus(7.hours),
            duration = 5.hours,
            progress = 0.7f,
            dependencies = listOf("4"),
            group = "Design"
        ),
        GanttTask(
            "6", "6. UI/UX Design",
            taskStartBaseline.plus(8.hours),
            duration = 6.hours,
            progress = 0.65f,
            dependencies = listOf("4"),
            group = "Design"
        ),
        GanttTask(
            "7", "7. Database Schema",
            taskStartBaseline.plus(10.hours),
            duration = 3.hours,
            progress = 0.6f,
            dependencies = listOf("5"),
            group = "Design"
        ),
        GanttTask(
            "8", "8. Design Review",
            taskStartBaseline.plus(14.hours),
            duration = 2.hours,
            progress = 0.5f,
            dependencies = listOf("5", "6", "7"),
            group = "Design"
        ),

        // Phase 3 - Development
        GanttTask(
            "9", "9. Backend Development",
            taskStartBaseline.plus(16.hours),
            duration = 8.hours,
            progress = 0.4f,
            dependencies = listOf("8"),
            group = "Development"
        ),
        GanttTask(
            "10", "10. Frontend Development",
            taskStartBaseline.plus(17.hours),
            duration = 7.hours,
            progress = 0.35f,
            dependencies = listOf("8"),
            group = "Development"
        ),
        GanttTask(
            "11", "11. API Integration",
            taskStartBaseline.plus(24.hours),
            duration = 4.hours,
            progress = 0.3f,
            dependencies = listOf("9"),
            group = "Development"
        ),
        GanttTask(
            "12", "12. Performance Optimization",
            taskStartBaseline.plus(28.hours),
            duration = 3.hours,
            progress = 0.2f,
            dependencies = listOf("11"),
            group = "Development"
        ),

        // Phase 4 - Testing
        GanttTask(
            "13", "13. Unit Testing",
            taskStartBaseline.plus(31.hours),
            duration = 4.hours,
            progress = 0.15f,
            dependencies = listOf("12"),
            group = "Testing"
        ),
        GanttTask(
            "14", "14. Integration Testing",
            taskStartBaseline.plus(35.hours),
            duration = 5.hours,
            progress = 0.1f,
            dependencies = listOf("13"),
            group = "Testing"
        ),
        GanttTask(
            "15", "15. User Acceptance Testing",
            taskStartBaseline.plus(40.hours),
            duration = 6.hours,
            progress = 0.05f,
            dependencies = listOf("14"),
            group = "Testing"
        ),

        // Phase 5 - Deployment & Documentation
        GanttTask(
            "16", "16. Deployment Planning",
            taskStartBaseline.plus(38.hours),
            duration = 3.hours,
            progress = 0.3f,
            dependencies = listOf("12"),
            group = "Deployment"
        ),
        GanttTask(
            "17", "17. Documentation",
            taskStartBaseline.plus(39.hours),
            duration = 8.hours,
            progress = 0.2f,
            dependencies = listOf("12"),
            group = "Documentation"
        ),
        GanttTask(
            "18", "18. Deployment to Staging",
            taskStartBaseline.plus(46.hours),
            duration = 2.hours,
            progress = 0.0f,
            dependencies = listOf("15", "16"),
            group = "Deployment"
        ),
        GanttTask(
            "19", "19. Final Review",
            taskStartBaseline.plus(48.hours),
            duration = 2.hours,
            progress = 0.0f,
            dependencies = listOf("17", "18"),
            group = "Documentation"
        ),
        GanttTask(
            "20", "20. Production Deployment",
            taskStartBaseline.plus(50.hours),
            duration = 3.hours,
            progress = 0.0f,
            dependencies = listOf("19"),
            group = "Deployment"
        ),

        // Phase 6 - Post-Deployment
        GanttTask(
            "21", "21. Post-Deploy Verification",
            taskStartBaseline.plus(53.hours),
            duration = 2.hours,
            progress = 0.0f,
            dependencies = listOf("20"),
            group = "Deployment"
        ),
        GanttTask(
            "22", "22. Performance Monitoring",
            taskStartBaseline.plus(55.hours),
            duration = 4.hours,
            progress = 0.0f,
            dependencies = listOf("21"),
            group = "Maintenance"
        ),
        GanttTask(
            "23", "23. Feedback Collection",
            taskStartBaseline.plus(56.hours),
            duration = 6.hours,
            progress = 0.0f,
            dependencies = listOf("20"),
            group = "Maintenance"
        ),

        // Phase 7 - Maintenance
        GanttTask(
            "24", "24. Hotfix Planning",
            taskStartBaseline.plus(59.hours),
            duration = 2.hours,
            progress = 0.0f,
            dependencies = listOf("22", "23"),
            group = "Maintenance"
        ),
        GanttTask(
            "25", "25. Bug Fix Implementation",
            taskStartBaseline.plus(61.hours),
            duration = 5.hours,
            progress = 0.0f,
            dependencies = listOf("24"),
            group = "Maintenance"
        ),
        GanttTask(
            "26", "26. Regression Testing",
            taskStartBaseline.plus(66.hours),
            duration = 3.hours,
            progress = 0.0f,
            dependencies = listOf("25"),
            group = "Testing"
        ),

        // Phase 8 - Enhancements
        GanttTask(
            "27", "27. Enhancement Planning",
            taskStartBaseline.plus(64.hours),
            duration = 4.hours,
            progress = 0.0f,
            dependencies = listOf("23"),
            group = "Planning"
        ),
        GanttTask(
            "28", "28. Feature Development",
            taskStartBaseline.plus(69.hours),
            duration = 8.hours,
            progress = 0.0f,
            dependencies = listOf("26", "27"),
            group = "Development"
        ),
        GanttTask(
            "29", "29. Security Audit",
            taskStartBaseline.plus(72.hours),
            duration = 5.hours,
            progress = 0.0f,
            dependencies = listOf("26"),
            group = "Testing"
        ),
        GanttTask(
            "30", "30. Version 2.0 Release",
            taskStartBaseline.plus(77.hours),
            duration = 2.hours,
            progress = 0.0f,
            dependencies = listOf("28", "29"),
            group = "Deployment"
        )
    )

    return GanttChartState(tasks, timeZone = tz)
}