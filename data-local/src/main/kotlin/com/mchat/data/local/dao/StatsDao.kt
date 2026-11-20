package com.mchat.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.InitialCategory
import kotlinx.coroutines.flow.Flow

data class CategoryCountRow(
  @ColumnInfo(name = "initial_category") val category: InitialCategory,
  @ColumnInfo(name = "total") val count: Int,
)

data class OutcomeCountRow(
  @ColumnInfo(name = "final_outcome") val outcome: FinalOutcome,
  @ColumnInfo(name = "total") val count: Int,
)

data class MonthlyCountRow(
  @ColumnInfo(name = "month_key") val monthKey: String,
  @ColumnInfo(name = "total") val total: Int,
  @ColumnInfo(name = "positive_count") val positiveCount: Int,
)

data class AgeBinCountRow(
  @ColumnInfo(name = "child_age_months") val ageMonths: Int,
  @ColumnInfo(name = "total") val count: Int,
)

data class PerQuestionStatRow(
  @ColumnInfo(name = "item_number") val itemNumber: Int,
  @ColumnInfo(name = "total_answered") val totalAnswered: Int,
  @ColumnInfo(name = "yes_count") val yesCount: Int,
  @ColumnInfo(name = "no_count") val noCount: Int,
  @ColumnInfo(name = "at_risk_count") val atRiskCount: Int,
)

data class FollowUpAggregateRow(
  @ColumnInfo(name = "total_required") val totalRequired: Int,
  @ColumnInfo(name = "completed_negative") val completedNegative: Int,
  @ColumnInfo(name = "completed_positive") val completedPositive: Int,
  @ColumnInfo(name = "pending_count") val pendingCount: Int,
)

@Dao
interface StatsDao {
  @Query(
    """
    SELECT initial_category, COUNT(*) AS total
    FROM screenings
    WHERE is_archived = 0 AND initial_category IS NOT NULL
      AND screening_date >= :startDateIso AND screening_date <= :endDateIso
    GROUP BY initial_category
    """,
  )
  fun observeInitialCategoryCounts(startDateIso: String, endDateIso: String): Flow<List<CategoryCountRow>>

  @Query(
    """
    SELECT final_outcome, COUNT(*) AS total
    FROM screenings
    WHERE is_archived = 0
      AND screening_date >= :startDateIso AND screening_date <= :endDateIso
    GROUP BY final_outcome
    """,
  )
  fun observeFinalOutcomeCounts(startDateIso: String, endDateIso: String): Flow<List<OutcomeCountRow>>

  @Query(
    """
    SELECT substr(screening_date, 1, 7) AS month_key,
           COUNT(*) AS total,
           SUM(CASE WHEN final_outcome IN ('POSITIVE_AFTER_FOLLOWUP', 'POSITIVE_HIGH_INITIAL') THEN 1 ELSE 0 END) AS positive_count
    FROM screenings
    WHERE is_archived = 0
      AND screening_date >= :startDateIso AND screening_date <= :endDateIso
    GROUP BY month_key
    ORDER BY month_key ASC
    """,
  )
  fun observeMonthlyTrend(startDateIso: String, endDateIso: String): Flow<List<MonthlyCountRow>>

  @Query(
    """
    SELECT child_age_months, COUNT(*) AS total
    FROM screenings
    WHERE is_archived = 0
      AND screening_date >= :startDateIso AND screening_date <= :endDateIso
    GROUP BY child_age_months
    ORDER BY child_age_months ASC
    """,
  )
  fun observeAgeDistribution(startDateIso: String, endDateIso: String): Flow<List<AgeBinCountRow>>

  @Query(
    """
    SELECT qr.item_number,
           COUNT(*) AS total_answered,
           SUM(CASE WHEN qr.response_value = 'YES' THEN 1 ELSE 0 END) AS yes_count,
           SUM(CASE WHEN qr.response_value = 'NO' THEN 1 ELSE 0 END) AS no_count,
           SUM(CASE WHEN qr.is_at_risk = 1 THEN 1 ELSE 0 END) AS at_risk_count
    FROM questionnaire_responses qr
    INNER JOIN screenings s
      ON s.id = qr.screening_id AND s.current_revision_number = qr.revision_number
    WHERE s.is_archived = 0
      AND s.screening_date >= :startDateIso AND s.screening_date <= :endDateIso
    GROUP BY qr.item_number
    ORDER BY qr.item_number ASC
    """,
  )
  fun observePerQuestionStats(startDateIso: String, endDateIso: String): Flow<List<PerQuestionStatRow>>

  @Query(
    """
    SELECT
      SUM(CASE WHEN initial_category = 'MODERATE' THEN 1 ELSE 0 END) AS total_required,
      SUM(CASE WHEN final_outcome = 'NEGATIVE_AFTER_FOLLOWUP' THEN 1 ELSE 0 END) AS completed_negative,
      SUM(CASE WHEN final_outcome = 'POSITIVE_AFTER_FOLLOWUP' THEN 1 ELSE 0 END) AS completed_positive,
      SUM(CASE WHEN final_outcome = 'FOLLOWUP_PENDING' THEN 1 ELSE 0 END) AS pending_count
    FROM screenings
    WHERE is_archived = 0
      AND screening_date >= :startDateIso AND screening_date <= :endDateIso
    """,
  )
  fun observeFollowUpAggregates(startDateIso: String, endDateIso: String): Flow<FollowUpAggregateRow?>
}
