package com.mchat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mchat.domain.scoring.model.SyncStatus
import kotlinx.serialization.Serializable

@Serializable
@Entity(
  tableName = "sync_records",
  indices = [
    Index(value = ["status", "created_at"]),
    Index(value = ["artifact_type", "last_synced_at"]),
    Index(value = ["drive_file_id"]),
  ],
)
data class SyncRecordEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "artifact_type") val artifactType: String,
  @ColumnInfo(name = "related_entity_id") val relatedEntityId: String? = null,
  @ColumnInfo(name = "drive_file_id") val driveFileId: String? = null,
  @ColumnInfo(name = "drive_subfolder") val driveSubfolder: String,
  @ColumnInfo(name = "file_name") val fileName: String,
  @ColumnInfo(name = "local_sha256") val localSha256: String,
  @ColumnInfo(name = "remote_sha256") val remoteSha256: String? = null,
  @ColumnInfo(name = "backup_format_version") val backupFormatVersion: Int = 1,
  @ColumnInfo(name = "status") val status: SyncStatus = SyncStatus.PENDING_SYNC,
  @ColumnInfo(name = "error_code") val errorCode: String? = null,
  @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
  @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
  @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null,
  @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Serializable
@Entity(
  tableName = "audit_logs",
  indices = [
    Index(value = ["sequence_number"], unique = true),
    Index(value = ["event_type", "timestamp_utc"]),
    Index(value = ["entity_type", "entity_id"]),
  ],
)
data class AuditLogEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "sequence_number") val sequenceNumber: Long,
  @ColumnInfo(name = "event_type") val eventType: String,
  @ColumnInfo(name = "entity_type") val entityType: String,
  @ColumnInfo(name = "entity_id") val entityId: String? = null,
  @ColumnInfo(name = "metadata_json") val metadataJson: String = "{}",
  @ColumnInfo(name = "timestamp_utc") val timestampUtc: Long,
  @ColumnInfo(name = "previous_hash") val previousHash: String,
  @ColumnInfo(name = "entry_hash") val entryHash: String,
)

@Serializable
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
  @PrimaryKey val id: Int = 1,
  @ColumnInfo(name = "clinician_name") val clinicianName: String? = null,
  @ColumnInfo(name = "clinic_name") val clinicName: String? = null,
  @ColumnInfo(name = "language_code") val languageCode: String = "en",
  @ColumnInfo(name = "theme_mode") val themeMode: String = "LIGHT",
  @ColumnInfo(name = "auto_lock_timeout_seconds") val autoLockTimeoutSeconds: Long = 300L,
  @ColumnInfo(name = "biometric_enabled") val biometricEnabled: Boolean = false,
  @ColumnInfo(name = "reduce_transparency") val reduceTransparency: Boolean = false,
  @ColumnInfo(name = "hide_patient_names_on_home") val hidePatientNamesOnHome: Boolean = false,
  @ColumnInfo(name = "patient_code_prefix") val patientCodePrefix: String = "PT-",
  @ColumnInfo(name = "next_patient_code_seq") val nextPatientCodeSeq: Int = 1,
  @ColumnInfo(name = "recovery_passphrase_configured") val recoveryPassphraseConfigured: Boolean = false,
  @ColumnInfo(name = "drive_connected_email") val driveConnectedEmail: String? = null,
  @ColumnInfo(name = "drive_root_folder_id") val driveRootFolderId: String? = null,
  @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
)
