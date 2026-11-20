package com.mchat.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mchat.core.design.R
import com.mchat.core.design.components.EmptyStateCard
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.OutcomeStatusChip
import com.mchat.core.design.components.PrimaryButton
import com.mchat.core.design.components.SecondaryGlassButton
import com.mchat.core.design.components.SectionHeader
import com.mchat.core.design.components.StatusChip
import com.mchat.core.design.components.StatusTone
import com.mchat.core.design.components.SyncStatusChip
import com.mchat.data.local.dao.ScreeningWithPatientSummary
import com.mchat.data.local.preferences.UiPreferencesRepository
import com.mchat.data.local.repository.PatientRepository
import com.mchat.data.local.repository.ScreeningRepository
import com.mchat.data.sync.DriveSyncCoordinator
import com.mchat.data.sync.SyncStatusProvider
import com.mchat.data.sync.SyncStatusSummary
import com.mchat.domain.scoring.model.FinalOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

object HomeFilterKeys {
  const val ALL = "ALL"
  const val NEGATIVE = "NEGATIVE"
  const val FOLLOWUP_PENDING = "FOLLOWUP_PENDING"
  const val POSITIVE = "POSITIVE"
  const val THIS_MONTH = "THIS_MONTH"
}

data class HomeUiState(
  val patientCount: Int = 0,
  val totalScreenings: Int = 0,
  val negativeCount: Int = 0,
  val followUpPendingCount: Int = 0,
  val positiveCount: Int = 0,
  val thisMonthCount: Int = 0,
  val recentScreenings: List<ScreeningWithPatientSummary> = emptyList(),
  val hidePatientNames: Boolean = false,
  val syncSummary: SyncStatusSummary = SyncStatusSummary(),
  val isBangla: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
  patientRepository: PatientRepository,
  screeningRepository: ScreeningRepository,
  private val preferencesRepository: UiPreferencesRepository,
  syncStatusProvider: SyncStatusProvider,
  private val driveSyncCoordinator: DriveSyncCoordinator,
) : ViewModel() {

  val uiState: StateFlow<HomeUiState> = combine(
    patientRepository.observeActivePatientCount(),
    screeningRepository.observeActiveScreeningsWithPatient(),
    preferencesRepository.preferences,
    syncStatusProvider.syncSummary,
  ) { patientCount, screenings, prefs, sync ->
    val currentMonthPrefix = LocalDate.now().toString().substring(0, 7)
    val negative = screenings.count {
      it.finalOutcome == FinalOutcome.NEGATIVE_LOW_INITIAL ||
        it.finalOutcome == FinalOutcome.NEGATIVE_AFTER_FOLLOWUP
    }
    val positive = screenings.count {
      it.finalOutcome == FinalOutcome.POSITIVE_AFTER_FOLLOWUP ||
        it.finalOutcome == FinalOutcome.POSITIVE_HIGH_INITIAL
    }
    val followUpPending = screenings.count { it.finalOutcome == FinalOutcome.FOLLOWUP_PENDING }
    val thisMonth = screenings.count { it.screeningDate.startsWith(currentMonthPrefix) }

    HomeUiState(
      patientCount = patientCount,
      totalScreenings = screenings.size,
      negativeCount = negative,
      followUpPendingCount = followUpPending,
      positiveCount = positive,
      thisMonthCount = thisMonth,
      recentScreenings = screenings.take(8),
      hidePatientNames = prefs.hidePatientNamesOnHome,
      syncSummary = sync,
      isBangla = prefs.language.languageTag == "bn",
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

  fun togglePrivacyMask() {
    viewModelScope.launch {
      preferencesRepository.setHidePatientNamesOnHome(!uiState.value.hidePatientNames)
    }
  }

  fun triggerSyncNow() {
    driveSyncCoordinator.scheduleUniqueSyncWork(replace = true)
  }
}

@Composable
fun HomeRoute(
  onStartNewSurvey: () -> Unit,
  onOpenPatients: () -> Unit = {},
  onOpenFilteredScreenings: (String) -> Unit = {},
  onOpenScreening: (ScreeningWithPatientSummary) -> Unit = {},
  viewModel: HomeViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val haptic = LocalHapticFeedback.current

  // Pull-down-and-hold (~1s) gesture on Home ONLY:
  // Only engages when list is strictly at scroll offset 0; debounced to launch at most ONE survey.
  var pullDistancePx by remember { mutableFloatStateOf(0f) }
  var isHoldingPull by remember { mutableStateOf(false) }
  var holdProgress by remember { mutableFloatStateOf(0f) }
  var holdTriggered by remember { mutableStateOf(false) }

  val animatedHoldProgress by animateFloatAsState(targetValue = holdProgress, label = "pull_hold_progress")

  LaunchedEffect(isHoldingPull) {
    if (isHoldingPull && !holdTriggered) {
      val steps = 20
      val stepMs = 50L // 20 * 50ms = 1000ms hold
      for (i in 1..steps) {
        delay(stepMs)
        if (!isHoldingPull) break
        holdProgress = i / steps.toFloat()
      }
      if (isHoldingPull && !holdTriggered && holdProgress >= 0.99f) {
        holdTriggered = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onStartNewSurvey()
        isHoldingPull = false
        pullDistancePx = 0f
        holdProgress = 0f
      }
    } else if (!isHoldingPull) {
      holdProgress = 0f
    }
  }

  val nestedScrollConnection = remember(listState) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y < 0f && pullDistancePx > 0f) {
          pullDistancePx = (pullDistancePx + available.y).coerceAtLeast(0f)
          if (pullDistancePx < 90f) isHoldingPull = false
        }
        return Offset.Zero
      }

      override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
      ): Offset {
        val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if (atTop && available.y > 0f && source == NestedScrollSource.UserInput) {
          pullDistancePx = (pullDistancePx + available.y).coerceAtMost(240f)
          if (pullDistancePx >= 110f && !holdTriggered) {
            isHoldingPull = true
          }
          return Offset(0f, available.y * 0.4f)
        }
        return Offset.Zero
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        pullDistancePx = 0f
        isHoldingPull = false
        holdTriggered = false
        return Velocity.Zero
      }
    }
  }

  val greeting = remember(state.isBangla) {
    val hour = LocalTime.now().hour
    if (state.isBangla) {
      when (hour) {
        in 5..11 -> "শুভ সকাল"
        in 12..16 -> "শুভ অপরাহ্ণ"
        else -> "শুভ সন্ধ্যা"
      }
    } else {
      when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .nestedScroll(nestedScrollConnection),
  ) {
    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 128.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Pull-down-and-hold feedback banner
      item {
        AnimatedVisibility(visible = pullDistancePx > 24f || isHoldingPull) {
          GlassCard(contentPadding = PaddingValues(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              CircularProgressIndicator(
                progress = { animatedHoldProgress.coerceIn(0.05f, 1f) },
                modifier = Modifier.size(30.dp),
                strokeWidth = 3.dp,
              )
              Column {
                Text(
                  text = if (isHoldingPull) "Keep holding (~1s) to start a new screening…" else "Pull further and hold to start new survey",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = "Debounced single-trigger shortcut",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      // Time-based Greeting + Non-diagnostic badge
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            StatusChip(
              label = stringResource(R.string.clinical_tool_badge),
              icon = Icons.AutoMirrored.Rounded.Assignment,
              tone = StatusTone.INFO_TEAL,
            )
            Text(
              text = greeting,
              style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onBackground,
              modifier = Modifier.testTag("home_greeting_text"),
            )
            Text(
              text = stringResource(R.string.non_diagnostic_notice),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          SyncStatusChip(status = state.syncSummary.status)
        }
      }

      // 6 Real Stat Cards (2 x 3 grid) — each opens its filtered record list
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            HomeStatCard(
              title = stringResource(R.string.home_card_patients),
              value = state.patientCount.toString(),
              icon = Icons.Rounded.People,
              tone = StatusTone.INFO_TEAL,
              onClick = onOpenPatients,
              modifier = Modifier
                .weight(1f)
                .testTag("stat_card_patients"),
            )
            HomeStatCard(
              title = stringResource(R.string.home_card_screenings),
              value = state.totalScreenings.toString(),
              icon = Icons.AutoMirrored.Rounded.Assignment,
              tone = StatusTone.NEUTRAL,
              onClick = { onOpenFilteredScreenings(HomeFilterKeys.ALL) },
              modifier = Modifier
                .weight(1f)
                .testTag("stat_card_total"),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            HomeStatCard(
              title = stringResource(R.string.status_negative_screen),
              value = state.negativeCount.toString(),
              icon = Icons.Rounded.CheckCircle,
              tone = StatusTone.LOW_GREEN,
              onClick = { onOpenFilteredScreenings(HomeFilterKeys.NEGATIVE) },
              modifier = Modifier
                .weight(1f)
                .testTag("stat_card_negative"),
            )
            HomeStatCard(
              title = stringResource(R.string.home_card_followup),
              value = state.followUpPendingCount.toString(),
              icon = Icons.Rounded.Schedule,
              tone = StatusTone.MODERATE_AMBER,
              onClick = { onOpenFilteredScreenings(HomeFilterKeys.FOLLOWUP_PENDING) },
              modifier = Modifier
                .weight(1f)
                .testTag("stat_card_followup"),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            HomeStatCard(
              title = stringResource(R.string.status_positive_screen),
              value = state.positiveCount.toString(),
              icon = Icons.Rounded.WarningAmber,
              tone = StatusTone.HIGH_CORAL,
              onClick = { onOpenFilteredScreenings(HomeFilterKeys.POSITIVE) },
              modifier = Modifier
                .weight(1f)
                .testTag("stat_card_positive"),
            )
            HomeStatCard(
              title = if (state.isBangla) "এই মাসে" else "This Month",
              value = state.thisMonthCount.toString(),
              icon = Icons.Rounded.CalendarMonth,
              tone = StatusTone.INFO_TEAL,
              onClick = { onOpenFilteredScreenings(HomeFilterKeys.THIS_MONTH) },
              modifier = Modifier
                .weight(1f)
                .testTag("stat_card_this_month"),
            )
          }
        }
      }

      // Quick Actions
      item {
        GlassCard(contentPadding = PaddingValues(16.dp)) {
          SectionHeader(
            title = if (state.isBangla) "দ্রুত কার্যক্রম" else "Quick Actions",
            subtitle = "Pull down & hold 1s at top of Home to start a survey anytime",
          )
          PrimaryButton(
            text = stringResource(R.string.home_action_start_screening),
            icon = Icons.Rounded.Add,
            onClick = onStartNewSurvey,
            modifier = Modifier.fillMaxWidth(),
            testTag = "home_start_screening_button",
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            SecondaryGlassButton(
              text = stringResource(R.string.home_card_patients),
              icon = Icons.Rounded.People,
              onClick = onOpenPatients,
              modifier = Modifier.weight(1f),
              testTag = "home_open_patients_button",
            )
            SecondaryGlassButton(
              text = if (state.isBangla) "এখনই সিঙ্ক" else "Sync Now",
              icon = Icons.Rounded.Sync,
              onClick = viewModel::triggerSyncNow,
              modifier = Modifier.weight(1f),
              testTag = "home_sync_now_button",
            )
          }
        }
      }

      // Recent Screenings header with Privacy Mask Toggle
      item {
        SectionHeader(
          title = if (state.isBangla) "সাম্প্রতিক স্ক্রিনিং" else "Recent Screenings",
          subtitle = if (state.hidePatientNames) "Privacy mask active — names hidden" else "Tap any screening to open",
          trailing = {
            IconButton(
              onClick = viewModel::togglePrivacyMask,
              modifier = Modifier.testTag("home_privacy_toggle_button"),
            ) {
              Icon(
                imageVector = if (state.hidePatientNames) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (state.hidePatientNames) "Show patient names" else "Hide patient names",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          },
        )
      }

      if (state.recentScreenings.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.AutoMirrored.Rounded.Assignment,
            title = stringResource(R.string.home_empty_title),
            body = "Register a patient with caregiver consent and start your first offline M-CHAT-R/F screening.",
            actionLabel = stringResource(R.string.home_action_start_screening),
            onAction = onStartNewSurvey,
            actionTestTag = "home_empty_start_button",
          )
        }
      } else {
        items(state.recentScreenings, key = { it.id }) { row ->
          GlassCard(
            onClick = { onOpenScreening(row) },
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.testTag("home_recent_${row.id}"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (state.hidePatientNames) "Patient ${row.patientCode}" else row.patientName,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                  text = "${row.patientCode} • ${row.screeningDate} • ${row.childAgeMonths}m (${row.languageUsed.uppercase()})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (row.initialScore != null) {
                Text(
                  text = "${row.initialScore}/20",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            OutcomeStatusChip(outcome = row.finalOutcome)
          }
        }
      }

      item { Spacer(modifier = Modifier.height(12.dp)) }
    }
  }
}

@Composable
private fun HomeStatCard(
  title: String,
  value: String,
  icon: ImageVector,
  tone: StatusTone,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  GlassCard(
    onClick = onClick,
    contentPadding = PaddingValues(14.dp),
    elevation = 5.dp,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
      )
      StatusChip(label = "", icon = icon, tone = tone)
    }
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
    )
  }
}
