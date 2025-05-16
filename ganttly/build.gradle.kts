import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    id("com.vanniktech.maven.publish") version "0.32.0"
}

group = "io.github.kotlinlabs"
version = "0.1.2"

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "ganttly"
            isStatic = true
        }
    }

    wasmJs { // Changed from js(IR)
        browser()
        binaries.executable()
    }


    sourceSets {
        val commonMain by getting {
            val composeBom = project.dependencies.platform(libs.androidx.compose.bom)
            dependencies {
                implementation(composeBom)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.datetime)
            }
        }
    }

}

android {
    namespace = "io.github.kotlinlabs.ganttly"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates("$group", name, "$version")

    pom {
        name.set("Ganttly")
        description.set("A library to create gantt charts in Compose Multiplatform")
        inceptionYear.set("2025")
        url.set("https://github.com/kotlinlabs/Ganttastic")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("karthyks")
                name.set("Karthik Periasami")
                url.set("https://github.com/kotlinlabs/")
            }
        }
        scm {
            url.set("https://github.com/kotlinlabs/Ganttastic/")
            connection.set("scm:git:git://github.com/kotlinlabs/Ganttastic.git")
            developerConnection.set("scm:git:ssh://git@github.com/kotlinlabs/Ganttastic.git")
        }
    }
}

tasks.named("signWasmJsPublication").configure {
    outputs.upToDateWhen { false }
}
