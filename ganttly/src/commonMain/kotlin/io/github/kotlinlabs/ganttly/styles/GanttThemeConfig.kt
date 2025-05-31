package io.github.kotlinlabs.ganttly.styles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Main theme class that holds all theme configuration
 */
class GanttThemeConfig {
    var colors = GanttColors()
    var styles = GanttStyles()
    var naming = GanttNaming()

    // Builder functions for DSL
    fun colors(init: GanttColors.() -> Unit) {
        colors = GanttColors().apply(init)
    }

    fun styles(init: GanttStyles.() -> Unit) {
        styles = GanttStyles().apply(init)
    }

    fun naming(init: GanttNaming.() -> Unit) {
        naming = GanttNaming().apply(init)
    }
}

/**
 * Colors configuration for Gantt chart
 */
class GanttColors {
    // Group color palette - will be used for automatic assignment
    var groupColorPalette: List<Color> = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFFE91E63), // Pink
        Color(0xFF00BCD4), // Cyan
        Color(0xFFFFEB3B), // Yellow
        Color(0xFF795548), // Brown
        Color(0xFF607D8B), // Blue Grey
        Color(0xFFFF5722)  // Deep Orange
    )

    // Explicit group color mapping (from the DSL)
    var groupColors: Map<String, Color> = emptyMap()
    var defaultGroupColor: Color = Color(0xFF4CAF50)

    // Task bar appearance
    var taskBarBackground: (Color, Boolean) -> Color = { baseColor, isHovered ->
        baseColor.copy(alpha = if (isHovered) 0.4f else 0.2f)
    }
    var taskBarBorder: (Color, Boolean) -> Color = { baseColor, isHovered ->
        baseColor.copy(alpha = if (isHovered) 0.9f else 0.7f)
    }
    var taskBarProgress: (Color, Boolean) -> Color = { baseColor, isHovered ->
        baseColor.copy(alpha = if (isHovered) 0.9f else 0.8f)
    }

    // Dependency arrow
    var dependencyArrowColor: (Color) -> Color = { baseColor ->
        baseColor.copy(alpha = 0.7f)
    }

    // Row colors
    var rowBorderColor: (Color) -> Color = { outlineColor ->
        outlineColor.copy(alpha = 0.2f)
    }

    // Timeline header
    var timelineHeaderBorderColor: (Color) -> Color = { outlineColor ->
        outlineColor.copy(alpha = 0.3f)
    }

    // Define group colors with DSL
    fun groupColorScheme(init: GroupColorScheme.() -> Unit) {
        val scheme = GroupColorScheme().apply(init)
        groupColors = scheme.groups
    }

    /**
     * Get color for a specific group. This is the core method that implements the color
     * selection strategy: first check explicit mapping, then palette, then default.
     */
    fun getColorForGroup(groupName: String, existingGroups: List<String>): Color {
        // 1. Use explicit color mapping from the theme if available
        groupColors[groupName]?.let { return it }

        // 2. If group has no explicit color, assign from palette based on its position
        // in the list of all groups (for consistent coloring)
        val groupIndex = existingGroups.indexOf(groupName)
        if (groupIndex >= 0 && groupIndex < groupColorPalette.size) {
            return groupColorPalette[groupIndex]
        }

        // 3. If beyond palette size, use default color
        return defaultGroupColor
    }
}

/**
 * Helper class for defining group colors
 */
class GroupColorScheme {
    val groups = mutableMapOf<String, Color>()

    // This allows syntax like: "Development" to Color.Blue
    infix fun String.to(color: Color) {
        groups[this] = color
    }
}

/**
 * Styles configuration for Gantt chart
 */
class GanttStyles {
    // Task styling
    var taskBarHeight: Float = 0.7f
    var taskBarCornerRadius: Float = 0.2f
    var taskBarTextPadding: Dp = 4.dp

    // Row styling
    var rowBorderWidth: Dp = 0.5.dp

    // Dependency arrows
    var dependencyArrowWidth: Dp = 1.5.dp
    var dependencyArrowHeadSize: Dp = 7.dp
    var dependencyArrowCornerRadius: Dp = 4.dp

    // Timeline header
    var timelineHeaderBorderWidth: Dp = 0.5.dp

    // Group info
    var groupTagShape: Float = 4.0f
    var groupTagBorderWidth: Dp = 1.dp
    var groupTagPadding: Dp = 8.dp

    var showTaskProgress = false
}

/**
 * Naming/terminology customization
 */
class GanttNaming {
    var taskGroups: String = "Task Group"
    var noGroupsMessage: String = "No groups defined"
    var taskListHeader: String = "Tasks" // Add this if it doesn't exist

    var subtasks: String = "Subtasks"
}

/**
 * Creates a Gantt theme configuration using the DSL builder
 */
fun ganttTheme(init: GanttThemeConfig.() -> Unit): GanttThemeConfig {
    return GanttThemeConfig().apply(init)
}