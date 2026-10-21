package com.example.data.model

enum class VerificationStatus(val label: String) {
  VERIFIED_FACT("Fato Documentado & Verificado"),
  HISTORICAL_ANALYSIS("Análise Histórica & Musical"),
  TECHNICAL_SPEC("Especificação Técnica Acústica"),
  UNVERIFIED("Sem Fonte Confiável")
}

data class Curiosity(
  val trackId: String,
  val trackTitle: String,
  val artist: String,
  val shortTrivia: String,
  val wordCount: Int,
  val documentedFacts: String,
  val musicalInterpretation: String,
  val sources: List<String>,
  val status: VerificationStatus,
  val verifiedDate: String,
  val deepDive: String
)
