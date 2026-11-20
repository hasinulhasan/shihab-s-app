package com.mchat.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mchat.core.design.components.EmptyStateCard
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.OutcomeStatusChip
import com.mchat.data.local.dao.ScreeningWithPatientSummary
import com.mchat.data.local.preferences.UiPreferencesRepository
import com.mchat.data.local.repository.ScreeningRepository
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.InitialCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class FilteredScreeningsUiState(
  val title: String = "Screenings",
  val items: List<ScreeningWithPatientSummary> = emptyList(),
  val hideNames: Boolean = false,
)

@HiltViewModel
class FilteredScreeningsViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  screeningRepository: ScreeningRepository,
  preferencesRepository: UiPreferencesRepository,
) : ViewModel() {

  private val filterKey: String = savedStateHandle["filterKey"] ?: HomeFilterKeys.ALL

  val uiState: StateFlow<FilteredScreeningsUiState> = combine(
    screeningRepository.observeActiveScreeningsWithPatient(),
    preferencesRepository.preferences,
  ) { all, prefs ->
    val currentMonth = LocalDate.now().toString().substring(0, 7)
    val filtered = when {
      filterKey == HomeFilterKeys.ALL -> all
      filterKey == HomeFilterKeys.NEGATIVE -> all.filter {
        it.finalOutcome == FinalOutcome.NEGATIVE_LOW_INITIAL ||
          it.finalOutcome == FinalOutcome.NEGATIVE_AFTER_FOLLOWUP
      }
      filterKey == HomeFilterKeys.POSITIVE -> all.filter {
        it.finalOutcome == FinalOutcome.POSITIVE_AFTER_FOLLOWUP ||
          it.finalOutcome == FinalOutcome.POSITIVE_HIGH_INITIAL
      }
      filterKey == HomeFilterKeys.FOLLOWUP_PENDING -> all.filter {
        it.finalOutcome == FinalOutcome.FOLLOWUP_PENDING
      }
      filterKey == HomeFilterKeys.THIS_MONTH -> all.filter {
        it.screeningDate.startsWith(currentMonth)
      }
      filterKey.startsWith("CATEGORY:") -> {
        val cat = runCatching { InitialCategory.valueOf(filterKey.removePrefix("CATEGORY:")) }.getOrNull()
        all.filter { it.initialCategory == cat }
      }
      filterKey.startsWith("OUTCOME:") -> {
        val out = runCatching { FinalOutcome.valueOf(filterKey.removePrefix("OUTCOME:")) }.getOrNull()
        all.filter { it.finalOutcome == out }
      }
      filterKey.startsWith("AGE:") -> {
        val m = filterKey.removePrefix("AGE:").toIntOrNull()
        all.filter { it.childAgeMonths == m }
      }
      else -> all
    }

    val title = when {
      filterKey == HomeFilterKeys.NEGATIVE -> "Negative Screens"
      filterKey == HomeFilterKeys.POSITIVE -> "Positive Screens"
      filterKey == HomeFilterKeys.FOLLOWUP_PENDING -> "Follow-Up Pending"
      filterKey == HomeFilterKeys.THIS_MONTH -> "This Month's Screenings"
      filterKey.startsWith("CATEGORY:") -> "Category: ${filterKey.removePrefix("CATEGORY:")}"
      filterKey.startsWith("OUTCOME:") -> "Outcome: ${filterKey.removePrefix("OUTCOME:")}"
      filterKey.startsWith("AGE:") -> "Age ${filterKey.removePrefix("AGE:")} Months"
      else -> "All Screenings"
    }

    FilteredScreeningsUiState(
      title = title,
      items = filtered,
      hideNames = prefs.hidePatientNamesOnHome,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilteredScreeningsUiState())
}

@Composable
fun FilteredScreeningsRoute(
  onBack: () -> Unit,
  onOpenScreening: (ScreeningWithPatientSummary) -> Unit,
  viewModel: FilteredScreeningsViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 48.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("filtered_back_button")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column {
          Text(state.title, style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "${state.items.size} matching records",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    if (state.items.isEmpty()) {
      item {
        EmptyStateCard(
          icon = Icons.AutoMirrored.Rounded.Assignment,
          title = "No Matching Screenings",
          body = "There are currently no saved screenings in this filter.",
        )
      }
    } else {
      items(state.items, key = { it.id }) { row ->
        GlassCard(
          onClick = { onOpenScreening(row) },
          contentPadding = PaddingValues(16.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (state.hideNames) "Patient ${row.patientCode}" else row.patientName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              )
              Text(
                text = "${row.patientCode} • ${row.screeningDate} • ${row.childAgeMonths}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (row.initialScore != null) {
              Text(
                text = "${row.initialScore}/20",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              )
            }
          }
          OutcomeStatusChip(outcome = row.finalOutcome)
        }
      }
    }
  }
}
