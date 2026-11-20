package com.mchat.core.design.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.mchat.core.design.R
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.InitialCategory
import com.mchat.domain.scoring.model.SyncStatus

data class OutcomePresentation(
  val label: String,
  val icon: ImageVector,
  val tone: StatusTone,
)

@Composable
fun FinalOutcome.toPresentation(): OutcomePresentation = when (this) {
  FinalOutcome.NEGATIVE_LOW_INITIAL -> OutcomePresentation(
    label = "${stringResource(R.string.status_negative_screen)} (${stringResource(R.string.status_low_risk)})",
    icon = Icons.Rounded.CheckCircle,
    tone = StatusTone.LOW_GREEN,
  )
  FinalOutcome.NEGATIVE_AFTER_FOLLOWUP -> OutcomePresentation(
    label = "${stringResource(R.string.status_negative_screen)} (Follow-Up)",
    icon = Icons.Rounded.CheckCircle,
    tone = StatusTone.LOW_GREEN,
  )
  FinalOutcome.POSITIVE_AFTER_FOLLOWUP -> OutcomePresentation(
    label = "${stringResource(R.string.status_positive_screen)} (Follow-Up)",
    icon = Icons.Rounded.WarningAmber,
    tone = StatusTone.HIGH_CORAL,
  )
  FinalOutcome.POSITIVE_HIGH_INITIAL -> OutcomePresentation(
    label = "${stringResource(R.string.status_positive_screen)} (${stringResource(R.string.status_high_risk)})",
    icon = Icons.Rounded.WarningAmber,
    tone = StatusTone.HIGH_CORAL,
  )
  FinalOutcome.FOLLOWUP_PENDING -> OutcomePresentation(
    label = stringResource(R.string.status_followup_pending),
    icon = Icons.Rounded.Schedule,
    tone = StatusTone.MODERATE_AMBER,
  )
  FinalOutcome.INCOMPLETE -> OutcomePresentation(
    label = stringResource(R.string.status_incomplete),
    icon = Icons.Rounded.EditNote,
    tone = StatusTone.NEUTRAL,
  )
}

@Composable
fun InitialCategory.toPresentation(): OutcomePresentation = when (this) {
  InitialCategory.LOW -> OutcomePresentation(
    label = stringResource(R.string.status_low_risk),
    icon = Icons.Rounded.CheckCircle,
    tone = StatusTone.LOW_GREEN,
  )
  InitialCategory.MODERATE -> OutcomePresentation(
    label = stringResource(R.string.status_moderate_risk),
    icon = Icons.Rounded.Schedule,
    tone = StatusTone.MODERATE_AMBER,
  )
  InitialCategory.HIGH -> OutcomePresentation(
    label = stringResource(R.string.status_high_risk),
    icon = Icons.Rounded.WarningAmber,
    tone = StatusTone.HIGH_CORAL,
  )
}

@Composable
fun SyncStatus.toPresentation(): OutcomePresentation = when (this) {
  SyncStatus.SYNCED -> OutcomePresentation(
    label = stringResource(R.string.status_synced),
    icon = Icons.Rounded.CloudDone,
    tone = StatusTone.LOW_GREEN,
  )
  SyncStatus.PENDING_SYNC -> OutcomePresentation(
    label = stringResource(R.string.status_pending_sync),
    icon = Icons.Rounded.CloudQueue,
    tone = StatusTone.MODERATE_AMBER,
  )
  SyncStatus.IN_PROGRESS -> OutcomePresentation(
    label = stringResource(R.string.status_sync_in_progress),
    icon = Icons.Rounded.CloudSync,
    tone = StatusTone.INFO_TEAL,
  )
  SyncStatus.FAILED -> OutcomePresentation(
    label = stringResource(R.string.status_sync_failed),
    icon = Icons.Rounded.ErrorOutline,
    tone = StatusTone.HIGH_CORAL,
  )
  SyncStatus.CONFLICT -> OutcomePresentation(
    label = "Sync Conflict",
    icon = Icons.Rounded.WarningAmber,
    tone = StatusTone.MODERATE_AMBER,
  )
  SyncStatus.LOCAL_ONLY -> OutcomePresentation(
    label = stringResource(R.string.status_offline),
    icon = Icons.Rounded.CloudOff,
    tone = StatusTone.INFO_TEAL,
  )
}

@Composable
fun OutcomeStatusChip(
  outcome: FinalOutcome,
  modifier: Modifier = Modifier,
) {
  val presentation = outcome.toPresentation()
  StatusChip(
    label = presentation.label,
    icon = presentation.icon,
    tone = presentation.tone,
    modifier = modifier,
  )
}

@Composable
fun SyncStatusChip(
  status: SyncStatus,
  modifier: Modifier = Modifier,
) {
  val presentation = status.toPresentation()
  StatusChip(
    label = presentation.label,
    icon = presentation.icon,
    tone = presentation.tone,
    modifier = modifier,
  )
}
