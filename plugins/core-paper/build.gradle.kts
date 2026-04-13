plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "fr.smp"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

tasks {
    shadowJar {
        archiveBaseName.set("SMPCore-Paper")
        archiveClassifier.set("")
        archiveVersion.set("1.0.0")
    }

    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        filteringCharset = "UTF-8"
    }
}
