package com.mchat.feature.followup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mchat.core.common.AppLanguage
import com.mchat.core.common.instrument.AssetInstrumentLoader
import com.mchat.core.common.instrument.InstrumentLoadState
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.OutcomeStatusChip
import com.mchat.core.design.components.PrimaryButton
import com.mchat.core.design.components.SecondaryGlassButton
import com.mchat.core.design.components.SectionHeader
import com.mchat.core.design.components.StatusChip
import com.mchat.core.design.components.StatusTone
import com.mchat.core.design.components.toPresentation
import com.mchat.data.local.entity.AssessmentEntity
import com.mchat.data.local.entity.PatientEntity
import com.mchat.data.local.entity.ScreeningEntity
import com.mchat.data.local.entity.ScreeningRevisionEntity
import com.mchat.data.local.repository.PatientRepository
import com.mchat.data.local.repository.ScreeningRepository
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.InstrumentDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScreeningResultUiState(
  val screening: ScreeningEntity? = null,
  val patient: PatientEntity? = null,
  val assessment: AssessmentEntity? = null,
  val revisions: List<ScreeningRevisionEntity> = emptyList(),
  val instrument: InstrumentDefinition? = null,
  val reminderScheduledMessage: String? = null,
  val reopenedForRevisionId: String? = null,
)

@HiltViewModel
class ScreeningResultViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val screeningRepository: ScreeningRepository,
  private val patientRepository: PatientRepository,
  private val instrumentLoader: AssetInstrumentLoader,
  private val reminderScheduler: RescreenReminderScheduler,
) : ViewModel() {

  private val screeningId: String = checkNotNull(savedStateHandle["screeningId"])

  private val _uiState = MutableStateFlow(ScreeningResultUiState())
  val uiState: StateFlow<ScreeningResultUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      val screening = screeningRepository.getScreeningById(screeningId) ?: return@launch
      val patient = patientRepository.getPatientById(screening.patientId)
      val assessment = screeningRepository.getAssessment(screening.id, screening.currentRevisionNumber)
      val loaded = instrumentLoader.loadVerifiedInstrument(AppLanguage.fromTag(screening.languageUsed))
      val instrument = (loaded as? InstrumentLoadState.Verified)?.instrument?.definition
      _uiState.update {
        it.copy(
          screening = screening,
          patient = patient,
          assessment = assessment,
          instrument = instrument,
        )
      }
      screeningRepository.observeRevisions(screening.id).collect { revs ->
        _uiState.update { it.copy(revisions = revs) }
      }
    }
  }

  fun scheduleRescreenReminder() {
    val screening = _uiState.value.screening ?: return
    val dueDate = screening.rescreenDueDate ?: return
    reminderScheduler.scheduleRescreenReminder(screening.id, dueDate)
    _uiState.update {
      it.copy(reminderScheduledMessage = "Privacy-safe local reminder scheduled for $dueDate (contains NO patient name).")
    }
  }

  fun createCorrectionRevision(reason: String) {
    if (reason.isBlank()) return
    viewModelScope.launch {
      val reopened = screeningRepository.createCorrectionRevision(screeningId, reason)
      _uiState.update { it.copy(reopenedForRevisionId = reopened.id) }
    }
  }
}

@Composable
fun ScreeningResultRoute(
  onBack: () -> Unit,
  onOpenReport: (String) -> Unit,
  onOpenCorrectionQuestionnaire: (String) -> Unit,
  viewModel: ScreeningResultViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showCorrectionDialog by remember { mutableStateOf(false) }
  var correctionReason by remember { mutableStateOf("") }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      viewModel.scheduleRescreenReminder()
    }
  }

  LaunchedEffect(state.reopenedForRevisionId) {
    state.reopenedForRevisionId?.let(onOpenCorrectionQuestionnaire)
  }

  if (showCorrectionDialog) {
    AlertDialog(
      onDismissRequest = { showCorrectionDialog = false },
      icon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
      title = { Text("Create Correction Revision") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Completed screenings are immutable. Creating a correction starts a new revision " +
              "and logs an entry in the tamper-evident audit chain without overwriting history.",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = correctionReason,
            onValueChange = { correctionReason = it },
            label = { Text("Clinical reason for correction *") },
            minLines = 2,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("correction_reason_input"),
          )
        }
      },
      confirmButton = {
        TextButton(
          enabled = correctionReason.isNotBlank(),
          onClick = {
            showCorrectionDialog = false
            viewModel.createCorrectionRevision(correctionReason)
          },
          modifier = Modifier.testTag("confirm_correction_button"),
        ) {
          Text("Create Revision")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCorrectionDialog = false }) { Text("Cancel") }
      },
    )
  }

  val screening = state.screening
  val assessment = state.assessment
  val instrument = state.instrument

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 48.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("result_back_button")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column {
          Text("Screening Result", style = MaterialTheme.typography.headlineSmall)
          if (state.patient != null && screening != null) {
            Text(
              text = "${state.patient!!.fullName} (${state.patient!!.patientCode}) • ${screening.screeningDate}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    if (screening != null) {
      // Outcome Summary Card
      item {
        GlassCard {
          SectionHeader(
            title = "Final Screening Outcome",
            subtitle = "Revision ${screening.currentRevisionNumber} • ${screening.instrumentVersion}",
          )
          OutcomeStatusChip(
            outcome = screening.finalOutcome,
            modifier = Modifier.testTag("result_outcome_chip"),
          )
          if (screening.finalOutcome == FinalOutcome.POSITIVE_HIGH_INITIAL) {
            StatusChip(
              label = "High-Initial Direct Referral — Follow-Up Bypassed per Official Rules",
              icon = Icons.Rounded.WarningAmber,
              tone = StatusTone.HIGH_CORAL,
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            GlassCard(
              modifier = Modifier.weight(1f),
              contentPadding = PaddingValues(14.dp),
              elevation = 2.dp,
            ) {
              Text("Initial Score", style = MaterialTheme.typography.labelMedium)
              Text(
                text = "${screening.initialScore ?: "—"} / 20",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag("result_initial_score"),
              )
              screening.initialCategory?.let { cat ->
                val p = cat.toPresentation()
                Text(p.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            GlassCard(
              modifier = Modifier.weight(1f),
              contentPadding = PaddingValues(14.dp),
              elevation = 2.dp,
            ) {
              Text("Follow-Up Score", style = MaterialTheme.typography.labelMedium)
              Text(
                text = screening.followupScore?.toString() ?: "N/A",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag("result_followup_score"),
              )
              Text(
                text = if (screening.followupScore != null) "Threshold ≥ 2 positive" else "Not required / bypassed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      // Official Recommendation Card
      item {
        GlassCard {
          SectionHeader(
            title = "Official Clinical Next Steps",
            subtitle = "Verbatim guidance from verified instrument file",
          )
          Text(
            text = assessment?.recommendationTextSnapshot ?: "Follow official M-CHAT-R/F clinical guidance.",
            style = MaterialTheme.typography.bodyLarge,
          )
          if (assessment?.rescreenRecommended == true && !screening.rescreenDueDate.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            StatusChip(
              label = "Rescreen recommended at ${assessment.rescreenTargetAgeMonths ?: 24}m (Due ${screening.rescreenDueDate})",
              icon = Icons.Rounded.NotificationsActive,
              tone = StatusTone.INFO_TEAL,
            )
            SecondaryGlassButton(
              text = "Schedule Privacy-Safe Local Reminder",
              icon = Icons.Rounded.NotificationsActive,
              onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                  viewModel.scheduleRescreenReminder()
                }
              },
              modifier = Modifier.fillMaxWidth(),
              testTag = "schedule_rescreen_reminder_button",
            )
            state.reminderScheduledMessage?.let { msg ->
              Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }

      // Non-diagnostic disclaimer & Official Attribution
      item {
        GlassCard {
          StatusChip(
            label = "Non-Diagnostic Screening Disclaimer",
            icon = Icons.Rounded.Shield,
            tone = StatusTone.INFO_TEAL,
          )
          Text(
            text = assessment?.disclaimerTextSnapshot
              ?: instrument?.nonDiagnosticDisclaimer
              ?: "The M-CHAT-R/F is a screening tool, not a diagnostic instrument.",
            style = MaterialTheme.typography.bodyMedium,
          )
          if (instrument != null) {
            Text(
              text = listOfNotNull(
                instrument.copyrightNotice,
                instrument.translatorCredit,
                instrument.permissionNotice,
              ).joinToString("\n"),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Actions: PDF Report & Immutable Correction
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          PrimaryButton(
            text = "Generate / View PDF Report (EN / BN / Bilingual)",
            icon = Icons.Rounded.PictureAsPdf,
            onClick = { onOpenReport(screening.id) },
            modifier = Modifier.fillMaxWidth(),
            testTag = "open_pdf_report_button",
          )
          if (screening.isLocked) {
            SecondaryGlassButton(
              text = "Create Correction Revision (Immutable Lock)",
              icon = Icons.Rounded.Lock,
              onClick = { showCorrectionDialog = true },
              modifier = Modifier.fillMaxWidth(),
              testTag = "create_correction_revision_button",
            )
          }
        }
      }

      if (state.revisions.isNotEmpty()) {
        item {
          GlassCard {
            SectionHeader(title = "Revision History (${state.revisions.size})")
            state.revisions.forEach { rev ->
              Text(
                text = "Rev ${rev.revisionNumber}: ${rev.reasonForCorrection}",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      }
    }
  }
}
