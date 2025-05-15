package io.github.kotlinlabs.ganttly

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform