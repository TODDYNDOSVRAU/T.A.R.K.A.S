package com.example.data.model

data class Playlist(
  val id: String,
  val name: String,
  val trackIds: List<String> = emptyList()
)

enum class RepeatMode { OFF, ALL, ONE }
