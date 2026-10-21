package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Curiosity
import com.example.data.model.Track
import com.example.ui.components.CuriosityDetailDialog
import com.example.ui.theme.SpaceGrotesk
import com.example.ui.viewmodel.TarkasNavTab

@Composable
fun HomeScreen(
  currentTrack: Track?,
  tracks: List<Track>,
  isPlaying: Boolean,
  waveformEnergy: Float,
  activeCuriosity: Curiosity?,
  allCuriosities: List<Curiosity>,
  onPlayTrack: (Track) -> Unit,
  onTogglePlayPause: () -> Unit,
  onNextTrack: () -> Unit,
  onPreviousTrack: () -> Unit,
  onNavigateToTab: (TarkasNavTab) -> Unit,
  onSearchAudius: (String) -> Unit,
  onAddLocalTrack: (String, String, String, Long) -> Unit = { _, _, _, _ -> },
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("Todas") }
  var selectedCuriosityForDialog by remember { mutableStateOf<Curiosity?>(null) }
  var showActiveCuriosityDialog by remember { mutableStateOf(false) }

  // SAF File Picker for local audio files
  val audioPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    uri?.let {
      var displayName = "Áudio Local"
      try {
        context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIndex != -1 && cursor.moveToFirst()) {
            displayName = cursor.getString(nameIndex)
          }
        }
      } catch (_: Exception) {}
      onAddLocalTrack(displayName, "Biblioteca Local", it.toString(), 180000L)
    }
  }

  // Filter tracks by category and search query
  val filteredTracks = tracks.filter { track ->
    val matchesCategory = when (selectedCategory) {
      "Audius" -> track.album.contains("Audius", ignoreCase = true)
      "Locais" -> track.isLocalFile
      "Clássicos" -> !track.isLocalFile && !track.album.contains("Audius", ignoreCase = true)
      else -> true
    }
    val matchesSearch = if (searchQuery.isBlank()) {
      true
    } else {
      track.title.contains(searchQuery, ignoreCase = true) ||
          track.artist.contains(searchQuery, ignoreCase = true) ||
          track.album.contains(searchQuery, ignoreCase = true)
    }
    matchesCategory && matchesSearch
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {

    // 1. Apple Music Large Library Header
    item {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Biblioteca",
              fontFamily = SpaceGrotesk,
              fontWeight = FontWeight.Bold,
              fontSize = 32.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
            Text(
              text = "${tracks.size} faixas na sua biblioteca",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          // Import Local Audio Button (Apple Music style)
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
              .clickable { audioPickerLauncher.launch("audio/*") }
              .testTag("home_add_local_audio")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Importar Áudio",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
              )
              Text(
                text = "Importar",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar (Clean Apple Music style search pill)
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Buscar na biblioteca...", fontSize = 13.sp) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "Buscar",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Limpar busca",
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            focusedBorderColor = MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Category Filter Pills
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val categories = listOf("Todas", "Clássicos", "Audius", "Locais")
          items(categories) { cat ->
            val selected = selectedCategory == cat
            FilterChip(
              selected = selected,
              onClick = { selectedCategory = cat },
              label = {
                Text(
                  text = cat,
                  fontSize = 12.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
              )
            )
          }
        }
      }
    }

    // 2. Clean, Minimal Curiosity Pill (Shown ONLY on Home, click opens full modal)
    if (activeCuriosity != null) {
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showActiveCuriosityDialog = true }
            .testTag("home_curiosity_pill")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
              )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Curiosidade: ${activeCuriosity.trackTitle}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                text = activeCuriosity.shortTrivia,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }

            Text(
              text = "Ver",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(start = 8.dp)
            )
          }
        }
      }
    }

    // Curiosidades Musicais Carousel (Only shown here on Home)
    if (allCuriosities.isNotEmpty()) {
      item {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Curiosidades Musicais",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
              )
            }
            Text(
              text = "Toque para abrir",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            items(allCuriosities) { cur ->
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                  .width(220.dp)
                  .clickable { selectedCuriosityForDialog = cur }
                  .testTag("curiosity_card_${cur.trackId}")
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Surface(
                      shape = RoundedCornerShape(6.dp),
                      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                      Text(
                        text = cur.artist,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                    Text(
                      text = "Ler",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary
                    )
                  }

                  Spacer(modifier = Modifier.height(6.dp))

                  Text(
                    text = cur.trackTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                  )

                  Spacer(modifier = Modifier.height(4.dp))

                  Text(
                    text = cur.shortTrivia,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                  )
                }
              }
            }
          }
        }
      }
    }

    // 3. Tocadas Recentemente / Destaques (Apple Music horizontal album cards)
    if (searchQuery.isBlank()) {
      item {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Tocadas Recentemente",
              fontFamily = SpaceGrotesk,
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp,
              color = MaterialTheme.colorScheme.onBackground
            )

            Text(
              text = "Ver Player",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.clickable { onNavigateToTab(TarkasNavTab.MUSIC) }
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            items(tracks.take(6)) { track ->
              val isCurrent = currentTrack?.id == track.id
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                  .width(135.dp)
                  .clickable { onPlayTrack(track) }
                  .testTag("featured_track_${track.id}")
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Box(
                    modifier = Modifier
                      .size(115.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(Color(track.colorHex)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = if (isCurrent && isPlaying) Icons.Default.Equalizer else Icons.Default.MusicNote,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(40.dp)
                    )
                  }

                  Spacer(modifier = Modifier.height(8.dp))

                  Text(
                    text = track.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = track.artist,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        }
      }
    }

    // 4. Músicas da Biblioteca (Clean Apple Music Tracklist)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (searchQuery.isNotBlank()) "Resultados da Busca" else "Músicas",
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = MaterialTheme.colorScheme.onBackground
        )

        Text(
          text = "${filteredTracks.size} faixas",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    if (filteredTracks.isEmpty()) {
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              imageVector = Icons.Default.FolderOpen,
              contentDescription = null,
              modifier = Modifier.size(44.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = if (searchQuery.isNotBlank()) "Nenhuma música encontrada para \"$searchQuery\"" else "Nenhuma música nesta categoria",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    items(filteredTracks) { track ->
      val isSelected = currentTrack?.id == track.id
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onPlayTrack(track) }
          .testTag("lib_track_${track.id}")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Album thumbnail
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Color(track.colorHex)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isSelected && isPlaying) Icons.Default.Equalizer else Icons.Default.MusicNote,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          // Title & Artist
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = track.title,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              fontSize = 14.sp,
              color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${track.artist} • ${track.album}",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          // Duration
          Text(
            text = track.formattedDuration(),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
          )
        }
      }
    }
  }

  // Curiosity Detail Dialog when clicked (shown only on demand)
  if (showActiveCuriosityDialog && activeCuriosity != null) {
    CuriosityDetailDialog(
      curiosity = activeCuriosity,
      onDismiss = { showActiveCuriosityDialog = false }
    )
  }

  selectedCuriosityForDialog?.let { cur ->
    CuriosityDetailDialog(
      curiosity = cur,
      onDismiss = { selectedCuriosityForDialog = null }
    )
  }
}
