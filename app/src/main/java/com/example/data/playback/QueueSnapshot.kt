package com.example.data.playback

import com.example.data.model.Track

/**
 * P3: what "save the queue as a playlist" saves, in play order: the song playing now, what the user
 * queued, then what the list plays after it. Repeats stay (a playlist may hold a song twice).
 */
object QueueSnapshot {
  fun tracks(current: Track?, queue: List<Track>, upcoming: List<Track>): List<Track> =
    (listOfNotNull(current) + queue + upcoming).filterNot { it.isDemo }
}
