package com.mchat.core.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mchat.core.common.AppThemeMode
import com.mchat.core.design.theme.MChatTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ambient liquid gradient background with 3 slow, low-opacity botanical orbs (sage, soft lime, muted teal).
 * - Pauses automatically when the app moves to the background (ON_STOP).
 * - Respects system animator duration scale (static frame when scale == 0).
 * - Renders directly on Canvas DrawScope for smooth 60 FPS on mid-range hardware.
 */
@Composable
fun AnimatedAmbientBackground(
  modifier: Modifier = Modifier,
) {
  val extendedColors = MChatTheme.extendedColors
  val glassConfig = MChatTheme.glassConfig
  val lifecycleOwner = LocalLifecycleOwner.current

  var isForeground by remember { mutableStateOf(true) }
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> isForeground = true
        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isForeground = false
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val shouldAnimate = glassConfig.animationsEnabled && isForeground

  val phase = if (shouldAnimate) {
    val transition = rememberInfiniteTransition(label = "ambient_gradient_lights")
    val animated by transition.animateFloat(
      initialValue = 0f,
      targetValue = (2.0 * PI).toFloat(),
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 26_000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "ambient_orb_phase",
    )
    animated
  } else {
    0.4f
  }

  val backgroundColor = MaterialTheme.colorScheme.background
  val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(backgroundColor, surfaceVariant.copy(alpha = 0.55f), backgroundColor),
        ),
      ),
  ) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@Canvas

    val baseRadius = minOf(w, h) * 0.68f

    val sageCenter = Offset(
      x = w * (0.18f + 0.08f * cos(phase)),
      y = h * (0.14f + 0.05f * sin(phase)),
    )
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(extendedColors.ambientSage, Color.Transparent),
        center = sageCenter,
        radius = baseRadius,
      ),
      radius = baseRadius,
      center = sageCenter,
    )

    val limeCenter = Offset(
      x = w * (0.86f + 0.07f * sin(phase * 0.8f)),
      y = h * (0.34f + 0.06f * cos(phase * 0.8f)),
    )
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(extendedColors.ambientLime, Color.Transparent),
        center = limeCenter,
        radius = baseRadius * 0.88f,
      ),
      radius = baseRadius * 0.88f,
      center = limeCenter,
    )

    val tealCenter = Offset(
      x = w * (0.32f + 0.10f * sin(phase + 1.4f)),
      y = h * (0.86f + 0.05f * cos(phase + 1.4f)),
    )
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(extendedColors.ambientTeal, Color.Transparent),
        center = tealCenter,
        radius = baseRadius * 0.96f,
      ),
      radius = baseRadius * 0.96f,
      center = tealCenter,
    )
  }
}

@Preview(name = "Ambient Background Light", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AnimatedAmbientBackgroundLightPreview() {
  MChatTheme(themeMode = AppThemeMode.LIGHT) {
    Box(modifier = Modifier.fillMaxSize()) {
      AnimatedAmbientBackground()
    }
  }
}

@Preview(name = "Ambient Background Dark", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AnimatedAmbientBackgroundDarkPreview() {
  MChatTheme(themeMode = AppThemeMode.DARK) {
    Box(modifier = Modifier.fillMaxSize()) {
      AnimatedAmbientBackground()
    }
  }
}
