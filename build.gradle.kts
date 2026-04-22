plugins {
    val jvmVersion = libs.versions.fabric.kotlin.get()
        .split("+kotlin.")[1]
        .split("+")[0]

    kotlin("jvm").version(jvmVersion)
    kotlin("plugin.serialization").version(jvmVersion)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.mod.publish)
    `maven-publish`
    java
}

repositories {
    mavenLocal()
    maven("https://maven.supersanta.me/snapshots")
    maven("https://maven.parchmentmc.org/")
    maven("https://jitpack.io")
    mavenCentral()
}

val modVersion = "3.4.1"
val releaseVersion = "${modVersion}+${libs.versions.minecraft.get()}"
version = releaseVersion
group = "me.senseiwells"

dependencies {
    minecraft(libs.minecraft)

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.kotlin)

    include(implementation(libs.arcade.replay.get())!!)
    include(implementation(libs.arcade.commands.get())!!)
    include(implementation(libs.arcade.event.registry.get())!!)
    include(implementation(libs.arcade.events.server.get())!!)
    include(implementation(libs.arcade.interceptor.get())!!)
    include(implementation(libs.arcade.rph.get())!!)
    include(implementation(libs.arcade.utils.get())!!)

    include(implementation(libs.permissions.get())!!)
}

loom {
    runs {
        getByName("server") {
            runDir = "run/${libs.versions.minecraft.get()}"
        }

        getByName("client") {
            runDir = "run/client"
        }
    }
}

java {
    withSourcesJar()
}

tasks {
    processResources {
        inputs.property("version", modVersion)
        filesMatching("fabric.mod.json") {
            expand(mutableMapOf(
                "version" to version,
                "minecraft_dependency" to "~${libs.versions.minecraft.get()}",
                "fabric_api_dependency" to libs.versions.fabric.api.get(),
                "fabric_kotlin_dependency" to libs.versions.fabric.kotlin.get(),
            ))
        }
    }

    publishMods {
        file = jar.get().archiveFile
        changelog.set(
            """
            - Updated to 26.1.x
            """.trimIndent()
        )
        type = STABLE
        modLoaders.add("fabric")

        displayName = "ServerReplay $modVersion for ${libs.versions.minecraft.get()}"
        version = releaseVersion

        modrinth {
            accessToken = providers.environmentVariable("MODRINTH_API_KEY")
            projectId = "qCvSZ8ra"
            minecraftVersions.add(libs.versions.minecraft)

            requires {
                id = "P7dR8mSH"
            }
            requires {
                id = "Ha28R6CL"
            }
            optional {
                id = "Vebnzrzj"
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("ServerReplay") {
            groupId = "me.senseiwells"
            artifactId = "server-replay"
            version = releaseVersion
            from(components["java"])
        }
    }

    repositories {
        val mavenUrl = System.getenv("MAVEN_URL")
        if (mavenUrl != null) {
            maven {
                url = uri(mavenUrl)
                val mavenUsername = System.getenv("MAVEN_USERNAME")
                val mavenPassword = System.getenv("MAVEN_PASSWORD")
                if (mavenUsername != null && mavenPassword != null) {
                    credentials {
                        username = mavenUsername
                        password = mavenPassword
                    }
                }
            }
        }
    }
}