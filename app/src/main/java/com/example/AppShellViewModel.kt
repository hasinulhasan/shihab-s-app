package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mchat.core.design.components.AppLockMode
import com.mchat.core.security.applock.AppLockManager
import com.mchat.core.security.applock.AppLockStatus
import com.mchat.core.security.applock.PinHasher
import com.mchat.data.local.preferences.AppShellPreferences
import com.mchat.data.local.preferences.UiPreferencesRepository
import com.mchat.data.local.repository.AuditEventType
import com.mchat.data.local.repository.AuditRepository
import com.mchat.data.sync.SessionRecoveryPassphraseStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class PinErrorType {
  WRONG_PIN,
  PIN_MISMATCH,
}

data class PinOverlayState(
  val isSettingUpPin: Boolean = false,
  val setupStepConfirm: Boolean = false,
  val enteredDigits: String = "",
  val firstSetupPin: String? = null,
  val errorType: PinErrorType? = null,
) {
  val mode: AppLockMode
    get() = when {
      !isSettingUpPin -> AppLockMode.UNLOCK
      setupStepConfirm -> AppLockMode.SETUP_CONFIRM
      else -> AppLockMode.SETUP_FIRST
    }
}

@HiltViewModel
class AppShellViewModel @Inject constructor(
  private val preferencesRepository: UiPreferencesRepository,
  private val appLockManager: AppLockManager,
  private val pinHasher: PinHasher,
  private val auditRepository: AuditRepository,
  private val recoveryPassphraseStore: SessionRecoveryPassphraseStore,
) : ViewModel() {

  val preferences: StateFlow<AppShellPreferences> = preferencesRepository.preferences
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = AppShellPreferences(),
    )

  val lockStatus: StateFlow<AppLockStatus> = appLockManager.lockStatus

  private val _pinOverlay = MutableStateFlow(PinOverlayState())
  val pinOverlay: StateFlow<PinOverlayState> = _pinOverlay.asStateFlow()

  init {
    viewModelScope.launch {
      val initialPrefs = preferencesRepository.preferences.first()
      appLockManager.requireInitialLockIfConfigured(initialPrefs.isPinConfigured)
    }
  }

  fun onAppBackgrounded() {
    val prefs = preferences.value
    appLockManager.onAppBackgrounded(prefs.isPinConfigured, prefs.autoLockTimeout)
    if (appLockManager.lockStatus.value == AppLockStatus.LOCKED) {
      recoveryPassphraseStore.clear()
    }
  }

  fun onAppForegrounded() {
    val prefs = preferences.value
    appLockManager.onAppForegrounded(prefs.isPinConfigured, prefs.autoLockTimeout)
    if (appLockManager.lockStatus.value == AppLockStatus.LOCKED) {
      recoveryPassphraseStore.clear()
    }
  }

  fun startPinSetup() {
    _pinOverlay.value = PinOverlayState(isSettingUpPin = true)
  }

  fun cancelPinSetup() {
    _pinOverlay.value = PinOverlayState(isSettingUpPin = false)
  }

  fun onPinDigit(digit: Char) {
    val current = _pinOverlay.value
    if (current.enteredDigits.length >= 6) return
    val updatedDigits = current.enteredDigits + digit
    _pinOverlay.update { it.copy(enteredDigits = updatedDigits, errorType = null) }

    if (updatedDigits.length == 6) {
      handleCompletedSixDigits(updatedDigits, current)
    }
  }

  fun onPinBackspace() {
    _pinOverlay.update {
      if (it.enteredDigits.isEmpty()) it
      else it.copy(enteredDigits = it.enteredDigits.dropLast(1), errorType = null)
    }
  }

  fun onBiometricAuthenticated() {
    appLockManager.unlock()
    _pinOverlay.value = PinOverlayState()
    viewModelScope.launch {
      auditRepository.recordEvent(AuditEventType.LOGIN, "APP_LOCK", null, "{\"method\":\"BIOMETRIC\"}")
    }
  }

  private fun handleCompletedSixDigits(pin: String, state: PinOverlayState) {
    if (state.isSettingUpPin) {
      if (!state.setupStepConfirm) {
        _pinOverlay.value = PinOverlayState(
          isSettingUpPin = true,
          setupStepConfirm = true,
          firstSetupPin = pin,
        )
      } else {
        if (state.firstSetupPin == pin) {
          viewModelScope.launch {
            val digest = withContext(Dispatchers.Default) { pinHasher.hashPin(pin) }
            preferencesRepository.savePinDigest(digest)
            appLockManager.unlock()
            _pinOverlay.value = PinOverlayState()
            auditRepository.recordEvent(AuditEventType.EDIT, "APP_LOCK", null, "{\"action\":\"PIN_CONFIGURED\"}")
          }
        } else {
          _pinOverlay.value = PinOverlayState(
            isSettingUpPin = true,
            setupStepConfirm = false,
            errorType = PinErrorType.PIN_MISMATCH,
          )
        }
      }
    } else {
      val storedDigest = preferences.value.pinDigest ?: return
      viewModelScope.launch {
        val valid = withContext(Dispatchers.Default) { pinHasher.verifyPin(pin, storedDigest) }
        if (valid) {
          appLockManager.unlock()
          _pinOverlay.value = PinOverlayState()
          auditRepository.recordEvent(AuditEventType.LOGIN, "APP_LOCK", null, "{\"method\":\"PIN\"}")
        } else {
          _pinOverlay.value = PinOverlayState(
            isSettingUpPin = false,
            errorType = PinErrorType.WRONG_PIN,
          )
        }
      }
    }
  }
}
