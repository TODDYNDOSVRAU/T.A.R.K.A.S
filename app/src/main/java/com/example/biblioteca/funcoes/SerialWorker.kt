package com.example.data.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Runs jobs one at a time, in the order they were sent, off the caller's thread. A job that fails is
 * reported and the next one still runs. Used so database writes never overlap or reorder.
 */
class SerialWorker(scope: CoroutineScope, private val onError: (Throwable) -> Unit) {
  private val jobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)

  init {
    scope.launch {
      for (job in jobs) {
        try {
          job()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          onError(e)
        }
      }
    }
  }

  fun submit(job: suspend () -> Unit) {
    jobs.trySend(job)
  }

  /** Returns once every job sent before this call has finished. */
  suspend fun idle() {
    val done = CompletableDeferred<Unit>()
    submit { done.complete(Unit) }
    done.await()
  }
}
