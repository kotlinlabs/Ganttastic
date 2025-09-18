package io.github.kotlinlabs.ganttly.chart

import java.awt.Desktop
import java.net.URI

/**
 * Desktop JVM implementation to open URL in the default browser
 */
actual fun openUrlInBrowser(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            // Fallback for systems without Desktop support
            val os = System.getProperty("os.name").lowercase()
            val runtime = Runtime.getRuntime()
            when {
                os.contains("win") -> runtime.exec("rundll32 url.dll,FileProtocolHandler $url")
                os.contains("mac") -> runtime.exec("open $url")
                os.contains("nix") || os.contains("nux") -> runtime.exec("xdg-open $url")
                else -> println("Cannot open URL: $url (unsupported platform)")
            }
        }
    } catch (e: Exception) {
        println("Failed to open URL: $url, error: ${e.message}")
    }
}
