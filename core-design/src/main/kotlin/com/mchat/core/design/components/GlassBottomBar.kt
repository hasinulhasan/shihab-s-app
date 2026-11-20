package com.mchat.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mchat.core.common.AppThemeMode
import com.mchat.core.design.R
import com.mchat.core.design.theme.LocalHazeState
import com.mchat.core.design.theme.MChatTheme
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

enum class PrimaryTab {
  HOME,
  STATISTICS,
  NEW_SURVEY,
  SETTINGS,
}

/**
 * Floating Liquid Glass capsule bottom bar:
 * Home | Statistics | central prominent "+" New Survey | Settings.
 * - Explicitly applies navigationBarsPadding() so gesture pill / 3-button nav never clips it.
 * - Supports hiding cleanly during active questionnaires via [visible].
 * - Provides light haptic feedback and >= 48dp touch targets.
 */
@Composable
fun GlassBottomBar(
  selectedTab: PrimaryTab,
  onSelectTab: (PrimaryTab) -> Unit,
  modifier: Modifier = Modifier,
  visible: Boolean = true,
) {
  val extended = MChatTheme.extendedColors
  val glassConfig = MChatTheme.glassConfig
  val hazeState = LocalHazeState.current
  val haptic = LocalHapticFeedback.current

  val capsuleShape = RoundedCornerShape(34.dp)
  val borderBrush = Brush.linearGradient(
    colors = listOf(extended.glassBorderStart, extended.glassBorderEnd),
  )

  val surfaceModifier = if (glassConfig.useRealBlur && hazeState != null) {
    Modifier.hazeEffect(
      state = hazeState,
      style = HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.surface,
        tints = listOf(HazeTint(extended.glassTint)),
        blurRadius = 26.dp,
        noiseFactor = 0.02f,
      ),
    )
  } else {
    Modifier.background(
      color = extended.glassSolidFallback,
      shape = capsuleShape,
    )
  }

  AnimatedVisibility(
    visible = visible,
    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    modifier = modifier.fillMaxWidth(),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      contentAlignment = Alignment.Center,
    ) {
      Row(
        modifier = Modifier
          .widthIn(max = 520.dp)
          .fillMaxWidth()
          .shadow(
            elevation = 16.dp,
            shape = capsuleShape,
            ambientColor = Color.Black.copy(alpha = 0.14f),
            spotColor = Color.Black.copy(alpha = 0.18f),
          )
          .clip(capsuleShape)
          .then(surfaceModifier)
          .border(BorderStroke(1.dp, borderBrush), capsuleShape)
          .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        GlassTabItem(
          label = stringResource(R.string.nav_home),
          selectedIcon = Icons.Rounded.Home,
          unselectedIcon = Icons.Outlined.Home,
          isSelected = selectedTab == PrimaryTab.HOME,
          testTag = "bottom_tab_home",
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelectTab(PrimaryTab.HOME)
          },
          modifier = Modifier.weight(1f),
        )

        GlassTabItem(
          label = stringResource(R.string.nav_statistics),
          selectedIcon = Icons.Rounded.BarChart,
          unselectedIcon = Icons.Outlined.BarChart,
          isSelected = selectedTab == PrimaryTab.STATISTICS,
          testTag = "bottom_tab_statistics",
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelectTab(PrimaryTab.STATISTICS)
          },
          modifier = Modifier.weight(1f),
        )

        // Central prominent "+" New Survey action pill
        val newSurveyDesc = stringResource(R.string.cd_nav_new_survey)
        val isNewSelected = selectedTab == PrimaryTab.NEW_SURVEY
        Box(
          modifier = Modifier
            .padding(horizontal = 6.dp)
            .semantics {
              contentDescription = newSurveyDesc
              selected = isNewSelected
            }
            .testTag("bottom_tab_new_survey")
            .size(56.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(
              brush = Brush.linearGradient(
                colors = listOf(
                  MaterialTheme.colorScheme.primary,
                  MaterialTheme.colorScheme.secondary,
                ),
              ),
            )
            .border(
              width = if (isNewSelected) 2.5.dp else 1.dp,
              color = if (isNewSelected) {
                MaterialTheme.colorScheme.tertiaryContainer
              } else {
                Color.White.copy(alpha = 0.45f)
              },
              shape = CircleShape,
            )
            .clickable(role = Role.Tab) {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              onSelectTab(PrimaryTab.NEW_SURVEY)
            },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(30.dp),
          )
        }

        GlassTabItem(
          label = stringResource(R.string.nav_settings),
          selectedIcon = Icons.Rounded.Settings,
          unselectedIcon = Icons.Outlined.Settings,
          isSelected = selectedTab == PrimaryTab.SETTINGS,
          testTag = "bottom_tab_settings",
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelectTab(PrimaryTab.SETTINGS)
          },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun GlassTabItem(
  label: String,
  selectedIcon: ImageVector,
  unselectedIcon: ImageVector,
  isSelected: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val contentColor = if (isSelected) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

  Column(
    modifier = modifier
      .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
      .clip(RoundedCornerShape(22.dp))
      .background(
        color = if (isSelected) {
          MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
          Color.Transparent
        },
      )
      .clickable(role = Role.Tab, onClick = onClick)
      .semantics { selected = isSelected }
      .testTag(testTag)
      .padding(horizontal = 6.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Icon(
      imageVector = if (isSelected) selectedIcon else unselectedIcon,
      contentDescription = null,
      tint = contentColor,
      modifier = Modifier.size(22.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Preview(name = "GlassBottomBar Light", showBackground = true, widthDp = 390)
@Composable
private fun GlassBottomBarLightPreview() {
  MChatTheme(themeMode = AppThemeMode.LIGHT) {
    Surface(color = MaterialTheme.colorScheme.background) {
      GlassBottomBar(
        selectedTab = PrimaryTab.HOME,
        onSelectTab = {},
      )
    }
  }
}

@Preview(name = "GlassBottomBar Dark", showBackground = true, widthDp = 390)
@Composable
private fun GlassBottomBarDarkPreview() {
  MChatTheme(themeMode = AppThemeMode.DARK) {
    Surface(color = MaterialTheme.colorScheme.background) {
      GlassBottomBar(
        selectedTab = PrimaryTab.STATISTICS,
        onSelectTab = {},
      )
    }
  }
}
