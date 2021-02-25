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
  "datatable",
  "menu"
)

enableFeaturePreview("GRADLE_METADATA")