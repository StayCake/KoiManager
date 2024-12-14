import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val ktorVer = "3.0.1"

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.koisv"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    implementation("io.ktor:ktor-client-core:$ktorVer")
    implementation("io.ktor:ktor-client-cio:$ktorVer")
    implementation("io.ktor:ktor-client-websockets:$ktorVer")
}

compose.desktop {
    application {
        mainClass = "com.koisv.koichatcfd.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "koichatcfd"
            packageVersion = "1.0.0"
        }
    }
}
