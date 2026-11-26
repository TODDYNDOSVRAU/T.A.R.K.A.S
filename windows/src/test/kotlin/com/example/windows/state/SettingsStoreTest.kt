package com.example.windows.state

import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class SettingsStoreTest {
  @Test fun writesCutOffByTheDatabaseClosingAreReportedNotThrown() = runBlocking {
    // What the suite hit: settings writes still running when a test (or the app at exit) closes the database.
    val leaked = AtomicReference<Throwable?>(null)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> leaked.set(e) })
    val db = AppDatabase.inMemory()
    val store = SettingsStore(db, scope).also { it.load() }
    repeat(400) { store.put("global", "k$it", "v$it") }
    db.close()
    scope.coroutineContext[Job]!!.children.forEach { it.join() }
    assertEquals(null, leaked.get())
    assertEquals("v399", store.get("global", "k399"))         // every value stays live for this run
  }

  @Test fun normalWriteReachesTheDatabase() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.inMemory()
    val store = SettingsStore(db, scope).also { it.load() }
    store.put("global", "k", "v")
    scope.coroutineContext[Job]!!.children.forEach { it.join() }
    assertTrue(db.settings().all().any { it.scope == "global" && it.key == "k" && it.value == "v" })
    assertEquals(0, store.failures)
    db.close()
  }
}
