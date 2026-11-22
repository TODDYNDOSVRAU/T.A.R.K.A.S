plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.secrets)
  alias(libs.plugins.ksp)
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.tarkas.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 23
    versionName = "2.4"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // The release key comes from the environment (see release-signing.local); the debug key is in the
  // repository so every machine produces the same debug build.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
    // The release build signed with the debug key, to measure frames on a device. A debuggable build is not
    // optimised by Android and runs Compose several times slower, so its frame times say little about the app.
    create("perf") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debugConfig")
      matchingFallbacks += "release"
      // Real names in the profile; src/perf/AndroidManifest.xml makes the build profileable from the shell.
      proguardFiles("perf-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  // Errors that existed before the CI are listed in the baseline; a new one fails the build.
  lint { baseline = file("lint-baseline.xml") }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Keys (Gemini, Jamendo) are read from .env, which stays out of git; .env.example lists the names.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

/** Writes the Maven group and name of everything the release build ships, one per line. */
abstract class WriteDependencyModules : DefaultTask() {
  @get:Input abstract val modules: ListProperty<String>
  @get:OutputFile abstract val output: RegularFileProperty

  @TaskAction
  fun write() {
    output.get().asFile.writeText(modules.get().joinToString("\n"))
  }
}

// LicenseCoverageTest reads this list and fails when a shipped dependency has no entry on the
// licences screen (ui/legal/OpenSourceNotices.kt), so the screen cannot fall behind the build.
val dependencyModulesFile = layout.buildDirectory.file("generated/licenses/dependency-modules.txt")
val writeDependencyModules = tasks.register<WriteDependencyModules>("writeDependencyModules") {
  modules.set(
    providers.provider { configurations.getByName("releaseRuntimeClasspath") }.flatMap { classpath ->
      classpath.incoming.resolutionResult.rootComponent.map { root ->
        val seen = LinkedHashSet<ResolvedComponentResult>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
          val component = queue.removeFirst()
          if (!seen.add(component)) continue
          component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue.add(it.selected) }
        }
        seen.mapNotNull { (it.id as? ModuleComponentIdentifier)?.let { id -> "${id.group}:${id.module}" } }.distinct().sorted()
      }
    }
  )
  output.set(dependencyModulesFile)
}
tasks.withType<Test>().configureEach {
  dependsOn(writeDependencyModules)
  inputs.file(dependencyModulesFile).withPropertyName("dependencyModules").withPathSensitivity(PathSensitivity.NONE)
  systemProperty("tarkas.dependencyModules", dependencyModulesFile.get().asFile.absolutePath)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.haze)
  // Real liquid glass (refraction, edge lensing, chromatic aberration) on Android 13+
  implementation(libs.backdrop)
  // Playback with our own audio processing (parametric EQ with adjustable Q, tone, limiter, width)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  // Cover art and thumbnails
  implementation(libs.coil.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
