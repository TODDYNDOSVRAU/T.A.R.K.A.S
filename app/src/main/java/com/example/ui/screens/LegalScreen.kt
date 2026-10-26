package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.legal.LegalDoc
import com.example.ui.theme.LocalBarInset

/** A long text to read: the Terms, the Privacy Policy, the licences, what's new. */
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
    modifier = modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("legal_screen"),
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp + LocalBarInset.current),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Column {
        Row(
          modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onBack)
            .padding(end = 12.dp)
            .testTag("legal_back"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.ChevronLeft, contentDescription = "Voltar", tint = colors.primary, modifier = Modifier.size(32.dp))
          Text(backLabel, fontSize = 20.sp, color = colors.primary)
        }
        Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = colors.onBackground, modifier = Modifier.padding(top = 4.dp))
        Text(doc.subtitle, fontSize = 14.sp, lineHeight = 19.sp, color = colors.onSurfaceVariant)
      }
    }
    items(doc.sections) { (heading, body) ->
      Column {
        Text(heading, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.onSurface)
        Text(body, fontSize = 15.sp, lineHeight = 22.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
      }
    }
  }
}
