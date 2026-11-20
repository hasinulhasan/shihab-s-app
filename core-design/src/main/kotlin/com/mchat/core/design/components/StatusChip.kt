package com.mchat.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mchat.core.common.AppThemeMode
import com.mchat.core.design.theme.MChatTheme

enum class StatusTone {
  LOW_GREEN,
  MODERATE_AMBER,
  HIGH_CORAL,
  INFO_TEAL,
  NEUTRAL,
}

/**
 * Accessible clinical status pill. ALWAYS pairs a semantic color with both an explicit [icon]
 * and a text [label] so status never relies on color alone.
 */
@Composable
fun StatusChip(
  label: String,
  icon: ImageVector,
  tone: StatusTone,
  modifier: Modifier = Modifier,
) {
  val extended = MChatTheme.extendedColors
  val (containerColor, contentColor) = when (tone) {
    StatusTone.LOW_GREEN -> extended.statusGreenContainer to extended.statusGreenContent
    StatusTone.MODERATE_AMBER -> extended.statusAmberContainer to extended.statusAmberContent
    StatusTone.HIGH_CORAL -> extended.statusCoralContainer to extended.statusCoralContent
    StatusTone.INFO_TEAL -> extended.statusInfoContainer to extended.statusInfoContent
    StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
  }

  Row(
    modifier = modifier
      .semantics(mergeDescendants = true) {}
      .clip(RoundedCornerShape(50))
      .background(containerColor)
      .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = contentColor,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = contentColor,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Preview(name = "StatusChip Light", showBackground = true)
@Composable
private fun StatusChipLightPreview() {
  MChatTheme(themeMode = AppThemeMode.LIGHT) {
    Surface(color = MaterialTheme.colorScheme.background) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatusChip("Low Risk — Negative Screen", Icons.Rounded.CheckCircle, StatusTone.LOW_GREEN)
        StatusChip("Moderate Risk — Follow-Up Pending", Icons.Rounded.Schedule, StatusTone.MODERATE_AMBER)
        StatusChip("High Risk — Positive Screen", Icons.Rounded.WarningAmber, StatusTone.HIGH_CORAL)
        StatusChip("Offline Ready", Icons.Rounded.CloudOff, StatusTone.INFO_TEAL)
        StatusChip("Synced", Icons.Rounded.CloudDone, StatusTone.LOW_GREEN)
        StatusChip("Sync Failed", Icons.Rounded.ErrorOutline, StatusTone.HIGH_CORAL)
      }
    }
  }
}

@Preview(name = "StatusChip Dark", showBackground = true)
@Composable
private fun StatusChipDarkPreview() {
  MChatTheme(themeMode = AppThemeMode.DARK) {
    Surface(color = MaterialTheme.colorScheme.background) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatusChip("Low Risk", Icons.Rounded.CheckCircle, StatusTone.LOW_GREEN)
        StatusChip("Moderate Risk", Icons.Rounded.Schedule, StatusTone.MODERATE_AMBER)
        StatusChip("High Risk", Icons.Rounded.WarningAmber, StatusTone.HIGH_CORAL)
        StatusChip("Offline Ready", Icons.Rounded.CloudOff, StatusTone.INFO_TEAL)
      }
    }
  }
}
