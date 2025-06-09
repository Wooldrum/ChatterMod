plugins {
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
    // Minecraft & Yarn Mappings
    minecraft("com.mojang:minecraft:1.21.5")
    mappings("net.fabricmc:yarn:1.21.5+build.1:v2")

    // Fabric Loader & API (using published, compatible versions)
    modImplementation("net.fabricmc:fabric-loader:0.16.10")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.126.0+1.21.5")

    // GSON for parsing YouTube's JSON responses
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    // Minecraft 1.21.x and modern Fabric API require Java 21
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.compileJava {
    options.release.set(21)
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
            groupId = project.group.toString()
            artifactId = rootProject.name
            version = project.version.toString()
        }
    }
}
