plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "fr.smp"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
}

tasks {
    shadowJar {
        archiveBaseName.set("SMPCore-Velocity")
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
}
