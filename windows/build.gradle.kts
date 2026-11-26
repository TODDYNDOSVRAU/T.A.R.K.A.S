import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  kotlin("jvm") version "2.2.10"
  id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
  id("org.jetbrains.compose") version "1.9.0"
  id("com.google.devtools.ksp") version "2.3.5"
}

kotlin { jvmToolchain(21) }

// The Android app stays the single source of truth: pure Kotlin files (no android.* / androidx.*)
// are compiled here straight from app/src, never copied. Add a file to these lists only after
// checking it has no Android import.
val appMain = "../app/src/main/java"
val appTest = "../app/src/test/java"
val sharedMain = listOf(
  "com/example/equalizador/funcoes/model/EqModel.kt",
  "com/example/equalizador/funcoes/model/AudioProfile.kt",
  "com/example/equalizador/funcoes/dsp/Biquad.kt",
  "com/example/equalizador/funcoes/dsp/EqActivity.kt",
  "com/example/equalizador/funcoes/dsp/EqDsp.kt",
  "com/example/equalizador/funcoes/dsp/EqTextFormat.kt",
  "com/example/equalizador/funcoes/dsp/AnalogHarmonicsDsp.kt",
  "com/example/equalizador/funcoes/dsp/ConvolverDsp.kt",
  "com/example/equalizador/funcoes/dsp/CrossfeedDsp.kt",
  "com/example/equalizador/funcoes/dsp/EqualLoudnessDsp.kt",
  "com/example/equalizador/funcoes/dsp/MultibandCompanderDsp.kt",
  "com/example/equalizador/funcoes/dsp/RtaAnalyzer.kt",
  "com/example/equalizador/funcoes/spatial/AcousticPhysics.kt",
  "com/example/equalizador/funcoes/spatial/Fft.kt",
  "com/example/equalizador/funcoes/spatial/HeadPose.kt",
  "com/example/equalizador/funcoes/spatial/MeasuredHrtf.kt",
  "com/example/equalizador/funcoes/spatial/SpatialEngine.kt",
  "com/example/equalizador/funcoes/spatial/SpatialRoom.kt",
  "com/example/equalizador/funcoes/spatial/SpatialStage.kt",
  "com/example/equalizador/funcoes/spatial/SphericalHeadHrtf.kt",
  "com/example/equalizador/funcoes/spatial/SurroundRoom.kt",
  "com/example/equalizador/funcoes/spatial/SurroundVirtualizer.kt",
  "com/example/equalizador/funcoes/spatial/Upmixer.kt",
  "com/example/equalizador/funcoes/spatial/UpmixVirtualizer.kt",
  // win-audio: capabilities, chain state, output profiles/routing, loudness rules (pure Kotlin)
  "com/example/equalizador/funcoes/model/AudioCapabilities.kt",
  "com/example/equalizador/funcoes/model/EqCompare.kt",
  "com/example/equalizador/funcoes/EqChainState.kt",
  "com/example/equalizador/funcoes/OutputProfiles.kt",
  "com/example/equalizador/funcoes/OutputRouting.kt",
  "com/example/equalizador/funcoes/loudness/LoudnessMeter.kt",
  "com/example/equalizador/funcoes/loudness/LoudnessNormalization.kt",
  "com/example/equalizador/funcoes/loudness/LoudnessUi.kt",
  // win-base: T.A.L. theme tokens, navigation routes, library models and Room schema, pure Kotlin
  "com/example/geral/ui/theme/Color.kt",
  "com/example/geral/ui/theme/GridLayout.kt",
  "com/example/geral/ui/theme/Tokens.kt",
  "com/example/geral/ui/theme/ThemeMode.kt",
  "com/example/geral/funcoes/viewmodel/NavRoutes.kt",
  "com/example/biblioteca/funcoes/model/Track.kt",
  "com/example/biblioteca/funcoes/model/Playlist.kt",
  "com/example/geral/funcoes/db/Entities.kt",
  "com/example/geral/funcoes/db/Daos.kt",
  // win-player: queue, order, crossfade, restore, lyrics, waveform rules (pure Kotlin)
  "com/example/biblioteca/funcoes/AlbumOrder.kt",
  "com/example/biblioteca/funcoes/PlaybackOrder.kt",
  "com/example/biblioteca/funcoes/model/TrackJson.kt",
  "com/example/player/funcoes/CoverNeighbors.kt",
  "com/example/player/funcoes/CrossfadePlanner.kt",
  "com/example/player/ui/CoverCarousel.kt",
  "com/example/player/funcoes/SessionQueue.kt",
  "com/example/player/funcoes/QueueSnapshot.kt",
  "com/example/player/funcoes/PlaybackProblem.kt",
  "com/example/player/funcoes/PlaybackRestore.kt",
  "com/example/player/funcoes/lyrics/Lyrics.kt",
  "com/example/player/funcoes/lyrics/LrcParser.kt",
  "com/example/player/funcoes/lyrics/Id3Lyrics.kt",
  "com/example/player/funcoes/waveform/PeakAccumulator.kt",
  // win-biblioteca: ordering, search, groups, auto lists, folder rules, ids (pure Kotlin)
  "com/example/biblioteca/funcoes/AutoLists.kt",
  "com/example/biblioteca/funcoes/FolderFilter.kt",
  "com/example/biblioteca/funcoes/JsonSalvage.kt",
  "com/example/biblioteca/funcoes/LibraryGroups.kt",
  "com/example/biblioteca/funcoes/LibrarySearch.kt",
  "com/example/biblioteca/funcoes/LibrarySort.kt",
  "com/example/biblioteca/funcoes/LibraryTree.kt",
  "com/example/biblioteca/funcoes/MediaDiff.kt",
  "com/example/biblioteca/funcoes/PickedIdRewrite.kt",
  "com/example/biblioteca/funcoes/TrackIds.kt",
  "com/example/biblioteca/funcoes/TrackEntities.kt",
  "com/example/biblioteca/funcoes/SerialWorker.kt",
  "com/example/geral/funcoes/TextMatch.kt",
  // win-geral: legal texts and licences, crash/event store (pure Kotlin)
  "com/example/geral/ui/legal/LegalTexts.kt",
  "com/example/geral/ui/legal/LegalTextsEn.kt",
  "com/example/geral/ui/legal/OpenSourceNotices.kt",
  "com/example/geral/funcoes/diagnostics/DiagnosticStore.kt",
)
val sharedTest = listOf(
  "com/example/equalizador/funcoes/EqDspTest.kt",
  "com/example/equalizador/funcoes/SpatialEngineTest.kt",
  "com/example/equalizador/funcoes/UpmixerTest.kt",
  "com/example/equalizador/funcoes/SurroundVirtualizerTest.kt",
  "com/example/equalizador/funcoes/AcousticPhysicsTest.kt",
  "com/example/equalizador/funcoes/AutoEqParserTest.kt",
  "com/example/equalizador/funcoes/EqChainStateTest.kt",
  "com/example/equalizador/funcoes/EqTextFormatTest.kt",
  "com/example/equalizador/funcoes/LoudnessNormalizationTest.kt",
  "com/example/player/funcoes/CoverNeighborsTest.kt",
  "com/example/player/funcoes/CrossfadePlannerTest.kt",
  "com/example/player/funcoes/PlaybackOrderTest.kt",
  "com/example/player/funcoes/QueueSnapshotTest.kt",
  "com/example/player/funcoes/LyricsTest.kt",
  "com/example/player/funcoes/PlaybackProblemTest.kt",
  "com/example/biblioteca/funcoes/AutoListsTest.kt",
  "com/example/biblioteca/funcoes/FolderFilterTest.kt",
  "com/example/biblioteca/funcoes/LibraryGroupsTest.kt",
  "com/example/biblioteca/funcoes/LibrarySearchTest.kt",
  "com/example/biblioteca/funcoes/LibraryTreeTest.kt",
  "com/example/biblioteca/funcoes/MediaDiffTest.kt",
  "com/example/biblioteca/funcoes/AlbumOrderTest.kt",
)

sourceSets {
  main {
    kotlin.srcDir(appMain)
    kotlin.include("com/example/windows/**", "com/example/ui/**", "com/example/data/**", "androidx/**")
    sharedMain.forEach { kotlin.include(it) }
    // The app's assets (HRTF tables, AutoEq package) are read from the classpath on the desktop.
    resources.srcDir("../app/src/main/assets")
  }
  test {
    kotlin.srcDir(appTest)
    kotlin.include("com/example/windows/**")
    sharedTest.forEach { kotlin.include(it) }
  }
}

dependencies {
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  implementation(compose.materialIconsExtended)
  implementation(compose.components.resources)
  implementation("androidx.room:room-runtime:2.8.4")
  implementation("androidx.sqlite:sqlite-bundled:2.6.2")
  ksp("androidx.room:room-compiler:2.8.4")
  implementation("org.json:json:20250517")
  // win-player: media keys (RegisterHotKey) from a message-loop thread
  implementation("net.java.dev.jna:jna-platform:5.6.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
  // Java Sound decoders: MP3 (JLayer/mp3spi, LGPL) and FLAC (jFLAC, Apache 2.0)
  runtimeOnly("com.googlecode.soundlibs:mp3spi:1.9.5.4")
  runtimeOnly("org.jflac:jflac-codec:1.5.2")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation(compose.desktop.uiTestJUnit4)
}

// Generated by scripts/gen-resources.js from app/src/main/res (never edit composeResources by hand).
compose.resources {
  packageOfResClass = "com.example.windows.res"
  publicResClass = true
  generateResClass = always
}

ksp { arg("room.schemaDirectory", "$projectDir/schemas") }

compose.desktop {
  application {
    mainClass = "com.example.windows.MainKt"
    nativeDistributions {
      // jlink keeps only listed JDK modules: HttpClient (Gemini, catalogue, update), TLS curves, JDBC for Room.
      modules("java.instrument", "java.net.http", "jdk.unsupported", "jdk.crypto.ec", "java.sql", "java.naming")
      targetFormats(TargetFormat.Msi, TargetFormat.Exe)
      packageName = "TARKAS"
      packageVersion = "2.1.6"
      vendor = "Totally Accurate Labs"
      description = "Totally Accurate Real-Time Kind of Audio System"
      windows {
        iconFile.set(project.file("packaging/tarkas.ico"))
        menuGroup = "T.A.L."
        shortcut = true
        dirChooser = true
        perUserInstall = true
        upgradeUuid = "6f1d3c2a-8b4e-4e0f-9a7d-2c5b1e8f4a10"
      }
    }
  }
}

tasks.test {
  System.getProperty("tarkas.sample")?.let { systemProperty("tarkas.sample", it) }
  testLogging { showStandardStreams = true }
}
