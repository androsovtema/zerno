plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "app.zerno"
  compileSdk = 37
  defaultConfig {
    applicationId = "app.zerno"
    minSdk = 29
    targetSdk = 37
    versionCode = 2
    versionName = "0.2.0"
  }

  // Общий отладочный ключ в репозитории: сборки из CI и любой облачной сессии ставятся поверх друг друга.
  // Ключ не секретный и годится только для тестовых сборок, не для Google Play.
  signingConfigs {
    getByName("debug") {
      storeFile = file("debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        // Robolectric сам качает образ Android с Maven Central; облачные IP получают 429 — берём зеркало Google.
        it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
        it.maxHeapSize = "3g"
        it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        System.getenv("ZERNO_DNG_DIR")?.let { dir -> it.environment("ZERNO_DNG_DIR", dir) }
        // Скриншоты экранов — только по запросу: иначе каждый прогон тянет образ Android для Robolectric.
        val shots = System.getenv("ZERNO_SHOTS_DIR")
        if (shots != null) it.environment("ZERNO_SHOTS_DIR", shots) else it.exclude("**/ScreenshotTool*")
      }
    }
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(composeBom)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
