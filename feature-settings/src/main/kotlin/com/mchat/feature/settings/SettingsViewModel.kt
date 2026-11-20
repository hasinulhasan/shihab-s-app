package com.mchat.feature.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mchat.core.common.AppLanguage
import com.mchat.core.common.AppThemeMode
import com.mchat.core.common.AutoLockTimeout
import com.mchat.core.common.LocaleController
import com.mchat.core.security.applock.AppLockManager
import com.mchat.core.security.applock.PinHasher
import com.mchat.data.local.preferences.AppShellPreferences
import com.mchat.data.local.preferences.UiPreferencesRepository
import com.mchat.data.local.repository.AuditEventType
import com.mchat.data.local.repository.AuditRepository
import com.mchat.data.local.repository.BackupDataRepository
import com.mchat.data.local.repository.RestoreConflictStrategy
import com.mchat.data.sync.BackupInspectionResult
import com.mchat.data.sync.DriveAuthManager
import com.mchat.data.sync.DriveAuthTokenResult
import com.mchat.data.sync.DriveFileItem
import com.mchat.data.sync.DriveRestoreManager
import com.mchat.data.sync.DriveSignInResult
import com.mchat.data.sync.DriveSyncCoordinator
import com.mchat.data.sync.SessionRecoveryPassphraseStore
import com.mchat.data.sync.SyncStatusProvider
import com.mchat.data.sync.SyncStatusSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
  val preferences: AppShellPreferences = AppShellPreferences(),
  val syncSummary: SyncStatusSummary = SyncStatusSummary(),
  val isRecoveryPassphraseUnlockedInSession: Boolean = false,
  val remoteBackups: List<DriveFileItem> = emptyList(),
  val pendingConsentIntent: android.app.PendingIntent? = null,
  val restoreInspection: BackupInspectionResult? = null,
  val isLoadingBackups: Boolean = false,
  val statusBanner: String? = null,
  val errorBanner: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val preferencesRepository: UiPreferencesRepository,
  private val appLockManager: AppLockManager,
  private val syncStatusProvider: SyncStatusProvider,
  private val driveAuthManager: DriveAuthManager,
  private val driveSyncCoordinator: DriveSyncCoordinator,
  private val driveRestoreManager: DriveRestoreManager,
  private val recoveryPassphraseStore: SessionRecoveryPassphraseStore,
  private val backupDataRepository: BackupDataRepository,
  private val auditRepository: AuditRepository,
  private val pinHasher: PinHasher,
  private val localeController: LocaleController,
) : ViewModel() {

  private val internalState = MutableStateFlow(SettingsUiState())

  val uiState: StateFlow<SettingsUiState> = combine(
    preferencesRepository.preferences,
    syncStatusProvider.syncSummary,
    internalState,
  ) { prefs, sync, extra ->
    extra.copy(
      preferences = prefs,
      syncSummary = sync,
      isRecoveryPassphraseUnlockedInSession = recoveryPassphraseStore.isUnlocked(),
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

  fun setThemeMode(mode: AppThemeMode) {
    viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
  }

  fun setLanguage(language: AppLanguage) {
    viewModelScope.launch {
      preferencesRepository.setLanguage(language)
      localeController.applyLanguage(language)
    }
  }

  fun setReduceTransparency(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setReduceTransparency(enabled) }
  }

  fun setBiometricEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setBiometricEnabled(enabled) }
  }

  fun setAutoLockTimeout(timeout: AutoLockTimeout) {
    viewModelScope.launch { preferencesRepository.setAutoLockTimeout(timeout) }
  }

  fun lockNow() {
    recoveryPassphraseStore.clear()
    appLockManager.lockNow(uiState.value.preferences.isPinConfigured)
  }

  fun saveClinicianProfile(clinicianName: String, clinicName: String) {
    viewModelScope.launch {
      preferencesRepository.setClinicianProfile(clinicianName, clinicName)
      internalState.update { it.copy(statusBanner = "Clinician profile saved for PDF reports.", errorBanner = null) }
    }
  }

  fun configureOrUnlockRecoveryPassphrase(passphrase: String, confirmPassphrase: String?) {
    if (passphrase.length < 10) {
      internalState.update {
        it.copy(errorBanner = "Recovery passphrase must be at least 10 characters for strong Argon2id encryption.")
      }
      return
    }
    val prefs = uiState.value.preferences
    if (!prefs.recoveryPassphraseConfigured) {
      if (passphrase != confirmPassphrase) {
        internalState.update { it.copy(errorBanner = "Recovery passphrases do not match.") }
        return
      }
      viewModelScope.launch {
        val verifier = pinHasher.hashSecret(passphrase)
        preferencesRepository.saveRecoveryPassphraseVerifier(verifier)
        recoveryPassphraseStore.setPassphrase(passphrase)
        auditRepository.recordEvent(AuditEventType.CREATE, "RECOVERY_KEY", null, "{\"kdf\":\"ARGON2ID\"}")
        internalState.update {
          it.copy(
            statusBanner = "Recovery passphrase configured and unlocked for encrypted Drive backups.",
            errorBanner = null,
          )
        }
      }
    } else {
      val verifier = prefs.recoveryPassphraseVerifier
      if (verifier != null && !pinHasher.verifySecret(passphrase, verifier)) {
        internalState.update { it.copy(errorBanner = "Incorrect recovery passphrase.") }
        return
      }
      recoveryPassphraseStore.setPassphrase(passphrase)
      internalState.update {
        it.copy(statusBanner = "Recovery passphrase unlocked for this session.", errorBanner = null)
      }
    }
  }

  fun signInAndAuthorizeDrive(activityContext: Context, webClientId: String) {
    viewModelScope.launch {
      internalState.update { it.copy(errorBanner = null, statusBanner = "Connecting Google account…") }
      if (webClientId.isNotBlank()) {
        when (val signIn = driveAuthManager.signInWithCredentialManager(activityContext, webClientId)) {
          is DriveSignInResult.SignedIn -> preferencesRepository.setDriveAccount(signIn.email)
          is DriveSignInResult.Error -> {
            internalState.update { it.copy(errorBanner = signIn.message, statusBanner = null) }
            return@launch
          }
        }
      }
      authorizeDriveScopeOnly()
    }
  }

  fun authorizeDriveScopeOnly() {
    viewModelScope.launch {
      when (val auth = driveAuthManager.authorizeDriveFileScope()) {
        is DriveAuthTokenResult.Authorized -> {
          internalState.update {
            it.copy(
              statusBanner = "Google Drive authorized (drive.file scope only).",
              errorBanner = null,
              pendingConsentIntent = null,
            )
          }
        }
        is DriveAuthTokenResult.NeedsUserConsent -> {
          internalState.update { it.copy(pendingConsentIntent = auth.pendingIntent) }
        }
        is DriveAuthTokenResult.Error -> {
          internalState.update { it.copy(errorBanner = auth.message, statusBanner = null) }
        }
      }
    }
  }

  fun onDriveConsentResult(data: Intent?) {
    internalState.update { it.copy(pendingConsentIntent = null) }
    when (val parsed = driveAuthManager.parseAuthorizationResultFromIntent(data)) {
      is DriveAuthTokenResult.Authorized -> internalState.update {
        it.copy(statusBanner = "Google Drive access granted (drive.file).", errorBanner = null)
      }
      is DriveAuthTokenResult.Error -> internalState.update {
        it.copy(errorBanner = parsed.message, statusBanner = null)
      }
      is DriveAuthTokenResult.NeedsUserConsent -> Unit
    }
  }

  fun syncNow() {
    if (!recoveryPassphraseStore.isUnlocked()) {
      internalState.update {
        it.copy(errorBanner = "Unlock your Recovery Passphrase first so the database snapshot can be encrypted before upload.")
      }
      return
    }
    driveSyncCoordinator.scheduleUniqueSyncWork(replace = true)
    internalState.update {
      it.copy(statusBanner = "Encrypted Drive sync job queued (WorkManager, network-connected).", errorBanner = null)
    }
  }

  fun loadRemoteBackups() {
    viewModelScope.launch {
      internalState.update { it.copy(isLoadingBackups = true, errorBanner = null) }
      driveRestoreManager.listAvailableBackups()
        .onSuccess { list ->
          internalState.update {
            it.copy(
              isLoadingBackups = false,
              remoteBackups = list,
              statusBanner = "Found ${list.size} encrypted backups in M-CHAT Medical Records/backups.",
            )
          }
        }
        .onFailure { ex ->
          internalState.update {
            it.copy(isLoadingBackups = false, errorBanner = ex.message ?: "Unable to list remote backups.")
          }
        }
    }
  }

  fun inspectBackupForRestore(file: DriveFileItem, passphrase: String) {
    viewModelScope.launch {
      internalState.update { it.copy(errorBanner = null, statusBanner = "Downloading & verifying SHA-256 integrity…") }
      val chars = passphrase.toCharArray()
      val result = try {
        driveRestoreManager.downloadAndInspectBackup(file, chars)
      } finally {
        chars.fill('\u0000')
      }
      internalState.update {
        when (result) {
          is BackupInspectionResult.Ready -> it.copy(restoreInspection = result, statusBanner = null)
          is BackupInspectionResult.IntegrityCorrupted -> it.copy(errorBanner = result.reason, statusBanner = null)
          is BackupInspectionResult.WrongPassphrase -> it.copy(errorBanner = result.reason, statusBanner = null)
          is BackupInspectionResult.Error -> it.copy(errorBanner = result.reason, statusBanner = null)
        }
      }
    }
  }

  fun confirmApplyRestore(strategy: RestoreConflictStrategy) {
    val ready = internalState.value.restoreInspection as? BackupInspectionResult.Ready ?: return
    viewModelScope.launch {
      driveRestoreManager.confirmAndApplyRestore(ready.snapshot, strategy)
      internalState.update {
        it.copy(
          restoreInspection = null,
          statusBanner = "Encrypted backup restored cleanly using ${strategy.name}.",
          errorBanner = null,
        )
      }
    }
  }

  fun dismissRestoreInspection() {
    internalState.update { it.copy(restoreInspection = null) }
  }

  fun wipeAllClinicalData() {
    viewModelScope.launch {
      backupDataRepository.wipeAllClinicalRecords()
      internalState.update {
        it.copy(statusBanner = "All local clinical records wiped and logged in Audit Log.", errorBanner = null)
      }
    }
  }
}
