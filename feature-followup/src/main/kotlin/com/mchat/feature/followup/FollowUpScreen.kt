package com.mchat.feature.followup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.PrimaryButton
import com.mchat.core.design.components.SecondaryGlassButton
import com.mchat.core.design.components.SectionHeader
import com.mchat.core.design.components.StatusChip
import com.mchat.core.design.components.StatusTone
import com.mchat.data.local.entity.ScreeningEntity
import com.mchat.data.local.repository.ScreeningRepository
import com.mchat.domain.scoring.engine.ScoringEngine
import com.mchat.domain.scoring.model.FollowUpNode
import com.mchat.domain.scoring.model.FollowUpNodeType
import com.mchat.domain.scoring.model.FollowUpResultValue
import com.mchat.domain.scoring.model.InitialResponseValue
import com.mchat.domain.scoring.model.InstrumentDefinition
import com.mchat.domain.scoring.model.InstrumentItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class FollowUpUiState(
  val screening: ScreeningEntity? = null,
  val instrument: InstrumentDefinition? = null,
  val requiredItems: List<InstrumentItem> = emptyList(),
  val currentItemIndex: Int = 0,
  val currentNodeId: String? = null,
  val selectedPassExamples: Set<String> = emptySet(),
  val selectedFailExamples: Set<String> = emptySet(),
  val traceSteps: List<String> = emptyList(),
  val savedResults: Map<Int, FollowUpResultValue> = emptyMap(),
  val clinicianNotesByItem: Map<Int, String> = emptyMap(),
  val isFinalizing: Boolean = false,
  val finalizedScreeningId: String? = null,
  val errorMessage: String? = null,
) {
  val currentItem: InstrumentItem? get() = requiredItems.getOrNull(currentItemIndex)
  val currentNode: FollowUpNode?
    get() = currentItem?.followUpFlow?.nodes?.firstOrNull { it.nodeId == currentNodeId }
      ?: currentItem?.followUpFlow?.nodes?.firstOrNull()
  val allCompleted: Boolean
    get() = requiredItems.isNotEmpty() && requiredItems.all { savedResults.containsKey(it.officialNumber) }
}

@HiltViewModel
class FollowUpViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val screeningRepository: ScreeningRepository,
  private val instrumentLoader: AssetInstrumentLoader,
) : ViewModel() {

  private val screeningId: String = checkNotNull(savedStateHandle["screeningId"])
  private val json = Json { encodeDefaults = true }

  private val _uiState = MutableStateFlow(FollowUpUiState())
  val uiState: StateFlow<FollowUpUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      val screening = screeningRepository.getScreeningById(screeningId)
      if (screening == null) {
        _uiState.update { it.copy(errorMessage = "Screening not found.") }
        return@launch
      }
      val language = AppLanguage.fromTag(screening.languageUsed)
      val loaded = instrumentLoader.loadVerifiedInstrument(language)
      if (loaded !is InstrumentLoadState.Verified) {
        _uiState.update { it.copy(errorMessage = "Instrument integrity check failed.") }
        return@launch
      }
      val instrument = loaded.instrument.definition
      val stage1Rows = screeningRepository.getQuestionnaireResponses(screening.id, screening.currentRevisionNumber)
      val stage1Map = stage1Rows.associate { it.itemNumber to InitialResponseValue.valueOf(it.responseValue) }
      val initialEval = ScoringEngine.evaluateInitial(instrument, stage1Map, screening.childAgeMonths)

      val required = initialEval.atRiskItemNumbers.mapNotNull { num ->
        instrument.items.firstOrNull { it.officialNumber == num }
      }
      val existingFu = screeningRepository.getFollowUpResponses(screening.id, screening.currentRevisionNumber)
      val savedMap = existingFu.mapNotNull { row ->
        runCatching { row.itemNumber to FollowUpResultValue.valueOf(row.itemResult) }.getOrNull()
      }.toMap()
      val notesMap = existingFu.mapNotNull { row ->
        row.clinicianNotes?.let { row.itemNumber to it }
      }.toMap()

      val firstPendingIdx = required.indexOfFirst { !savedMap.containsKey(it.officialNumber) }.let {
        if (it == -1) 0 else it
      }
      val entryNodeId = required.getOrNull(firstPendingIdx)?.followUpFlow?.entryNodeId

      _uiState.update {
        it.copy(
          screening = screening,
          instrument = instrument,
          requiredItems = required,
          currentItemIndex = firstPendingIdx,
          currentNodeId = entryNodeId,
          savedResults = savedMap,
          clinicianNotesByItem = notesMap,
        )
      }
    }
  }

  fun togglePassExample(id: String) = _uiState.update { state ->
    val next = if (id in state.selectedPassExamples) state.selectedPassExamples - id else state.selectedPassExamples + id
    state.copy(selectedPassExamples = next)
  }

  fun toggleFailExample(id: String) = _uiState.update { state ->
    val next = if (id in state.selectedFailExamples) state.selectedFailExamples - id else state.selectedFailExamples + id
    state.copy(selectedFailExamples = next)
  }

  fun onItemNoteChange(itemNumber: Int, note: String) = _uiState.update { state ->
    state.copy(clinicianNotesByItem = state.clinicianNotesByItem + (itemNumber to note))
  }

  fun applyChecklistEvaluation() {
    val state = _uiState.value
    val item = state.currentItem ?: return
    val node = state.currentNode ?: return
    val hasPass = state.selectedPassExamples.isNotEmpty()
    val hasFail = state.selectedFailExamples.isNotEmpty()

    val condition = when {
      hasPass && !hasFail -> "PASS_ONLY"
      !hasPass && hasFail -> "FAIL_ONLY"
      hasPass && hasFail -> "BOTH"
      else -> "FAIL_ONLY"
    }
    applyNodeTransition(item, node, condition)
  }

  fun applyCondition(condition: String) {
    val state = _uiState.value
    val item = state.currentItem ?: return
    val node = state.currentNode ?: return
    applyNodeTransition(item, node, condition)
  }

  fun recordDirectPassFail(result: FollowUpResultValue) {
    val item = _uiState.value.currentItem ?: return
    val node = _uiState.value.currentNode
    val trace = _uiState.value.traceSteps + "${node?.nodeId ?: "direct"}:${result.name}"
    persistAndAdvance(item, result, trace)
  }

  private fun applyNodeTransition(item: InstrumentItem, node: FollowUpNode, condition: String) {
    val trace = _uiState.value.traceSteps + "${node.nodeId}:$condition"
    val outcome = runCatching {
      ScoringEngine.evaluateFollowUpTransition(item.followUpFlow, node, condition)
    }.getOrNull()

    when {
      outcome == null -> {
        val fallback = if (condition.contains("PASS", ignoreCase = true)) FollowUpResultValue.PASS else FollowUpResultValue.FAIL
        persistAndAdvance(item, fallback, trace)
      }
      outcome.terminalResult != null -> {
        persistAndAdvance(item, outcome.terminalResult!!, trace)
      }
      outcome.nextNode != null -> {
        _uiState.update {
          it.copy(
            currentNodeId = outcome.nextNode!!.nodeId,
            traceSteps = trace,
            selectedPassExamples = emptySet(),
            selectedFailExamples = emptySet(),
          )
        }
      }
    }
  }

  private fun persistAndAdvance(item: InstrumentItem, result: FollowUpResultValue, trace: List<String>) {
    val note = _uiState.value.clinicianNotesByItem[item.officialNumber]
    val traceJson = json.encodeToString(trace)

    _uiState.update { state ->
      val updatedMap = state.savedResults + (item.officialNumber to result)
      val nextIdx = (state.currentItemIndex + 1).coerceAtMost((state.requiredItems.size - 1).coerceAtLeast(0))
      val shouldAdvance = state.currentItemIndex < state.requiredItems.size - 1
      val targetIdx = if (shouldAdvance) nextIdx else state.currentItemIndex
      state.copy(
        savedResults = updatedMap,
        currentItemIndex = targetIdx,
        currentNodeId = state.requiredItems.getOrNull(targetIdx)?.followUpFlow?.entryNodeId,
        selectedPassExamples = emptySet(),
        selectedFailExamples = emptySet(),
        traceSteps = emptyList(),
      )
    }

    viewModelScope.launch {
      screeningRepository.saveFollowUpItem(
        screeningId = screeningId,
        itemNumber = item.officialNumber,
        itemId = item.itemId,
        stepsTraceJson = traceJson,
        result = result,
        clinicianNotes = note,
      )
    }
  }

  fun goPreviousItem() {
    _uiState.update { state ->
      if (state.currentItemIndex > 0) {
        val prevIdx = state.currentItemIndex - 1
        state.copy(
          currentItemIndex = prevIdx,
          currentNodeId = state.requiredItems.getOrNull(prevIdx)?.followUpFlow?.entryNodeId,
          selectedPassExamples = emptySet(),
          selectedFailExamples = emptySet(),
          traceSteps = emptyList(),
        )
      } else {
        state
      }
    }
  }

  fun finalizeFollowUp() {
    val state = _uiState.value
    val instrument = state.instrument ?: return
    if (!state.allCompleted || state.isFinalizing) return

    viewModelScope.launch {
      _uiState.update { it.copy(isFinalizing = true, errorMessage = null) }
      val updated = screeningRepository.completeFollowUpStage(
        screeningId = screeningId,
        instrument = instrument,
      )
      _uiState.update { it.copy(isFinalizing = false, finalizedScreeningId = updated.id) }
    }
  }
}

@Composable
fun FollowUpRoute(
  onBack: () -> Unit,
  onCompleted: (String) -> Unit,
  viewModel: FollowUpViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.finalizedScreeningId) {
    state.finalizedScreeningId?.let(onCompleted)
  }

  BackHandler {
    if (state.currentItemIndex > 0) viewModel.goPreviousItem() else onBack()
  }

  val instrument = state.instrument
  val item = state.currentItem
  val node = state.currentNode

  Column(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = { if (state.currentItemIndex > 0) viewModel.goPreviousItem() else onBack() },
        modifier = Modifier.testTag("followup_back_button"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "Stage 2 Follow-Up",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
          text = "At-Risk Item ${state.currentItemIndex + 1} of ${state.requiredItems.size.coerceAtLeast(1)} • Autosaved",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      StatusChip(
        label = "${state.savedResults.size}/${state.requiredItems.size}",
        icon = Icons.Rounded.Schedule,
        tone = StatusTone.MODERATE_AMBER,
      )
    }

    val progress = if (state.requiredItems.isNotEmpty()) {
      state.savedResults.size.toFloat() / state.requiredItems.size.toFloat()
    } else {
      0f
    }
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier
        .fillMaxWidth()
        .height(8.dp),
    )

    if (state.errorMessage != null) {
      Text(
        text = state.errorMessage!!,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }

    if (instrument != null && item != null && node != null) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        GlassCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Official Item #${"%02d".format(item.officialNumber)}",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
            state.savedResults[item.officialNumber]?.let { saved ->
              StatusChip(
                label = if (saved == FollowUpResultValue.PASS) {
                  instrument.responseOptions.followUp.passLabel
                } else {
                  instrument.responseOptions.followUp.failLabel
                },
                icon = if (saved == FollowUpResultValue.PASS) Icons.Rounded.CheckCircle else Icons.Rounded.Close,
                tone = if (saved == FollowUpResultValue.PASS) StatusTone.LOW_GREEN else StatusTone.HIGH_CORAL,
              )
            }
          }
          Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        GlassCard(contentPadding = PaddingValues(20.dp)) {
          SectionHeader(
            title = "Follow-Up Interview Prompt",
            subtitle = "Driven by verified instrument data (${instrument.instrumentVersion})",
          )
          Text(
            text = node.promptText,
            style = MaterialTheme.typography.titleMedium,
          )

          when (node.nodeType) {
            FollowUpNodeType.MULTI_EXAMPLE_CHECKLIST -> {
              if (node.passExamples.isNotEmpty()) {
                Text(
                  text = "PASS Examples",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
                )
                node.passExamples.forEach { ex ->
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                      checked = ex.id in state.selectedPassExamples,
                      onCheckedChange = { viewModel.togglePassExample(ex.id) },
                    )
                    Text(ex.label, style = MaterialTheme.typography.bodyMedium)
                  }
                }
              }
              if (node.failExamples.isNotEmpty()) {
                Text(
                  text = "FAIL Examples",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.error,
                )
                node.failExamples.forEach { ex ->
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                      checked = ex.id in state.selectedFailExamples,
                      onCheckedChange = { viewModel.toggleFailExample(ex.id) },
                    )
                    Text(ex.label, style = MaterialTheme.typography.bodyMedium)
                  }
                }
              }
              PrimaryButton(
                text = "Evaluate Selected Examples",
                icon = Icons.Rounded.FactCheck,
                onClick = viewModel::applyChecklistEvaluation,
                modifier = Modifier.fillMaxWidth(),
                testTag = "followup_eval_checklist_button",
              )
            }

            FollowUpNodeType.FREQUENCY_TIEBREAKER -> {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                ClinicalAnswerButton(
                  label = "More Often PASS",
                  icon = Icons.Rounded.CheckCircle,
                  selected = false,
                  onClick = { viewModel.applyCondition("PASS_MOST_OFTEN") },
                  modifier = Modifier.weight(1f),
                  testTag = "followup_tiebreaker_pass",
                )
                ClinicalAnswerButton(
                  label = "More Often FAIL",
                  icon = Icons.Rounded.Close,
                  selected = false,
                  onClick = { viewModel.applyCondition("FAIL_MOST_OFTEN") },
                  modifier = Modifier.weight(1f),
                  testTag = "followup_tiebreaker_fail",
                )
              }
            }

            FollowUpNodeType.SINGLE_CHOICE,
            FollowUpNodeType.CLINICIAN_PASS_FAIL,
            -> {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                ClinicalAnswerButton(
                  label = instrument.responseOptions.followUp.passLabel,
                  icon = Icons.Rounded.CheckCircle,
                  selected = state.savedResults[item.officialNumber] == FollowUpResultValue.PASS,
                  onClick = { viewModel.recordDirectPassFail(FollowUpResultValue.PASS) },
                  modifier = Modifier.weight(1f),
                  testTag = "followup_pass_button",
                )
                ClinicalAnswerButton(
                  label = instrument.responseOptions.followUp.failLabel,
                  icon = Icons.Rounded.Close,
                  selected = state.savedResults[item.officialNumber] == FollowUpResultValue.FAIL,
                  onClick = { viewModel.recordDirectPassFail(FollowUpResultValue.FAIL) },
                  modifier = Modifier.weight(1f),
                  testTag = "followup_fail_button",
                )
              }
            }
          }
        }

        GlassCard {
          OutlinedTextField(
            value = state.clinicianNotesByItem[item.officialNumber].orEmpty(),
            onValueChange = { viewModel.onItemNoteChange(item.officialNumber, it) },
            label = { Text("Item #${item.officialNumber} Follow-Up Notes (optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      SecondaryGlassButton(
        text = "Previous",
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        enabled = state.currentItemIndex > 0,
        onClick = viewModel::goPreviousItem,
        modifier = Modifier.weight(0.8f),
      )
      PrimaryButton(
        text = if (state.allCompleted) "Finalize Follow-Up" else "Complete All (${state.savedResults.size}/${state.requiredItems.size})",
        icon = Icons.Rounded.FactCheck,
        enabled = state.allCompleted && !state.isFinalizing,
        onClick = viewModel::finalizeFollowUp,
        modifier = Modifier.weight(1.2f),
        testTag = "followup_finalize_button",
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
  }
}
