package com.mchat.core.security.keystore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates and wraps the random 32-byte SQLCipher database passphrase using an
 * Android Keystore AES-256-GCM master key (never exported, never backed up).
 */
@Singleton
class DatabasePassphraseProvider @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "mchat_sqlcipher_master_key_v1"
    private const val WRAPPED_FILE_NAME = "mchat_db_key.bin"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val PASSPHRASE_BYTES = 32
  }

  private val secureRandom = SecureRandom()

  @Synchronized
  fun getOrCreatePassphrase(): ByteArray {
    val file = File(context.noBackupFilesDir, WRAPPED_FILE_NAME)
    if (file.exists()) {
      val blob = file.readBytes()
      if (blob.size > GCM_IV_BYTES) {
        return unwrap(blob)
      }
    }
    val rawPassphrase = ByteArray(PASSPHRASE_BYTES).also(secureRandom::nextBytes)
    val wrapped = wrap(rawPassphrase)
    file.writeBytes(wrapped)
    return rawPassphrase
  }

  private fun getOrCreateKeystoreKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
    if (existing != null) return existing.secretKey

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      KEY_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .setRandomizedEncryptionRequired(true)
      .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  private fun wrap(plainBytes: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
    val iv = cipher.iv
    val ciphertext = cipher.doFinal(plainBytes)
    return ByteArray(iv.size + ciphertext.size).apply {
      System.arraycopy(iv, 0, this, 0, iv.size)
      System.arraycopy(ciphertext, 0, this, iv.size, ciphertext.size)
    }
  }

  private fun unwrap(blob: ByteArray): ByteArray {
    val iv = blob.copyOfRange(0, GCM_IV_BYTES)
    val ciphertext = blob.copyOfRange(GCM_IV_BYTES, blob.size)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(ciphertext)
  }
}
