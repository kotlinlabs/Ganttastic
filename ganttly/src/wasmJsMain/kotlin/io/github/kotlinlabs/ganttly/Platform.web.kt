package io.github.kotlinlabs.ganttly

class WebPlatform: Platform {
    override val name: String
        get() = "Hello from JS"
}

actual fun getPlatform(): Platform = WebPlatform()