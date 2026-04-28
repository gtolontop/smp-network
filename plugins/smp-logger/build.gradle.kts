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
}

tasks {
    shadowJar {
        archiveBaseName.set("SMPLogger-Paper")
        archiveClassifier.set("")
        archiveVersion.set("1.0.0")
        // org.sqlite cannot be relocated — its JDBC loads native libs by package path.
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
