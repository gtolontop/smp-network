import java.math.BigInteger
import java.security.MessageDigest

plugins {
    java
    checkstyle
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.shadow)
    // Spotless is intentionally NOT applied yet. Both google-java-format
    // 1.25.x and palantir-java-format 2.50 throw NoSuchMethodError on JDK
    // 25's javac internals; the foundation runs on Java 25 by mandate. The
    // checkstyle profile below catches the architectural mistakes the
    // formatter would have. Re-enable once gjf/palantir publish JDK 25
    // builds — see docs/V3_ROADMAP.md.
    // alias(libs.plugins.spotless)
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

    // Folia API is intentionally NOT declared as a separate dependency.
    // paper-api and folia-api both publish the `paper-mojangapi` capability
    // and Gradle refuses to resolve both. Paper exposes Folia's
    // RegionScheduler / GlobalRegionScheduler / AsyncScheduler / Entity
    // scheduler shims natively since 1.20.6, so the foundation compiles
    // against paper-api alone and runs unchanged on Folia.

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

        // Don't ship the resource pack inside the plugin jar — it's a separate
        // artifact built by `buildResourcePack` below.
        exclude("pack/**")

        // Relocate everything but the SQLite JDBC driver — that one loads
        // native libs from its declared package name and breaks if relocated.
        relocate("com.zaxxer.hikari", "fr.smp.ptr.foundation.shaded.hikari")
        relocate("org.spongepowered.configurate", "fr.smp.ptr.foundation.shaded.configurate")
        relocate("io.leangen.geantyref", "fr.smp.ptr.foundation.shaded.geantyref")
        relocate("org.flywaydb", "fr.smp.ptr.foundation.shaded.flyway")
        relocate("org.yaml.snakeyaml", "fr.smp.ptr.foundation.shaded.snakeyaml")

        mergeServiceFiles()
    }

    val buildResourcePack by registering(Zip::class) {
        group = "build"
        description =
            "Zip src/main/resources/pack/ into build/resource-pack/PtrFoundation-pack.zip."
        from(project.file("src/main/resources/pack"))
        archiveFileName.set("PtrFoundation-pack.zip")
        destinationDirectory.set(project.layout.buildDirectory.dir("resource-pack"))
        // Reproducible build: stable timestamps so the SHA1 is stable across
        // CI runs that did not change the assets.
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        doLast {
            val zip = archiveFile.get().asFile
            val digest = MessageDigest.getInstance("SHA-1").digest(zip.readBytes())
            val sha1 = String.format("%040x", BigInteger(1, digest))
            destinationDirectory.get().asFile.resolve("PtrFoundation-pack.sha1")
                .writeText(sha1)
            logger.lifecycle("Resource pack built: ${zip.absolutePath} sha1=$sha1")
        }
    }

    build {
        dependsOn(shadowJar)
        dependsOn(buildResourcePack)
    }

    compileJava {
        options.encoding = "UTF-8"
        // Paper 26.1.2 + Folia 26.1.2 both publish their API with --release 25,
        // so the consumer release must match. Runtime Java is also 25.
        options.release.set(25)
    }

    compileTestJava {
        options.encoding = "UTF-8"
        options.release.set(25)
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

// spotless block intentionally removed — see plugin block above for why.
// Once google-java-format ships a JDK 25 build, re-introduce:
//   spotless {
//     java { target("src/**/*.java"); googleJavaFormat("<jdk25-compatible>"); ... }
//   }
//   tasks.named("check") { dependsOn("spotlessCheck") }
