package io.github.kotlinlabs.ganttly.chart

import io.github.kotlinlabs.ganttly.createLargeGanttStateDemo
import kotlin.test.Test
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class DemoSmartOrderingTest {
    
    @Test
    fun analyzeProjectInitiationAndRequirementsAnalysis() {
        println("=== Analyzing Demo Smart Ordering ===")
        
        // Create the demo state
        val demoState = createLargeGanttStateDemo()
        val allTasks = demoState.tasks
        
        println("Total top-level tasks: ${allTasks.size}")
        println("Top-level task order: ${allTasks.map { it.id }}")
        println("Top-level task names: ${allTasks.map { it.name }}")
        println()
        
        // Analyze the dependency structure
        println("=== DEPENDENCY STRUCTURE ===")
        allTasks.forEach { task ->
            println("${task.id} (${task.name}) -> Dependencies: ${task.dependencies}")
        }
        println()
        
        // Find Project Initiation and Requirements Analysis phases
        val projectInitiation = allTasks.find { it.name == "Project Initiation" }
        val requirementsAnalysis = allTasks.find { it.name == "Requirements Analysis" }
        
        if (projectInitiation != null) {
            println("=== PROJECT INITIATION PHASE ===")
            println("Phase ID: ${projectInitiation.id}")
            println("Dependencies: ${projectInitiation.dependencies}")
            println("Children count: ${projectInitiation.children.size}")
            println()
            
            // Analyze subphases and their tasks
            projectInitiation.children.forEachIndexed { subIndex, subphase ->
                println("  Subphase $subIndex: ${subphase.name} (ID: ${subphase.id})")
                println("    Dependencies: ${subphase.dependencies}")
                println("    Tasks:")
                subphase.children.forEachIndexed { taskIndex, task ->
                    println("      Task $taskIndex: ${task.name} (ID: ${task.id})")
                    println("        Dependencies: ${task.dependencies}")
                }
                println()
            }
        }
        
        if (requirementsAnalysis != null) {
            println("=== REQUIREMENTS ANALYSIS PHASE ===")
            println("Phase ID: ${requirementsAnalysis.id}")
            println("Dependencies: ${requirementsAnalysis.dependencies}")
            println("Children count: ${requirementsAnalysis.children.size}")
            println()
            
            // Analyze subphases and their tasks
            requirementsAnalysis.children.forEachIndexed { subIndex, subphase ->
                println("  Subphase $subIndex: ${subphase.name} (ID: ${subphase.id})")
                println("    Dependencies: ${subphase.dependencies}")
                println("    Tasks:")
                subphase.children.forEachIndexed { taskIndex, task ->
                    println("      Task $taskIndex: ${task.name} (ID: ${task.id})")
                    println("        Dependencies: ${task.dependencies}")
                }
                println()
            }
        }
        
        // Analyze the dependency chain for the first few tasks
        println("=== DEPENDENCY CHAIN ANALYSIS ===")
        val flatTasks = mutableListOf<Pair<String, List<String>>>()
        
        fun collectTasks(tasks: List<io.github.kotlinlabs.ganttly.models.GanttTask>) {
            tasks.forEach { task ->
                flatTasks.add(task.id to task.dependencies)
                if (task.hasChildren) {
                    collectTasks(task.children)
                }
            }
        }
        
        collectTasks(allTasks)
        
        // Show first 20 tasks and their dependencies
        flatTasks.take(20).forEach { (id, deps) ->
            println("$id -> ${deps.joinToString(", ")}")
        }
        
        println()
        println("=== SMART ORDERING VERIFICATION ===")
        
        // The key insight: Requirements Analysis directly depends on Project Initiation
        // Since they're at the same hierarchical level with no dependencies between them,
        // smart ordering should place them adjacent to each other
        
        val actualOrder = allTasks.map { it.id }
        val expectedOrder = listOf("phase_0", "phase_1", "phase_2", "phase_3", "phase_4")
        
        println("Expected optimal order: $expectedOrder")
        println("Actual order: $actualOrder")
        println()
        
        // Verify the critical adjacency: Project Initiation → Requirements Analysis
        val projectInitiationIndex = actualOrder.indexOf("phase_0")
        val requirementsAnalysisIndex = actualOrder.indexOf("phase_1")
        
        println("Project Initiation index: $projectInitiationIndex")
        println("Requirements Analysis index: $requirementsAnalysisIndex")
        
        if (projectInitiationIndex == 0 && requirementsAnalysisIndex == 1) {
            println("✅ PERFECT! Project Initiation and Requirements Analysis are adjacent!")
            println("✅ Requirements Analysis comes immediately after Project Initiation!")
        } else if (projectInitiationIndex >= 0 && requirementsAnalysisIndex >= 0 && 
                   projectInitiationIndex < requirementsAnalysisIndex) {
            println("⚠️  PARTIAL: Project Initiation comes before Requirements Analysis, but they're not adjacent")
            println("   This means there are other tasks between them, increasing dependency arrow length")
        } else {
            println("❌ INCORRECT: The dependency order is violated!")
        }
        
        // Check overall order
        if (actualOrder == expectedOrder) {
            println("✅ PERFECT: All phases are in optimal dependency order!")
        } else {
            println("❌ SUBOPTIMAL: Phase ordering could be improved")
        }
    }
}
