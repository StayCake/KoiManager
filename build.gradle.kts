import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.10"
    kotlin("plugin.serialization") version "+"
    id("com.github.johnrengelman.shadow") version "+"
}

group "com.koisv"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://m2.dv8tion.net/releases")
}
dependencies {
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("dev.kord:kord-core:0.14.0-SNAPSHOT")
    implementation("dev.kord:kord-voice:0.14.0-SNAPSHOT")
    implementation("dev.kord:kord-core-voice:0.14.0-SNAPSHOT")
    implementation("dev.kord:kord-gateway:0.14.0-SNAPSHOT")
    implementation("com.github.walkyst:lavaplayer-fork:1.4.3")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.0.10")
}
tasks {
    compileKotlin {
        this.compilerOptions {
            jvmTarget.set(JvmTarget.JVM_22)
        }
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