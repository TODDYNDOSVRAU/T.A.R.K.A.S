package com.example.data.systemaudio

/**
 * When an attached session may be let go. A player that changes track often closes its session and
 * opens it again, or drops out of one dump; releasing the effect at once would leave a moment without
 * the curve. A session is released only after it was missing for [graceMs] in a row; seen again in
 * that time, it simply stays.
 *
 * It also counts failed attaches, so a session the device refused gets a few quick retries
 * ([MAX_ATTEMPTS], [RETRY_MS] apart) instead of waiting for the next scan.
 *
 * Pure bookkeeping with the clock passed in, so it is checked without a device.
 */
class SessionGrace(private val graceMs: Long = GRACE_MS) {

  companion object {
    const val GRACE_MS = 2000L
    const val RETRY_MS = 100L
    const val MAX_ATTEMPTS = 5
  }

  private val missingSince = mutableMapOf<Int, Long>()
  private val failures = mutableMapOf<Int, Int>()

  /**
   * Of the [attached] sessions, the ones to release now, given the sessions seen [alive] at [nowMs].
   * Forgets whatever it no longer needs.
   */
  fun expired(attached: Set<Int>, alive: Set<Int>, nowMs: Long): Set<Int> {
    val gone = mutableSetOf<Int>()
    for (id in attached) {
      if (id in alive) {
        missingSince.remove(id)
        continue
      }
      val since = missingSince.getOrPut(id) { nowMs }
      if (nowMs - since >= graceMs) gone += id
    }
    missingSince.keys.retainAll(attached - gone)
    failures.keys.retainAll(alive)
    return gone
  }

  /** When the next session would expire, or null when none is missing. */
  fun nextExpiry(): Long? = missingSince.values.minOrNull()?.plus(graceMs)

  /** Records how attaching [sessionId] went; true when it failed and should be tried again soon. */
  fun attached(sessionId: Int, ok: Boolean): Boolean {
    if (ok) {
      failures.remove(sessionId)
      return false
    }
    val n = (failures[sessionId] ?: 0) + 1
    failures[sessionId] = n
    return n < MAX_ATTEMPTS
  }

  /** False once a session failed [MAX_ATTEMPTS] times; it is tried again only after it disappears. */
  fun shouldTry(sessionId: Int): Boolean = (failures[sessionId] ?: 0) < MAX_ATTEMPTS

  fun clear() {
    missingSince.clear()
    failures.clear()
  }
}
