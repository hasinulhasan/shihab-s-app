package com.mchat.core.common.age

import java.time.LocalDate
import java.time.Period

enum class AgeRangeStatus {
  UNDER_RANGE,
  IN_RANGE,
  OVER_RANGE,
}

data class ChildAge(
  val years: Int,
  val remainingMonths: Int,
  val remainingDays: Int,
  val totalMonths: Int,
  val rangeStatus: AgeRangeStatus,
) {
  val isWithinIntendedRange: Boolean
    get() = rangeStatus == AgeRangeStatus.IN_RANGE

  val requiresOutOfRangeAcknowledgement: Boolean
    get() = !isWithinIntendedRange
}

/**
 * Deterministic calendar age calculator using ISO-8601 LocalDate.
 * Handles month-end clamping and leap days (Feb 29) accurately via java.time.Period.
 */
object AgeCalculator {
  const val INTENDED_MIN_MONTHS = 16
  const val INTENDED_MAX_MONTHS = 30

  fun calculate(
    dateOfBirth: LocalDate,
    asOfDate: LocalDate = LocalDate.now(),
  ): ChildAge {
    require(!dateOfBirth.isAfter(asOfDate)) {
      "Date of birth ($dateOfBirth) cannot be after reference date ($asOfDate)"
    }
    val period = Period.between(dateOfBirth, asOfDate)
    val totalMonths = period.years * 12 + period.months
    val rangeStatus = when {
      totalMonths < INTENDED_MIN_MONTHS -> AgeRangeStatus.UNDER_RANGE
      totalMonths > INTENDED_MAX_MONTHS -> AgeRangeStatus.OVER_RANGE
      else -> AgeRangeStatus.IN_RANGE
    }
    return ChildAge(
      years = period.years,
      remainingMonths = period.months,
      remainingDays = period.days,
      totalMonths = totalMonths,
      rangeStatus = rangeStatus,
    )
  }

  fun calculateFromIso(
    dobIso: String,
    asOfIso: String = LocalDate.now().toString(),
  ): ChildAge? = runCatching {
    calculate(LocalDate.parse(dobIso), LocalDate.parse(asOfIso))
  }.getOrNull()
}
