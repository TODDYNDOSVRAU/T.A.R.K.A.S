package com.example

import com.example.data.update.ReleaseInfo
import com.example.data.update.ReleaseNotes
import com.example.data.update.ReleaseParser
import com.example.data.update.ReleaseReading
import com.example.data.update.UpdateIntegrity
import com.example.data.update.UpdateIntegrity.Expected
import com.example.data.update.UpdateProblem
import com.example.data.update.UpdateState
import com.example.data.update.UpdateState.Phase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The install-time hash rules, the cancel clean-up and how release notes are shown. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateIntegrityTest {

  @get:Rule val tmp = TemporaryFolder()

  private val content = "apk bytes".toByteArray()
  private val good = "7ee1f2e0b6a3b3c3b0a5d0e7a5f0cde2a0a3ad3ee3a0b6b1c5c3c1e0a0f7d0aa"

  private fun apk(): File = tmp.newFile("TARKAS-1.18.apk").also { it.writeBytes(content) }
  private fun realHash(f: File) = UpdateIntegrity.sha256(f)

  private fun info(sha: String? = null, url: String? = null, notes: String = "") = ReleaseInfo(
    tag = "v1.18", versionName = "1.18", apkName = "TARKAS-1.18.apk", apkUrl = "https://x/a.apk",
    apkSize = 9, sha256 = sha, checksumUrl = url, notes = notes, pageUrl = "https://x"
  )

  // ---- hash source ----

  @Test fun digestFieldWinsAndNothingIsFetched() {
    var fetched = false
    val e = UpdateIntegrity.expected(info(sha = good.uppercase(), url = "https://x/a.sha256")) { fetched = true; null }
    assertEquals(Expected.Hash(good), e)
    assertFalse(fetched)
  }

  @Test fun sidecarIsFetchedWhenThereIsNoDigest() {
    val e = UpdateIntegrity.expected(info(url = "https://x/a.sha256")) { good }
    assertEquals(Expected.Hash(good), e)
  }

  @Test fun sidecarThatCannotBeFetchedIsUnavailable() {
    assertEquals(Expected.Unavailable, UpdateIntegrity.expected(info(url = "https://x/a.sha256")) { null })
  }

  @Test fun noHashPublishedIsReportedAsSuch() {
    assertEquals(Expected.NotPublished, UpdateIntegrity.expected(info()) { error("must not fetch") })
  }

  // ---- the check before installing ----

  @Test fun matchingHashPasses() {
    val f = apk()
    assertNull(UpdateIntegrity.check(f, Expected.Hash(realHash(f))))
  }

  @Test fun mismatchingHashBlocksTheInstall() {
    assertEquals(UpdateProblem.CHECKSUM, UpdateIntegrity.check(apk(), Expected.Hash(good)))
  }

  @Test fun missingHashBlocksTheInstall() {
    assertEquals(UpdateProblem.HASH_UNAVAILABLE, UpdateIntegrity.check(apk(), Expected.Unavailable))
  }

  @Test fun fileChangedAfterDownloadIsCaught() {
    val f = apk()
    val wanted = Expected.Hash(realHash(f))
    f.appendBytes(byteArrayOf(1))
    assertEquals(UpdateProblem.CHECKSUM, UpdateIntegrity.check(f, wanted))
  }

  @Test fun checksumFileFormatsAreRead() {
    assertEquals(good, ReleaseParser.parseChecksumFile("$good  TARKAS-1.18.apk\n"))
    assertNull(ReleaseParser.parseChecksumFile("<html>not found</html>"))
  }

  // ---- cancel ----

  @Test fun cancelRemovesThePartialFileOnly() {
    val dir = tmp.newFolder("updates")
    val part = File(dir, "TARKAS-1.18.apk.part").also { it.writeBytes(content) }
    val other = File(dir, "TARKAS-1.18.apk").also { it.writeBytes(content) }
    UpdateIntegrity.discardPartials(dir)
    assertFalse(part.exists())
    assertTrue(other.exists())
  }

  @Test fun cancelReturnsToUpdateAvailable() {
    val downloading = UpdateState(phase = Phase.DOWNLOADING, release = info(), progress = 0.4f, problem = UpdateProblem.METERED)
    val after = UpdateIntegrity.afterCancel(downloading)
    assertEquals(Phase.AVAILABLE, after.phase)
    assertEquals(0f, after.progress, 0f)
    assertNull(after.problem)
    assertEquals(downloading.release, after.release)
  }

  // ---- release notes ----

  @Test fun emptyNotesShowNothing() {
    assertNull(ReleaseNotes.plain(""))
    assertNull(ReleaseNotes.plain("  \n\r\n "))
    assertNull(ReleaseNotes.plain("<!-- nothing -->"))
  }

  @Test fun notesAreReducedToPlainText() {
    val raw = "## What's new\r\n\r\n* **Faster** scan\r\n- See [the docs](https://x/y) and `code`\r\n\r\n\r\n\r\nEnd"
    assertEquals("What's new\n\n• Faster scan\n• See the docs and code\n\nEnd", ReleaseNotes.plain(raw))
  }

  @Test fun veryLongNotesAreCut() {
    val out = ReleaseNotes.plain("a".repeat(20000))!!
    assertTrue(out.length <= 6001)
    assertTrue(out.endsWith("…"))
  }

  @Test fun notesComeFromTheReleaseBody() {
    val json = """{"tag_name":"v1.18","body":"  Fixes\n","html_url":"https://x","assets":[{"name":"TARKAS-1.18.apk","size":9,"state":"uploaded","browser_download_url":"https://x/a.apk"}]}"""
    val found = (ReleaseParser.parse(json) as ReleaseReading.Found).release
    assertEquals("Fixes", found.notes)
    assertEquals("Fixes", ReleaseNotes.plain(found.notes))
  }
}
