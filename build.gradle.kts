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

group = property("maven_group")!!
version = property("mod_version")!!

val releaseVersion = "${project.version}+mc${project.property("minecraft_version")}"

val shade by configurations.creating

repositories {
    maven {
        url = uri("https://maven.parchmentmc.org/")
    }
    maven {
        url = uri("https://masa.dy.fi/maven")
    }
    maven {
        url = uri("https://jitpack.io")
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
    maven {
        url = uri("https://api.modrinth.com/maven")
    }
    maven {
        url = uri("https://maven.maxhenkel.de/repository/public")
    }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    @Suppress("UnstableApiUsage")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${property("parchment_version")}@zip")
    })

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    modImplementation("com.github.gnembon:fabric-carpet:${property("carpet_version")}")
    modCompileOnly("maven.modrinth:simple-voice-chat:fabric-${property("voicechat_version")}")
    implementation("de.maxhenkel.voicechat:voicechat-api:${property("voicechat_api_version")}")

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
    include(modImplementation("me.lucko:fabric-permissions-api:${property("permissions_version")}")!!)

    // include(implementation(annotationProcessor("com.github.llamalad7.mixinextras:mixinextras-fabric:${property("mixin_extras_version")}")!!)!!)

    implementation(kotlin("stdlib-jdk8"))
}

loom {
    accessWidenerPath.set(file("src/main/resources/serverreplay.accesswidener"))

    runs {
        getByName("server") {
            runDir = "run/${project.property("minecraft_version")}"
        }

        getByName("client") {
            runDir = "run/client"
        }
    }
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand(mutableMapOf("version" to project.version))
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

        val minecraftVersion = "${property("minecraft_version")}"

        displayName = "ServerReplay ${project.version} for $minecraftVersion"
        version = "${project.version}+mc${minecraftVersion}"

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
                artifact(remapJar) {
                    builtBy(remapJar)
                }
                artifact(kotlinSourcesJar) {
                    builtBy(remapSourcesJar)
                }
            }
        }

        repositories {

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