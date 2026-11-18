package com.example.data.library

import com.example.data.db.TrackEntity
import com.example.data.model.Track

private const val DEFAULT_COLOR = 0xFF46C9D3

/** The row of a track; `colorHex` is the colour as hexadecimal text, `albumId` 0 when unknown. */
fun Track.toEntity(picked: Boolean = false, dateAddedSec: Long = this.dateAddedSec) = TrackEntity(
  id = id, title = title, artist = artist, album = album, durationMs = durationMs, genre = genre, era = era,
  isLocalFile = isLocalFile, filePath = filePath, colorHex = colorHex.toString(16), artworkUrl = artworkUrl,
  isDemo = isDemo, albumId = albumId ?: 0L, discNumber = discNumber, trackNumber = trackNumber,
  dateAddedSec = dateAddedSec, picked = picked
)

fun TrackEntity.toTrack() = Track(
  id = id, title = title, artist = artist, album = album, durationMs = durationMs, genre = genre, era = era,
  isLocalFile = isLocalFile, filePath = filePath, colorHex = colorHex?.toLongOrNull(16) ?: DEFAULT_COLOR,
  artworkUrl = artworkUrl, isDemo = isDemo, albumId = albumId.takeIf { it > 0 },
  discNumber = discNumber, trackNumber = trackNumber, dateAddedSec = dateAddedSec
)
