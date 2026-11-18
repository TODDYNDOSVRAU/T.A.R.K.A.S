package com.example.windows.geral

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import com.example.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
  private val hash = "a".repeat(64)

  private fun release(assets: String, extra: String = "") =
    """{"tag_name":"v2.2.0","html_url":"https://github.com/x/y/releases/tag/v2.2.0","body":"n"$extra,"assets":[$assets]}"""

  private fun asset(name: String, digest: String? = null, url: String = "https://github.com/x/y/$name") =
    """{"name":"$name","state":"uploaded","size":10,"browser_download_url":"$url"${digest?.let { ""","digest":"$it"""" } ?: ""}}"""

  @Test fun versionOrder() {
    assertTrue(Version.isNewer("v2.2.0", "2.1"))
    assertFalse(Version.isNewer("2.1.0", "2.1"))
    assertFalse(Version.isNewer("2.1.0-rc1", "2.1.0"))
    assertNull(Version.parse("not a version"))
  }

  @Test fun picksMsiAndDigest() {
    val r = ReleaseParser.parse(release(asset("TARKAS-2.2.0.msi", "sha256:$hash") + "," + asset("TARKAS-2.2.0.exe")))
    r as ReleaseReading.Found
    assertEquals("TARKAS-2.2.0.msi", r.release.assetName)
    assertEquals(hash, r.release.sha256)
    assertEquals("2.2.0", r.release.versionName)
  }

  @Test fun refusesNonHttpsAndMissingInstaller() {
    assertTrue(ReleaseParser.parse(release(asset("TARKAS.msi", url = "http://x/y.msi"))) is ReleaseReading.NotInstallable)
    assertTrue(ReleaseParser.parse(release(asset("notes.txt"))) is ReleaseReading.NotInstallable)
    assertTrue(ReleaseParser.parse(release("", ""","prerelease":true""")) is ReleaseReading.NotInstallable)
    assertTrue(ReleaseParser.parse("{broken") is ReleaseReading.Unreadable)
  }

  @Test fun checksumFileFormats() {
    assertEquals(hash, ReleaseParser.parseChecksumFile("${hash.uppercase()}  TARKAS.msi\n"))
    assertEquals(hash, ReleaseParser.parseChecksumFile(hash))
    assertNull(ReleaseParser.parseChecksumFile("zzz"))
  }

  @Test fun integrityAcceptsMatchRefusesOtherwise() {
    val f = Files.createTempFile("tarkas", ".msi").toFile().apply { writeText("installer"); deleteOnExit() }
    val real = UpdateIntegrity.sha256(f)
    assertEquals(64, real.length)
    assertNull(UpdateIntegrity.check(f, UpdateIntegrity.Expected.Hash(real)))
    assertEquals(UpdateProblem.CHECKSUM, UpdateIntegrity.check(f, UpdateIntegrity.Expected.Hash(hash)))
    assertEquals(UpdateProblem.HASH_UNAVAILABLE, UpdateIntegrity.check(f, UpdateIntegrity.Expected.NotPublished))
    assertEquals(UpdateProblem.HASH_UNAVAILABLE, UpdateIntegrity.check(f, UpdateIntegrity.Expected.Unavailable))
  }

  @Test fun discardsPartialDownloads() {
    val dir = Files.createTempDirectory("tarkas-upd").toFile()
    val part = File(dir, "a.msi.part").apply { writeText("x") }
    val keep = File(dir, "a.msi").apply { writeText("x") }
    UpdateIntegrity.discardPartials(dir)
    assertFalse(part.exists()); assertTrue(keep.exists())
    dir.deleteRecursively()
  }
}

class AutostartTest {
  @Test fun commandsTargetCurrentUserRunKey() {
    assertTrue(Autostart.KEY.startsWith("HKCU"))
    assertTrue(Autostart.addArgs("C:\\p\\TARKAS.exe").joinToString(" ").contains(Autostart.MINIMIZED_FLAG))
    assertEquals("reg", Autostart.deleteArgs().first())
  }

  @Test fun enabledOnlyWhenQueryFindsValue() {
    assertTrue(Autostart { 0 to "    ${Autostart.VALUE}    REG_SZ    x" }.isEnabled())
    assertFalse(Autostart { 1 to "" }.isEnabled())
  }
}

class LicensesTest {
  @Test fun jlayerAndJflacAreListed() {
    val names = WindowsLicenses.libraries.map { it.first }
    assertTrue(names.any { it.contains("JLayer") })
    assertTrue(names.any { it.contains("jFLAC") })
    assertTrue(WindowsLicenses.libraries.first { it.first.contains("JLayer") }.second.contains("Lesser"))
  }
}

class BackupTest {
  @Test fun garbageIsRejectedBeforeAnyChange() = runBlocking {
    val db = AppDatabase.inMemory()
    val b = UserDataBackup(db)
    val err = runCatching { b.restore("not json") }.exceptionOrNull()
    assertNotNull(err)
    assertTrue(err is RestoreException)
    db.close()
  }

  @Test fun emptyExportRoundTrips() = runBlocking {
    val db = AppDatabase.inMemory()
    val b = UserDataBackup(db)
    val json = b.export("2.1", "2026-11-18T00:00:00Z")
    assertEquals(UserDataBackup.FORMAT, json.getInt("format"))
    b.restore(json.toString())
    assertEquals(0, db.playlists().all().size)
    db.close()
  }
}
