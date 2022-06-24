plugins {
    kotlin("jvm") version "+"
    kotlin("plugin.serialization") version "+"
    id("com.github.johnrengelman.shadow") version "+"
}

group "com.koisv"
version "1.0"

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.6.1-native-mt")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:+")
    implementation("org.slf4j:slf4j-simple:1.7.36")
    implementation("dev.kord:kord-core:0.8.0-M14")
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
        archiveVersion.set("R4j 1.0")
    }
    create<Copy>("dist") {
        from (shadowJar)
        into(".\\")
    }
}