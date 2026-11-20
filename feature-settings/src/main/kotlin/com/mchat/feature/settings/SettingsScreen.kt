package com.mchat.feature.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchat.core.common.AppLanguage
import com.mchat.core.common.AppThemeMode
import com.mchat.core.common.AutoLockTimeout
import com.mchat.core.design.R
import com.mchat.core.design.components.ClinicianReAuthDialog
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.PrimaryButton
import com.mchat.core.design.components.SecondaryGlassButton
import com.mchat.core.design.components.SectionHeader
import com.mchat.core.design.components.StatusChip
import com.mchat.core.design.components.StatusTone
import com.mchat.core.design.components.SyncStatusChip
import com.mchat.core.security.applock.BiometricAuthHelper
import com.mchat.data.local.repository.RestoreConflictStrategy
import com.mchat.data.sync.BackupInspectionResult
import com.mchat.data.sync.DriveFileItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsRoute(
  onRequestPinSetup: () -> Unit,
  onOpenAuditLog: () -> Unit = {},
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val prefs = state.preferences
  val context = LocalContext.current
  val biometricAvailable = remember(context) { BiometricAuthHelper.isBiometricAvailable(context) }

  var clinicianName by remember(prefs.clinicianName) { mutableStateOf(prefs.clinicianName) }
  var clinicName by remember(prefs.clinicName) { mutableStateOf(prefs.clinicName) }
  var recoveryPassphrase by remember { mutableStateOf("") }
  var recoveryPassphraseConfirm by remember { mutableStateOf("") }
  var oauthWebClientId by remember { mutableStateOf("") }
  var showCloudSetupGuide by remember { mutableStateOf(false) }
  var showWipeReAuth by remember { mutableStateOf(false) }
  var selectedBackupToInspect by remember { mutableStateOf<DriveFileItem?>(null) }
  var restorePassphraseInput by remember { mutableStateOf("") }

  val driveConsentLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      viewModel.onDriveConsentResult(result.data)
    }
  }

  LaunchedEffect(state.pendingConsentIntent) {
    state.pendingConsentIntent?.let { pi ->
      driveConsentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }
  }

  if (showWipeReAuth) {
    ClinicianReAuthDialog(
      title = "Wipe All Local Clinical Data",
      message = "This permanently deletes all local patients, screenings, and assessments on this device. " +
        "Audit log entries are preserved. Clinician verification is required.",
      confirmLabel = "Wipe All Data",
      pinDigest = prefs.pinDigest,
      biometricEnabled = prefs.biometricEnabled,
      onDismiss = { showWipeReAuth = false },
      onAuthenticated = {
        showWipeReAuth = false
        viewModel.wipeAllClinicalData()
      },
    )
  }

  selectedBackupToInspect?.let { file ->
    AlertDialog(
      onDismissRequest = { selectedBackupToInspect = null },
      title = { Text("Decrypt & Verify Backup") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("File: ${file.name}\nModified: ${file.modifiedTime ?: "—"}", style = MaterialTheme.typography.bodySmall)
          OutlinedTextField(
            value = restorePassphraseInput,
            onValueChange = { restorePassphraseInput = it },
            label = { Text("Recovery Passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        TextButton(
          enabled = restorePassphraseInput.isNotBlank(),
          onClick = {
            val target = file
            val pass = restorePassphraseInput
            selectedBackupToInspect = null
            restorePassphraseInput = ""
            viewModel.inspectBackupForRestore(target, pass)
          },
        ) {
          Text("Verify & Preview Diff")
        }
      },
      dismissButton = {
        TextButton(onClick = { selectedBackupToInspect = null }) { Text("Cancel") }
      },
    )
  }

  (state.restoreInspection as? BackupInspectionResult.Ready)?.let { ready ->
    AlertDialog(
      onDismissRequest = viewModel::dismissRestoreInspection,
      title = { Text("Confirm Backup Restore") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          StatusChip("SHA-256 Integrity & AES-GCM Verified", Icons.Rounded.VerifiedUser, StatusTone.LOW_GREEN)
          Text(
            text = "Backup: ${ready.fileItem.name}\n" +
              "New patients: ${ready.diff.newPatients} • Updated patients: ${ready.diff.updatedPatients}\n" +
              "New screenings: ${ready.diff.newScreenings} • Updated screenings: ${ready.diff.updatedScreenings}\n" +
              "Version conflicts detected: ${ready.diff.conflicts.size}",
            style = MaterialTheme.typography.bodySmall,
          )
          Text("Choose how to apply this restore:", style = MaterialTheme.typography.labelLarge)
        }
      },
      confirmButton = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          PrimaryButton(
            text = "Merge (Prefer Newer)",
            onClick = { viewModel.confirmApplyRestore(RestoreConflictStrategy.MERGE_PREFER_NEWER) },
            modifier = Modifier.fillMaxWidth(),
          )
          SecondaryGlassButton(
            text = "Merge (Keep Local on Conflict)",
            onClick = { viewModel.confirmApplyRestore(RestoreConflictStrategy.MERGE_KEEP_LOCAL_ON_CONFLICT) },
            modifier = Modifier.fillMaxWidth(),
          )
          TextButton(
            onClick = { viewModel.confirmApplyRestore(RestoreConflictStrategy.REPLACE_ALL_LOCAL) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Replace All Local Data", color = MaterialTheme.colorScheme.error)
          }
        }
      },
      dismissButton = {
        TextButton(onClick = viewModel::dismissRestoreInspection) { Text("Cancel") }
      },
    )
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 128.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.settings_title),
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          text = stringResource(R.string.settings_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (state.statusBanner != null || state.errorBanner != null) {
      item {
        GlassCard {
          state.statusBanner?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
          }
          state.errorBanner?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
          }
        }
      }
    }

    // Language Section
    item {
      GlassCard {
        SectionHeader(
          title = stringResource(R.string.settings_section_language),
          subtitle = "Switches English and Bangla UI immediately at runtime",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          FilterChip(
            selected = prefs.language == AppLanguage.ENGLISH,
            onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
            label = { Text(stringResource(R.string.settings_language_en)) },
            modifier = Modifier.testTag("settings_lang_en"),
          )
          FilterChip(
            selected = prefs.language == AppLanguage.BANGLA,
            onClick = { viewModel.setLanguage(AppLanguage.BANGLA) },
            label = { Text(stringResource(R.string.settings_language_bn)) },
            modifier = Modifier.testTag("settings_lang_bn"),
          )
        }
      }
    }

    // Appearance Section
    item {
      GlassCard {
        SectionHeader(title = stringResource(R.string.settings_section_appearance))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = prefs.themeMode == AppThemeMode.LIGHT,
            onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
            label = { Text(stringResource(R.string.settings_theme_light)) },
          )
          FilterChip(
            selected = prefs.themeMode == AppThemeMode.DARK,
            onClick = { viewModel.setThemeMode(AppThemeMode.DARK) },
            label = { Text(stringResource(R.string.settings_theme_dark)) },
          )
          FilterChip(
            selected = prefs.themeMode == AppThemeMode.SYSTEM,
            onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) },
            label = { Text(stringResource(R.string.settings_theme_system)) },
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_reduce_transparency), style = MaterialTheme.typography.titleSmall)
            Text(
              stringResource(R.string.settings_reduce_transparency_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(checked = prefs.reduceTransparency, onCheckedChange = viewModel::setReduceTransparency)
        }
      }
    }

    // Clinician Profile (PDF Report Header)
    item {
      GlassCard {
        SectionHeader(
          title = "Clinician & Clinic Header",
          subtitle = "Printed on clinical PDF reports",
        )
        OutlinedTextField(
          value = clinicianName,
          onValueChange = { clinicianName = it },
          label = { Text("Clinician Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = clinicName,
          onValueChange = { clinicName = it },
          label = { Text("Clinic / Hospital Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        SecondaryGlassButton(
          text = "Save Report Header",
          onClick = { viewModel.saveClinicianProfile(clinicianName, clinicName) },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    // App Lock & Security Section
    item {
      GlassCard {
        SectionHeader(title = stringResource(R.string.settings_section_security))
        StatusChip(
          label = if (prefs.isPinConfigured) {
            stringResource(R.string.settings_pin_status_configured)
          } else {
            stringResource(R.string.settings_pin_status_not_set)
          },
          icon = if (prefs.isPinConfigured) Icons.Rounded.VerifiedUser else Icons.Rounded.Shield,
          tone = if (prefs.isPinConfigured) StatusTone.LOW_GREEN else StatusTone.MODERATE_AMBER,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          PrimaryButton(
            text = if (prefs.isPinConfigured) stringResource(R.string.settings_change_pin) else stringResource(R.string.settings_setup_pin),
            icon = Icons.Rounded.Lock,
            onClick = onRequestPinSetup,
            modifier = Modifier.weight(1f),
            testTag = "settings_setup_pin_button",
          )
          if (prefs.isPinConfigured) {
            SecondaryGlassButton(
              text = stringResource(R.string.settings_lock_now),
              onClick = viewModel::lockNow,
              modifier = Modifier.weight(0.9f),
              testTag = "settings_lock_now_button",
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_biometric_toggle), style = MaterialTheme.typography.titleSmall)
            if (!biometricAvailable) {
              Text(
                stringResource(R.string.settings_biometric_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Switch(
            checked = prefs.biometricEnabled && biometricAvailable,
            onCheckedChange = viewModel::setBiometricEnabled,
            enabled = biometricAvailable && prefs.isPinConfigured,
          )
        }
        Text(stringResource(R.string.settings_autolock_title), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          AutoLockTimeout.entries.forEach { timeout ->
            val label = when (timeout) {
              AutoLockTimeout.IMMEDIATE -> "0s"
              AutoLockTimeout.ONE_MINUTE -> "1m"
              AutoLockTimeout.FIVE_MINUTES -> "5m"
              AutoLockTimeout.FIFTEEN_MINUTES -> "15m"
            }
            FilterChip(
              selected = prefs.autoLockTimeout == timeout,
              onClick = { viewModel.setAutoLockTimeout(timeout) },
              label = { Text(label) },
            )
          }
        }
        Text(
          stringResource(R.string.settings_screen_security_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Encrypted Backup Recovery Passphrase (Argon2id + AES-256-GCM)
    item {
      GlassCard {
        SectionHeader(
          title = "Encrypted Cloud Backup Key",
          subtitle = "AES-256-GCM random DEK wrapped via Argon2id KEK (independent of Android Keystore)",
        )
        StatusChip(
          label = when {
            state.isRecoveryPassphraseUnlockedInSession -> "Recovery Key Unlocked in Memory"
            prefs.recoveryPassphraseConfigured -> "Recovery Key Configured (Locked)"
            else -> "Recovery Passphrase Not Set"
          },
          icon = Icons.Rounded.Key,
          tone = if (state.isRecoveryPassphraseUnlockedInSession) StatusTone.LOW_GREEN else StatusTone.MODERATE_AMBER,
        )
        Text(
          text = "CRITICAL WARNING: Android Keystore keys do not survive reinstall or new devices. " +
            "Your cloud backups are encrypted with this recovery passphrase. If you lose it, encrypted backups CANNOT be recovered.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
        OutlinedTextField(
          value = recoveryPassphrase,
          onValueChange = { recoveryPassphrase = it },
          label = { Text(if (prefs.recoveryPassphraseConfigured) "Enter Recovery Passphrase to Unlock" else "Create Recovery Passphrase (min 10 chars)") },
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("recovery_passphrase_input"),
        )
        if (!prefs.recoveryPassphraseConfigured) {
          OutlinedTextField(
            value = recoveryPassphraseConfirm,
            onValueChange = { recoveryPassphraseConfirm = it },
            label = { Text("Confirm Recovery Passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        PrimaryButton(
          text = if (prefs.recoveryPassphraseConfigured) "Unlock Backup Key for Session" else "Save Recovery Passphrase",
          icon = Icons.Rounded.Key,
          onClick = {
            viewModel.configureOrUnlockRecoveryPassphrase(
              passphrase = recoveryPassphrase,
              confirmPassphrase = if (prefs.recoveryPassphraseConfigured) null else recoveryPassphraseConfirm,
            )
            recoveryPassphrase = ""
            recoveryPassphraseConfirm = ""
          },
          modifier = Modifier.fillMaxWidth(),
          testTag = "save_recovery_passphrase_button",
        )
      }
    }

    // Google Drive Sync & Restore (drive.file scope only)
    item {
      val lastSyncStr = state.syncSummary.lastSuccessfulSyncMillis?.let { ms ->
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
          .withZone(ZoneId.systemDefault())
          .format(Instant.ofEpochMilli(ms))
      } ?: "Never"

      GlassCard {
        SectionHeader(
          title = "Google Drive Encrypted Backup",
          subtitle = "Folder: M-CHAT Medical Records/{backups,reports,statistics} • drive.file scope only",
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SyncStatusChip(status = state.syncSummary.status)
          Text("Last sync: $lastSyncStr", style = MaterialTheme.typography.labelSmall)
        }
        if (state.syncSummary.failureReasonCode != null) {
          Text(
            text = "Last failure code: ${state.syncSummary.failureReasonCode}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        if (!prefs.driveAccountEmail.isNullOrBlank()) {
          Text("Connected: ${prefs.driveAccountEmail}", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
          value = oauthWebClientId,
          onValueChange = { oauthWebClientId = it },
          label = { Text("OAuth Web Client ID (optional for Credential Manager)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SecondaryGlassButton(
            text = "Authorize Drive",
            icon = Icons.Rounded.CloudDone,
            onClick = { viewModel.signInAndAuthorizeDrive(context, oauthWebClientId) },
            modifier = Modifier.weight(1f),
            testTag = "settings_authorize_drive_button",
          )
          PrimaryButton(
            text = "Sync Now",
            icon = Icons.Rounded.CloudSync,
            onClick = viewModel::syncNow,
            modifier = Modifier.weight(1f),
            testTag = "settings_sync_now_button",
          )
        }
        SecondaryGlassButton(
          text = if (state.isLoadingBackups) "Loading Backups…" else "List & Restore Encrypted Backups",
          icon = Icons.Rounded.Restore,
          onClick = viewModel::loadRemoteBackups,
          modifier = Modifier.fillMaxWidth(),
          testTag = "settings_list_backups_button",
        )
        state.remoteBackups.forEach { file ->
          GlassCard(
            onClick = { selectedBackupToInspect = file },
            contentPadding = PaddingValues(12.dp),
            elevation = 2.dp,
          ) {
            Text(file.name, style = MaterialTheme.typography.labelLarge)
            Text(
              "Modified: ${file.modifiedTime ?: "—"} • ${file.size ?: "0"} bytes • Tap to verify & restore",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        TextButton(onClick = { showCloudSetupGuide = !showCloudSetupGuide }) {
          Icon(Icons.Rounded.Info, contentDescription = null)
          Text(if (showCloudSetupGuide) " Hide Google Cloud Console Setup Guide" else " Show Google Cloud Console Setup Guide")
        }
        if (showCloudSetupGuide) {
          Text(
            text = "1. Create a Google Cloud Project and enable Google Drive API.\n" +
              "2. Configure OAuth Consent Screen (External) with ONLY scope: https://www.googleapis.com/auth/drive.file.\n" +
              "3. Create an Android OAuth Client ID using package 'com.aistudio.mchatrf.kvnqxr' and your signing keystore SHA-1.\n" +
              "4. Optionally create a Web Client ID for Credential Manager Google Sign-In.\n" +
              "IMPORTANT TOKEN EXPIRY WARNING: OAuth consent screens left in 'Testing' mode issue refresh tokens that expire every 7 days. " +
              "Add your clinician Google account as a Test User, or publish the OAuth consent screen to avoid 7-day token expiration.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Tamper-Evident Audit Log & Destructive Wipe
    item {
      GlassCard {
        SectionHeader(
          title = "Audit Chain & Local Data Governance",
          subtitle = "SHA-256 hash-chained log of create, edit, correction, archive, export, restore, sync, and wipe events",
        )
        SecondaryGlassButton(
          text = "Inspect Tamper-Evident Audit Log",
          icon = Icons.Rounded.History,
          onClick = onOpenAuditLog,
          modifier = Modifier.fillMaxWidth(),
          testTag = "settings_open_audit_log_button",
        )
        SecondaryGlassButton(
          text = "Wipe All Local Clinical Records",
          icon = Icons.Rounded.DeleteForever,
          onClick = { showWipeReAuth = true },
          modifier = Modifier.fillMaxWidth(),
          testTag = "settings_wipe_data_button",
        )
        StatusChip(
          label = "Requires explicit confirmation + PIN/Biometric re-auth",
          icon = Icons.Rounded.WarningAmber,
          tone = StatusTone.MODERATE_AMBER,
        )
      }
    }

    item { Spacer(modifier = Modifier.height(12.dp)) }
  }
}
