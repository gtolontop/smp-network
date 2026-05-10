plugins {
    java
    checkstyle
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotless)
}

group = "fr.smp"
version = "0.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.get())

    // Folia API for RegionScheduler / GlobalRegionScheduler / isOwnedByCurrentRegion.
    // Paper ships shims so the plugin still builds against Paper alone, but we
    // pin folia-api to lock the contract.
    compileOnly(libs.folia.api)

    implementation(libs.configurate.yaml)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.sqlite)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    shadowJar {
        archiveBaseName.set("PtrFoundation")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())

        // Relocate everything but the SQLite JDBC driver — that one loads
        // native libs from its declared package name and breaks if relocated.
        relocate("com.zaxxer.hikari", "fr.smp.ptr.foundation.shaded.hikari")
        relocate("org.spongepowered.configurate", "fr.smp.ptr.foundation.shaded.configurate")
        relocate("io.leangen.geantyref", "fr.smp.ptr.foundation.shaded.geantyref")
        relocate("org.flywaydb", "fr.smp.ptr.foundation.shaded.flyway")
        relocate("org.yaml.snakeyaml", "fr.smp.ptr.foundation.shaded.snakeyaml")

        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    compileTestJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }
}

checkstyle {
    toolVersion = "10.21.1"
    configFile = file("config/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.25.2")
        removeUnusedImports()
        endWithNewline()
        trimTrailingWhitespace()
    }
}

// Spotless on `build` to fail fast in CI.
tasks.named("check") {
    dependsOn("spotlessCheck")
}
