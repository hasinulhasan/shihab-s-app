package com.mchat.core.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages runtime English/Bangla locale switching using AndroidX AppCompat per-app locales.
 */
@Singleton
class LocaleController @Inject constructor() {
  fun applyLanguage(language: AppLanguage) {
    val targetLocales = LocaleListCompat.forLanguageTags(language.languageTag)
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    if (currentLocales.toLanguageTags() != targetLocales.toLanguageTags()) {
      AppCompatDelegate.setApplicationLocales(targetLocales)
    }
  }

  fun currentLanguage(): AppLanguage {
    val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return AppLanguage.fromTag(tags.ifBlank { null })
  }
}

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {
  @Provides
  @Singleton
  fun provideClockProvider(): ClockProvider = SystemClockProvider()
}
