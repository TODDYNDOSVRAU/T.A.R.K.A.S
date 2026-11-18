package com.example.windows.equalizador

import com.example.data.autoeq.AutoEqEntry

/** What the headphone-correction list shows: the AutoEq index, the search and the curve being downloaded. */
data class DeviceSearchState(
  val loading: Boolean = false,
  val error: String? = null,
  val query: String = "",
  val source: String? = null,
  val sources: List<String> = emptyList(),
  val total: Int = 0,
  val onlineOnly: Int = 0,
  val results: List<AutoEqEntry> = emptyList(),
  val applyingPath: String? = null
)
