plugins {
    id("fabric-loom") version "1.3.5"
    id("maven-publish")
}

// Define versions in one place
val minecraft_version = "1.21.5"
val fabric_loader_version = "0.16.14"
val fabric_api_version = "0.126.0+1.21.5"
val yarn_mappings = "1.21.5+build.1:v2"
val gson_version = "2.10.1"
val twitch4j_version = "1.20.0"
val kick_api_version = "1.1.3" // Community API for Kick

group = "com.wooldrum"
version = "2.0.0" // Version bump for the new features

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
    // Repository for Twitch4J
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings("net.fabricmc:yarn:$yarn_mappings")

    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")

    // JSON parsing
    implementation("com.google.code.gson:gson:$gson_version")

    // Twitch4J for Twitch Integration (we need several of its modules)
    implementation("com.github.twitch4j:twitch4j-chat:$twitch4j_version")
    implementation("com.github.twitch4j:twitch4j-auth:$twitch4j_version")
    implementation("com.github.twitch4j:twitch4j-common:$twitch4j_version")

    // Java-Kick-API for Kick Integration
    implementation("com.github.jroy:Java-Kick-API:$kick_api_version")

    // SLF4J is required by Twitch4J, so we include a simple implementation
    implementation("org.slf4j:slf4j-simple:2.0.13")
}


java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
tasks.compileJava { options.release.set(21) }

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

// Shadow JAR to package dependencies (important for Twitch4J and Kick API)
loom {
    runs {
        // This is needed for the shadow jar task to work correctly
    }
}

tasks.jar {
    from(configurations.compileClasspath.map { if (it.isDirectory) it else zipTree(it) })
}
