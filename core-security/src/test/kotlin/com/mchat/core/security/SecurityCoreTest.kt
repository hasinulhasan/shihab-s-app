package com.mchat.core.security

import com.mchat.core.security.audit.AuditHashChain
import com.mchat.core.security.audit.AuditHashInput
import com.mchat.core.security.backup.BackupCryptoEngine
import com.mchat.core.security.backup.BackupCryptoException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SecurityCoreTest {

  @Test
  fun backupCrypto_roundTripEncryptDecryptSucceeds() {
    val engine = BackupCryptoEngine()
    val payload = "{\"clinicalBackup\":true,\"records\":42}".toByteArray(Charsets.UTF_8)
    val encrypted = engine.encrypt(
      plaintext = payload,
      recoveryPassphrase = "Correct-Horse-Battery-99!",
      memoryKiB = 4_096,
      iterations = 1,
    )
    val decrypted = engine.decrypt(encrypted, "Correct-Horse-Battery-99!")
    assertArrayEquals(payload, decrypted)
  }

  @Test
  fun backupCrypto_wrongPassphraseThrowsInvalidPassphrase() {
    val engine = BackupCryptoEngine()
    val encrypted = engine.encrypt(
      plaintext = "sensitive-record".toByteArray(),
      recoveryPassphrase = "RightPassphrase123",
      memoryKiB = 4_096,
      iterations = 1,
    )
    try {
      engine.decrypt(encrypted, "WrongPassphrase123")
      fail("Expected BackupCryptoException.InvalidPassphrase")
    } catch (_: BackupCryptoException.InvalidPassphrase) {
      // Expected
    }
  }

  @Test
  fun backupCrypto_corruptedCiphertextFailsIntegrityCheckBeforeDecrypt() {
    val engine = BackupCryptoEngine()
    val encrypted = engine.encrypt(
      plaintext = "sensitive-record".toByteArray(),
      recoveryPassphrase = "RightPassphrase123",
      memoryKiB = 4_096,
      iterations = 1,
    )
    val tampered = encrypted.toString(Charsets.UTF_8)
      .replaceFirst("\"ciphertextBase64\":\"", "\"ciphertextBase64\":\"AA")
      .toByteArray(Charsets.UTF_8)
    try {
      engine.decrypt(tampered, "RightPassphrase123")
      fail("Expected BackupCryptoException.CorruptedIntegrity")
    } catch (_: BackupCryptoException.CorruptedIntegrity) {
      // Expected
    }
  }

  @Test
  fun auditHashChain_validatesIntactChainAndDetectsTampering() {
    val e1Input = AuditHashInput(1L, "CREATE", "PATIENT", "uuid-1", "{}", 1000L, AuditHashChain.GENESIS_HASH)
    val e1Hash = AuditHashChain.computeEntryHash(e1Input)

    val e2Input = AuditHashInput(2L, "CONSENT", "CONSENT", "uuid-2", "{}", 2000L, e1Hash)
    val e2Hash = AuditHashChain.computeEntryHash(e2Input)

    val e3Input = AuditHashInput(3L, "CREATE", "SCREENING", "uuid-3", "{}", 3000L, e2Hash)
    val e3Hash = AuditHashChain.computeEntryHash(e3Input)

    val validResult = AuditHashChain.verifyChain(
      listOf(e1Input to e1Hash, e2Input to e2Hash, e3Input to e3Hash),
    )
    assertTrue(validResult.isValid)
    assertEquals(3, validResult.totalEntriesChecked)

    // Tamper with entry 2 metadata
    val tamperedE2 = e2Input.copy(metadataJson = "{\"edited\":true}")
    val tamperedResult = AuditHashChain.verifyChain(
      listOf(e1Input to e1Hash, tamperedE2 to e2Hash, e3Input to e3Hash),
    )
    assertFalse(tamperedResult.isValid)
    assertEquals(2L, tamperedResult.brokenAtSequence)
  }
}
