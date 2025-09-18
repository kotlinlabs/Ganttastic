package io.github.kotlinlabs.ganttly.chart

import io.github.kotlinlabs.ganttly.models.GanttTask
import kotlin.time.Clock.System
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SmartOrderingTest {
    
    private val baseTime = System.now()
    
    @Test
    fun testSimpleDependencyChain() {
        // Create tasks with dependencies: A -> B -> C
        val taskA = GanttTask(
            id = "A",
            name = "Task A",
            startDate = baseTime,
            duration = 2.hours
        )
        
        val taskB = GanttTask(
            id = "B", 
            name = "Task B",
            startDate = baseTime + 2.hours,
            duration = 2.hours,
            dependencies = listOf("A")
        )
        
        val taskC = GanttTask(
            id = "C",
            name = "Task C", 
            startDate = baseTime + 4.hours,
            duration = 2.hours,
            dependencies = listOf("B")
        )
        
        // Test with wrong order: C, A, B
        val wrongOrderTasks = listOf(taskC, taskA, taskB)
        
        // Apply smart ordering
        val optimizedTasks = optimizeTaskOrdering(wrongOrderTasks, true)
        
        // Print results for debugging
        println("Original order: ${wrongOrderTasks.map { it.id }}")
        println("Optimized order: ${optimizedTasks.map { it.id }}")
        
        // Verify correct order: A -> B -> C
        assertEquals("A", optimizedTasks[0].id, "Task A should be first")
        assertEquals("B", optimizedTasks[1].id, "Task B should be second") 
        assertEquals("C", optimizedTasks[2].id, "Task C should be third")
    }
    
    @Test
    fun testMultipleDependencies() {
        // Create tasks: A, B -> C (C depends on both A and B)
        val taskA = GanttTask(
            id = "A",
            name = "Task A",
            startDate = baseTime,
            duration = 2.hours
        )
        
        val taskB = GanttTask(
            id = "B",
            name = "Task B", 
            startDate = baseTime,
            duration = 2.hours
        )
        
        val taskC = GanttTask(
            id = "C",
            name = "Task C",
            startDate = baseTime + 2.hours,
            duration = 2.hours,
            dependencies = listOf("A", "B")
        )
        
        // Test with wrong order: C, A, B
        val wrongOrderTasks = listOf(taskC, taskA, taskB)
        
        val optimizedTasks = optimizeTaskOrdering(wrongOrderTasks, true)
        
        println("Multiple deps - Original: ${wrongOrderTasks.map { it.id }}")
        println("Multiple deps - Optimized: ${optimizedTasks.map { it.id }}")
        
        // A and B should come before C
        val indexA = optimizedTasks.indexOfFirst { it.id == "A" }
        val indexB = optimizedTasks.indexOfFirst { it.id == "B" }
        val indexC = optimizedTasks.indexOfFirst { it.id == "C" }
        
        assertTrue(indexA < indexC, "Task A should come before C")
        assertTrue(indexB < indexC, "Task B should come before C")
    }
    
    @Test
    fun testComplexDependencyChain() {
        // Create a more complex scenario:
        // A -> B -> D
        // A -> C -> D  
        // E (independent)
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
            startDate = baseTime + 1.hours,
            duration = 1.hours,
            dependencies = listOf("A")
        )
        val taskD = GanttTask(
            id = "D",
            name = "Task D",
            startDate = baseTime + 2.hours,
            duration = 1.hours,
            dependencies = listOf("B", "C")
        )
        val taskE = GanttTask(
            id = "E",
            name = "Task E",
            startDate = baseTime,
            duration = 1.hours
        ) // Independent
        
        // Test with scrambled order
        val scrambledTasks = listOf(taskE, taskD, taskB, taskA, taskC)
        
        val optimizedTasks = optimizeTaskOrdering(scrambledTasks, true)
        
        println("Complex - Original: ${scrambledTasks.map { it.id }}")
        println("Complex - Optimized: ${optimizedTasks.map { it.id }}")
        
        // Verify dependency order constraints
        val positions = optimizedTasks.mapIndexed { index, task -> task.id to index }.toMap()
        
        // A should come before B and C
        assertTrue(positions["A"]!! < positions["B"]!!, "A should come before B")
        assertTrue(positions["A"]!! < positions["C"]!!, "A should come before C")
        
        // B and C should come before D
        assertTrue(positions["B"]!! < positions["D"]!!, "B should come before D")
        assertTrue(positions["C"]!! < positions["D"]!!, "C should come before D")
    }
    
    @Test
    fun testHierarchicalTasks() {
        // Test with parent-child relationships
        val childTask1 = GanttTask(
            id = "child1",
            name = "Child Task 1",
            startDate = baseTime,
            duration = 1.hours
        )
        val childTask2 = GanttTask(
            id = "child2",
            name = "Child Task 2",
            startDate = baseTime + 1.hours,
            duration = 1.hours,
            dependencies = listOf("child1")
        )
        
        val parentTask = GanttTask(
            id = "parent",
            name = "Parent Task",
            startDate = baseTime,
            duration = 2.hours,
            children = listOf(childTask1, childTask2)
        )
        
        val independentTask = GanttTask(
            id = "independent",
            name = "Independent Task",
            startDate = baseTime + 2.hours,
            duration = 1.hours,
            dependencies = listOf("child2")
        )
        
        val tasks = listOf(independentTask, parentTask)
        val optimizedTasks = optimizeTaskOrdering(tasks, true)
        
        println("Hierarchical - Original: ${tasks.map { it.id }}")
        println("Hierarchical - Optimized: ${optimizedTasks.map { it.id }}")
        
        // Parent should come before independent (because independent depends on child2)
        val parentIndex = optimizedTasks.indexOfFirst { it.id == "parent" }
        val independentIndex = optimizedTasks.indexOfFirst { it.id == "independent" }
        assertTrue(parentIndex < independentIndex, "Parent should come before independent task")
    }
    
    @Test
    fun testNoOptimizationWhenDisabled() {
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
        
        val originalOrder = listOf(taskC, taskA, taskB)
        
        // Test with optimization disabled
        val unoptimized = optimizeTaskOrdering(originalOrder, false)
        
        println("Disabled optimization - Original: ${originalOrder.map { it.id }}")
        println("Disabled optimization - Result: ${unoptimized.map { it.id }}")
        
        // Should maintain original order when disabled
        assertEquals(originalOrder.map { it.id }, unoptimized.map { it.id }, 
            "Should maintain original order when optimization is disabled")
    }
    
    @Test
    fun testGanttStateIntegration() {
        // Test that GanttState applies smart ordering
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
        
        val wrongOrderTasks = listOf(taskC, taskA, taskB)
        
        // Create GanttState with smart ordering enabled
        val state = GanttChartState(wrongOrderTasks)
        
        println("GanttState integration - Input: ${wrongOrderTasks.map { it.id }}")
        println("GanttState integration - Result: ${state.tasks.map { it.id }}")
        
        // The state should have applied smart ordering automatically
        val tasks = state.tasks
        assertEquals("A", tasks[0].id, "Task A should be first in state")
        assertEquals("B", tasks[1].id, "Task B should be second in state")
        assertEquals("C", tasks[2].id, "Task C should be third in state")
        
        // Test toggling smart ordering off
        state.enableSmartOrdering = false
        state.tasks = wrongOrderTasks // Reset to wrong order
        
        println("GanttState disabled - Result: ${state.tasks.map { it.id }}")
        
        // Should maintain wrong order when disabled
        assertEquals(wrongOrderTasks.map { it.id }, state.tasks.map { it.id },
            "Should maintain original order when smart ordering is disabled")
    }
}