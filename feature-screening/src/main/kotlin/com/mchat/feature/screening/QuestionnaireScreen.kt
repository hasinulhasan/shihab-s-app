package com.mchat.feature.screening

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.mchat.core.design.components.ClinicalAnswerButton
import com.mchat.core.design.components.ClinicianReAuthDialog
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.PrimaryButton
import com.mchat.core.design.components.SecondaryGlassButton
import com.mchat.core.design.components.SectionHeader
import com.mchat.core.design.components.StatusChip
import com.mchat.core.design.components.StatusTone
import com.mchat.data.local.entity.PatientEntity
import com.mchat.data.local.entity.ScreeningEntity
import com.mchat.data.local.preferences.AppShellPreferences
import com.mchat.data.local.preferences.UiPreferencesRepository
import com.mchat.data.local.repository.PatientRepository
import com.mchat.data.local.repository.ScreeningRepository
import com.mchat.domain.scoring.model.InitialResponseValue
import com.mchat.domain.scoring.model.InstrumentDefinition
import com.mchat.domain.scoring.model.ScreeningStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UndoTapState(
  val itemNumber: Int,
  val previousAnswer: InitialResponseValue?,
  val previousIndex: Int,
)

sealed interface QuestionnaireCompletionEvent {
  data class GoToFollowUp(val screeningId: String) : QuestionnaireCompletionEvent
  data class GoToResult(val screeningId: String) : QuestionnaireCompletionEvent
  data object Abandoned : QuestionnaireCompletionEvent
}

data class QuestionnaireUiState(
  val screening: ScreeningEntity? = null,
  val patient: PatientEntity? = null,
  val instrument: InstrumentDefinition? = null,
  val integrityError: String? = null,
  val responses: Map<Int, InitialResponseValue> = emptyMap(),
  val currentIndex: Int = 0, // 0..19
  val isReviewMode: Boolean = false,
  val isCaregiverHandOverMode: Boolean = false,
  val undoState: UndoTapState? = null,
  val preferences: AppShellPreferences = AppShellPreferences(),
  val isSubmitting: Boolean = false,
  val completionEvent: QuestionnaireCompletionEvent? = null,
) {
  val totalItems: Int get() = instrument?.items?.size ?: 20
  val answeredCount: Int get() = responses.size
  val completionPercent: Int get() = ((answeredCount * 100f) / totalItems.coerceAtLeast(1)).toInt()
  val allAnswered: Boolean get() = answeredCount == totalItems
}

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val screeningRepository: ScreeningRepository,
  private val patientRepository: PatientRepository,
  private val instrumentLoader: AssetInstrumentLoader,
  private val preferencesRepository: UiPreferencesRepository,
) : ViewModel() {

  private val screeningId: String = checkNotNull(savedStateHandle["screeningId"])

  private val _uiState = MutableStateFlow(QuestionnaireUiState())
  val uiState: StateFlow<QuestionnaireUiState> = _uiState.asStateFlow()

  private var autoAdvanceJob: Job? = null

  init {
    viewModelScope.launch {
      preferencesRepository.preferences.collect { prefs ->
        _uiState.update { it.copy(preferences = prefs) }
      }
    }

    viewModelScope.launch {
      val screening = screeningRepository.getScreeningById(screeningId)
      if (screening == null) {
        _uiState.update { it.copy(integrityError = "Screening record not found.") }
        return@launch
      }
      val patient = patientRepository.getPatientById(screening.patientId)
      val language = AppLanguage.fromTag(screening.languageUsed)
      when (val load = instrumentLoader.loadVerifiedInstrument(language)) {
        is InstrumentLoadState.IntegrityFailure -> {
          _uiState.update { it.copy(integrityError = load.reason) }
        }
        is InstrumentLoadState.Verified -> {
          val savedRows = screeningRepository.getQuestionnaireResponses(
            screening.id,
            screening.currentRevisionNumber,
          )
          val map = savedRows.associate { it.itemNumber to InitialResponseValue.valueOf(it.responseValue) }
          val firstUnansweredIdx = (1..20).indexOfFirst { !map.containsKey(it) }.let { idx ->
            if (idx == -1) 0 else idx
          }
          _uiState.update {
            it.copy(
              screening = screening,
              patient = patient,
              instrument = load.instrument.definition,
              responses = map,
              currentIndex = firstUnansweredIdx,
              isReviewMode = map.size == 20,
            )
          }
        }
      }
    }
  }

  fun selectAnswer(response: InitialResponseValue) {
    val state = _uiState.value
    val instrument = state.instrument ?: return
    val item = instrument.items.getOrNull(state.currentIndex) ?: return
    val prevAnswer = state.responses[item.officialNumber]
    val prevIndex = state.currentIndex

    autoAdvanceJob?.cancel()

    _uiState.update {
      it.copy(
        responses = it.responses + (item.officialNumber to response),
        undoState = UndoTapState(
          itemNumber = item.officialNumber,
          previousAnswer = prevAnswer,
          previousIndex = prevIndex,
        ),
      )
    }

    // Instant autosave to encrypted Room
    viewModelScope.launch {
      screeningRepository.saveInitialAnswer(
        screeningId = screeningId,
        instrument = instrument,
        itemNumber = item.officialNumber,
        response = response,
      )
    }

    // Short auto-advance (~450ms) with Undo still visible
    autoAdvanceJob = viewModelScope.launch {
      delay(450L)
      _uiState.update { current ->
        if (current.currentIndex == prevIndex) {
          if (current.currentIndex < current.totalItems - 1) {
            current.copy(currentIndex = current.currentIndex + 1)
          } else {
            current.copy(isReviewMode = true)
          }
        } else {
          current
        }
      }
    }
  }

  fun undoLastTap() {
    autoAdvanceJob?.cancel()
    val undo = _uiState.value.undoState ?: return
    val instrument = _uiState.value.instrument ?: return

    _uiState.update { state ->
      val updatedMap = if (undo.previousAnswer != null) {
        state.responses + (undo.itemNumber to undo.previousAnswer)
      } else {
        state.responses - undo.itemNumber
      }
      state.copy(
        responses = updatedMap,
        currentIndex = undo.previousIndex,
        isReviewMode = false,
        undoState = null,
      )
    }

    if (undo.previousAnswer != null) {
      viewModelScope.launch {
        screeningRepository.saveInitialAnswer(
          screeningId = screeningId,
          instrument = instrument,
          itemNumber = undo.itemNumber,
          response = undo.previousAnswer,
        )
      }
    }
  }

  fun goPrevious() {
    autoAdvanceJob?.cancel()
    _uiState.update { state ->
      if (state.isReviewMode) {
        state.copy(isReviewMode = false, currentIndex = (state.totalItems - 1).coerceAtLeast(0))
      } else if (state.currentIndex > 0) {
        state.copy(currentIndex = state.currentIndex - 1)
      } else {
        state
      }
    }
  }

  fun openReview() {
    autoAdvanceJob?.cancel()
    _uiState.update { it.copy(isReviewMode = true) }
  }

  fun jumpToQuestion(index: Int) {
    autoAdvanceJob?.cancel()
    _uiState.update {
      it.copy(
        currentIndex = index.coerceIn(0, (it.totalItems - 1).coerceAtLeast(0)),
        isReviewMode = false,
      )
    }
  }

  fun enterCaregiverHandOverMode() {
    _uiState.update { it.copy(isCaregiverHandOverMode = true) }
  }

  fun exitCaregiverHandOverMode() {
    _uiState.update { it.copy(isCaregiverHandOverMode = false) }
  }

  fun abandonScreening() {
    viewModelScope.launch {
      screeningRepository.abandonScreening(screeningId)
      _uiState.update { it.copy(completionEvent = QuestionnaireCompletionEvent.Abandoned) }
    }
  }

  fun submitStage1() {
    val state = _uiState.value
    val instrument = state.instrument ?: return
    val patient = state.patient ?: return
    if (!state.allAnswered || state.isSubmitting) return

    viewModelScope.launch {
      _uiState.update { it.copy(isSubmitting = true) }
      val updated = screeningRepository.completeInitialStage(
        screeningId = screeningId,
        instrument = instrument,
        patientDobIso = patient.dateOfBirth,
      )
      val event = if (updated.status == ScreeningStatus.FOLLOWUP_REQUIRED) {
        QuestionnaireCompletionEvent.GoToFollowUp(updated.id)
      } else {
        QuestionnaireCompletionEvent.GoToResult(updated.id)
      }
      _uiState.update { it.copy(isSubmitting = false, completionEvent = event) }
    }
  }
}

@Composable
fun QuestionnaireRoute(
  onBack: () -> Unit,
  onGoToFollowUp: (String) -> Unit,
  onGoToResult: (String) -> Unit,
  viewModel: QuestionnaireViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showAbandonDialog by remember { mutableStateOf(false) }
  var showExitHandOverPinDialog by remember { mutableStateOf(false) }

  LaunchedEffect(state.completionEvent) {
    when (val evt = state.completionEvent) {
      is QuestionnaireCompletionEvent.GoToFollowUp -> onGoToFollowUp(evt.screeningId)
      is QuestionnaireCompletionEvent.GoToResult -> onGoToResult(evt.screeningId)
      QuestionnaireCompletionEvent.Abandoned -> onBack()
      null -> Unit
    }
  }

  // Intercept hardware/gesture Back: locked in Caregiver Hand-Over Mode; prompts Abandon otherwise
  BackHandler {
    if (state.isCaregiverHandOverMode) {
      showExitHandOverPinDialog = true
    } else if (state.isReviewMode) {
      viewModel.goPrevious()
    } else if (state.currentIndex > 0) {
      viewModel.goPrevious()
    } else {
      showAbandonDialog = true
    }
  }

  if (showAbandonDialog) {
    AlertDialog(
      onDismissRequest = { showAbandonDialog = false },
      icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
      title = { Text("Leave Questionnaire?") },
      text = {
        Text(
          "Your ${state.answeredCount} answered items are already autosaved. " +
            "You can exit and resume later, or mark this screening as abandoned.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showAbandonDialog = false
            onBack()
          },
          modifier = Modifier.testTag("questionnaire_save_exit_button"),
        ) {
          Text("Save & Exit (Resume Later)")
        }
      },
      dismissButton = {
        Row {
          TextButton(
            onClick = {
              showAbandonDialog = false
              viewModel.abandonScreening()
            },
            modifier = Modifier.testTag("questionnaire_abandon_confirm_button"),
          ) {
            Text("Mark Abandoned", color = MaterialTheme.colorScheme.error)
          }
          TextButton(onClick = { showAbandonDialog = false }) {
            Text("Stay")
          }
        }
      },
    )
  }

  if (showExitHandOverPinDialog) {
    ClinicianReAuthDialog(
      title = "Exit Caregiver Hand-Over Mode",
      message = "Enter clinician PIN or verify biometrics to unlock full clinician controls.",
      confirmLabel = "Unlock",
      pinDigest = state.preferences.pinDigest,
      biometricEnabled = state.preferences.biometricEnabled,
      onDismiss = { showExitHandOverPinDialog = false },
      onAuthenticated = {
        showExitHandOverPinDialog = false
        viewModel.exitCaregiverHandOverMode()
      },
    )
  }

  if (state.integrityError != null) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      GlassCard {
        StatusChip("Integrity Verification Failed", Icons.Rounded.WarningAmber, StatusTone.HIGH_CORAL)
        Text(state.integrityError!!, style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(text = "Return", onClick = onBack, modifier = Modifier.fillMaxWidth())
      }
    }
    return
  }

  val instrument = state.instrument ?: return
  val isBn = instrument.language.equals("bn", ignoreCase = true)
  val animatedProgress by animateFloatAsState(
    targetValue = (state.answeredCount.toFloat() / state.totalItems.coerceAtLeast(1)),
    label = "questionnaire_progress",
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Top bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (!state.isCaregiverHandOverMode) {
        IconButton(
          onClick = { showAbandonDialog = true },
          modifier = Modifier.testTag("questionnaire_close_button"),
        ) {
          Icon(Icons.Rounded.Close, contentDescription = "Close or abandon questionnaire")
        }
      } else {
        StatusChip(
          label = "Caregiver Mode",
          icon = Icons.Rounded.Shield,
          tone = StatusTone.INFO_TEAL,
        )
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = if (state.isReviewMode) {
            if (isBn) "সব ২০টি উত্তর পর্যালোচনা" else "Review All 20 Answers"
          } else {
            val num = "%02d".format(state.currentIndex + 1)
            if (isBn) "প্রশ্ন $num / ২০" else "Question $num of ${state.totalItems}"
          },
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          modifier = Modifier.testTag("questionnaire_progress_label"),
        )
        Text(
          text = "${state.completionPercent}% complete • Autosaved",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (state.isCaregiverHandOverMode) {
        IconButton(
          onClick = { showExitHandOverPinDialog = true },
          modifier = Modifier.testTag("exit_caregiver_mode_button"),
        ) {
          Icon(Icons.Rounded.Lock, contentDescription = "Clinician PIN to exit caregiver mode")
        }
      } else {
        TextButton(
          onClick = viewModel::enterCaregiverHandOverMode,
          modifier = Modifier.testTag("enter_caregiver_mode_button"),
        ) {
          Text("Hand-Over")
        }
      }
    }

    LinearProgressIndicator(
      progress = { animatedProgress },
      modifier = Modifier
        .fillMaxWidth()
        .height(8.dp),
    )

    // Undo banner for accidental taps
    state.undoState?.let { undo ->
      GlassCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Saved Q${"%02d".format(undo.itemNumber)}",
            style = MaterialTheme.typography.labelLarge,
          )
          TextButton(
            onClick = viewModel::undoLastTap,
            modifier = Modifier.testTag("questionnaire_undo_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
            Text(" Undo")
          }
        }
      }
    }

    if (state.isReviewMode) {
      // Review all 20 answers + Edit & Recalculate
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
      ) {
        item {
          SectionHeader(
            title = if (isBn) "সব উত্তর যাচাই করুন" else "Review & Edit Answers",
            subtitle = "Tap any item to edit before deterministic scoring.",
          )
        }
        items(instrument.items, key = { it.officialNumber }) { item ->
          val answer = state.responses[item.officialNumber]
          GlassCard(
            onClick = { viewModel.jumpToQuestion(item.officialNumber - 1) },
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.testTag("review_item_${item.officialNumber}"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${"%02d".format(item.officialNumber)}. ${item.text}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
              )
              val label = when (answer) {
                InitialResponseValue.YES -> instrument.responseOptions.initial.yesLabel
                InitialResponseValue.NO -> instrument.responseOptions.initial.noLabel
                null -> "—"
              }
              StatusChip(
                label = label,
                icon = if (answer != null) Icons.Rounded.Edit else Icons.Rounded.WarningAmber,
                tone = if (answer != null) StatusTone.INFO_TEAL else StatusTone.MODERATE_AMBER,
              )
            }
          }
        }
      }

      if (state.isCaregiverHandOverMode) {
        GlassCard {
          Text(
            text = "Thank you for completing the 20 questions. Please hand the device back to the clinician.",
            style = MaterialTheme.typography.bodyMedium,
          )
          PrimaryButton(
            text = "Clinician Unlock & Score",
            icon = Icons.Rounded.Lock,
            onClick = { showExitHandOverPinDialog = true },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      } else {
        PrimaryButton(
          text = if (state.allAnswered) "Calculate Initial Score" else "Answer All 20 Items (${state.answeredCount}/20)",
          icon = Icons.Rounded.FactCheck,
          enabled = state.allAnswered && !state.isSubmitting,
          onClick = viewModel::submitStage1,
          modifier = Modifier.fillMaxWidth(),
          testTag = "submit_stage1_button",
        )
      }
    } else {
      // One question per screen
      val item = instrument.items[state.currentIndex]
      val currentAnswer = state.responses[item.officialNumber]

      Box(modifier = Modifier.weight(1f)) {
        AnimatedContent(
          targetState = item,
          label = "question_card_transition",
        ) { currentItem ->
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
          ) {
            GlassCard(contentPadding = PaddingValues(22.dp)) {
              Text(
                text = if (isBn) "প্রশ্ন ${"%02d".format(currentItem.officialNumber)}" else "ITEM ${"%02d".format(currentItem.officialNumber)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = currentItem.text,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("question_text_${currentItem.officialNumber}"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              ClinicalAnswerButton(
                label = instrument.responseOptions.initial.yesLabel,
                icon = Icons.Rounded.CheckCircle,
                selected = currentAnswer == InitialResponseValue.YES,
                onClick = { viewModel.selectAnswer(InitialResponseValue.YES) },
                modifier = Modifier.weight(1f),
                testTag = "answer_yes_button",
              )
              ClinicalAnswerButton(
                label = instrument.responseOptions.initial.noLabel,
                icon = Icons.Rounded.Close,
                selected = currentAnswer == InitialResponseValue.NO,
                onClick = { viewModel.selectAnswer(InitialResponseValue.NO) },
                modifier = Modifier.weight(1f),
                testTag = "answer_no_button",
              )
            }
          }
        }
      }

      // Bottom navigation row inside questionnaire
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SecondaryGlassButton(
          text = if (isBn) "আগের প্রশ্ন" else "Back",
          icon = Icons.AutoMirrored.Rounded.ArrowBack,
          enabled = state.currentIndex > 0,
          onClick = viewModel::goPrevious,
          testTag = "questionnaire_prev_button",
        )
        TextButton(
          onClick = viewModel::openReview,
          modifier = Modifier.testTag("questionnaire_review_all_button"),
        ) {
          Text(if (isBn) "সব দেখুন (${state.answeredCount}/২০)" else "Review All (${state.answeredCount}/20)")
        }
      }
    }
  }
}
