import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "gantt-app"
        browser {
            commonWebpackConfig {
                outputFileName = "gantt-app.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf())
                }
            }
        }
        binaries.executable()
    }



    sourceSets {
        val wasmJsMain by getting { // Changed from jsMain
            dependencies {
                val composeBom = project.dependencies.platform("androidx.compose:compose-bom:2025.05.00")
                implementation(composeBom)
                implementation(projects.ganttly)
//                implementation(libs.ganttly)
                implementation(compose.runtime)
                implementation(compose.material3)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))

            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}
