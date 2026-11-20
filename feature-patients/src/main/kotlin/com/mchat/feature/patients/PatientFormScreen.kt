package com.mchat.feature.patients

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mchat.core.common.age.AgeCalculator
import com.mchat.core.common.age.AgeRangeStatus
import com.mchat.core.common.age.ChildAge
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.PrimaryButton
import com.mchat.core.design.components.SecondaryGlassButton
import com.mchat.core.design.components.SectionHeader
import com.mchat.core.design.components.StatusChip
import com.mchat.core.design.components.StatusTone
import com.mchat.data.local.entity.PatientEntity
import com.mchat.data.local.repository.PatientRegistrationInput
import com.mchat.data.local.repository.PatientRepository
import com.mchat.data.local.repository.PatientSaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class RegistrationStep {
  CONSENT,
  DETAILS,
}

data class PatientFormUiState(
  val isEditMode: Boolean = false,
  val existingPatient: PatientEntity? = null,
  val step: RegistrationStep = RegistrationStep.CONSENT,
  // Consent step
  val consentedByName: String = "",
  val consentedByRelationship: String = "Mother",
  val consentScope: String = "SCREENING_AND_LOCAL_RECORD",
  val consentConfirmed: Boolean = false,
  // Patient details
  val useAutoCode: Boolean = true,
  val patientCode: String = "",
  val duplicateCodeDetected: Boolean = false,
  val fullName: String = "",
  val dateOfBirthIso: String = LocalDate.now().minusMonths(20).toString(),
  val ageResult: ChildAge? = null,
  val dobError: String? = null,
  val outOfRangeAcknowledged: Boolean = false,
  val sex: String = "FEMALE",
  val extraFields: Map<String, String> = emptyMap(),
  val clinicalNotes: String = "",
  // Caregiver details
  val caregiverName: String = "",
  val caregiverRelationship: String = "Mother",
  val caregiverPhone: String = "",
  val caregiverEmail: String = "",
  // Status
  val isSaving: Boolean = false,
  val errorMessage: String? = null,
  val savedPatientId: String? = null,
)

@HiltViewModel
class PatientFormViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val patientRepository: PatientRepository,
) : ViewModel() {

  private val editPatientId: String? = savedStateHandle["patientId"]

  private val _uiState = MutableStateFlow(PatientFormUiState())
  val uiState: StateFlow<PatientFormUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      if (!editPatientId.isNullOrBlank()) {
        val patient = patientRepository.getPatientById(editPatientId)
        val caregiver = patient?.let { patientRepository.getPrimaryCaregiver(it.id) }
        if (patient != null) {
          val age = runCatching {
            AgeCalculator.calculate(LocalDate.parse(patient.dateOfBirth), LocalDate.now())
          }.getOrNull()
          _uiState.update {
            it.copy(
              isEditMode = true,
              existingPatient = patient,
              step = RegistrationStep.DETAILS,
              useAutoCode = false,
              patientCode = patient.patientCode,
              fullName = patient.fullName,
              dateOfBirthIso = patient.dateOfBirth,
              ageResult = age,
              sex = patient.sex,
              extraFields = PatientFieldRegistry.decode(patient.extraFieldsJson),
              clinicalNotes = patient.clinicalNotes.orEmpty(),
              caregiverName = caregiver?.fullName.orEmpty(),
              caregiverRelationship = caregiver?.relationship ?: "Mother",
              caregiverPhone = caregiver?.phone.orEmpty(),
              caregiverEmail = caregiver?.email.orEmpty(),
            )
          }
          return@launch
        }
      }
      val suggested = patientRepository.generateNextPatientCode()
      val defaultDob = LocalDate.now().minusMonths(20)
      _uiState.update {
        it.copy(
          patientCode = suggested,
          dateOfBirthIso = defaultDob.toString(),
          ageResult = AgeCalculator.calculate(defaultDob, LocalDate.now()),
        )
      }
    }
  }

  fun onConsentNameChange(v: String) = _uiState.update {
    it.copy(
      consentedByName = v,
      caregiverName = if (it.caregiverName.isBlank()) v else it.caregiverName,
    )
  }

  fun onConsentRelationshipChange(v: String) = _uiState.update {
    it.copy(consentedByRelationship = v, caregiverRelationship = v)
  }

  fun onConsentScopeChange(scope: String) = _uiState.update { it.copy(consentScope = scope) }
  fun onConsentConfirmedChange(checked: Boolean) = _uiState.update { it.copy(consentConfirmed = checked) }

  fun proceedFromConsent() {
    val state = _uiState.value
    if (state.consentedByName.isBlank() || !state.consentConfirmed) {
      _uiState.update {
        it.copy(errorMessage = "Enter the consenting caregiver's name and confirm consent before proceeding.")
      }
      return
    }
    _uiState.update {
      it.copy(
        step = RegistrationStep.DETAILS,
        caregiverName = it.caregiverName.ifBlank { it.consentedByName },
        errorMessage = null,
      )
    }
  }

  fun onToggleAutoCode(auto: Boolean) {
    _uiState.update { it.copy(useAutoCode = auto, duplicateCodeDetected = false) }
    if (auto) {
      viewModelScope.launch {
        val next = patientRepository.generateNextPatientCode()
        _uiState.update { it.copy(patientCode = next, duplicateCodeDetected = false) }
      }
    }
  }

  fun onPatientCodeChange(code: String) {
    _uiState.update { it.copy(patientCode = code, useAutoCode = false) }
    viewModelScope.launch {
      val duplicate = if (code.isNotBlank()) {
        patientRepository.isCodeDuplicate(code, excludePatientId = _uiState.value.existingPatient?.id)
      } else {
        false
      }
      _uiState.update { it.copy(duplicateCodeDetected = duplicate) }
    }
  }

  fun onFullNameChange(v: String) = _uiState.update { it.copy(fullName = v, errorMessage = null) }

  fun onDobChange(raw: String) {
    val trimmed = raw.trim()
    val parsed = runCatching { LocalDate.parse(trimmed) }.getOrNull()
    val today = LocalDate.now()
    val (age, error) = when {
      parsed == null -> null to "Use YYYY-MM-DD format (e.g. 2024-05-18)"
      parsed.isAfter(today) -> null to "Date of birth cannot be in the future"
      else -> AgeCalculator.calculate(parsed, today) to null
    }
    _uiState.update {
      it.copy(
        dateOfBirthIso = raw,
        ageResult = age,
        dobError = error,
        outOfRangeAcknowledged = if (age?.isWithinIntendedRange == true) false else it.outOfRangeAcknowledged,
      )
    }
  }

  fun onOutOfRangeAcknowledgedChange(checked: Boolean) =
    _uiState.update { it.copy(outOfRangeAcknowledged = checked, errorMessage = null) }

  fun onSexChange(v: String) = _uiState.update { it.copy(sex = v) }
  fun onExtraFieldChange(key: String, value: String) = _uiState.update {
    it.copy(extraFields = it.extraFields + (key to value))
  }
  fun onClinicalNotesChange(v: String) = _uiState.update { it.copy(clinicalNotes = v) }
  fun onCaregiverNameChange(v: String) = _uiState.update { it.copy(caregiverName = v) }
  fun onCaregiverRelationshipChange(v: String) = _uiState.update { it.copy(caregiverRelationship = v) }
  fun onCaregiverPhoneChange(v: String) = _uiState.update { it.copy(caregiverPhone = v) }
  fun onCaregiverEmailChange(v: String) = _uiState.update { it.copy(caregiverEmail = v) }

  fun savePatient() {
    val state = _uiState.value
    if (state.fullName.isBlank()) {
      _uiState.update { it.copy(errorMessage = "Child's full name is required.") }
      return
    }
    val age = state.ageResult
    if (age == null || state.dobError != null) {
      _uiState.update { it.copy(errorMessage = "Enter a valid Date of Birth (YYYY-MM-DD).") }
      return
    }
    if (!age.isWithinIntendedRange && !state.outOfRangeAcknowledged) {
      _uiState.update {
        it.copy(errorMessage = "Please acknowledge the 16–30 month clinical age warning before saving.")
      }
      return
    }
    if (!state.useAutoCode && state.patientCode.isBlank()) {
      _uiState.update { it.copy(errorMessage = "Enter a Patient ID or enable Auto ID.") }
      return
    }
    if (state.duplicateCodeDetected) {
      _uiState.update { it.copy(errorMessage = "Patient ID '${state.patientCode}' already exists.") }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, errorMessage = null) }
      val extraJson = PatientFieldRegistry.encode(state.extraFields)
      val result = if (state.isEditMode && state.existingPatient != null) {
        patientRepository.updatePatient(
          existing = state.existingPatient,
          newCode = state.patientCode,
          fullName = state.fullName,
          dateOfBirthIso = state.dateOfBirthIso,
          sex = state.sex,
          extraFieldsJson = extraJson,
          clinicalNotes = state.clinicalNotes,
        )
      } else {
        patientRepository.registerPatientWithConsent(
          PatientRegistrationInput(
            patientCode = if (state.useAutoCode) null else state.patientCode,
            fullName = state.fullName,
            dateOfBirthIso = state.dateOfBirthIso,
            sex = state.sex,
            extraFieldsJson = extraJson,
            clinicalNotes = state.clinicalNotes,
            caregiverName = state.caregiverName.ifBlank { state.consentedByName },
            caregiverRelationship = state.caregiverRelationship,
            caregiverPhone = state.caregiverPhone,
            caregiverEmail = state.caregiverEmail,
            consentedByName = state.consentedByName,
            consentedByRelationship = state.consentedByRelationship,
            consentScope = state.consentScope,
          ),
        )
      }

      when (result) {
        is PatientSaveResult.DuplicateCode -> _uiState.update {
          it.copy(
            isSaving = false,
            duplicateCodeDetected = true,
            errorMessage = "Patient ID '${result.existingCode}' is already in use.",
          )
        }
        is PatientSaveResult.Success -> _uiState.update {
          it.copy(isSaving = false, savedPatientId = result.patientId)
        }
      }
    }
  }
}

@Composable
fun PatientFormRoute(
  onBack: () -> Unit,
  onSaved: (String) -> Unit,
  viewModel: PatientFormViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.savedPatientId) {
    state.savedPatientId?.let(onSaved)
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 48.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("patient_form_back")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column {
          Text(
            text = when {
              state.isEditMode -> "Edit Patient Record"
              state.step == RegistrationStep.CONSENT -> "Caregiver Consent"
              else -> "Register Patient"
            },
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text = if (state.step == RegistrationStep.CONSENT && !state.isEditMode) {
              "Step 1 of 2 — Required before storing a child's clinical data"
            } else {
              "Encrypted local storage • SQLCipher AES-256"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    if (state.step == RegistrationStep.CONSENT && !state.isEditMode) {
      item {
        GlassCard {
          SectionHeader(
            title = "Informed Screening & Record Consent",
            subtitle = "Recorded immutably with timestamp and audit log",
          )
          Text(
            text = "I understand that the M-CHAT-R/F is a developmental screening tool and NOT a medical diagnosis. " +
              "I consent to recording my child's demographic and screening responses on this clinician's encrypted device " +
              "(and optional encrypted clinician backups if selected below).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          OutlinedTextField(
            value = state.consentedByName,
            onValueChange = viewModel::onConsentNameChange,
            label = { Text("Consenting Caregiver Full Name *") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("consent_name_input"),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Mother", "Father", "Guardian").forEach { rel ->
              FilterChip(
                selected = state.consentedByRelationship == rel,
                onClick = { viewModel.onConsentRelationshipChange(rel) },
                label = { Text(rel) },
              )
            }
          }
          Text("Consent Scope", style = MaterialTheme.typography.labelLarge)
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = state.consentScope == "SCREENING_AND_LOCAL_RECORD",
              onClick = { viewModel.onConsentScopeChange("SCREENING_AND_LOCAL_RECORD") },
              label = { Text("Screening + Encrypted Local Record Only") },
            )
            FilterChip(
              selected = state.consentScope == "SCREENING_LOCAL_AND_ENCRYPTED_BACKUP",
              onClick = { viewModel.onConsentScopeChange("SCREENING_LOCAL_AND_ENCRYPTED_BACKUP") },
              label = { Text("Screening + Local Record + Encrypted Drive Backup") },
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Checkbox(
              checked = state.consentConfirmed,
              onCheckedChange = viewModel::onConsentConfirmedChange,
              modifier = Modifier.testTag("consent_confirm_checkbox"),
            )
            Text(
              text = "Caregiver gave informed consent to administer M-CHAT-R/F and store records.",
              style = MaterialTheme.typography.bodyMedium,
            )
          }

          if (state.errorMessage != null) {
            Text(
              text = state.errorMessage!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }

          PrimaryButton(
            text = "Continue to Child Details",
            icon = Icons.Rounded.FactCheck,
            onClick = viewModel::proceedFromConsent,
            modifier = Modifier.fillMaxWidth(),
            testTag = "consent_continue_button",
          )
        }
      }
    } else {
      item {
        GlassCard {
          SectionHeader(
            title = "Patient Identification",
            subtitle = "Auto-generated or manual code with live duplicate check",
          )
          if (!state.isEditMode) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Auto-generate Patient ID", style = MaterialTheme.typography.bodyLarge)
              Switch(
                checked = state.useAutoCode,
                onCheckedChange = viewModel::onToggleAutoCode,
              )
            }
          }
          OutlinedTextField(
            value = state.patientCode,
            onValueChange = viewModel::onPatientCodeChange,
            label = { Text("Patient ID *") },
            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            singleLine = true,
            isError = state.duplicateCodeDetected,
            supportingText = if (state.duplicateCodeDetected) {
              { Text("Duplicate Patient ID detected. Choose a unique code.") }
            } else {
              null
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("patient_code_input"),
          )
          OutlinedTextField(
            value = state.fullName,
            onValueChange = viewModel::onFullNameChange,
            label = { Text("Child's Full Name *") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("patient_name_input"),
          )
          Text("Sex", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("FEMALE" to "Female", "MALE" to "Male", "OTHER" to "Other").forEach { (key, label) ->
              FilterChip(
                selected = state.sex == key,
                onClick = { viewModel.onSexChange(key) },
                label = { Text(label) },
              )
            }
          }
        }
      }

      item {
        GlassCard {
          SectionHeader(
            title = "Date of Birth & M-CHAT-R/F Age Check",
            subtitle = "Intended validated age range: 16–30 months",
          )
          OutlinedTextField(
            value = state.dateOfBirthIso,
            onValueChange = viewModel::onDobChange,
            label = { Text("Date of Birth (YYYY-MM-DD) *") },
            singleLine = true,
            isError = state.dobError != null,
            supportingText = state.dobError?.let { err -> { Text(err) } },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("patient_dob_input"),
          )

          state.ageResult?.let { age ->
            val ageText = "${age.years} yr ${age.remainingMonths} mo (${age.totalMonths} months, ${age.remainingDays} days)"
            Text(
              text = "Calculated Age: $ageText",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            if (age.isWithinIntendedRange) {
              StatusChip(
                label = "Within validated 16–30 month range",
                icon = Icons.Rounded.CheckCircle,
                tone = StatusTone.LOW_GREEN,
              )
            } else {
              StatusChip(
                label = if (age.rangeStatus == AgeRangeStatus.UNDER_RANGE) {
                  "Under 16 months — Outside intended range"
                } else {
                  "Over 30 months — Outside intended range"
                },
                icon = Icons.Rounded.WarningAmber,
                tone = StatusTone.MODERATE_AMBER,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Checkbox(
                  checked = state.outOfRangeAcknowledged,
                  onCheckedChange = viewModel::onOutOfRangeAcknowledgedChange,
                  modifier = Modifier.testTag("age_out_of_range_ack_checkbox"),
                )
                Text(
                  text = "I acknowledge the child is outside the 16–30 month validated M-CHAT-R/F range and wish to proceed.",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
      }

      item {
        GlassCard {
          SectionHeader(
            title = "Primary Caregiver",
            subtitle = "Contact details stay strictly on this encrypted device",
          )
          OutlinedTextField(
            value = state.caregiverName,
            onValueChange = viewModel::onCaregiverNameChange,
            label = { Text("Caregiver Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = state.caregiverPhone,
            onValueChange = viewModel::onCaregiverPhoneChange,
            label = { Text("Phone (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = state.caregiverEmail,
            onValueChange = viewModel::onCaregiverEmailChange,
            label = { Text("Email (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      item {
        GlassCard {
          SectionHeader(
            title = "Configurable Intake Fields",
            subtitle = "Defined centrally in PatientFieldRegistry",
          )
          PatientFieldRegistry.fields.forEach { spec ->
            OutlinedTextField(
              value = state.extraFields[spec.key].orEmpty(),
              onValueChange = { viewModel.onExtraFieldChange(spec.key, it) },
              label = { Text(spec.labelEn) },
              placeholder = { Text(spec.placeholderEn) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = if (spec.inputType == ExtraFieldInputType.NUMBER) {
                  KeyboardType.Decimal
                } else {
                  KeyboardType.Text
                },
              ),
              modifier = Modifier.fillMaxWidth(),
            )
          }
          OutlinedTextField(
            value = state.clinicalNotes,
            onValueChange = viewModel::onClinicalNotesChange,
            label = { Text("Clinical Notes (optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
          )
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
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SecondaryGlassButton(
            text = "Cancel",
            onClick = onBack,
            modifier = Modifier.weight(1f),
          )
          PrimaryButton(
            text = if (state.isEditMode) "Save Changes" else "Save Patient",
            icon = Icons.Rounded.Save,
            enabled = !state.isSaving,
            onClick = viewModel::savePatient,
            modifier = Modifier.weight(1f),
            testTag = "save_patient_button",
          )
        }
      }
    }

    item { Spacer(modifier = Modifier.height(24.dp)) }
  }
}
