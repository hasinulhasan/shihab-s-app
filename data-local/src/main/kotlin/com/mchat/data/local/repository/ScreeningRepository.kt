package com.mchat.data.local.repository

import androidx.room.withTransaction
import com.mchat.core.common.ClockProvider
import com.mchat.core.common.age.AgeCalculator
import com.mchat.data.local.dao.ScreeningDao
import com.mchat.data.local.dao.ScreeningWithPatientSummary
import com.mchat.data.local.db.MChatDatabase
import com.mchat.data.local.entity.AssessmentEntity
import com.mchat.data.local.entity.FollowUpResponseEntity
import com.mchat.data.local.entity.QuestionnaireResponseEntity
import com.mchat.data.local.entity.ScreeningEntity
import com.mchat.data.local.entity.ScreeningRevisionEntity
import com.mchat.domain.scoring.engine.FinalScreeningEvaluation
import com.mchat.domain.scoring.engine.ScoringEngine
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.FollowUpResultValue
import com.mchat.domain.scoring.model.InitialResponseValue
import com.mchat.domain.scoring.model.InstrumentDefinition
import com.mchat.domain.scoring.model.ScreeningStatus
import com.mchat.domain.scoring.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreeningRepository @Inject constructor(
  private val database: MChatDatabase,
  private val screeningDao: ScreeningDao,
  private val auditRepository: AuditRepository,
  private val clockProvider: ClockProvider,
) {
  fun observeActiveScreeningsWithPatient(): Flow<List<ScreeningWithPatientSummary>> =
    screeningDao.observeActiveScreeningsWithPatient()

  fun observeScreeningsForPatient(patientId: String): Flow<List<ScreeningEntity>> =
    screeningDao.observeScreeningsForPatient(patientId)

  fun observeScreening(screeningId: String): Flow<ScreeningEntity?> =
    screeningDao.observeScreeningById(screeningId)

  fun observeQuestionnaireResponses(screeningId: String, revision: Int): Flow<List<QuestionnaireResponseEntity>> =
    screeningDao.observeQuestionnaireResponses(screeningId, revision)

  fun observeFollowUpResponses(screeningId: String, revision: Int): Flow<List<FollowUpResponseEntity>> =
    screeningDao.observeFollowUpResponses(screeningId, revision)

  fun observeAssessment(screeningId: String, revision: Int): Flow<AssessmentEntity?> =
    screeningDao.observeAssessment(screeningId, revision)

  fun observeRevisions(screeningId: String): Flow<List<ScreeningRevisionEntity>> =
    screeningDao.observeRevisions(screeningId)

  suspend fun getScreeningById(screeningId: String): ScreeningEntity? =
    screeningDao.getScreeningById(screeningId)

  suspend fun getLatestUnfinishedScreening(): ScreeningEntity? =
    screeningDao.getLatestUnfinishedScreening()

  suspend fun getQuestionnaireResponses(screeningId: String, revision: Int): List<QuestionnaireResponseEntity> =
    screeningDao.getQuestionnaireResponses(screeningId, revision)

  suspend fun getFollowUpResponses(screeningId: String, revision: Int): List<FollowUpResponseEntity> =
    screeningDao.getFollowUpResponses(screeningId, revision)

  suspend fun getAssessment(screeningId: String, revision: Int): AssessmentEntity? =
    screeningDao.getAssessment(screeningId, revision)

  suspend fun createDraftScreening(
    patientId: String,
    caregiverId: String?,
    consentRecordId: String,
    patientDobIso: String,
    outOfRangeAcknowledged: Boolean,
    instrument: InstrumentDefinition,
    verifiedChecksum: String,
    screeningDateIso: String = LocalDate.now().toString(),
  ): ScreeningEntity {
    val now = clockProvider.nowEpochMillis()
    val age = AgeCalculator.calculate(LocalDate.parse(patientDobIso), LocalDate.parse(screeningDateIso))
    val entity = ScreeningEntity(
      id = UUID.randomUUID().toString(),
      patientId = patientId,
      caregiverId = caregiverId,
      consentRecordId = consentRecordId,
      screeningDate = screeningDateIso,
      childAgeMonths = age.totalMonths,
      childAgeDaysRemainder = age.remainingDays,
      outOfRangeAcknowledged = outOfRangeAcknowledged,
      languageUsed = instrument.language,
      instrumentVersion = instrument.instrumentVersion,
      instrumentChecksum = verifiedChecksum,
      scoringAlgorithmVersion = instrument.scoringAlgorithmVersion,
      status = ScreeningStatus.DRAFT,
      finalOutcome = FinalOutcome.INCOMPLETE,
      currentRevisionNumber = 1,
      isLocked = false,
      syncStatus = SyncStatus.PENDING_SYNC,
      recordVersion = 1,
      createdAt = now,
      updatedAt = now,
    )
    screeningDao.upsertScreening(entity)
    auditRepository.recordEvent(
      eventType = AuditEventType.CREATE,
      entityType = "SCREENING",
      entityId = entity.id,
      metadataJson = "{\"lang\":\"${instrument.language}\",\"instrument\":\"${instrument.instrumentVersion}\"}",
    )
    return entity
  }

  /**
   * Instant autosave of a single Stage 1 questionnaire answer.
   * Refuses direct mutation if the screening is locked/completed (enforcing revision immutability).
   */
  suspend fun saveInitialAnswer(
    screeningId: String,
    instrument: InstrumentDefinition,
    itemNumber: Int,
    response: InitialResponseValue,
  ) {
    val screening = screeningDao.getScreeningById(screeningId)
      ?: error("Screening $screeningId not found")
    require(!screening.isLocked) { "Completed screenings are immutable; create a revision to correct." }

    val item = instrument.items.firstOrNull { it.officialNumber == itemNumber }
      ?: error("Item $itemNumber not found in instrument")
    val scored = ScoringEngine.scoreItem(item, response)
    val now = clockProvider.nowEpochMillis()

    database.withTransaction {
      val existing = screeningDao.getQuestionnaireResponses(screeningId, screening.currentRevisionNumber)
        .firstOrNull { it.itemNumber == itemNumber }
      val entity = QuestionnaireResponseEntity(
        id = existing?.id ?: UUID.randomUUID().toString(),
        screeningId = screeningId,
        revisionNumber = screening.currentRevisionNumber,
        itemNumber = itemNumber,
        itemId = item.itemId,
        responseValue = response.name,
        isAtRisk = scored.isAtRisk,
        pointValue = scored.pointValue,
        answeredAt = existing?.answeredAt ?: now,
        updatedAt = now,
      )
      screeningDao.upsertQuestionnaireResponse(entity)

      val nextStatus = if (screening.status == ScreeningStatus.DRAFT) {
        ScreeningStatus.INITIAL_IN_PROGRESS
      } else {
        screening.status
      }
      screeningDao.updateScreening(
        screening.copy(
          status = nextStatus,
          syncStatus = SyncStatus.PENDING_SYNC,
          updatedAt = now,
        ),
      )
    }
  }

  /**
   * Scores Stage 1 after all 20 items are answered and advances the state machine cleanly.
   */
  suspend fun completeInitialStage(
    screeningId: String,
    instrument: InstrumentDefinition,
    patientDobIso: String,
  ): ScreeningEntity {
    val screening = screeningDao.getScreeningById(screeningId)
      ?: error("Screening $screeningId not found")
    val responses = screeningDao.getQuestionnaireResponses(screeningId, screening.currentRevisionNumber)
    val responseMap = responses.associate {
      it.itemNumber to InitialResponseValue.valueOf(it.responseValue)
    }

    val initialEval = ScoringEngine.evaluateInitial(
      instrument = instrument,
      responsesByItemNumber = responseMap,
      childAgeMonths = screening.childAgeMonths,
    )
    val now = clockProvider.nowEpochMillis()
    val targetAgeMonths = initialEval.rescreenTargetAgeMonths
    val rescreenDueDate = if (initialEval.rescreenRecommended && targetAgeMonths != null) {
      runCatching {
        LocalDate.parse(patientDobIso).plusMonths(targetAgeMonths.toLong()).toString()
      }.getOrNull()
    } else {
      null
    }

    val updated = when (initialEval.nextStatus) {
      ScreeningStatus.FOLLOWUP_NOT_REQUIRED,
      ScreeningStatus.HIGH_DIRECT_REFERRAL,
      -> {
        val finalEval = ScoringEngine.evaluateFinal(
          instrument = instrument,
          responsesByItemNumber = responseMap,
          followUpResultsByItemNumber = emptyMap(),
          childAgeMonths = screening.childAgeMonths,
        )
        persistCompletedAssessment(
          screening = screening,
          finalEval = finalEval,
          instrument = instrument,
          rescreenDueDate = rescreenDueDate,
          now = now,
        )
      }

      else -> {
        val modScreening = screening.copy(
          status = ScreeningStatus.FOLLOWUP_REQUIRED,
          initialScore = initialEval.initialScore,
          initialCategory = initialEval.initialCategory,
          finalOutcome = FinalOutcome.FOLLOWUP_PENDING,
          isLocked = false,
          rescreenDueDate = null,
          syncStatus = SyncStatus.PENDING_SYNC,
          recordVersion = screening.recordVersion + 1,
          updatedAt = now,
        )
        screeningDao.updateScreening(modScreening)
        modScreening
      }
    }

    auditRepository.recordEvent(
      eventType = AuditEventType.EDIT,
      entityType = "SCREENING",
      entityId = screeningId,
      metadataJson = "{\"stage\":\"INITIAL_COMPLETE\",\"score\":${initialEval.initialScore},\"category\":\"${initialEval.initialCategory.name}\"}",
    )
    return updated
  }

  /**
   * Autosaves a single Stage 2 Follow-Up item result and traversal trace.
   */
  suspend fun saveFollowUpItem(
    screeningId: String,
    itemNumber: Int,
    itemId: String,
    stepsTraceJson: String,
    result: FollowUpResultValue,
    clinicianNotes: String?,
  ) {
    val screening = screeningDao.getScreeningById(screeningId)
      ?: error("Screening $screeningId not found")
    require(!screening.isLocked) { "Completed screenings are immutable; create a revision to correct." }

    val now = clockProvider.nowEpochMillis()
    database.withTransaction {
      val existing = screeningDao.getFollowUpResponses(screeningId, screening.currentRevisionNumber)
        .firstOrNull { it.itemNumber == itemNumber }
      screeningDao.upsertFollowUpResponse(
        FollowUpResponseEntity(
          id = existing?.id ?: UUID.randomUUID().toString(),
          screeningId = screeningId,
          revisionNumber = screening.currentRevisionNumber,
          itemNumber = itemNumber,
          itemId = itemId,
          stepsTraceJson = stepsTraceJson,
          itemResult = result.name,
          pointValue = if (result == FollowUpResultValue.FAIL) 1 else 0,
          clinicianNotes = clinicianNotes?.trim()?.ifBlank { null },
          startedAt = existing?.startedAt ?: now,
          completedAt = now,
          updatedAt = now,
        ),
      )
      screeningDao.updateScreening(
        screening.copy(
          status = ScreeningStatus.FOLLOWUP_IN_PROGRESS,
          syncStatus = SyncStatus.PENDING_SYNC,
          updatedAt = now,
        ),
      )
    }
  }

  /**
   * Finalizes Stage 2 Follow-Up, writes the Assessment snapshot, and locks the screening as immutable.
   */
  suspend fun completeFollowUpStage(
    screeningId: String,
    instrument: InstrumentDefinition,
    clinicianNotes: String? = null,
  ): ScreeningEntity {
    val screening = screeningDao.getScreeningById(screeningId)
      ?: error("Screening $screeningId not found")
    val initialResponses = screeningDao.getQuestionnaireResponses(screeningId, screening.currentRevisionNumber)
      .associate { it.itemNumber to InitialResponseValue.valueOf(it.responseValue) }
    val followUpResponses = screeningDao.getFollowUpResponses(screeningId, screening.currentRevisionNumber)
      .mapNotNull { row ->
        runCatching { row.itemNumber to FollowUpResultValue.valueOf(row.itemResult) }.getOrNull()
      }.toMap()

    val finalEval = ScoringEngine.evaluateFinal(
      instrument = instrument,
      responsesByItemNumber = initialResponses,
      followUpResultsByItemNumber = followUpResponses,
      childAgeMonths = screening.childAgeMonths,
    )
    require(finalEval.finalStatus == ScreeningStatus.COMPLETED) {
      "All at-risk items must have a Follow-Up PASS or FAIL result before finalizing"
    }

    val now = clockProvider.nowEpochMillis()
    val updated = persistCompletedAssessment(
      screening = screening,
      finalEval = finalEval,
      instrument = instrument,
      rescreenDueDate = null,
      now = now,
      clinicianNotes = clinicianNotes,
    )

    auditRepository.recordEvent(
      eventType = AuditEventType.EDIT,
      entityType = "SCREENING",
      entityId = screeningId,
      metadataJson = "{\"stage\":\"COMPLETED\",\"followUpScore\":${finalEval.followUpScore},\"outcome\":\"${finalEval.finalOutcome.name}\"}",
    )
    return updated
  }

  /**
   * Immutability-preserving correction workflow: creates revision N+1, copies prior responses into
   * the new revision without mutating revision N, unlocks for clinician edit, and records an AuditLog.
   */
  suspend fun createCorrectionRevision(
    screeningId: String,
    reasonForCorrection: String,
  ): ScreeningEntity {
    val screening = screeningDao.getScreeningById(screeningId)
      ?: error("Screening $screeningId not found")
    require(screening.isLocked) { "Only completed screenings require a formal correction revision." }

    val now = clockProvider.nowEpochMillis()
    val oldRev = screening.currentRevisionNumber
    val newRev = oldRev + 1

    val audit = auditRepository.recordEvent(
      eventType = AuditEventType.CORRECTION,
      entityType = "SCREENING",
      entityId = screeningId,
      metadataJson = "{\"fromRevision\":$oldRev,\"toRevision\":$newRev}",
    )

    return database.withTransaction {
      val priorResponses = screeningDao.getQuestionnaireResponses(screeningId, oldRev)
      val clonedResponses = priorResponses.map { row ->
        row.copy(
          id = UUID.randomUUID().toString(),
          revisionNumber = newRev,
          updatedAt = now,
        )
      }
      screeningDao.upsertQuestionnaireResponses(clonedResponses)

      screeningDao.insertRevision(
        ScreeningRevisionEntity(
          id = UUID.randomUUID().toString(),
          screeningId = screeningId,
          revisionNumber = newRev,
          reasonForCorrection = reasonForCorrection.trim(),
          previousInitialScore = screening.initialScore,
          newInitialScore = screening.initialScore,
          previousFollowupScore = screening.followupScore,
          newFollowupScore = null,
          previousFinalOutcome = screening.finalOutcome,
          newFinalOutcome = FinalOutcome.INCOMPLETE,
          diffSummaryJson = "{\"clonedResponses\":${clonedResponses.size}}",
          auditLogId = audit.id,
          createdAt = now,
        ),
      )

      val reopened = screening.copy(
        currentRevisionNumber = newRev,
        status = ScreeningStatus.INITIAL_IN_PROGRESS,
        isLocked = false,
        finalOutcome = FinalOutcome.INCOMPLETE,
        followupScore = null,
        syncStatus = SyncStatus.PENDING_SYNC,
        recordVersion = screening.recordVersion + 1,
        updatedAt = now,
      )
      screeningDao.updateScreening(reopened)
      reopened
    }
  }

  suspend fun abandonScreening(screeningId: String) {
    val screening = screeningDao.getScreeningById(screeningId) ?: return
    val now = clockProvider.nowEpochMillis()
    screeningDao.updateScreening(
      screening.copy(
        status = ScreeningStatus.ABANDONED,
        finalOutcome = FinalOutcome.INCOMPLETE,
        syncStatus = SyncStatus.PENDING_SYNC,
        recordVersion = screening.recordVersion + 1,
        updatedAt = now,
      ),
    )
    auditRepository.recordEvent(
      eventType = AuditEventType.EDIT,
      entityType = "SCREENING",
      entityId = screeningId,
      metadataJson = "{\"status\":\"ABANDONED\"}",
    )
  }

  suspend fun archiveScreening(screeningId: String) {
    val screening = screeningDao.getScreeningById(screeningId) ?: return
    val now = clockProvider.nowEpochMillis()
    screeningDao.updateScreening(
      screening.copy(
        status = ScreeningStatus.ARCHIVED,
        isArchived = true,
        archivedAt = now,
        syncStatus = SyncStatus.PENDING_SYNC,
        recordVersion = screening.recordVersion + 1,
        updatedAt = now,
      ),
    )
    auditRepository.recordEvent(
      eventType = AuditEventType.ARCHIVE,
      entityType = "SCREENING",
      entityId = screeningId,
      metadataJson = "{\"archived\":true}",
    )
  }

  private suspend fun persistCompletedAssessment(
    screening: ScreeningEntity,
    finalEval: FinalScreeningEvaluation,
    instrument: InstrumentDefinition,
    rescreenDueDate: String?,
    now: Long,
    clinicianNotes: String? = null,
  ): ScreeningEntity = database.withTransaction {
    val updated = screening.copy(
      status = ScreeningStatus.COMPLETED,
      initialScore = finalEval.initialScore,
      initialCategory = finalEval.initialCategory,
      followupScore = finalEval.followUpScore,
      finalOutcome = finalEval.finalOutcome,
      isLocked = true,
      rescreenDueDate = rescreenDueDate,
      syncStatus = SyncStatus.PENDING_SYNC,
      recordVersion = screening.recordVersion + 1,
      updatedAt = now,
    )
    screeningDao.updateScreening(updated)
    screeningDao.upsertAssessment(
      AssessmentEntity(
        id = UUID.randomUUID().toString(),
        screeningId = screening.id,
        revisionNumber = screening.currentRevisionNumber,
        initialScore = finalEval.initialScore,
        initialCategory = finalEval.initialCategory,
        followupScore = finalEval.followUpScore,
        finalOutcome = finalEval.finalOutcome,
        recommendationCode = finalEval.recommendation.code,
        recommendationTextSnapshot = finalEval.recommendation.body,
        disclaimerTextSnapshot = instrument.nonDiagnosticDisclaimer,
        rescreenRecommended = finalEval.rescreenRecommended,
        rescreenTargetAgeMonths = finalEval.rescreenTargetAgeMonths,
        clinicianNotes = clinicianNotes?.trim()?.ifBlank { null },
        assessedAt = now,
      ),
    )
    updated
  }
}
