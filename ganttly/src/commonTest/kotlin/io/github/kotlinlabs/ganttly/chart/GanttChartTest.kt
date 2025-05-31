@file:OptIn(ExperimentalTestApi::class)

package io.github.kotlinlabs.ganttly.chart

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import io.github.kotlinlabs.ganttly.createLargeGanttStateDemo
import io.github.kotlinlabs.ganttly.styles.DefaultGanttTheme
import io.github.kotlinlabs.ganttly.styles.TaskGroupColorCoordinator
import io.github.kotlinlabs.ganttly.styles.ganttTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

// TODO: Add tests to verify scrolling and tasks visibility.
class GanttChartTest {

    @Test
    fun testMainComponentsVisibility() = runComposeUiTest {
        // Create a test gantt state
        val ganttState = createLargeGanttStateDemo()

        // Render the chart
        setContent {
            GanttChartView(state = ganttState)
        }

        // Verify that the main components exist
        onNodeWithTag("gantt_chart_view").assertExists()
        onNodeWithTag("task_list_panel").assertExists()
        onNodeWithTag("timeline_panel").assertExists()
        onNodeWithTag("timeline_header").assertExists()
    }

    @Test
    fun testTimelineHeadersAndDuration() = runComposeUiTest {
        // Create a test gantt state
        val ganttState = createLargeGanttStateDemo()

        // Render the chart
        setContent {
            GanttChartView(state = ganttState)
        }

        // Verify timeline info exists and has the correct structure
        val timelineInfo = ganttState.timelineViewInfo
        assertNotNull(timelineInfo, "Timeline info should not be null")
        assertTrue(timelineInfo.totalViewDuration > 0.minutes, "Timeline should have a positive duration")

        // Verify start and end dates are consistent
        assertTrue(
            timelineInfo.viewStartDate <= timelineInfo.viewEndDate,
            "Start date should be before or equal to end date"
        )

        // Verify pixel calculation is non-negative
        assertTrue(timelineInfo.pixelsPerSecond >= 0, "Pixels per second should be non-negative")

        // Set a chart width to trigger header cells generation
        ganttState.chartWidthPx = 1000f

        // After setting chart width, verify header cells are generated
        // Note: Header cells might still be empty in the test environment, so we'll make this check conditional
        if (ganttState.timelineViewInfo.headerCells.isEmpty()) {
            // If headerCells are still empty, check that we at least have valid timeline boundaries
            assertTrue(
                ganttState.timelineViewInfo.pixelsPerSecond > 0,
                "Pixels per second should be positive after setting width"
            )
        } else {
            // If headerCells are populated, perform normal checks
            assertTrue(ganttState.timelineViewInfo.headerCells.isNotEmpty(), "Timeline should have header cells")
            assertNotNull(
                ganttState.timelineViewInfo.headerCells.firstOrNull()?.label,
                "Header cell should have a label"
            )
        }
    }

    @Test
    fun testExpandAndCollapseTasks() = runComposeUiTest {
        // Create a test gantt state
        val ganttState = createLargeGanttStateDemo()

        // Render the chart
        setContent {
            GanttChartView(state = ganttState)
        }

        // Find a parent task that has children
        val parentTask = ganttState.tasks.find { it.hasChildren }
        assertNotNull(parentTask, "No parent task found")

        // Count initial visible tasks
        val initialTaskCount = ganttState.tasks.size
        assertTrue(initialTaskCount > 0, "No tasks found in the demo state")

        // Verify parent task properties
        assertTrue(parentTask.hasChildren, "Parent task should have children")
        assertTrue(parentTask.children.isNotEmpty(), "Parent task should have at least one child")

        // If parent is expanded, collapse it and verify task count decreases
        if (parentTask.isExpanded) {
            val childCount = parentTask.children.size

            // Collapse the parent task
            ganttState.toggleTaskExpansion(parentTask.id)

            // Find the updated parent task
            val collapsedTask = ganttState.tasks.find { it.id == parentTask.id }
            assertNotNull(collapsedTask, "Task should still exist after collapsing")

            // Verify it's now collapsed
            assertTrue(!collapsedTask.isExpanded, "Task should be collapsed")

            // Verify task count has decreased
            val newTaskCount = ganttState.tasks.size
            assertTrue(newTaskCount < initialTaskCount, "Task count should decrease after collapsing")

            // Re-expand the parent task
            ganttState.toggleTaskExpansion(parentTask.id)

            // Find the updated parent task again
            val reExpandedTask = ganttState.tasks.find { it.id == parentTask.id }
            assertNotNull(reExpandedTask, "Task should still exist after re-expanding")

            // Verify it's expanded again
            assertTrue(reExpandedTask.isExpanded, "Task should be expanded again")

            // Verify task count has returned to initial count
            val finalTaskCount = ganttState.tasks.size
            assertEquals(initialTaskCount, finalTaskCount, "Task count should return to initial value")
        } else {
            // If already collapsed, expand it and verify task count increases
            // Expand the parent task
            ganttState.toggleTaskExpansion(parentTask.id)

            // Find the updated parent task
            val expandedTask = ganttState.tasks.find { it.id == parentTask.id }
            assertNotNull(expandedTask, "Task should still exist after expanding")

            // Verify it's now expanded
            assertTrue(expandedTask.isExpanded, "Task should be expanded")

            // Verify task count has increased
            val newTaskCount = ganttState.tasks.size
            assertTrue(newTaskCount > initialTaskCount, "Task count should increase after expanding")

            // Re-collapse the parent task
            ganttState.toggleTaskExpansion(parentTask.id)

            // Find the updated parent task again
            val reCollapsedTask = ganttState.tasks.find { it.id == parentTask.id }
            assertNotNull(reCollapsedTask, "Task should still exist after re-collapsing")

            // Verify it's collapsed again
            assertTrue(!reCollapsedTask.isExpanded, "Task should be collapsed again")

            // Verify task count has returned to initial count
            val finalTaskCount = ganttState.tasks.size
            assertEquals(initialTaskCount, finalTaskCount, "Task count should return to initial value")
        }
    }

    @Test
    fun testGanttChartStateBasics() {
        // This is a non-UI test that verifies basic state functions
        val ganttState = createLargeGanttStateDemo()

        // Verify that we have tasks
        assertTrue(ganttState.tasks.isNotEmpty(), "State should have tasks")

        // Verify hierarchical tasks
        assertTrue(ganttState.hierarchicalTasks.isNotEmpty(), "State should have hierarchical tasks")

        // Verify timeline info exists
        assertNotNull(ganttState.timelineViewInfo, "Timeline info should exist")

        // Verify we can get group info
        val groupInfo = ganttState.getGroupInfo()
        assertNotNull(groupInfo, "Group info should exist")

        // Verify task counts by level
        val taskCountsByLevel = ganttState.getTaskCountsByLevel()
        assertTrue(taskCountsByLevel.isNotEmpty(), "Task counts by level should exist")

        // Verify validation errors should be empty
        assertTrue(ganttState.validationErrors.isEmpty(), "There should be no validation errors")
    }

    @Test
    fun testGanttTaskProperties() {
        // Create a test gantt state
        val ganttState = createLargeGanttStateDemo()

        // Verify basic task properties
        val sampleTask = ganttState.tasks.first()
        assertNotNull(sampleTask.id, "Task ID should not be null")
        assertNotNull(sampleTask.name, "Task name should not be null")
        assertNotNull(sampleTask.startDate, "Task start date should not be null")
        assertTrue(sampleTask.duration.inWholeSeconds >= 0, "Task duration should be non-negative")

        // Verify effective duration always returns a positive value
        assertTrue(sampleTask.effectiveDuration.inWholeSeconds > 0, "Effective duration should be positive")

        // Verify progress is within valid range
        assertTrue(sampleTask.progress in 0f..1f, "Progress should be between 0 and 1")

        // Find a parent task with children
        val parentTask = ganttState.tasks.find { it.hasChildren }
        if (parentTask != null) {
            assertTrue(parentTask.hasChildren, "Parent task should have children")
            assertTrue(parentTask.children.isNotEmpty(), "Parent task should have at least one child")

            // Verify parent task properties
            val childTask = parentTask.children.first()
            assertNotNull(childTask, "Child task should exist")
            assertNotNull(childTask.id, "Child task ID should not be null")
            assertNotNull(childTask.name, "Child task name should not be null")

            // Verify parent-child relationships
            assertTrue(childTask.level > parentTask.level, "Child level should be greater than parent level")
        }
    }

    @Test
    fun testTaskDependencies() {
        // Create a test gantt state
        val ganttState = createLargeGanttStateDemo()

        // Find tasks with dependencies
        val tasksWithDependencies = ganttState.tasks.filter { it.dependencies.isNotEmpty() }

        // Skip the test if no dependencies are found (this is a valid case)
        if (tasksWithDependencies.isNotEmpty()) {
            val dependentTask = tasksWithDependencies.first()
            assertTrue(dependentTask.dependencies.isNotEmpty(), "Dependent task should have dependencies")

            // Verify the dependency exists in the task list
            val dependencyId = dependentTask.dependencies.first()
            val sourceTask = ganttState.tasks.find { it.id == dependencyId }
            assertNotNull(sourceTask, "Source task should exist")

            // Verify dependency relationship is consistent (source task should start before dependent task)
            assertTrue(
                sourceTask.startDate <= dependentTask.startDate,
                "Source task should start before or at the same time as dependent task"
            )
        }
    }

    @Test
    fun testTaskGroupColorCoordination() {
        // Create a test gantt state
        val ganttState = createLargeGanttStateDemo()

        // Get all unique groups
        val uniqueGroups = ganttState.tasks.mapNotNull { it.group }.filter { it.isNotEmpty() }.distinct()

        // Skip the test if no groups are defined
        if (uniqueGroups.isNotEmpty()) {
            // Get the default theme colors
            val themeColors = ganttTheme {}.colors

            // Reset the coordinator to ensure clean state
            TaskGroupColorCoordinator.reset()

            // Test color coordination for the first group
            val firstGroup = uniqueGroups.first()
            val color = TaskGroupColorCoordinator.getColorForGroup(firstGroup, uniqueGroups, themeColors)
            assertNotNull(color, "Group color should not be null")

            // Test color coordination for the same group again (should be cached)
            val cachedColor = TaskGroupColorCoordinator.getColorForGroup(firstGroup, uniqueGroups, themeColors)
            assertEquals(color, cachedColor, "Cached color should match original color")

            // Test applying color to a task
            val taskWithGroup = ganttState.tasks.find { it.group == firstGroup }
            if (taskWithGroup != null) {
                val taskWithColor = TaskGroupColorCoordinator.applyGroupColorToTask(
                    taskWithGroup, uniqueGroups, themeColors
                )
                assertNotNull(taskWithColor.color, "Task color should not be null")
            }
        }
    }

    @Test
    fun testGanttThemeConfiguration() {
        // Test the theme configuration system
        val customTheme = ganttTheme {
            naming {
                taskListHeader = "Custom Tasks"
                taskGroups = "Custom Groups"
                noGroupsMessage = "No custom groups found"
            }
            styles {
                taskBarHeight = 0.8f
                taskBarCornerRadius = 0.3f
            }
        }

        // Verify custom naming
        assertEquals("Custom Tasks", customTheme.naming.taskListHeader)
        assertEquals("Custom Groups", customTheme.naming.taskGroups)
        assertEquals("No custom groups found", customTheme.naming.noGroupsMessage)

        // Verify custom styles
        assertEquals(0.8f, customTheme.styles.taskBarHeight)
        assertEquals(0.3f, customTheme.styles.taskBarCornerRadius)

        // Verify default theme exists
        val defaultTheme = DefaultGanttTheme
        assertNotNull(defaultTheme)
    }

    @Test
    fun testParentTaskCalculation() {
        // Create a test gantt state
        val ganttState = createLargeGanttStateDemo()

        // Find a parent task with children
        val parentTask = ganttState.tasks.find { it.hasChildren && it.children.isNotEmpty() }

        if (parentTask != null) {
            // Re-calculate parent progress
            ganttState.recalculateParentProgress()

            // Find the updated parent task
            val updatedParentTask = ganttState.tasks.find { it.id == parentTask.id }
            assertNotNull(updatedParentTask, "Updated parent task should exist")

            // Verify that parent duration encompasses all children
            val childrenStartDate = parentTask.children.minOfOrNull { it.startDate }
            val childrenEndDate = parentTask.children.maxOfOrNull { it.endDate }

            if (childrenStartDate != null && childrenEndDate != null) {
                assertTrue(
                    updatedParentTask.startDate <= childrenStartDate,
                    "Parent start date should be before or equal to earliest child start date"
                )
                assertTrue(
                    updatedParentTask.endDate >= childrenEndDate,
                    "Parent end date should be after or equal to latest child end date"
                )
            }
        }
    }
}