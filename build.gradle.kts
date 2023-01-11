plugins {
    kotlin("jvm") version "+"
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
    implementation("org.slf4j:slf4j-simple:+")
    implementation("dev.kord:kord-core:0.8.x-SNAPSHOT")
    implementation("dev.kord:kord-voice:0.8.x-SNAPSHOT")
    implementation("dev.kord:kord-gateway:0.8.x-SNAPSHOT")
    implementation("com.github.walkyst:lavaplayer-fork:+")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:+")
}
tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "17"
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