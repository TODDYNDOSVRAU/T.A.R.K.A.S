package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.PreferencesRepository
import com.example.ui.legal.LegalTexts
import com.example.ui.theme.AccentColor
import com.example.ui.theme.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingAndDataTest {

  private lateinit var prefs: PreferencesRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("tarkas_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    prefs = PreferencesRepository(context)
  }

  @Test
  fun firstLaunchHasNoAcceptedTermsAndNoFinishedSetup() {
    assertFalse(prefs.getOnboardingDone())
    assertEquals(0, prefs.getTermsVersionAccepted())
    assertTrue("the terms step must show", prefs.getTermsVersionAccepted() < LegalTexts.TERMS_VERSION)
  }

  @Test
  fun acceptingStoresTheVersionAndWhen() {
    prefs.saveTermsAccepted(LegalTexts.TERMS_VERSION, 1_700_000_000_000L)
    prefs.saveOnboardingDone(true)
    assertEquals(LegalTexts.TERMS_VERSION, prefs.getTermsVersionAccepted())
    assertEquals(1_700_000_000_000L, prefs.getTermsAcceptedAt())
    assertFalse("nothing to show once the current terms were accepted", prefs.getTermsVersionAccepted() < LegalTexts.TERMS_VERSION)
  }

  @Test
  fun anOlderAcceptanceAsksAgain() {
    prefs.saveTermsAccepted(LegalTexts.TERMS_VERSION - 1, 1L)
    prefs.saveOnboardingDone(true)
    assertTrue(prefs.getOnboardingDone() && prefs.getTermsVersionAccepted() < LegalTexts.TERMS_VERSION)
  }

  @Test
  fun exportHasTheSettingsAndEraseLeavesNothing() {
    prefs.saveThemeMode("amoled")
    prefs.saveAccent(AccentColor.LILAC.key)
    prefs.saveReduceTransparency(true)
    val exported = prefs.exportAll()
    assertEquals("amoled", exported["theme_mode"])
    assertEquals("lilac", exported["accent"])
    assertEquals(true, exported["reduce_transparency"])

    prefs.clearAll()
    assertTrue(prefs.exportAll().isEmpty())
    assertEquals("system", prefs.getThemeMode())
    assertEquals(AccentColor.ICE, AccentColor.fromKey(prefs.getAccent()))
  }

  @Test
  fun unknownAccentFallsBackToTheBrandColour() {
    assertEquals(AccentColor.ICE, AccentColor.fromKey("neon"))
    assertEquals(AccentColor.AMBER, AccentColor.fromKey("amber"))
  }

  @Test
  fun legalTextsNameTheControllerAndContact() {
    val privacy = LegalTexts.privacy.sections.joinToString(" ") { it.second }
    assertTrue(privacy.contains(LegalTexts.CONTROLLER))
    assertTrue(privacy.contains(LegalTexts.CONTACT_EMAIL))
    assertTrue("rights of art. 18 are listed", LegalTexts.privacy.sections.any { it.first.contains("18") })
    assertTrue(LegalTexts.changelog.sections.first().first == BuildConfig.VERSION_NAME)
  }

  @Test
  fun motionTokensMatchMaterialExpressive() {
    assertEquals(0.6f, Motion.FastSpatialDamping, 0f)
    assertEquals(800f, Motion.FastSpatialStiffness, 0f)
    assertEquals(0.8f, Motion.DefaultSpatialDamping, 0f)
    assertEquals(380f, Motion.DefaultSpatialStiffness, 0f)
    // Only the first screenful enters one after another, 30 ms apart: under a quarter of a second.
    assertTrue(Motion.StaggerItems * Motion.StaggerStepMs <= 250L)
  }
}
