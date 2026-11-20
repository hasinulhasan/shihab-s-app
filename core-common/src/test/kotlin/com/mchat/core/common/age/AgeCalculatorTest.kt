package com.mchat.core.common.age

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AgeCalculatorTest {

  @Test
  fun exactBoundaries_15_16_30_31Months() {
    val asOf = LocalDate.of(2026, 6, 15)

    val age15 = AgeCalculator.calculate(LocalDate.of(2025, 3, 15), asOf)
    assertEquals(15, age15.totalMonths)
    assertEquals(1, age15.years)
    assertEquals(3, age15.remainingMonths)
    assertEquals(AgeRangeStatus.UNDER_RANGE, age15.rangeStatus)
    assertTrue(age15.requiresOutOfRangeAcknowledgement)

    val age16 = AgeCalculator.calculate(LocalDate.of(2025, 2, 15), asOf)
    assertEquals(16, age16.totalMonths)
    assertEquals(AgeRangeStatus.IN_RANGE, age16.rangeStatus)
    assertFalse(age16.requiresOutOfRangeAcknowledgement)

    val age30 = AgeCalculator.calculate(LocalDate.of(2023, 12, 15), asOf)
    assertEquals(30, age30.totalMonths)
    assertEquals(2, age30.years)
    assertEquals(6, age30.remainingMonths)
    assertEquals(AgeRangeStatus.IN_RANGE, age30.rangeStatus)

    val age31 = AgeCalculator.calculate(LocalDate.of(2023, 11, 15), asOf)
    assertEquals(31, age31.totalMonths)
    assertEquals(AgeRangeStatus.OVER_RANGE, age31.rangeStatus)
    assertTrue(age31.requiresOutOfRangeAcknowledgement)
  }

  @Test
  fun monthEndBoundaries_jan31ToFeb28AndMar31() {
    // Born Jan 31, 2025 -> on Feb 28, 2025 (non-leap year): 0 months, 28 days
    val feb28 = AgeCalculator.calculate(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 28))
    assertEquals(0, feb28.totalMonths)
    assertEquals(28, feb28.remainingDays)

    // Born Jan 31, 2025 -> on Mar 31, 2025: exact 2 months
    val mar31 = AgeCalculator.calculate(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 3, 31))
    assertEquals(2, mar31.totalMonths)
    assertEquals(0, mar31.remainingDays)

    // Born Aug 31, 2024 -> on Feb 28, 2026 (17m 28d -> still 17 completed months)
    val endOfFeb = AgeCalculator.calculate(LocalDate.of(2024, 8, 31), LocalDate.of(2026, 2, 28))
    assertEquals(17, endOfFeb.totalMonths)
    assertEquals(AgeRangeStatus.IN_RANGE, endOfFeb.rangeStatus)
  }

  @Test
  fun leapDayBirth_feb29HandlingAcrossLeapAndNonLeapYears() {
    val dob = LocalDate.of(2024, 2, 29)

    // Feb 28, 2025: 11 months, 30 days (not yet 1 year)
    val dayBefore = AgeCalculator.calculate(dob, LocalDate.of(2025, 2, 28))
    assertEquals(0, dayBefore.years)
    assertEquals(11, dayBefore.remainingMonths)
    assertEquals(11, dayBefore.totalMonths)

    // Mar 1, 2025: 1 year, 0 months, 1 day
    val dayAfter = AgeCalculator.calculate(dob, LocalDate.of(2025, 3, 1))
    assertEquals(1, dayAfter.years)
    assertEquals(0, dayAfter.remainingMonths)
    assertEquals(12, dayAfter.totalMonths)

    // Jun 29, 2025: exact 16 months -> IN_RANGE
    val month16 = AgeCalculator.calculate(dob, LocalDate.of(2025, 6, 29))
    assertEquals(16, month16.totalMonths)
    assertEquals(AgeRangeStatus.IN_RANGE, month16.rangeStatus)

    // Feb 29, 2028 (next leap year): exact 4 years = 48 months
    val leap4y = AgeCalculator.calculate(dob, LocalDate.of(2028, 2, 29))
    assertEquals(4, leap4y.years)
    assertEquals(0, leap4y.remainingMonths)
    assertEquals(48, leap4y.totalMonths)
    assertEquals(AgeRangeStatus.OVER_RANGE, leap4y.rangeStatus)
  }

  @Test(expected = IllegalArgumentException::class)
  fun futureDateOfBirth_throws() {
    AgeCalculator.calculate(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 6, 1))
  }
}
