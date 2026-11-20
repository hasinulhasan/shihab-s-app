package com.mchat.core.security.audit

import java.security.MessageDigest

data class AuditHashInput(
  val sequenceNumber: Long,
  val eventType: String,
  val entityType: String,
  val entityId: String?,
  val metadataJson: String,
  val timestampUtc: Long,
  val previousHash: String,
)

data class AuditChainVerificationResult(
  val isValid: Boolean,
  val totalEntriesChecked: Int,
  val brokenAtSequence: Long? = null,
  val reason: String? = null,
)

/**
 * Computes and verifies tamper-evident SHA-256 hash chains for AuditLog entries.
 */
object AuditHashChain {
  const val GENESIS_HASH = "GENESIS"

  fun computeEntryHash(input: AuditHashInput): String {
    val canonical = listOf(
      input.sequenceNumber.toString(),
      input.eventType,
      input.entityType,
      input.entityId.orEmpty(),
      input.metadataJson,
      input.timestampUtc.toString(),
      input.previousHash,
    ).joinToString("|")
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  fun verifyChain(entries: List<Pair<AuditHashInput, String>>): AuditChainVerificationResult {
    if (entries.isEmpty()) {
      return AuditChainVerificationResult(isValid = true, totalEntriesChecked = 0)
    }
    var expectedPreviousHash = GENESIS_HASH
    var expectedSequence = 1L

    for ((input, storedHash) in entries) {
      if (input.sequenceNumber != expectedSequence) {
        return AuditChainVerificationResult(
          isValid = false,
          totalEntriesChecked = expectedSequence.toInt() - 1,
          brokenAtSequence = input.sequenceNumber,
          reason = "Missing or out-of-order sequence number (expected $expectedSequence)",
        )
      }
      if (input.previousHash != expectedPreviousHash) {
        return AuditChainVerificationResult(
          isValid = false,
          totalEntriesChecked = expectedSequence.toInt() - 1,
          brokenAtSequence = input.sequenceNumber,
          reason = "Previous hash mismatch at sequence ${input.sequenceNumber}",
        )
      }
      val recomputed = computeEntryHash(input)
      if (recomputed != storedHash) {
        return AuditChainVerificationResult(
          isValid = false,
          totalEntriesChecked = expectedSequence.toInt() - 1,
          brokenAtSequence = input.sequenceNumber,
          reason = "Entry hash tamper detected at sequence ${input.sequenceNumber}",
        )
      }
      expectedPreviousHash = storedHash
      expectedSequence++
    }
    return AuditChainVerificationResult(isValid = true, totalEntriesChecked = entries.size)
  }
}
