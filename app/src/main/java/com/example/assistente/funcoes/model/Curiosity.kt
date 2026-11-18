package com.example.data.model

import androidx.annotation.StringRes
import com.example.R

enum class VerificationStatus(@StringRes val labelRes: Int) {
  VERIFIED_FACT(R.string.assistant_curiosity_status_verified),
  HISTORICAL_ANALYSIS(R.string.assistant_curiosity_status_historical),
  TECHNICAL_SPEC(R.string.assistant_curiosity_status_technical)
}

data class Curiosity(
  val trackId: String,
  val trackTitle: String,
  val artist: String,
  val shortTrivia: String,
  val wordCount: Int,
  val documentedFacts: String,
  val sources: List<String>,
  val status: VerificationStatus,
  val verifiedDate: String
)
