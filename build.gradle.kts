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

val modVersion = "3.5.1"
val releaseVersion = "${modVersion}+${libs.versions.minecraft.get()}"
version = releaseVersion
group = "me.senseiwells"

dependencies {
    minecraft(libs.minecraft)

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.kotlin)

    include(libs.bundles.arcade)
    implementation(libs.bundles.arcade)
}

loom {
    runs {
        getByName("server") {
            runDirectory.set(file("run/${libs.versions.minecraft.get()}"))
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
            - Re-enable replay-mod support
            - Fix automatic flashback recordings not playing back
            - Fix a few other minor bugs
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

            file("README.md")
            projectDescription.set(createProjectDescription())

            requires {
                slug = "fabric-api"
            }
            requires {
                slug = "fabric-language-kotlin"
            }
            optional {
                slug = "luckperms"
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

fun createProjectDescription(): String {
    val description = StringBuilder(file("README.md").readText())

    fun replaceTranslations() {
        val regex = Regex("""\./README_(.+)\.md""")
        for (result in regex.findAll(description)) {
            val range = result.groups[0]!!.range
            val lang = result.groups[1]!!.value
            val url = "https://github.com/senseiwells/ServerReplay/blob/HEAD/README_${lang}.md"
            description.replace(range.first, range.last + 1, url)
        }
    }

    fun replaceNotes() {
        val regex = Regex("""\[!([A-Z]+)\]""")
        for (result in regex.findAll(description)) {
            val range = result.groups[0]!!.range
            val type = result.groups[1]!!.value
            val formatted = type.lowercase().replaceFirstChar { c -> c.uppercase() }
            description.replace(range.first, range.last + 1, "$formatted:")
        }
    }

    replaceTranslations()
    replaceNotes()
    return description.toString()
}