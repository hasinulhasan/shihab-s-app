package com.mchat.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mchat.core.common.AppThemeMode
import com.mchat.core.design.theme.MChatTheme

@Composable
fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  testTag: String? = null,
) {
  val haptic = LocalHapticFeedback.current
  val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

  Button(
    onClick = {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      onClick()
    },
    enabled = enabled,
    shape = RoundedCornerShape(18.dp),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
    ),
    modifier = taggedModifier.defaultMinSize(minWidth = 48.dp, minHeight = 52.dp),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
    )
  }
}

@Composable
fun SecondaryGlassButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  testTag: String? = null,
) {
  val haptic = LocalHapticFeedback.current
  val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

  OutlinedButton(
    onClick = {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      onClick()
    },
    enabled = enabled,
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = MChatTheme.extendedColors.glassTint,
      contentColor = MaterialTheme.colorScheme.onSurface,
    ),
    modifier = taggedModifier.defaultMinSize(minWidth = 48.dp, minHeight = 52.dp),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
    )
  }
}

/**
 * Large accessible response button designed for questionnaire items.
 * Never relies on color alone: always shows an explicit icon, clear label, and border/selected state.
 */
@Composable
fun ClinicalAnswerButton(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String? = null,
) {
  val haptic = LocalHapticFeedback.current
  val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier
  val containerColor = if (selected) {
    MaterialTheme.colorScheme.primary
  } else {
    MChatTheme.extendedColors.glassTint
  }
  val contentColor = if (selected) {
    MaterialTheme.colorScheme.onPrimary
  } else {
    MaterialTheme.colorScheme.onSurface
  }
  val borderColor = if (selected) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.outline
  }

  OutlinedButton(
    onClick = {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      onClick()
    },
    shape = RoundedCornerShape(22.dp),
    border = BorderStroke(if (selected) 2.dp else 1.2.dp, borderColor),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = containerColor,
      contentColor = contentColor,
    ),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    modifier = taggedModifier.defaultMinSize(minWidth = 48.dp, minHeight = 64.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
      )
    }
  }
}

@Preview(name = "Buttons Light", showBackground = true)
@Composable
private fun ButtonsLightPreview() {
  MChatTheme(themeMode = AppThemeMode.LIGHT) {
    Surface(color = MaterialTheme.colorScheme.background) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        PrimaryButton(
          text = "Start New Screening",
          icon = Icons.Rounded.Add,
          onClick = {},
          modifier = Modifier.fillMaxWidth(),
        )
        SecondaryGlassButton(
          text = "Lock App Now",
          icon = Icons.Rounded.Lock,
          onClick = {},
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          ClinicalAnswerButton(
            label = "YES",
            icon = Icons.Rounded.CheckCircle,
            selected = true,
            onClick = {},
            modifier = Modifier.weight(1f),
          )
          ClinicalAnswerButton(
            label = "NO",
            icon = Icons.Rounded.Close,
            selected = false,
            onClick = {},
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Preview(name = "Buttons Dark", showBackground = true)
@Composable
private fun ButtonsDarkPreview() {
  MChatTheme(themeMode = AppThemeMode.DARK) {
    Surface(color = MaterialTheme.colorScheme.background) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        PrimaryButton(
          text = "Start New Screening",
          icon = Icons.Rounded.Add,
          onClick = {},
          modifier = Modifier.fillMaxWidth(),
        )
        SecondaryGlassButton(
          text = "Set Clinician PIN",
          icon = Icons.Rounded.Lock,
          onClick = {},
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}
