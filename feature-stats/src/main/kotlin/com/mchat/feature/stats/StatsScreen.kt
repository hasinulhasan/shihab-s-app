package com.mchat.feature.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mchat.core.design.R
import com.mchat.core.design.components.EmptyStateCard
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.PrimaryButton
import com.mchat.core.design.components.SecondaryGlassButton
import com.mchat.core.design.components.SectionHeader
import com.mchat.core.design.components.StatusChip
import com.mchat.core.design.components.StatusTone
import com.mchat.core.design.theme.MChatTheme
import com.mchat.data.local.repository.ClinicalStatsSnapshot
import com.mchat.data.local.repository.StatsRepository
import com.mchat.data.sync.DriveSyncCoordinator
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.InitialCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class StatsDatePreset(val label: String) {
  TODAY("Today"),
  DAYS_7("7d"),
  DAYS_30("30d"),
  DAYS_90("90d"),
  THIS_YEAR("This Year"),
  CUSTOM("Custom"),
}

data class StatsUiState(
  val preset: StatsDatePreset = StatsDatePreset.THIS_YEAR,
  val startDateIso: String = LocalDate.now().withDayOfYear(1).toString(),
  val endDateIso: String = LocalDate.now().toString(),
  val snapshot: ClinicalStatsSnapshot? = null,
  val exportStatusMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
  private val statsRepository: StatsRepository,
  private val driveSyncCoordinator: DriveSyncCoordinator,
) : ViewModel() {

  private val preset = MutableStateFlow(StatsDatePreset.THIS_YEAR)
  private val customStart = MutableStateFlow(LocalDate.now().minusDays(30).toString())
  private val customEnd = MutableStateFlow(LocalDate.now().toString())
  private val exportMessage = MutableStateFlow<String?>(null)

  private val dateRange = combine(preset, customStart, customEnd) { p, cs, ce ->
    val today = LocalDate.now()
    when (p) {
      StatsDatePreset.TODAY -> Triple(p, today.toString(), today.toString())
      StatsDatePreset.DAYS_7 -> Triple(p, today.minusDays(6).toString(), today.toString())
      StatsDatePreset.DAYS_30 -> Triple(p, today.minusDays(29).toString(), today.toString())
      StatsDatePreset.DAYS_90 -> Triple(p, today.minusDays(89).toString(), today.toString())
      StatsDatePreset.THIS_YEAR -> Triple(p, today.withDayOfYear(1).toString(), today.toString())
      StatsDatePreset.CUSTOM -> Triple(p, cs, ce)
    }
  }

  val uiState: StateFlow<StatsUiState> = combine(
    dateRange,
    dateRange.flatMapLatest { (_, start, end) -> statsRepository.observeStats(start, end) },
    exportMessage,
  ) { (p, start, end), snapshot, msg ->
    StatsUiState(
      preset = p,
      startDateIso = start,
      endDateIso = end,
      snapshot = snapshot,
      exportStatusMessage = msg,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

  fun selectPreset(p: StatsDatePreset) {
    preset.value = p
  }

  fun onCustomStartChange(iso: String) {
    customStart.value = iso
  }

  fun onCustomEndChange(iso: String) {
    customEnd.value = iso
  }

  suspend fun generateDeIdentifiedCsv(): String {
    val state = uiState.value
    return statsRepository.buildDeIdentifiedCsv(state.startDateIso, state.endDateIso)
  }

  fun markCsvExportedLocally() {
    exportMessage.value = "De-identified CSV exported (zero patient names/contacts) and logged in Audit Log."
  }

  fun uploadEncryptedCsvToDrive() {
    viewModelScope.launch {
      val state = uiState.value
      val csv = statsRepository.buildDeIdentifiedCsv(state.startDateIso, state.endDateIso)
      val fileName = "mchat_stats_${state.startDateIso}_to_${state.endDateIso}.csv"
      val queued = driveSyncCoordinator.enqueueEncryptedArtifactUpload(
        entityType = "STATS_CSV",
        baseFileName = fileName,
        plainBytes = csv.toByteArray(Charsets.UTF_8),
      )
      exportMessage.value = if (queued) {
        "De-identified CSV encrypted locally and queued for Google Drive /statistics."
      } else {
        "Unlock your Recovery Passphrase in Settings before uploading encrypted statistics."
      }
    }
  }
}

@Composable
fun StatsRoute(
  onOpenFilteredList: (String) -> Unit = {},
  viewModel: StatsViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var pendingCsvContent by remember { mutableStateOf<String?>(null) }

  val saveCsvLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("text/csv"),
  ) { uri ->
    val csv = pendingCsvContent ?: return@rememberLauncherForActivityResult
    if (uri != null) {
      context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
      viewModel.markCsvExportedLocally()
    }
  }

  val snapshot = state.snapshot
  val totalScreenings = snapshot?.finalOutcomeCounts?.sumOf { it.count } ?: 0
  val extended = MChatTheme.extendedColors

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
          text = stringResource(R.string.stats_title),
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          text = "${state.startDateIso} → ${state.endDateIso} • $totalScreenings real saved screenings",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Date filter chips
    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(StatsDatePreset.entries) { p ->
          FilterChip(
            selected = state.preset == p,
            onClick = { viewModel.selectPreset(p) },
            label = { Text(p.label) },
            modifier = Modifier.testTag("stats_preset_${p.name.lowercase()}"),
          )
        }
      }
    }

    if (state.preset == StatsDatePreset.CUSTOM) {
      item {
        GlassCard {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = state.startDateIso,
              onValueChange = viewModel::onCustomStartChange,
              label = { Text("Start (YYYY-MM-DD)") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = state.endDateIso,
              onValueChange = viewModel::onCustomEndChange,
              label = { Text("End (YYYY-MM-DD)") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }

    // Explicit Outcome Definitions (Clinical Rule Requirement)
    item {
      GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StatusChip(
            label = stringResource(R.string.stats_definition_title),
            icon = Icons.Rounded.Info,
            tone = StatusTone.INFO_TEAL,
          )
        }
        Text(
          text = stringResource(R.string.stats_definition_body),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (snapshot == null || totalScreenings == 0) {
      item {
        EmptyStateCard(
          icon = Icons.Rounded.BarChart,
          title = stringResource(R.string.stats_empty_title),
          body = stringResource(R.string.stats_empty_body),
        )
      }
    } else {
      // 1. Initial Category Distribution Donut Chart (tappable)
      item {
        val low = snapshot.initialCategoryCounts.firstOrNull { it.category == InitialCategory.LOW }?.count ?: 0
        val mod = snapshot.initialCategoryCounts.firstOrNull { it.category == InitialCategory.MODERATE }?.count ?: 0
        val high = snapshot.initialCategoryCounts.firstOrNull { it.category == InitialCategory.HIGH }?.count ?: 0
        val catTotal = (low + mod + high).coerceAtLeast(1)

        GlassCard {
          SectionHeader(
            title = "Initial Category Distribution",
            subtitle = "Tap any category row to open matching records",
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
          ) {
            Canvas(modifier = Modifier.size(116.dp)) {
              val stroke = Stroke(width = 24f, cap = StrokeCap.Butt)
              val inset = 16f
              val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
              var startAngle = -90f
              val segments = listOf(
                low to extended.statusGreenContent,
                mod to extended.statusAmberContent,
                high to extended.statusCoralContent,
              )
              segments.forEach { (count, color) ->
                if (count > 0) {
                  val sweep = (count.toFloat() / catTotal) * 360f
                  drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                  )
                  startAngle += sweep
                }
              }
            }
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CategoryLegendRow("Low (0–2)", low, catTotal, extended.statusGreenContent) {
                onOpenFilteredList("CATEGORY:${InitialCategory.LOW.name}")
              }
              CategoryLegendRow("Moderate (3–7)", mod, catTotal, extended.statusAmberContent) {
                onOpenFilteredList("CATEGORY:${InitialCategory.MODERATE.name}")
              }
              CategoryLegendRow("High (8–20)", high, catTotal, extended.statusCoralContent) {
                onOpenFilteredList("CATEGORY:${InitialCategory.HIGH.name}")
              }
            }
          }
        }
      }

      // 2. Final Outcomes Breakdown (tappable)
      item {
        GlassCard {
          SectionHeader(
            title = "Final Outcomes (Aggregate %)",
            subtitle = "Tap an outcome to view filtered records",
          )
          FinalOutcome.entries.forEach { outcome ->
            val count = snapshot.finalOutcomeCounts.firstOrNull { it.outcome == outcome }?.count ?: 0
            val pct = (count * 100f / totalScreenings.coerceAtLeast(1))
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenFilteredList("OUTCOME:${outcome.name}") }
                .padding(vertical = 4.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(outcome.name.replace('_', ' '), style = MaterialTheme.typography.labelMedium)
                Text("$count (${"%.1f".format(pct)}%)", style = MaterialTheme.typography.labelLarge)
              }
              LinearProgressIndicator(
                progress = { (count.toFloat() / totalScreenings.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }

      // 3. Follow-Up Conversion Stats
      item {
        val fu = snapshot.followUpAggregate
        GlassCard {
          SectionHeader(
            title = "Stage 2 Follow-Up Summary",
            subtitle = "Moderate-risk cases routed through Follow-Up",
          )
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Required: ${fu.totalRequired}", style = MaterialTheme.typography.labelLarge)
            Text("Negative: ${fu.completedNegative}", style = MaterialTheme.typography.labelLarge, color = extended.statusGreenContent)
            Text("Positive: ${fu.completedPositive}", style = MaterialTheme.typography.labelLarge, color = extended.statusCoralContent)
            Text("Pending: ${fu.pendingCount}", style = MaterialTheme.typography.labelLarge, color = extended.statusAmberContent)
          }
        }
      }

      // 4. Monthly Trend Line Chart
      if (snapshot.monthlyTrend.isNotEmpty()) {
        item {
          val lineColor = MaterialTheme.colorScheme.primary
          val positiveColor = extended.statusCoralContent
          GlassCard {
            SectionHeader(
              title = "Monthly Screening Trend",
              subtitle = "Total screenings (sage) vs. Positive screens (coral)",
            )
            Canvas(
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(top = 8.dp),
            ) {
              val data = snapshot.monthlyTrend
              val maxVal = data.maxOf { it.total }.coerceAtLeast(1).toFloat()
              val stepX = if (data.size > 1) size.width / (data.size - 1) else size.width / 2f

              val totalPath = Path()
              val posPath = Path()
              data.forEachIndexed { idx, row ->
                val x = if (data.size == 1) size.width / 2f else idx * stepX
                val yTotal = size.height - (row.total / maxVal) * (size.height - 20f) - 10f
                val yPos = size.height - (row.positiveCount / maxVal) * (size.height - 20f) - 10f
                if (idx == 0) {
                  totalPath.moveTo(x, yTotal)
                  posPath.moveTo(x, yPos)
                } else {
                  totalPath.lineTo(x, yTotal)
                  posPath.lineTo(x, yPos)
                }
                drawCircle(color = lineColor, radius = 6f, center = Offset(x, yTotal))
                drawCircle(color = positiveColor, radius = 5f, center = Offset(x, yPos))
              }
              if (data.size > 1) {
                drawPath(totalPath, color = lineColor, style = Stroke(width = 4f))
                drawPath(posPath, color = positiveColor, style = Stroke(width = 4f))
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              snapshot.monthlyTrend.takeLast(4).forEach { row ->
                Text(
                  text = "${row.monthKey}: ${row.total} (${row.positiveCount}+)",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      // 5. Age Distribution in Monthly Bins (tappable)
      if (snapshot.ageDistribution.isNotEmpty()) {
        item {
          val maxBin = snapshot.ageDistribution.maxOf { it.count }.coerceAtLeast(1)
          GlassCard {
            SectionHeader(
              title = "Age Distribution (Monthly Bins)",
              subtitle = "Tap any age bin to view matching screenings",
            )
            snapshot.ageDistribution.forEach { bin ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onOpenFilteredList("AGE:${bin.ageMonths}") }
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text("${bin.ageMonths}m", style = MaterialTheme.typography.labelLarge, modifier = Modifier.size(width = 42.dp, height = 20.dp))
                LinearProgressIndicator(
                  progress = { bin.count.toFloat() / maxBin },
                  modifier = Modifier.weight(1f),
                )
                Text("${bin.count}", style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }
      }

      // 6. Per-Question Yes / No / At-Risk Counts & Percentages
      if (snapshot.perQuestionStats.isNotEmpty()) {
        item {
          GlassCard {
            SectionHeader(
              title = "Per-Question Breakdown (Items 01–20)",
              subtitle = "Aggregate YES / NO / At-Risk counts & %",
            )
            snapshot.perQuestionStats.forEach { q ->
              val riskPct = (q.atRiskCount * 100f / q.totalAnswered.coerceAtLeast(1))
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Item ${"%02d".format(q.itemNumber)}", style = MaterialTheme.typography.labelLarge)
                Text(
                  text = "Y:${q.yesCount}  N:${q.noCount}  At-Risk:${q.atRiskCount} (${"%.0f".format(riskPct)}%)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      // De-Identified CSV Export
      item {
        GlassCard {
          SectionHeader(
            title = "De-Identified CSV Export",
            subtitle = "Contains ZERO patient names, contacts, or free-text clinical notes",
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            PrimaryButton(
              text = "Export CSV",
              icon = Icons.Rounded.Download,
              onClick = {
                scope.launch {
                  pendingCsvContent = viewModel.generateDeIdentifiedCsv()
                  saveCsvLauncher.launch("mchat_stats_${state.startDateIso}_to_${state.endDateIso}.csv")
                }
              },
              modifier = Modifier.weight(1f),
              testTag = "stats_export_csv_button",
            )
            SecondaryGlassButton(
              text = "Encrypt & Upload",
              icon = Icons.Rounded.CloudUpload,
              onClick = viewModel::uploadEncryptedCsvToDrive,
              modifier = Modifier.weight(1f),
              testTag = "stats_upload_csv_button",
            )
          }
          state.exportStatusMessage?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }

    item { Spacer(modifier = Modifier.height(12.dp)) }
  }
}

@Composable
private fun CategoryLegendRow(
  label: String,
  count: Int,
  total: Int,
  color: Color,
  onClick: () -> Unit,
) {
  val pct = (count * 100f / total.coerceAtLeast(1))
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
    Text("$count (${"%.0f".format(pct)}%)", style = MaterialTheme.typography.labelMedium)
  }
}
