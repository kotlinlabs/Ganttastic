package com.karthyks.ganttastic.chart

// In a separate file, e.g., model/Task.kt or within your jsMain source set
data class GanttTask(
    val id: String,
    val name: String,
    val startDate: Long, // Consider using kotlinx-datetime for richer date/time handling
    val endDate: Long,
    val progress: Float = 0f, // 0.0 to 1.0
    val dependencies: List<String> = emptyList() // List of task IDs this task depends on
)

// Potentially a class for the overall project or view model
data class GanttProject(
    val tasks: List<GanttTask>,
    val viewStartDate: Long,
    val viewEndDate: Long
)