package com.example.windows.state

import com.example.data.db.AppDatabase
import com.example.windows.audio.DesktopPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** D8 (owner, 2026-11-27): optional nickname in Ajustes; empty falls back to the Windows user name. */
class NicknameTest {
  @Test fun cleaningFoldsSpacesDropsControlsAndCaps() {
    assertEquals("Ana Lu", TarkasState.cleanNickname("  Ana \t  Lu \n"))
    assertEquals("AnaLu", TarkasState.cleanNickname("Ana\u0000Lu\u0007"))
    assertEquals("", TarkasState.cleanNickname("   "))
    val long = "x".repeat(500)
    assertEquals(TarkasState.NICKNAME_MAX, TarkasState.cleanNickname(long).length)
    // A cut that lands on a space leaves no trailing space.
    assertEquals("a".repeat(23), TarkasState.cleanNickname("a".repeat(23) + " bcd"))
  }

  @Test fun greetingNamePrefersNicknameThenWindowsUser() {
    assertEquals("Ana", TarkasState.greetingName("Ana", "todyn"))
    assertEquals("Todyn", TarkasState.greetingName("", "todyn"))
    assertEquals("Todyn", TarkasState.greetingName("   ", "todyn"))
    assertEquals("Élio", TarkasState.greetingName("", "élio"))
    assertEquals("", TarkasState.greetingName("", null))
    assertEquals("", TarkasState.greetingName("", "  "))
  }

  @Test fun nicknameIsSavedAndReadBackOnTheNextStart() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.inMemory()
    val first = TarkasState(DesktopPlayer(), db, scope = scope)
    assertEquals("", first.nickname.value)
    first.setNickname("  Ana  ")
    assertEquals("Ana", first.nickname.value)
    // The write runs on the scope (whose eager stateIn collectors never finish, so wait on the row itself).
    kotlinx.coroutines.withTimeout(2_000) {
      while (db.settings().all().none { it.key == TarkasState.KEY_NICKNAME && it.value == "Ana" }) kotlinx.coroutines.delay(10)
    }
    val second = TarkasState(DesktopPlayer(), db, scope = scope)
    assertEquals("Ana", second.nickname.value)
    assertEquals("Ana", second.displayName.value)
    // Clearing it returns the greeting to the Windows account name.
    second.setNickname("")
    val user = TarkasState.greetingName("", System.getProperty("user.name"))
    kotlinx.coroutines.withTimeout(2_000) { while (second.displayName.value != user) kotlinx.coroutines.yield() }
    assertEquals(user, second.displayName.value)
    scope.cancel(); db.close()
  }
}
