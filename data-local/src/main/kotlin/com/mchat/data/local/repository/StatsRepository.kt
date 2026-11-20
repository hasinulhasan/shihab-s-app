package com.mchat.data.local.repository

import com.mchat.data.local.dao.AgeBinCountRow
import com.mchat.data.local.dao.CategoryCountRow
import com.mchat.data.local.dao.FollowUpAggregateRow
import com.mchat.data.local.dao.MonthlyCountRow
import com.mchat.data.local.dao.OutcomeCountRow
import com.mchat.data.local.dao.PerQuestionStatRow
import com.mchat.data.local.dao.ScreeningDao
import com.mchat.data.local.dao.StatsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class ClinicalStatsSnapshot(
  val startDateIso: String,
  val endDateIso: String,
  val initialCategoryCounts: List<CategoryCountRow>,
  val finalOutcomeCounts: List<OutcomeCountRow>,
  val monthlyTrend: List<MonthlyCountRow>,
  val ageDistribution: List<AgeBinCountRow>,
  val perQuestionStats: List<PerQuestionStatRow>,
  val followUpAggregate: FollowUpAggregateRow,
)

@Singleton
class StatsRepository @Inject constructor(
  private val statsDao: StatsDao,
  private val screeningDao: ScreeningDao,
  private val auditRepository: AuditRepository,
) {
  fun observeStats(startDateIso: String, endDateIso: String): Flow<ClinicalStatsSnapshot> {
    val part1 = combine(
      statsDao.observeInitialCategoryCounts(startDateIso, endDateIso),
      statsDao.observeFinalOutcomeCounts(startDateIso, endDateIso),
      statsDao.observeMonthlyTrend(startDateIso, endDateIso),
    ) { categories, outcomes, monthly ->
      Triple(categories, outcomes, monthly)
    }

    val part2 = combine(
      statsDao.observeAgeDistribution(startDateIso, endDateIso),
      statsDao.observePerQuestionStats(startDateIso, endDateIso),
      statsDao.observeFollowUpAggregates(startDateIso, endDateIso),
    ) { ageBins, perQuestion, followUp ->
      Triple(
        ageBins,
        perQuestion,
        followUp ?: FollowUpAggregateRow(0, 0, 0, 0),
      )
    }

    return combine(part1, part2) { p1, p2 ->
      ClinicalStatsSnapshot(
        startDateIso = startDateIso,
        endDateIso = endDateIso,
        initialCategoryCounts = p1.first,
        finalOutcomeCounts = p1.second,
        monthlyTrend = p1.third,
        ageDistribution = p2.first,
        perQuestionStats = p2.second,
        followUpAggregate = p2.third,
      )
    }
  }

  /**
   * Builds a strictly de-identified CSV export (zero patient names, zero caregiver names/phones/emails,
   * zero clinical free-text notes). Records an AUDIT EXPORT event.
   */
  suspend fun buildDeIdentifiedCsv(startDateIso: String, endDateIso: String): String {
    val allScreenings = screeningDao.getAllScreeningsSnapshot()
      .filter { !it.isArchived && it.screeningDate >= startDateIso && it.screeningDate <= endDateIso }
      .sortedBy { it.screeningDate }

    val sb = StringBuilder()
    sb.appendLine(
      "record_hash_id,screening_date,child_age_months,language_used,instrument_version,status,initial_score,initial_category,followup_score,final_outcome,revision_number",
    )
    allScreenings.forEach { s ->
      val anonId = s.id.take(8)
      sb.appendLine(
        listOf(
          anonId,
          s.screeningDate,
          s.childAgeMonths.toString(),
          s.languageUsed,
          s.instrumentVersion,
          s.status.name,
          s.initialScore?.toString() ?: "",
          s.initialCategory?.name ?: "",
          s.followupScore?.toString() ?: "",
          s.finalOutcome.name,
          s.currentRevisionNumber.toString(),
        ).joinToString(","),
      )
    }

    auditRepository.recordEvent(
      eventType = AuditEventType.EXPORT,
      entityType = "STATISTICS_CSV",
      entityId = null,
      metadataJson = "{\"deIdentified\":true,\"rows\":${allScreenings.size},\"start\":\"$startDateIso\",\"end\":\"$endDateIso\"}",
    )
    return sb.toString()
  }
}
