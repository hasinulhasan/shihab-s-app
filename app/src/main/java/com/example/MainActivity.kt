package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.navigation.AppDestination
import com.example.navigation.toDestination
import com.mchat.core.design.R as DesignR
import com.mchat.core.design.components.AnimatedAmbientBackground
import com.mchat.core.design.components.AppLockScreen
import com.mchat.core.design.components.GlassBottomBar
import com.mchat.core.design.components.PrimaryTab
import com.mchat.core.design.theme.MChatTheme
import com.mchat.core.security.applock.AppLockStatus
import com.mchat.core.security.applock.BiometricAuthHelper
import com.mchat.core.security.screen.SecureScreenEffect
import com.mchat.core.security.screen.shouldEnforceFlagSecure
import com.mchat.domain.scoring.model.ScreeningStatus
import com.mchat.feature.followup.FollowUpRoute
import com.mchat.feature.followup.ScreeningResultRoute
import com.mchat.feature.home.FilteredScreeningsRoute
import com.mchat.feature.home.HomeRoute
import com.mchat.feature.patients.PatientFormRoute
import com.mchat.feature.patients.PatientListRoute
import com.mchat.feature.patients.PatientProfileRoute
import com.mchat.feature.reports.ReportViewerRoute
import com.mchat.feature.screening.NewSurveyRoute
import com.mchat.feature.screening.QuestionnaireRoute
import com.mchat.feature.settings.AuditLogRoute
import com.mchat.feature.settings.SettingsRoute
import com.mchat.feature.stats.StatsRoute
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (shouldEnforceFlagSecure(BuildConfig.DEBUG)) {
      window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    enableEdgeToEdge()

    setContent {
      MChatClinicianApp(activity = this)
    }
  }
}

@Composable
private fun MChatClinicianApp(
  activity: AppCompatActivity,
  viewModel: AppShellViewModel = hiltViewModel(),
) {
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val lockStatus by viewModel.lockStatus.collectAsStateWithLifecycle()
  val pinOverlay by viewModel.pinOverlay.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current
  val context = LocalContext.current

  SecureScreenEffect(enabled = shouldEnforceFlagSecure(BuildConfig.DEBUG))

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
        Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val hazeState = remember { HazeState() }
  val biometricAvailable = remember(context) { BiometricAuthHelper.isBiometricAvailable(context) }

  MChatTheme(
    themeMode = preferences.themeMode,
    reduceTransparency = preferences.reduceTransparency,
    hazeState = hazeState,
  ) {
    val showAppLock = lockStatus == AppLockStatus.LOCKED || pinOverlay.isSettingUpPin

    if (showAppLock) {
      val promptTitle = stringResource(DesignR.string.applock_biometric_prompt_title)
      val promptSubtitle = stringResource(DesignR.string.applock_biometric_prompt_subtitle)
      val promptNegative = stringResource(DesignR.string.applock_biometric_negative)
      val errorMessage = when (pinOverlay.errorType) {
        PinErrorType.WRONG_PIN -> stringResource(DesignR.string.applock_error_wrong_pin)
        PinErrorType.PIN_MISMATCH -> stringResource(DesignR.string.applock_error_mismatch)
        null -> null
      }
      val canUseBiometric = !pinOverlay.isSettingUpPin &&
        preferences.biometricEnabled &&
        biometricAvailable

      LaunchedEffect(lockStatus, canUseBiometric) {
        if (lockStatus == AppLockStatus.LOCKED && canUseBiometric) {
          BiometricAuthHelper.authenticate(
            activity = activity,
            title = promptTitle,
            subtitle = promptSubtitle,
            negativeButtonText = promptNegative,
            onSuccess = viewModel::onBiometricAuthenticated,
          )
        }
      }

      AppLockScreen(
        mode = pinOverlay.mode,
        enteredDigitsCount = pinOverlay.enteredDigits.length,
        errorMessage = errorMessage,
        showBiometricButton = canUseBiometric,
        showCancelButton = pinOverlay.isSettingUpPin,
        onDigitClick = viewModel::onPinDigit,
        onBackspaceClick = viewModel::onPinBackspace,
        onBiometricClick = {
          BiometricAuthHelper.authenticate(
            activity = activity,
            title = promptTitle,
            subtitle = promptSubtitle,
            negativeButtonText = promptNegative,
            onSuccess = viewModel::onBiometricAuthenticated,
          )
        },
        onCancelClick = viewModel::cancelPinSetup,
      )
    } else {
      val navController = rememberNavController()
      val navBackStackEntry by navController.currentBackStackEntryAsState()
      val currentDestination = navBackStackEntry?.destination

      val isPrimaryTabRoute = currentDestination?.let { dest ->
        dest.hasRoute<AppDestination.Home>() ||
          dest.hasRoute<AppDestination.Statistics>() ||
          dest.hasRoute<AppDestination.NewSurvey>() ||
          dest.hasRoute<AppDestination.Settings>()
      } ?: true

      val selectedTab = when {
        currentDestination?.hasRoute<AppDestination.Statistics>() == true -> PrimaryTab.STATISTICS
        currentDestination?.hasRoute<AppDestination.NewSurvey>() == true -> PrimaryTab.NEW_SURVEY
        currentDestination?.hasRoute<AppDestination.Settings>() == true -> PrimaryTab.SETTINGS
        else -> PrimaryTab.HOME
      }

      fun navigateToTab(tab: PrimaryTab) {
        navController.navigate(tab.toDestination()) {
          popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
          }
          launchSingleTop = true
          restoreState = true
        }
      }

      fun openScreeningByStatus(screeningId: String, status: ScreeningStatus) {
        when (status) {
          ScreeningStatus.DRAFT,
          ScreeningStatus.INITIAL_IN_PROGRESS,
          -> navController.navigate(AppDestination.Questionnaire(screeningId))
          ScreeningStatus.FOLLOWUP_REQUIRED,
          ScreeningStatus.FOLLOWUP_IN_PROGRESS,
          -> navController.navigate(AppDestination.FollowUp(screeningId))
          else -> navController.navigate(AppDestination.ScreeningResult(screeningId))
        }
      }

      Box(modifier = Modifier.fillMaxSize()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState),
        ) {
          AnimatedAmbientBackground()

          NavHost(
            navController = navController,
            startDestination = AppDestination.Home,
            modifier = Modifier.fillMaxSize(),
          ) {
            composable<AppDestination.Home> {
              HomeRoute(
                onStartNewSurvey = { navigateToTab(PrimaryTab.NEW_SURVEY) },
                onOpenPatients = { navController.navigate(AppDestination.PatientList) },
                onOpenFilteredScreenings = { key ->
                  navController.navigate(AppDestination.FilteredScreenings(key))
                },
                onOpenScreening = { summary ->
                  openScreeningByStatus(summary.id, summary.status)
                },
              )
            }

            composable<AppDestination.Statistics> {
              StatsRoute(
                onOpenFilteredList = { filterKey ->
                  navController.navigate(AppDestination.FilteredScreenings(filterKey))
                },
              )
            }

            composable<AppDestination.NewSurvey> { backStackEntry ->
              val route = backStackEntry.toRoute<AppDestination.NewSurvey>()
              NewSurveyRoute(
                preselectedPatientId = route.preselectedPatientId,
                onRegisterNewPatient = { navController.navigate(AppDestination.PatientRegister) },
                onStartQuestionnaire = { screeningId ->
                  navController.navigate(AppDestination.Questionnaire(screeningId))
                },
                onResumeFollowUp = { screeningId ->
                  navController.navigate(AppDestination.FollowUp(screeningId))
                },
              )
            }

            composable<AppDestination.Settings> {
              SettingsRoute(
                onRequestPinSetup = viewModel::startPinSetup,
                onOpenAuditLog = { navController.navigate(AppDestination.AuditLog) },
              )
            }

            composable<AppDestination.PatientList> {
              PatientListRoute(
                onBack = { navController.popBackStack() },
                onRegisterPatient = { navController.navigate(AppDestination.PatientRegister) },
                onOpenPatient = { patientId ->
                  navController.navigate(AppDestination.PatientProfile(patientId))
                },
              )
            }

            composable<AppDestination.PatientRegister> {
              PatientFormRoute(
                onBack = { navController.popBackStack() },
                onSaved = { patientId ->
                  navController.navigate(AppDestination.PatientProfile(patientId)) {
                    popUpTo(AppDestination.PatientRegister) { inclusive = true }
                  }
                },
              )
            }

            composable<AppDestination.PatientEdit> {
              PatientFormRoute(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
              )
            }

            composable<AppDestination.PatientProfile> {
              PatientProfileRoute(
                onBack = { navController.popBackStack() },
                onEditPatient = { patientId ->
                  navController.navigate(AppDestination.PatientEdit(patientId))
                },
                onStartScreening = { patientId ->
                  navController.navigate(AppDestination.NewSurvey(preselectedPatientId = patientId))
                },
                onOpenScreening = { entity ->
                  openScreeningByStatus(entity.id, entity.status)
                },
              )
            }

            composable<AppDestination.Questionnaire> {
              QuestionnaireRoute(
                onBack = { navController.popBackStack() },
                onGoToFollowUp = { screeningId ->
                  navController.navigate(AppDestination.FollowUp(screeningId)) {
                    popUpTo<AppDestination.Questionnaire> { inclusive = true }
                  }
                },
                onGoToResult = { screeningId ->
                  navController.navigate(AppDestination.ScreeningResult(screeningId)) {
                    popUpTo<AppDestination.Questionnaire> { inclusive = true }
                  }
                },
              )
            }

            composable<AppDestination.FollowUp> {
              FollowUpRoute(
                onBack = { navController.popBackStack() },
                onCompleted = { screeningId ->
                  navController.navigate(AppDestination.ScreeningResult(screeningId)) {
                    popUpTo<AppDestination.FollowUp> { inclusive = true }
                  }
                },
              )
            }

            composable<AppDestination.ScreeningResult> {
              ScreeningResultRoute(
                onBack = { navController.popBackStack() },
                onOpenReport = { screeningId ->
                  navController.navigate(AppDestination.ReportViewer(screeningId))
                },
                onOpenCorrectionQuestionnaire = { screeningId ->
                  navController.navigate(AppDestination.Questionnaire(screeningId)) {
                    popUpTo<AppDestination.ScreeningResult> { inclusive = true }
                  }
                },
              )
            }

            composable<AppDestination.ReportViewer> {
              ReportViewerRoute(
                onBack = { navController.popBackStack() },
              )
            }

            composable<AppDestination.FilteredScreenings> {
              FilteredScreeningsRoute(
                onBack = { navController.popBackStack() },
                onOpenScreening = { summary ->
                  openScreeningByStatus(summary.id, summary.status)
                },
              )
            }

            composable<AppDestination.AuditLog> {
              AuditLogRoute(
                onBack = { navController.popBackStack() },
              )
            }
          }
        }

        GlassBottomBar(
          selectedTab = selectedTab,
          onSelectTab = ::navigateToTab,
          visible = isPrimaryTabRoute,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    }
  }
}
