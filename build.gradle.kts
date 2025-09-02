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
    maven("https://maven.andante.dev/releases/")
    mavenCentral()
}


val modVersion = "3.0.0-beta.1"
val releaseVersion = "${modVersion}+${libs.versions.minecraft.get()}"
version = releaseVersion
group = "me.senseiwells"

dependencies {
    minecraft(libs.minecraft)
    @Suppress("UnstableApiUsage")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${libs.versions.parchment.get()}@zip")
    })

    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
    modImplementation(libs.fabric.kotlin)

    includeModImplementation(libs.arcade.replay)
    includeModImplementation(libs.arcade.commands)
    includeModImplementation(libs.arcade.event.registry)
    includeModImplementation(libs.arcade.events.server)
    includeModImplementation(libs.arcade.rph)
    includeModImplementation(libs.arcade.utils)

    includeModImplementation(libs.permissions)
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
            expand(mutableMapOf("version" to modVersion))
        }
    }

    publishMods {
        file = remapJar.get().archiveFile
        changelog.set(
            """
            # ServerReplay $modVersion
            
            This update completely reworks the internals of the mod to allow for more flexibility
            and hopefully will make future development of replay based moderation tools easier.
            
            This version is in *beta*, if you encounter any bugs please report them
            to the issue tracker: https://github.com/senseiwells/ServerReplay/issues
            
            This version also comes with numerous features and bug fixes:
            - Re-added the `max_file_size` config option
              - The behaviour of this differs from the previous behavior
                as this refers to the max *raw* file size before the replay
                has been compressed, see the documentation for more information.
            - Added the `record_hotbar` config option to allow for recording
              the player's hotbar (flashback only)
            - Added the `chunk_recording_strategy` config option to allow you
              to specify chunk recorder pausing behaviour, allowing you to only
              record (and otherwise pause) under certain conditions, the options are:
              `"always"`, `"chunk_loaded"`, `"chunk_contains_player"`, and 
              `"chunk_contains_non_spectator_player"`
            - Replaced the `enable` config option with the `automatically_record`
              option, as well as removed the command to enable/disable ServerReplay. 
              This toggle was quite ambiguous as it still allowed you to record but 
              just disabled the server automatically recording. The new config option
              reflects this much better.
            - Added the ability to record both flashback and replay-mod format
              recordings for players at the same time
              - Currently the only way to do this is to set the default encoding method
                to replay-mod start a recording, then switch the encoding to flashback
                (or vice versa) then starting another recording.
            - Added the ability to chunk record the same chunk area with multiple recorders
              - With the caveat that you each chunk recorder must have a unique name in order
                to do this.
              - As a consequence this also allows you to record both flashback and replay-mod
                replays of the same chunk area at the same time.
            - Added Simple Voice Chat support for flashback replays
            - Fixed the recording quality of Simple Voice Chat
            - Fixed time-limited recordings not accounting for server pausing
            - Fixed an issue where an exception would sometimes occur when shutting
              down the server
            """.trimIndent()
        )
        type = BETA
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

private fun DependencyHandler.includeModImplementation(provider: Provider<*>) {
    include(provider)
    modImplementation(provider)
}