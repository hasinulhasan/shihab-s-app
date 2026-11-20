package com.mchat.feature.screening

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mchat.core.common.AppLanguage
import com.mchat.core.common.age.AgeCalculator
import com.mchat.core.common.age.ChildAge
import com.mchat.core.common.instrument.AssetInstrumentLoader
import com.mchat.core.common.instrument.InstrumentLoadState
import com.mchat.core.design.R
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.PrimaryButton
import com.mchat.core.design.components.SecondaryGlassButton
import com.mchat.core.design.components.SectionHeader
import com.mchat.core.design.components.StatusChip
import com.mchat.core.design.components.StatusTone
import com.mchat.data.local.entity.PatientEntity
import com.mchat.data.local.entity.ScreeningEntity
import com.mchat.data.local.preferences.UiPreferencesRepository
import com.mchat.data.local.repository.PatientRepository
import com.mchat.data.local.repository.ScreeningRepository
import com.mchat.domain.scoring.model.ScreeningStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class UnfinishedScreeningPrompt(
  val screening: ScreeningEntity,
  val patientName: String,
  val patientCode: String,
  val answeredCount: Int,
)

data class NewSurveyUiState(
  val patients: List<PatientEntity> = emptyList(),
  val searchQuery: String = "",
  val selectedPatientId: String? = null,
  val selectedPatientAge: ChildAge? = null,
  val outOfRangeAcknowledged: Boolean = false,
  val selectedLanguage: AppLanguage = AppLanguage.ENGLISH,
  val instrumentStates: Map<AppLanguage, InstrumentLoadState> = emptyMap(),
  val unfinishedPrompt: UnfinishedScreeningPrompt? = null,
  val errorMessage: String? = null,
  val isCreating: Boolean = false,
  val createdScreeningId: String? = null,
) {
  val selectedInstrumentState: InstrumentLoadState?
    get() = instrumentStates[selectedLanguage]

  val isInstrumentVerified: Boolean
    get() = selectedInstrumentState is InstrumentLoadState.Verified
}

@HiltViewModel
class NewSurveyViewModel @Inject constructor(
  private val patientRepository: PatientRepository,
  private val screeningRepository: ScreeningRepository,
  private val preferencesRepository: UiPreferencesRepository,
  private val instrumentLoader: AssetInstrumentLoader,
) : ViewModel() {

  private val _uiState = MutableStateFlow(NewSurveyUiState())
  val uiState: StateFlow<NewSurveyUiState> = _uiState.asStateFlow()

  init {
    val verifiedMap = instrumentLoader.verifyAllBundledInstruments()
    _uiState.update { it.copy(instrumentStates = verifiedMap) }

    viewModelScope.launch {
      combine(
        patientRepository.observeActivePatients(),
        preferencesRepository.preferences,
      ) { patients, prefs -> patients to prefs }.collect { (patients, prefs) ->
        _uiState.update { current ->
          val selectedId = current.selectedPatientId?.takeIf { id -> patients.any { it.id == id } }
          val selectedEntity = patients.firstOrNull { it.id == selectedId }
          val age = selectedEntity?.let { p ->
            runCatching { AgeCalculator.calculate(LocalDate.parse(p.dateOfBirth), LocalDate.now()) }.getOrNull()
          }
          current.copy(
            patients = patients,
            selectedPatientId = selectedId,
            selectedPatientAge = age,
            selectedLanguage = if (current.selectedPatientId == null) prefs.language else current.selectedLanguage,
          )
        }
      }
    }
    refreshUnfinishedPrompt()
  }

  fun preselectPatient(patientId: String?) {
    if (patientId.isNullOrBlank()) return
    selectPatient(patientId)
  }

  fun refreshUnfinishedPrompt() {
    viewModelScope.launch {
      val unfinished = screeningRepository.getLatestUnfinishedScreening()
      if (unfinished == null) {
        _uiState.update { it.copy(unfinishedPrompt = null) }
      } else {
        val patient = patientRepository.getPatientById(unfinished.patientId)
        val answered = screeningRepository.getQuestionnaireResponses(
          unfinished.id,
          unfinished.currentRevisionNumber,
        ).size
        _uiState.update {
          it.copy(
            unfinishedPrompt = UnfinishedScreeningPrompt(
              screening = unfinished,
              patientName = patient?.fullName ?: "Unknown",
              patientCode = patient?.patientCode ?: "",
              answeredCount = answered,
            ),
          )
        }
      }
    }
  }

  fun onSearchChange(q: String) = _uiState.update { it.copy(searchQuery = q) }

  fun selectPatient(patientId: String) {
    val entity = _uiState.value.patients.firstOrNull { it.id == patientId }
    val age = entity?.let {
      runCatching { AgeCalculator.calculate(LocalDate.parse(it.dateOfBirth), LocalDate.now()) }.getOrNull()
    }
    _uiState.update {
      it.copy(
        selectedPatientId = patientId,
        selectedPatientAge = age,
        outOfRangeAcknowledged = false,
        errorMessage = null,
      )
    }
  }

  fun onLanguageSelect(language: AppLanguage) {
    _uiState.update { it.copy(selectedLanguage = language, errorMessage = null) }
  }

  fun onOutOfRangeAcknowledged(checked: Boolean) {
    _uiState.update { it.copy(outOfRangeAcknowledged = checked, errorMessage = null) }
  }

  fun consumeCreatedScreening() {
    _uiState.update { it.copy(createdScreeningId = null) }
  }

  fun startScreening() {
    val state = _uiState.value
    val verified = state.selectedInstrumentState as? InstrumentLoadState.Verified
    if (verified == null) {
      _uiState.update {
        it.copy(errorMessage = "Instrument SHA-256 verification failed. Screening cannot start.")
      }
      return
    }
    val patient = state.patients.firstOrNull { it.id == state.selectedPatientId }
    if (patient == null) {
      _uiState.update { it.copy(errorMessage = "Select a registered patient or register a new child first.") }
      return
    }
    val age = state.selectedPatientAge
    if (age != null && !age.isWithinIntendedRange && !state.outOfRangeAcknowledged) {
      _uiState.update {
        it.copy(errorMessage = "Please acknowledge the 16–30 month clinical age warning before starting.")
      }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isCreating = true, errorMessage = null) }
      val consent = patientRepository.getLatestActiveConsent(patient.id)
      if (consent == null) {
        _uiState.update {
          it.copy(
            isCreating = false,
            errorMessage = "Active caregiver consent record is required before screening.",
          )
        }
        return@launch
      }
      val caregiver = patientRepository.getPrimaryCaregiver(patient.id)
      val draft = screeningRepository.createDraftScreening(
        patientId = patient.id,
        caregiverId = caregiver?.id,
        consentRecordId = consent.id,
        patientDobIso = patient.dateOfBirth,
        outOfRangeAcknowledged = state.outOfRangeAcknowledged,
        instrument = verified.instrument.definition,
        verifiedChecksum = verified.instrument.verifiedSha256Hex,
      )
      _uiState.update { it.copy(isCreating = false, createdScreeningId = draft.id) }
    }
  }
}

@Composable
fun NewSurveyRoute(
  preselectedPatientId: String? = null,
  onRegisterNewPatient: () -> Unit = {},
  onStartQuestionnaire: (String) -> Unit = {},
  onResumeFollowUp: (String) -> Unit = {},
  viewModel: NewSurveyViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(preselectedPatientId) {
    viewModel.preselectPatient(preselectedPatientId)
    viewModel.refreshUnfinishedPrompt()
  }

  LaunchedEffect(state.createdScreeningId) {
    state.createdScreeningId?.let { id ->
      viewModel.consumeCreatedScreening()
      onStartQuestionnaire(id)
    }
  }

  val filteredPatients = state.patients.filter { p ->
    state.searchQuery.isBlank() ||
      p.fullName.contains(state.searchQuery, ignoreCase = true) ||
      p.patientCode.contains(state.searchQuery, ignoreCase = true)
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 128.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.new_survey_title),
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          text = stringResource(R.string.non_diagnostic_notice),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Resume prompt if unfinished screening exists (e.g. after app kill)
    state.unfinishedPrompt?.let { unfinished ->
      item {
        GlassCard {
          SectionHeader(
            title = "Unfinished Screening Detected",
            subtitle = "${unfinished.patientName} (${unfinished.patientCode}) • ${unfinished.answeredCount}/20 answered",
          )
          StatusChip(
            label = "Autosaved on device — ready to resume",
            icon = Icons.Rounded.Restore,
            tone = StatusTone.MODERATE_AMBER,
          )
          PrimaryButton(
            text = if (unfinished.screening.status == ScreeningStatus.FOLLOWUP_IN_PROGRESS) {
              "Resume Follow-Up Interview"
            } else {
              "Resume Questionnaire"
            },
            icon = Icons.Rounded.PlayArrow,
            onClick = {
              if (unfinished.screening.status == ScreeningStatus.FOLLOWUP_IN_PROGRESS) {
                onResumeFollowUp(unfinished.screening.id)
              } else {
                onStartQuestionnaire(unfinished.screening.id)
              }
            },
            modifier = Modifier.fillMaxWidth(),
            testTag = "resume_unfinished_screening_button",
          )
        }
      }
    }

    // Instrument integrity & language selector
    item {
      GlassCard {
        SectionHeader(
          title = "Instrument Language & Integrity",
          subtitle = "SHA-256 checksum verified before every screening",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          FilterChip(
            selected = state.selectedLanguage == AppLanguage.ENGLISH,
            onClick = { viewModel.onLanguageSelect(AppLanguage.ENGLISH) },
            label = { Text("English (M-CHAT-R/F)") },
            modifier = Modifier.testTag("lang_chip_en"),
          )
          FilterChip(
            selected = state.selectedLanguage == AppLanguage.BANGLA,
            onClick = { viewModel.onLanguageSelect(AppLanguage.BANGLA) },
            label = { Text("বাংলা (M-CHAT-R/F)") },
            modifier = Modifier.testTag("lang_chip_bn"),
          )
        }
        when (val inst = state.selectedInstrumentState) {
          is InstrumentLoadState.Verified -> {
            StatusChip(
              label = "Verified ${inst.instrument.definition.instrumentVersion} • SHA-256 ${inst.instrument.verifiedSha256Hex.take(10)}…",
              icon = Icons.Rounded.VerifiedUser,
              tone = StatusTone.LOW_GREEN,
            )
          }
          is InstrumentLoadState.IntegrityFailure -> {
            StatusChip(
              label = "Integrity check FAILED — screening blocked",
              icon = Icons.Rounded.ErrorOutline,
              tone = StatusTone.HIGH_CORAL,
            )
            Text(
              text = inst.reason,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
          null -> Unit
        }
      }
    }

    // Patient Selection
    item {
      GlassCard {
        SectionHeader(
          title = "Select Child Record",
          subtitle = "Or register a new child with caregiver consent",
        )
        SecondaryGlassButton(
          text = "Register New Patient (Consent + Profile)",
          icon = Icons.Rounded.PersonAdd,
          onClick = onRegisterNewPatient,
          modifier = Modifier.fillMaxWidth(),
          testTag = "new_survey_register_patient_button",
        )
        if (state.patients.isNotEmpty()) {
          OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchChange,
            placeholder = { Text("Filter registered patients by name or ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    items(filteredPatients.take(8), key = { it.id }) { p ->
      val isSelected = p.id == state.selectedPatientId
      val age = runCatching { AgeCalculator.calculate(LocalDate.parse(p.dateOfBirth), LocalDate.now()) }.getOrNull()
      GlassCard(
        onClick = { viewModel.selectPatient(p.id) },
        modifier = Modifier.testTag("select_patient_${p.patientCode}"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = p.fullName,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
              text = "${p.patientCode} • ${age?.let { "${it.years}y ${it.remainingMonths}m (${it.totalMonths}m)" } ?: p.dateOfBirth}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (isSelected) {
            StatusChip("Selected", Icons.Rounded.CheckCircle, StatusTone.LOW_GREEN)
          }
        }
      }
    }

    // Age warning if selected patient is outside 16–30 months
    state.selectedPatientAge?.let { age ->
      if (!age.isWithinIntendedRange) {
        item {
          GlassCard {
            StatusChip(
              label = "Child age (${age.totalMonths}m) is outside 16–30 months",
              icon = Icons.Rounded.WarningAmber,
              tone = StatusTone.MODERATE_AMBER,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = state.outOfRangeAcknowledged,
                onCheckedChange = viewModel::onOutOfRangeAcknowledged,
                modifier = Modifier.testTag("screening_age_ack_checkbox"),
              )
              Text(
                text = "Acknowledge out-of-range age and continue screening.",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      }
    }

    if (state.errorMessage != null) {
      item {
        Text(
          text = state.errorMessage!!,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }

    item {
      PrimaryButton(
        text = "Begin 20-Item M-CHAT-R/F",
        icon = Icons.Rounded.PlayArrow,
        enabled = state.isInstrumentVerified && state.selectedPatientId != null && !state.isCreating,
        onClick = viewModel::startScreening,
        modifier = Modifier.fillMaxWidth(),
        testTag = "begin_questionnaire_button",
      )
    }

    item { Spacer(modifier = Modifier.height(12.dp)) }
  }
}
