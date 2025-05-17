package io.github.kotlinlabs.ganttly.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Access the current GanttChartTheme at the call site.
 */
object GanttTheme {
    val current: GanttThemeConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalGanttThemeConfig.current
}


/**
 * Provider for Gantt theme throughout the composition tree
 */
@Composable
fun ProvideGanttTheme(
    theme: GanttThemeConfig = DefaultGanttTheme,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalGanttThemeConfig provides theme,
        content = content
    )
}

val DefaultGanttTheme = ganttTheme {
    // Default values are already set in the classes
}

// Composition Local for the theme
private val LocalGanttThemeConfig = staticCompositionLocalOf {
    DefaultGanttTheme
}
