package com.example.data.assistant

import com.example.data.model.AssistantFailure
import com.example.data.model.AssistantMessage
import com.example.data.model.MessageSender
import com.example.data.model.ToolExecutionReport
import org.json.JSONArray
import org.json.JSONObject

/** The conversation keeps at most this many messages on disk; the oldest are dropped first. */
internal const val ASSISTANT_HISTORY_LIMIT = 200

/** Serialises the newest [ASSISTANT_HISTORY_LIMIT] messages, keeping every field a message needs to show again. */
internal fun assistantHistoryToJson(messages: List<AssistantMessage>): String {
  val array = JSONArray()
  messages.takeLast(ASSISTANT_HISTORY_LIMIT).forEach { message ->
    array.put(JSONObject().apply {
      put("id", message.id)
      put("sender", message.sender.name)
      put("text", message.text)
      put("timestampMs", message.timestampMs)
      message.failure?.let { put("failure", it.name) }
      message.toolReport?.let { report ->
        val arguments = JSONObject()
        report.arguments.forEach { (key, value) -> arguments.put(key, value) }
        put("tool", JSONObject()
          .put("toolName", report.toolName)
          .put("arguments", arguments)
          .put("success", report.success)
          .put("resultSummary", report.resultSummary))
      }
    })
  }
  return array.toString()
}

/**
 * Reads a conversation written by [assistantHistoryToJson]. A message with an unknown sender or failure is
 * skipped; text that is not a conversation at all gives an empty list, never an exception.
 */
internal fun assistantHistoryFromJson(json: String): List<AssistantMessage> {
  val array = try {
    JSONArray(json)
  } catch (_: Exception) {
    return emptyList()
  }
  val messages = mutableListOf<AssistantMessage>()
  for (i in 0 until array.length()) {
    val item = array.optJSONObject(i) ?: continue
    val sender = MessageSender.values().firstOrNull { it.name == item.optString("sender") } ?: continue
    val failureName = item.optString("failure", "")
    val failure = if (failureName.isEmpty()) null else {
      AssistantFailure.values().firstOrNull { it.name == failureName } ?: continue
    }
    val report = item.optJSONObject("tool")?.let { tool ->
      val arguments = tool.optJSONObject("arguments")
      val map = mutableMapOf<String, String>()
      arguments?.keys()?.forEach { key -> map[key] = arguments.optString(key) }
      ToolExecutionReport(
        toolName = tool.optString("toolName"),
        arguments = map,
        success = tool.optBoolean("success"),
        resultSummary = tool.optString("resultSummary")
      )
    }
    messages += AssistantMessage(
      id = item.optString("id"),
      sender = sender,
      text = item.optString("text"),
      timestampMs = item.optLong("timestampMs", 0L),
      toolReport = report,
      failure = failure
    )
  }
  return messages
}
