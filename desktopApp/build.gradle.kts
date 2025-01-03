import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val skikoVersion = "0.8.19"
val ktorVer = "3.0.3"
val log4jVer = "2.24.2"
//val nav_version = "2.8.5"

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

val osName = System.getProperty("os.name")
val targetOs = when {
    osName == "Mac OS X" -> "macos"
    osName.startsWith("Win") -> "windows"
    osName.startsWith("Linux") -> "linux"
    else -> error("Unsupported OS: $osName")
}

val osArch = System.getProperty("os.arch")
val targetArch = when (osArch) {
    "x86_64", "amd64" -> "x64"
    "aarch64" -> "arm64"
    else -> error("Unsupported arch: $osArch")
}
val target = "${targetOs}-${targetArch}"

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    //implementation("org.jetbrains.skiko:skiko-awt-runtime-$target:$skikoVersion")
    implementation(compose.materialIconsExtended)
    implementation(compose.desktop.currentOs)
    annotationProcessor("org.apache.logging.log4j:log4j-core:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVer")
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
    implementation("io.ktor:ktor-client-core:$ktorVer")
    implementation("io.ktor:ktor-client-cio:$ktorVer")
    implementation("io.ktor:ktor-client-websockets:$ktorVer")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.5.31")
    testImplementation("io.mockk:mockk:1.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

tasks {
    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_22)
    }
    test {
// previous JVM options
        jvmArgs("-noverify", "-XX:+EnableDynamicAgentLoading", "-Djdk.instrument.traceUsage") // add this
    }

    compose.desktop {
        application {
            mainClass = "com.koisv.kcdesktop.MainKt"

            nativeDistributions {
                targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Deb)

                windows {
                    iconFile = file("icons/icon.ico")
                    perUserInstall = true
                    shortcut = true
                    upgradeUuid = "c4b9d9db-dc78-4de4-b670-783177e57b87"
                    menuGroup = "KoiSV"
                }

                macOS {
                    iconFile = file("icons/icon.icns")
                    bundleID = "com.koisv.kcdesktop"
                    this.appStore = false
                    this.dockName = "KoiChat"
                }

                linux {
                    iconFile = file("icons/icon.png")
                    packageName = "KoiChat Desktop"
                    menuGroup = "KoiSV"
                }

                modules(
                    "java.io", "java.sequrity", "java.time", "java.util", "javax.crypto", "javax.net",
                    "kotlin.io", "kotlin.jvm", "kotlin.text", "kotlin.time", "kotlinx.coroutines"
                )
                includeAllModules = true
                outputBaseDir = file("output")
                vendor = "KeiKoi"
                packageName = "KoiChat Desktop"
                packageVersion = "1.0.0"
            }
        }
    }
}