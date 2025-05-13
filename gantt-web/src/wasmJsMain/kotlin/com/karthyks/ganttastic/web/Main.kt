package com.karthyks.ganttastic.web

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.karthyks.ganttastic.chart.GanttChartView
import com.karthyks.ganttastic.chart.createSampleGanttState

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow("Ganttastic", canvasElementId = "ganttasticCanvas") {
        MaterialTheme {
            val ganttState = remember { createSampleGanttState() }

            GanttChartView(ganttState)
        }
    }
}
