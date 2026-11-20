plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt.android)
}

android {
  namespace = "com.mchat.data.sync"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(project(":core-common"))
  implementation(project(":core-security"))
  implementation(project(":domain-scoring"))
  implementation(project(":data-local"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.play.services.auth)
  implementation(libs.retrofit)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.work)

  ksp(libs.hilt.compiler)
  ksp(libs.androidx.hilt.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
