package com.example.navigation

import com.mchat.core.design.components.PrimaryTab
import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose routes for every clinician workflow screen.
 */
@Serializable
sealed interface AppDestination {
  @Serializable
  data object Home : AppDestination

  @Serializable
  data object Statistics : AppDestination

  @Serializable
  data class NewSurvey(val preselectedPatientId: String? = null) : AppDestination

  @Serializable
  data object Settings : AppDestination

  @Serializable
  data object PatientList : AppDestination

  @Serializable
  data object PatientRegister : AppDestination

  @Serializable
  data class PatientEdit(val patientId: String) : AppDestination

  @Serializable
  data class PatientProfile(val patientId: String) : AppDestination

  @Serializable
  data class Questionnaire(val screeningId: String) : AppDestination

  @Serializable
  data class FollowUp(val screeningId: String) : AppDestination

  @Serializable
  data class ScreeningResult(val screeningId: String) : AppDestination

  @Serializable
  data class ReportViewer(val screeningId: String) : AppDestination

  @Serializable
  data class FilteredScreenings(val filterKey: String) : AppDestination

  @Serializable
  data object AuditLog : AppDestination
}

fun PrimaryTab.toDestination(): AppDestination = when (this) {
  PrimaryTab.HOME -> AppDestination.Home
  PrimaryTab.STATISTICS -> AppDestination.Statistics
  PrimaryTab.NEW_SURVEY -> AppDestination.NewSurvey()
  PrimaryTab.SETTINGS -> AppDestination.Settings
}
