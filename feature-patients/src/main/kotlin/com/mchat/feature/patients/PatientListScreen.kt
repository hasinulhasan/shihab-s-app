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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mchat.core.common.age.AgeCalculator
import com.mchat.core.design.components.EmptyStateCard
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.StatusChip
import com.mchat.core.design.components.StatusTone
import com.mchat.data.local.entity.PatientEntity
import com.mchat.data.local.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

enum class PatientAgeFilter { ALL, IN_RANGE, OUT_OF_RANGE }

data class PatientListItemUi(
  val entity: PatientEntity,
  val ageLabel: String,
  val totalMonths: Int,
  val inIntendedRange: Boolean,
)

data class PatientListUiState(
  val query: String = "",
  val showArchived: Boolean = false,
  val ageFilter: PatientAgeFilter = PatientAgeFilter.ALL,
  val items: List<PatientListItemUi> = emptyList(),
)

@HiltViewModel
class PatientListViewModel @Inject constructor(
  patientRepository: PatientRepository,
) : ViewModel() {
  private val query = MutableStateFlow("")
  private val showArchived = MutableStateFlow(false)
  private val ageFilter = MutableStateFlow(PatientAgeFilter.ALL)

  val uiState: StateFlow<PatientListUiState> = combine(
    query,
    showArchived,
    ageFilter,
    patientRepository.observeActivePatients(),
    patientRepository.observeArchivedPatients(),
  ) { q, archived, filter, activeList, archivedList ->
    val source = if (archived) archivedList else activeList
    val today = LocalDate.now()
    val mapped = source.mapNotNull { entity ->
      val age = runCatching { AgeCalculator.calculate(LocalDate.parse(entity.dateOfBirth), today) }.getOrNull()
        ?: return@mapNotNull null
      PatientListItemUi(
        entity = entity,
        ageLabel = "${age.years}y ${age.remainingMonths}m (${age.totalMonths}m)",
        totalMonths = age.totalMonths,
        inIntendedRange = age.isWithinIntendedRange,
      )
    }.filter { item ->
      val matchesQuery = q.isBlank() ||
        item.entity.fullName.contains(q, ignoreCase = true) ||
        item.entity.patientCode.contains(q, ignoreCase = true)
      val matchesAge = when (filter) {
        PatientAgeFilter.ALL -> true
        PatientAgeFilter.IN_RANGE -> item.inIntendedRange
        PatientAgeFilter.OUT_OF_RANGE -> !item.inIntendedRange
      }
      matchesQuery && matchesAge
    }

    PatientListUiState(
      query = q,
      showArchived = archived,
      ageFilter = filter,
      items = mapped,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientListUiState())

  fun onQueryChange(value: String) {
    query.value = value
  }

  fun onToggleArchived(archived: Boolean) {
    showArchived.value = archived
  }

  fun onAgeFilterChange(filter: PatientAgeFilter) {
    ageFilter.value = filter
  }
}

@Composable
fun PatientListRoute(
  onBack: () -> Unit,
  onRegisterPatient: () -> Unit,
  onOpenPatient: (String) -> Unit,
  viewModel: PatientListViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    floatingActionButton = {
      FloatingActionButton(
        onClick = onRegisterPatient,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
          .padding(bottom = 84.dp)
          .testTag("fab_register_patient"),
      ) {
        Icon(Icons.Rounded.PersonAdd, contentDescription = "Register new patient")
      }
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .windowInsetsPadding(WindowInsets.safeDrawing),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("patient_list_back"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
          Column {
            Text(
              text = "Patients",
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "${state.items.size} encrypted records",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item {
        OutlinedTextField(
          value = state.query,
          onValueChange = viewModel::onQueryChange,
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          placeholder = { Text("Search by child name or Patient ID") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("patient_search_input"),
        )
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = !state.showArchived,
            onClick = { viewModel.onToggleArchived(false) },
            label = { Text("Active") },
          )
          FilterChip(
            selected = state.showArchived,
            onClick = { viewModel.onToggleArchived(true) },
            label = { Text("Archived") },
          )
          FilterChip(
            selected = state.ageFilter == PatientAgeFilter.IN_RANGE,
            onClick = {
              viewModel.onAgeFilterChange(
                if (state.ageFilter == PatientAgeFilter.IN_RANGE) PatientAgeFilter.ALL else PatientAgeFilter.IN_RANGE,
              )
            },
            label = { Text("16–30m") },
          )
          FilterChip(
            selected = state.ageFilter == PatientAgeFilter.OUT_OF_RANGE,
            onClick = {
              viewModel.onAgeFilterChange(
                if (state.ageFilter == PatientAgeFilter.OUT_OF_RANGE) PatientAgeFilter.ALL else PatientAgeFilter.OUT_OF_RANGE,
              )
            },
            label = { Text("Out of range") },
          )
        }
      }

      if (state.items.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.Rounded.PersonAdd,
            title = if (state.showArchived) "No Archived Patients" else "No Patients Found",
            body = if (state.showArchived) {
              "Soft-archived patient records appear here and can be restored anytime."
            } else {
              "Tap the + button below to capture caregiver consent and register a child."
            },
          )
        }
      } else {
        items(state.items, key = { it.entity.id }) { item ->
          GlassCard(
            onClick = { onOpenPatient(item.entity.id) },
            modifier = Modifier.testTag("patient_card_${item.entity.patientCode}"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.entity.fullName,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                  text = "${item.entity.patientCode} • DOB ${item.entity.dateOfBirth} • ${item.ageLabel}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (item.entity.isArchived) {
                StatusChip("Archived", Icons.Rounded.Archive, StatusTone.NEUTRAL)
              } else if (item.inIntendedRange) {
                StatusChip("Age 16–30m", Icons.Rounded.CheckCircle, StatusTone.LOW_GREEN)
              } else {
                StatusChip("Outside 16–30m", Icons.Rounded.WarningAmber, StatusTone.MODERATE_AMBER)
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}
