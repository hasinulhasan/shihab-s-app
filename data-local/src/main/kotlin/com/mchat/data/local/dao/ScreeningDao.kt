package com.mchat.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mchat.data.local.entity.AssessmentEntity
import com.mchat.data.local.entity.FollowUpResponseEntity
import com.mchat.data.local.entity.QuestionnaireResponseEntity
import com.mchat.data.local.entity.ScreeningEntity
import com.mchat.data.local.entity.ScreeningRevisionEntity
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.InitialCategory
import com.mchat.domain.scoring.model.ScreeningStatus
import kotlinx.coroutines.flow.Flow

data class ScreeningWithPatientSummary(
  @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "patient_id") val patientId: String,
  @ColumnInfo(name = "patient_name") val patientName: String,
  @ColumnInfo(name = "patient_code") val patientCode: String,
  @ColumnInfo(name = "screening_date") val screeningDate: String,
  @ColumnInfo(name = "child_age_months") val childAgeMonths: Int,
  @ColumnInfo(name = "language_used") val languageUsed: String,
  @ColumnInfo(name = "status") val status: ScreeningStatus,
  @ColumnInfo(name = "initial_score") val initialScore: Int?,
  @ColumnInfo(name = "initial_category") val initialCategory: InitialCategory?,
  @ColumnInfo(name = "followup_score") val followupScore: Int?,
  @ColumnInfo(name = "final_outcome") val finalOutcome: FinalOutcome,
  @ColumnInfo(name = "is_locked") val isLocked: Boolean,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface ScreeningDao {
  @Query(
    """
    SELECT s.id, s.patient_id, p.full_name AS patient_name, p.patient_code,
           s.screening_date, s.child_age_months, s.language_used, s.status,
           s.initial_score, s.initial_category, s.followup_score, s.final_outcome,
           s.is_locked, s.updated_at
    FROM screenings s
    INNER JOIN patients p ON p.id = s.patient_id
    WHERE s.is_archived = 0
    ORDER BY s.updated_at DESC
    """,
  )
  fun observeActiveScreeningsWithPatient(): Flow<List<ScreeningWithPatientSummary>>

  @Query("SELECT * FROM screenings WHERE patient_id = :patientId AND is_archived = 0 ORDER BY screening_date DESC, updated_at DESC")
  fun observeScreeningsForPatient(patientId: String): Flow<List<ScreeningEntity>>

  @Query("SELECT * FROM screenings WHERE id = :screeningId LIMIT 1")
  fun observeScreeningById(screeningId: String): Flow<ScreeningEntity?>

  @Query("SELECT * FROM screenings WHERE id = :screeningId LIMIT 1")
  suspend fun getScreeningById(screeningId: String): ScreeningEntity?

  @Query(
    """
    SELECT * FROM screenings
    WHERE is_archived = 0 AND status IN ('DRAFT', 'INITIAL_IN_PROGRESS', 'FOLLOWUP_IN_PROGRESS')
    ORDER BY updated_at DESC LIMIT 1
    """,
  )
  suspend fun getLatestUnfinishedScreening(): ScreeningEntity?

  @Query("SELECT * FROM screenings")
  suspend fun getAllScreeningsSnapshot(): List<ScreeningEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertScreening(screening: ScreeningEntity)

  @Update
  suspend fun updateScreening(screening: ScreeningEntity)

  // Questionnaire Responses
  @Query("SELECT * FROM questionnaire_responses WHERE screening_id = :screeningId AND revision_number = :revision ORDER BY item_number ASC")
  fun observeQuestionnaireResponses(screeningId: String, revision: Int): Flow<List<QuestionnaireResponseEntity>>

  @Query("SELECT * FROM questionnaire_responses WHERE screening_id = :screeningId AND revision_number = :revision ORDER BY item_number ASC")
  suspend fun getQuestionnaireResponses(screeningId: String, revision: Int): List<QuestionnaireResponseEntity>

  @Query("SELECT * FROM questionnaire_responses")
  suspend fun getAllQuestionnaireResponsesSnapshot(): List<QuestionnaireResponseEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertQuestionnaireResponse(response: QuestionnaireResponseEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertQuestionnaireResponses(responses: List<QuestionnaireResponseEntity>)

  // Follow-Up Responses
  @Query("SELECT * FROM followup_responses WHERE screening_id = :screeningId AND revision_number = :revision ORDER BY item_number ASC")
  fun observeFollowUpResponses(screeningId: String, revision: Int): Flow<List<FollowUpResponseEntity>>

  @Query("SELECT * FROM followup_responses WHERE screening_id = :screeningId AND revision_number = :revision ORDER BY item_number ASC")
  suspend fun getFollowUpResponses(screeningId: String, revision: Int): List<FollowUpResponseEntity>

  @Query("SELECT * FROM followup_responses")
  suspend fun getAllFollowUpResponsesSnapshot(): List<FollowUpResponseEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertFollowUpResponse(response: FollowUpResponseEntity)

  // Assessments
  @Query("SELECT * FROM assessments WHERE screening_id = :screeningId AND revision_number = :revision LIMIT 1")
  fun observeAssessment(screeningId: String, revision: Int): Flow<AssessmentEntity?>

  @Query("SELECT * FROM assessments WHERE screening_id = :screeningId AND revision_number = :revision LIMIT 1")
  suspend fun getAssessment(screeningId: String, revision: Int): AssessmentEntity?

  @Query("SELECT * FROM assessments")
  suspend fun getAllAssessmentsSnapshot(): List<AssessmentEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAssessment(assessment: AssessmentEntity)

  // Revisions
  @Query("SELECT * FROM screening_revisions WHERE screening_id = :screeningId ORDER BY revision_number DESC")
  fun observeRevisions(screeningId: String): Flow<List<ScreeningRevisionEntity>>

  @Query("SELECT * FROM screening_revisions")
  suspend fun getAllRevisionsSnapshot(): List<ScreeningRevisionEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertRevision(revision: ScreeningRevisionEntity)
}
