# Ganttly UI Tests

This directory contains UI tests for the Ganttly chart components. These tests are written in the commonTest sourceset so they can be run on any platform that supports Compose UI testing.

## Test Structure

The tests are organized into two main files:

1. `GanttChartTest.kt` - Tests for the GanttChartState class

## Running the Tests

### Web (WasmJs)

To run the tests on the web platform:

```bash
./gradlew :ganttly:wasmJsBrowserTest --tests "io.github.kotlinlabs.ganttly.chart.GanttChartTest" --rerun-tasks --no-configuration-cache
```

## Writing UI Tests for Compose Multiplatform

When writing UI tests for Compose Multiplatform, keep in mind the following:

1. Use `kotlin.test` assertions instead of JUnit assertions
2. Avoid platform-specific APIs
3. Use the `@OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)` annotation when using Compose UI testing APIs

### Example of a UI Test

Here's an example of how to write a UI test for a Compose component:

```kotlin
@Test
fun testGanttChartRendering() {
    // Create a test state
    val state = GanttChartState(listOf(
        GanttTask(
            id = "1",
            name = "Task 1",
            startDate = Clock.System.now(),
            duration = 2.hours,
            progress = 0.5f
        )
    ))
    
    // In a real UI test with a compose test rule:
    // composeTestRule.setContent {
    //     GanttChartView(state = state)
    // }
    // 
    // composeTestRule.onNodeWithText("Task 1").assertIsDisplayed()
    
    // For now, we just verify the state
    assertEquals(1, state.tasks.size)
    assertEquals("Task 1", state.tasks[0].name)
}
```

## Notes on Platform-Specific Testing

Some testing features may only be available on specific platforms. For example, the full Compose UI testing framework with `createComposeRule()` is primarily designed for Android. When testing on other platforms, you may need to use platform-specific approaches.

For comprehensive UI testing across all platforms, consider using a combination of:

1. Common tests for shared logic
2. Platform-specific tests for UI interactions
3. Screenshot testing for visual verification