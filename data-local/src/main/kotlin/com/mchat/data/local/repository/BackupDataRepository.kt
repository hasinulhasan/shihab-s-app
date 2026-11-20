package com.mchat.data.local.repository

import androidx.room.withTransaction
import com.mchat.core.common.ClockProvider
import com.mchat.data.local.dao.PatientDao
import com.mchat.data.local.dao.ScreeningDao
import com.mchat.data.local.dao.SystemDao
import com.mchat.data.local.db.MChatDatabase
import com.mchat.data.local.entity.AssessmentEntity
import com.mchat.data.local.entity.CaregiverEntity
import com.mchat.data.local.entity.ConsentRecordEntity
import com.mchat.data.local.entity.FollowUpResponseEntity
import com.mchat.data.local.entity.PatientEntity
import com.mchat.data.local.entity.QuestionnaireResponseEntity
import com.mchat.data.local.entity.ScreeningEntity
import com.mchat.data.local.entity.ScreeningRevisionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ClinicalBackupSnapshot(
  val schemaVersion: Int = 1,
  val exportedAtUtc: Long,
  val patients: List<PatientEntity>,
  val caregivers: List<CaregiverEntity>,
  val consents: List<ConsentRecordEntity>,
  val screenings: List<ScreeningEntity>,
  val questionnaireResponses: List<QuestionnaireResponseEntity>,
  val followUpResponses: List<FollowUpResponseEntity>,
  val assessments: List<AssessmentEntity>,
  val revisions: List<ScreeningRevisionEntity>,
)

data class RestoreConflictItem(
  val entityType: String,
  val entityId: String,
  val localVersion: Int,
  val remoteVersion: Int,
  val localUpdatedAt: Long,
  val remoteUpdatedAt: Long,
)

data class RestoreDiffSummary(
  val newPatients: Int,
  val updatedPatients: Int,
  val newScreenings: Int,
  val updatedScreenings: Int,
  val conflicts: List<RestoreConflictItem>,
)

enum class RestoreConflictStrategy {
  MERGE_PREFER_NEWER,
  MERGE_KEEP_LOCAL_ON_CONFLICT,
  REPLACE_ALL_LOCAL,
}

@Singleton
class BackupDataRepository @Inject constructor(
  private val database: MChatDatabase,
  private val patientDao: PatientDao,
  private val screeningDao: ScreeningDao,
  private val systemDao: SystemDao,
  private val auditRepository: AuditRepository,
  private val clockProvider: ClockProvider,
) {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  suspend fun exportSnapshotJson(): ByteArray {
    val snapshot = ClinicalBackupSnapshot(
      schemaVersion = 1,
      exportedAtUtc = clockProvider.nowEpochMillis(),
      patients = patientDao.getAllPatientsSnapshot(),
      caregivers = patientDao.getAllCaregiversSnapshot(),
      consents = patientDao.getAllConsentsSnapshot(),
      screenings = screeningDao.getAllScreeningsSnapshot(),
      questionnaireResponses = screeningDao.getAllQuestionnaireResponsesSnapshot(),
      followUpResponses = screeningDao.getAllFollowUpResponsesSnapshot(),
      assessments = screeningDao.getAllAssessmentsSnapshot(),
      revisions = screeningDao.getAllRevisionsSnapshot(),
    )
    return json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
  }

  fun parseSnapshot(bytes: ByteArray): ClinicalBackupSnapshot =
    json.decodeFromString(bytes.toString(Charsets.UTF_8))

  suspend fun inspectRestoreDiff(snapshot: ClinicalBackupSnapshot): RestoreDiffSummary {
    val localPatients = patientDao.getAllPatientsSnapshot().associateBy { it.id }
    val localScreenings = screeningDao.getAllScreeningsSnapshot().associateBy { it.id }
    val conflicts = mutableListOf<RestoreConflictItem>()

    var newPatients = 0
    var updatedPatients = 0
    snapshot.patients.forEach { remote ->
      val local = localPatients[remote.id]
      if (local == null) {
        newPatients++
      } else if (local.recordVersion != remote.recordVersion || local.updatedAt != remote.updatedAt) {
        updatedPatients++
        if (local.updatedAt > remote.updatedAt || local.recordVersion > remote.recordVersion) {
          conflicts += RestoreConflictItem(
            entityType = "PATIENT",
            entityId = remote.id,
            localVersion = local.recordVersion,
            remoteVersion = remote.recordVersion,
            localUpdatedAt = local.updatedAt,
            remoteUpdatedAt = remote.updatedAt,
          )
        }
      }
    }

    var newScreenings = 0
    var updatedScreenings = 0
    snapshot.screenings.forEach { remote ->
      val local = localScreenings[remote.id]
      if (local == null) {
        newScreenings++
      } else if (local.recordVersion != remote.recordVersion || local.updatedAt != remote.updatedAt) {
        updatedScreenings++
        if (local.updatedAt > remote.updatedAt || local.recordVersion > remote.recordVersion) {
          conflicts += RestoreConflictItem(
            entityType = "SCREENING",
            entityId = remote.id,
            localVersion = local.recordVersion,
            remoteVersion = remote.recordVersion,
            localUpdatedAt = local.updatedAt,
            remoteUpdatedAt = remote.updatedAt,
          )
        }
      }
    }

    return RestoreDiffSummary(
      newPatients = newPatients,
      updatedPatients = updatedPatients,
      newScreenings = newScreenings,
      updatedScreenings = updatedScreenings,
      conflicts = conflicts,
    )
  }

  suspend fun applyRestore(
    snapshot: ClinicalBackupSnapshot,
    strategy: RestoreConflictStrategy,
  ) {
    database.withTransaction {
      if (strategy == RestoreConflictStrategy.REPLACE_ALL_LOCAL) {
        systemDao.deleteAllRevisions()
        systemDao.deleteAllAssessments()
        systemDao.deleteAllFollowUpResponses()
        systemDao.deleteAllQuestionnaireResponses()
        systemDao.deleteAllScreenings()
        systemDao.deleteAllConsentRecords()
        systemDao.deleteAllCaregivers()
        systemDao.deleteAllPatients()
      }

      val localPatients = patientDao.getAllPatientsSnapshot().associateBy { it.id }
      snapshot.patients.forEach { remote ->
        val local = localPatients[remote.id]
        val shouldWrite = shouldOverwrite(local?.recordVersion, local?.updatedAt, remote.recordVersion, remote.updatedAt, strategy)
        if (shouldWrite) patientDao.upsertPatient(remote)
      }

      snapshot.caregivers.forEach { patientDao.upsertCaregiver(it) }
      snapshot.consents.forEach { patientDao.upsertConsent(it) }

      val localScreenings = screeningDao.getAllScreeningsSnapshot().associateBy { it.id }
      snapshot.screenings.forEach { remote ->
        val local = localScreenings[remote.id]
        val shouldWrite = shouldOverwrite(local?.recordVersion, local?.updatedAt, remote.recordVersion, remote.updatedAt, strategy)
        if (shouldWrite) screeningDao.upsertScreening(remote)
      }

      screeningDao.upsertQuestionnaireResponses(snapshot.questionnaireResponses)
      snapshot.followUpResponses.forEach { screeningDao.upsertFollowUpResponse(it) }
      snapshot.assessments.forEach { screeningDao.upsertAssessment(it) }
    }

    auditRepository.recordEvent(
      eventType = AuditEventType.RESTORE,
      entityType = "DATABASE",
      entityId = null,
      metadataJson = "{\"strategy\":\"${strategy.name}\",\"patients\":${snapshot.patients.size},\"screenings\":${snapshot.screenings.size}}",
    )
  }

  suspend fun wipeAllClinicalRecords() {
    database.withTransaction {
      systemDao.deleteAllRevisions()
      systemDao.deleteAllAssessments()
      systemDao.deleteAllFollowUpResponses()
      systemDao.deleteAllQuestionnaireResponses()
      systemDao.deleteAllScreenings()
      systemDao.deleteAllConsentRecords()
      systemDao.deleteAllCaregivers()
      systemDao.deleteAllPatients()
    }
    auditRepository.recordEvent(
      eventType = AuditEventType.DELETE,
      entityType = "DATABASE",
      entityId = null,
      metadataJson = "{\"wipeAll\":true}",
    )
  }

  private fun shouldOverwrite(
    localVersion: Int?,
    localUpdatedAt: Long?,
    remoteVersion: Int,
    remoteUpdatedAt: Long,
    strategy: RestoreConflictStrategy,
  ): Boolean {
    if (localVersion == null || localUpdatedAt == null) return true
    return when (strategy) {
      RestoreConflictStrategy.REPLACE_ALL_LOCAL -> true
      RestoreConflictStrategy.MERGE_KEEP_LOCAL_ON_CONFLICT -> false
      RestoreConflictStrategy.MERGE_PREFER_NEWER ->
        remoteVersion > localVersion || (remoteVersion == localVersion && remoteUpdatedAt >= localUpdatedAt)
    }
  }
}
