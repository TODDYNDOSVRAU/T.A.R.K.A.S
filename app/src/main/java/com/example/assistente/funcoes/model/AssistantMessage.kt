package com.example.data.model

enum class MessageSender {
  USER,
  ASSISTANT,
  TOOL,
  SYSTEM
}

data class ToolExecutionReport(
  val toolName: String,
  val arguments: Map<String, String>,
  val success: Boolean,
  val resultSummary: String
)

data class AssistantMessage(
  val id: String,
  val sender: MessageSender,
  val text: String,
  val timestampMs: Long = System.currentTimeMillis(),
  val toolReport: ToolExecutionReport? = null,
  // Set when the reply is an error message; the screen offers "try again" when the failure allows it.
  val failure: AssistantFailure? = null
)
