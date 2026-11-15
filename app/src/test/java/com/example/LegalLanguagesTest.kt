package com.example

import com.example.ui.legal.LegalDoc
import com.example.ui.legal.LegalTexts
import com.example.ui.screens.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The English legal texts are a translation: they must keep the shape and the facts of the Portuguese ones. */
class LegalLanguagesTest {
  private val pt = LegalTexts.forLanguage("pt")
  private val en = LegalTexts.forLanguage("en")

  @Test
  fun `portuguese is the original and every other language reads english`() {
    assertSame(LegalTexts.terms, pt.terms)
    assertSame(pt, LegalTexts.forLanguage("PT"))
    assertSame(en, LegalTexts.forLanguage("es"))
    assertSame(en, LegalTexts.forLanguage(""))
    assertEquals("Terms of Use", en.terms.title)
  }

  @Test
  fun `both languages have the same sections`() {
    fun same(a: LegalDoc, b: LegalDoc) = assertEquals(a.title, a.sections.size, b.sections.size)
    same(pt.terms, en.terms)
    same(pt.privacy, en.privacy)
    same(pt.changelog, en.changelog)
    same(pt.licenses, en.licenses)
    assertEquals(pt.summary.size, en.summary.size)
    assertEquals(pt.credits.size, en.credits.size)
  }

  @Test
  fun `what's new lists the same versions in the same order`() {
    assertEquals(pt.changelog.sections.map { it.first }, en.changelog.sections.map { it.first })
    assertEquals(BuildConfig.VERSION_NAME, en.changelog.sections.first().first)
  }

  @Test
  fun `the english privacy policy names the controller, the contact and the same version`() {
    val body = en.privacy.sections.joinToString("\n") { it.second }
    assertTrue(body.contains(LegalTexts.CONTROLLER))
    assertTrue(body.contains(LegalTexts.CONTACT_EMAIL))
    assertTrue(en.privacy.subtitle.contains("${LegalTexts.TERMS_VERSION}"))
    assertTrue(en.terms.subtitle.contains("${LegalTexts.TERMS_VERSION}"))
    assertTrue(en.terms.subtitle.contains("Portuguese version prevails"))
  }

  @Test
  fun `no section is empty and the english text has no portuguese accents`() {
    val all = listOf(en.terms, en.privacy, en.changelog, en.licenses).flatMap { it.sections } + en.credits
    all.forEach { (title, body) ->
      assertTrue(title, title.isNotBlank() && body.isNotBlank())
      assertFalse(title, Regex("[ãõçáéíóúâêô]").containsMatchIn(title + body))
    }
  }

  @Test
  fun `a language choice is read from the tags android keeps`() {
    assertEquals(AppLanguage.SYSTEM, AppLanguage.choiceOf(""))
    assertEquals("pt", AppLanguage.choiceOf("pt-BR"))
    assertEquals("pt", AppLanguage.choiceOf("pt"))
    assertEquals("en", AppLanguage.choiceOf("en-US,pt-BR"))
    assertEquals(AppLanguage.SYSTEM, AppLanguage.choiceOf("es-ES"))
  }
}
