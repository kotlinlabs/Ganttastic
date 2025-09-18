package io.github.kotlinlabs.ganttly.chart

import io.github.kotlinlabs.ganttly.models.GanttTask
import kotlin.time.Clock.System
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SimpleOrderingTest {
    
    private val baseTime = System.now()
    
    @Test
    fun testDirectOptimizeFunction() {
        // Create simple chain: A -> B -> C
        val taskA = GanttTask(
            id = "A", 
            name = "Task A", 
            startDate = baseTime, 
            duration = 1.hours
        )
        val taskB = GanttTask(
            id = "B", 
            name = "Task B", 
            startDate = baseTime + 1.hours, 
            duration = 1.hours, 
            dependencies = listOf("A")
        )
        val taskC = GanttTask(
            id = "C", 
            name = "Task C", 
            startDate = baseTime + 2.hours, 
            duration = 1.hours, 
            dependencies = listOf("B")
        )
        
        val inputTasks = listOf(taskC, taskA, taskB) // Wrong order
        println("=== Simple Ordering Test ===")
        println("Input order: ${inputTasks.map { it.id }}")
        
        // Test the optimizeTaskOrdering function directly
        val result = optimizeTaskOrdering(inputTasks, true)
        println("Result order: ${result.map { it.id }}")
        println("Expected: [A, B, C]")
        println("Success: ${result.map { it.id } == listOf("A", "B", "C")}")
        
        // Verify the result
        assertEquals(3, result.size, "Should have 3 tasks")
        assertEquals("A", result[0].id, "First task should be A")
        assertEquals("B", result[1].id, "Second task should be B") 
        assertEquals("C", result[2].id, "Third task should be C")
    }
    
    @Test
    fun testWithoutOptimization() {
        val taskA = GanttTask(
            id = "A", 
            name = "Task A", 
            startDate = baseTime, 
            duration = 1.hours
        )
        val taskB = GanttTask(
            id = "B", 
            name = "Task B", 
            startDate = baseTime + 1.hours, 
            duration = 1.hours, 
            dependencies = listOf("A")
        )
        val taskC = GanttTask(
            id = "C", 
            name = "Task C", 
            startDate = baseTime + 2.hours, 
            duration = 1.hours, 
            dependencies = listOf("B")
        )
        
        val inputTasks = listOf(taskC, taskA, taskB) // Wrong order
        println("=== Without Optimization Test ===")
        println("Input order: ${inputTasks.map { it.id }}")
        
        // Test with optimization disabled
        val result = optimizeTaskOrdering(inputTasks, false)
        println("Result order: ${result.map { it.id }}")
        
        // Should maintain original order
        assertEquals(inputTasks.map { it.id }, result.map { it.id }, 
            "Should maintain original order when optimization disabled")
    }
    
    @Test
    fun testGanttStateIntegration() {
        val taskA = GanttTask(
            id = "A", 
            name = "Task A", 
            startDate = baseTime, 
            duration = 1.hours
        )
        val taskB = GanttTask(
            id = "B", 
            name = "Task B", 
            startDate = baseTime + 1.hours, 
            duration = 1.hours, 
            dependencies = listOf("A")
        )
        val taskC = GanttTask(
            id = "C", 
            name = "Task C", 
            startDate = baseTime + 2.hours, 
            duration = 1.hours, 
            dependencies = listOf("B")
        )
        
        val inputTasks = listOf(taskC, taskA, taskB) // Wrong order
        println("=== GanttState Integration Test ===")
        println("Input order: ${inputTasks.map { it.id }}")
        
        // Create GanttState - should automatically apply smart ordering
        val state = GanttChartState(inputTasks)
        println("State result: ${state.tasks.map { it.id }}")
        println("Expected: [A, B, C]")
        println("Success: ${state.tasks.map { it.id } == listOf("A", "B", "C")}")
        
        // Verify the state automatically applied smart ordering
        assertEquals("A", state.tasks[0].id, "First task should be A")
        assertEquals("B", state.tasks[1].id, "Second task should be B") 
        assertEquals("C", state.tasks[2].id, "Third task should be C")
    }
}
