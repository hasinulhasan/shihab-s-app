package com.mchat.domain.scoring

import com.mchat.domain.scoring.engine.ScoringEngine
import com.mchat.domain.scoring.integrity.ChecksumVerificationResult
import com.mchat.domain.scoring.integrity.ChecksumVerifier
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.FollowUpResultValue
import com.mchat.domain.scoring.model.InitialCategory
import com.mchat.domain.scoring.model.InitialResponseValue
import com.mchat.domain.scoring.model.InstrumentDefinition
import com.mchat.domain.scoring.model.ScreeningStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScoringEngineTest {
  private val json = Json { ignoreUnknownKeys = true }

  private fun loadAssetInstrument(fileName: String): InstrumentDefinition {
    val file = File("../app/src/main/assets/instrument/$fileName")
    require(file.exists()) { "Missing test instrument asset: ${file.absolutePath}" }
    return json.decodeFromString(file.readText(Charsets.UTF_8))
  }

  private fun allSafeResponses(instrument: InstrumentDefinition): MutableMap<Int, InitialResponseValue> =
    instrument.items.associate { item ->
      val safe = if (item.atRiskResponse == InitialResponseValue.YES) {
        InitialResponseValue.NO
      } else {
        InitialResponseValue.YES
      }
      item.officialNumber to safe
    }.toMutableMap()

  private fun responsesWithAtRiskCount(
    instrument: InstrumentDefinition,
    count: Int,
  ): MutableMap<Int, InitialResponseValue> {
    val map = allSafeResponses(instrument)
    instrument.items.sortedBy { it.officialNumber }.take(count).forEach { item ->
      map[item.officialNumber] = item.atRiskResponse
    }
    return map
  }

  @Test
  fun everyItem_1Through20_atRiskAndNotAtRisk_verifiedForEnglishAndBangla() {
    val reverseItems = setOf(2, 5, 12)
    listOf("mchat_rf_en_v1.json", "mchat_rf_bn_v1.json").forEach { asset ->
      val instrument = loadAssetInstrument(asset)
      assertTrue(ChecksumVerifier.validateDefinition(instrument).isEmpty())
      assertEquals(20, instrument.items.size)

      instrument.items.forEach { item ->
        val expectedAtRisk = if (item.officialNumber in reverseItems) {
          InitialResponseValue.YES
        } else {
          InitialResponseValue.NO
        }
        val expectedSafe = if (expectedAtRisk == InitialResponseValue.YES) {
          InitialResponseValue.NO
        } else {
          InitialResponseValue.YES
        }

        val atRiskResult = ScoringEngine.scoreItem(item, expectedAtRisk)
        assertTrue("Item ${item.officialNumber} ($asset) should be at-risk", atRiskResult.isAtRisk)
        assertEquals(1, atRiskResult.pointValue)

        val safeResult = ScoringEngine.scoreItem(item, expectedSafe)
        assertFalse("Item ${item.officialNumber} ($asset) should be safe", safeResult.isAtRisk)
        assertEquals(0, safeResult.pointValue)
      }
    }
  }

  @Test
  fun allNoAndAllYes_produceExactExpectedScoresAndCategories() {
    val en = loadAssetInstrument("mchat_rf_en_v1.json")

    // All NO: 17 standard items fail (all except 2, 5, 12) -> score 17 -> HIGH
    val allNo = (1..20).associateWith { InitialResponseValue.NO }
    val allNoEval = ScoringEngine.evaluateInitial(en, allNo, childAgeMonths = 20)
    assertEquals(17, allNoEval.initialScore)
    assertEquals(InitialCategory.HIGH, allNoEval.initialCategory)
    assertEquals(ScreeningStatus.HIGH_DIRECT_REFERRAL, allNoEval.nextStatus)

    // All YES: 3 reverse items fail (2, 5, 12) -> score 3 -> MODERATE
    val allYes = (1..20).associateWith { InitialResponseValue.YES }
    val allYesEval = ScoringEngine.evaluateInitial(en, allYes, childAgeMonths = 20)
    assertEquals(3, allYesEval.initialScore)
    assertEquals(InitialCategory.MODERATE, allYesEval.initialCategory)
    assertEquals(listOf(2, 5, 12), allYesEval.atRiskItemNumbers)
    assertEquals(ScreeningStatus.FOLLOWUP_REQUIRED, allYesEval.nextStatus)
  }

  @Test
  fun exactBoundaryScores_0_2_3_7_8_20_categorizedAccurately() {
    val en = loadAssetInstrument("mchat_rf_en_v1.json")

    // Score 0 (< 24 months -> rescreen recommended at 24m)
    val s0 = ScoringEngine.evaluateInitial(en, responsesWithAtRiskCount(en, 0), childAgeMonths = 18)
    assertEquals(0, s0.initialScore)
    assertEquals(InitialCategory.LOW, s0.initialCategory)
    assertEquals(FinalOutcome.NEGATIVE_LOW_INITIAL, s0.provisionalOutcome)
    assertTrue(s0.rescreenRecommended)
    assertEquals(24, s0.rescreenTargetAgeMonths)

    // Score 2 (>= 24 months -> no age-dependent rescreen)
    val s2 = ScoringEngine.evaluateInitial(en, responsesWithAtRiskCount(en, 2), childAgeMonths = 24)
    assertEquals(2, s2.initialScore)
    assertEquals(InitialCategory.LOW, s2.initialCategory)
    assertFalse(s2.rescreenRecommended)

    // Score 3 (lower MODERATE bound)
    val s3 = ScoringEngine.evaluateInitial(en, responsesWithAtRiskCount(en, 3), childAgeMonths = 20)
    assertEquals(3, s3.initialScore)
    assertEquals(InitialCategory.MODERATE, s3.initialCategory)
    assertEquals(ScreeningStatus.FOLLOWUP_REQUIRED, s3.nextStatus)

    // Score 7 (upper MODERATE bound)
    val s7 = ScoringEngine.evaluateInitial(en, responsesWithAtRiskCount(en, 7), childAgeMonths = 20)
    assertEquals(7, s7.initialScore)
    assertEquals(InitialCategory.MODERATE, s7.initialCategory)
    assertEquals(ScreeningStatus.FOLLOWUP_REQUIRED, s7.nextStatus)

    // Score 8 (lower HIGH bound)
    val s8 = ScoringEngine.evaluateInitial(en, responsesWithAtRiskCount(en, 8), childAgeMonths = 20)
    assertEquals(8, s8.initialScore)
    assertEquals(InitialCategory.HIGH, s8.initialCategory)
    assertEquals(ScreeningStatus.HIGH_DIRECT_REFERRAL, s8.nextStatus)
    assertEquals(FinalOutcome.POSITIVE_HIGH_INITIAL, s8.provisionalOutcome)

    // Score 20 (max HIGH bound)
    val s20 = ScoringEngine.evaluateInitial(en, responsesWithAtRiskCount(en, 20), childAgeMonths = 20)
    assertEquals(20, s20.initialScore)
    assertEquals(InitialCategory.HIGH, s20.initialCategory)
    assertEquals(FinalOutcome.POSITIVE_HIGH_INITIAL, s20.provisionalOutcome)
  }

  @Test
  fun followUpTotals_0_1_2_produceNegativeNegativePositive() {
    val en = loadAssetInstrument("mchat_rf_en_v1.json")
    // Items 1, 2, 3, 4 at-risk (score 4 -> MODERATE)
    val initialResponses = responsesWithAtRiskCount(en, 4)

    val fu0 = ScoringEngine.evaluateFinal(
      instrument = en,
      responsesByItemNumber = initialResponses,
      followUpResultsByItemNumber = mapOf(
        1 to FollowUpResultValue.PASS,
        2 to FollowUpResultValue.PASS,
        3 to FollowUpResultValue.PASS,
        4 to FollowUpResultValue.PASS,
      ),
      childAgeMonths = 22,
    )
    assertEquals(0, fu0.followUpScore)
    assertEquals(FinalOutcome.NEGATIVE_AFTER_FOLLOWUP, fu0.finalOutcome)
    assertEquals(ScreeningStatus.COMPLETED, fu0.finalStatus)

    val fu1 = ScoringEngine.evaluateFinal(
      instrument = en,
      responsesByItemNumber = initialResponses,
      followUpResultsByItemNumber = mapOf(
        1 to FollowUpResultValue.FAIL,
        2 to FollowUpResultValue.PASS,
        3 to FollowUpResultValue.PASS,
        4 to FollowUpResultValue.PASS,
      ),
      childAgeMonths = 22,
    )
    assertEquals(1, fu1.followUpScore)
    assertEquals(FinalOutcome.NEGATIVE_AFTER_FOLLOWUP, fu1.finalOutcome)

    val fu2 = ScoringEngine.evaluateFinal(
      instrument = en,
      responsesByItemNumber = initialResponses,
      followUpResultsByItemNumber = mapOf(
        1 to FollowUpResultValue.FAIL,
        2 to FollowUpResultValue.FAIL,
        3 to FollowUpResultValue.PASS,
        4 to FollowUpResultValue.PASS,
      ),
      childAgeMonths = 22,
    )
    assertEquals(2, fu2.followUpScore)
    assertEquals(FinalOutcome.POSITIVE_AFTER_FOLLOWUP, fu2.finalOutcome)
  }

  @Test
  fun answerChangeRecalculation_updatesScoreCategoryAndAtRiskItems() {
    val en = loadAssetInstrument("mchat_rf_en_v1.json")
    val responses = responsesWithAtRiskCount(en, 2) // Items 1, 2 at-risk -> score 2 (LOW)
    val before = ScoringEngine.evaluateInitial(en, responses, childAgeMonths = 25)
    assertEquals(2, before.initialScore)
    assertEquals(InitialCategory.LOW, before.initialCategory)

    // Flip item 5 (reverse item) to YES -> score becomes 3 (MODERATE)
    responses[5] = InitialResponseValue.YES
    val afterIncrease = ScoringEngine.evaluateInitial(en, responses, childAgeMonths = 25)
    assertEquals(3, afterIncrease.initialScore)
    assertEquals(InitialCategory.MODERATE, afterIncrease.initialCategory)
    assertEquals(listOf(1, 2, 5), afterIncrease.atRiskItemNumbers)

    // Flip item 2 (reverse item) back to NO -> score returns to 2 (LOW)
    responses[2] = InitialResponseValue.NO
    val afterDecrease = ScoringEngine.evaluateInitial(en, responses, childAgeMonths = 25)
    assertEquals(2, afterDecrease.initialScore)
    assertEquals(InitialCategory.LOW, afterDecrease.initialCategory)
  }

  @Test
  fun followUpDagTraversal_andChecksumTamperDetection() {
    val en = loadAssetInstrument("mchat_rf_en_v1.json")
    val item1Flow = en.items.first { it.officialNumber == 1 }.followUpFlow
    val startNode = item1Flow.nodes.first { it.nodeId == item1Flow.entryNodeId }

    val tieStep = ScoringEngine.evaluateFollowUpTransition(item1Flow, startNode, "BOTH")
    assertEquals("fu_01_tie", tieStep.nextNode?.nodeId)
    val terminal = ScoringEngine.evaluateFollowUpTransition(item1Flow, tieStep.nextNode!!, "MOST_OFTEN_FAIL")
    assertEquals(FollowUpResultValue.FAIL, terminal.terminalResult)

    val rawBytes = File("../app/src/main/assets/instrument/mchat_rf_en_v1.json").readBytes()
    val validHash = ChecksumVerifier.sha256Hex(rawBytes)
    assertTrue(ChecksumVerifier.verify(rawBytes, validHash) is ChecksumVerificationResult.Verified)
    assertTrue(
      ChecksumVerifier.verify(rawBytes + byteArrayOf(0x20), validHash) is ChecksumVerificationResult.Mismatch,
    )
  }
}
