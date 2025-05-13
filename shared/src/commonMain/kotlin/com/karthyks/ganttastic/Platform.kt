package com.karthyks.ganttastic

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform