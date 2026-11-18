package com.example.data.repository

import com.example.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Where the player gets a track's waveform: 100 amplitudes, one byte each (0..255), or null while they
 * are not known. Null stays null for tracks that cannot be analysed (online, protected, undecodable);
 * the progress bar then keeps the plain thin bar. Emits again when the calculation finishes.
 *
 * The calculation and the storage belong to the library side; this is only what the player reads.
 */
fun interface WaveformSource {
  fun waveform(track: Track): Flow<ByteArray?>

  /** Used until the library side provides one: nothing is known, so the thin bar stays. */
  object None : WaveformSource {
    override fun waveform(track: Track): Flow<ByteArray?> = flowOf(null)
  }
}
