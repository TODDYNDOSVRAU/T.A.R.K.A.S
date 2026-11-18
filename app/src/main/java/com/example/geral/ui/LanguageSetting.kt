package com.example.ui.screens

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.components.GroupDivider
import com.example.ui.components.RadioRow

/**
 * The app's own language, kept by Android (13 or newer) and not by the app: the same choice shows in
 * the system's "App languages" page, and Android restarts the screen when it changes. Below Android
 * 13 there is no such setting and the app follows the system language.
 */
internal object AppLanguage {
  /** Follow the system language. */
  const val SYSTEM = ""

  /** The languages the app is written in, as in res/xml/locales_config.xml. */
  val written = listOf("en", "pt")

  val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

  /**
   * The choice a list of language tags stands for: [SYSTEM] when the list is empty or starts with a
   * language the app is not written in, otherwise the language code alone ("pt-BR" is "pt").
   */
  fun choiceOf(languageTags: String): String {
    val language = languageTags.substringBefore(',').trim().substringBefore('-').substringBefore('_').lowercase()
    return if (language in written) language else SYSTEM
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  fun current(context: Context): String =
    choiceOf(context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  fun set(context: Context, choice: String) {
    val locales = if (choice == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(choice)
    context.getSystemService(LocaleManager::class.java).applicationLocales = locales
  }
}

/** The language as radio rows; put inside an [InsetGroup]. Call only where [AppLanguage.supported]. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun LanguageOptionRows() {
  val context = LocalContext.current
  var choice by remember { mutableStateOf(AppLanguage.current(context)) }
  val options = listOf(
    AppLanguage.SYSTEM to stringResource(R.string.settings_language_system),
    "pt" to stringResource(R.string.settings_language_pt),
    "en" to stringResource(R.string.settings_language_en)
  )
  Column(Modifier.selectableGroup()) {
    options.forEachIndexed { i, (key, label) ->
      if (i > 0) GroupDivider()
      RadioRow(
        title = label,
        selected = choice == key,
        onClick = {
          choice = key
          AppLanguage.set(context, key)
        },
        modifier = Modifier.testTag("language_${key.ifEmpty { "system" }}")
      )
    }
  }
}
