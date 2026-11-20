package com.mchat.core.design.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Liquid Glass clinical palette:
 * Sage green, soft lime, muted teal on warm white, light grey, and soft beige.
 * All foreground/background pairs target WCAG AA contrast in both Light and Dark themes.
 */
object MChatPalette {
  // Brand & Natural Botanicals
  val SageDeep = Color(0xFF2B594B)
  val SagePrimary = Color(0xFF366B5B)
  val SageSoft = Color(0xFF5D8F7F)
  val SageLightContainer = Color(0xFFD6EAE2)
  val SageDarkPrimary = Color(0xFF8CC7B3)
  val SageDarkContainer = Color(0xFF20453A)

  val SoftLime = Color(0xFFA9CE84)
  val SoftLimeDeep = Color(0xFF3E6123)
  val SoftLimeContainer = Color(0xFFE5F2D6)
  val SoftLimeDarkContainer = Color(0xFF2C3F1E)

  val MutedTeal = Color(0xFF2A666C)
  val MutedTealSoft = Color(0xFF5B969C)
  val MutedTealContainer = Color(0xFFD6EBED)
  val MutedTealDark = Color(0xFF84C6CC)
  val MutedTealDarkContainer = Color(0xFF1B3F43)

  // Warm Neutrals (Light)
  val WarmWhite = Color(0xFFF8F6F1)
  val SoftBeige = Color(0xFFEFEAE0)
  val LightGrey = Color(0xFFE7E6E0)
  val SurfaceCardLight = Color(0xFFFFFFFF)
  val InkDark = Color(0xFF16211E)
  val InkMutedLight = Color(0xFF475752)
  val OutlineLight = Color(0xFFC4CEC9)

  // Deep Pine Neutrals (Dark)
  val PineBackground = Color(0xFF101917)
  val PineSurface = Color(0xFF172421)
  val PineSurfaceElevated = Color(0xFF20302C)
  val InkLight = Color(0xFFF2F0EA)
  val InkMutedDark = Color(0xFFB4C3BD)
  val OutlineDark = Color(0xFF364943)

  // Semantic Clinical Status (Light)
  val StatusGreenLight = Color(0xFF1C6B49)
  val StatusGreenBgLight = Color(0xFFD9F1E5)
  val StatusAmberLight = Color(0xFF855200)
  val StatusAmberBgLight = Color(0xFFFBEFD5)
  val StatusCoralLight = Color(0xFFA63427)
  val StatusCoralBgLight = Color(0xFFFCE2DE)
  val StatusInfoLight = Color(0xFF225C63)
  val StatusInfoBgLight = Color(0xFFD9ECEF)

  // Semantic Clinical Status (Dark)
  val StatusGreenDark = Color(0xFF88DFB6)
  val StatusGreenBgDark = Color(0xFF163A2B)
  val StatusAmberDark = Color(0xFFF4C56D)
  val StatusAmberBgDark = Color(0xFF3E2B0C)
  val StatusCoralDark = Color(0xFFF5968A)
  val StatusCoralBgDark = Color(0xFF481B16)
  val StatusInfoDark = Color(0xFF8BD0D6)
  val StatusInfoBgDark = Color(0xFF15363A)
}

@Immutable
data class MChatExtendedColors(
  val glassTint: Color,
  val glassSolidFallback: Color,
  val glassBorderStart: Color,
  val glassBorderEnd: Color,
  val ambientSage: Color,
  val ambientLime: Color,
  val ambientTeal: Color,
  val statusGreenContent: Color,
  val statusGreenContainer: Color,
  val statusAmberContent: Color,
  val statusAmberContainer: Color,
  val statusCoralContent: Color,
  val statusCoralContainer: Color,
  val statusInfoContent: Color,
  val statusInfoContainer: Color,
)

val LightMChatExtendedColors = MChatExtendedColors(
  glassTint = Color(0xB8FFFFFF),
  glassSolidFallback = Color(0xF0FBF9F5),
  glassBorderStart = Color(0xCCFFFFFF),
  glassBorderEnd = Color(0x45B5C7BF),
  ambientSage = Color(0x366EA490),
  ambientLime = Color(0x33B8DA8E),
  ambientTeal = Color(0x2E5D9CA3),
  statusGreenContent = MChatPalette.StatusGreenLight,
  statusGreenContainer = MChatPalette.StatusGreenBgLight,
  statusAmberContent = MChatPalette.StatusAmberLight,
  statusAmberContainer = MChatPalette.StatusAmberBgLight,
  statusCoralContent = MChatPalette.StatusCoralLight,
  statusCoralContainer = MChatPalette.StatusCoralBgLight,
  statusInfoContent = MChatPalette.StatusInfoLight,
  statusInfoContainer = MChatPalette.StatusInfoBgLight,
)

val DarkMChatExtendedColors = MChatExtendedColors(
  glassTint = Color(0xAD1A2925),
  glassSolidFallback = Color(0xF0192724),
  glassBorderStart = Color(0x4D94C7B5),
  glassBorderEnd = Color(0x1F56756C),
  ambientSage = Color(0x2E3F8A72),
  ambientLime = Color(0x2284B55C),
  ambientTeal = Color(0x2E2E7A82),
  statusGreenContent = MChatPalette.StatusGreenDark,
  statusGreenContainer = MChatPalette.StatusGreenBgDark,
  statusAmberContent = MChatPalette.StatusAmberDark,
  statusAmberContainer = MChatPalette.StatusAmberBgDark,
  statusCoralContent = MChatPalette.StatusCoralDark,
  statusCoralContainer = MChatPalette.StatusCoralBgDark,
  statusInfoContent = MChatPalette.StatusInfoDark,
  statusInfoContainer = MChatPalette.StatusInfoBgDark,
)

val MChatLightColorScheme = lightColorScheme(
  primary = MChatPalette.SagePrimary,
  onPrimary = Color.White,
  primaryContainer = MChatPalette.SageLightContainer,
  onPrimaryContainer = MChatPalette.SageDeep,
  secondary = MChatPalette.MutedTeal,
  onSecondary = Color.White,
  secondaryContainer = MChatPalette.MutedTealContainer,
  onSecondaryContainer = Color(0xFF14383C),
  tertiary = MChatPalette.SoftLimeDeep,
  onTertiary = Color.White,
  tertiaryContainer = MChatPalette.SoftLimeContainer,
  onTertiaryContainer = Color(0xFF213810),
  background = MChatPalette.WarmWhite,
  onBackground = MChatPalette.InkDark,
  surface = MChatPalette.WarmWhite,
  onSurface = MChatPalette.InkDark,
  surfaceVariant = MChatPalette.SoftBeige,
  onSurfaceVariant = MChatPalette.InkMutedLight,
  outline = MChatPalette.OutlineLight,
  outlineVariant = MChatPalette.LightGrey,
  error = MChatPalette.StatusCoralLight,
  onError = Color.White,
  errorContainer = MChatPalette.StatusCoralBgLight,
  onErrorContainer = Color(0xFF6B1A12),
)

val MChatDarkColorScheme = darkColorScheme(
  primary = MChatPalette.SageDarkPrimary,
  onPrimary = Color(0xFF0C2B22),
  primaryContainer = MChatPalette.SageDarkContainer,
  onPrimaryContainer = Color(0xFFCBECE0),
  secondary = MChatPalette.MutedTealDark,
  onSecondary = Color(0xFF0A2C30),
  secondaryContainer = MChatPalette.MutedTealDarkContainer,
  onSecondaryContainer = Color(0xFFC6EAEE),
  tertiary = MChatPalette.SoftLime,
  onTertiary = Color(0xFF1A310B),
  tertiaryContainer = MChatPalette.SoftLimeDarkContainer,
  onTertiaryContainer = Color(0xFFD7EFC1),
  background = MChatPalette.PineBackground,
  onBackground = MChatPalette.InkLight,
  surface = MChatPalette.PineSurface,
  onSurface = MChatPalette.InkLight,
  surfaceVariant = MChatPalette.PineSurfaceElevated,
  onSurfaceVariant = MChatPalette.InkMutedDark,
  outline = MChatPalette.OutlineDark,
  outlineVariant = Color(0xFF263833),
  error = MChatPalette.StatusCoralDark,
  onError = Color(0xFF4F130D),
  errorContainer = MChatPalette.StatusCoralBgDark,
  onErrorContainer = Color(0xFFFCDAD5),
)
