plugins {
    val jvmVersion = libs.versions.fabric.kotlin.get()
        .split("+kotlin.")[1]
        .split("+")[0]

    kotlin("jvm").version(jvmVersion)
    kotlin("plugin.serialization").version(jvmVersion)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.mod.publish)
    alias(libs.plugins.shadow)
    alias(libs.plugins.explosion)
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
    maven("https://maven.andante.dev/releases/")
    maven("https://maven4.bai.lol")
    maven("https://maven.nucleoid.xyz")
    mavenCentral()
}


val modVersion = "2.0.1"
val releaseVersion = "${modVersion}+mc${libs.versions.minecraft.get()}"
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

    include(implementation(libs.inject.api.get())!!)
    include(implementation(libs.inject.http.get())!!)
    include(modImplementation(libs.inject.fabric.get())!!)

    modCompileOnly(libs.carpet)
    modCompileOnly(libs.vmp)
    modCompileOnly(explosion.fabric(libs.c2me))
    modCompileOnly(libs.servux)
    modCompileOnly(libs.syncmatica)
    modCompileOnly(libs.voicechat)
    modCompileOnly(libs.polymer.core)
    compileOnly(libs.voicechat.api)

    shade(implementation(libs.replay.studio.get())!!)
    includeModImplementation(libs.permissions) {
        exclude(libs.fabric.api.get().group)
    }
}

loom {
    accessWidenerPath.set(file("src/main/resources/serverreplay.accesswidener"))

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

        exclude("it/unimi/dsi/**")
        exclude("org/apache/commons/**")
        exclude("org/xbill/DNS/**")
        exclude("com/google/**")

        configurations = listOf(shade)

        archiveClassifier = "shaded"
    }

    publishMods {
        file = remapJar.get().archiveFile
        changelog.set(
            """
            # ServerReplay $modVersion
            
            This is quite a big update to ServerReplay with lots of internal
            changes. ServerReplay now supports recording to the flashback
            format! This support is experimental, and usage may result in 
            corrupted recordings, please report any bugs you encounter to the github.
            
            Replay mod support will still be maintained for the foreseeable future,
            and remains the default recording method.
            
            To change the format to flashback you can run the `/replay encoding set flashback`,
            to change back to replay mod you can run `/replay encoding set replay-mod`.
            
            There are some features of flashback that aren't currently available:
            - No flashback voicechat support
            - No viewing flashback replays server-side
            - Flashback will not save resource packs, this is a limitation of flashback itself,
            resource packs in replays will still be loaded if the packs are still being hosted externally
            
            **Other changes this update:**
            - Fixed an issue where you couldn't use ServerReplay in Singleplayer
            - Fixed compatability with servux
            - Added config option `"ignore_custom_payloads"` which ignores custom payload
            packets, this may resolve compatability issues with some mods, but will break others
            - Deprecated `"max_file_size"` and `"include_compressed_in_status"`, for longer replays
            these options are just too expensive to feasibly use, these options do not work for the
            new flashback format and will eventually be removed for replay mod replays. It's advised
            to use `"max_duration"` instead.
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
            version = "${modVersion}+${libs.versions.minecraft.get()}"
            from(components["java"])

            updateReadme("./README.md")
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

private fun DependencyHandler.includeModImplementation(provider: Provider<*>, action: Action<ExternalModuleDependency>) {
    include(provider, action)
    modImplementation(provider, action)
}

private fun MavenPublication.updateReadme(vararg readmes: String) {
    val location = "${groupId}:${artifactId}"
    val regex = Regex("""${Regex.escape(location)}:[\d\.\-a-zA-Z+]+""")
    val locationWithVersion = "${location}:${version}"
    for (path in readmes) {
        val readme = file(path)
        readme.writeText(readme.readText().replace(regex, locationWithVersion))
    }
}