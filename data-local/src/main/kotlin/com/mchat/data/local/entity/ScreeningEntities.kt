package com.mchat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.InitialCategory
import com.mchat.domain.scoring.model.ScreeningStatus
import com.mchat.domain.scoring.model.SyncStatus
import kotlinx.serialization.Serializable

@Serializable
@Entity(
  tableName = "screenings",
  foreignKeys = [
    ForeignKey(
      entity = PatientEntity::class,
      parentColumns = ["id"],
      childColumns = ["patient_id"],
      onDelete = ForeignKey.RESTRICT,
    ),
    ForeignKey(
      entity = CaregiverEntity::class,
      parentColumns = ["id"],
      childColumns = ["caregiver_id"],
      onDelete = ForeignKey.RESTRICT,
    ),
    ForeignKey(
      entity = ConsentRecordEntity::class,
      parentColumns = ["id"],
      childColumns = ["consent_record_id"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["patient_id"]),
    Index(value = ["caregiver_id"]),
    Index(value = ["consent_record_id"]),
    Index(value = ["screening_date"]),
    Index(value = ["status"]),
    Index(value = ["initial_category"]),
    Index(value = ["final_outcome"]),
    Index(value = ["is_archived", "screening_date"]),
    Index(value = ["sync_status"]),
  ],
)
data class ScreeningEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "patient_id") val patientId: String,
  @ColumnInfo(name = "caregiver_id") val caregiverId: String? = null,
  @ColumnInfo(name = "consent_record_id") val consentRecordId: String,
  @ColumnInfo(name = "screening_date") val screeningDate: String,
  @ColumnInfo(name = "child_age_months") val childAgeMonths: Int,
  @ColumnInfo(name = "child_age_days_remainder") val childAgeDaysRemainder: Int,
  @ColumnInfo(name = "out_of_range_acknowledged") val outOfRangeAcknowledged: Boolean = false,
  @ColumnInfo(name = "language_used") val languageUsed: String,
  @ColumnInfo(name = "instrument_version") val instrumentVersion: String,
  @ColumnInfo(name = "instrument_checksum") val instrumentChecksum: String,
  @ColumnInfo(name = "scoring_algorithm_version") val scoringAlgorithmVersion: String,
  @ColumnInfo(name = "status") val status: ScreeningStatus = ScreeningStatus.DRAFT,
  @ColumnInfo(name = "initial_score") val initialScore: Int? = null,
  @ColumnInfo(name = "initial_category") val initialCategory: InitialCategory? = null,
  @ColumnInfo(name = "followup_score") val followupScore: Int? = null,
  @ColumnInfo(name = "final_outcome") val finalOutcome: FinalOutcome = FinalOutcome.INCOMPLETE,
  @ColumnInfo(name = "current_revision_number") val currentRevisionNumber: Int = 1,
  @ColumnInfo(name = "is_locked") val isLocked: Boolean = false,
  @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
  @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
  @ColumnInfo(name = "rescreen_due_date") val rescreenDueDate: String? = null,
  @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC,
  @ColumnInfo(name = "record_version") val recordVersion: Int = 1,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Serializable
@Entity(
  tableName = "screening_revisions",
  foreignKeys = [
    ForeignKey(
      entity = ScreeningEntity::class,
      parentColumns = ["id"],
      childColumns = ["screening_id"],
      onDelete = ForeignKey.RESTRICT,
    ),
    ForeignKey(
      entity = AuditLogEntity::class,
      parentColumns = ["id"],
      childColumns = ["audit_log_id"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["screening_id", "revision_number"], unique = true),
    Index(value = ["audit_log_id"]),
  ],
)
data class ScreeningRevisionEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "screening_id") val screeningId: String,
  @ColumnInfo(name = "revision_number") val revisionNumber: Int,
  @ColumnInfo(name = "reason_for_correction") val reasonForCorrection: String,
  @ColumnInfo(name = "previous_initial_score") val previousInitialScore: Int? = null,
  @ColumnInfo(name = "new_initial_score") val newInitialScore: Int? = null,
  @ColumnInfo(name = "previous_followup_score") val previousFollowupScore: Int? = null,
  @ColumnInfo(name = "new_followup_score") val newFollowupScore: Int? = null,
  @ColumnInfo(name = "previous_final_outcome") val previousFinalOutcome: FinalOutcome,
  @ColumnInfo(name = "new_final_outcome") val newFinalOutcome: FinalOutcome,
  @ColumnInfo(name = "diff_summary_json") val diffSummaryJson: String,
  @ColumnInfo(name = "audit_log_id") val auditLogId: String,
  @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Serializable
@Entity(
  tableName = "questionnaire_responses",
  foreignKeys = [
    ForeignKey(
      entity = ScreeningEntity::class,
      parentColumns = ["id"],
      childColumns = ["screening_id"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["screening_id", "revision_number", "item_number"], unique = true),
    Index(value = ["item_number", "is_at_risk"]),
  ],
)
data class QuestionnaireResponseEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "screening_id") val screeningId: String,
  @ColumnInfo(name = "revision_number") val revisionNumber: Int = 1,
  @ColumnInfo(name = "item_number") val itemNumber: Int,
  @ColumnInfo(name = "item_id") val itemId: String,
  @ColumnInfo(name = "response_value") val responseValue: String,
  @ColumnInfo(name = "is_at_risk") val isAtRisk: Boolean,
  @ColumnInfo(name = "point_value") val pointValue: Int,
  @ColumnInfo(name = "answered_at") val answeredAt: Long,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Serializable
@Entity(
  tableName = "followup_responses",
  foreignKeys = [
    ForeignKey(
      entity = ScreeningEntity::class,
      parentColumns = ["id"],
      childColumns = ["screening_id"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["screening_id", "revision_number", "item_number"], unique = true),
    Index(value = ["item_number", "item_result"]),
  ],
)
data class FollowUpResponseEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "screening_id") val screeningId: String,
  @ColumnInfo(name = "revision_number") val revisionNumber: Int = 1,
  @ColumnInfo(name = "item_number") val itemNumber: Int,
  @ColumnInfo(name = "item_id") val itemId: String,
  @ColumnInfo(name = "steps_trace_json") val stepsTraceJson: String,
  @ColumnInfo(name = "item_result") val itemResult: String,
  @ColumnInfo(name = "point_value") val pointValue: Int,
  @ColumnInfo(name = "clinician_notes") val clinicianNotes: String? = null,
  @ColumnInfo(name = "started_at") val startedAt: Long,
  @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Serializable
@Entity(
  tableName = "assessments",
  foreignKeys = [
    ForeignKey(
      entity = ScreeningEntity::class,
      parentColumns = ["id"],
      childColumns = ["screening_id"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["screening_id", "revision_number"], unique = true),
  ],
)
data class AssessmentEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "screening_id") val screeningId: String,
  @ColumnInfo(name = "revision_number") val revisionNumber: Int = 1,
  @ColumnInfo(name = "initial_score") val initialScore: Int,
  @ColumnInfo(name = "initial_category") val initialCategory: InitialCategory,
  @ColumnInfo(name = "followup_score") val followupScore: Int? = null,
  @ColumnInfo(name = "final_outcome") val finalOutcome: FinalOutcome,
  @ColumnInfo(name = "recommendation_code") val recommendationCode: String,
  @ColumnInfo(name = "recommendation_text_snapshot") val recommendationTextSnapshot: String,
  @ColumnInfo(name = "disclaimer_text_snapshot") val disclaimerTextSnapshot: String,
  @ColumnInfo(name = "rescreen_recommended") val rescreenRecommended: Boolean,
  @ColumnInfo(name = "rescreen_target_age_months") val rescreenTargetAgeMonths: Int? = null,
  @ColumnInfo(name = "clinician_notes") val clinicianNotes: String? = null,
  @ColumnInfo(name = "assessed_at") val assessedAt: Long,
)
