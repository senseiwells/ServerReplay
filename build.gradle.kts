import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
    kotlin("jvm")
    id("fabric-loom")
    id("io.github.juuxel.loom-quiltflower").version("1.7.3")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    `maven-publish`
    java
}

group = property("maven_group")!!
version = property("mod_version")!!

val shade: Configuration by configurations.creating

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
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${property("parchment_version")}@zip")
    })

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    // modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    shade(modImplementation("com.github.ReplayMod:ReplayStudio:a1e2b83") {
        exclude(group = "com.google.guava", module = "guava-jdk5")
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.google.code.gson", module = "gson")
        isTransitive = false
    })
    include(modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")!!)

    // include(implementation(annotationProcessor("com.github.llamalad7.mixinextras:mixinextras-fabric:0.2.0-beta.6")!!)!!)

    implementation(kotlin("stdlib-jdk8"))
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand(mutableMapOf("version" to project.version))
        }
    }

    prepareRemapJar {
        dependsOn("shadowJar")
    }

    remapJar {
        remapperIsolation.set(true)
        inputFile.set(shadowJar.get().archiveFile.get())
        doLast {
            // shadowJar.archiveFile.get().asFile.delete()
        }
    }

    jar {
        from("LICENSE")
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
}

tasks.shadowJar {
    configurations = listOf(shade)

    from("LICENSE")

    relocate("org.slf4j", "me.senseiwells.org.slf4j")
    exclude("it.unimi", "com.steveice.opennbt", "org.apache")

    minimize()
    //archiveClassifier.set("fat")

    // archiveFileName.set("${rootProject.name}-${archiveVersion.get()}.jar")
}

java {
    withSourcesJar()
}
