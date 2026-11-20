package com.mchat.data.local.repository

import com.mchat.core.common.ClockProvider
import com.mchat.core.security.audit.AuditChainVerificationResult
import com.mchat.core.security.audit.AuditHashChain
import com.mchat.core.security.audit.AuditHashInput
import com.mchat.data.local.dao.SystemDao
import com.mchat.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

object AuditEventType {
  const val CREATE = "CREATE"
  const val EDIT = "EDIT"
  const val CORRECTION = "CORRECTION"
  const val ARCHIVE = "ARCHIVE"
  const val EXPORT = "EXPORT"
  const val RESTORE = "RESTORE"
  const val DELETE = "DELETE"
  const val LOGIN = "LOGIN"
  const val SYNC = "SYNC"
  const val CONSENT = "CONSENT"
}

/**
 * Append-only tamper-evident audit repository. Strictly logs IDs and event codes — NEVER patient PHI.
 */
@Singleton
class AuditRepository @Inject constructor(
  private val systemDao: SystemDao,
  private val clockProvider: ClockProvider,
) {
  private val appendMutex = Mutex()

  fun observeRecentLogs(limit: Int = 100): Flow<List<AuditLogEntity>> =
    systemDao.observeRecentAuditLogs(limit)

  suspend fun recordEvent(
    eventType: String,
    entityType: String,
    entityId: String? = null,
    metadataJson: String = "{}",
  ): AuditLogEntity = appendMutex.withLock {
    val last = systemDao.getLastAuditLog()
    val nextSeq = (last?.sequenceNumber ?: 0L) + 1L
    val prevHash = last?.entryHash ?: AuditHashChain.GENESIS_HASH
    val now = clockProvider.nowEpochMillis()
    val input = AuditHashInput(
      sequenceNumber = nextSeq,
      eventType = eventType,
      entityType = entityType,
      entityId = entityId,
      metadataJson = metadataJson,
      timestampUtc = now,
      previousHash = prevHash,
    )
    val entryHash = AuditHashChain.computeEntryHash(input)
    val entity = AuditLogEntity(
      id = UUID.randomUUID().toString(),
      sequenceNumber = nextSeq,
      eventType = eventType,
      entityType = entityType,
      entityId = entityId,
      metadataJson = metadataJson,
      timestampUtc = now,
      previousHash = prevHash,
      entryHash = entryHash,
    )
    systemDao.insertAuditLog(entity)
    entity
  }

  suspend fun verifyIntegrityChain(): AuditChainVerificationResult {
    val entries = systemDao.getAllAuditLogsOrdered()
    return AuditHashChain.verifyChain(
      entries.map { row ->
        AuditHashInput(
          sequenceNumber = row.sequenceNumber,
          eventType = row.eventType,
          entityType = row.entityType,
          entityId = row.entityId,
          metadataJson = row.metadataJson,
          timestampUtc = row.timestampUtc,
          previousHash = row.previousHash,
        ) to row.entryHash
      },
    )
  }
}
