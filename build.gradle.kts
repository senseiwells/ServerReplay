import org.apache.commons.io.output.ByteArrayOutputStream
import java.nio.charset.Charset

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.0.0"
    id("me.modmuss50.mod-publish-plugin") version "0.4.5"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("fabric-loom")
    `maven-publish`
    java
}

val shade: Configuration by configurations.creating

repositories {
    maven("https://maven.parchmentmc.org/")
    maven("https://masa.dy.fi/maven")
    maven("https://jitpack.io")
    maven("https://repo.viaversion.com")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.maxhenkel.de/repository/public")
    mavenCentral()
}

val modVersion: String by project
val mcVersion: String by project
val parchmentVersion: String by project
val loaderVersion: String by project
val fabricVersion: String by project
val fabricKotlinVersion: String by project

val carpetVersion: String by project
val voicechatVersion: String by project
val voicechatApiVersion: String by project
val permissionsVersion: String by project

val releaseVersion = "${modVersion}+mc${mcVersion}"
version = releaseVersion
group = "me.senseiwells"

dependencies {
    minecraft("com.mojang:minecraft:${mcVersion}")
    @Suppress("UnstableApiUsage")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${parchmentVersion}@zip")
    })

    modImplementation("net.fabricmc:fabric-loader:${loaderVersion}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${fabricKotlinVersion}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabricVersion}")

    modImplementation("carpet:fabric-carpet:${mcVersion}-${carpetVersion}")
    modCompileOnly("maven.modrinth:simple-voice-chat:fabric-${voicechatVersion}")
    implementation("de.maxhenkel.voicechat:voicechat-api:${voicechatApiVersion}")

    // I've had some issues with ReplayStudio and slf4j (in dev env)
    // Simplest workaround that I've found is just to unzip the
    // jar and yeet the org.slf4j packages then rezip the jar.
    shade(modImplementation("com.github.ReplayMod:ReplayStudio:1e96fda605") {
        exclude(group = "org.slf4j")
        exclude(group = "it.unimi.dsi")
        exclude(group = "org.apache.commons")
        exclude(group = "commons-cli")
        exclude(group = "com.google.guava", module = "guava-jdk5")
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.google.code.gson", module = "gson")
    })
    include(modImplementation("me.lucko:fabric-permissions-api:${permissionsVersion}") {
        exclude("net.fabricmc.fabric-api")
    })
}

loom {
    accessWidenerPath.set(file("src/main/resources/serverreplay.accesswidener"))

    runs {
        getByName("server") {
            runDir = "run/$mcVersion"
        }

        getByName("client") {
            runDir = "run/client"
        }
    }
}

tasks {
    register("relocateResources") {

    }

    processResources {
        inputs.property("version", modVersion)
        filesMatching("fabric.mod.json") {
            expand(mutableMapOf("version" to modVersion))
        }
    }

    remapJar {
        inputFile.set(shadowJar.get().archiveFile)
    }

    shadowJar {
        destinationDirectory.set(File("./build/devlibs"))
        isZip64 = true

        from("LICENSE")

        // For compatability with viaversion
        relocate("assets/viaversion", "assets/replay-viaversion")

        relocate("com.github.steveice10.netty", "io.netty")
        exclude("com/github/steveice10/netty/**")
        configurations = listOf(shade)

        archiveClassifier = "shaded"
    }

    publishMods {
        file = remapJar.get().archiveFile
        changelog.set(
            """
            - Added new Server Side Replay Viewer
                - You can now view your replays completely server-side!
                - Added the `/replay view` command
                - When your replays finish saving a message will appear, if clicked you can view the replay
            - Added new config "chunk_recorder_load_radius" which allows you to specify a maximum radius that will be initially loaded
            - Fixes an error when VoiceChat was enabled
            - Fixes compatability with ViaVersion
            - Fixes compatability with VeryManyPlayers
            """.trimIndent()
        )
        type = STABLE
        modLoaders.add("fabric")

        displayName = "ServerReplay $modVersion for $mcVersion"
        version = releaseVersion

        modrinth {
            accessToken = providers.environmentVariable("MODRINTH_API_KEY")
            projectId = "qCvSZ8ra"
            minecraftVersions.add(mcVersion)

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

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(project.components.getByName("java"))
            }
        }
    }

    register("updateReadme") {
        val readmes = listOf("./README.md")
        val regex = Regex("""com.github.Senseiwells:ServerReplay:[a-z0-9]+""")
        val replacement = "com.github.Senseiwells:ServerReplay:${getGitHash()}"
        for (path in readmes) {
            val readme = file(path)
            readme.writeText(readme.readText().replace(regex, replacement))
        }

        println("Successfully updated all READMEs")
    }
}

java {
    withSourcesJar()
}

fun getGitHash(): String {
    val out = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short=10", "HEAD")
        standardOutput = out
    }
    return out.toString(Charset.defaultCharset()).trim()
}