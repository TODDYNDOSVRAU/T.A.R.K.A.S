package com.example.data.assistant

import org.json.JSONArray
import org.json.JSONObject

/*
 * Shapes of the Gemini REST requests and replies. Pure functions: the tests check them without a network,
 * and AssistantEngine only sends what these build.
 */

internal const val GEMINI_MODEL_URL =
  "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

/** The player commands as function declarations. The names are the tool names of [PLAYER_COMMANDS]. */
internal fun geminiTools(): JSONArray {
  val declarations = JSONArray()
  PLAYER_COMMANDS.forEach { entry ->
    declarations.put(
      JSONObject()
        .put("name", entry.toolName)
        .put("description", "Runs the player command ${entry.toolName} in the Tarkas app. Use only when the user asks for it.")
    )
  }
  return JSONArray().put(JSONObject().put("functionDeclarations", declarations))
}

internal fun geminiRequestJson(systemContext: String, contents: JSONArray): JSONObject = JSONObject().apply {
  put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemContext))))
  put("contents", contents)
  put("tools", geminiTools())
  put("generationConfig", JSONObject().apply {
    put("temperature", 0.2)
    put("maxOutputTokens", 120)
  })
}

internal fun geminiUserTurn(text: String): JSONObject = JSONObject()
  .put("role", "user")
  .put("parts", JSONArray().put(JSONObject().put("text", text)))

/** The model's turn that asked for a function, repeated in the conversation before its result. */
internal fun geminiFunctionCallTurn(name: String): JSONObject = JSONObject()
  .put("role", "model")
  .put(
    "parts",
    JSONArray().put(JSONObject().put("functionCall", JSONObject().put("name", name).put("args", JSONObject())))
  )

/** The result of a function, sent back to the model so it can answer the user. */
internal fun geminiFunctionResponseTurn(name: String, result: String): JSONObject = JSONObject()
  .put("role", "user")
  .put(
    "parts",
    JSONArray().put(
      JSONObject().put(
        "functionResponse",
        JSONObject().put("name", name).put("response", JSONObject().put("result", result))
      )
    )
  )

/** The tool name the model asked to run, or null when the reply is text (or is not valid JSON). */
internal fun geminiFunctionCall(body: String): String? {
  val parts = try {
    JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
  } catch (_: Exception) {
    null
  } ?: return null
  for (i in 0 until parts.length()) {
    val call = parts.optJSONObject(i)?.optJSONObject("functionCall") ?: continue
    return call.optString("name").takeIf { it.isNotBlank() }
  }
  return null
}

/** The player command for a tool name the model asked for, or null for a name this app does not offer. */
internal fun geminiPlayerCommand(toolName: String): PlayerCommand? =
  PLAYER_COMMANDS.firstOrNull { it.toolName == toolName }?.command
