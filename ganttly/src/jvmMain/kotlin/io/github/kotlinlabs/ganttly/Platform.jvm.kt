package io.github.kotlinlabs.ganttly

actual fun getPlatform(): Platform {
    return object : Platform {
        override val name: String
            get() = "JVM"
    }
}
