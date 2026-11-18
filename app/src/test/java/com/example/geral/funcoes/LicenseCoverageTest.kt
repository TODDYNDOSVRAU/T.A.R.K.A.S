package com.example

import com.example.ui.legal.LegalTexts
import com.example.ui.legal.OpenSourceNotices
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The licences screen against what the release build really ships. The build writes the dependency
 * list (task writeDependencyModules) and hands its path in `tarkas.dependencyModules`.
 */
class LicenseCoverageTest {
  private val groups: List<String> by lazy {
    val path = System.getProperty("tarkas.dependencyModules")
    checkNotNull(path) { "tarkas.dependencyModules is not set: run the tests through Gradle" }
    File(path).readLines().filter { it.isNotBlank() }.map { it.substringBefore(':') }.distinct()
  }

  @Test
  fun `the build lists its dependencies`() {
    assertTrue("only ${groups.size} groups", groups.size >= 10)
    assertTrue(groups.contains("com.squareup.okhttp3"))
  }

  @Test
  fun `every shipped dependency has an entry on the licences screen`() {
    val missing = groups.filter { OpenSourceNotices.covering(it) == null }
    assertEquals("add these groups to OpenSourceNotices", emptyList<String>(), missing)
  }

  @Test
  fun `every entry still matches a shipped dependency`() {
    val used = groups.mapNotNull { OpenSourceNotices.covering(it) }.toSet()
    val stale = OpenSourceNotices.libraries.filterNot { it in used }.map { it.title }
    assertEquals("these entries match no dependency any more", emptyList<String>(), stale)
  }

  @Test
  fun `the longest group wins`() {
    assertEquals("AndroidX Media3 (ExoPlayer)", OpenSourceNotices.covering("androidx.media3")?.title)
    assertEquals("Jetpack Compose, AndroidX, Material 3", OpenSourceNotices.covering("androidx.compose.ui")?.title)
    assertEquals("Kotlin, kotlinx.coroutines, kotlinx.serialization", OpenSourceNotices.covering("org.jetbrains.kotlinx")?.title)
    assertEquals("Compose Multiplatform, JetBrains Annotations", OpenSourceNotices.covering("org.jetbrains")?.title)
    assertEquals(null, OpenSourceNotices.covering("androidxyz"))
  }

  @Test
  fun `both languages show every library and the full text of the MIT licence`() {
    listOf(LegalTexts.forLanguage("pt"), LegalTexts.forLanguage("en")).forEach { texts ->
      val titles = texts.licenses.sections.map { it.first }
      OpenSourceNotices.libraries.forEach { assertTrue(it.title, it.title in titles) }
      assertEquals(titles.size, titles.distinct().size)
      val bodies = texts.licenses.sections.joinToString("\n") { it.second }
      assertTrue(bodies.contains("Copyright (c) 2018-2022 Jaakko Pasanen"))
      assertTrue(bodies.contains("The above copyright notice and this permission notice shall be included"))
    }
  }
}
