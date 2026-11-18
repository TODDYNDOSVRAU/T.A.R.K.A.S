package com.example.windows.assistente
// Port of app/.../assistente/funcoes (win-assistente). Android file not shareable: it names Android-only types in the same package.

/**
 * One player command and every word that asks for it. Words are written after normalizeForSearch
 * (lowercase, no accents), in Portuguese and English. Adding a command means adding one row here
 * and one test in AssistantCommandsTest.
 */
internal data class CommandEntry(val command: PlayerCommand, val toolName: String, val words: Set<String>)

/** Table order decides which command wins when a sentence asks for two. */
internal val PLAYER_COMMANDS: List<CommandEntry> = listOf(
  CommandEntry(PlayerCommand.NEXT, "next_track", setOf("proxima", "proximo", "pular", "next", "skip")),
  CommandEntry(PlayerCommand.PREVIOUS, "previous_track", setOf("anterior", "voltar", "previous", "back")),
  CommandEntry(PlayerCommand.PAUSE, "pause", setOf("pausar", "pausa", "pause", "stop")),
  CommandEntry(PlayerCommand.PLAY, "play", setOf("tocar", "toque", "continuar", "retomar", "reproduzir", "play", "resume")),
  CommandEntry(PlayerCommand.TOGGLE_SHUFFLE, "toggle_shuffle", setOf("aleatorio", "embaralhar", "shuffle")),
  CommandEntry(PlayerCommand.CYCLE_REPEAT, "cycle_repeat", setOf("repetir", "repeticao", "repeat")),
  CommandEntry(PlayerCommand.FAVORITE, "toggle_favorite", setOf("favoritar", "curtir", "favorito", "favorite", "like")),
)

/**
 * True when the whole phrase is a command and nothing else ("stop", "tocar play"), so a title that only
 * contains a command word ("Back in Black", "Like a Rolling Stone") is still a name to look up.
 */
internal fun isCommandPhrase(text: String): Boolean {
  val words = commandWords(text)
  val allCommandWords = PLAYER_COMMANDS.flatMap { it.words }.toSet()
  return words.isNotEmpty() && words.all { it in allCommandWords } && playerCommandFor(text) != null
}

/** Words that cancel the command word they stand within two words before. */
private val NEGATIONS = setOf("nao", "nunca", "not", "never", "dont", "no")

/** Splits a sentence into words: accents and case dropped, apostrophes joined ("don't" becomes "dont"). */
internal fun commandWords(text: String): List<String> =
  normalizeForSearch(text).replace("'", "").replace("’", "").split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }

/**
 * Maps a sentence to a player command and its tool name, or null.
 * Whole words only, so "pausado" never means "pausar". A command word with a negation one or two
 * words before it ("não pausar", "don't stop") does not count; the other commands in the sentence still do.
 */
internal fun playerCommandFor(lower: String): Pair<PlayerCommand, String>? {
  val words = commandWords(lower)
  for (entry in PLAYER_COMMANDS) {
    val asked = words.indices.any { index ->
      words[index] in entry.words && (1..2).none { back -> index - back >= 0 && words[index - back] in NEGATIONS }
    }
    if (asked) return entry.command to entry.toolName
  }
  return null
}
