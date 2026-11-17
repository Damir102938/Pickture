import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// Pickture для ПК (Windows): Compose Desktop поверх общего модуля :core.
val appVersion = project.property("pickture.version") as String

// Версия приложения доступна в коде как BuildInfo.VERSION
val generateBuildInfo by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildinfo")
    inputs.property("version", appVersion)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/moviepicker/desktop/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.moviepicker.desktop\n\nobject BuildInfo {\n    const val VERSION = \"$appVersion\"\n}\n"
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    sourceSets["main"].kotlin.srcDir(generateBuildInfo)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.moviepicker.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Pickture"
            packageVersion = appVersion
            description = "Новинки кино и сериалов и рекомендации по вашему вкусу"
            vendor = "Pickture"
            // Только нужные модули Java: TLS-шифры, русская локаль для дат, DataStore/OkHttp
            modules(
                "java.instrument", "jdk.unsupported", "java.naming", "java.net.http", "java.sql",
                "jdk.crypto.ec", "jdk.charsets", "jdk.localedata",
            )

            windows {
                iconFile.set(project.file("icons/pickture.ico"))
                menuGroup = "Pickture"
                shortcut = true
                perUserInstall = true
                upgradeUuid = "4f6a1c2e-8b3d-4e5f-9a7b-1c2d3e4f5a6b"
            }
        }
    }
}
