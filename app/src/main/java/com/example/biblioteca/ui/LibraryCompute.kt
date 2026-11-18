package com.example.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The result of [compute], made on [Dispatchers.Default] again whenever [keys] change, and null until the
 * first one is ready. The previous result stays on screen while the next is made, so a list never blinks.
 */
@Composable
internal fun <T : Any> rememberComputed(vararg keys: Any?, compute: () -> T): T? {
  val latest by rememberUpdatedState(compute)
  var result by remember { mutableStateOf<T?>(null) }
  LaunchedEffect(*keys) { result = withContext(Dispatchers.Default) { latest() } }
  return result
}
