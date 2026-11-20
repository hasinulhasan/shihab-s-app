package com.mchat.feature.settings

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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.StatusChip
import com.mchat.core.design.components.StatusTone
import com.mchat.core.security.audit.AuditChainVerificationResult
import com.mchat.data.local.entity.AuditLogEntity
import com.mchat.data.local.repository.AuditRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AuditLogUiState(
  val entries: List<AuditLogEntity> = emptyList(),
  val verification: AuditChainVerificationResult? = null,
)

@HiltViewModel
class AuditLogViewModel @Inject constructor(
  private val auditRepository: AuditRepository,
) : ViewModel() {

  private val verification = MutableStateFlow<AuditChainVerificationResult?>(null)

  val uiState: StateFlow<AuditLogUiState> = combine(
    auditRepository.observeRecentLogs(150),
    verification,
  ) { logs, result ->
    AuditLogUiState(entries = logs, verification = result)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuditLogUiState())

  init {
    reverify()
  }

  fun reverify() {
    viewModelScope.launch {
      verification.value = auditRepository.verifyIntegrityChain()
    }
  }
}

@Composable
fun AuditLogRoute(
  onBack: () -> Unit,
  viewModel: AuditLogViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 48.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("audit_back_button")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column {
          Text("Tamper-Evident Audit Log", style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "Zero patient names or PHI stored in audit metadata",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    item {
      GlassCard {
        val v = state.verification
        when {
          v == null -> Text("Verifying SHA-256 chain…", style = MaterialTheme.typography.bodySmall)
          v.isValid -> StatusChip(
            label = "SHA-256 Hash Chain Intact (${v.totalEntriesChecked} entries verified)",
            icon = Icons.Rounded.VerifiedUser,
            tone = StatusTone.LOW_GREEN,
            modifier = Modifier.testTag("audit_chain_valid_chip"),
          )
          else -> StatusChip(
            label = "TAMPER ALERT at Seq #${v.brokenAtSequence ?: "?"}: ${v.reason ?: "Hash mismatch"}",
            icon = Icons.Rounded.ErrorOutline,
            tone = StatusTone.HIGH_CORAL,
          )
        }
      }
    }

    items(state.entries, key = { it.id }) { entry ->
      val ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(entry.timestampUtc))
      GlassCard(contentPadding = PaddingValues(14.dp), elevation = 3.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("#${entry.sequenceNumber} • ${entry.eventType} (${entry.entityType})", style = MaterialTheme.typography.labelLarge)
          Text(ts, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
          text = "Hash: ${entry.entryHash.take(18)}…  Prev: ${entry.previousHash.take(12)}…",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
