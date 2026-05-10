plugins {
    id("net.fabricmc.fabric-loom") version "1.15-SNAPSHOT"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.nucleoid.xyz/") { name = "Nucleoid" }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
    maven("https://oss.sonatype.org/content/repositories/snapshots") { name = "Sonatype Snapshots" }
}

dependencies {
    // Minecraft 26.1+ ships unobfuscated; no remapping, no mappings.
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    compileOnly("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    compileOnly("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    val polymerVersion = project.property("polymer_version") as String
    compileOnly("eu.pb4:polymer-core:$polymerVersion")
    compileOnly("eu.pb4:polymer-blocks:$polymerVersion")
    compileOnly("eu.pb4:polymer-virtual-entity:$polymerVersion")
    compileOnly("eu.pb4:polymer-resource-pack:$polymerVersion")
    compileOnly("eu.pb4:polymer-resource-pack-extras:$polymerVersion")
    compileOnly("eu.pb4:polymer-autohost:$polymerVersion")
    compileOnly("eu.pb4:polymer-networking:$polymerVersion")
    compileOnly("eu.pb4:polymer-common:$polymerVersion")
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
