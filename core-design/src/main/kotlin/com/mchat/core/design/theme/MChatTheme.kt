package com.mchat.core.design.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mchat.core.common.AppThemeMode
import dev.chrisbanes.haze.HazeState

@Immutable
data class GlassEffectConfig(
  val useRealBlur: Boolean,
  val reduceTransparency: Boolean,
  val animationsEnabled: Boolean,
)

val LocalMChatExtendedColors = staticCompositionLocalOf { LightMChatExtendedColors }
val LocalGlassEffectConfig = staticCompositionLocalOf {
  GlassEffectConfig(
    useRealBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    reduceTransparency = false,
    animationsEnabled = true,
  )
}
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

val MChatShapes = Shapes(
  extraSmall = RoundedCornerShape(10.dp),
  small = RoundedCornerShape(14.dp),
  medium = RoundedCornerShape(22.dp),
  large = RoundedCornerShape(28.dp),
  extraLarge = RoundedCornerShape(36.dp),
)

object MChatTheme {
  val extendedColors: MChatExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMChatExtendedColors.current

  val glassConfig: GlassEffectConfig
    @Composable
    @ReadOnlyComposable
    get() = LocalGlassEffectConfig.current
}

@Composable
fun MChatTheme(
  themeMode: AppThemeMode = AppThemeMode.LIGHT,
  reduceTransparency: Boolean = false,
  hazeState: HazeState? = null,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
  }

  val context = LocalContext.current
  val configuration = LocalConfiguration.current
  val isBangla = remember(configuration) {
    val locale = configuration.locales[0]
    locale?.language.equals("bn", ignoreCase = true)
  }

  val systemAnimationScale = remember(context) {
    runCatching {
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      )
    }.getOrDefault(1f)
  }

  val animationsEnabled = systemAnimationScale > 0f
  val useRealBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
    !reduceTransparency &&
    animationsEnabled

  val colorScheme = if (darkTheme) MChatDarkColorScheme else MChatLightColorScheme
  val extendedColors = if (darkTheme) DarkMChatExtendedColors else LightMChatExtendedColors
  val typography = remember(isBangla) { buildMChatTypography(isBangla) }
  val glassConfig = remember(useRealBlur, reduceTransparency, animationsEnabled) {
    GlassEffectConfig(
      useRealBlur = useRealBlur,
      reduceTransparency = reduceTransparency,
      animationsEnabled = animationsEnabled,
    )
  }

  CompositionLocalProvider(
    LocalMChatExtendedColors provides extendedColors,
    LocalGlassEffectConfig provides glassConfig,
    LocalHazeState provides hazeState,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = typography,
      shapes = MChatShapes,
      content = content,
    )
  }
}
