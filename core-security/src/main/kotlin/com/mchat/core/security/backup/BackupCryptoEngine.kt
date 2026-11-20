package com.mchat.core.security.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupKdfParams(
  val algorithm: String = ALGORITHM_ARGON2ID,
  val saltBase64: String,
  val memoryKiB: Int = DEFAULT_ARGON2_MEMORY_KIB,
  val iterations: Int = DEFAULT_ARGON2_ITERATIONS,
  val parallelism: Int = DEFAULT_ARGON2_PARALLELISM,
) {
  companion object {
    const val ALGORITHM_ARGON2ID = "ARGON2ID"
    const val ALGORITHM_PBKDF2 = "PBKDF2_HMAC_SHA256"
    const val DEFAULT_ARGON2_MEMORY_KIB = 32_768 // 32 MiB — balanced for mid-range mobile devices
    const val DEFAULT_ARGON2_ITERATIONS = 3
    const val DEFAULT_ARGON2_PARALLELISM = 1
    const val DEFAULT_PBKDF2_ITERATIONS = 310_000
  }
}

/**
 * Self-describing encrypted backup container (used for DB backups, PDFs, and CSV exports).
 * Uses envelope encryption:
 *  - Random 256-bit DEK encrypts the payload with AES-256-GCM.
 *  - User recovery passphrase derives a 256-bit KEK via Argon2id (or PBKDF2 fallback) to wrap the DEK.
 *  - SHA-256 integrity hash covers format version, KDF params, nonces, wrapped DEK, and ciphertext+GCM tag.
 */
@Serializable
data class EncryptedBackupEnvelope(
  val formatVersion: Int = CURRENT_FORMAT_VERSION,
  val kdf: BackupKdfParams,
  val wrapNonceBase64: String,
  val wrappedDekBase64: String,
  val payloadNonceBase64: String,
  val ciphertextBase64: String,
  val integritySha256Hex: String,
) {
  companion object {
    const val CURRENT_FORMAT_VERSION = 1
  }
}

sealed class BackupCryptoException(message: String, cause: Throwable? = null) :
  Exception(message, cause) {
  class CorruptedIntegrity(message: String) : BackupCryptoException(message)
  class InvalidPassphrase(message: String, cause: Throwable? = null) :
    BackupCryptoException(message, cause)
  class UnsupportedFormat(message: String) : BackupCryptoException(message)
}

@Singleton
class BackupCryptoEngine @Inject constructor() {
  private val secureRandom = SecureRandom()
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  companion object {
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_NONCE_BYTES = 12
    private const val KEY_BYTES = 32
    private const val SALT_BYTES = 16
  }

  fun encrypt(
    plaintext: ByteArray,
    recoveryPassphrase: String,
    memoryKiB: Int = BackupKdfParams.DEFAULT_ARGON2_MEMORY_KIB,
    iterations: Int = BackupKdfParams.DEFAULT_ARGON2_ITERATIONS,
  ): ByteArray {
    require(recoveryPassphrase.length >= 8) {
      "Recovery passphrase must be at least 8 characters"
    }
    val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
    val kdfParams = BackupKdfParams(
      algorithm = BackupKdfParams.ALGORITHM_ARGON2ID,
      saltBase64 = encodeB64(salt),
      memoryKiB = memoryKiB,
      iterations = iterations,
      parallelism = BackupKdfParams.DEFAULT_ARGON2_PARALLELISM,
    )

    val dek = ByteArray(KEY_BYTES).also(secureRandom::nextBytes)
    val kek = deriveKek(recoveryPassphrase, kdfParams)
    return try {
      val payloadNonce = ByteArray(GCM_NONCE_BYTES).also(secureRandom::nextBytes)
      val ciphertext = aesGcmEncrypt(dek, payloadNonce, plaintext)

      val wrapNonce = ByteArray(GCM_NONCE_BYTES).also(secureRandom::nextBytes)
      val wrappedDek = aesGcmEncrypt(kek, wrapNonce, dek)

      val wrapNonceB64 = encodeB64(wrapNonce)
      val wrappedDekB64 = encodeB64(wrappedDek)
      val payloadNonceB64 = encodeB64(payloadNonce)
      val ciphertextB64 = encodeB64(ciphertext)

      val integrityHex = computeEnvelopeIntegrity(
        formatVersion = EncryptedBackupEnvelope.CURRENT_FORMAT_VERSION,
        kdf = kdfParams,
        wrapNonceBase64 = wrapNonceB64,
        wrappedDekBase64 = wrappedDekB64,
        payloadNonceBase64 = payloadNonceB64,
        ciphertextBase64 = ciphertextB64,
      )

      val envelope = EncryptedBackupEnvelope(
        formatVersion = EncryptedBackupEnvelope.CURRENT_FORMAT_VERSION,
        kdf = kdfParams,
        wrapNonceBase64 = wrapNonceB64,
        wrappedDekBase64 = wrappedDekB64,
        payloadNonceBase64 = payloadNonceB64,
        ciphertextBase64 = ciphertextB64,
        integritySha256Hex = integrityHex,
      )
      json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
    } finally {
      dek.fill(0)
      kek.fill(0)
    }
  }

  fun decrypt(
    envelopeBytes: ByteArray,
    recoveryPassphrase: String,
  ): ByteArray {
    val envelope = try {
      json.decodeFromString<EncryptedBackupEnvelope>(envelopeBytes.toString(Charsets.UTF_8))
    } catch (e: Exception) {
      throw BackupCryptoException.CorruptedIntegrity("Backup file is malformed or truncated")
    }

    if (envelope.formatVersion != EncryptedBackupEnvelope.CURRENT_FORMAT_VERSION) {
      throw BackupCryptoException.UnsupportedFormat(
        "Unsupported backup format version: ${envelope.formatVersion}",
      )
    }

    val expectedIntegrity = computeEnvelopeIntegrity(
      formatVersion = envelope.formatVersion,
      kdf = envelope.kdf,
      wrapNonceBase64 = envelope.wrapNonceBase64,
      wrappedDekBase64 = envelope.wrappedDekBase64,
      payloadNonceBase64 = envelope.payloadNonceBase64,
      ciphertextBase64 = envelope.ciphertextBase64,
    )
    if (!MessageDigest.isEqual(
        expectedIntegrity.toByteArray(Charsets.UTF_8),
        envelope.integritySha256Hex.toByteArray(Charsets.UTF_8),
      )
    ) {
      throw BackupCryptoException.CorruptedIntegrity("Backup integrity hash verification failed")
    }

    val kek = deriveKek(recoveryPassphrase, envelope.kdf)
    val dek = try {
      aesGcmDecrypt(
        keyBytes = kek,
        nonce = decodeB64(envelope.wrapNonceBase64),
        ciphertext = decodeB64(envelope.wrappedDekBase64),
      )
    } catch (e: GeneralSecurityException) {
      kek.fill(0)
      throw BackupCryptoException.InvalidPassphrase("Incorrect recovery passphrase", e)
    }

    return try {
      aesGcmDecrypt(
        keyBytes = dek,
        nonce = decodeB64(envelope.payloadNonceBase64),
        ciphertext = decodeB64(envelope.ciphertextBase64),
      )
    } catch (e: GeneralSecurityException) {
      throw BackupCryptoException.CorruptedIntegrity("Encrypted payload authentication tag failed")
    } finally {
      dek.fill(0)
      kek.fill(0)
    }
  }

  fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
  }

  private fun deriveKek(passphrase: String, kdf: BackupKdfParams): ByteArray {
    val salt = decodeB64(kdf.saltBase64)
    return when (kdf.algorithm) {
      BackupKdfParams.ALGORITHM_ARGON2ID -> {
        val output = ByteArray(KEY_BYTES)
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
          .withSalt(salt)
          .withMemoryAsKB(kdf.memoryKiB)
          .withIterations(kdf.iterations)
          .withParallelism(kdf.parallelism)
          .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val passBytes = passphrase.toByteArray(Charsets.UTF_8)
        try {
          generator.generateBytes(passBytes, output)
        } finally {
          passBytes.fill(0)
        }
        output
      }
      BackupKdfParams.ALGORITHM_PBKDF2 -> {
        val chars = passphrase.toCharArray()
        val spec = PBEKeySpec(chars, salt, kdf.iterations, KEY_BYTES * 8)
        try {
          SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
          spec.clearPassword()
          chars.fill('\u0000')
        }
      }
      else -> throw BackupCryptoException.UnsupportedFormat("Unsupported KDF: ${kdf.algorithm}")
    }
  }

  private fun aesGcmEncrypt(keyBytes: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(AES_GCM)
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
    return cipher.doFinal(plaintext)
  }

  private fun aesGcmDecrypt(keyBytes: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(AES_GCM)
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
    return cipher.doFinal(ciphertext)
  }

  private fun computeEnvelopeIntegrity(
    formatVersion: Int,
    kdf: BackupKdfParams,
    wrapNonceBase64: String,
    wrappedDekBase64: String,
    payloadNonceBase64: String,
    ciphertextBase64: String,
  ): String {
    val canonical = listOf(
      formatVersion.toString(),
      kdf.algorithm,
      kdf.saltBase64,
      kdf.memoryKiB.toString(),
      kdf.iterations.toString(),
      kdf.parallelism.toString(),
      wrapNonceBase64,
      wrappedDekBase64,
      payloadNonceBase64,
      ciphertextBase64,
    ).joinToString("|")
    return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
  }

  private fun encodeB64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
  private fun decodeB64(value: String): ByteArray = Base64.getDecoder().decode(value)
}
