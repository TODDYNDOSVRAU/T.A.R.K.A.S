package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.diagnostics.Diagnostics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagnosticsReportTest {
  @Test
  fun `install chains the crash handler and the report names the version without the key`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val before = Thread.getDefaultUncaughtExceptionHandler()
    try {
      Diagnostics.install(context)
      assertNotSame(before, Thread.getDefaultUncaughtExceptionHandler())
      val report = Diagnostics.report(context)
      assertTrue(report.contains("T.A.R.K.A.S. diagnostics"))
      assertTrue(report.contains(BuildConfig.VERSION_NAME))
      assertTrue(report.contains("== Crashes"))
      assertTrue(report.contains("== Events"))
      if (BuildConfig.GEMINI_API_KEY.length >= 6) assertFalse(report.contains(BuildConfig.GEMINI_API_KEY))
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(before)
    }
  }
}
