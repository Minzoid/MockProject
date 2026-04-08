plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

repositories {
    mavenCentral()
    maven {
        name = "tcoded-releases"
        url = uri("https://repo.tcoded.com/releases")
    }
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    implementation("com.tcoded:FoliaLib:0.5.1")
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    processResources {
        val props = mapOf("version" to project.version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("") // normal plugin jar name
        relocate("com.tcoded.folialib", "com.minzoid.mockproject.lib.folialib")
    }

    runServer {
        minecraftVersion("1.21.4")
        jvmArgs("-Xms2G", "-Xmx2G")
    }
}