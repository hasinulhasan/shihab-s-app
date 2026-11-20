package com.mchat.core.common.instrument

import android.content.Context
import com.mchat.core.common.AppLanguage
import com.mchat.domain.scoring.integrity.ChecksumVerificationResult
import com.mchat.domain.scoring.integrity.ChecksumVerifier
import com.mchat.domain.scoring.model.InstrumentDefinition
import com.mchat.domain.scoring.model.InstrumentManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class VerifiedInstrument(
  val definition: InstrumentDefinition,
  val verifiedSha256Hex: String,
)

sealed interface InstrumentLoadState {
  data class Verified(val instrument: VerifiedInstrument) : InstrumentLoadState
  data class IntegrityFailure(val reason: String) : InstrumentLoadState
}

/**
 * Loads versioned clinical instrument JSON assets and enforces SHA-256 checksum verification
 * before any screening can start.
 */
@Singleton
class AssetInstrumentLoader @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  companion object {
    const val MANIFEST_ASSET_PATH = "instrument/instrument_manifest.json"
  }

  private val json = Json { ignoreUnknownKeys = true }
  private val cache = mutableMapOf<String, InstrumentLoadState>()

  @Synchronized
  fun loadVerifiedInstrument(language: AppLanguage): InstrumentLoadState {
    cache[language.languageTag]?.let { return it }

    val result = runCatching {
      val manifestBytes = context.assets.open(MANIFEST_ASSET_PATH).use { it.readBytes() }
      val manifest = json.decodeFromString<InstrumentManifest>(manifestBytes.toString(Charsets.UTF_8))
      val entry = manifest.instruments.firstOrNull {
        it.language.equals(language.languageTag, ignoreCase = true)
      } ?: return InstrumentLoadState.IntegrityFailure(
        "No instrument manifest entry registered for language '${language.languageTag}'",
      )

      val instrumentBytes = context.assets.open(entry.assetPath).use { it.readBytes() }
      when (val check = ChecksumVerifier.verify(instrumentBytes, entry.sha256)) {
        is ChecksumVerificationResult.Mismatch -> InstrumentLoadState.IntegrityFailure(
          "SHA-256 checksum mismatch for ${entry.assetPath}. Expected ${check.expectedHex.take(12)}…, got ${check.actualHex.take(12)}…",
        )
        is ChecksumVerificationResult.Verified -> {
          val definition = json.decodeFromString<InstrumentDefinition>(
            instrumentBytes.toString(Charsets.UTF_8),
          )
          val validationErrors = ChecksumVerifier.validateDefinition(definition)
          if (validationErrors.isNotEmpty()) {
            InstrumentLoadState.IntegrityFailure(validationErrors.joinToString("; "))
          } else {
            InstrumentLoadState.Verified(
              VerifiedInstrument(definition = definition, verifiedSha256Hex = check.sha256Hex),
            )
          }
        }
      }
    }.getOrElse { ex ->
      InstrumentLoadState.IntegrityFailure("Instrument load error: ${ex.message ?: "unknown"}")
    }

    cache[language.languageTag] = result
    return result
  }

  fun verifyAllBundledInstruments(): Map<AppLanguage, InstrumentLoadState> =
    AppLanguage.entries.associateWith { loadVerifiedInstrument(it) }
}
