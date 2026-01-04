plugins {
    // Root aggregator. Module builds are configured in their own build.gradle.kts files.
    id("base")
}

tasks {
    named("build") {
        dependsOn(":democracy-lib:build")
    }
}

