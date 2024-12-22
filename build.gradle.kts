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


val modVersion = "1.2.10"
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
            - Fix a compatability issue with polymer
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