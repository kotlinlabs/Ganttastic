package io.github.kotlinlabs.ganttly.chart

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS implementation to open URL in the default browser
 */
actual fun openUrlInBrowser(url: String) {
    val nsUrl = NSURL.URLWithString(url)
    if (nsUrl != null && UIApplication.sharedApplication.canOpenURL(nsUrl)) {
        UIApplication.sharedApplication.openURL(nsUrl)
    } else {
        println("Cannot open URL: $url")
    }
}
