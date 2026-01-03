plugins {
    kotlin("jvm") version "2.3.0-RC3"
    id("java")
    id("com.gradleup.shadow") version "8.3.0"
}

group = "net.democracycraft"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://maven.enginehub.org/repo/") {
        name = "enginehub"
    }
}

dependencies {
    implementation(project(":bridge-contract"))
    annotationProcessor(project(":bridge-processor"))
    testAnnotationProcessor(project(":bridge-processor"))

    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.14")

    implementation("org.spongepowered:configurate-yaml:4.2.0")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    shadowJar {
        archiveClassifier.set("all")
        relocate("org.spongepowered.configurate", "net.democracycraft.democracyLib.configurate")
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        val protocolVersion = (findProperty("protocolVersion") as String?)?.trim() ?: "1"
        val props = mapOf(
            "version" to project.version,
            "protocolVersion" to protocolVersion
        )
        inputs.properties(props)
        filteringCharset = "UTF-8"

        filesMatching("democracylib.properties") {
            expand(props)
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
