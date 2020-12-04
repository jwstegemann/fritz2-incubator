plugins {
    kotlin("multiplatform") version "1.4.10" apply false
    id("org.jetbrains.dokka") version "1.4.10.2"
}

allprojects {
    //manage common setting and dependencies
    repositories {
        //FIXME: remove after release
        maven("https://oss.jfrog.org/artifactory/jfrog-dependencies")
        mavenCentral()
        jcenter()
        maven("https://dl.bintray.com/kotlin/kotlin-dev")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
    }
}

subprojects {
    group = "dev.fritz2"
    version = "0.9-SNAPSHOT"
}

tasks.dokkaHtmlMultiModule.configure {
    outputDirectory.set(rootDir.resolve("api"))
}
