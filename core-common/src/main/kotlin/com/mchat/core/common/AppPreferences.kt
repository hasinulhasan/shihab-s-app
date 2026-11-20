package com.mchat.core.common

import kotlinx.serialization.Serializable

@Serializable
enum class AppLanguage(val languageTag: String) {
  ENGLISH("en"),
  BANGLA("bn");

  companion object {
    fun fromTag(tag: String?): AppLanguage =
      entries.firstOrNull { tag?.startsWith(it.languageTag, ignoreCase = true) == true } ?: ENGLISH
  }
}

@Serializable
enum class AppThemeMode {
  LIGHT,
  DARK,
  SYSTEM,
}

@Serializable
enum class AutoLockTimeout(val seconds: Long) {
  IMMEDIATE(0L),
  ONE_MINUTE(60L),
  FIVE_MINUTES(300L),
  FIFTEEN_MINUTES(900L);

  companion object {
    fun fromSeconds(seconds: Long): AutoLockTimeout =
      entries.firstOrNull { it.seconds == seconds } ?: FIVE_MINUTES
  }
}

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val messageKey: String) : UiState<Nothing>
}

interface ClockProvider {
  fun nowEpochMillis(): Long
}

class SystemClockProvider : ClockProvider {
  override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
