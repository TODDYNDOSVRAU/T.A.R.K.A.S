package com.example

import com.example.data.assistant.ASSISTANT_HISTORY_LIMIT
import com.example.data.assistant.assistantHistoryFromJson
import com.example.data.assistant.assistantHistoryToJson
import com.example.data.model.AssistantFailure
import com.example.data.model.AssistantMessage
import com.example.data.model.MessageSender
import com.example.data.model.ToolExecutionReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The saved conversation: round trip, the 200-message cap, and input that is not a conversation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AssistantHistoryTest {

  private fun message(i: Int, sender: MessageSender = MessageSender.USER) =
    AssistantMessage(id = "m$i", sender = sender, text = "texto $i", timestampMs = 1_000L + i)

  @Test
  fun roundTripKeepsEveryField() {
    val report = ToolExecutionReport(
      toolName = "set_preset",
      arguments = mapOf("preset" to "rock"),
      success = true,
      resultSummary = "Aplicado"
    )
    val original = listOf(
      message(1),
      AssistantMessage(
        id = "m2", sender = MessageSender.ASSISTANT, text = "Feito", timestampMs = 2_000L, toolReport = report
      ),
      AssistantMessage(
        id = "m3", sender = MessageSender.ASSISTANT, text = "Falhou", timestampMs = 3_000L,
        failure = AssistantFailure.QUOTA
      )
    )

    val restored = assistantHistoryFromJson(assistantHistoryToJson(original))

    assertEquals(original, restored)
  }

  @Test
  fun keepsOnlyTheNewestMessages() {
    val all = (0 until ASSISTANT_HISTORY_LIMIT + 50).map { message(it) }

    val restored = assistantHistoryFromJson(assistantHistoryToJson(all))

    assertEquals(ASSISTANT_HISTORY_LIMIT, restored.size)
    assertEquals("m50", restored.first().id)
    assertEquals("m${ASSISTANT_HISTORY_LIMIT + 49}", restored.last().id)
  }

  @Test
  fun textThatIsNotAConversationGivesNoMessages() {
    assertTrue(assistantHistoryFromJson("not json").isEmpty())
    assertTrue(assistantHistoryFromJson("{}").isEmpty())
    assertTrue(assistantHistoryFromJson("").isEmpty())
  }

  @Test
  fun messagesWithAnUnknownSenderOrFailureAreSkipped() {
    val json = """
      [
        {"id":"a","sender":"ROBOT","text":"x","timestampMs":1},
        {"id":"b","sender":"USER","text":"ok","timestampMs":2},
        {"id":"c","sender":"ASSISTANT","text":"y","timestampMs":3,"failure":"NOT_A_FAILURE"}
      ]
    """.trimIndent()

    val restored = assistantHistoryFromJson(json)

    assertEquals(listOf("b"), restored.map { it.id })
  }
}
