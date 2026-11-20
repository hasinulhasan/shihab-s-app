package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.mchat.core.common.AppLanguage
import com.mchat.core.common.instrument.AssetInstrumentLoader
import com.mchat.core.common.instrument.InstrumentLoadState
import com.mchat.domain.scoring.model.InitialResponseValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InstrumentAssetIntegrityTest {

  @Test
  fun bundledInstruments_verifySha256_and_matchOfficialScoringKey() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val loader = AssetInstrumentLoader(context)

    val allStates = loader.verifyAllBundledInstruments()
    assertEquals(2, allStates.size)

    AppLanguage.entries.forEach { lang ->
      val state = allStates[lang]
      assertTrue(
        "Instrument for $lang must pass SHA-256 verification, got $state",
        state is InstrumentLoadState.Verified,
      )
      val def = (state as InstrumentLoadState.Verified).instrument.definition
      assertEquals(20, def.items.size)

      // Official scoring key verification: ONLY items 2, 5, 12 have YES as at-risk response
      val yesAtRiskItems = def.items
        .filter { it.atRiskResponse == InitialResponseValue.YES }
        .map { it.officialNumber }
        .toSet()
      assertEquals(setOf(2, 5, 12), yesAtRiskItems)

      // Verify all required recommendation codes exist
      val expectedRecKeys = setOf(
        "NEGATIVE_LOW_INITIAL_UNDER_24M",
        "NEGATIVE_LOW_INITIAL_24M_OR_OLDER",
        "FOLLOWUP_REQUIRED_MODERATE",
        "NEGATIVE_AFTER_FOLLOWUP",
        "POSITIVE_AFTER_FOLLOWUP",
        "POSITIVE_HIGH_INITIAL",
      )
      assertTrue(
        "Missing recommendation keys for $lang",
        def.recommendations.keys.containsAll(expectedRecKeys),
      )
    }
  }

  @Test
  fun manifest_disablesAndroidAutoBackup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val allowBackupFlag = (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
    assertFalse("Android auto-backup MUST be disabled for encrypted clinical DB", allowBackupFlag)
  }
}
