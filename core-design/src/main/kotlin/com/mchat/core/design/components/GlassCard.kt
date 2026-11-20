package com.mchat.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mchat.core.common.AppThemeMode
import com.mchat.core.design.theme.LocalHazeState
import com.mchat.core.design.theme.MChatTheme
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Liquid Glass container using real Haze backdrop blur on Android 12+ when transparency is enabled,
 * and seamlessly falling back to a crisp solid translucent frosted layer on API 26–30 or when
 * "Reduce Transparency" / reduced animations is active.
 */
@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  shape: Shape = MaterialTheme.shapes.medium,
  contentPadding: PaddingValues = PaddingValues(18.dp),
  elevation: Dp = 8.dp,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val extendedColors = MChatTheme.extendedColors
  val glassConfig = MChatTheme.glassConfig
  val hazeState = LocalHazeState.current

  val borderBrush = Brush.linearGradient(
    colors = listOf(extendedColors.glassBorderStart, extendedColors.glassBorderEnd),
  )

  val surfaceModifier = if (glassConfig.useRealBlur && hazeState != null) {
    Modifier.hazeEffect(
      state = hazeState,
      style = HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.surface,
        tints = listOf(HazeTint(extendedColors.glassTint)),
        blurRadius = 22.dp,
        noiseFactor = 0.02f,
      ),
    )
  } else {
    Modifier.background(
      color = if (glassConfig.reduceTransparency) {
        extendedColors.glassSolidFallback
      } else {
        extendedColors.glassTint
      },
      shape = shape,
    )
  }

  val clickModifier = if (onClick != null) {
    Modifier.clickable(onClick = onClick)
  } else {
    Modifier
  }

  Box(
    modifier = modifier
      .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.08f),
        spotColor = Color.Black.copy(alpha = 0.12f),
      )
      .clip(shape)
      .then(surfaceModifier)
      .border(BorderStroke(1.dp, borderBrush), shape)
      .then(clickModifier),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(contentPadding),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
  }
}

@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (trailing != null) {
      trailing()
    }
  }
}

@Preview(name = "GlassCard Light", showBackground = true)
@Composable
private fun GlassCardLightPreview() {
  MChatTheme(themeMode = AppThemeMode.LIGHT) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
        .padding(16.dp),
    ) {
      GlassCard {
        SectionHeader(
          title = "Recent Screening",
          subtitle = "Offline encrypted record",
        )
        Text(
          text = "Liquid glass card with soft specular border and solid fallback.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Preview(name = "GlassCard Dark", showBackground = true)
@Composable
private fun GlassCardDarkPreview() {
  MChatTheme(themeMode = AppThemeMode.DARK) {
    Surface(color = MaterialTheme.colorScheme.background) {
      Box(modifier = Modifier.padding(16.dp)) {
        GlassCard(shape = RoundedCornerShape(24.dp)) {
          SectionHeader(
            title = "Recent Screening",
            subtitle = "Dark mode liquid glass",
          )
          Text(
            text = "High-contrast typography preserved over deep sage glass.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
