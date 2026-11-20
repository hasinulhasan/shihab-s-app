package com.mchat.data.sync

import com.mchat.core.security.backup.BackupCryptoEngine
import com.mchat.core.security.backup.BackupCryptoException
import com.mchat.data.local.repository.BackupDataRepository
import com.mchat.data.local.repository.ClinicalBackupSnapshot
import com.mchat.data.local.repository.RestoreConflictStrategy
import com.mchat.data.local.repository.RestoreDiffSummary
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupInspectionResult {
  data class Ready(
    val fileItem: DriveFileItem,
    val snapshot: ClinicalBackupSnapshot,
    val diff: RestoreDiffSummary,
  ) : BackupInspectionResult

  data class IntegrityCorrupted(val reason: String) : BackupInspectionResult
  data class WrongPassphrase(val reason: String) : BackupInspectionResult
  data class Error(val reason: String) : BackupInspectionResult
}

@Singleton
class DriveRestoreManager @Inject constructor(
  private val driveAuthManager: DriveAuthManager,
  private val folderManager: DriveFolderManager,
  private val backupDataRepository: BackupDataRepository,
  private val cryptoEngine: BackupCryptoEngine,
) {
  suspend fun listAvailableBackups(): Result<List<DriveFileItem>> {
    val auth = driveAuthManager.authorizeDriveFileScope()
    val token = (auth as? DriveAuthTokenResult.Authorized)?.accessToken
      ?: return Result.failure(IllegalStateException("Google Drive authorization required"))
    val folders = folderManager.ensureFolderHierarchy(token).getOrElse { return Result.failure(it) }
    return folderManager.listBackups(token, folders.backupsId)
  }

  /**
   * Downloads, verifies SHA-256 integrity BEFORE decryption, decrypts with the clinician's recovery
   * passphrase, and computes a merge/conflict diff summary without modifying local data yet.
   */
  suspend fun downloadAndInspectBackup(
    fileItem: DriveFileItem,
    recoveryPassphrase: CharArray,
  ): BackupInspectionResult {
    val auth = driveAuthManager.authorizeDriveFileScope()
    val token = (auth as? DriveAuthTokenResult.Authorized)?.accessToken
      ?: return BackupInspectionResult.Error("Drive authorization required")

    val encryptedBytes = folderManager.downloadFileBytes(token, fileItem.id).getOrElse { ex ->
      return BackupInspectionResult.Error("Download failed: ${ex.message ?: "network error"}")
    }

    return inspectEncryptedBlob(fileItem, encryptedBytes, recoveryPassphrase)
  }

  suspend fun inspectEncryptedBlob(
    fileItem: DriveFileItem,
    encryptedBytes: ByteArray,
    recoveryPassphrase: CharArray,
  ): BackupInspectionResult {
    val plaintext = try {
      cryptoEngine.decrypt(encryptedBytes, String(recoveryPassphrase))
    } catch (e: BackupCryptoException.CorruptedIntegrity) {
      return BackupInspectionResult.IntegrityCorrupted(e.message ?: "Backup integrity check failed")
    } catch (e: BackupCryptoException.UnsupportedFormat) {
      return BackupInspectionResult.IntegrityCorrupted(e.message ?: "Unsupported backup format")
    } catch (e: BackupCryptoException.InvalidPassphrase) {
      return BackupInspectionResult.WrongPassphrase(e.message ?: "Incorrect recovery passphrase")
    } catch (e: Exception) {
      return BackupInspectionResult.Error(e.message ?: "Unexpected decryption error")
    }

    val snapshot = runCatching { backupDataRepository.parseSnapshot(plaintext) }.getOrElse { ex ->
      return BackupInspectionResult.IntegrityCorrupted("Backup JSON schema invalid: ${ex.message}")
    }
    val diff = backupDataRepository.inspectRestoreDiff(snapshot)
    return BackupInspectionResult.Ready(fileItem = fileItem, snapshot = snapshot, diff = diff)
  }

  suspend fun confirmAndApplyRestore(
    snapshot: ClinicalBackupSnapshot,
    strategy: RestoreConflictStrategy,
  ) {
    backupDataRepository.applyRestore(snapshot, strategy)
  }
}
