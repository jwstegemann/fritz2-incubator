pluginManagement {
  repositories {
    mavenCentral()
    jcenter()
    maven("https://dl.bintray.com/kotlin/kotlin-dev")
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
    gradlePluginPortal()
  }
}
rootProject.name = "fritz2-incubator"

include(
  "demo",
  "datatable"
)

enableFeaturePreview("GRADLE_METADATA")