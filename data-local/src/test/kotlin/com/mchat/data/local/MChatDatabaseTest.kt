package com.mchat.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mchat.core.common.ClockProvider
import com.mchat.data.local.db.MChatDatabase
import com.mchat.data.local.repository.AuditRepository
import com.mchat.data.local.repository.BackupDataRepository
import com.mchat.data.local.repository.PatientRegistrationInput
import com.mchat.data.local.repository.PatientRepository
import com.mchat.data.local.repository.PatientSaveResult
import com.mchat.data.local.repository.RestoreConflictStrategy
import com.mchat.data.local.repository.ScreeningRepository
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.FollowUpFlowDefinition
import com.mchat.domain.scoring.model.FollowUpNode
import com.mchat.domain.scoring.model.FollowUpNodeType
import com.mchat.domain.scoring.model.FollowUpResponseLabels
import com.mchat.domain.scoring.model.FollowUpResultValue
import com.mchat.domain.scoring.model.InitialCategory
import com.mchat.domain.scoring.model.InitialCategoryRule
import com.mchat.domain.scoring.model.InitialResponseLabels
import com.mchat.domain.scoring.model.InitialResponseValue
import com.mchat.domain.scoring.model.InstrumentDefinition
import com.mchat.domain.scoring.model.InstrumentItem
import com.mchat.domain.scoring.model.InstrumentResponseOptions
import com.mchat.domain.scoring.model.IntendedAgeRange
import com.mchat.domain.scoring.model.LowRiskRescreenRule
import com.mchat.domain.scoring.model.RecommendationContent
import com.mchat.domain.scoring.model.ScoringConfig
import com.mchat.domain.scoring.model.ScreeningStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MChatDatabaseTest {

  private lateinit var db: MChatDatabase
  private lateinit var auditRepo: AuditRepository
  private lateinit var patientRepo: PatientRepository
  private lateinit var screeningRepo: ScreeningRepository
  private lateinit var backupRepo: BackupDataRepository
  private var fakeTime = 1_750_000_000_000L
  private val clock = object : ClockProvider {
    override fun nowEpochMillis(): Long = fakeTime.also { fakeTime += 1_000L }
  }

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, MChatDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    auditRepo = AuditRepository(db.systemDao(), clock)
    patientRepo = PatientRepository(db, db.patientDao(), db.systemDao(), auditRepo, clock)
    screeningRepo = ScreeningRepository(db, db.screeningDao(), auditRepo, clock)
    backupRepo = BackupDataRepository(db, db.patientDao(), db.screeningDao(), db.systemDao(), auditRepo, clock)
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun testInstrument(): InstrumentDefinition {
    val reverseItems = setOf(2, 5, 12)
    val items = (1..20).map { n ->
      InstrumentItem(
        itemId = "mchat_r_%02d".format(n),
        officialNumber = n,
        text = "Item $n",
        atRiskResponse = if (n in reverseItems) InitialResponseValue.YES else InitialResponseValue.NO,
        pointValue = 1,
        followUpFlow = FollowUpFlowDefinition(
          entryNodeId = "n1",
          nodes = listOf(FollowUpNode("n1", FollowUpNodeType.CLINICIAN_PASS_FAIL, "Follow up $n")),
        ),
      )
    }
    return InstrumentDefinition(
      schemaVersion = 1,
      instrumentId = "mchat-r-f",
      instrumentVersion = "test-v1",
      scoringAlgorithmVersion = "1.0.0",
      language = "en",
      displayTitle = "M-CHAT-R/F",
      copyrightNotice = "Copyright 2009",
      permissionNotice = "Official",
      nonDiagnosticDisclaimer = "Screening only",
      intendedAgeMonths = IntendedAgeRange(16, 30),
      responseOptions = InstrumentResponseOptions(
        InitialResponseLabels("Yes", "No"),
        FollowUpResponseLabels("Pass", "Fail"),
      ),
      scoringConfig = ScoringConfig(
        totalItems = 20,
        initialCategories = listOf(
          InitialCategoryRule(InitialCategory.LOW, 0, 2),
          InitialCategoryRule(InitialCategory.MODERATE, 3, 7),
          InitialCategoryRule(InitialCategory.HIGH, 8, 20),
        ),
        followUpPositiveThreshold = 2,
        highRiskAllowsOptionalFollowUp = false,
        lowRiskRescreenRule = LowRiskRescreenRule(24, 24),
      ),
      items = items,
      recommendations = mapOf(
        "NEGATIVE_LOW_INITIAL_24M_OR_OLDER" to RecommendationContent("NEGATIVE_LOW_INITIAL_24M_OR_OLDER", "Low", "No rescreen"),
        "NEGATIVE_LOW_INITIAL_UNDER_24M" to RecommendationContent("NEGATIVE_LOW_INITIAL_UNDER_24M", "Low", "Rescreen at 24m"),
        "FOLLOWUP_REQUIRED_MODERATE" to RecommendationContent("FOLLOWUP_REQUIRED_MODERATE", "Mod", "Do follow-up"),
        "NEGATIVE_AFTER_FOLLOWUP" to RecommendationContent("NEGATIVE_AFTER_FOLLOWUP", "Neg", "Negative after FU"),
        "POSITIVE_AFTER_FOLLOWUP" to RecommendationContent("POSITIVE_AFTER_FOLLOWUP", "Pos", "Positive after FU"),
        "POSITIVE_HIGH_INITIAL" to RecommendationContent("POSITIVE_HIGH_INITIAL", "High", "Refer immediately"),
      ),
    )
  }

  @Test
  fun registerPatient_duplicateDetection_and_auditChainValid() = runTest {
    val first = patientRepo.registerPatientWithConsent(
      PatientRegistrationInput(
        patientCode = "CLN-0001",
        fullName = "Amina Rahman",
        dateOfBirthIso = "2024-01-15",
        sex = "FEMALE",
        extraFieldsJson = "{}",
        clinicalNotes = null,
        caregiverName = "Farhana Rahman",
        caregiverRelationship = "Mother",
        caregiverPhone = null,
        caregiverEmail = null,
        consentedByName = "Farhana Rahman",
        consentedByRelationship = "Mother",
        consentScope = "SCREENING_AND_LOCAL_RECORD",
      ),
    )
    assertTrue(first is PatientSaveResult.Success)

    val dup = patientRepo.registerPatientWithConsent(
      PatientRegistrationInput(
        patientCode = "CLN-0001",
        fullName = "Duplicate Child",
        dateOfBirthIso = "2024-02-10",
        sex = "MALE",
        extraFieldsJson = "{}",
        clinicalNotes = null,
        caregiverName = "Guardian",
        caregiverRelationship = "Father",
        caregiverPhone = null,
        caregiverEmail = null,
        consentedByName = "Guardian",
        consentedByRelationship = "Father",
        consentScope = "SCREENING_AND_LOCAL_RECORD",
      ),
    )
    assertTrue(dup is PatientSaveResult.DuplicateCode)

    val chain = auditRepo.verifyIntegrityChain()
    assertTrue(chain.isValid)
    assertTrue(chain.totalEntriesChecked >= 2)
  }

  @Test
  fun autosaveResume_followUpFlow_immutabilityAndCorrectionRevision() = runTest {
    val reg = patientRepo.registerPatientWithConsent(
      PatientRegistrationInput(
        patientCode = null,
        fullName = "Tariq Hasan",
        dateOfBirthIso = "2024-03-01",
        sex = "MALE",
        extraFieldsJson = "{}",
        clinicalNotes = null,
        caregiverName = "Nadia Hasan",
        caregiverRelationship = "Mother",
        caregiverPhone = null,
        caregiverEmail = null,
        consentedByName = "Nadia Hasan",
        consentedByRelationship = "Mother",
        consentScope = "SCREENING_AND_LOCAL_RECORD",
      ),
    ) as PatientSaveResult.Success

    val instrument = testInstrument()
    val draft = screeningRepo.createDraftScreening(
      patientId = reg.patientId,
      caregiverId = null,
      consentRecordId = reg.consentId,
      patientDobIso = "2024-03-01",
      outOfRangeAcknowledged = false,
      instrument = instrument,
      verifiedChecksum = "abc123",
      screeningDateIso = "2025-11-01",
    )

    // Answer 5 items -> verify resume detection returns this screening (items 1..4 at-risk; item 5 safe NO since item 5 is reverse-scored)
    for (n in 1..5) {
      val item = instrument.items[n - 1]
      val safeResp = if (item.atRiskResponse == InitialResponseValue.YES) InitialResponseValue.NO else InitialResponseValue.YES
      val resp = if (n in setOf(1, 2, 3, 4)) item.atRiskResponse else safeResp
      screeningRepo.saveInitialAnswer(draft.id, instrument, n, resp)
    }
    val unfinished = screeningRepo.getLatestUnfinishedScreening()
    assertNotNull(unfinished)
    assertEquals(draft.id, unfinished?.id)

    // Complete remaining 15 items as NOT at-risk -> total 4 at-risk (MODERATE)
    for (n in 6..20) {
      val item = instrument.items[n - 1]
      val safeResp = if (item.atRiskResponse == InitialResponseValue.YES) InitialResponseValue.NO else InitialResponseValue.YES
      screeningRepo.saveInitialAnswer(draft.id, instrument, n, safeResp)
    }
    val afterStage1 = screeningRepo.completeInitialStage(draft.id, instrument, "2024-03-01")
    assertEquals(4, afterStage1.initialScore)
    assertEquals(InitialCategory.MODERATE, afterStage1.initialCategory)
    assertEquals(ScreeningStatus.FOLLOWUP_REQUIRED, afterStage1.status)
    assertEquals(FinalOutcome.FOLLOWUP_PENDING, afterStage1.finalOutcome)

    // Complete Follow-Up: 2 FAIL + 2 PASS -> POSITIVE_AFTER_FOLLOWUP
    screeningRepo.saveFollowUpItem(draft.id, 1, "mchat_r_01", "[]", FollowUpResultValue.FAIL, null)
    screeningRepo.saveFollowUpItem(draft.id, 2, "mchat_r_02", "[]", FollowUpResultValue.FAIL, null)
    screeningRepo.saveFollowUpItem(draft.id, 3, "mchat_r_03", "[]", FollowUpResultValue.PASS, null)
    screeningRepo.saveFollowUpItem(draft.id, 4, "mchat_r_04", "[]", FollowUpResultValue.PASS, null)

    val completed = screeningRepo.completeFollowUpStage(draft.id, instrument)
    assertTrue(completed.isLocked)
    assertEquals(2, completed.followupScore)
    assertEquals(FinalOutcome.POSITIVE_AFTER_FOLLOWUP, completed.finalOutcome)

    // Verify immutability: direct edit throws
    var threw = false
    try {
      screeningRepo.saveInitialAnswer(draft.id, instrument, 1, InitialResponseValue.YES)
    } catch (_: IllegalArgumentException) {
      threw = true
    }
    assertTrue("Locked screening must reject direct mutation", threw)

    // Verify correction creates revision 2 while preserving revision 1 responses
    val rev2 = screeningRepo.createCorrectionRevision(draft.id, "Parent clarified item 1")
    assertEquals(2, rev2.currentRevisionNumber)
    assertFalse(rev2.isLocked)
    assertEquals(20, screeningRepo.getQuestionnaireResponses(draft.id, 1).size)
    assertEquals(20, screeningRepo.getQuestionnaireResponses(draft.id, 2).size)

    // Verify snapshot export + wipe + restore round-trip
    val bytes = backupRepo.exportSnapshotJson()
    val parsed = backupRepo.parseSnapshot(bytes)
    backupRepo.wipeAllClinicalRecords()
    assertEquals(0, db.patientDao().getAllPatientsSnapshot().size)

    val diff = backupRepo.inspectRestoreDiff(parsed)
    assertEquals(1, diff.newPatients)
    assertEquals(1, diff.newScreenings)

    backupRepo.applyRestore(parsed, RestoreConflictStrategy.MERGE_PREFER_NEWER)
    assertEquals(1, db.patientDao().getAllPatientsSnapshot().size)
    assertEquals(1, db.screeningDao().getAllScreeningsSnapshot().size)

    // Audit hash chain remains unbroken across all events
    val verified = auditRepo.verifyIntegrityChain()
    assertTrue(verified.isValid)
    assertTrue(verified.totalEntriesChecked > 0)
  }
}
