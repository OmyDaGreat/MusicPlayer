import org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
import org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg
import org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi

plugins {
    alias(libs.plugins.compose.kotlin)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.compose)
    kotlin("plugin.serialization") version "2.2.10"
}

group = "xyz.malefic.compose"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation(libs.bundles.malefic.compose)
    implementation(compose.desktop.currentOs)
    implementation(libs.bundles.malefic.ext)
    implementation(libs.precompose)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // VLCJ for enhanced audio format support (Opus, M4A, etc.)
    implementation("uk.co.caprica:vlcj:4.8.2")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Enhanced metadata extraction and artwork support
    implementation("org:jaudiotagger:2.0.3") // Use available version
    implementation("org.mp4parser:isoparser:1.9.56") // For M4A/MP4 parsing
    implementation("com.drewnoakes:metadata-extractor:2.19.0") // Additional metadata extraction
    
    // Enhanced metadata support for more formats
    implementation("com.mpatric:mp3agic:0.9.1") // Alternative MP3 metadata
    implementation("org.gagravarr:vorbis-java-core:0.8") // Ogg/Vorbis metadata
    
    // HTTP client for downloading - updated versions
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")

    // JSON parsing - updated version
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // YouTube search and download capabilities
    implementation("org.json:json:20240303")
    implementation("org.jsoup:jsoup:1.17.2")

    // File system operations - updated version
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

compose.desktop {
    application {
        mainClass = "xyz.malefic.compose.MainKt"

        nativeDistributions {
            targetFormats(Dmg, Msi, Deb)
            packageName = "ComposeDesktopTemplate"
            packageVersion = "1.0.0"
        }
    }
}

tasks {
    register("formatAndLintKotlin") {
        group = "formatting"
        description = "Fix Kotlin code style deviations with kotlinter"
        dependsOn(named("formatKotlin"))
        dependsOn(named("lintKotlin"))
    }
    build {
        dependsOn(named("formatAndLintKotlin"))
    }
    check {
        dependsOn("installKotlinterPrePushHook")
    }
}
