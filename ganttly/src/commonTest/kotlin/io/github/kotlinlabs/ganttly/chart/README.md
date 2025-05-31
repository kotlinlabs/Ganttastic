# Ganttly UI Tests

This directory contains UI tests for the Ganttly chart components. These tests are written in the commonTest sourceset but are primarily tested and verified to work in the wasmJS environment. While they are designed to run on any platform that supports Compose UI testing, platform-specific issues prevent UI-dependent tests from running successfully on Android and iOS without additional configuration. Some non-UI tests (like those testing state logic) may work across platforms, but UI tests using `runComposeUiTest` are only verified in wasmJS.

## Test Structure

The tests are organized in the following file:

1. `GanttChartTest.kt` - Tests for the Gantt chart components and state

The test file contains the following test cases:

- `testMainComponentsVisibility()` - Tests that the main UI components are visible
- `testTimelineHeadersAndDuration()` - Tests timeline headers and duration calculations
- `testExpandAndCollapseTasks()` - Tests expanding and collapsing parent tasks
- `testGanttChartStateBasics()` - Tests basic state functions
- `testGanttTaskProperties()` - Tests task properties
- `testTaskDependencies()` - Tests task dependency relationships
- `testTaskGroupColorCoordination()` - Tests color coordination for task groups
- `testGanttThemeConfiguration()` - Tests theme configuration
- `testParentTaskCalculation()` - Tests parent task calculations

## Running the Tests

**Note: UI tests using `runComposeUiTest` are only verified to work in the wasmJS environment, while some non-UI tests may work on other platforms.**

### Web (WasmJs)

To run all tests on the web platform (recommended for both UI and non-UI tests):

```bash
./gradlew :ganttly:wasmJsBrowserTest --tests "io.github.kotlinlabs.ganttly.chart.GanttChartTest" --rerun-tasks --no-configuration-cache
```

### Running Specific Tests

To run a specific test on the web platform:

```bash
./gradlew :ganttly:wasmJsBrowserTest --tests "io.github.kotlinlabs.ganttly.chart.GanttChartTest.testGanttChartStateBasics" --rerun-tasks --no-configuration-cache
```

### Running Non-UI Tests on Other Platforms

Non-UI tests (those not using `runComposeUiTest`) may work on other platforms. For example, to run a non-UI test on Android:

```bash
./gradlew :ganttly:testDebugUnitTest --tests "io.github.kotlinlabs.ganttly.chart.GanttChartTest.testGanttChartStateBasics"
```

Note that UI tests will likely fail on platforms other than wasmJS due to the issues described in the "Known Platform-Specific Issues" section.

## Writing UI Tests for Compose Multiplatform

When writing UI tests for Compose Multiplatform, keep in mind the following:

1. Use `kotlin.test` assertions instead of JUnit assertions
2. Avoid platform-specific APIs
3. Use the `@OptIn(ExperimentalTestApi::class)` annotation when using Compose UI testing APIs

### Example of a UI Test

Here's an example of how to write a UI test for a Compose component:

```kotlin
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
```

## Notes on Platform-Specific Testing

Some testing features may only be available on specific platforms. For example, the full Compose UI testing framework with `createComposeRule()` is primarily designed for Android. When testing on other platforms, you may need to use platform-specific approaches.

### Known Platform-Specific Issues

**Important: UI tests using `runComposeUiTest` are currently only verified to work in the wasmJS environment.**

1. **Android Tests**: 
   - Tests using `runComposeUiTest` will fail with `NullPointerException: Cannot invoke "String.toLowerCase(java.util.Locale)" because "android.os.Build.FINGERPRINT" is null` when run outside of a properly configured Android environment.
   - Non-UI tests (those not using `runComposeUiTest`, like `testGanttChartStateBasics`) may work on Android without additional configuration.

2. **iOS Tests**: 
   - iOS simulator tests will fail with `Xcode does not support simulator tests for ios_simulator_arm64. Check that requested SDK is installed.` if the required Xcode SDK is not installed.
   - These tests have not been verified on iOS.

3. **Web Tests**: 
   - All tests (both UI and non-UI) are verified to work on the web platform using the WasmJs target, as shown in the "Running the Tests" section.
   - This is currently the only fully supported testing environment for UI tests.

### Recommended Testing Strategy

Based on our testing results, we recommend the following approach:

1. **UI Tests**: Run tests that use `runComposeUiTest` only in the wasmJS environment, as this is the only verified platform for UI testing
2. **Non-UI Tests**: Tests for shared logic that don't use `runComposeUiTest` (like `testGanttChartStateBasics`) can be run on multiple platforms and may work without additional configuration
3. **Platform-Specific Testing**: If you need comprehensive UI testing on Android or iOS, you'll need to create platform-specific configurations to address the issues mentioned above

When developing new tests:
- For UI tests, focus on making them work in the wasmJS environment first
- For non-UI tests, design them to be platform-agnostic when possible
- Document any platform-specific configurations or limitations you discover
- Consider separating UI and non-UI tests to make it easier to run appropriate tests on each platform
