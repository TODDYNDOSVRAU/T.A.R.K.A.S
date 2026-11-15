package com.example

import com.example.data.assistant.shouldSpeakReply
import com.example.data.assistant.speakableReply
import com.example.data.model.AssistantFailure
import com.example.data.model.AssistantMessage
import com.example.data.model.MessageSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which replies are read aloud, and how the text reads. The engine itself needs a device. */
class AssistantSpeechTest {

  private fun reply(text: String, failure: AssistantFailure? = null) =
    AssistantMessage(id = "r", sender = MessageSender.ASSISTANT, text = text, failure = failure)

  @Test
  fun markersAreDroppedAndSpacingCollapsed() {
    assertEquals("Tocando Bohemian agora", speakableReply("Tocando **Bohemian**   agora"))
    assertEquals("ok", speakableReply("  *ok*  "))
  }

  @Test
  fun onlyAVoiceQuestionsRealReplyIsSpoken() {
    assertTrue(shouldSpeakReply(askedByVoice = true, reply = reply("Pausado")))
    assertFalse(shouldSpeakReply(askedByVoice = false, reply = reply("Pausado")))
  }

  @Test
  fun errorsAndEmptyRepliesAreNotSpoken() {
    assertFalse(shouldSpeakReply(askedByVoice = true, reply = reply("", failure = AssistantFailure.SERVER)))
    assertFalse(shouldSpeakReply(askedByVoice = true, reply = reply("   ")))
    val userLine = AssistantMessage(id = "u", sender = MessageSender.USER, text = "pausa")
    assertFalse(shouldSpeakReply(askedByVoice = true, reply = userLine))
  }
}
