package com.example.data.library

/**
 * Reads the complete elements of a JSON array that may have been cut short (a write that did not
 * finish). `JSONArray(text)` refuses the whole text then; this keeps every element that is complete
 * and drops the last one when it is cut. Elements come back as text, to be parsed one by one.
 */
object JsonSalvage {

  fun elements(raw: String?): List<String> {
    if (raw == null) return emptyList()
    var i = 0
    while (i < raw.length && raw[i].isWhitespace()) i++
    if (i >= raw.length || raw[i] != '[') return emptyList()
    i++
    val out = ArrayList<String>()
    var start = i
    var depth = 0
    var inString = false
    var escaped = false
    while (i < raw.length) {
      val c = raw[i]
      if (inString) {
        if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false
      } else when (c) {
        '"' -> inString = true
        '{', '[' -> depth++
        '}' -> depth--
        ']' -> if (depth == 0) {
          add(out, raw.substring(start, i))
          return out
        } else depth--
        ',' -> if (depth == 0) {
          add(out, raw.substring(start, i))
          start = i + 1
        }
      }
      i++
    }
    // The text ended before the closing bracket: the last element counts only when it is whole.
    if (!inString && depth == 0) add(out, raw.substring(start))
    return out
  }

  private fun add(out: MutableList<String>, element: String) {
    val text = element.trim()
    if (text.isNotEmpty()) out += text
  }
}
