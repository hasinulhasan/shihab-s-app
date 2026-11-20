package com.mchat.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mchat.core.common.AppThemeMode
import com.mchat.core.design.R
import com.mchat.core.design.theme.MChatTheme

enum class AppLockMode {
  UNLOCK,
  SETUP_FIRST,
  SETUP_CONFIRM,
}

@Composable
fun AppLockScreen(
  mode: AppLockMode,
  enteredDigitsCount: Int,
  errorMessage: String?,
  showBiometricButton: Boolean,
  showCancelButton: Boolean,
  onDigitClick: (Char) -> Unit,
  onBackspaceClick: () -> Unit,
  onBiometricClick: () -> Unit,
  onCancelClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val title = when (mode) {
    AppLockMode.UNLOCK -> stringResource(R.string.applock_title_unlock)
    AppLockMode.SETUP_FIRST -> stringResource(R.string.applock_title_setup)
    AppLockMode.SETUP_CONFIRM -> stringResource(R.string.applock_title_confirm)
  }
  val subtitle = when (mode) {
    AppLockMode.UNLOCK -> stringResource(R.string.applock_subtitle_unlock)
    AppLockMode.SETUP_FIRST -> stringResource(R.string.applock_subtitle_setup)
    AppLockMode.SETUP_CONFIRM -> stringResource(R.string.applock_subtitle_confirm)
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    AnimatedAmbientBackground()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      GlassCard(
        modifier = Modifier
          .widthIn(max = 420.dp)
          .fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(56.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(28.dp),
            )
          }

          Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 8.dp),
          ) {
            repeat(6) { index ->
              val filled = index < enteredDigitsCount
              Box(
                modifier = Modifier
                  .size(16.dp)
                  .clip(CircleShape)
                  .background(
                    if (filled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                  )
                  .border(
                    width = 1.5.dp,
                    color = if (filled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                  ),
              )
            }
          }

          if (!errorMessage.isNullOrBlank()) {
            Text(
              text = errorMessage,
              style = MaterialTheme.typography.labelMedium,
              color = MChatTheme.extendedColors.statusCoralContent,
              textAlign = TextAlign.Center,
              modifier = Modifier.testTag("applock_error_text"),
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          PinKeypad(
            showBiometricButton = showBiometricButton && mode == AppLockMode.UNLOCK,
            onDigitClick = onDigitClick,
            onBackspaceClick = onBackspaceClick,
            onBiometricClick = onBiometricClick,
          )

          if (showCancelButton) {
            TextButton(
              onClick = onCancelClick,
              modifier = Modifier.testTag("applock_cancel_button"),
            ) {
              Text(text = stringResource(R.string.applock_cancel))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PinKeypad(
  showBiometricButton: Boolean,
  onDigitClick: (Char) -> Unit,
  onBackspaceClick: () -> Unit,
  onBiometricClick: () -> Unit,
) {
  val rows = listOf(
    listOf('1', '2', '3'),
    listOf('4', '5', '6'),
    listOf('7', '8', '9'),
  )
  Column(
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    rows.forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        row.forEach { digit ->
          PinDigitKey(digit = digit, onClick = { onDigitClick(digit) })
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
      if (showBiometricButton) {
        val bioDesc = stringResource(R.string.applock_use_biometric)
        PinIconKey(
          contentDesc = bioDesc,
          testTag = "applock_biometric_button",
          onClick = onBiometricClick,
        ) {
          Icon(
            imageVector = Icons.Rounded.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
          )
        }
      } else {
        Spacer(modifier = Modifier.size(64.dp))
      }

      PinDigitKey(digit = '0', onClick = { onDigitClick('0') })

      val backspaceDesc = stringResource(R.string.applock_backspace)
      PinIconKey(
        contentDesc = backspaceDesc,
        testTag = "applock_backspace_button",
        onClick = onBackspaceClick,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.Backspace,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(24.dp),
        )
      }
    }
  }
}

@Composable
private fun PinDigitKey(
  digit: Char,
  onClick: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  Box(
    modifier = Modifier
      .size(64.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
      .clickable(role = Role.Button) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
      }
      .testTag("pin_key_$digit"),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = digit.toString(),
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun PinIconKey(
  contentDesc: String,
  testTag: String,
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  Box(
    modifier = Modifier
      .size(64.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
      .clickable(role = Role.Button) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
      }
      .semantics { contentDescription = contentDesc }
      .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

@Preview(name = "AppLock Light", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun AppLockLightPreview() {
  MChatTheme(themeMode = AppThemeMode.LIGHT) {
    AppLockScreen(
      mode = AppLockMode.UNLOCK,
      enteredDigitsCount = 3,
      errorMessage = null,
      showBiometricButton = true,
      showCancelButton = false,
      onDigitClick = {},
      onBackspaceClick = {},
      onBiometricClick = {},
      onCancelClick = {},
    )
  }
}

@Preview(name = "AppLock Dark", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun AppLockDarkPreview() {
  MChatTheme(themeMode = AppThemeMode.DARK) {
    AppLockScreen(
      mode = AppLockMode.SETUP_FIRST,
      enteredDigitsCount = 4,
      errorMessage = "PINs did not match. Start again.",
      showBiometricButton = false,
      showCancelButton = true,
      onDigitClick = {},
      onBackspaceClick = {},
      onBiometricClick = {},
      onCancelClick = {},
    )
  }
}
