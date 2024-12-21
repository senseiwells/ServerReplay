rootProject.name = "ServerReplay"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("libs.versions.toml"))
        }
    }
}

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven4.bai.lol")
        mavenCentral()
        gradlePluginPortal()
    }
}