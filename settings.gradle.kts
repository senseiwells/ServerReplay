rootProject.name = "ServerReplay"
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        mavenCentral()
        gradlePluginPortal()
    }

    val loomVersion: String by settings
    val fabricKotlinVersion: String by settings
    plugins {
        id("fabric-loom") version loomVersion
        id("org.jetbrains.kotlin.jvm") version
                fabricKotlinVersion
                    .split("+kotlin.")[1] // Grabs the sentence after `+kotlin.`
                    .split("+")[0] // Ensures sentences like `+build.1` are ignored
    }
}