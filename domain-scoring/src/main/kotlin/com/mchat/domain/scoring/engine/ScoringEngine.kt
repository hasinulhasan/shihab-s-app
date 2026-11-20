package com.mchat.domain.scoring.engine

import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.FollowUpFlowDefinition
import com.mchat.domain.scoring.model.FollowUpNode
import com.mchat.domain.scoring.model.FollowUpResultValue
import com.mchat.domain.scoring.model.InitialCategory
import com.mchat.domain.scoring.model.InitialResponseValue
import com.mchat.domain.scoring.model.InstrumentDefinition
import com.mchat.domain.scoring.model.InstrumentItem
import com.mchat.domain.scoring.model.RecommendationContent
import com.mchat.domain.scoring.model.ScreeningStatus

data class ScoredItemResult(
  val itemNumber: Int,
  val itemId: String,
  val response: InitialResponseValue,
  val isAtRisk: Boolean,
  val pointValue: Int,
)

data class InitialScoringEvaluation(
  val initialScore: Int,
  val initialCategory: InitialCategory,
  val nextStatus: ScreeningStatus,
  val provisionalOutcome: FinalOutcome,
  val atRiskItemNumbers: List<Int>,
  val scoredItems: List<ScoredItemResult>,
  val rescreenRecommended: Boolean,
  val rescreenTargetAgeMonths: Int?,
  val recommendation: RecommendationContent?,
)

data class FollowUpStepOutcome(
  val nextNode: FollowUpNode?,
  val terminalResult: FollowUpResultValue?,
)

data class FinalScreeningEvaluation(
  val initialScore: Int,
  val initialCategory: InitialCategory,
  val followUpScore: Int?,
  val finalStatus: ScreeningStatus,
  val finalOutcome: FinalOutcome,
  val rescreenRecommended: Boolean,
  val rescreenTargetAgeMonths: Int?,
  val recommendation: RecommendationContent,
)

/**
 * Deterministic pure-Kotlin M-CHAT-R/F scoring engine.
 * All item risk keys, category cutoffs, follow-up thresholds, and rescreen rules
 * are read strictly from the verified [InstrumentDefinition] data — never hardcoded.
 */
object ScoringEngine {

  fun scoreItem(item: InstrumentItem, response: InitialResponseValue): ScoredItemResult {
    val isAtRisk = response == item.atRiskResponse
    return ScoredItemResult(
      itemNumber = item.officialNumber,
      itemId = item.itemId,
      response = response,
      isAtRisk = isAtRisk,
      pointValue = if (isAtRisk) item.pointValue else 0,
    )
  }

  fun evaluateInitial(
    instrument: InstrumentDefinition,
    responsesByItemNumber: Map<Int, InitialResponseValue>,
    childAgeMonths: Int,
  ): InitialScoringEvaluation {
    require(responsesByItemNumber.size == instrument.scoringConfig.totalItems) {
      "All ${instrument.scoringConfig.totalItems} items must be answered before initial scoring"
    }

    val scoredItems = instrument.items
      .sortedBy { it.officialNumber }
      .map { item ->
        val response = responsesByItemNumber[item.officialNumber]
          ?: error("Missing response for item ${item.officialNumber}")
        scoreItem(item, response)
      }

    val initialScore = scoredItems.sumOf { it.pointValue }
    val categoryRule = instrument.scoringConfig.initialCategories.firstOrNull { rule ->
      initialScore in rule.minScore..rule.maxScore
    } ?: error("Score $initialScore is outside configured category bounds")

    val atRiskItems = scoredItems.filter { it.isAtRisk }.map { it.itemNumber }
    val rescreenRule = instrument.scoringConfig.lowRiskRescreenRule
    val rescreenRecommended = categoryRule.category == InitialCategory.LOW &&
      childAgeMonths < rescreenRule.triggerIfAgeMonthsUnder
    val rescreenTargetAge = if (rescreenRecommended) rescreenRule.rescreenAtAgeMonths else null

    val (nextStatus, provisionalOutcome, recommendationKey) = when (categoryRule.category) {
      InitialCategory.LOW -> Triple(
        ScreeningStatus.FOLLOWUP_NOT_REQUIRED,
        FinalOutcome.NEGATIVE_LOW_INITIAL,
        if (rescreenRecommended) "NEGATIVE_LOW_INITIAL_UNDER_24M" else "NEGATIVE_LOW_INITIAL_24M_OR_OLDER",
      )
      InitialCategory.MODERATE -> Triple(
        ScreeningStatus.FOLLOWUP_REQUIRED,
        FinalOutcome.FOLLOWUP_PENDING,
        "FOLLOWUP_REQUIRED_MODERATE",
      )
      InitialCategory.HIGH -> Triple(
        ScreeningStatus.HIGH_DIRECT_REFERRAL,
        FinalOutcome.POSITIVE_HIGH_INITIAL,
        "POSITIVE_HIGH_INITIAL",
      )
    }

    return InitialScoringEvaluation(
      initialScore = initialScore,
      initialCategory = categoryRule.category,
      nextStatus = nextStatus,
      provisionalOutcome = provisionalOutcome,
      atRiskItemNumbers = atRiskItems,
      scoredItems = scoredItems,
      rescreenRecommended = rescreenRecommended,
      rescreenTargetAgeMonths = rescreenTargetAge,
      recommendation = instrument.recommendations[recommendationKey],
    )
  }

  /**
   * Evaluates a transition from [currentNode] within a data-driven [FollowUpFlowDefinition].
   */
  fun evaluateFollowUpTransition(
    flow: FollowUpFlowDefinition,
    currentNode: FollowUpNode,
    condition: String,
  ): FollowUpStepOutcome {
    val transition = currentNode.transitions.firstOrNull {
      it.condition.equals(condition, ignoreCase = true)
    } ?: error("No transition defined for condition '$condition' on node '${currentNode.nodeId}'")

    if (transition.terminalResult != null) {
      return FollowUpStepOutcome(nextNode = null, terminalResult = transition.terminalResult)
    }
    val nextId = transition.nextNodeId
      ?: error("Transition '$condition' on '${currentNode.nodeId}' must specify nextNodeId or terminalResult")
    val nextNode = flow.nodes.firstOrNull { it.nodeId == nextId }
      ?: error("Follow-up node '$nextId' not found")
    return FollowUpStepOutcome(nextNode = nextNode, terminalResult = null)
  }

  /**
   * Computes final outcome after Stage 1 (Low / High direct referral) or Stage 2 Follow-Up (Moderate).
   */
  fun evaluateFinal(
    instrument: InstrumentDefinition,
    responsesByItemNumber: Map<Int, InitialResponseValue>,
    followUpResultsByItemNumber: Map<Int, FollowUpResultValue>,
    childAgeMonths: Int,
  ): FinalScreeningEvaluation {
    val initial = evaluateInitial(instrument, responsesByItemNumber, childAgeMonths)

    return when (initial.initialCategory) {
      InitialCategory.LOW -> {
        val key = if (initial.rescreenRecommended) {
          "NEGATIVE_LOW_INITIAL_UNDER_24M"
        } else {
          "NEGATIVE_LOW_INITIAL_24M_OR_OLDER"
        }
        FinalScreeningEvaluation(
          initialScore = initial.initialScore,
          initialCategory = InitialCategory.LOW,
          followUpScore = null,
          finalStatus = ScreeningStatus.COMPLETED,
          finalOutcome = FinalOutcome.NEGATIVE_LOW_INITIAL,
          rescreenRecommended = initial.rescreenRecommended,
          rescreenTargetAgeMonths = initial.rescreenTargetAgeMonths,
          recommendation = requireRecommendation(instrument, key),
        )
      }

      InitialCategory.HIGH -> {
        FinalScreeningEvaluation(
          initialScore = initial.initialScore,
          initialCategory = InitialCategory.HIGH,
          followUpScore = null,
          finalStatus = ScreeningStatus.COMPLETED,
          finalOutcome = FinalOutcome.POSITIVE_HIGH_INITIAL,
          rescreenRecommended = false,
          rescreenTargetAgeMonths = null,
          recommendation = requireRecommendation(instrument, "POSITIVE_HIGH_INITIAL"),
        )
      }

      InitialCategory.MODERATE -> {
        val requiredItems = initial.atRiskItemNumbers
        val allRequiredAnswered = requiredItems.all { followUpResultsByItemNumber.containsKey(it) }
        if (!allRequiredAnswered) {
          return FinalScreeningEvaluation(
            initialScore = initial.initialScore,
            initialCategory = InitialCategory.MODERATE,
            followUpScore = null,
            finalStatus = ScreeningStatus.FOLLOWUP_IN_PROGRESS,
            finalOutcome = FinalOutcome.FOLLOWUP_PENDING,
            rescreenRecommended = false,
            rescreenTargetAgeMonths = null,
            recommendation = requireRecommendation(instrument, "FOLLOWUP_REQUIRED_MODERATE"),
          )
        }

        // Only items flagged at-risk in Stage 1 contribute to the Stage 2 Follow-Up fail count.
        val followUpFailCount = requiredItems.count { itemNum ->
          followUpResultsByItemNumber[itemNum] == FollowUpResultValue.FAIL
        }
        val isPositive = followUpFailCount >= instrument.scoringConfig.followUpPositiveThreshold
        val finalOutcome = if (isPositive) {
          FinalOutcome.POSITIVE_AFTER_FOLLOWUP
        } else {
          FinalOutcome.NEGATIVE_AFTER_FOLLOWUP
        }
        val recommendationKey = if (isPositive) {
          "POSITIVE_AFTER_FOLLOWUP"
        } else {
          "NEGATIVE_AFTER_FOLLOWUP"
        }

        FinalScreeningEvaluation(
          initialScore = initial.initialScore,
          initialCategory = InitialCategory.MODERATE,
          followUpScore = followUpFailCount,
          finalStatus = ScreeningStatus.COMPLETED,
          finalOutcome = finalOutcome,
          rescreenRecommended = false,
          rescreenTargetAgeMonths = null,
          recommendation = requireRecommendation(instrument, recommendationKey),
        )
      }
    }
  }

  private fun requireRecommendation(
    instrument: InstrumentDefinition,
    key: String,
  ): RecommendationContent = instrument.recommendations[key]
    ?: error("Missing recommendation entry '$key' in instrument ${instrument.instrumentVersion}")
}
