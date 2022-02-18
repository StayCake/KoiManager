plugins {
    kotlin("jvm") version "+"
    kotlin("plugin.serialization") version "+"
    id("com.github.johnrengelman.shadow") version "+"
}

group "com.koisv"
version "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://m2.dv8tion.net/releases")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:+")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.5.1-native-mt")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:+")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:+")
    implementation("org.slf4j:slf4j-simple:+")
    implementation("com.discord4j:discord4j-core:+")
    implementation("io.netty:netty-tcnative:+")
    implementation("com.google.http-client:google-http-client-jackson2:+")
    implementation("com.google.apis:google-api-services-youtube:+")
    implementation("com.sedmelluq:lavaplayer:1.3.77")
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