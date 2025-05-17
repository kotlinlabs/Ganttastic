package io.github.kotlinlabs.ganttly.styles

import androidx.compose.ui.graphics.Color
import io.github.kotlinlabs.ganttly.models.GanttTask

/**
 * Coordinates group colors across the Gantt chart based on theme settings.
 * This is a singleton that will be used by both the state and the rendering components.
 */
object TaskGroupColorCoordinator {
    private val groupColorCache = mutableMapOf<String, Color>()

    /**
     * Get color for a group, following the priority order:
     * 1. Check if already in cache
     * 2. Use explicit theme mapping
     * 3. Use theme palette
     * 4. Use theme default color
     */
    fun getColorForGroup(
        groupName: String,
        allGroups: List<String>,
        themeColors: GanttColors
    ): Color {
        // Return cached color if available
        groupColorCache[groupName]?.let { return it }

        // Otherwise, get color from theme and cache it
        val color = themeColors.getColorForGroup(groupName, allGroups)
        groupColorCache[groupName] = color
        return color
    }

    /**
     * Update the task color based on its group
     */
    fun applyGroupColorToTask(
        task: GanttTask,
        allGroups: List<String>,
        themeColors: GanttColors
    ): GanttTask {
        // If task has no group, use the default color
        if (task.group.isEmpty()) {
            return task.copy(color = themeColors.defaultGroupColor)
        }

        // Get color for this group
        val groupColor = getColorForGroup(task.group, allGroups, themeColors)
        return task.copy(color = groupColor)
    }

    /**
     * Reset the color cache (useful when theme changes)
     */
    fun reset() {
        groupColorCache.clear()
    }
}