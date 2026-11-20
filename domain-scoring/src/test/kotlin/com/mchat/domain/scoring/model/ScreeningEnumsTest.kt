package com.mchat.domain.scoring.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningEnumsTest {

  @Test
  fun finalOutcome_positiveAndNegativeDefinitionsMatchSpec() {
    assertTrue(FinalOutcome.POSITIVE_AFTER_FOLLOWUP.isPositiveScreen)
    assertTrue(FinalOutcome.POSITIVE_HIGH_INITIAL.isPositiveScreen)
    assertFalse(FinalOutcome.POSITIVE_AFTER_FOLLOWUP.isNegativeScreen)
    assertFalse(FinalOutcome.POSITIVE_HIGH_INITIAL.isNegativeScreen)

    assertTrue(FinalOutcome.NEGATIVE_LOW_INITIAL.isNegativeScreen)
    assertTrue(FinalOutcome.NEGATIVE_AFTER_FOLLOWUP.isNegativeScreen)
    assertFalse(FinalOutcome.NEGATIVE_LOW_INITIAL.isPositiveScreen)
    assertFalse(FinalOutcome.NEGATIVE_AFTER_FOLLOWUP.isPositiveScreen)

    assertFalse(FinalOutcome.FOLLOWUP_PENDING.isPositiveScreen)
    assertFalse(FinalOutcome.FOLLOWUP_PENDING.isNegativeScreen)
    assertFalse(FinalOutcome.INCOMPLETE.isPositiveScreen)
    assertFalse(FinalOutcome.INCOMPLETE.isNegativeScreen)
  }
}
