package com.mchat.core.design.components

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mchat.core.design.theme.MChatTheme
import com.mchat.core.security.applock.BiometricAuthHelper
import com.mchat.core.security.applock.PinDigest
import com.mchat.core.security.applock.PinHasher

/**
 * Re-authentication gate required before sensitive actions (archiving/deleting patient records,
 * wiping local data, or exiting Caregiver Hand-Over mode).
 * Verifies against the clinician's stored PIN digest or BiometricPrompt.
 */
@Composable
fun ClinicianReAuthDialog(
  title: String,
  message: String,
  confirmLabel: String,
  pinDigest: PinDigest?,
  biometricEnabled: Boolean,
  onDismiss: () -> Unit,
  onAuthenticated: () -> Unit,
) {
  val context = LocalContext.current
  val activity = context as? AppCompatActivity
  var pinInput by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Rounded.Security,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pinDigest != null) {
          OutlinedTextField(
            value = pinInput,
            onValueChange = { next ->
              if (next.length <= 6 && next.all { it.isDigit() }) {
                pinInput = next
                errorText = null
              }
            },
            label = { Text("6-Digit Clinician PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = errorText != null,
            supportingText = errorText?.let { err -> { Text(err) } },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("reauth_pin_input"),
          )
          if (biometricEnabled && activity != null && BiometricAuthHelper.isBiometricAvailable(context)) {
            SecondaryGlassButton(
              text = "Verify with Biometrics",
              icon = Icons.Rounded.Fingerprint,
              onClick = {
                BiometricAuthHelper.authenticate(
                  activity = activity,
                  title = title,
                  subtitle = message,
                  negativeButtonText = "Use PIN",
                  onSuccess = onAuthenticated,
                )
              },
              modifier = Modifier.fillMaxWidth(),
              testTag = "reauth_biometric_button",
            )
          }
        } else {
          Text(
            text = "No clinician PIN configured yet. Confirm below to proceed (recommended: set a PIN in Settings).",
            style = MaterialTheme.typography.bodySmall,
            color = MChatTheme.extendedColors.statusAmberContent,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (pinDigest == null) {
            onAuthenticated()
          } else if (PinHasher().verifyPin(pinInput, pinDigest)) {
            onAuthenticated()
          } else {
            errorText = "Incorrect clinician PIN"
          }
        },
        modifier = Modifier.testTag("reauth_confirm_button"),
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}
