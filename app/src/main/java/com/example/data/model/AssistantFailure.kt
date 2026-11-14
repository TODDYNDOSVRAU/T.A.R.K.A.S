package com.example.data.model

import androidx.annotation.StringRes
import com.example.R

// Why the online reply did not reach the screen. Each case has its own text and says whether
// trying again can help. The text itself is resolved from resources, in EN and PT.
enum class AssistantFailure(@StringRes val textRes: Int, val retryable: Boolean) {
  NO_CONNECTION(R.string.assistant_error_no_connection, retryable = true),
  // The key is part of the build; trying again with the same key cannot succeed.
  KEY_REFUSED(R.string.assistant_error_key_refused, retryable = false),
  QUOTA(R.string.assistant_error_quota, retryable = true),
  // finishReason MAX_TOKENS: the partial text is discarded instead of shown cut in half.
  CUT_OFF(R.string.assistant_error_cut_off, retryable = true),
  NO_REPLY(R.string.assistant_error_no_reply, retryable = true),
  SERVER(R.string.assistant_error_server, retryable = true)
}
