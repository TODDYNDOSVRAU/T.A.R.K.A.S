package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
  @Upsert
  suspend fun upsert(tracks: List<TrackEntity>)

  @Query("SELECT * FROM tracks WHERE picked = 1 ORDER BY dateAddedSec")
  suspend fun picked(): List<TrackEntity>

  @Query("SELECT * FROM tracks WHERE id IN (:ids)")
  suspend fun byIds(ids: List<String>): List<TrackEntity>

  @Query("SELECT * FROM tracks")
  suspend fun all(): List<TrackEntity>

  @Query("DELETE FROM tracks WHERE id = :id")
  suspend fun delete(id: String)

  /** Removes the tracks nothing refers to any more: not picked, not a favourite, not in a playlist. */
  @Query(
    "DELETE FROM tracks WHERE picked = 0 AND id NOT IN (SELECT trackId FROM favorites) " +
      "AND id NOT IN (SELECT trackId FROM playlist_tracks)"
  )
  suspend fun deleteUnreferenced()

  @Query("DELETE FROM tracks")
  suspend fun deleteAll()
}

@Dao
interface FavoriteDao {
  @Query("SELECT trackId FROM favorites ORDER BY addedAt DESC, trackId")
  fun observe(): Flow<List<String>>

  @Query("SELECT trackId FROM favorites ORDER BY addedAt DESC, trackId")
  suspend fun all(): List<String>

  @Query("SELECT * FROM favorites ORDER BY addedAt DESC, trackId")
  suspend fun entities(): List<FavoriteEntity>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun add(favorite: FavoriteEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun addAll(favorites: List<FavoriteEntity>)

  @Query("DELETE FROM favorites WHERE trackId = :trackId")
  suspend fun remove(trackId: String)

  @Query("DELETE FROM favorites")
  suspend fun deleteAll()
}

@Dao
interface PlaylistDao {
  @Query("SELECT * FROM playlists ORDER BY createdAt, id")
  fun observe(): Flow<List<PlaylistEntity>>

  @Query("SELECT * FROM playlists ORDER BY createdAt, id")
  suspend fun all(): List<PlaylistEntity>

  @Query("SELECT * FROM playlist_tracks ORDER BY playlistId, position")
  fun observeEntries(): Flow<List<PlaylistTrackEntity>>

  @Query("SELECT * FROM playlist_tracks ORDER BY playlistId, position")
  suspend fun entries(): List<PlaylistTrackEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(playlist: PlaylistEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEntries(entries: List<PlaylistTrackEntity>)

  @Query("UPDATE playlists SET name = :name WHERE id = :id")
  suspend fun rename(id: String, name: String)

  @Query("DELETE FROM playlists WHERE id = :id")
  suspend fun delete(id: String)

  @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
  suspend fun clearEntries(id: String)

  @Query("DELETE FROM playlists")
  suspend fun deleteAll()

  /** Replaces the whole list of a playlist; positions are rewritten 0..n-1. */
  @Transaction
  suspend fun setTracks(id: String, trackIds: List<String>) {
    clearEntries(id)
    insertEntries(trackIds.mapIndexed { i, t -> PlaylistTrackEntity(id, i, t) })
  }
}

@Dao
interface PlayStatDao {
  @Query("SELECT * FROM play_stats")
  suspend fun all(): List<PlayStatEntity>

  @Query("SELECT * FROM play_stats WHERE trackId = :trackId")
  suspend fun get(trackId: String): PlayStatEntity?

  @Upsert
  suspend fun upsert(stat: PlayStatEntity)

  @Upsert
  suspend fun upsertAll(stats: List<PlayStatEntity>)

  @Query("DELETE FROM play_stats")
  suspend fun deleteAll()
}

@Dao
interface PlaybackStateDao {
  @Query("SELECT * FROM playback_state WHERE id = 0")
  suspend fun get(): PlaybackStateEntity?

  @Upsert
  suspend fun put(state: PlaybackStateEntity)

  @Query("UPDATE playback_state SET positionMs = :positionMs, positionTrackId = :trackId WHERE id = 0")
  suspend fun putPosition(positionMs: Long, trackId: String?)

  @Query("DELETE FROM playback_state")
  suspend fun clear()
}

@Dao
interface SettingDao {
  @Query("SELECT * FROM settings WHERE scope = :scope")
  suspend fun scope(scope: String): List<SettingEntity>

  @Query("SELECT * FROM settings")
  suspend fun all(): List<SettingEntity>

  @Upsert
  suspend fun put(setting: SettingEntity)

  @Upsert
  suspend fun putAll(settings: List<SettingEntity>)

  @Query("DELETE FROM settings WHERE scope = :scope AND key = :key")
  suspend fun remove(scope: String, key: String)

  @Query("DELETE FROM settings")
  suspend fun deleteAll()
}
