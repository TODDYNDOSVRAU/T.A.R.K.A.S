package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A track the app has to remember beyond the device scan: a picked file, or a track that a playlist or
 * the favourites still point to. The columns follow `Track`; `TrackJson` keeps the same meaning.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
  @PrimaryKey val id: String,
  val title: String,
  val artist: String,
  val album: String,
  val durationMs: Long,
  val genre: String,
  val era: String,
  val isLocalFile: Boolean,
  val filePath: String?,
  val colorHex: String?,
  val artworkUrl: String?,
  val isDemo: Boolean,
  val albumId: Long,
  val discNumber: Int,
  val trackNumber: Int,
  val dateAddedSec: Long,
  /** True for a file the user picked by hand (it is listed even when the device scan does not find it). */
  val picked: Boolean = false
)

/** One favourite. `addedAt` orders them, newest first. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
  @PrimaryKey val trackId: String,
  val addedAt: Long
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
  @PrimaryKey val id: String,
  val name: String,
  val createdAt: Long
)

/** A track inside a playlist, at a position. The same track can appear twice, at two positions. */
@Entity(
  tableName = "playlist_tracks",
  primaryKeys = ["playlistId", "position"],
  foreignKeys = [ForeignKey(
    entity = PlaylistEntity::class,
    parentColumns = ["id"],
    childColumns = ["playlistId"],
    onDelete = ForeignKey.CASCADE
  )],
  indices = [Index("trackId")]
)
data class PlaylistTrackEntity(
  val playlistId: String,
  val position: Int,
  val trackId: String
)

/** Plays and skips of one track (rules in `PlayRules`). */
@Entity(tableName = "play_stats")
data class PlayStatEntity(
  @PrimaryKey val trackId: String,
  val plays: Int,
  val skips: Int,
  val lastPlayed: Long,
  val lastSkipped: Long
)

/**
 * The state of the player, one row (`id` is always 0): the track, the queue and the list it came from
 * as JSON in [stateJson], and the position with the track it belongs to.
 */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
  @PrimaryKey val id: Int = 0,
  val stateJson: String,
  val positionMs: Long,
  val positionTrackId: String?
)

/** One preference of a settings group, as text. Replaces the separate SharedPreferences files (G8). */
@Entity(tableName = "settings", primaryKeys = ["scope", "key"])
data class SettingEntity(
  val scope: String,
  val key: String,
  val value: String
)
