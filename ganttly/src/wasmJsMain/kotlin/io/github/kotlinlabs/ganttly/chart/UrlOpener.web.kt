package io.github.kotlinlabs.ganttly.chart

/**
 * Web implementation to open URL in a new browser tab
 */
actual fun openUrlInBrowser(url: String) {
    kotlinx.browser.window.open(url, "_blank")
}
