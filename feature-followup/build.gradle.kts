plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt.android)
}

android {
  namespace = "com.mchat.feature.followup"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(project(":core-common"))
  implementation(project(":core-design"))
  implementation(project(":core-security"))
  implementation(project(":domain-scoring"))
  implementation(project(":data-local"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.haze)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.hilt.work)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.navigation.compose)
  ksp(libs.hilt.compiler)
  ksp(libs.androidx.hilt.compiler)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
