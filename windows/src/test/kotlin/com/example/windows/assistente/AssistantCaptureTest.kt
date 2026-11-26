package com.example.windows.assistente

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.windows.res.*
import com.example.ui.theme.TarkasTheme
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Evidence and layout checks for the Assistente. Captures are the window only (captureToImage, never the display),
 * at the four desktop sizes in light and dark; they are written only when `ASSISTANT_CAPTURE_DIR` is set, so the
 * normal suite stays fast. The measurements and the scroll and error checks always run.
 */
@OptIn(ExperimentalTestApi::class)
class AssistantCaptureTest {
  private val sizes = listOf(1024 to 640, 1366 to 768, 1920 to 1080, 2560 to 1440)
  private val themes = listOf("light", "dark")

  private fun msg(i: Int, sender: MessageSender, text: String, failure: AssistantFailure? = null, report: ToolExecutionReport? = null) =
    AssistantMessage("m$i", sender, text, 1_700_000_000_000L + i * 60_000L, report, failure)

  private fun conversation(): List<AssistantMessage> = listOf(
    msg(1, MessageSender.USER, "Qual música está tocando agora?"),
    msg(2, MessageSender.ASSISTANT, "Tocando **Aqualung** de Jethro Tull, do álbum Aqualung.", report = ToolExecutionReport("get_current_track", emptyMap(), true, "Aqualung")),
    msg(3, MessageSender.USER, "Conte uma história sobre o álbum"),
    msg(4, MessageSender.ASSISTANT, "", failure = AssistantFailure.NO_CONNECTION),
    msg(5, MessageSender.USER, "Conte uma história sobre o álbum"),
    msg(6, MessageSender.ASSISTANT, "", failure = AssistantFailure.KEY_REFUSED),
    msg(7, MessageSender.USER, "Conte uma história sobre o álbum"),
    msg(8, MessageSender.ASSISTANT, "", failure = AssistantFailure.QUOTA)
  )

  private fun DesktopComposeUiTest.show(
    theme: String, messages: List<AssistantMessage>, processing: Boolean = false, reduced: Boolean = true,
    onSend: (String) -> Unit = {}
  ) = setContent {
    TarkasTheme(themeMode = theme) {
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AssistantScreen(
          messages = messages, isProcessing = processing, hasGeminiKey = true, onlineAi = true, isListening = false,
          voiceError = null, activeTimers = emptyList(), playback = AssistantPlayback.NONE, reduced = reduced,
          onSend = onSend, onMic = {}, onCancelTimer = {}, onRetry = {}, onSetOnlineAi = {}, onClear = {},
          onSaveKey = { true }, onClearKey = {}
        )
      }
    }
  }

  /**
   * Text nodes whose text is cut: characters after the last visible one, or a layout larger than its box.
   * `hasVisualOverflow` is not used (it is true for every text on this desktop build, even a plain label). History previews cut on purpose with an ellipsis and are skipped.
   */
  private fun DesktopComposeUiTest.clippedTexts(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)).fetchSemanticsNodes().mapNotNull { node ->
      var inHistory = false
      var p: androidx.compose.ui.semantics.SemanticsNode? = node
      while (p != null) {
        if (p.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) == "assistant_history_pane") inHistory = true
        p = p.parent
      }
      if (inHistory) return@mapNotNull null
      val results = mutableListOf<TextLayoutResult>()
      node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
      val r = results.firstOrNull() ?: return@mapNotNull null
      // The last visible character comes before the end of the text when lines were dropped or ellipsized.
      val cut = r.getLineEnd(r.lineCount - 1, visibleEnd = true) < r.layoutInput.text.text.trimEnd().length
      val larger = r.size.width > node.size.width + 1 || r.size.height > node.size.height + 1
      if (cut || larger) "${r.layoutInput.text.text.take(40)} (cut=$cut layout=${r.size} box=${node.size})" else null
    }

  private fun contrast(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
  }

  @Test fun captureEmptyAndChat() {
    val dir = System.getProperty("assistant.capture.dir") ?: System.getenv("ASSISTANT_CAPTURE_DIR")
    assumeTrue(dir != null)
    val out = File(dir!!).also { it.mkdirs() }
    for ((w, h) in sizes) for (theme in themes) for ((name, list) in listOf("empty" to emptyList(), "chat" to conversation())) {
      runDesktopComposeUiTest(width = w, height = h) {
        show(theme, list)
        waitForIdle()
        val image = onRoot().captureToImage().toAwtImage()
        assertTrue(ImageIO.write(image, "png", File(out, "assistente-${w}x$h-$theme-$name.png")))
      }
    }
  }

  @Test fun layoutMatchesTheSpecAtTheFourSizes() {
    for ((w, h) in sizes) for (theme in themes) runDesktopComposeUiTest(width = w, height = h) {
      show(theme, emptyList())
      waitForIdle()
      val label = "${w}x$h $theme"
      val bar = onNodeWithTag("assistant_status_pill").getUnclippedBoundsInRoot()
      assertTrue("$label: status pill height ${bar.height}", bar.height >= 40.dp)
      val chat = onNodeWithTag("assistant_chat_column").getUnclippedBoundsInRoot()
      assertTrue("$label: chat width ${chat.width}", chat.width <= 760.dp)
      if (w >= 1366) assertEquals("$label: chat width", 760.dp.value, chat.width.value, 0.5f)
      val capsule = onNodeWithTag("assistant_capsule").getUnclippedBoundsInRoot()
      assertTrue("$label: capsule inside the chat column", capsule.width <= chat.width)
      for (tag in listOf("assistant_quick_button", "siri_voice_orb", "siri_send_button")) {
        val b = onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertEquals("$label: $tag width", 40f, b.width.value, 0.5f)
        assertEquals("$label: $tag height", 40f, b.height.value, 0.5f)
      }
      val cards = onAllNodes(SemanticsMatcher("card") { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("assistant_card_") == true })
        .fetchSemanticsNodes()
      assertEquals("$label: cards", 4, cards.size)
      assertEquals("$label: cards in one row of three plus one", 2, cards.map { it.boundsInRoot.top }.distinct().size)
      val root = onRoot().getUnclippedBoundsInRoot()
      for (c in cards) assertTrue("$label: card inside the window", c.boundsInRoot.right <= root.width.value && c.boundsInRoot.bottom <= root.height.value)
      assertEquals("$label: clipped texts", emptyList<String>(), clippedTexts())
    }
  }

  @Test fun theCutTextDetectorSeesAnEllipsis() = runDesktopComposeUiTest(width = 300, height = 200) {
    setContent {
      androidx.compose.foundation.layout.Box(Modifier.androidx_width()) {
        androidx.compose.material3.Text("a very long line that cannot fit in one hundred dp", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
      }
    }
    waitForIdle()
    assertTrue(clippedTexts().isNotEmpty())
  }

  private fun Modifier.androidx_width() = this.then(Modifier.width(100.dp))

  @Test fun conversationHasNoCutTextAtTheFourSizes() {
    for ((w, h) in sizes) for (theme in themes) runDesktopComposeUiTest(width = w, height = h) {
      show(theme, conversation())
      waitForIdle()
      assertEquals("${w}x$h $theme: clipped texts", emptyList<String>(), clippedTexts())
    }
  }

  @Test fun compactWidthUsesTheSmallButtonsAndTwoColumns() = runDesktopComposeUiTest(width = 480, height = 800) {
    show("dark", emptyList())
    waitForIdle()
    for (tag in listOf("assistant_quick_button", "siri_voice_orb", "siri_send_button")) {
      val b = onNodeWithTag(tag).getUnclippedBoundsInRoot()
      assertEquals("$tag width", 36f, b.width.value, 0.5f)
    }
    val cards = onAllNodes(SemanticsMatcher("card") { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("assistant_card_") == true })
      .fetchSemanticsNodes()
    assertEquals(4, cards.size)
    assertEquals("two columns means two rows", 2, cards.map { it.boundsInRoot.top }.distinct().size)
    assertEquals(2, cards.count { it.boundsInRoot.top == cards.minOf { c -> c.boundsInRoot.top } })
  }

  @Test fun textColoursHaveReadableContrastInBothThemes() {
    for (theme in themes) runDesktopComposeUiTest(width = 1024, height = 640) {
      var pairs = emptyList<Triple<String, Color, Color>>()
      setContent {
        TarkasTheme(themeMode = theme) {
          val c = MaterialTheme.colorScheme
          pairs = listOf(
            Triple("onSurface/background", c.onSurface, c.background),
            Triple("onSurface/surfaceContainer", c.onSurface, c.surfaceContainer),
            Triple("onSurfaceVariant/background", c.onSurfaceVariant, c.background),
            Triple("onSurfaceVariant/surfaceContainer", c.onSurfaceVariant, c.surfaceContainer),
            Triple("onPrimary/primary", c.onPrimary, c.primary),
            Triple("primary/background", c.primary, c.background),
            Triple("error/background", c.error, c.background)
          )
        }
      }
      waitForIdle()
      for ((name, fg, bg) in pairs) assertTrue("$theme $name contrast ${contrast(fg, bg)}", contrast(fg, bg) >= 4.5)
    }
  }

  @Test fun everySendScrollsToTheLastMessageEvenAtTheHistoryLimit() = runDesktopComposeUiTest(width = 1366, height = 768) {
    var list by mutableStateOf(List(ASSISTANT_HISTORY_LIMIT) { i -> msg(i, if (i % 2 == 0) MessageSender.USER else MessageSender.ASSISTANT, "message number $i") })
    setContent {
      TarkasTheme(themeMode = "dark") {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
          AssistantScreen(
            messages = list, isProcessing = false, hasGeminiKey = true, onlineAi = true, isListening = false,
            voiceError = null, activeTimers = emptyList(), playback = AssistantPlayback.NONE, reduced = false,
            onSend = {}, onMic = {}, onCancelTimer = {}, onRetry = {}, onSetOnlineAi = {}, onClear = {},
            onSaveKey = { true }, onClearKey = {}
          )
        }
      }
    }
    waitForIdle()
    onNodeWithText("message number ${ASSISTANT_HISTORY_LIMIT - 1}").assertIsDisplayed()
    // Same size as before (the oldest drops out), the way AssistenteState caps the history.
    list = (list + msg(1000, MessageSender.USER, "sent at the limit")).takeLast(ASSISTANT_HISTORY_LIMIT)
    assertEquals(ASSISTANT_HISTORY_LIMIT, list.size)
    waitForIdle()
    onNodeWithText("sent at the limit").assertIsDisplayed()
    list = (list + msg(1001, MessageSender.ASSISTANT, "reply at the limit")).takeLast(ASSISTANT_HISTORY_LIMIT)
    waitForIdle()
    onNodeWithText("reply at the limit").assertIsDisplayed()
  }

  @Test fun failedRepliesNameTheCauseInsteadOfNotUnderstood() = runDesktopComposeUiTest(width = 1366, height = 768) {
    for (failure in AssistantFailure.entries) {
      runDesktopComposeUiTest(width = 1366, height = 768) {
        show("light", listOf(msg(1, MessageSender.USER, "pergunta"), msg(2, MessageSender.ASSISTANT, "", failure)))
        waitForIdle()
        val text = ResourceTexts.text(failureResource(failure))
        assertTrue("$failure has a text", text.isNotBlank())
        assertTrue("$failure is not the generic reply", text != ResourceTexts.text(Res.string.assistant_not_understood))
        onNodeWithText(text.take(30), substring = true).assertIsDisplayed()
      }
    }
  }
}
