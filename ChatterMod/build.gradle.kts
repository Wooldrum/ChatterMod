// File: build.gradle.kts
plugins {
    // Fabric Loom plugin for Minecraft modding
    id("fabric-loom") version "1.3.5"
    id("maven-publish")
}

group = "com.wooldrum"
version = "1.0.0"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
}

dependencies {
    // Minecraft & Yarn mappings (swap build.5 → build.1)
    minecraft("com.mojang:minecraft:1.21.5")
    mappings("net.fabricmc:yarn:1.21.5+build.1:v2") // ← use build.1, not build.5 :contentReference[oaicite:1]{index=1}

    // Fabric Loader & Fabric API
    modImplementation("net.fabricmc:fabric-loader:0.17.3")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.88.0+1.21.5")

    // GSON for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId    = project.group.toString()
            artifactId = rootProject.name
            version    = project.version.toString()
        }
    }
}
