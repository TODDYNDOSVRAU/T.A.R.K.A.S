package com.example.ui.legal

/** One open-source project the app ships: who holds it, its licence and the Maven groups it covers. */
class LibraryNotice(val title: String, val holder: String, val license: String, val groups: List<String>)

/**
 * The libraries in the release build, by Maven group. The licences screen lists these in both
 * languages, and `LicenseCoverageTest` reads the real dependency list from the build and fails when a
 * dependency has no entry here, or when an entry no longer matches any dependency.
 */
object OpenSourceNotices {
  private const val APACHE = "Apache License 2.0"

  val libraries = listOf(
    LibraryNotice("Jetpack Compose, AndroidX, Material 3", "Google", APACHE, listOf("androidx")),
    LibraryNotice("AndroidX Media3 (ExoPlayer)", "Google", APACHE, listOf("androidx.media3")),
    LibraryNotice("Kotlin, kotlinx.coroutines, kotlinx.serialization", "JetBrains", APACHE, listOf("org.jetbrains.kotlin", "org.jetbrains.kotlinx")),
    LibraryNotice(
      "Compose Multiplatform, JetBrains Annotations", "JetBrains", APACHE,
      listOf("org.jetbrains", "org.jetbrains.compose", "org.jetbrains.androidx")
    ),
    LibraryNotice("Coil", "Coil Contributors", APACHE, listOf("io.coil-kt")),
    LibraryNotice("Accompanist", "Google", APACHE, listOf("com.google.accompanist")),
    LibraryNotice("Haze", "Chris Banes", APACHE, listOf("dev.chrisbanes.haze")),
    LibraryNotice("Backdrop (AndroidLiquidGlass)", "Kyant", APACHE, listOf("io.github.kyant0")),
    LibraryNotice("OkHttp", "Square", APACHE, listOf("com.squareup.okhttp3")),
    LibraryNotice("Okio", "Square", APACHE, listOf("com.squareup.okio")),
    LibraryNotice("Guava", "The Guava Authors", APACHE, listOf("com.google.guava")),
    LibraryNotice("JSpecify", "The JSpecify Authors", APACHE, listOf("org.jspecify"))
  )

  /** The rows of the licences screen for the libraries; the same in every language. */
  val librarySections: List<Pair<String, String>> = libraries.map { it.title to "${it.holder} · ${it.license}" }

  /** The entry that covers a Maven group: the one with the longest group that is it or a parent of it. */
  fun covering(group: String): LibraryNotice? =
    libraries
      .flatMap { notice -> notice.groups.map { it to notice } }
      .filter { (prefix, _) -> group == prefix || group.startsWith("$prefix.") }
      .maxByOrNull { (prefix, _) -> prefix.length }
      ?.second

  /** AutoEq's licence in full, as its repository publishes it. The MIT licence asks for this text to go along. */
  const val AUTOEQ_MIT =
    "MIT License\n\n" +
      "Copyright (c) 2018-2022 Jaakko Pasanen\n\n" +
      "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated " +
      "documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation " +
      "the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to " +
      "permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\n" +
      "The above copyright notice and this permission notice shall be included in all copies or substantial portions of " +
      "the Software.\n\n" +
      "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO " +
      "THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE " +
      "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, " +
      "TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE " +
      "SOFTWARE."
}
