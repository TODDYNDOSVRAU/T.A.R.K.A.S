package androidx.compose.ui.platform

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/** Desktop twin of the Android Configuration: the shared legal texts only read `locales[0].language`. */
class Configuration(val locales: List<Locale>)

/** The language the screens show. [com.example.windows.geral.AppLanguage] provides it; the default is the system's. */
val LocalConfiguration = compositionLocalOf { Configuration(listOf(Locale.getDefault())) }
