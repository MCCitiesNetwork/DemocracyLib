plugins {
    id("java-library")
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
}

dependencies {
    implementation(project(":bridge-contract"))
}
