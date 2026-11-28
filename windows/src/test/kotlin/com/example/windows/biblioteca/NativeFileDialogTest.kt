package com.example.windows.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.db.AppDatabase
import com.example.ui.theme.TarkasTheme
import com.example.windows.audio.DesktopPlayer
import com.example.windows.player.FakeOut
import com.example.windows.player.MemoryPrefs
import com.example.windows.player.PlayerController
import com.example.windows.state.TarkasState
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The native explorer behind the music picker: the rules (filter, HRESULT, what each outcome does), the wiring from the
 * Glass Folder to the dialog, and, when TAL_EVIDENCE names a folder, the real Windows dialog captured in folder and file
 * mode and the empty library in four sizes.
 */
@OptIn(ExperimentalTestApi::class)
class NativeFileDialogTest {
  // ---- the rules ----

  @Test
  fun `the audio filter lists every extension the scanner reads, then all files`() {
    val specs = audioFilterSpecs(TagReader.EXTENSIONS, "Áudio", "Todos os arquivos")
    assertEquals(2, specs.size)
    assertEquals("Áudio (*.mp3;*.flac;*.wav)" to "*.mp3;*.flac;*.wav", specs[0])
    assertEquals("Todos os arquivos (*.*)" to "*.*", specs[1])
    for (ext in TagReader.EXTENSIONS) assertTrue("filter has $ext", specs[0].second.split(';').contains("*.$ext"))
  }

  @Test
  fun `the filter lower-cases and follows any extension set it is given`() {
    val specs = audioFilterSpecs(listOf("OGG", "m4a"), "Áudio", "Todos os arquivos")
    assertEquals("Áudio (*.ogg;*.m4a)" to "*.ogg;*.m4a", specs[0])
    assertEquals("Todos os arquivos (*.*)" to "*.*", specs[1])
  }

  @Test
  fun `S_OK is a pick, the cancel code is silent, every other code is a failure`() {
    assertEquals(PickOutcome.Picked(listOf("C:\\Music")), outcomeOf(0) { listOf("C:\\Music") })
    assertSame(PickOutcome.Cancelled, outcomeOf(HR_CANCELLED) { error("must not read the result of a cancel") })
    assertEquals(0x800704C7.toInt(), HR_CANCELLED)
    assertEquals(PickOutcome.Failed(0x80070005.toInt()), outcomeOf(0x80070005.toInt()) { emptyList() })
    assertEquals(PickOutcome.Failed(1), outcomeOf(1) { emptyList() })
  }

  @Test
  fun `a code is written the way Windows documents it`() {
    assertEquals("0x800704C7", hresultText(HR_CANCELLED))
    assertEquals("0x80004005", hresultText(HR_FAIL))
    assertEquals("0x00000001", hresultText(1))
  }

  @Test
  fun `a cancel does nothing and says nothing`() {
    for (mode in PickMode.values()) {
      assertSame(PickEffect.Nothing, interpret(mode, PickOutcome.Cancelled) { error("no fallback on cancel") })
    }
  }

  @Test
  fun `a pick becomes files, an empty pick becomes nothing`() {
    val effect = interpret(PickMode.Folders, PickOutcome.Picked(listOf("C:\\A", "D:\\B"))) { error("no fallback") }
    assertEquals(PickEffect.Use(listOf(File("C:\\A"), File("D:\\B"))), effect)
    assertSame(PickEffect.Nothing, interpret(PickMode.Files, PickOutcome.Picked(emptyList())) { error("no fallback") })
  }

  @Test
  fun `a failed folder dialog tells the person what happened, why and what to do, and never falls back`() {
    var fallbackCalls = 0
    val effect = interpret(PickMode.Folders, PickOutcome.Failed(0x80070005.toInt())) { fallbackCalls++; emptyList() }
    assertEquals(0, fallbackCalls)
    val msg = (effect as PickEffect.Error).message
    assertTrue("names the code: $msg", msg.contains("0x80070005"))
    assertTrue("says what to do: $msg", msg.contains("Try again") || msg.contains("Tente de novo"))
    assertTrue("no exclamation, no emoji: $msg", !msg.contains('!'))
  }

  @Test
  fun `a failed file dialog falls back to the system file dialog`() {
    val got = interpret(PickMode.Files, PickOutcome.Failed(HR_FAIL)) { listOf(File("C:\\x.mp3")) }
    assertEquals(PickEffect.Use(listOf(File("C:\\x.mp3"))), got)
    assertSame(PickEffect.Nothing, interpret(PickMode.Files, PickOutcome.Failed(HR_FAIL)) { emptyList() })
  }

  @Test
  fun `folders ask for folders on the file system with several allowed, files for existing files`() {
    val f = WindowsFileDialog.optionsFor(PickMode.Folders)
    assertEquals(0x20 or 0x40 or 0x200 or 0x800, f)
    val a = WindowsFileDialog.optionsFor(PickMode.Files)
    assertEquals(0x200 or 0x1000 or 0x40, a)
    assertEquals("only the folder dialog picks folders", 0, a and WindowsFileDialog.FOS_PICKFOLDERS)
  }

  @Test
  fun `the folder says how many folders are watched and the two sheets are folders then audio`() {
    assertTrue(watchedFoldersMeta(0).isNotEmpty())
    assertTrue(watchedFoldersMeta(1).contains("1"))
    assertTrue(watchedFoldersMeta(3).contains("3"))
    val sheets = pickerSheets()
    assertEquals(2, sheets.size)
    assertTrue(sheets[SHEET_FILES].meta.contains("MP3") && sheets[SHEET_FILES].meta.contains("FLAC") && sheets[SHEET_FILES].meta.contains("WAV"))
    assertEquals(0, SHEET_FOLDERS)
    assertEquals(1, SHEET_FILES)
  }

  // ---- the picker with a stand-in dialog ----

  private class Fake(val outcome: PickOutcome, val gate: CountDownLatch? = null) : PickerBackend {
    val requests = CopyOnWriteArrayList<PickRequest>()
    override fun pick(request: PickRequest): PickOutcome { requests += request; gate?.await(10, TimeUnit.SECONDS); return outcome }
  }

  private inline fun <T> withBackend(fake: PickerBackend, block: () -> T): T {
    NativePicker.backendOverride = fake
    try { return block() } finally { NativePicker.backendOverride = null }
  }

  private fun settle(cond: () -> Boolean) {
    val end = System.currentTimeMillis() + 8000
    while (!cond() && System.currentTimeMillis() < end) Thread.sleep(20)
    assertTrue("condition met within 8 s", cond())
  }

  // The picker keeps one global "a dialog is up" flag that is cleared on the UI thread a moment after the dialog thread ends:
  // every test starts and ends with it down, so a late clear from the previous test cannot swallow this test's first click.
  @Before fun startIdle() = settle { !isBusy() }
  @After fun endIdle() = settle { !isBusy() }

  /** Lets the UI thread's queue run what was posted before this call. */
  private fun drainUiThread() = SwingUtilities.invokeAndWait { }

  @Test
  fun `a pick reaches the callback on the UI thread and a cancel reaches nothing`() {
    val got = CopyOnWriteArrayList<List<File>>()
    val errors = CopyOnWriteArrayList<String>()
    val onUi = AtomicReference<Boolean>()
    withBackend(Fake(PickOutcome.Picked(listOf("C:\\Music")))) {
      NativePicker.pick(PickMode.Folders, { onUi.set(SwingUtilities.isEventDispatchThread()); got += it }, { errors += it })
      settle { got.isNotEmpty() }
    }
    assertEquals(listOf(File("C:\\Music")), got.single())
    assertTrue("callback ran on the UI thread", onUi.get())
    assertTrue(errors.isEmpty())

    val none = CopyOnWriteArrayList<List<File>>()
    val fake = Fake(PickOutcome.Cancelled)
    withBackend(fake) {
      NativePicker.pick(PickMode.Files, { none += it }, { errors += it })
      settle { fake.requests.isNotEmpty() }
      drainUiThread(); drainUiThread()
    }
    assertTrue("a cancel adds nothing", none.isEmpty())
    assertTrue("a cancel shows no error", errors.isEmpty())
  }

  @Test
  fun `a failed folder dialog reports an error and adds nothing`() {
    val got = CopyOnWriteArrayList<List<File>>()
    val errors = CopyOnWriteArrayList<String>()
    withBackend(Fake(PickOutcome.Failed(0x80070005.toInt()))) {
      NativePicker.pick(PickMode.Folders, { got += it }, { errors += it })
      settle { errors.isNotEmpty() }
    }
    assertTrue(got.isEmpty())
    assertTrue(errors.single().contains("0x80070005"))
  }

  @Test
  fun `a second click while a dialog is up does not stack another dialog`() {
    val gate = CountDownLatch(1)
    val fake = Fake(PickOutcome.Cancelled, gate)
    withBackend(fake) {
      NativePicker.pick(PickMode.Folders, {}, {})
      settle { fake.requests.size == 1 }
      NativePicker.pick(PickMode.Folders, {}, {})
      NativePicker.pick(PickMode.Files, {}, {})
      drainUiThread()
      assertEquals("only the first dialog was asked for", 1, fake.requests.size)
      gate.countDown()
      settle { !isBusy() }
      NativePicker.pick(PickMode.Files, {}, {})
      settle { fake.requests.size == 2 }
      drainUiThread()
    }
  }

  private fun isBusy(): Boolean {
    val f = NativePicker::class.java.getDeclaredField("busy").apply { isAccessible = true }
    return f.getBoolean(NativePicker)
  }

  @Test
  fun `the requests carry their own title and only the file request carries the filter`() {
    val folders = Fake(PickOutcome.Cancelled)
    val files = Fake(PickOutcome.Cancelled)
    withBackend(folders) { NativePicker.pick(PickMode.Folders, {}, {}); settle { folders.requests.isNotEmpty() }; settle { !isBusy() } }
    withBackend(files) { NativePicker.pick(PickMode.Files, {}, {}); settle { files.requests.isNotEmpty() }; settle { !isBusy() } }
    val f = folders.requests.single()
    val a = files.requests.single()
    assertEquals(PickMode.Folders, f.mode)
    assertTrue(f.filters.isEmpty())
    assertEquals(PickMode.Files, a.mode)
    assertEquals(2, a.filters.size)
    assertTrue(a.filters[0].second.contains("*.mp3"))
    assertTrue("titles differ", f.title != a.title)
  }

  // ---- the Glass Folder wired to the picker ----

  private fun emptyLib(): BibliotecaState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val app = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
    app.settings.put(BibliotecaState.SCOPE, BibliotecaState.KEY_ROOTS, "")
    val ctl = PlayerController(FakeOut(), MemoryPrefs(), scope)
    return BibliotecaState(app, ctl, scope, dataDir = Files.createTempDirectory("tal-nfd").toFile()).also { Thread.sleep(600) }
  }

  private fun ComposeUiTest.showEmptyLibrary(lib: BibliotecaState, w: Int, h: Int, mode: String = "dark", fontScale: Float = 1f) {
    setContent {
      val d = LocalDensity.current
      androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
        TarkasTheme(themeMode = mode) {
          Box(Modifier.requiredSize(w.dp, h.dp).background(MaterialTheme.colorScheme.background).testTag("root")) { LibraryScreen(lib, Modifier) }
        }
      }
    }
    waitForIdle(); Thread.sleep(1500); waitForIdle()
    mainClock.advanceTimeBy(2200); waitForIdle()
  }

  private fun ComposeUiTest.openFolder() {
    onNodeWithTag("glass-folder-hit").performClick()
    mainClock.advanceTimeBy(1600); waitForIdle()
  }

  /** A click inside a sheet's top-left, which the glass does not cover. */
  private fun ComposeUiTest.clickSheet(i: Int) {
    onNodeWithTag("glass-folder-sheet-$i").performMouseInput { click(Offset(40f, 20f)) }
  }

  @Test
  fun `the folder sheet opens the folder dialog and the files sheet opens the file dialog`() {
    val lib = emptyLib()
    val fake = Fake(PickOutcome.Cancelled)
    withBackend(fake) {
      runDesktopComposeUiTest(1024, 640) {
        showEmptyLibrary(lib, 1024, 640)
        openFolder()
        clickSheet(SHEET_FOLDERS)
        settle { fake.requests.size == 1 }
        assertEquals(PickMode.Folders, fake.requests[0].mode)
        settle { !isBusy() }
        waitForIdle()
        openFolder()
        clickSheet(SHEET_FILES)
        settle { fake.requests.size == 2 }
        assertEquals(PickMode.Files, fake.requests[1].mode)
        assertTrue("the file dialog got the audio filter", fake.requests[1].filters.isNotEmpty())
      }
    }
  }

  @Test
  fun `the plus key of the folder opens the folder dialog directly`() {
    val lib = emptyLib()
    val fake = Fake(PickOutcome.Cancelled)
    withBackend(fake) {
      runDesktopComposeUiTest(1024, 640) {
        showEmptyLibrary(lib, 1024, 640)
        onNodeWithTag("glass-folder-key").performClick()
        settle { fake.requests.size == 1 }
        assertEquals(PickMode.Folders, fake.requests[0].mode)
      }
    }
  }

  @Test
  fun `a picked folder is added to the watched folders`() {
    val lib = emptyLib()
    val dir = Files.createTempDirectory("tal-nfd-pick").toFile()
    val fake = Fake(PickOutcome.Picked(listOf(dir.absolutePath)))
    withBackend(fake) {
      runDesktopComposeUiTest(1024, 640) {
        showEmptyLibrary(lib, 1024, 640)
        onNodeWithTag("glass-folder-key").performClick()
        settle { lib.roots.value.any { it.absoluteFile == dir.absoluteFile } }
      }
    }
  }

  @Test
  fun `a failed folder dialog shows the error on the page`() {
    val lib = emptyLib()
    val fake = Fake(PickOutcome.Failed(0x80070005.toInt()))
    withBackend(fake) {
      runDesktopComposeUiTest(1024, 640) {
        showEmptyLibrary(lib, 1024, 640)
        onNodeWithTag("glass-folder-key").performClick()
        settle { fake.requests.isNotEmpty() }
        val end = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < end && onAllShown(TAG_PICKER_ERROR) == 0) { waitForIdle(); Thread.sleep(30) }
        assertEquals(1, onAllShown(TAG_PICKER_ERROR))
        assertTrue("nothing was added", lib.roots.value.isEmpty())
      }
    }
  }

  private fun ComposeUiTest.onAllShown(tag: String): Int =
    onAllNodesWithTagCount(tag)

  private fun ComposeUiTest.onAllNodesWithTagCount(tag: String): Int =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

  // ---- the pictures (opt-in) ----

  private val dir: File? = System.getenv("TAL_EVIDENCE")?.takeIf { it.isNotBlank() }?.let(::File)

  private fun shootLibrary(name: String, w: Int, h: Int, mode: String, fontScale: Float = 1f, open: Boolean = false) {
    val out = dir ?: return
    val lib = emptyLib()
    // A stand-in dialog: a click that lands on the folder's plus key must never open the real Windows explorer in a render run.
    withBackend(Fake(PickOutcome.Cancelled)) {
      runDesktopComposeUiTest(w, h) {
        showEmptyLibrary(lib, w, h, mode, fontScale)
        if (open) openFolder()
        val img = onNodeWithTag("root").captureToImage().toAwtImage()
        assertTrue(img.width > 0 && img.height > 0)
        ImageIO.write(img, "png", File(out.also(File::mkdirs), "$name-${w}x$h-$mode.png"))
      }
    }
  }

  @Test
  fun emptyLibraryWithTheGlassFolder() {
    if (dir == null) return
    for (mode in listOf("light", "dark")) {
      shootLibrary("empty-library", 1024, 640, mode)
      shootLibrary("empty-library-open", 1024, 640, mode, open = true)
    }
    shootLibrary("empty-library-narrow", 640, 560, "dark")
    shootLibrary("empty-library-narrow-open", 640, 560, "dark", open = true)
    shootLibrary("empty-library-font200", 1024, 640, "light", fontScale = 2f)
    // A full-HD window: the folder takes the 384 step and the sheets keep their full names and the file types.
    shootLibrary("empty-library-large", 1600, 900, "dark")
    shootLibrary("empty-library-large-open", 1600, 900, "dark", open = true)
    shootLibrary("empty-library-large-open", 1600, 900, "light", open = true)
    shootLibrary("empty-library-large-font200", 1600, 900, "light", fontScale = 2f, open = true)
    shootLibrary("empty-library-short", 1024, 420, "dark")
    // Portrait: the app never goes below 960 dp wide (WindowMemory.MinSize), so portrait is a tall window of that width.
    for (mode in listOf("light", "dark")) {
      shootLibrary("empty-library-portrait", 960, 1280, mode)
      shootLibrary("empty-library-portrait-open", 960, 1280, mode, open = true)
    }
  }

  /** Where, in dp of the page, a person points: the folder, then the top-left of each sheet once it is open. Same layout in a real window. */
  private fun pointerTargets(w: Int, h: Int, mode: String): List<Offset> {
    val out = mutableListOf<Offset>()
    withBackend(Fake(PickOutcome.Cancelled)) {
      runDesktopComposeUiTest(w, h) {
        showEmptyLibrary(emptyLib(), w, h, mode)
        out += onNodeWithTag("glass-folder-hit").fetchSemanticsNode().boundsInRoot.center
        openFolder()
        for (i in listOf(SHEET_FOLDERS, SHEET_FILES)) {
          out += onNodeWithTag("glass-folder-sheet-$i").fetchSemanticsNode().layoutInfo.coordinates.localToRoot(Offset(40f, 20f))
        }
      }
    }
    return out
  }

  /**
   * The whole flow of a person, in a real window with the real Windows backend: the pointer goes to the folder and clicks
   * it, goes to a sheet and clicks it, and the explorer that opens is found by the title of its request, photographed with
   * the app beside it and closed. Both sheets, both themes. Opt-in (TAL_EVIDENCE) because it moves the real pointer.
   */
  @Test
  fun realClickFlowInBothModes() {
    val out = dir ?: return
    assumeTrue(System.getProperty("os.name").startsWith("Windows"))
    assumeFalse(GraphicsEnvironment.isHeadless())
    val shots = File(out, "native").also(File::mkdirs)
    val user32 = User32.INSTANCE
    val w = 1024
    val h = 640
    for (mode in listOf("light", "dark")) {
      val targets = pointerTargets(w, h, mode)
      for ((n, sheet) in listOf(SHEET_FOLDERS, SHEET_FILES).withIndex()) {
        val pickMode = if (sheet == SHEET_FOLDERS) PickMode.Folders else PickMode.Files
        val lib = emptyLib()
        val win = AtomicReference<java.awt.Window>()
        val up = CountDownLatch(1)
        val app = Thread {
          androidx.compose.ui.window.application(exitProcessOnExit = false) {
            androidx.compose.ui.window.Window(
              onCloseRequest = ::exitApplication,
              state = androidx.compose.ui.window.WindowState(
                position = androidx.compose.ui.window.WindowPosition(60.dp, 60.dp), size = androidx.compose.ui.unit.DpSize(w.dp, h.dp),
              ),
              title = "T.A.L. evidence app", undecorated = true, alwaysOnTop = true, resizable = false,
            ) {
              win.set(window); up.countDown()
              TarkasTheme(themeMode = mode) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { LibraryScreen(lib, Modifier) }
              }
            }
          }
        }.apply { isDaemon = true; start() }
        assertTrue("the app window opened ($mode, sheet $n)", up.await(30, TimeUnit.SECONDS))
        Thread.sleep(3500)
        val bot = Robot().apply { autoDelay = 20 }
        bot.keyPress(java.awt.event.KeyEvent.VK_ALT); bot.keyRelease(java.awt.event.KeyEvent.VK_ALT)
        SwingUtilities.invokeAndWait { win.get().toFront(); win.get().requestFocus() }
        Thread.sleep(500)
        val origin = win.get().locationOnScreen
        fun at(p: Offset) = (origin.x + p.x).toInt() to (origin.y + p.y).toInt()
        fun click(p: Offset) {
          val (x, y) = at(p)
          // A short glide so the hover state is reached the way a pointer reaches it.
          for (s in 1..8) { bot.mouseMove(x - (8 - s) * 6, y - (8 - s) * 3); Thread.sleep(15) }
          Thread.sleep(250)
          bot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK); Thread.sleep(60); bot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
        }
        click(targets[0])
        Thread.sleep(2200)
        val request = NativePicker.request(pickMode)
        println("REALFLOW $mode sheet=$sheet origin=${origin.x},${origin.y} hit=${targets[0]} sheetPt=${targets[1 + n]} title=${request.title}")
        Robot().createScreenCapture(Rectangle(java.awt.Toolkit.getDefaultToolkit().screenSize)).let {
          ImageIO.write(it, "png", File(shots, "real-flow-$mode-${pickMode.name.lowercase()}-1-open.png"))
        }
        click(targets[1 + n])
        var hwnd: WinDef.HWND? = null
        val end = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < end) {
          val hh = user32.FindWindow("#32770", request.title)
          if (hh != null && user32.IsWindowVisible(hh)) { hwnd = hh; break }
          Thread.sleep(50)
        }
        assertTrue("the explorer opened with the title \"${request.title}\" after the click on the sheet ($mode)", hwnd != null)
        Robot().apply { keyPress(java.awt.event.KeyEvent.VK_ALT); keyRelease(java.awt.event.KeyEvent.VK_ALT) }
        user32.ShowWindow(hwnd, 9)
        user32.SetWindowPos(hwnd, WinDef.HWND(com.sun.jna.Pointer.createConstant(-1)), 0, 0, 0, 0, 0x0001 or 0x0002 or 0x0040)
        user32.SetForegroundWindow(hwnd)
        Thread.sleep(2500)
        Robot().createScreenCapture(Rectangle(java.awt.Toolkit.getDefaultToolkit().screenSize)).let {
          ImageIO.write(it, "png", File(shots, "real-flow-$mode-${pickMode.name.lowercase()}-2-explorer.png"))
        }
        user32.PostMessage(hwnd, 0x0010, WinDef.WPARAM(0), WinDef.LPARAM(0))
        settle { !isBusy() }
        assertTrue("a cancel adds nothing", lib.roots.value.isEmpty())
        SwingUtilities.invokeAndWait { win.get().dispatchEvent(java.awt.event.WindowEvent(win.get(), java.awt.event.WindowEvent.WINDOW_CLOSING)) }
        app.join(15000)
      }
    }
  }

  /**
   * The real Windows dialog, once in each mode: it is opened, found by its title, photographed from the screen and closed
   * the way a person closes it, which also proves that the cancel code is mapped to a silent no-op.
   */
  @Test
  fun realNativeDialogCapturedInBothModes() {
    val out = dir ?: return
    assumeTrue(System.getProperty("os.name").startsWith("Windows"))
    assumeFalse(GraphicsEnvironment.isHeadless())
    out.mkdirs()
    for (mode in PickMode.values()) {
      val title = "T.A.L. evidence ${mode.name}"
      val filters = NativePicker.request(mode).filters
      val result = AtomicReference<PickOutcome>()
      val t = Thread { result.set(WindowsFileDialog.pick(PickRequest(mode, title, filters, 0L))) }.apply { isDaemon = true; start() }
      val user32 = User32.INSTANCE
      var hwnd: WinDef.HWND? = null
      val end = System.currentTimeMillis() + 15000
      while (System.currentTimeMillis() < end) {
        val h = user32.FindWindow("#32770", title)
        if (h != null && user32.IsWindowVisible(h)) { hwnd = h; break }
        Thread.sleep(50)
      }
      assertTrue("the $mode dialog opened", hwnd != null)
      // Topmost for the photograph, so no other window can sit over the dialog (SWP_NOSIZE | SWP_NOMOVE | SWP_SHOWWINDOW).
      // A background process may not take the foreground; a tap on Alt lets SetForegroundWindow through.
      Robot().apply { keyPress(java.awt.event.KeyEvent.VK_ALT); keyRelease(java.awt.event.KeyEvent.VK_ALT) }
      user32.ShowWindow(hwnd, 9)
      user32.SetWindowPos(hwnd, WinDef.HWND(com.sun.jna.Pointer.createConstant(-1)), 0, 0, 0, 0, 0x0001 or 0x0002 or 0x0040)
      user32.SetForegroundWindow(hwnd)
      Thread.sleep(2500)
      val rect = WinDef.RECT()
      assertTrue(user32.GetWindowRect(hwnd, rect))
      println("EVIDENCE $mode rect=${rect.left},${rect.top},${rect.right},${rect.bottom} visible=${user32.IsWindowVisible(hwnd)} fg=${user32.GetForegroundWindow() == hwnd}")
      Robot().createScreenCapture(Rectangle(java.awt.Toolkit.getDefaultToolkit().screenSize)).let {
        ImageIO.write(it, "png", File(File(out, "native").also(File::mkdirs), "screen-with-${mode.name.lowercase()}-dialog.png"))
      }
      val shot = Robot().createScreenCapture(Rectangle(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top))
      ImageIO.write(shot, "png", File(File(out, "native").also(File::mkdirs), "native-dialog-${mode.name.lowercase()}.png"))
      println("EVIDENCE native-dialog $mode ${shot.width}x${shot.height}")
      user32.PostMessage(hwnd, 0x0010, WinDef.WPARAM(0), WinDef.LPARAM(0))
      t.join(15000)
      assertFalse("the $mode dialog thread ended", t.isAlive)
      assertSame("closing the $mode dialog is a cancel", PickOutcome.Cancelled, result.get())
    }
  }
}
