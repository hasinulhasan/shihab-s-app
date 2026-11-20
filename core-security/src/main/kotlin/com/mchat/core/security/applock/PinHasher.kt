package com.mchat.core.security.applock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class PinDigest(
  val hashBase64: String,
  val saltBase64: String,
  val iterations: Int = DEFAULT_ITERATIONS,
) {
  companion object {
    const val DEFAULT_ITERATIONS = 120_000
    const val KEY_LENGTH_BITS = 256
    const val SALT_BYTES = 16
  }
}

/**
 * Derives salted PBKDF2-HMAC-SHA256 digests for clinician PIN storage and performs
 * constant-time verification without ever logging or persisting the raw PIN.
 */
@Singleton
class PinHasher @Inject constructor() {
  private val secureRandom = SecureRandom()

  fun hashPin(pin: String, iterations: Int = PinDigest.DEFAULT_ITERATIONS): PinDigest {
    require(pin.length == 6 && pin.all { it.isDigit() }) {
      "PIN must consist of exactly 6 digits"
    }
    val salt = ByteArray(PinDigest.SALT_BYTES).also(secureRandom::nextBytes)
    val derived = derive(pin.toCharArray(), salt, iterations)
    return PinDigest(
      hashBase64 = Base64.getEncoder().encodeToString(derived),
      saltBase64 = Base64.getEncoder().encodeToString(salt),
      iterations = iterations,
    )
  }

  fun verifyPin(pin: String, stored: PinDigest): Boolean {
    if (pin.length != 6 || !pin.all { it.isDigit() }) return false
    return verifySecret(pin, stored)
  }

  fun hashSecret(secret: String, iterations: Int = PinDigest.DEFAULT_ITERATIONS): PinDigest {
    require(secret.isNotBlank()) { "Secret must not be blank" }
    val salt = ByteArray(PinDigest.SALT_BYTES).also(secureRandom::nextBytes)
    val derived = derive(secret.toCharArray(), salt, iterations)
    return PinDigest(
      hashBase64 = Base64.getEncoder().encodeToString(derived),
      saltBase64 = Base64.getEncoder().encodeToString(salt),
      iterations = iterations,
    )
  }

  fun verifySecret(secret: String, stored: PinDigest): Boolean {
    if (secret.isBlank()) return false
    val salt = runCatching { Base64.getDecoder().decode(stored.saltBase64) }.getOrNull()
      ?: return false
    val expectedHash = runCatching { Base64.getDecoder().decode(stored.hashBase64) }.getOrNull()
      ?: return false
    val actualHash = derive(secret.toCharArray(), salt, stored.iterations)
    return MessageDigest.isEqual(actualHash, expectedHash)
  }

  private fun derive(chars: CharArray, salt: ByteArray, iterations: Int): ByteArray {
    val spec = PBEKeySpec(chars, salt, iterations, PinDigest.KEY_LENGTH_BITS)
    return try {
      val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      factory.generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
      chars.fill('\u0000')
    }
  }
}
