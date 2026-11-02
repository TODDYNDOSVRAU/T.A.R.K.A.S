package com.example.data.audio

/**
 * Whether a playback error is the connection's fault (stop on the track and offer a retry) or the
 * track's (skip it). Pure, so the rules are tested without a player.
 */
object PlaybackErrors {

  /**
   * @param network the player said the connection failed or timed out
   * @param httpStatus the server's answer for a bad HTTP status, or null
   * @param timeout the cause was a socket timeout
   *
   * A server that is busy or failing (408, 429, 5xx) is the connection's fault too: the next track
   * comes from the same server and would fail the same way. A track that is not there (404, 410, other
   * 4xx) or a file that cannot be decoded is the track's fault.
   */
  fun isNetwork(network: Boolean, httpStatus: Int?, timeout: Boolean): Boolean =
    network || timeout || (httpStatus != null && (httpStatus == 408 || httpStatus == 429 || httpStatus >= 500))
}
