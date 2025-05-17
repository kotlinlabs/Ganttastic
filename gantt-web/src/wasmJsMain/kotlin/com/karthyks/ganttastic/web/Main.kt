package com.karthyks.ganttastic.web

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.CanvasBasedWindow
import io.github.kotlinlabs.ganttly.chart.GanttChartView
import io.github.kotlinlabs.ganttly.chart.createSampleGanttState

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow("Ganttastic", canvasElementId = "ganttasticCanvas") {
        MaterialTheme {
            MaterialTheme {
                val ganttState = remember { createSampleGanttState() }

                GanttChartView(
                    state = ganttState,
                    headerContent = {
                        // This content will collapse on scroll
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Project Timeline Overview",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                "This Gantt chart shows all project tasks and their dependencies. " +
                                        "Hover over a task to see details.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Total Tasks: ${ganttState.tasks.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Text(
                                    "In Progress: ${ganttState.tasks.count { it.progress > 0 && it.progress < 1 }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Text(
                                    "Completed: ${ganttState.tasks.count { it.progress >= 1 }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    headerMaxHeight = 200.dp // Adjust as needed
                )
            }
        }
    }
}
