package com.mchat.domain.scoring.model

import kotlinx.serialization.Serializable

/**
 * Screening lifecycle state machine defined in Part A Section 2.
 */
@Serializable
enum class ScreeningStatus {
  DRAFT,
  INITIAL_IN_PROGRESS,
  INITIAL_COMPLETE,
  FOLLOWUP_NOT_REQUIRED,
  FOLLOWUP_REQUIRED,
  HIGH_DIRECT_REFERRAL,
  FOLLOWUP_IN_PROGRESS,
  COMPLETED,
  ABANDONED,
  ARCHIVED,
}

/**
 * Stage 1 M-CHAT-R initial risk category stored independently from [FinalOutcome].
 */
@Serializable
enum class InitialCategory {
  LOW,
  MODERATE,
  HIGH,
}

/**
 * Final screening outcome stored independently from [InitialCategory].
 * Aggregate statistics define Positive = POSITIVE_AFTER_FOLLOWUP + POSITIVE_HIGH_INITIAL
 * and Negative = NEGATIVE_LOW_INITIAL + NEGATIVE_AFTER_FOLLOWUP.
 */
@Serializable
enum class FinalOutcome {
  NEGATIVE_LOW_INITIAL,
  NEGATIVE_AFTER_FOLLOWUP,
  POSITIVE_AFTER_FOLLOWUP,
  POSITIVE_HIGH_INITIAL,
  FOLLOWUP_PENDING,
  INCOMPLETE;

  val isPositiveScreen: Boolean
    get() = this == POSITIVE_AFTER_FOLLOWUP || this == POSITIVE_HIGH_INITIAL

  val isNegativeScreen: Boolean
    get() = this == NEGATIVE_LOW_INITIAL || this == NEGATIVE_AFTER_FOLLOWUP
}

/**
 * Offline-first sync state for local records and encrypted Drive uploads.
 */
@Serializable
enum class SyncStatus {
  LOCAL_ONLY,
  PENDING_SYNC,
  IN_PROGRESS,
  SYNCED,
  FAILED,
  CONFLICT,
}
