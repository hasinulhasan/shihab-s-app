package com.mchat.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mchat.core.common.AppLanguage
import com.mchat.core.common.AppThemeMode
import com.mchat.core.common.AutoLockTimeout
import com.mchat.core.security.applock.PinDigest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "mchat_ui_preferences",
)

data class AppShellPreferences(
  val themeMode: AppThemeMode = AppThemeMode.LIGHT,
  val language: AppLanguage = AppLanguage.ENGLISH,
  val reduceTransparency: Boolean = false,
  val biometricEnabled: Boolean = false,
  val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.FIVE_MINUTES,
  val pinDigest: PinDigest? = null,
  val hidePatientNamesOnHome: Boolean = false,
  val recoveryPassphraseConfigured: Boolean = false,
  val recoveryPassphraseVerifier: PinDigest? = null,
  val driveAccountEmail: String? = null,
  val driveRootFolderId: String? = null,
  val clinicianName: String = "",
  val clinicName: String = "",
) {
  val isPinConfigured: Boolean
    get() = pinDigest != null
}

@Singleton
class UiPreferencesRepository @Inject constructor(
  private val dataStore: DataStore<Preferences>,
) {
  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val LANGUAGE = stringPreferencesKey("language")
    val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
    val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    val AUTO_LOCK_SECONDS = longPreferencesKey("auto_lock_seconds")
    val PIN_HASH = stringPreferencesKey("pin_hash")
    val PIN_SALT = stringPreferencesKey("pin_salt")
    val PIN_ITERATIONS = intPreferencesKey("pin_iterations")
    val HIDE_NAMES_HOME = booleanPreferencesKey("hide_names_home")
    val RECOVERY_CONFIGURED = booleanPreferencesKey("recovery_configured")
    val RECOVERY_HASH = stringPreferencesKey("recovery_hash")
    val RECOVERY_SALT = stringPreferencesKey("recovery_salt")
    val RECOVERY_ITERATIONS = intPreferencesKey("recovery_iterations")
    val DRIVE_ACCOUNT_EMAIL = stringPreferencesKey("drive_account_email")
    val DRIVE_ROOT_FOLDER_ID = stringPreferencesKey("drive_root_folder_id")
    val CLINICIAN_NAME = stringPreferencesKey("clinician_name")
    val CLINIC_NAME = stringPreferencesKey("clinic_name")
  }

  val preferences: Flow<AppShellPreferences> = dataStore.data.map { prefs ->
    val pinHash = prefs[Keys.PIN_HASH]
    val pinSalt = prefs[Keys.PIN_SALT]
    val pinIterations = prefs[Keys.PIN_ITERATIONS] ?: PinDigest.DEFAULT_ITERATIONS
    val digest = if (!pinHash.isNullOrBlank() && !pinSalt.isNullOrBlank()) {
      PinDigest(hashBase64 = pinHash, saltBase64 = pinSalt, iterations = pinIterations)
    } else {
      null
    }

    val recHash = prefs[Keys.RECOVERY_HASH]
    val recSalt = prefs[Keys.RECOVERY_SALT]
    val recIterations = prefs[Keys.RECOVERY_ITERATIONS] ?: PinDigest.DEFAULT_ITERATIONS
    val recoveryVerifier = if (!recHash.isNullOrBlank() && !recSalt.isNullOrBlank()) {
      PinDigest(hashBase64 = recHash, saltBase64 = recSalt, iterations = recIterations)
    } else {
      null
    }

    AppShellPreferences(
      themeMode = prefs[Keys.THEME_MODE]
        ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
        ?: AppThemeMode.LIGHT,
      language = AppLanguage.fromTag(prefs[Keys.LANGUAGE]),
      reduceTransparency = prefs[Keys.REDUCE_TRANSPARENCY] ?: false,
      biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
      autoLockTimeout = AutoLockTimeout.fromSeconds(
        prefs[Keys.AUTO_LOCK_SECONDS] ?: AutoLockTimeout.FIVE_MINUTES.seconds,
      ),
      pinDigest = digest,
      hidePatientNamesOnHome = prefs[Keys.HIDE_NAMES_HOME] ?: false,
      recoveryPassphraseConfigured = prefs[Keys.RECOVERY_CONFIGURED] ?: false,
      recoveryPassphraseVerifier = recoveryVerifier,
      driveAccountEmail = prefs[Keys.DRIVE_ACCOUNT_EMAIL]?.ifBlank { null },
      driveRootFolderId = prefs[Keys.DRIVE_ROOT_FOLDER_ID]?.ifBlank { null },
      clinicianName = prefs[Keys.CLINICIAN_NAME] ?: "",
      clinicName = prefs[Keys.CLINIC_NAME] ?: "",
    )
  }

  suspend fun getCurrent(): AppShellPreferences = preferences.first()

  suspend fun setThemeMode(mode: AppThemeMode) {
    dataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setLanguage(language: AppLanguage) {
    dataStore.edit { it[Keys.LANGUAGE] = language.languageTag }
  }

  suspend fun setReduceTransparency(enabled: Boolean) {
    dataStore.edit { it[Keys.REDUCE_TRANSPARENCY] = enabled }
  }

  suspend fun setBiometricEnabled(enabled: Boolean) {
    dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
  }

  suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
    dataStore.edit { it[Keys.AUTO_LOCK_SECONDS] = timeout.seconds }
  }

  suspend fun savePinDigest(digest: PinDigest) {
    dataStore.edit {
      it[Keys.PIN_HASH] = digest.hashBase64
      it[Keys.PIN_SALT] = digest.saltBase64
      it[Keys.PIN_ITERATIONS] = digest.iterations
    }
  }

  suspend fun setHidePatientNamesOnHome(hide: Boolean) {
    dataStore.edit { it[Keys.HIDE_NAMES_HOME] = hide }
  }

  suspend fun saveRecoveryPassphraseVerifier(digest: PinDigest) {
    dataStore.edit {
      it[Keys.RECOVERY_CONFIGURED] = true
      it[Keys.RECOVERY_HASH] = digest.hashBase64
      it[Keys.RECOVERY_SALT] = digest.saltBase64
      it[Keys.RECOVERY_ITERATIONS] = digest.iterations
    }
  }

  suspend fun setDriveAccount(email: String?, rootFolderId: String? = null) {
    dataStore.edit {
      if (email.isNullOrBlank()) {
        it.remove(Keys.DRIVE_ACCOUNT_EMAIL)
        it.remove(Keys.DRIVE_ROOT_FOLDER_ID)
      } else {
        it[Keys.DRIVE_ACCOUNT_EMAIL] = email.trim()
        if (!rootFolderId.isNullOrBlank()) {
          it[Keys.DRIVE_ROOT_FOLDER_ID] = rootFolderId
        }
      }
    }
  }

  suspend fun setDriveRootFolderId(folderId: String) {
    dataStore.edit { it[Keys.DRIVE_ROOT_FOLDER_ID] = folderId }
  }

  suspend fun setClinicianProfile(clinicianName: String, clinicName: String) {
    dataStore.edit {
      it[Keys.CLINICIAN_NAME] = clinicianName.trim()
      it[Keys.CLINIC_NAME] = clinicName.trim()
    }
  }
}

@Module
@InstallIn(SingletonComponent::class)
object LocalPreferencesModule {
  @Provides
  @Singleton
  fun providePreferencesDataStore(
    @ApplicationContext context: Context,
  ): DataStore<Preferences> = context.uiPreferencesDataStore
}
