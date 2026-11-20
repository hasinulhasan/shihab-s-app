package com.mchat.core.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mchat.core.design.R

val LatinSansFontFamily = FontFamily(
  Font(R.font.plus_jakarta_sans, FontWeight.Normal),
  Font(R.font.plus_jakarta_sans, FontWeight.Medium),
  Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
  Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

val BengaliSansFontFamily = FontFamily(
  Font(R.font.noto_sans_bengali, FontWeight.Normal),
  Font(R.font.noto_sans_bengali, FontWeight.Medium),
  Font(R.font.noto_sans_bengali, FontWeight.SemiBold),
  Font(R.font.noto_sans_bengali, FontWeight.Bold),
)

/**
 * Generates Material 3 Typography tuned for up to 200% system font scaling without clipping,
 * with generous line heights for Bangla diacritics and large questionnaire prompts.
 */
fun buildMChatTypography(isBangla: Boolean): Typography {
  val primaryFamily = if (isBangla) BengaliSansFontFamily else LatinSansFontFamily
  val lineHeightBoost = if (isBangla) 1.12f else 1.0f

  fun scaleLine(baseSp: Float) = (baseSp * lineHeightBoost).sp

  return Typography(
    displaySmall = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 30.sp,
      lineHeight = scaleLine(38f),
      letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 24.sp,
      lineHeight = scaleLine(34f),
      letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 20.sp,
      lineHeight = scaleLine(28f),
    ),
    titleLarge = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 18.sp,
      lineHeight = scaleLine(26f),
    ),
    titleMedium = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 16.sp,
      lineHeight = scaleLine(24f),
      letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 14.sp,
      lineHeight = scaleLine(20f),
      letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 16.sp,
      lineHeight = scaleLine(25f),
      letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 14.sp,
      lineHeight = scaleLine(22f),
      letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 12.sp,
      lineHeight = scaleLine(18f),
      letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 14.sp,
      lineHeight = scaleLine(20f),
      letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 12.sp,
      lineHeight = scaleLine(17f),
      letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
      fontFamily = primaryFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 11.sp,
      lineHeight = scaleLine(16f),
      letterSpacing = 0.3.sp,
    ),
  )
}
