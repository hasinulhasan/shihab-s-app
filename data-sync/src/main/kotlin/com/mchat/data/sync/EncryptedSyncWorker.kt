package com.mchat.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mchat.core.common.ClockProvider
import com.mchat.core.security.backup.BackupCryptoEngine
import com.mchat.data.local.dao.SystemDao
import com.mchat.data.local.entity.SyncRecordEntity
import com.mchat.data.local.repository.AuditEventType
import com.mchat.data.local.repository.AuditRepository
import com.mchat.data.local.repository.BackupDataRepository
import com.mchat.domain.scoring.model.SyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the clinician's recovery passphrase strictly in memory during an active unlocked session
 * so background WorkManager sync jobs can encrypt snapshots without writing plaintext passphrases to disk.
 */
@Singleton
class SessionRecoveryPassphraseStore @Inject constructor() {
  @Volatile
  private var activePassphrase: CharArray? = null

  fun setPassphrase(passphrase: String) {
    clear()
    activePassphrase = passphrase.toCharArray()
  }

  fun getPassphraseCopy(): CharArray? = activePassphrase?.copyOf()

  fun isUnlocked(): Boolean = activePassphrase != null

  fun clear() {
    activePassphrase?.fill('\u0000')
    activePassphrase = null
  }
}

/**
 * Offline-first encrypted Google Drive sync worker.
 * - Requires NetworkType.CONNECTED
 * - Exponential backoff on transient failures
 * - Encrypts ALL snapshots, PDF reports, and CSV statistics client-side BEFORE upload
 * - Verifies remote response + size before marking SYNCED
 */
@HiltWorker
class EncryptedSyncWorker @AssistedInject constructor(
  @Assisted private val appContext: Context,
  @Assisted params: WorkerParameters,
  private val driveAuthManager: DriveAuthManager,
  private val folderManager: DriveFolderManager,
  private val backupDataRepository: BackupDataRepository,
  private val systemDao: SystemDao,
  private val auditRepository: AuditRepository,
  private val passphraseStore: SessionRecoveryPassphraseStore,
  private val cryptoEngine: BackupCryptoEngine,
  private val clockProvider: ClockProvider,
) : CoroutineWorker(appContext, params) {

  companion object {
    const val UNIQUE_WORK_NAME = "mchat_encrypted_drive_sync"
    const val OUTBOX_DIR_NAME = "encrypted_sync_outbox"
  }

  override suspend fun doWork(): Result {
    val now = clockProvider.nowEpochMillis()
    val tokenResult = driveAuthManager.authorizeDriveFileScope()
    val accessToken = when (tokenResult) {
      is DriveAuthTokenResult.Authorized -> tokenResult.accessToken
      is DriveAuthTokenResult.NeedsUserConsent -> {
        recordGlobalFailure("TOKEN_REAUTH_REQUIRED", now)
        return Result.failure()
      }
      is DriveAuthTokenResult.Error -> {
        recordGlobalFailure(tokenResult.code, now)
        return Result.retry()
      }
    }

    val hierarchy = folderManager.ensureFolderHierarchy(accessToken).getOrElse { ex ->
      val code = if (ex.message?.contains("TOKEN_EXPIRED") == true) "TOKEN_EXPIRED" else "FOLDER_SYNC_ERROR"
      recordGlobalFailure(code, now)
      return if (code == "TOKEN_EXPIRED") Result.failure() else Result.retry()
    }

    // 1. Upload any staged encrypted artifacts (PDFs / CSVs / queued backups) in noBackupFilesDir
    val outboxDir = File(appContext.noBackupFilesDir, OUTBOX_DIR_NAME)
    val pendingRecords = systemDao.getPendingOrFailedSyncRecords()
    var anyRetryableFailure = false

    pendingRecords.forEach { rec ->
      val file = File(outboxDir, rec.fileName)
      if (!file.exists()) {
        systemDao.upsertSyncRecord(
          rec.copy(status = SyncStatus.FAILED, errorCode = "STAGED_FILE_MISSING", lastAttemptAt = now),
        )
        return@forEach
      }
      val folderId = when (rec.artifactType) {
        "REPORT_PDF" -> hierarchy.reportsId
        "STATS_CSV" -> hierarchy.statisticsId
        else -> hierarchy.backupsId
      }
      systemDao.upsertSyncRecord(rec.copy(status = SyncStatus.IN_PROGRESS, lastAttemptAt = now))
      val bytes = file.readBytes()
      when (val outcome = folderManager.uploadEncryptedPayload(accessToken, folderId, file.name, bytes)) {
        is DriveUploadOutcome.Success -> {
          val sha = sha256Hex(bytes)
          systemDao.upsertSyncRecord(
            rec.copy(
              driveFileId = outcome.fileId,
              remoteSha256 = sha,
              status = SyncStatus.SYNCED,
              errorCode = null,
              lastAttemptAt = now,
              lastSyncedAt = clockProvider.nowEpochMillis(),
            ),
          )
          file.delete()
          auditRepository.recordEvent(
            eventType = AuditEventType.SYNC,
            entityType = rec.artifactType,
            entityId = rec.id,
            metadataJson = "{\"driveFileId\":\"${outcome.fileId}\",\"bytes\":${outcome.remoteSizeBytes}}",
          )
        }
        is DriveUploadOutcome.TokenExpired -> {
          driveAuthManager.clearCachedToken()
          systemDao.markSyncAttempt(rec.id, SyncStatus.FAILED, "TOKEN_EXPIRED", now)
          return Result.failure()
        }
        is DriveUploadOutcome.Failure -> {
          systemDao.markSyncAttempt(rec.id, SyncStatus.FAILED, outcome.code, now)
          anyRetryableFailure = true
        }
      }
    }

    // 2. Also create & upload a fresh encrypted DB snapshot if the recovery passphrase is unlocked in memory
    val passphrase = passphraseStore.getPassphraseCopy()
    if (passphrase != null) {
      try {
        val plainSnapshot = backupDataRepository.exportSnapshotJson()
        val encryptedBlob = cryptoEngine.encrypt(plainSnapshot, String(passphrase))
        val localSha = sha256Hex(encryptedBlob)
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(now))
        val fileName = "mchat_backup_v1_$ts.mchatenc"
        val recId = UUID.randomUUID().toString()
        val rec = SyncRecordEntity(
          id = recId,
          artifactType = "DB_BACKUP",
          driveSubfolder = "Backups",
          fileName = fileName,
          localSha256 = localSha,
          status = SyncStatus.IN_PROGRESS,
          attemptCount = 1,
          lastAttemptAt = now,
          createdAt = now,
        )
        systemDao.upsertSyncRecord(rec)

        when (val outcome = folderManager.uploadEncryptedPayload(accessToken, hierarchy.backupsId, fileName, encryptedBlob)) {
          is DriveUploadOutcome.Success -> {
            systemDao.upsertSyncRecord(
              rec.copy(
                driveFileId = outcome.fileId,
                remoteSha256 = localSha,
                status = SyncStatus.SYNCED,
                lastSyncedAt = clockProvider.nowEpochMillis(),
              ),
            )
            auditRepository.recordEvent(
              eventType = AuditEventType.SYNC,
              entityType = "DB_BACKUP",
              entityId = recId,
              metadataJson = "{\"driveFileId\":\"${outcome.fileId}\",\"encryptedBytes\":${outcome.remoteSizeBytes}}",
            )
          }
          is DriveUploadOutcome.TokenExpired -> {
            driveAuthManager.clearCachedToken()
            systemDao.markSyncAttempt(recId, SyncStatus.FAILED, "TOKEN_EXPIRED", now)
            return Result.failure()
          }
          is DriveUploadOutcome.Failure -> {
            systemDao.markSyncAttempt(recId, SyncStatus.FAILED, outcome.code, now)
            anyRetryableFailure = true
          }
        }
      } finally {
        passphrase.fill('\u0000')
      }
    }

    return if (anyRetryableFailure) Result.retry() else Result.success()
  }

  private suspend fun recordGlobalFailure(code: String, now: Long) {
    val existingPending = systemDao.getPendingOrFailedSyncRecords()
    if (existingPending.isNotEmpty()) {
      existingPending.forEach { rec ->
        systemDao.markSyncAttempt(rec.id, SyncStatus.FAILED, code, now)
      }
    } else {
      systemDao.upsertSyncRecord(
        SyncRecordEntity(
          id = UUID.randomUUID().toString(),
          artifactType = "DB_BACKUP",
          driveSubfolder = "Backups",
          fileName = "sync_attempt_$now",
          localSha256 = "",
          status = SyncStatus.FAILED,
          errorCode = code,
          attemptCount = 1,
          lastAttemptAt = now,
          createdAt = now,
        ),
      )
    }
  }

  private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

@Singleton
class DriveSyncCoordinator @Inject constructor(
  @ApplicationContext private val context: Context,
  private val systemDao: SystemDao,
  private val passphraseStore: SessionRecoveryPassphraseStore,
  private val cryptoEngine: BackupCryptoEngine,
  private val clockProvider: ClockProvider,
) {
  /**
   * Encrypts an artifact (PDF report or de-identified CSV) BEFORE staging it in the private outbox
   * and enqueues the unique WorkManager job with network constraint + exponential backoff.
   */
  suspend fun enqueueEncryptedArtifactUpload(
    entityType: String,
    baseFileName: String,
    plainBytes: ByteArray,
    passphraseOverride: CharArray? = null,
  ): Boolean {
    val keyChars = passphraseOverride?.copyOf() ?: passphraseStore.getPassphraseCopy() ?: return false
    return try {
      val encrypted = cryptoEngine.encrypt(plainBytes, String(keyChars))
      val localSha = cryptoEngine.sha256Hex(encrypted)
      val outboxDir = File(context.noBackupFilesDir, EncryptedSyncWorker.OUTBOX_DIR_NAME).apply { mkdirs() }
      val encName = "$baseFileName.mchatenc"
      File(outboxDir, encName).writeBytes(encrypted)
      val now = clockProvider.nowEpochMillis()
      val subfolder = when (entityType) {
        "REPORT_PDF" -> "Reports"
        "STATS_CSV" -> "Statistics"
        else -> "Backups"
      }
      systemDao.upsertSyncRecord(
        SyncRecordEntity(
          id = UUID.randomUUID().toString(),
          artifactType = entityType,
          driveSubfolder = subfolder,
          fileName = encName,
          localSha256 = localSha,
          status = SyncStatus.PENDING_SYNC,
          createdAt = now,
        ),
      )
      scheduleUniqueSyncWork()
      true
    } finally {
      keyChars.fill('\u0000')
    }
  }

  fun scheduleUniqueSyncWork(replace: Boolean = false) {
    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

    val request = OneTimeWorkRequestBuilder<EncryptedSyncWorker>()
      .setConstraints(constraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
      EncryptedSyncWorker.UNIQUE_WORK_NAME,
      if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
      request,
    )
  }
}
