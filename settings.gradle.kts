pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "M-CHAT-RF Clinician"

include(":app")
include(":domain-scoring")
include(":core-common")
include(":core-design")
include(":core-security")
include(":data-local")
include(":data-sync")
include(":feature-home")
include(":feature-patients")
include(":feature-screening")
include(":feature-followup")
include(":feature-stats")
include(":feature-reports")
include(":feature-settings")
