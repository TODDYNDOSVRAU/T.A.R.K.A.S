package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.AudioCapabilities
import com.example.data.model.CuriosityConfig
import com.example.ui.components.TarkasSectionTitle
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.SurfaceStyle
import com.example.ui.theme.glassContentColor
import com.example.ui.theme.glassControl
import com.example.ui.components.GlassChip
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Check

@Composable
fun SettingsScreen(
  themeMode: String,
  capabilities: AudioCapabilities,
  onThemeModeChange: (String) -> Unit,
  onBackToHome: () -> Unit,
  curiosityConfig: CuriosityConfig? = null,
  onOpenCuriositySettings: () -> Unit = {},
  hasGeminiKey: Boolean = false,
  surfaceStyle: SurfaceStyle = SurfaceStyle.SOLID,
  onSurfaceStyleChange: (SurfaceStyle) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp + LocalBarInset.current),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {

    // Appearance
    item { SectionTitle("Aparência") }
    item {
      SettingsGroup(modifier = Modifier.testTag("settings_theme_card")) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val modes = listOf(
            Triple("amoled", "AMOLED", Icons.Default.Brightness1),
            Triple("dark", "Escuro", Icons.Default.DarkMode),
            Triple("light", "Claro", Icons.Default.LightMode),
            Triple("system", "Sistema", Icons.Default.SettingsBrightness)
          )
          modes.forEach { (modeKey, label, icon) ->
            val selected = themeMode == modeKey
            Column(
              modifier = Modifier
                .weight(1f)
                .glassControl(RoundedCornerShape(18.dp), selected = selected)
                .clickable { onThemeModeChange(modeKey) }
                .padding(vertical = 12.dp)
                .testTag("theme_chip_$modeKey"),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                tint = glassContentColor(selected),
                modifier = Modifier.size(22.dp)
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = glassContentColor(selected),
                textAlign = TextAlign.Center,
                maxLines = 1
              )
            }
          }
        }
        HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
        SettingsRow(
          title = "Preto puro",
          subtitle = "O modo AMOLED usa fundo totalmente preto, que economiza bateria em telas OLED."
        )
      }
    }

    // Surface style of the floating bar and player controls
    item { SectionTitle("Superfícies") }
    item {
      SettingsGroup(modifier = Modifier.testTag("settings_surface_card")) {
        SurfaceStyle.entries.forEachIndexed { index, style ->
          val selected = surfaceStyle == style
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSurfaceStyleChange(style) }
              .padding(vertical = 14.dp)
              .testTag("surface_style_${style.key}"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = style.label,
                fontSize = 17.sp,
                color = if (selected) colors.primary else colors.onSurface
              )
              Text(
                text = style.description,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = colors.onSurfaceVariant
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
              if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = colors.primary)
              }
            }
          }
          if (index < SurfaceStyle.entries.size - 1) {
            HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
          }
        }
      }
    }

    // Assistant
    item { SectionTitle("Assistente") }
    item {
      SettingsGroup {
        val enabled = curiosityConfig?.enabled != false
        SettingsRow(
          title = "Curiosidades musicais",
          subtitle = "${if (enabled) "Ativadas" else "Desativadas"} • ${curiosityConfig?.timing?.label ?: "Ao final da música"}",
          showChevron = true,
          onClick = onOpenCuriositySettings,
          modifier = Modifier.testTag("settings_curiosidades_musicais_card")
        )
        HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
        SettingsRow(
          title = "Comandos de voz e texto",
          subtitle = "Toque no orbe para falar ou digite. Diga \"ajuda\" para ver os comandos."
        )
        HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
        SettingsRow(
          title = "IA online (Gemini)",
          subtitle = if (hasGeminiKey) {
            "Conectada. Mensagens que não são comandos locais são enviadas ao Gemini."
          } else {
            "Não configurada. O assistente responde só com comandos locais."
          }
        )
      }
    }

    // Audio
    item { SectionTitle("Áudio") }
    item {
      SettingsGroup {
        SettingsRow(
          title = "Equalizador de 15 bandas",
          subtitle = "Começa em 0 dB (Flat). Só muda quando você escolhe um perfil ou ajusta as bandas. " +
            "Vale para a música tocada dentro do Tarkas."
        )
        HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
        SettingsRow(
          title = "Saída atual",
          subtitle = "${capabilities.activeRouteName} • ${String.format("%.1f", capabilities.sampleRateHz / 1000f)} kHz"
        )
        HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
        SettingsRow(
          title = "Áudio de outros apps",
          subtitle = "Para equalizar Spotify, YouTube e os outros apps, escolha ADB ou Root em Áudio > Alcance do equalizador. " +
            "Sem root, o equalizador vale para o que o T.A.R.K.A.S. toca."
        )
      }
    }

    // Privacy
    item { SectionTitle("Privacidade") }
    item {
      SettingsGroup {
        SettingsRow(
          title = "Dados neste aparelho",
          subtitle = "Favoritos, playlists e preferências ficam só neste aparelho. O Tarkas não tem conta nem servidor próprio."
        )
        HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
        SettingsRow(
          title = "Reconhecimento de voz",
          subtitle = "Usa o serviço de voz do Android. O áudio pode ser processado pelo provedor do seu aparelho."
        )
        HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
        SettingsRow(
          title = "Permissões",
          subtitle = "Microfone: voz e espectro em tempo real. Notificações: controles da música. " +
            "Você pode negar qualquer uma, e o resto continua funcionando."
        )
      }
    }

    // About
    item { SectionTitle("Sobre") }
    item {
      SettingsGroup {
        SettingsRow(
          title = "T.A.R.K.A.S.",
          subtitle = "Central de áudio, música e assistência pessoal • versão ${BuildConfig.VERSION_NAME}"
        )
      }
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  TarkasSectionTitle(text, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
}

/** Flat list between hairlines, like an Apple Music list: no card behind the rows. */
@Composable
private fun SettingsGroup(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Column(modifier = modifier.fillMaxWidth()) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    content()
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
  }
}

@Composable
private fun SettingsRow(
  title: String,
  subtitle: String? = null,
  showChevron: Boolean = false,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
      .padding(vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontSize = 17.sp,
        color = MaterialTheme.colorScheme.onSurface
      )
      if (subtitle != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          fontSize = 15.sp,
          lineHeight = 20.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    if (showChevron) {
      Spacer(modifier = Modifier.width(8.dp))
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
