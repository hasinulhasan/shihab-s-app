plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt.android)
}

android {
  namespace = "com.mchat.data.local"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.incremental", "true")
  arg("room.generateKotlin", "true")
}

dependencies {
  implementation(project(":core-common"))
  implementation(project(":core-security"))
  implementation(project(":domain-scoring"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.sqlcipher.android)
  implementation(libs.androidx.sqlite.ktx)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.hilt.android)

  ksp(libs.androidx.room.compiler)
  ksp(libs.hilt.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.robolectric)
}
