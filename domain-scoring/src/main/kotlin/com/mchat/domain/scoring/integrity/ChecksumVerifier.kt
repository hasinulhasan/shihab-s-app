package com.mchat.domain.scoring.integrity

import com.mchat.domain.scoring.model.InstrumentDefinition
import java.security.MessageDigest

sealed interface ChecksumVerificationResult {
  data class Verified(val sha256Hex: String) : ChecksumVerificationResult
  data class Mismatch(val expectedHex: String, val actualHex: String) : ChecksumVerificationResult
}

/**
 * Pure-Kotlin SHA-256 checksum & structural validator for instrument bundles.
 * Refuses to allow screening execution if content integrity fails.
 */
object ChecksumVerifier {
  fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
  }

  fun verify(bytes: ByteArray, expectedSha256Hex: String): ChecksumVerificationResult {
    val actual = sha256Hex(bytes)
    val normalizedExpected = expectedSha256Hex.trim().lowercase()
    return if (MessageDigest.isEqual(actual.toByteArray(), normalizedExpected.toByteArray())) {
      ChecksumVerificationResult.Verified(actual)
    } else {
      ChecksumVerificationResult.Mismatch(expectedHex = normalizedExpected, actualHex = actual)
    }
  }

  fun validateDefinition(definition: InstrumentDefinition): List<String> {
    val errors = mutableListOf<String>()
    if (definition.items.size != definition.scoringConfig.totalItems) {
      errors += "Item count (${definition.items.size}) does not match totalItems (${definition.scoringConfig.totalItems})"
    }
    val expectedNumbers = (1..definition.scoringConfig.totalItems).toList()
    val actualNumbers = definition.items.map { it.officialNumber }.sorted()
    if (actualNumbers != expectedNumbers) {
      errors += "Official item numbers must be contiguous 1..${definition.scoringConfig.totalItems}"
    }
    definition.items.forEach { item ->
      val entryExists = item.followUpFlow.nodes.any { it.nodeId == item.followUpFlow.entryNodeId }
      if (!entryExists) {
        errors += "Item ${item.officialNumber} missing follow-up entry node ${item.followUpFlow.entryNodeId}"
      }
    }
    return errors
  }
}
