package io.github.kotlinlabs.ganttly

import androidx.compose.ui.graphics.Color
import io.github.kotlinlabs.ganttly.chart.GanttChartState
import io.github.kotlinlabs.ganttly.models.GanttTask
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Creates a demo GanttChartState with 100 tasks organized into a hierarchical structure
 * for demonstration and testing purposes.
 *
 * The resulting chart contains:
 * - 5 main project phases
 * - Each phase has 3-5 subphases
 * - Each subphase contains 4-8 tasks
 * - Dependencies between related tasks
 * - Various progress states
 * - Different task groups with color coordination
 */
fun createLargeGanttStateDemo(): GanttChartState {
    val now = Clock.System.now()
    val tasks = mutableListOf<GanttTask>()

    // Color palette for different groups
    val groupColors = mapOf(
        "Planning" to Color(0xFF4CAF50),  // Green
        "Design" to Color(0xFF2196F3),    // Blue
        "Development" to Color(0xFFF44336), // Red
        "Testing" to Color(0xFFFF9800),   // Orange
        "Deployment" to Color(0xFF9C27B0)  // Purple
    )

    // Create 5 main project phases
    val phaseNames = listOf("Project Initiation", "Requirements Analysis", "Design & Architecture", "Implementation", "Testing & Deployment")
    val phaseGroups = listOf("Planning", "Planning", "Design", "Development", "Testing")

    var currentTaskId = 1
    var currentTime = now

    // Track task IDs for dependencies
    val taskIds = mutableMapOf<String, String>()
    val topLevelPhaseIds = mutableListOf<String>() // To track phase IDs for level 0 dependencies

    // Create the 5 main phases
    phaseNames.forEachIndexed { phaseIndex, phaseName ->
        val phaseGroup = phaseGroups[phaseIndex]
        val phaseId = "phase_$phaseIndex"
        taskIds[phaseId] = phaseId
        topLevelPhaseIds.add(phaseId) // Add to top level IDs list

        // Number of subphases per phase (3-5)
        val subphaseCount = 3 + (phaseIndex % 3)
        val subphases = mutableListOf<GanttTask>()

        // Create subphases for each main phase
        for (subphaseIndex in 0 until subphaseCount) {
            // Clean subphase name without "Phase X.Y:" prefix
            val subphaseName = getSubphaseName(phaseIndex, subphaseIndex)
            val subphaseId = "${phaseId}_sub_$subphaseIndex"
            taskIds[subphaseId] = subphaseId

            // Number of tasks per subphase (4-8)
            val taskCount = 4 + ((phaseIndex + subphaseIndex) % 5)
            val subphaseTasks = mutableListOf<GanttTask>()

            // Create individual tasks for each subphase
            for (taskIndex in 0 until taskCount) {
                // Clean task name without "Task X:" prefix
                val taskName = getTaskName(phaseIndex, subphaseIndex, taskIndex)
                val taskId = "task_$currentTaskId"
                taskIds["t$currentTaskId"] = taskId

                // Determine task dependencies
                val dependencies = mutableListOf<String>()
                if (taskIndex > 0) {
                    dependencies.add("task_${currentTaskId - 1}")
                } else if (subphaseIndex > 0 && taskIndex == 0) {
                    dependencies.add("task_${currentTaskId - 1}")
                } else if (phaseIndex > 0 && subphaseIndex == 0 && taskIndex == 0) {
                    dependencies.add(taskIds["phase_${phaseIndex - 1}_sub_${subphaseCount - 1}"] ?: "")
                }

                // Randomize progress based on phase
                val progressBase = 1.0f - (phaseIndex * 0.2f)
                val progress = (progressBase - (subphaseIndex * 0.1f) - (taskIndex * 0.05f)).coerceIn(0f, 1f)

                // Create the task
                val task = GanttTask(
                    id = taskId,
                    name = taskName,
                    startDate = currentTime,
                    duration = (2 + (taskIndex % 3)).hours,
                    progress = progress,
                    dependencies = dependencies.filter { it.isNotEmpty() },
                    group = phaseGroup,
                    color = groupColors[phaseGroup] ?: Color.Gray
                )

                subphaseTasks.add(task)
                currentTaskId++
                currentTime += (1 + (taskIndex % 2)).hours
            }

            // Create subphase as parent task
            val subphase = GanttTask.createParentTask(
                id = subphaseId,
                name = subphaseName,
                startDate = subphaseTasks.first().startDate,
                children = subphaseTasks,
                progress = subphaseTasks.map { it.progress }.average().toFloat(),
                group = phaseGroup,
                color = groupColors[phaseGroup] ?: Color.Gray,
                isExpanded = true
            )

            subphases.add(subphase)
            currentTime += 2.hours
        }

        // Define dependencies for the top-level phases
        val dependencies = mutableListOf<String>()

        // Add dependency on previous phase (except for the first phase)
        if (phaseIndex > 0) {
            dependencies.add(topLevelPhaseIds[phaseIndex - 1])
        }

        // Create the main phase as parent task with dependencies
        val phase = GanttTask.createParentTask(
            id = phaseId,
            name = phaseName,
            startDate = subphases.first().startDate,
            children = subphases,
            progress = subphases.map { it.progress }.average().toFloat(),
            dependencies = dependencies, // Add dependencies to previous phase
            group = phaseGroup,
            color = groupColors[phaseGroup] ?: Color.Gray,
            isExpanded = phaseIndex <= 2 // Only expand first 3 phases by default
        )

        tasks.add(phase)
        currentTime += 4.hours
    }

    return GanttChartState(tasks)
}

// Helper functions to generate descriptive names
private fun getSubphaseName(phaseIndex: Int, subphaseIndex: Int): String {
    val subphaseNames = listOf(
        // Project Initiation subphases
        listOf("Project Kickoff", "Stakeholder Analysis", "Initial Planning", "Resource Allocation", "Feasibility Study"),
        // Requirements Analysis subphases
        listOf("Business Analysis", "User Research", "Requirements Documentation", "Requirement Validation", "Use Case Development"),
        // Design & Architecture subphases
        listOf("UI/UX Design", "System Architecture", "Database Design", "API Design", "Security Architecture"),
        // Implementation subphases
        listOf("Frontend Development", "Backend Development", "Database Implementation", "API Development", "Integration"),
        // Testing & Deployment subphases
        listOf("Unit Testing", "Integration Testing", "Performance Testing", "User Acceptance", "Production Deployment")
    )

    return if (phaseIndex < subphaseNames.size && subphaseIndex < subphaseNames[phaseIndex].size) {
        subphaseNames[phaseIndex][subphaseIndex]
    } else {
        "Additional Subphase"
    }
}

private fun getTaskName(phaseIndex: Int, subphaseIndex: Int, taskIndex: Int): String {
    // Task names organized by phase and subphase
    val taskNamesByPhaseAndSubphase = mapOf(
        // Project Initiation phase
        0 to mapOf(
            0 to listOf("Define scope", "Create charter", "Identify stakeholders", "Establish timeline"),
            1 to listOf("Assemble team", "Allocate resources", "Setup communication", "Initial meeting"),
            2 to listOf("Initial risk assessment", "Feasibility study", "Budget approval", "Kickoff meeting"),
            3 to listOf("Create roadmap", "Set milestones", "Define success metrics", "Stakeholder approval")
        ),
        // Requirements Analysis phase
        1 to mapOf(
            0 to listOf("User interviews", "Market research", "Competitor analysis", "Focus groups"),
            1 to listOf("Functional requirements", "Non-functional requirements", "Constraints", "Assumptions"),
            2 to listOf("Create user stories", "Prioritize features", "Create use cases", "Requirements mapping"),
            3 to listOf("Requirements review", "Requirements approval", "Scope finalization", "Documentation")
        ),
        // Design & Architecture phase
        2 to mapOf(
            0 to listOf("Wireframes", "Mockups", "Prototypes", "Design system"),
            1 to listOf("System architecture", "Component design", "Module specification", "Data flow"),
            2 to listOf("Database schema", "API specification", "Security model", "Integration points"),
            3 to listOf("Design review", "Architecture validation", "Design approval", "Technical documentation")
        ),
        // Implementation phase
        3 to mapOf(
            0 to listOf("Setup environment", "Code scaffolding", "Foundation components", "Build system"),
            1 to listOf("Core functionality", "User interface", "Business logic", "Data access"),
            2 to listOf("API development", "Integration", "Error handling", "Performance optimization"),
            3 to listOf("Code review", "Unit tests", "Documentation", "Version control")
        ),
        // Testing & Deployment phase
        4 to mapOf(
            0 to listOf("Test planning", "Test cases", "Test data", "Test environment"),
            1 to listOf("Unit testing", "Integration testing", "System testing", "Regression testing"),
            2 to listOf("User acceptance", "Bug fixes", "Final review", "Approval for release"),
            3 to listOf("Deployment preparation", "Production release", "Monitoring setup", "Knowledge transfer")
        )
    )

    // Try to get a task name from our structured map
    val subphaseMap = taskNamesByPhaseAndSubphase[phaseIndex] ?: mapOf()
    val taskNames = subphaseMap[subphaseIndex] ?: listOf()

    return if (taskIndex < taskNames.size) {
        taskNames[taskIndex]
    } else {
        "Additional task"
    }
}
