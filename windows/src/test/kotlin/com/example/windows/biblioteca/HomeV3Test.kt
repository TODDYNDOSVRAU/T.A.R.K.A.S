package com.example.windows.biblioteca

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.example.data.db.AppDatabase
import com.example.data.model.Track
import com.example.ui.theme.TarkasTheme
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.audio.DesktopPlayer
import com.example.windows.player.FakeOut
import com.example.windows.player.MemoryPrefs
import com.example.windows.player.PlayerController
import com.example.windows.shell.CornerInsets
import com.example.windows.shell.LocalCornerInsets
import com.example.windows.shell.Panels
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** Início v3, wave 0, Biblioteca side: greeting in the shell title, the lanes around the player's corner, the search focus ledger and the explicit mark. */
@OptIn(ExperimentalTestApi::class)
class HomeV3Test {
  // --- lanes -----------------------------------------------------------------------------------------------------

  private val corner = CornerInsets(432.dp, 324.dp, true)

  @Test fun aWideWindowCentresTheColumnAndLeavesTheCornerTheFreeMargin() {
    val l = HomeLayout.lanes(2100.dp, corner)
    assertEquals((2100.dp - HomeLayout.ColumnMax) / 2, l.start)
    assertEquals(l.start, l.end)
    assertTrue("the free margin must hold the lane", l.start >= corner.lane)
  }

  @Test fun aMediumWindowStartsTheWholeColumnAfterTheLane() {
    val l = HomeLayout.lanes(1366.dp, corner)
    assertEquals(432.dp, l.start)
    assertEquals(24.dp, l.end)
  }

  @Test fun aNarrowWindowOrAPageWithoutTheCardStepsAsideForNothing() {
    for (l in listOf(HomeLayout.lanes(900.dp, corner), HomeLayout.lanes(1366.dp, CornerInsets(432.dp, 120.dp, false)), HomeLayout.lanes(1366.dp, CornerInsets(0.dp, 0.dp, false)))) {
      assertEquals(24.dp, l.start); assertEquals(24.dp, l.end)
    }
  }

  @Test fun theBreakpointIsInclusive() {
    assertEquals(432.dp, HomeLayout.lanes(1024.dp, corner).start)
    assertEquals(24.dp, HomeLayout.lanes(1023.dp, corner).start)
  }

  private fun seeded(): Pair<TarkasState, BibliotecaState> {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val app = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
    val ctl = PlayerController(FakeOut(), MemoryPrefs(), scope)
    val lib = BibliotecaState(app, ctl, scope, dataDir = Files.createTempDirectory("tal-v3").toFile())
    val tracks = (1..12).map { i ->
      Track(id = "v3$i", title = "Faixa $i", artist = "Artista ${i % 3}", album = "Álbum ${i % 4}", durationMs = 180_000L, genre = "Rock", era = "2020s",
        isLocalFile = true, filePath = "C:/nao-existe/v3$i.mp3")
    }
    tracks.forEach { lib.store.addPicked(it) }
    Thread.sleep(400)
    return app to lib
  }

  private fun sectionLefts(width: Int, insets: CornerInsets): List<Float> {
    val (_, lib) = seeded()
    var lefts = emptyList<Float>()
    runDesktopComposeUiTest(width, 4000) {
      setContent {
        TarkasTheme(themeMode = "dark") {
          CompositionLocalProvider(LocalCornerInsets provides insets) {
            Box(Modifier.requiredSize(width.dp, 4000.dp)) { HomeScreen(lib, Modifier.fillMaxSize()) }
          }
        }
      }
      waitForIdle(); Thread.sleep(1200); waitForIdle()
      lefts = onAllNodesWithTag(TAG_SECTION).fetchSemanticsNodes().indices.map { onAllNodesWithTag(TAG_SECTION)[it].getBoundsInRoot().left.value }
    }
    return lefts
  }

  @Test fun onAMediumWindowEverySectionClearsTheCornerLane() {
    val lefts = sectionLefts(1366, corner)
    assertTrue("the sections must be drawn", lefts.isNotEmpty())
    lefts.forEach { assertTrue("section starts at $it, under 432", it >= 432f) }
  }

  @Test fun onANarrowWindowNothingStepsAside() {
    val lefts = sectionLefts(900, corner)
    assertTrue(lefts.isNotEmpty())
    lefts.forEach { assertEquals(24f, it, 0.5f) }
  }

  // --- greeting --------------------------------------------------------------------------------------------------

  @Test fun theHomeRegistersItsGreetingAsTheShellTitleAndItNamesTheUser() {
    val slots = mutableMapOf<TarkasNavTab, com.example.windows.shell.PanelContent>()
    BibliotecaHost.register(slots)
    val title = Panels.topTitle[TarkasNavTab.HOME]
    assertNotNull("the Início must register its greeting", title)
    val (app, _) = seeded()
    app.setNickname("Ana")
    runDesktopComposeUiTest(800, 200) {
      setContent { TarkasTheme(themeMode = "dark") { Box(Modifier.testTag("t")) { title!!.Show(app) } } }
      waitForIdle()
      assertTrue("the registered title composes", onAllNodesWithTag("t").fetchSemanticsNodes().isNotEmpty())
    }
    runDesktopComposeUiTest(800, 200) {
      setContent { TarkasTheme(themeMode = "dark") { HomeGreeting(app) } }
      waitForIdle()
      assertEquals(1, onAllNodes(hasText(", Ana", substring = true)).fetchSemanticsNodes().size)
    }
    app.setNickname("")
    runDesktopComposeUiTest(800, 200) {
      setContent { TarkasTheme(themeMode = "dark") { HomeGreeting(app) } }
      waitForIdle()
      // With no nickname the Windows account name takes its place, so the greeting still names someone or, with no account, is bare.
      val name = app.displayName.value
      assertEquals(if (name.isBlank()) 0 else 1, onAllNodes(hasText(", $name", substring = true)).fetchSemanticsNodes().size)
    }
    Panels.topTitle.remove(TarkasNavTab.HOME)
  }

  // --- search focus ledger ----------------------------------------------------------------------------------------

  @Test fun eachNewRequestFocusesOnceAndARevisitDoesNotRefocus() {
    SearchFocusLedger.reset(0)
    assertFalse("the starting value is not a request", SearchFocusLedger.take(0))
    assertTrue(SearchFocusLedger.take(1))
    assertFalse("the same count replayed by a new collector must not refocus", SearchFocusLedger.take(1))
    assertTrue(SearchFocusLedger.take(2))
    assertFalse(SearchFocusLedger.take(1))
    SearchFocusLedger.reset(0)
  }

  // --- explicit tag -----------------------------------------------------------------------------------------------

  @Test fun theAdvisoryValuesThatMeanExplicit() {
    assertTrue(TagReader.explicitTag("ITUNESADVISORY", "1"))
    assertTrue(TagReader.explicitTag("itunesadvisory", "4"))
    assertTrue(TagReader.explicitTag("EXPLICIT", "true"))
    assertTrue(TagReader.explicitTag("Parental Advisory", "Explicit"))
    assertFalse("2 is the clean version", TagReader.explicitTag("ITUNESADVISORY", "2"))
    assertFalse(TagReader.explicitTag("ITUNESADVISORY", "0"))
    assertFalse(TagReader.explicitTag("EXPLICIT", "0"))
    assertFalse(TagReader.explicitTag("COMMENT", "1"))
    assertFalse(TagReader.explicitTag("EXPLICIT", ""))
  }

  private fun frame(id: String, body: ByteArray): ByteArray {
    val n = body.size
    return id.toByteArray() + byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte(), 0, 0) + body
  }

  private fun latin(desc: String, value: String) = byteArrayOf(0) + desc.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + value.toByteArray(Charsets.ISO_8859_1)

  private fun utf16(desc: String, value: String): ByteArray {
    val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    return byteArrayOf(1) + bom + desc.toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0) + bom + value.toByteArray(Charsets.UTF_16LE)
  }

  private fun mp3(vararg frames: ByteArray): File {
    val all = frames.fold(ByteArray(0)) { a, f -> a + f }
    val n = all.size
    val header = "ID3".toByteArray() + byteArrayOf(3, 0, 0, (n shr 21 and 127).toByte(), (n shr 14 and 127).toByte(), (n shr 7 and 127).toByte(), (n and 127).toByte())
    return Files.createTempFile("tal-expl", ".mp3").toFile().also { it.writeBytes(header + all); it.deleteOnExit() }
  }

  private val title = frame("TIT2", byteArrayOf(0) + "Faixa".toByteArray())

  @Test fun id3ItunesAdvisoryInLatin1AndUtf16MarksExplicit() {
    assertTrue(TagReader.isExplicit(mp3(title, frame("TXXX", latin("ITUNESADVISORY", "1")))))
    assertTrue(TagReader.read(mp3(title, frame("TXXX", latin("ITUNESADVISORY", "1")))).explicit)
    assertTrue(TagReader.isExplicit(mp3(title, frame("TXXX", utf16("ITUNESADVISORY", "1")))))
    assertTrue(TagReader.isExplicit(mp3(frame("TXXX", latin("EXPLICIT", "1")), title)))
  }

  @Test fun id3WithoutTheTagOrWithTheCleanValueIsNotExplicit() {
    assertFalse(TagReader.isExplicit(mp3(title)))
    assertFalse(TagReader.isExplicit(mp3(title, frame("TXXX", latin("ITUNESADVISORY", "2")))))
    assertFalse(TagReader.isExplicit(mp3(title, frame("TXXX", latin("MOOD", "1")))))
    assertFalse(TagReader.read(mp3(title)).explicit)
    assertFalse("a file that does not exist is not explicit", TagReader.isExplicit(File("C:/nao-existe/x.mp3")))
  }

  private fun flac(vararg comments: String): File {
    val vendor = "tal".toByteArray()
    val body = ByteArrayOutputStream()
    fun le32(v: Int) { body.write(v and 255); body.write(v shr 8 and 255); body.write(v shr 16 and 255); body.write(v shr 24 and 255) }
    le32(vendor.size); body.write(vendor); le32(comments.size)
    for (c in comments) { val b = c.toByteArray(); le32(b.size); body.write(b) }
    val vc = body.toByteArray()
    val out = ByteArrayOutputStream()
    out.write("fLaC".toByteArray())
    out.write(0); out.write(0); out.write(0); out.write(34); out.write(ByteArray(34)) // STREAMINFO
    out.write(0x80 or 4); out.write(vc.size shr 16 and 255); out.write(vc.size shr 8 and 255); out.write(vc.size and 255); out.write(vc)
    return Files.createTempFile("tal-expl", ".flac").toFile().also { it.writeBytes(out.toByteArray()); it.deleteOnExit() }
  }

  @Test fun vorbisExplicitKeysMarkExplicitAndOthersDoNot() {
    assertTrue(TagReader.isExplicit(flac("TITLE=Faixa", "EXPLICIT=1")))
    assertTrue(TagReader.isExplicit(flac("itunesadvisory=1")))
    assertTrue(TagReader.read(flac("TITLE=Faixa", "ITUNESADVISORY=1")).explicit)
    assertFalse(TagReader.isExplicit(flac("TITLE=Faixa")))
    assertFalse(TagReader.isExplicit(flac("TITLE=Faixa", "ITUNESADVISORY=2")))
    assertFalse(TagReader.read(flac("TITLE=Faixa", "EXPLICIT=0")).explicit)
  }

  // --- the mark ---------------------------------------------------------------------------------------------------

  private fun track(file: File?) = Track(
    id = "e-${file?.name}", title = "Faixa", artist = "A", album = "B", durationMs = 1000L, genre = "", era = "",
    isLocalFile = file != null, filePath = file?.absolutePath,
  )

  private fun marks(t: Track, wait: Boolean): Int {
    var n = 0
    runDesktopComposeUiTest(300, 100) {
      setContent { TarkasTheme(themeMode = "dark") { Box(Modifier.requiredSize(300.dp, 100.dp)) { ExplicitMark(t) } } }
      waitForIdle()
      if (wait) waitUntil(timeoutMillis = 3000) { onAllNodesWithContentDescription("Conteúdo explícito").fetchSemanticsNodes().isNotEmpty() } else { Thread.sleep(400); waitForIdle() }
      n = onAllNodesWithContentDescription("Conteúdo explícito").fetchSemanticsNodes().size
    }
    return n
  }

  @Test fun theMarkAppearsOnlyForASongWhoseTagSaysExplicit() {
    ExplicitTags.clear()
    assertEquals("an explicit file gets the mark once its tag is read", 1, marks(track(mp3(title, frame("TXXX", latin("ITUNESADVISORY", "1")))), wait = true))
    assertEquals("a file without the tag gets none", 0, marks(track(mp3(title)), wait = false))
    assertEquals("an online song (no file) gets none", 0, marks(track(null), wait = false))
    assertEquals("a missing file gets none", 0, marks(track(File("C:/nao-existe/y.mp3")), wait = false))
    assertEquals("a song with no path is not read", false, ExplicitTags.cached(null))
    runBlocking { assertFalse(ExplicitTags.resolve(null)) }
  }
}
