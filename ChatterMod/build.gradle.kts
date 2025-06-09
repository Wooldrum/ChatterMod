plugins {
    id("fabric-loom") version "1.3.5"
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val minecraft_version = "1.21.5"
val fabric_loader_version = "0.16.14"
val fabric_api_version = "0.126.0+1.21.5"
val yarn_mappings = "1.21.5+build.1:v2"
val gson_version = "2.10.1"
val twitch4j_version = "1.20.0"

group = "com.wooldrum"
version = "1.0.0-BETA" // Updated version!

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings("net.fabricmc:yarn:$yarn_mappings")

    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")

    implementation("com.google.code.gson:gson:$gson_version")
    implementation("com.github.twitch4j:twitch4j-chat:$twitch4j_version")
    implementation("com.github.twitch4j:twitch4j-auth:$twitch4j_version")
    implementation("com.github.twitch4j:twitch4j-common:$twitch4j_version")
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

tasks.shadowJar {
    archiveClassifier.set("all")
    mergeServiceFiles()
    isZip64 = true
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.shadowJar)
        }
    }
}
