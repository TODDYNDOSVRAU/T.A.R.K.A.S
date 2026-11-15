package com.example

import com.example.data.assistant.AssistantFailureException
import com.example.data.assistant.failureForHttpStatus
import com.example.data.assistant.geminiReplyText
import com.example.data.model.AssistantFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Each path where the online reply fails has a test: HTTP status, finish reason, empty or broken body.
// The network call itself (no connection) is not covered here; it needs a server stub.
@RunWith(RobolectricTestRunner::class)
class AssistantGeminiErrorsTest {

  private fun assertFailure(expected: AssistantFailure, body: String) {
    try {
      geminiReplyText(body)
      fail("expected $expected, got a reply")
    } catch (e: AssistantFailureException) {
      assertEquals(expected, e.failure)
    }
  }

  @Test
  fun refusedKeyStatusesMapToKeyRefused() {
    assertEquals(AssistantFailure.KEY_REFUSED, failureForHttpStatus(401))
    assertEquals(AssistantFailure.KEY_REFUSED, failureForHttpStatus(403))
  }

  @Test
  fun quotaStatusMapsToQuota() {
    assertEquals(AssistantFailure.QUOTA, failureForHttpStatus(429))
  }

  @Test
  fun otherErrorStatusesMapToServer() {
    assertEquals(AssistantFailure.SERVER, failureForHttpStatus(400))
    assertEquals(AssistantFailure.SERVER, failureForHttpStatus(500))
    assertEquals(AssistantFailure.SERVER, failureForHttpStatus(503))
  }

  @Test
  fun completeReplyReturnsTrimmedText() {
    val body = """{"candidates":[{"content":{"parts":[{"text":"  Tocando agora.  "}]},"finishReason":"STOP"}]}"""
    assertEquals("Tocando agora.", geminiReplyText(body))
  }

  @Test
  fun maxTokensIsCutOffEvenWhenPartTextExists() {
    val body = """{"candidates":[{"content":{"parts":[{"text":"A música que você pediu começa"}]},"finishReason":"MAX_TOKENS"}]}"""
    assertFailure(AssistantFailure.CUT_OFF, body)
  }

  @Test
  fun noCandidatesIsNoReply() {
    assertFailure(AssistantFailure.NO_REPLY, """{"candidates":[]}""")
    assertFailure(AssistantFailure.NO_REPLY, """{"promptFeedback":{"blockReason":"SAFETY"}}""")
  }

  @Test
  fun safetyStopWithoutTextIsNoReply() {
    val body = """{"candidates":[{"finishReason":"SAFETY"}]}"""
    assertFailure(AssistantFailure.NO_REPLY, body)
  }

  @Test
  fun blankTextIsNoReply() {
    val body = """{"candidates":[{"content":{"parts":[{"text":"   "}]},"finishReason":"STOP"}]}"""
    assertFailure(AssistantFailure.NO_REPLY, body)
  }

  @Test
  fun malformedBodyIsNoReply() {
    assertFailure(AssistantFailure.NO_REPLY, "not json at all")
  }

  @Test
  fun onlyTheRetryableFailuresOfferTryAgain() {
    assertFalse(AssistantFailure.KEY_REFUSED.retryable)
    assertTrue(AssistantFailure.NO_CONNECTION.retryable)
    assertTrue(AssistantFailure.QUOTA.retryable)
    assertTrue(AssistantFailure.CUT_OFF.retryable)
    assertTrue(AssistantFailure.NO_REPLY.retryable)
    assertTrue(AssistantFailure.SERVER.retryable)
  }

  @Test
  fun everyFailureHasItsOwnText() {
    val texts = AssistantFailure.values().map { it.textRes }.toSet()
    assertEquals(AssistantFailure.values().size, texts.size)
  }
}
