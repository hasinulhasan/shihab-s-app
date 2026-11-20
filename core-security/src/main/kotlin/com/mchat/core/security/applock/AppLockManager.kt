package com.mchat.core.security.applock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.mchat.core.common.AutoLockTimeout
import com.mchat.core.common.ClockProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLockStatus {
  UNLOCKED,
  LOCKED,
}

/**
 * Coordinates clinician app lock state, background auto-lock timeouts, and manual lock requests.
 */
@Singleton
class AppLockManager @Inject constructor(
  private val clockProvider: ClockProvider,
) {
  private val _lockStatus = MutableStateFlow(AppLockStatus.UNLOCKED)
  val lockStatus: StateFlow<AppLockStatus> = _lockStatus.asStateFlow()

  private var backgroundedAtMillis: Long? = null

  fun requireInitialLockIfConfigured(isPinConfigured: Boolean) {
    if (isPinConfigured) {
      _lockStatus.value = AppLockStatus.LOCKED
    }
  }

  fun onAppBackgrounded(isPinConfigured: Boolean, timeout: AutoLockTimeout) {
    if (!isPinConfigured) return
    backgroundedAtMillis = clockProvider.nowEpochMillis()
    if (timeout == AutoLockTimeout.IMMEDIATE) {
      _lockStatus.value = AppLockStatus.LOCKED
    }
  }

  fun onAppForegrounded(isPinConfigured: Boolean, timeout: AutoLockTimeout) {
    if (!isPinConfigured) {
      backgroundedAtMillis = null
      _lockStatus.value = AppLockStatus.UNLOCKED
      return
    }
    val wentBackgroundAt = backgroundedAtMillis ?: return
    backgroundedAtMillis = null
    val elapsedMillis = (clockProvider.nowEpochMillis() - wentBackgroundAt).coerceAtLeast(0L)
    if (elapsedMillis >= timeout.seconds * 1_000L) {
      _lockStatus.value = AppLockStatus.LOCKED
    }
  }

  fun lockNow(isPinConfigured: Boolean) {
    if (isPinConfigured) {
      _lockStatus.value = AppLockStatus.LOCKED
    }
  }

  fun unlock() {
    backgroundedAtMillis = null
    _lockStatus.value = AppLockStatus.UNLOCKED
  }
}

object BiometricAuthHelper {
  private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
      BiometricManager.Authenticators.BIOMETRIC_WEAK

  fun isBiometricAvailable(context: Context): Boolean {
    val manager = BiometricManager.from(context)
    return manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
  }

  fun authenticate(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    negativeButtonText: String,
    onSuccess: () -> Unit,
    onFailure: () -> Unit = {},
  ) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        onSuccess()
      }

      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        onFailure()
      }
    }
    val prompt = BiometricPrompt(activity, executor, callback)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setNegativeButtonText(negativeButtonText)
      .setAllowedAuthenticators(AUTHENTICATORS)
      .build()
    prompt.authenticate(promptInfo)
  }
}
