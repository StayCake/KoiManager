import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Extracted constants for readability
val MYSQL_CONNECTOR_VERSION = "9.1.0"
val LAVAPLAYER_VERSION = "2.2.2"
val YOUTUBE_LIBRARY_VERSION = "1.10.2"
val INTELLIJ_FORMS_RT_VERSION = "7.0.3"
val ktorVer: String by project
val kordVer: String by project
val log4jVer: String by project
val kotlinVer: String by project

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("kapt") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}

group = "com.koisv"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.kord.dev/snapshots")
    maven("https://m2.dv8tion.net/releases")
    maven("https://maven.lavalink.dev/snapshots")
    maven("https://maven.lavalink.dev/releases")
}

dependencies {
    kapt("org.apache.logging.log4j:log4j-core:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVer")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVer")
    implementation("dev.kord:kord-core:$kordVer")
    implementation("dev.kord:kord-voice:$kordVer")
    implementation("dev.kord:kord-core-voice:$kordVer")
    implementation("dev.kord:kord-gateway:$kordVer")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVer")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVer")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktorVer")
    implementation("io.ktor:ktor-server-jetty-jakarta:$ktorVer")
    implementation("io.ktor:ktor-server-websockets:$ktorVer")
    implementation("io.ktor:ktor-network:$ktorVer")
    implementation("io.ktor:ktor-network-tls:$ktorVer")
    implementation("com.mysql:mysql-connector-j:$MYSQL_CONNECTOR_VERSION")
    implementation("dev.arbjerg:lavaplayer:$LAVAPLAYER_VERSION")
    implementation("dev.lavalink.youtube:v2:$YOUTUBE_LIBRARY_VERSION")
    implementation("com.intellij:forms_rt:$INTELLIJ_FORMS_RT_VERSION")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVer")
}
tasks {
    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_22)
    }
    processResources {
        filesMatching("**/*.yml") {
            expand(project.properties)
        }
        filteringCharset = "UTF-8"

    }
    shadowJar {
        manifest {
            attributes["Main-Class"] = "com.koisv.dkm.MainKt"
        }
        archiveClassifier.set("release")
        archiveVersion.set("Kord-R1.0")
    }
    create<Copy>("dist") {
        from (shadowJar)
        into(".\\")
    }
}