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
import com.example.ui.components.SubPageHeader
import com.example.ui.legal.LegalDoc
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.LocalBarInset

/** About 70 characters of Body per line: past that, long legal text gets hard to follow. */
private val ReadingWidth = Grid.u384 + Grid.u192

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
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Grid.u24),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item {
      SubPageHeader(title = doc.title, backLabel = backLabel, onBack = onBack, modifier = Modifier.widthIn(max = ReadingWidth).fillMaxWidth().testTag("legal_back"))
    }
    item {
      Text(
        doc.subtitle,
        style = GridText.Small,
        color = colors.onSurfaceVariant,
        modifier = Modifier.readingColumn().padding(bottom = Grid.u6)
      )
    }
    items(doc.sections) { (heading, body) ->
      Column(Modifier.readingColumn().padding(top = Grid.u24)) {
        Text(
          heading,
          style = GridText.BodyStrong,
          color = colors.onSurface,
          modifier = Modifier.semantics { heading() }
        )
        Text(body, style = GridText.Body, color = colors.onSurface, modifier = Modifier.padding(top = Grid.u6))
      }
    }
  }
}

private fun Modifier.readingColumn(): Modifier = this
  .widthIn(max = ReadingWidth)
  .fillMaxWidth()
  .padding(horizontal = Grid.margin)
