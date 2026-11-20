package com.mchat.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mchat.core.common.AppThemeMode
import com.mchat.core.design.R
import com.mchat.core.design.theme.MChatTheme

@Composable
fun EmptyStateCard(
  icon: ImageVector,
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  actionIcon: ImageVector? = null,
  actionTestTag: String? = null,
  onAction: (() -> Unit)? = null,
) {
  GlassCard(
    modifier = modifier.fillMaxWidth(),
    contentPadding = PaddingValues(24.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(32.dp),
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      if (!actionLabel.isNullOrBlank() && onAction != null) {
        PrimaryButton(
          text = actionLabel,
          icon = actionIcon,
          onClick = onAction,
          modifier = Modifier.padding(top = 4.dp),
          testTag = actionTestTag,
        )
      }
    }
  }
}

@Composable
fun NonDiagnosticBanner(
  modifier: Modifier = Modifier,
) {
  GlassCard(
    modifier = modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    elevation = 4.dp,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.HealthAndSafety,
        contentDescription = null,
        tint = MChatTheme.extendedColors.statusInfoContent,
        modifier = Modifier.size(22.dp),
      )
      Text(
        text = stringResource(R.string.non_diagnostic_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Preview(name = "EmptyStateCard Light", showBackground = true)
@Composable
private fun EmptyStateCardPreview() {
  MChatTheme(themeMode = AppThemeMode.LIGHT) {
    Surface(color = MaterialTheme.colorScheme.background) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        NonDiagnosticBanner()
        EmptyStateCard(
          icon = Icons.AutoMirrored.Rounded.Assignment,
          title = "No Screenings Recorded Yet",
          body = "Completed and pending clinical assessments will appear here.",
          actionLabel = "Start New Screening",
          onAction = {},
        )
      }
    }
  }
}
