plugins {
    id("fabric-loom") version "1.16-SNAPSHOT"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.nucleoid.xyz/") { name = "Nucleoid" }
    maven("https://oss.sonatype.org/content/repositories/snapshots") { name = "Sonatype Snapshots" }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    val polymerVersion = project.property("polymer_version") as String
    modImplementation("eu.pb4:polymer-core:$polymerVersion")
    modImplementation("eu.pb4:polymer-blocks:$polymerVersion")
    modImplementation("eu.pb4:polymer-virtual-entity:$polymerVersion")
    modImplementation("eu.pb4:polymer-resource-pack:$polymerVersion")
    modImplementation("eu.pb4:polymer-resource-pack-extras:$polymerVersion")
    modImplementation("eu.pb4:polymer-autohost:$polymerVersion")
    modImplementation("eu.pb4:polymer-networking:$polymerVersion")
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
