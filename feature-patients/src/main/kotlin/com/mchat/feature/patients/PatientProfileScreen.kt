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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mchat.core.common.age.AgeCalculator
import com.mchat.core.design.components.ClinicianReAuthDialog
import com.mchat.core.design.components.EmptyStateCard
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.OutcomeStatusChip
import com.mchat.core.design.components.PrimaryButton
import com.mchat.core.design.components.SecondaryGlassButton
import com.mchat.core.design.components.SectionHeader
import com.mchat.core.design.components.StatusChip
import com.mchat.core.design.components.StatusTone
import com.mchat.data.local.entity.CaregiverEntity
import com.mchat.data.local.entity.ConsentRecordEntity
import com.mchat.data.local.entity.PatientEntity
import com.mchat.data.local.entity.ScreeningEntity
import com.mchat.data.local.preferences.AppShellPreferences
import com.mchat.data.local.preferences.UiPreferencesRepository
import com.mchat.data.local.repository.PatientRepository
import com.mchat.data.local.repository.ScreeningRepository
import com.mchat.domain.scoring.model.ScreeningStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class PatientProfileUiState(
  val patient: PatientEntity? = null,
  val caregivers: List<CaregiverEntity> = emptyList(),
  val consents: List<ConsentRecordEntity> = emptyList(),
  val screenings: List<ScreeningEntity> = emptyList(),
  val preferences: AppShellPreferences = AppShellPreferences(),
)

@HiltViewModel
class PatientProfileViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val patientRepository: PatientRepository,
  screeningRepository: ScreeningRepository,
  preferencesRepository: UiPreferencesRepository,
) : ViewModel() {

  private val patientId: String = checkNotNull(savedStateHandle["patientId"])

  val uiState: StateFlow<PatientProfileUiState> = combine(
    patientRepository.observePatientById(patientId),
    patientRepository.observeCaregivers(patientId),
    patientRepository.observeConsents(patientId),
    screeningRepository.observeScreeningsForPatient(patientId),
    preferencesRepository.preferences,
  ) { patient, caregivers, consents, screenings, prefs ->
    PatientProfileUiState(
      patient = patient,
      caregivers = caregivers,
      consents = consents,
      screenings = screenings,
      preferences = prefs,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientProfileUiState())

  fun toggleArchive(archived: Boolean) {
    viewModelScope.launch {
      patientRepository.setPatientArchived(patientId, archived)
    }
  }
}

@Composable
fun PatientProfileRoute(
  onBack: () -> Unit,
  onEditPatient: (String) -> Unit,
  onStartScreening: (String) -> Unit,
  onOpenScreening: (ScreeningEntity) -> Unit,
  viewModel: PatientProfileViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showArchiveReAuth by remember { mutableStateOf(false) }

  val patient = state.patient

  if (showArchiveReAuth && patient != null) {
    val archiving = !patient.isArchived
    ClinicianReAuthDialog(
      title = if (archiving) "Archive Patient Record" else "Restore Patient Record",
      message = if (archiving) {
        "Soft-archiving hides ${patient.fullName} from active lists without deleting history. Clinician verification is required."
      } else {
        "Restore ${patient.fullName} to active patient lists?"
      },
      confirmLabel = if (archiving) "Archive" else "Restore",
      pinDigest = state.preferences.pinDigest,
      biometricEnabled = state.preferences.biometricEnabled,
      onDismiss = { showArchiveReAuth = false },
      onAuthenticated = {
        showArchiveReAuth = false
        viewModel.toggleArchive(archiving)
      },
    )
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
          Text("Patient Profile", style = MaterialTheme.typography.headlineSmall)
        }
        if (patient != null) {
          IconButton(
            onClick = { onEditPatient(patient.id) },
            modifier = Modifier.testTag("profile_edit_button"),
          ) {
            Icon(Icons.Rounded.Edit, contentDescription = "Edit patient")
          }
        }
      }
    }

    if (patient == null) {
      item {
        EmptyStateCard(
          icon = Icons.Rounded.VerifiedUser,
          title = "Patient Not Found",
          body = "The requested encrypted patient record could not be loaded.",
        )
      }
    } else {
      val age = runCatching { AgeCalculator.calculate(LocalDate.parse(patient.dateOfBirth), LocalDate.now()) }.getOrNull()
      val extraValues = PatientFieldRegistry.decode(patient.extraFieldsJson)

      item {
        GlassCard {
          Text(
            text = patient.fullName,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
          )
          Text(
            text = "ID: ${patient.patientCode} • Sex: ${patient.sex} • DOB: ${patient.dateOfBirth}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (age != null) {
            Text(
              text = "Age: ${age.years} yr ${age.remainingMonths} mo (${age.totalMonths} months)",
              style = MaterialTheme.typography.titleSmall,
            )
          }
          if (extraValues.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            PatientFieldRegistry.fields.forEach { spec ->
              val v = extraValues[spec.key]
              if (!v.isNullOrBlank()) {
                Text(
                  text = "${spec.labelEn}: $v",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          if (!patient.clinicalNotes.isNullOrBlank()) {
            Text(
              text = "Notes: ${patient.clinicalNotes}",
              style = MaterialTheme.typography.bodySmall,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            PrimaryButton(
              text = "Start Screening",
              icon = Icons.Rounded.Add,
              onClick = { onStartScreening(patient.id) },
              modifier = Modifier.weight(1f),
              testTag = "profile_start_screening_button",
            )
            SecondaryGlassButton(
              text = if (patient.isArchived) "Restore" else "Archive",
              icon = if (patient.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
              onClick = { showArchiveReAuth = true },
              modifier = Modifier.weight(0.8f),
              testTag = "profile_archive_button",
            )
          }
        }
      }

      item {
        GlassCard {
          SectionHeader(title = "Caregiver & Consent Record")
          state.caregivers.firstOrNull()?.let { cg ->
            Text(
              text = "${cg.fullName} (${cg.relationship})",
              style = MaterialTheme.typography.titleSmall,
            )
            if (!cg.phone.isNullOrBlank() || !cg.email.isNullOrBlank()) {
              Text(
                text = listOfNotNull(cg.phone, cg.email).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          state.consents.firstOrNull()?.let { consent ->
            val dateStr = DateTimeFormatter.ISO_LOCAL_DATE
              .withZone(ZoneId.systemDefault())
              .format(Instant.ofEpochMilli(consent.consentedAt))
            StatusChip(
              label = "Consent on $dateStr by ${consent.consentedByName}",
              icon = Icons.Rounded.VerifiedUser,
              tone = StatusTone.LOW_GREEN,
            )
          }
        }
      }

      item {
        SectionHeader(
          title = "Screening History (${state.screenings.size})",
          subtitle = "Completed screenings are immutable; corrections create versioned revisions",
        )
      }

      if (state.screenings.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.Rounded.Add,
            title = "No Screenings Yet",
            body = "Tap 'Start Screening' above to administer M-CHAT-R/F for this child.",
          )
        }
      } else {
        items(state.screenings, key = { it.id }) { s ->
          GlassCard(
            onClick = { onOpenScreening(s) },
            modifier = Modifier.testTag("patient_screening_${s.id}"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${s.screeningDate} • Rev ${s.currentRevisionNumber} (${s.languageUsed.uppercase()})",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = if (s.initialScore != null) "Initial: ${s.initialScore}/20" else s.status.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutcomeStatusChip(outcome = s.finalOutcome)
              if (s.status == ScreeningStatus.FOLLOWUP_REQUIRED || s.status == ScreeningStatus.FOLLOWUP_IN_PROGRESS) {
                StatusChip("Tap to Continue Follow-Up", Icons.Rounded.Edit, StatusTone.INFO_TEAL)
              }
            }
          }
        }
      }
    }
  }
}
