package io.github.kotlinlabs.ganttly.models

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TaskHoverInfo(
    val taskId: String,
    val position: Offset  // Position for tooltip
)

// Debouncer utility for smoother hover detection
class Debouncer(private val delayMillis: Long) {
    private var job: Job? = null

    fun debounce(action: () -> Unit) {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            delay(delayMillis)
            action()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
