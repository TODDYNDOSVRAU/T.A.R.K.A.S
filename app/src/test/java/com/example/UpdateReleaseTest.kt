package com.example

import com.example.data.update.ReleaseParser
import com.example.data.update.ReleaseReading
import com.example.data.update.Signers
import com.example.data.update.UpdateProblem
import com.example.data.update.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How versions compare and how the GitHub release answer is read. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateReleaseTest {

  private val hash = "ddbbfb02dc2ab9e58085bd1d69640e8b8efd923c1ad17a06ab00855a944ceb60"

  private fun release(
    tag: String = "v1.15", draft: Boolean = false, pre: Boolean = false, assets: String =
      """{"name":"TARKAS-1.15.apk","size":3337639,"state":"uploaded","digest":"sha256:$hash",
          "browser_download_url":"https://github.com/o/r/releases/download/v1.15/TARKAS-1.15.apk"}"""
  ) = """{"tag_name":"$tag","draft":$draft,"prerelease":$pre,"body":"Notas","html_url":"https://github.com/o/r/releases/tag/$tag","assets":[$assets]}"""

  // ---- versions ----

  @Test fun numbersCompareNumericallyNotAsText() {
    assertTrue(Version.isNewer("v1.14", "1.9"))
    assertTrue(Version.isNewer("1.15", "1.14"))
    assertTrue(Version.isNewer("2.0", "1.99"))
    assertTrue(Version.isNewer("1.14.1", "1.14"))
  }

  @Test fun sameOrOlderIsNotNewer() {
    assertFalse(Version.isNewer("v1.14", "1.14"))
    assertFalse(Version.isNewer("1.14.0", "1.14"))
    assertFalse(Version.isNewer("1.13", "1.14"))
  }

  @Test fun preReleaseIsOlderThanItsRelease() {
    assertTrue(Version.isNewer("1.15", "1.15-beta"))
    assertFalse(Version.isNewer("1.15-beta", "1.15"))
    assertTrue(Version.isNewer("1.15-beta", "1.14"))
  }

  @Test fun buildMetadataIsIgnored() {
    assertFalse(Version.isNewer("1.14+5", "1.14"))
    assertNull(Version.parse("1.14+5")!!.preRelease)
  }

  @Test fun textThatIsNotAVersionIsNeverNewer() {
    assertNull(Version.parse("nightly"))
    assertNull(Version.parse(""))
    assertFalse(Version.isNewer("nightly", "1.14"))
    assertFalse(Version.isNewer("1.15", "dev"))
  }

  // ---- the release answer ----

  @Test fun readsTagApkSizeAndGithubDigest() {
    val found = ReleaseParser.parse(release()) as ReleaseReading.Found
    val r = found.release
    assertEquals("v1.15", r.tag)
    assertEquals("1.15", r.versionName)
    assertEquals("TARKAS-1.15.apk", r.apkName)
    assertEquals(3337639L, r.apkSize)
    assertEquals(hash, r.sha256)
    assertNull(r.checksumUrl)
    assertEquals("Notas", r.notes)
  }

  @Test fun usesTheSha256SidecarWhenThereIsNoDigest() {
    val assets = """
      {"name":"TARKAS-1.15.apk","size":10,"browser_download_url":"https://x/TARKAS-1.15.apk"},
      {"name":"TARKAS-1.15.apk.sha256","size":90,"browser_download_url":"https://x/TARKAS-1.15.apk.sha256"}"""
    val r = (ReleaseParser.parse(release(assets = assets)) as ReleaseReading.Found).release
    assertNull(r.sha256)
    assertEquals("https://x/TARKAS-1.15.apk.sha256", r.checksumUrl)
  }

  @Test fun anApkWithoutAnyChecksumStillParses() {
    val assets = """{"name":"TARKAS-1.15.apk","size":10,"browser_download_url":"https://x/a.apk"}"""
    val r = (ReleaseParser.parse(release(assets = assets)) as ReleaseReading.Found).release
    assertNull(r.sha256)
    assertNull(r.checksumUrl)
  }

  @Test fun draftsAndPreReleasesAreNotInstalled() {
    assertEquals(ReleaseReading.NotInstallable(UpdateProblem.NO_APK), ReleaseParser.parse(release(draft = true)))
    assertEquals(ReleaseReading.NotInstallable(UpdateProblem.NO_APK), ReleaseParser.parse(release(pre = true)))
  }

  @Test fun releaseWithoutApkOrWithAmbiguousApksIsNotInstallable() {
    assertEquals(ReleaseReading.NotInstallable(UpdateProblem.NO_APK), ReleaseParser.parse(release(assets = "")))
    val two = """{"name":"a.apk","browser_download_url":"https://x/a.apk"},{"name":"b.apk","browser_download_url":"https://x/b.apk"}"""
    assertEquals(ReleaseReading.NotInstallable(UpdateProblem.NO_APK), ReleaseParser.parse(release(assets = two)))
  }

  @Test fun picksTheTarkasApkAmongSeveral() {
    val two = """{"name":"other.apk","browser_download_url":"https://x/o.apk"},{"name":"TARKAS-1.15.apk","browser_download_url":"https://x/t.apk"}"""
    val r = (ReleaseParser.parse(release(assets = two)) as ReleaseReading.Found).release
    assertEquals("https://x/t.apk", r.apkUrl)
  }

  @Test fun anApkOverPlainHttpIsRefused() {
    val http = """{"name":"TARKAS-1.15.apk","browser_download_url":"http://x/t.apk"}"""
    assertEquals(ReleaseReading.NotInstallable(UpdateProblem.NO_APK), ReleaseParser.parse(release(assets = http)))
  }

  @Test fun anAssetStillUploadingIsIgnored() {
    val partial = """{"name":"TARKAS-1.15.apk","state":"open","browser_download_url":"https://x/t.apk"}"""
    assertEquals(ReleaseReading.NotInstallable(UpdateProblem.NO_APK), ReleaseParser.parse(release(assets = partial)))
  }

  @Test fun garbageAndUnversionedTagsAreUnreadable() {
    assertEquals(ReleaseReading.Unreadable, ReleaseParser.parse("<html>"))
    assertEquals(ReleaseReading.Unreadable, ReleaseParser.parse("{}"))
    assertEquals(ReleaseReading.Unreadable, ReleaseParser.parse(release(tag = "nightly")))
  }

  @Test fun checksumFileHoldsTheFirstSha256() {
    assertEquals(hash, ReleaseParser.parseChecksumFile("${hash.uppercase()}  TARKAS-1.15.apk\n"))
    assertNull(ReleaseParser.parseChecksumFile("not a hash"))
  }

  // ---- signing and messages ----

  @Test fun onlyKnownCertificatesMayReplaceTheInstalledApp() {
    assertTrue(Signers.compatible(setOf("a"), setOf("a")))
    assertTrue(Signers.compatible(setOf("old", "new"), setOf("new")))
    assertFalse(Signers.compatible(setOf("a"), setOf("b")))
    assertFalse(Signers.compatible(setOf("a"), emptySet()))
    assertFalse(Signers.compatible(emptySet(), setOf("a")))
  }

  @Test fun theDevelopmentBuildNeverInstallsAndSaysWhy() {
    // Unit tests run the debug variant, signed with the developer's key.
    assertFalse(com.example.data.update.UpdateManager.installsUpdates)
    val notice = com.example.data.update.UpdateManager.DEV_BUILD_NOTICE
    assertFalse(notice.contains('!'))
    assertTrue(notice.contains("outra chave"))
  }

  @Test fun everyProblemSaysWhatHappenedAndHasNoExclamation() {
    UpdateProblem.entries.forEach {
      assertTrue(it.name, it.message.length > 30)
      assertFalse(it.name, it.message.contains('!'))
      assertTrue(it.name, it.message.endsWith("."))
      assertNotNull(it.message)
    }
  }
}
