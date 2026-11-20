package com.mchat.domain.scoring.model

import kotlinx.serialization.Serializable

@Serializable
enum class InitialResponseValue {
  YES,
  NO,
}

@Serializable
enum class FollowUpResultValue {
  PASS,
  FAIL,
}

@Serializable
enum class FollowUpNodeType {
  SINGLE_CHOICE,
  MULTI_EXAMPLE_CHECKLIST,
  FREQUENCY_TIEBREAKER,
  CLINICIAN_PASS_FAIL,
}

@Serializable
data class InstrumentManifest(
  val manifestVersion: Int,
  val instruments: List<InstrumentManifestEntry>,
)

@Serializable
data class InstrumentManifestEntry(
  val instrumentId: String,
  val instrumentVersion: String,
  val language: String,
  val assetPath: String,
  val sha256: String,
)

@Serializable
data class IntendedAgeRange(
  val min: Int,
  val max: Int,
)

@Serializable
data class InitialResponseLabels(
  val yesLabel: String,
  val noLabel: String,
)

@Serializable
data class FollowUpResponseLabels(
  val passLabel: String,
  val failLabel: String,
)

@Serializable
data class InstrumentResponseOptions(
  val initial: InitialResponseLabels,
  val followUp: FollowUpResponseLabels,
)

@Serializable
data class InitialCategoryRule(
  val category: InitialCategory,
  val minScore: Int,
  val maxScore: Int,
)

@Serializable
data class LowRiskRescreenRule(
  val triggerIfAgeMonthsUnder: Int,
  val rescreenAtAgeMonths: Int,
)

@Serializable
data class ScoringConfig(
  val totalItems: Int,
  val initialCategories: List<InitialCategoryRule>,
  val followUpPositiveThreshold: Int,
  val highRiskAllowsOptionalFollowUp: Boolean,
  val lowRiskRescreenRule: LowRiskRescreenRule,
)

@Serializable
data class FollowUpExampleOption(
  val id: String,
  val label: String,
)

@Serializable
data class FollowUpTransition(
  val condition: String,
  val nextNodeId: String? = null,
  val terminalResult: FollowUpResultValue? = null,
)

@Serializable
data class FollowUpNode(
  val nodeId: String,
  val nodeType: FollowUpNodeType,
  val promptText: String,
  val passExamples: List<FollowUpExampleOption> = emptyList(),
  val failExamples: List<FollowUpExampleOption> = emptyList(),
  val transitions: List<FollowUpTransition> = emptyList(),
)

@Serializable
data class FollowUpFlowDefinition(
  val entryNodeId: String,
  val nodes: List<FollowUpNode>,
)

@Serializable
data class InstrumentItem(
  val itemId: String,
  val officialNumber: Int,
  val text: String,
  val atRiskResponse: InitialResponseValue,
  val pointValue: Int = 1,
  val followUpFlow: FollowUpFlowDefinition,
)

@Serializable
data class RecommendationContent(
  val code: String,
  val title: String,
  val body: String,
)

@Serializable
data class InstrumentDefinition(
  val schemaVersion: Int,
  val instrumentId: String,
  val instrumentVersion: String,
  val scoringAlgorithmVersion: String,
  val language: String,
  val displayTitle: String,
  val copyrightNotice: String,
  val translatorCredit: String? = null,
  val permissionNotice: String,
  val nonDiagnosticDisclaimer: String,
  val intendedAgeMonths: IntendedAgeRange,
  val responseOptions: InstrumentResponseOptions,
  val scoringConfig: ScoringConfig,
  val items: List<InstrumentItem>,
  val recommendations: Map<String, RecommendationContent>,
)
