package io.github.kotlinlabs.ganttly.models

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.Instant
import kotlin.time.Duration

data class GanttTask(
    val id: String,
    val name: String,
    val startDate: Instant,
    val duration: Duration, // Use kotlinx.time.Duration
    val progress: Float = 0f, // 0.0 to 1.0
    val dependencies: List<String> = emptyList(), // List of task IDs this task depends on
    val color: Color = Color(0xFF4CAF50) // Default task color
) {
    val endDate: Instant by lazy { startDate + duration }

    init {
        require(duration.isPositive()) { "Task duration must be positive." }
        // Max duration: 24 hours (can be adjusted)
        require(duration.inWholeHours <= 24) { "Task duration cannot exceed 24 hours." }
    }

    // Helper properties for convenience if needed elsewhere, though calculations should use 'duration'
    val durationInSeconds: Long get() = duration.inWholeSeconds
    val durationInMinutes: Long get() = duration.inWholeMinutes
    val durationInHours: Long get() = duration.inWholeHours
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

// Enum to define the primary unit for timeline scaling
enum class TimeScaleDisplayUnit {
    SECONDS, // Show individual seconds, or groups of seconds
    MINUTES, // Show individual minutes, or groups of minutes (e.g., 5, 10, 15, 30 min intervals)
    HOURS    // Show individual hours
}