plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt.android)
}

android {
  namespace = "com.mchat.core.security"
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

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.bouncycastle.prov)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
