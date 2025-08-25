@file:OptIn(ExperimentalTime::class)

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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.until
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
    // Store the original hierarchical tasks
    private val _hierarchicalTasks = mutableStateOf(initialTasks)

    // Store the flattened tasks for display
    private val _flattenedTasks = mutableStateOf(flattenTasks(initialTasks))

    // Track if all tasks should be expanded initially
    private val _expandAllByDefault = mutableStateOf(true)

    // Validation result
    private val _validationErrors = mutableStateOf<List<String>>(emptyList())
    val validationErrors: List<String> get() = _validationErrors.value

    init {
        // Validate the initial task hierarchy
        _validationErrors.value = validateTaskHierarchy(initialTasks)
    }

    // Public accessor for the flattened tasks (used by UI)
    var tasks: List<GanttTask>
        get() = _flattenedTasks.value
        set(value) {
            setHierarchicalTasks(value)
        }

    // Access to the hierarchical tasks (for certain operations)
    val hierarchicalTasks: List<GanttTask> get() = _hierarchicalTasks.value

    // Set hierarchical tasks and update flattened view
    fun setHierarchicalTasks(newTasks: List<GanttTask>) {
        _hierarchicalTasks.value = newTasks
        _flattenedTasks.value = flattenTasks(newTasks)
        _validationErrors.value = validateTaskHierarchy(newTasks)
    }

    // Toggle a task's expanded state
    fun toggleTaskExpansion(taskId: String) {
        val updatedTasks = updateTaskExpansion(_hierarchicalTasks.value, taskId)
        _hierarchicalTasks.value = updatedTasks
        _flattenedTasks.value = flattenTasks(updatedTasks)
    }

    // Expand or collapse all tasks
    fun setAllTasksExpanded(expanded: Boolean) {
        _expandAllByDefault.value = expanded
        val updatedTasks = updateAllTasksExpansion(_hierarchicalTasks.value, expanded)
        _hierarchicalTasks.value = updatedTasks
        _flattenedTasks.value = flattenTasks(updatedTasks)
    }

    // Create a task at specified parent path
    fun createTask(
        parentPath: List<String>,
        newTask: GanttTask
    ): Boolean {
        if (parentPath.isEmpty()) {
            // Add at root level
            _hierarchicalTasks.value = _hierarchicalTasks.value + newTask
            _flattenedTasks.value = flattenTasks(_hierarchicalTasks.value)
            return true
        }

        // Navigate to parent and add child
        val updatedTasks = addTaskAtPath(_hierarchicalTasks.value, parentPath, 0, newTask)
        if (updatedTasks != null) {
            _hierarchicalTasks.value = updatedTasks
            _flattenedTasks.value = flattenTasks(updatedTasks)
            return true
        }
        return false
    }

    // Update a specific task
    fun updateTask(taskId: String, updater: (GanttTask) -> GanttTask): Boolean {
        val updatedTasks = updateTaskById(_hierarchicalTasks.value, taskId, updater)
        if (updatedTasks != null) {
            _hierarchicalTasks.value = updatedTasks
            _flattenedTasks.value = flattenTasks(updatedTasks)
            _validationErrors.value = validateTaskHierarchy(updatedTasks)
            return true
        }
        return false
    }

    // Delete a task and its children
    fun deleteTask(taskId: String): Boolean {
        val updatedTasks = deleteTaskById(_hierarchicalTasks.value, taskId)
        if (updatedTasks != _hierarchicalTasks.value) {
            _hierarchicalTasks.value = updatedTasks
            _flattenedTasks.value = flattenTasks(updatedTasks)
            return true
        }
        return false
    }

    // Move a task to a new parent
    fun moveTask(taskId: String, newParentId: String?): Boolean {
        // First find and remove the task
        var taskToMove: GanttTask? = null
        val tasksWithoutMoved = _hierarchicalTasks.value.flatMap {
            findAndRemoveTask(it, taskId) { found -> taskToMove = found }
        }

        if (taskToMove == null) return false

        if (newParentId == null) {
            // Add to root level
            _hierarchicalTasks.value = tasksWithoutMoved + taskToMove!!
            _flattenedTasks.value = flattenTasks(_hierarchicalTasks.value)
            return true
        } else {
            // Add as child of specified parent
            val updatedTasks = addTaskToParent(tasksWithoutMoved, newParentId, taskToMove!!)
            if (updatedTasks != null) {
                _hierarchicalTasks.value = updatedTasks
                _flattenedTasks.value = flattenTasks(updatedTasks)
                _validationErrors.value = validateTaskHierarchy(updatedTasks)
                return true
            }
        }

        // If we reach here, the operation failed
        _flattenedTasks.value = flattenTasks(_hierarchicalTasks.value)
        return false
    }

    // Helper function to recursively update a task's expansion state
    private fun updateTaskExpansion(tasks: List<GanttTask>, taskId: String): List<GanttTask> {
        return tasks.map { task ->
            if (task.id == taskId) {
                task.toggleExpanded()
            } else if (task.hasChildren) {
                task.copy(children = updateTaskExpansion(task.children, taskId))
            } else {
                task
            }
        }
    }

    // Helper function to recursively set expansion state for all tasks
    private fun updateAllTasksExpansion(tasks: List<GanttTask>, expanded: Boolean): List<GanttTask> {
        return tasks.map { task ->
            if (task.hasChildren) {
                task.copy(
                    isExpanded = expanded,
                    children = updateAllTasksExpansion(task.children, expanded)
                )
            } else {
                task
            }
        }
    }

    // Helper function to add a task at a specific path
    private fun addTaskAtPath(
        tasks: List<GanttTask>,
        parentPath: List<String>,
        pathIndex: Int,
        newTask: GanttTask
    ): List<GanttTask>? {
        if (pathIndex >= parentPath.size) return null

        val parentId = parentPath[pathIndex]

        val updatedTasks = tasks.map { task ->
            if (task.id == parentId) {
                if (pathIndex == parentPath.size - 1) {
                    // We've reached the target parent
                    val updatedChildren = task.children + newTask

                    // If parent has no children yet, make sure it's expanded
                    val shouldBeExpanded = task.isExpanded || task.children.isEmpty()

                    // Update the parent with the new child
                    if (task.hasChildren) {
                        // For existing parent, just add the child
                        task.copy(
                            children = updatedChildren,
                            isExpanded = shouldBeExpanded
                        )
                    } else {
                        // For a leaf becoming a parent, use createParentTask
                        GanttTask.createParentTask(
                            id = task.id,
                            name = task.name,
                            startDate = task.startDate,
                            children = updatedChildren,
                            progress = task.progress,
                            dependencies = task.dependencies,
                            group = task.group,
                            color = task.color,
                            isExpanded = shouldBeExpanded,
                            level = task.level
                        )
                    }
                } else {
                    // Navigate deeper
                    val updatedChildren = addTaskAtPath(
                        task.children,
                        parentPath,
                        pathIndex + 1,
                        newTask
                    )

                    if (updatedChildren != null) {
                        task.copy(children = updatedChildren)
                    } else {
                        task
                    }
                }
            } else {
                task
            }
        }

        // Check if any task was updated
        return if (updatedTasks == tasks) null else updatedTasks
    }

    // Helper to update a task by ID
    private fun updateTaskById(
        tasks: List<GanttTask>,
        taskId: String,
        updater: (GanttTask) -> GanttTask
    ): List<GanttTask>? {
        var updated = false

        val updatedTasks = tasks.map { task ->
            if (task.id == taskId) {
                updated = true
                updater(task)
            } else if (task.hasChildren) {
                val updatedChildren = updateTaskById(task.children, taskId, updater)
                if (updatedChildren != null) {
                    updated = true
                    task.copy(children = updatedChildren)
                } else {
                    task
                }
            } else {
                task
            }
        }

        return if (updated) updatedTasks else null
    }

    // Helper to delete a task by ID
    private fun deleteTaskById(tasks: List<GanttTask>, taskId: String): List<GanttTask> {
        val updatedTasks = tasks.flatMap { task ->
            if (task.id == taskId) {
                // Skip this task (delete it)
                emptyList()
            } else if (task.hasChildren) {
                // Process children recursively
                val updatedChildren = deleteTaskById(task.children, taskId)
                if (updatedChildren.isEmpty()) {
                    // All children were deleted, convert to a leaf task
                    listOf(task.copy(children = emptyList()))
                } else if (updatedChildren != task.children) {
                    // Some children were updated
                    listOf(task.copy(children = updatedChildren))
                } else {
                    // No changes
                    listOf(task)
                }
            } else {
                // Regular leaf task, keep as is
                listOf(task)
            }
        }

        return updatedTasks
    }

    // Helper to find and remove a task
    private fun findAndRemoveTask(
        task: GanttTask,
        taskId: String,
        onFound: (GanttTask) -> Unit
    ): List<GanttTask> {
        if (task.id == taskId) {
            onFound(task)
            return emptyList()
        }

        if (!task.hasChildren) return listOf(task)

        val updatedChildren = task.children.flatMap {
            findAndRemoveTask(it, taskId, onFound)
        }

        return if (updatedChildren != task.children) {
            listOf(task.copy(children = updatedChildren))
        } else {
            listOf(task)
        }
    }

    // Helper to add a task to a parent
    private fun addTaskToParent(
        tasks: List<GanttTask>,
        parentId: String,
        taskToAdd: GanttTask
    ): List<GanttTask>? {
        var updated = false

        val updatedTasks = tasks.map { task ->
            if (task.id == parentId) {
                updated = true

                if (task.hasChildren) {
                    // Add to existing parent
                    task.copy(
                        children = task.children + taskToAdd.copy(level = task.level + 1),
                        isExpanded = true // Expand the parent to show the new child
                    )
                } else {
                    // Convert leaf to parent
                    GanttTask.createParentTask(
                        id = task.id,
                        name = task.name,
                        startDate = task.startDate,
                        children = listOf(taskToAdd.copy(level = task.level + 1)),
                        progress = task.progress,
                        dependencies = task.dependencies,
                        group = task.group,
                        color = task.color,
                        isExpanded = true,
                        level = task.level
                    )
                }
            } else if (task.hasChildren) {
                val updatedChildren = addTaskToParent(task.children, parentId, taskToAdd)
                if (updatedChildren != null) {
                    updated = true
                    task.copy(children = updatedChildren)
                } else {
                    task
                }
            } else {
                task
            }
        }

        return if (updated) updatedTasks else null
    }

    // Apply theme colors to tasks
    fun applyThemeColors(themeColors: GanttColors) {
        val hierarchicalTasks = _hierarchicalTasks.value

        // Extract all unique groups
        val uniqueGroups = hierarchicalTasks
            .flatMap { it.getAllSubtasks() + it }
            .mapNotNull { task -> task.group.ifEmpty { null } }
            .distinct()

        // Apply group colors to tasks
        val coloredTasks = applyColorsRecursively(hierarchicalTasks, uniqueGroups, themeColors)
        _hierarchicalTasks.value = coloredTasks
        _flattenedTasks.value = flattenTasks(coloredTasks)
    }

    // Helper to apply colors recursively to the task hierarchy
    private fun applyColorsRecursively(
        tasks: List<GanttTask>,
        uniqueGroups: List<String>,
        themeColors: GanttColors
    ): List<GanttTask> {
        return tasks.map { task ->
            val coloredTask = TaskGroupColorCoordinator.applyGroupColorToTask(task, uniqueGroups, themeColors)

            if (task.hasChildren) {
                coloredTask.copy(children = applyColorsRecursively(task.children, uniqueGroups, themeColors))
            } else {
                coloredTask
            }
        }
    }

    // Get a summary of groups and their colors - for the group info header
    fun getGroupInfo(): Map<String, Color> {
        val result = mutableMapOf<String, Color>()

        // Include groups from all tasks (including subtasks)
        _hierarchicalTasks.value.forEach { task ->
            addGroupColors(task, result)
        }

        return result
    }

    private fun addGroupColors(task: GanttTask, groupMap: MutableMap<String, Color>) {
        if (task.group.isNotEmpty() && !groupMap.containsKey(task.group)) {
            groupMap[task.group] = task.color
        }

        task.children.forEach { child ->
            addGroupColors(child, groupMap)
        }
    }

    // Calculate task counts per level (for UI metrics)
    fun getTaskCountsByLevel(): Map<Int, Int> {
        val countsByLevel = mutableMapOf<Int, Int>()

        fun countTasksAtLevel(task: GanttTask) {
            val level = task.level
            countsByLevel[level] = (countsByLevel[level] ?: 0) + 1

            if (task.hasChildren) {
                task.children.forEach { countTasksAtLevel(it) }
            }
        }

        _hierarchicalTasks.value.forEach { countTasksAtLevel(it) }
        return countsByLevel
    }

    // Get the maximum depth of the task hierarchy
    val maxHierarchyDepth: Int
        get() {
            val counts = getTaskCountsByLevel()
            return if (counts.isEmpty()) 0 else counts.keys.maxOrNull() ?: 0
        }

    // The overall earliest start and latest end of all tasks
    private val projectStartDate by derivedStateOf {
        getAllTasksFlattened().minOfOrNull { it.startDate } ?: Clock.System.now()
    }

    private val projectEndDate by derivedStateOf {
        getAllTasksFlattened().maxOfOrNull { it.endDate } ?: (projectStartDate + 1.hours)
    }

    // Helper to get all tasks flattened (including hidden ones)
    private fun getAllTasksFlattened(): List<GanttTask> {
        return _hierarchicalTasks.value.flatMap { task ->
            listOf(task) + task.getAllSubtasks()
        }
    }

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


    // Auto-calculate progress for parent tasks based on children
    fun recalculateParentProgress() {
        val updatedTasks = recalculateParentProgressRecursive(_hierarchicalTasks.value)
        _hierarchicalTasks.value = updatedTasks
        _flattenedTasks.value = flattenTasks(updatedTasks)
    }

    private fun recalculateParentProgressRecursive(tasks: List<GanttTask>): List<GanttTask> {
        return tasks.map { task ->
            if (task.hasChildren) {
                // Process children first
                val updatedChildren = recalculateParentProgressRecursive(task.children)

                // Calculate progress based on children
                val newProgress = calculateParentProgress(updatedChildren)

                task.copy(
                    children = updatedChildren,
                    progress = newProgress
                )
            } else {
                task
            }
        }
    }

    // Initialize by fitting all tasks
    init {
        fitTasksToView()
        recalculateParentProgress() // Ensure parent progress is synchronized with children
    }
}

// Helper function to flatten tasks based on their expansion state
fun flattenTasks(tasks: List<GanttTask>): List<GanttTask> {
    val result = mutableListOf<GanttTask>()

    tasks.forEach { task ->
        // Add the parent task
        result.add(task)

        // Add children if expanded
        if (task.isExpanded && task.hasChildren) {
            result.addAll(flattenTasks(task.children))
        }
    }

    return result
}


/**
 * Calculate progress of a parent task based on its children
 */
fun calculateParentProgress(children: List<GanttTask>): Float {
    if (children.isEmpty()) return 0f

    // Sum the weighted progress of each child task
    val totalDuration = children.sumOf { it.effectiveDuration.inWholeSeconds.toDouble() }
    if (totalDuration <= 0) return 0f

    // Calculate weighted progress
    var weightedProgress = 0.0
    children.forEach { child ->
        val weight = child.effectiveDuration.inWholeSeconds / totalDuration
        weightedProgress += child.progress * weight
    }

    return weightedProgress.toFloat()
}

fun validateTaskHierarchy(tasks: List<GanttTask>): List<String> {
    val validationErrors = mutableListOf<String>()

    fun validateTask(task: GanttTask) {
        if (task.hasChildren) {
            // Check if all children are within parent timespan
            val earliestChildStart = task.children.minOf { it.startDate }
            if (earliestChildStart < task.startDate) {
                validationErrors.add(
                    "Parent task '${task.name}' starts at ${task.startDate} but has a child " +
                            "starting earlier at $earliestChildStart"
                )
            }

            val latestChildEnd = task.children.maxOf { it.endDate }
            if (latestChildEnd > task.endDate) {
                validationErrors.add(
                    "Parent task '${task.name}' ends at ${task.endDate} but has a child " +
                            "ending later at $latestChildEnd"
                )
            }

            // Recursively validate children
            task.children.forEach { validateTask(it) }
        }
    }

    tasks.forEach { validateTask(it) }
    return validationErrors
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
            "1", "Project Planning",
            taskStartBaseline,
            duration = 2.hours,
            progress = 1.0f,
            group = "Planning"
        ),
        GanttTask(
            "2", "Requirements Analysis",
            taskStartBaseline.plus(2.hours),
            duration = 3.hours,
            progress = 0.9f,
            dependencies = listOf("1"),
            group = "Planning"
        ),
        GanttTask(
            "3", "Market Research",
            taskStartBaseline.plus(3.hours),
            duration = 4.hours,
            progress = 0.8f,
            dependencies = listOf("1"),
            group = "Planning"
        ),
        GanttTask(
            "4", "Stakeholder Meeting",
            taskStartBaseline.plus(5.hours),
            duration = 1.hours + 30.minutes,
            progress = 1.0f,
            dependencies = listOf("2", "3"),
            group = "Planning"
        ),

        // Phase 2 - Design
        GanttTask(
            "5", "Architecture Design",
            taskStartBaseline.plus(7.hours),
            duration = 5.hours,
            progress = 0.7f,
            dependencies = listOf("4"),
            group = "Design"
        ),
        GanttTask(
            "6", "UI/UX Design",
            taskStartBaseline.plus(8.hours),
            duration = 6.hours,
            progress = 0.65f,
            dependencies = listOf("4"),
            group = "Design"
        ),
        GanttTask(
            "7", "Database Schema",
            taskStartBaseline.plus(10.hours),
            duration = 3.hours,
            progress = 0.6f,
            dependencies = listOf("5"),
            group = "Design"
        ),
        GanttTask(
            "8", "Design Review",
            taskStartBaseline.plus(14.hours),
            duration = 2.hours,
            progress = 0.5f,
            dependencies = listOf("5", "6", "7"),
            group = "Design"
        ),

        // Phase 3 - Development
        GanttTask(
            "9", "Backend Development",
            taskStartBaseline.plus(16.hours),
            duration = 8.hours,
            progress = 0.4f,
            dependencies = listOf("8"),
            group = "Development"
        ),
        GanttTask(
            "10", "Frontend Development",
            taskStartBaseline.plus(17.hours),
            duration = 7.hours,
            progress = 0.35f,
            dependencies = listOf("8"),
            group = "Development"
        ),
        GanttTask(
            "11", "API Integration",
            taskStartBaseline.plus(24.hours),
            duration = 4.hours,
            progress = 0.3f,
            dependencies = listOf("9"),
            group = "Development"
        ),
        GanttTask(
            "12", "Performance Optimization",
            taskStartBaseline.plus(28.hours),
            duration = 3.hours,
            progress = 0.2f,
            dependencies = listOf("11"),
            group = "Development"
        ),

        // Phase 4 - Testing
        GanttTask(
            "13", "Unit Testing",
            taskStartBaseline.plus(31.hours),
            duration = 4.hours,
            progress = 0.15f,
            dependencies = listOf("12"),
            group = "Testing"
        ),
        GanttTask(
            "14", "Integration Testing",
            taskStartBaseline.plus(35.hours),
            duration = 5.hours,
            progress = 0.1f,
            dependencies = listOf("13"),
            group = "Testing"
        ),
        GanttTask(
            "15", "User Acceptance Testing",
            taskStartBaseline.plus(40.hours),
            duration = 6.hours,
            progress = 0.05f,
            dependencies = listOf("14"),
            group = "Testing"
        ),

        // Phase 5 - Deployment & Documentation
        GanttTask(
            "16", "Deployment Planning",
            taskStartBaseline.plus(38.hours),
            duration = 3.hours,
            progress = 0.3f,
            dependencies = listOf("12"),
            group = "Deployment"
        ),
        GanttTask(
            "17", "Documentation",
            taskStartBaseline.plus(39.hours),
            duration = 8.hours,
            progress = 0.2f,
            dependencies = listOf("12"),
            group = "Documentation"
        ),
        GanttTask(
            "18", "Deployment to Staging",
            taskStartBaseline.plus(46.hours),
            duration = 2.hours,
            progress = 0.0f,
            dependencies = listOf("15", "16"),
            group = "Deployment"
        ),
        GanttTask(
            "19", "Final Review",
            taskStartBaseline.plus(48.hours),
            duration = 2.hours,
            progress = 0.0f,
            dependencies = listOf("17", "18"),
            group = "Documentation"
        ),
        GanttTask(
            "20", "Production Deployment",
            taskStartBaseline.plus(50.hours),
            duration = 3.hours,
            progress = 0.0f,
            dependencies = listOf("19"),
            group = "Deployment"
        ),

        // Phase 6 - Post-Deployment
        GanttTask(
            "21", "Post-Deploy Verification",
            taskStartBaseline.plus(53.hours),
            duration = 2.hours,
            progress = 0.0f,
            dependencies = listOf("20"),
            group = "Deployment"
        ),
        GanttTask(
            "22", "Performance Monitoring",
            taskStartBaseline.plus(55.hours),
            duration = 4.hours,
            progress = 0.0f,
            dependencies = listOf("21"),
            group = "Maintenance"
        ),
        GanttTask(
            "23", "Feedback Collection",
            taskStartBaseline.plus(56.hours),
            duration = 6.hours,
            progress = 0.0f,
            dependencies = listOf("20"),
            group = "Maintenance"
        ),

        // Phase 7 - Maintenance
        GanttTask(
            "24", "Hotfix Planning",
            taskStartBaseline.plus(59.hours),
            duration = 2.hours,
            progress = 0.0f,
            dependencies = listOf("22", "23"),
            group = "Maintenance"
        ),
        GanttTask(
            "25", "Bug Fix Implementation",
            taskStartBaseline.plus(61.hours),
            duration = 5.hours,
            progress = 0.0f,
            dependencies = listOf("24"),
            group = "Maintenance"
        ),
        GanttTask(
            "26", "Regression Testing",
            taskStartBaseline.plus(66.hours),
            duration = 3.hours,
            progress = 0.0f,
            dependencies = listOf("25"),
            group = "Testing"
        ),

        // Phase 8 - Enhancements
        GanttTask(
            "27", "Enhancement Planning",
            taskStartBaseline.plus(64.hours),
            duration = 4.hours,
            progress = 0.0f,
            dependencies = listOf("23"),
            group = "Planning"
        ),
        GanttTask(
            "28", "Feature Development",
            taskStartBaseline.plus(69.hours),
            duration = 8.hours,
            progress = 0.0f,
            dependencies = listOf("26", "27"),
            group = "Development"
        ),
        GanttTask(
            "29", "Security Audit",
            taskStartBaseline.plus(72.hours),
            duration = 5.hours,
            progress = 0.0f,
            dependencies = listOf("26"),
            group = "Testing"
        ),
        GanttTask(
            "30", "Version 2.0 Release",
            taskStartBaseline.plus(77.hours),
            duration = 2.hours,
            progress = 0.0f,
            dependencies = listOf("28", "29"),
            group = "Deployment"
        )
    )

    return GanttChartState(tasks, timeZone = tz)
}

fun createSampleNestedGanttState(): GanttChartState {
    val now = Clock.System.now()
    val tz = TimeZone.currentSystemDefault()

    val taskStartBaseline = now.toLocalDateTime(tz).let {
        Instant.parse("${it.date}T${it.hour.toString().padStart(2, '0')}:00:00Z") // Start of current hour UTC
    }

    // First, create the leaf tasks (tasks without children)
    // Planning subtasks
    val requirementsTask = GanttTask(
        "1.1", "Requirements Analysis",
        taskStartBaseline,
        duration = 2.hours,
        progress = 1.0f,
        group = "Planning"
    )

    val marketResearchTask = GanttTask(
        "1.2", "Market Research",
        taskStartBaseline.plus(2.hours),
        duration = 3.hours,
        progress = 0.7f,
        group = "Planning",
        dependencies = listOf("1.1")
    )

    val stakeholderMeetingTask = GanttTask(
        "1.3", "Stakeholder Meeting",
        taskStartBaseline.plus(5.hours),
        duration = 1.hours,
        progress = 0.5f,
        group = "Planning",
        dependencies = listOf("1.2")
    )

    // Design subtasks
    val architectureDesignTask = GanttTask(
        "2.1", "Architecture Design",
        taskStartBaseline.plus(6.hours),
        duration = 4.hours,
        progress = 0.9f,
        group = "Design"
    )

    // UI/UX subtasks
    val wireframesTask = GanttTask(
        "2.2.1", "Wireframes",
        taskStartBaseline.plus(8.hours),
        duration = 2.hours,
        progress = 1.0f,
        group = "Design"
    )

    val mockupsTask = GanttTask(
        "2.2.2", "Mockups",
        taskStartBaseline.plus(10.hours),
        duration = 3.hours,
        progress = 0.3f,
        group = "Design",
        dependencies = listOf("2.2.1")
    )

    val userTestingTask = GanttTask(
        "2.2.3", "User Testing",
        taskStartBaseline.plus(13.hours),
        duration = 1.hours,
        progress = 0.0f,
        group = "Design",
        dependencies = listOf("2.2.2")
    )

    // Now build up the hierarchy from bottom to top
    // UI/UX Design parent task (contains wireframes, mockups, testing)
    val uiUxDesignTask = GanttTask.createParentTask(
        "2.2", "UI/UX Design",
        taskStartBaseline.plus(8.hours),
        children = listOf(wireframesTask, mockupsTask, userTestingTask),
        progress = 0.5f,
        group = "Design"
    )

    val dbSchemaTask = GanttTask(
        "2.3", "Database Schema",
        taskStartBaseline.plus(10.hours),
        duration = 3.hours,
        progress = 0.4f,
        group = "Design",
        dependencies = listOf("2.1")
    )

    val designReviewTask = GanttTask(
        "2.4", "Design Review",
        taskStartBaseline.plus(14.hours),
        duration = 2.hours,
        progress = 0.0f,
        group = "Design",
        dependencies = listOf("2.2", "2.3")
    )

    // Design parent task (contains architecture, UI/UX, schema, review)
    val designTask = GanttTask.createParentTask(
        "2", "Design",
        taskStartBaseline.plus(6.hours),
        children = listOf(
            architectureDesignTask,
            uiUxDesignTask,
            dbSchemaTask,
            designReviewTask
        ),
        progress = 0.6f,
        group = "Design",
        dependencies = listOf("1")
    )

    // Planning parent task
    val planningTask = GanttTask.createParentTask(
        "1", "Project Planning",
        taskStartBaseline,
        children = listOf(
            requirementsTask,
            marketResearchTask,
            stakeholderMeetingTask
        ),
        progress = 0.8f,
        group = "Planning"
    )

    // Top-level tasks
    val tasks = listOf(planningTask, designTask)

    return GanttChartState(tasks, timeZone = tz)
}