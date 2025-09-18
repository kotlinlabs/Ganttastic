@file:OptIn(ExperimentalTime::class)

package io.github.kotlinlabs.ganttly.models

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.until
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class GanttTask(
    val id: String,
    val name: String,
    val startDate: Instant,
    val duration: Duration, // For leaf tasks (no children)
    val progress: Float = 0f,
    val dependencies: List<String> = emptyList(),
    val group: String = "",
    internal val color: Color = Color(0xFF4CAF50),
    val children: List<GanttTask> = emptyList(),
    val isExpanded: Boolean = true, // Whether subtasks are visible
    val level: Int = 0
) {
    // For parent tasks, the endDate is determined by the latest endDate of children
    val endDate: Instant by lazy {
        if (children.isEmpty()) {
            // For tasks without children, simply use the task's own duration
            startDate + duration
        } else {
            // For parent tasks, find the latest end date among children
            children.maxOf { it.endDate }
        }
    }

    // The effective duration is calculated from startDate to endDate
    val effectiveDuration: Duration by lazy {
        if (children.isEmpty()) {
            duration
        } else {
            // For parent tasks, calculate based on the latest child end date
            val timeSpan = startDate.until(endDate, DateTimeUnit.NANOSECOND, TimeZone.UTC)
            timeSpan.nanoseconds
        }
    }

    // Return all subtasks recursively (flattened)
    fun getAllSubtasks(): List<GanttTask> {
        return children + children.flatMap { it.getAllSubtasks() }
    }

    // Whether this task has any children
    val hasChildren: Boolean get() = children.isNotEmpty()

    init {
        // Validate direct duration for leaf tasks (no children)
        if (children.isEmpty()) {
            require(duration.isPositive()) { "Task duration must be positive." }
            require(duration.inWholeHours <= 24) { "Task duration cannot exceed 24 hours." }
        }

        // For parent tasks, ensure children start dates are not before parent start
        if (children.isNotEmpty()) {
            children.forEach { child ->
                require(child.startDate >= startDate) {
                    "Child task '${child.name}' cannot start before parent task '${name}'"
                }
            }
        }
    }

    // Create a copy with expanded/collapsed state toggled
    fun toggleExpanded(): GanttTask {
        return copy(isExpanded = !isExpanded)
    }

    companion object {
        /**
         * Creates a parent task with children, ensuring the parent's duration
         * encompasses all children
         */
        fun createParentTask(
            id: String,
            name: String,
            startDate: Instant,
            children: List<GanttTask>,
            progress: Float = 0f,
            dependencies: List<String> = emptyList(),
            group: String = "",
            color: Color = Color(0xFF4CAF50),
            isExpanded: Boolean = true,
            level: Int = 0
        ): GanttTask {
            // Ensure children is not empty
            require(children.isNotEmpty()) { "Parent task must have at least one child" }

            // Calculate the earliest start date from children (should be >= startDate)
            val earliestChildStart = children.minOf { it.startDate }
            val effectiveStartDate = minOf(startDate, earliestChildStart)

            // Calculate the latest end date from children
            val latestChildEnd = children.maxOf { it.endDate }

            // Create a dummy duration that won't be used directly
            // (the effectiveDuration property will calculate based on children)
            val placeholderDuration = latestChildEnd - effectiveStartDate

            // Create a list of children with updated levels
            val updatedChildren = children.map { child ->
                child.copy(level = level + 1)
            }

            return GanttTask(
                id = id,
                name = name,
                startDate = effectiveStartDate,
                duration = placeholderDuration, // This won't be used directly
                progress = progress,
                dependencies = dependencies,
                group = group,
                color = color,
                children = updatedChildren,
                isExpanded = isExpanded,
                level = level
            )
        }

    }
}

// Represents a single unit in the timeline header
data class TimelineHeaderCell(
    val label: String,
    val startDate: Instant,
    val endDate: Instant,
    val widthPx: Float // Calculated width in pixels for this cell
)

// Represents the overall visible timeline information
data class TimelineViewInfo(
    val viewStartDate: Instant,
    val viewEndDate: Instant,
    val totalViewDuration: Duration, // Total duration displayed in the view
    val pixelsPerSecond: Double, // Determines zoom level. How many pixels represent one second.
    val headerCells: List<TimelineHeaderCell> // Pre-calculated header cells
)

// Represents hover information for timeline vertical line
data class TimelineHoverInfo(
    val xPosition: Float,
    val time: String // formatted time in hh:mm format
)
