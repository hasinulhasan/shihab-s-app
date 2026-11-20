package com.mchat.core.security.applock

import com.mchat.core.common.AutoLockTimeout
import com.mchat.core.common.ClockProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSecurityTest {

  private class FakeClock(var currentMillis: Long = 1_000_000L) : ClockProvider {
    override fun nowEpochMillis(): Long = currentMillis
  }

  @Test
  fun pinHasher_verifiesCorrectPin_andRejectsWrongPin() {
    val hasher = PinHasher()
    val digest = hasher.hashPin("482915", iterations = 10_000)

    assertTrue(hasher.verifyPin("482915", digest))
    assertFalse(hasher.verifyPin("482916", digest))
    assertFalse(hasher.verifyPin("1234", digest))
    assertFalse(hasher.verifyPin("abcdef", digest))
  }

  @Test
  fun pinHasher_usesUniqueSaltPerHash() {
    val hasher = PinHasher()
    val first = hasher.hashPin("123456", iterations = 10_000)
    val second = hasher.hashPin("123456", iterations = 10_000)

    assertNotEquals(first.saltBase64, second.saltBase64)
    assertNotEquals(first.hashBase64, second.hashBase64)
    assertTrue(hasher.verifyPin("123456", first))
    assertTrue(hasher.verifyPin("123456", second))
  }

  @Test(expected = IllegalArgumentException::class)
  fun pinHasher_rejectsNonSixDigitPinOnHash() {
    PinHasher().hashPin("12345")
  }

  @Test
  fun appLockManager_locksImmediatelyWhenTimeoutIsImmediate() {
    val clock = FakeClock()
    val manager = AppLockManager(clock)

    assertEquals(AppLockStatus.UNLOCKED, manager.lockStatus.value)
    manager.onAppBackgrounded(isPinConfigured = true, timeout = AutoLockTimeout.IMMEDIATE)
    assertEquals(AppLockStatus.LOCKED, manager.lockStatus.value)

    manager.unlock()
    assertEquals(AppLockStatus.UNLOCKED, manager.lockStatus.value)
  }

  @Test
  fun appLockManager_respectsElapsedTimeoutOnForeground() {
    val clock = FakeClock(currentMillis = 10_000L)
    val manager = AppLockManager(clock)

    manager.onAppBackgrounded(isPinConfigured = true, timeout = AutoLockTimeout.ONE_MINUTE)
    assertEquals(AppLockStatus.UNLOCKED, manager.lockStatus.value)

    // 30 seconds elapsed -> stays unlocked
    clock.currentMillis += 30_000L
    manager.onAppForegrounded(isPinConfigured = true, timeout = AutoLockTimeout.ONE_MINUTE)
    assertEquals(AppLockStatus.UNLOCKED, manager.lockStatus.value)

    // 61 seconds elapsed -> locks on foreground
    manager.onAppBackgrounded(isPinConfigured = true, timeout = AutoLockTimeout.ONE_MINUTE)
    clock.currentMillis += 61_000L
    manager.onAppForegrounded(isPinConfigured = true, timeout = AutoLockTimeout.ONE_MINUTE)
    assertEquals(AppLockStatus.LOCKED, manager.lockStatus.value)
  }

  @Test
  fun appLockManager_neverLocksWhenPinNotConfigured() {
    val clock = FakeClock()
    val manager = AppLockManager(clock)

    manager.requireInitialLockIfConfigured(isPinConfigured = false)
    manager.onAppBackgrounded(isPinConfigured = false, timeout = AutoLockTimeout.IMMEDIATE)
    manager.lockNow(isPinConfigured = false)

    assertEquals(AppLockStatus.UNLOCKED, manager.lockStatus.value)
  }
}
