plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "fr.smp"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("26.1.2.build.7-alpha")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
}

tasks {
    shadowJar {
        archiveBaseName.set("SMPCore-Paper")
        archiveClassifier.set("")
        archiveVersion.set("1.0.0")

        // Note: org.sqlite cannot be relocated — the JDBC driver loads
        // its native libraries by looking up resources at a path derived
        // from its own package name.
    }

    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        filteringCharset = "UTF-8"
    }
}
