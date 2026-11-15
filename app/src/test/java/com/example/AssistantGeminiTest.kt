package com.example

import com.example.data.assistant.PlayerCommand
import com.example.data.assistant.geminiFunctionCall
import com.example.data.assistant.geminiFunctionCallTurn
import com.example.data.assistant.geminiFunctionResponseTurn
import com.example.data.assistant.geminiPlayerCommand
import com.example.data.assistant.geminiRequestJson
import com.example.data.assistant.geminiTools
import com.example.data.assistant.geminiUserTurn
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gemini request shape and function-calling round trip: pure builders, no network.
 * Error mapping and reply text are covered by AssistantGeminiErrorsTest.
 * org.json is the Android implementation, so this runs under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AssistantGeminiTest {

  private fun replyWithCall(name: String) =
    """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"$name","args":{}}}]}}]}"""

  private fun replyWithText(text: String) =
    """{"candidates":[{"content":{"parts":[{"text":"$text"}]},"finishReason":"STOP"}]}"""

  @Test
  fun requestCarriesSystemTextContentsToolsAndLimits() {
    val contents = JSONArray().put(geminiUserTurn("olá"))
    val json = geminiRequestJson("contexto", contents)

    assertEquals("contexto", json.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
    assertEquals("user", json.getJSONArray("contents").getJSONObject(0).getString("role"))
    assertEquals("olá", json.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))
    assertEquals(120, json.getJSONObject("generationConfig").getInt("maxOutputTokens"))
    assertEquals(0.2, json.getJSONObject("generationConfig").getDouble("temperature"), 0.0001)
    assertEquals(1, json.getJSONArray("tools").length())
  }

  @Test
  fun toolsListEveryPlayerCommandByItsToolName() {
    val declarations = geminiTools().getJSONObject(0).getJSONArray("functionDeclarations")
    val names = (0 until declarations.length()).map { declarations.getJSONObject(it).getString("name") }
    assertEquals(
      listOf("next_track", "previous_track", "pause", "play", "toggle_shuffle", "cycle_repeat", "toggle_favorite"),
      names
    )
    assertEquals(PlayerCommand.NEXT, geminiPlayerCommand("next_track"))
    assertNull(geminiPlayerCommand("delete_everything"))
  }

  @Test
  fun functionCallIsRecognisedAndTextIsNot() {
    assertEquals("next_track", geminiFunctionCall(replyWithCall("next_track")))
    assertNull(geminiFunctionCall(replyWithText("oi")))
    assertNull(geminiFunctionCall("not json"))
  }

  @Test
  fun functionRoundTripTurnsAreBuilt() {
    val model = geminiFunctionCallTurn("pause")
    assertEquals("model", model.getString("role"))
    assertEquals("pause", model.getJSONArray("parts").getJSONObject(0).getJSONObject("functionCall").getString("name"))

    val response = geminiFunctionResponseTurn("pause", "Pausado")
    val fr = response.getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse")
    assertEquals("pause", fr.getString("name"))
    assertEquals("Pausado", fr.getJSONObject("response").getString("result"))
  }
}
