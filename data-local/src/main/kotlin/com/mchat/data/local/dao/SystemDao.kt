package com.mchat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mchat.data.local.entity.AppSettingsEntity
import com.mchat.data.local.entity.AuditLogEntity
import com.mchat.data.local.entity.SyncRecordEntity
import com.mchat.domain.scoring.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemDao {
  // AuditLog (append-only)
  @Query("SELECT * FROM audit_logs ORDER BY sequence_number ASC")
  suspend fun getAllAuditLogsOrdered(): List<AuditLogEntity>

  @Query("SELECT * FROM audit_logs ORDER BY sequence_number DESC LIMIT :limit")
  fun observeRecentAuditLogs(limit: Int = 100): Flow<List<AuditLogEntity>>

  @Query("SELECT * FROM audit_logs ORDER BY sequence_number DESC LIMIT 1")
  suspend fun getLastAuditLog(): AuditLogEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertAuditLog(entry: AuditLogEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAuditLog(entry: AuditLogEntity)

  // SyncRecord
  @Query("SELECT * FROM sync_records ORDER BY created_at DESC")
  fun observeSyncRecords(): Flow<List<SyncRecordEntity>>

  @Query("SELECT * FROM sync_records WHERE status IN ('PENDING_SYNC', 'FAILED') ORDER BY created_at ASC")
  suspend fun getPendingOrFailedSyncRecords(): List<SyncRecordEntity>

  @Query("SELECT * FROM sync_records WHERE status = 'SYNCED' ORDER BY last_synced_at DESC LIMIT 1")
  fun observeLastSuccessfulSync(): Flow<SyncRecordEntity?>

  @Query("SELECT COUNT(*) FROM sync_records WHERE status = 'PENDING_SYNC'")
  fun observePendingSyncCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSyncRecord(record: SyncRecordEntity)

  @Query("UPDATE sync_records SET status = :status, error_code = :errorCode, attempt_count = attempt_count + 1, last_attempt_at = :attemptAt WHERE id = :id")
  suspend fun markSyncAttempt(id: String, status: SyncStatus, errorCode: String?, attemptAt: Long)

  // AppSettings (singleton id = 1)
  @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
  fun observeAppSettings(): Flow<AppSettingsEntity?>

  @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
  suspend fun getAppSettings(): AppSettingsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAppSettings(settings: AppSettingsEntity)

  // Safe destructive wipe (only invoked after explicit PIN/biometric re-auth & confirmation)
  @Query("DELETE FROM screening_revisions")
  suspend fun deleteAllRevisions()

  @Query("DELETE FROM assessments")
  suspend fun deleteAllAssessments()

  @Query("DELETE FROM followup_responses")
  suspend fun deleteAllFollowUpResponses()

  @Query("DELETE FROM questionnaire_responses")
  suspend fun deleteAllQuestionnaireResponses()

  @Query("DELETE FROM screenings")
  suspend fun deleteAllScreenings()

  @Query("DELETE FROM consent_records")
  suspend fun deleteAllConsentRecords()

  @Query("DELETE FROM caregivers")
  suspend fun deleteAllCaregivers()

  @Query("DELETE FROM patients")
  suspend fun deleteAllPatients()
}
