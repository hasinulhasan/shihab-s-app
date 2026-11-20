package com.mchat.core.security.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Detects virtual emulator environments (including cloud streaming emulators like Cuttlefish/Ranchu)
 * where FLAG_SECURE blanks out the entire streamed display surface as a solid black screen.
 */
fun isEmulatorDevice(): Boolean {
  val fingerprint = Build.FINGERPRINT.lowercase()
  val model = Build.MODEL.lowercase()
  val product = Build.PRODUCT.lowercase()
  val hardware = Build.HARDWARE.lowercase()
  val manufacturer = Build.MANUFACTURER.lowercase()
  val brand = Build.BRAND.lowercase()
  val device = Build.DEVICE.lowercase()

  return fingerprint.startsWith("generic") ||
    fingerprint.contains("emulator") ||
    fingerprint.contains("test-keys") && (hardware.contains("cutf") || hardware.contains("ranchu") || hardware.contains("goldfish")) ||
    model.contains("emulator") ||
    model.contains("android sdk built for") ||
    model.contains("cuttlefish") ||
    product.contains("sdk_gphone") ||
    product.contains("emulator") ||
    product.contains("cf_") ||
    product.contains("vsoc") ||
    hardware.contains("goldfish") ||
    hardware.contains("ranchu") ||
    hardware.contains("cutf") ||
    manufacturer.contains("genymotion") ||
    (brand.startsWith("generic") && device.startsWith("generic"))
}

/**
 * Always enforces FLAG_SECURE on release builds and on all physical devices.
 * In debug builds running on an emulator (e.g., streaming preview), defers FLAG_SECURE
 * so the virtual display stream is visible instead of blacked out.
 */
fun shouldEnforceFlagSecure(isDebugBuild: Boolean): Boolean {
  if (!isDebugBuild) return true
  return !isEmulatorDevice()
}

/**
 * Applies WindowManager.LayoutParams.FLAG_SECURE to prevent screenshots and recent-apps previews
 * on sensitive clinical screens.
 */
@Composable
fun SecureScreenEffect(enabled: Boolean = true) {
  val context = LocalContext.current
  DisposableEffect(context, enabled) {
    val activity = context.findActivity()
    if (enabled) {
      activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
      activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    onDispose {
      // Keep FLAG_SECURE active when app-wide clinical protection is enabled.
    }
  }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
