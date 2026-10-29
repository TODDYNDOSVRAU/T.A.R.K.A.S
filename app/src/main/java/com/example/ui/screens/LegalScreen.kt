package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.components.SubPageHeader
import com.example.ui.legal.LegalDoc
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Space

/** About 70 characters of Body per line: past that, long legal text gets hard to follow. */
private val ReadingWidth = 600.dp

/**
 * A long text to read: the Terms, the Privacy Policy, the licences, what's new. [backLabel] names the
 * page that back returns to.
 */
@Composable
fun LegalScreen(
  doc: LegalDoc,
  backLabel: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)
  val colors = MaterialTheme.colorScheme
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("legal_screen"),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item {
      SubPageHeader(title = doc.title, backLabel = backLabel, onBack = onBack, modifier = Modifier.testTag("legal_back"))
    }
    item {
      Text(
        doc.subtitle,
        style = AppText.Subhead,
        color = colors.onSurfaceVariant,
        modifier = Modifier.readingColumn().padding(bottom = Space.s)
      )
    }
    items(doc.sections) { (heading, body) ->
      Column(Modifier.readingColumn().padding(top = Space.xl)) {
        Text(
          heading,
          style = AppText.Title3,
          color = colors.onSurface,
          modifier = Modifier.semantics { heading() }
        )
        Text(body, style = AppText.Body, color = colors.onSurface, modifier = Modifier.padding(top = Space.s))
      }
    }
  }
}

private fun Modifier.readingColumn(): Modifier = this
  .widthIn(max = ReadingWidth)
  .fillMaxWidth()
  .padding(horizontal = Space.page)
