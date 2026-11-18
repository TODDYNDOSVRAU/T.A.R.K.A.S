package com.example

import com.example.data.diagnostics.CrashHandler
import com.example.data.diagnostics.DiagnosticStore
import com.example.data.diagnostics.Redaction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticStoreTest {
  @get:Rule val folder = TemporaryFolder()

  private fun store(clock: () -> Long = System::currentTimeMillis) = DiagnosticStore(File(folder.root, "diagnostics"), clock)

  @Test
  fun `a saved crash keeps the thread, the header and the stack`() {
    val store = store { 1_000L }
    store.saveCrash("main", IllegalStateException("boom"), header = "app: test 1.0")
    val crash = store.crashes().single()
    assertEquals(1_000L, crash.atMs)
    assertTrue(crash.text.startsWith("app: test 1.0"))
    assertTrue(crash.text.contains("thread: main"))
    assertTrue(crash.text.contains("java.lang.IllegalStateException: boom"))
    assertTrue(crash.text.contains("DiagnosticStoreTest"))
  }

  @Test
  fun `only the newest five crashes are kept, newest first`() {
    var t = 0L
    val store = store { ++t }
    repeat(8) { store.saveCrash("main", RuntimeException("n$it")) }
    val crashes = store.crashes()
    assertEquals(DiagnosticStore.MAX_CRASHES, crashes.size)
    assertEquals(listOf(8L, 7L, 6L, 5L, 4L), crashes.map { it.atMs })
    assertTrue(crashes.first().text.contains("n7"))
  }

  @Test
  fun `two crashes in the same millisecond are both kept`() {
    val store = store { 42L }
    store.saveCrash("a", RuntimeException("first"))
    store.saveCrash("b", RuntimeException("second"))
    assertEquals(2, store.crashes().size)
  }

  @Test
  fun `the previous handler runs after the crash is saved`() {
    val store = store()
    var seen: Throwable? = null
    val previous = Thread.UncaughtExceptionHandler { _, e -> seen = e }
    val error = IllegalArgumentException("x")
    CrashHandler(store, { "h" }, previous).uncaughtException(Thread.currentThread(), error)
    assertSame(error, seen)
    assertEquals(1, store.crashes().size)
  }

  @Test
  fun `the previous handler runs even when nothing can be written`() {
    val blocker = folder.newFile("not-a-directory")
    val store = DiagnosticStore(File(blocker, "diagnostics"))
    var seen: Throwable? = null
    val error = RuntimeException("x")
    CrashHandler(store, { throw IllegalStateException("header failed") }, { _, e -> seen = e })
      .uncaughtException(Thread.currentThread(), error)
    assertSame(error, seen)
    assertTrue(store.crashes().isEmpty())
    store.record("tag", "message") // must not throw either
    assertEquals("", store.events())
  }

  @Test
  fun `events are appended one per line with the stack indented`() {
    var t = 10L
    val store = store { t++ }
    store.record("playback.restore", "queue\nunreadable")
    store.record("spatial.bypass", "fell back", IllegalStateException("why"))
    val lines = store.events().lines()
    assertEquals("10 | playback.restore | queue unreadable", lines[0])
    assertEquals("11 | spatial.bypass | fell back", lines[1])
    assertTrue(lines[2].startsWith("    java.lang.IllegalStateException: why"))
  }

  @Test
  fun `the event log stays under its limit and keeps the newest lines whole`() {
    var t = 0L
    val store = store { ++t }
    val filler = "x".repeat(1_000)
    repeat(200) { store.record("tag", filler) }
    val text = store.events()
    assertTrue(text.length <= DiagnosticStore.MAX_EVENT_BYTES)
    assertTrue(text.trimEnd().lines().all { it.endsWith(filler) && it.contains(" | tag | ") })
    assertTrue(text.trimEnd().lines().last().startsWith("200 | "))
  }

  @Test
  fun `credentials are removed from exported text`() {
    val secret = "AIzaSyD-EXAMPLE-0123456789"
    val text = """
      java.io.IOException: https://host/v1/models:generate?key=$secret&alt=json
      header Authorization: Bearer abcdefghijklmnop.qrstuv
      plain mention of $secret in a message
      api_key=zzzzzzzzzz token=tttttttt
    """.trimIndent()
    val out = Redaction.scrub(text, secrets = listOf(secret))
    assertFalse(out.contains(secret))
    assertFalse(out.contains("abcdefghijklmnop"))
    assertFalse(out.contains("zzzzzzzzzz"))
    assertFalse(out.contains("tttttttt"))
    assertTrue(out.contains("key=${Redaction.REMOVED}&alt=json"))
    assertTrue(out.contains("java.io.IOException"))
  }

  @Test
  fun `an empty or short secret does not blank the text`() {
    assertEquals("a normal line", Redaction.scrub("a normal line", secrets = listOf("", "a")))
  }
}
