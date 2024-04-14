import org.apache.commons.io.output.ByteArrayOutputStream
import java.nio.charset.Charset

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.21"
    id("me.modmuss50.mod-publish-plugin") version "0.4.5"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("fabric-loom")
    `maven-publish`
    java
}

val modVersion: String by project
version = modVersion
group = "me.senseiwells"

val shade: Configuration by configurations.creating

repositories {
    maven("https://maven.parchmentmc.org/")
    maven("https://masa.dy.fi/maven")
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.maxhenkel.de/repository/public")
    mavenCentral()
}

val minecraftVersion: String by project
// Different name for loom...
val mcVersion = minecraftVersion
val parchmentVersion: String by project
val loaderVersion: String by project
val fabricVersion: String by project
val fabricKotlinVersion: String by project

val carpetVersion: String by project
val voicechatVersion: String by project
val voicechatApiVersion: String by project
val vmpVersion: String by project
val permissionsVersion: String by project

val releaseVersion = "${modVersion}+mc${minecraftVersion}"

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion}")
    @Suppress("UnstableApiUsage")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${parchmentVersion}@zip")
    })

    modImplementation("net.fabricmc:fabric-loader:${loaderVersion}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${fabricKotlinVersion}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabricVersion}")

    modImplementation("com.github.gnembon:fabric-carpet:${carpetVersion}")
    modCompileOnly("maven.modrinth:simple-voice-chat:fabric-${voicechatVersion}")
    implementation("de.maxhenkel.voicechat:voicechat-api:${voicechatApiVersion}")

    modCompileOnly("maven.modrinth:vmp-fabric:${vmpVersion}")

    // I've had some issues with ReplayStudio and slf4j (in dev)
    // Simplest workaround that I've found is just to unzip the
    // jar and yeet the org.slf4j packages then rezip the jar.
    shade(modImplementation("com.github.ReplayMod:ReplayStudio:6cd39b0874") {
        exclude(group = "org.slf4j")
        exclude(group = "it.unimi.dsi")
        exclude(group = "org.apache.commons")
        exclude(group = "commons-cli")
        exclude(group = "com.google.guava", module = "guava-jdk5")
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.google.code.gson", module = "gson")
    })
    include(modImplementation("me.lucko:fabric-permissions-api:${permissionsVersion}")!!)

    // include(implementation(annotationProcessor("com.github.llamalad7.mixinextras:mixinextras-fabric:${property("mixin_extras_version")}")!!)!!)
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
    processResources {
        inputs.property("version", modVersion)
        filesMatching("fabric.mod.json") {
            expand(mutableMapOf("version" to modVersion))
        }
    }

    remapJar {
        archiveVersion.set(releaseVersion)

        inputFile.set(shadowJar.get().archiveFile)
    }

    remapSourcesJar {
        archiveVersion.set(releaseVersion)
    }

    shadowJar {
        destinationDirectory.set(File("./build/devlibs"))
        isZip64 = true

        from("LICENSE")

        relocate("com.github.steveice10.netty", "io.netty")
        exclude("com/github/steveice10/netty/**")
        configurations = listOf(shade)

        archiveClassifier = "shaded"
    }

    publishMods {
        file = remapJar.get().archiveFile
        changelog.set(
            """
            - Added support for [simple-voice-chat](https://github.com/henkelmax/simple-voice-chat)
            - Added new player predicate `"type": "is_fake"` to check whether a player is not a real player (e.g. carpet bot)
            - Added `max_duration` that lets you specify a maximum duration for your replay
            - Added `restart_after_max_duration` that lets you automatically restart the replay if the max duration limit is met
            - Fixed a bug that would cause gradual server lag if `max_file_size` was set
            """.trimIndent()
        )
        type = STABLE
        modLoaders.add("fabric")

        displayName = "ServerReplay $modVersion for $minecraftVersion"
        version = releaseVersion

        modrinth {
            accessToken = providers.environmentVariable("MODRINTH_API_KEY")
            projectId = "qCvSZ8ra"
            minecraftVersions.add(minecraftVersion)

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

    compileKotlin {
        kotlinOptions.jvmTarget = "17"
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