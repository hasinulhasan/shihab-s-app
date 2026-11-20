package com.mchat.feature.reports

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mchat.core.common.AppLanguage
import com.mchat.core.common.instrument.AssetInstrumentLoader
import com.mchat.core.common.instrument.InstrumentLoadState
import com.mchat.core.design.components.GlassCard
import com.mchat.core.design.components.PrimaryButton
import com.mchat.core.design.components.SecondaryGlassButton
import com.mchat.data.local.preferences.UiPreferencesRepository
import com.mchat.data.local.repository.AuditEventType
import com.mchat.data.local.repository.AuditRepository
import com.mchat.data.local.repository.PatientRepository
import com.mchat.data.local.repository.ScreeningRepository
import com.mchat.data.sync.DriveSyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ReportViewerUiState(
  val languageMode: ReportLanguageMode = ReportLanguageMode.ENGLISH,
  val suggestedFileName: String = "MCHAT_Report.pdf",
  val pdfBytes: ByteArray? = null,
  val renderedPages: List<ImageBitmap> = emptyList(),
  val isGenerating: Boolean = false,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
)

@HiltViewModel
class ReportViewerViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  @ApplicationContext private val appContext: Context,
  private val screeningRepository: ScreeningRepository,
  private val patientRepository: PatientRepository,
  private val preferencesRepository: UiPreferencesRepository,
  private val instrumentLoader: AssetInstrumentLoader,
  private val pdfGenerator: PdfReportGenerator,
  private val auditRepository: AuditRepository,
  private val driveSyncCoordinator: DriveSyncCoordinator,
) : ViewModel() {

  private val screeningId: String = checkNotNull(savedStateHandle["screeningId"])

  private val _uiState = MutableStateFlow(ReportViewerUiState())
  val uiState: StateFlow<ReportViewerUiState> = _uiState.asStateFlow()

  init {
    regenerate(ReportLanguageMode.ENGLISH)
  }

  fun selectLanguageMode(mode: ReportLanguageMode) {
    if (mode != _uiState.value.languageMode) {
      regenerate(mode)
    }
  }

  private fun regenerate(mode: ReportLanguageMode) {
    viewModelScope.launch {
      _uiState.update { it.copy(languageMode = mode, isGenerating = true, errorMessage = null, statusMessage = null) }
      val screening = screeningRepository.getScreeningById(screeningId)
      val patient = screening?.let { patientRepository.getPatientById(it.patientId) }
      val en = instrumentLoader.loadVerifiedInstrument(AppLanguage.ENGLISH) as? InstrumentLoadState.Verified
      val bn = instrumentLoader.loadVerifiedInstrument(AppLanguage.BANGLA) as? InstrumentLoadState.Verified

      if (screening == null || patient == null || en == null || bn == null) {
        _uiState.update { it.copy(isGenerating = false, errorMessage = "Unable to load verified report inputs.") }
        return@launch
      }

      val assessment = screeningRepository.getAssessment(screening.id, screening.currentRevisionNumber)
      val responses = screeningRepository.getQuestionnaireResponses(screening.id, screening.currentRevisionNumber)
      val followUps = screeningRepository.getFollowUpResponses(screening.id, screening.currentRevisionNumber)
      val prefs = preferencesRepository.getCurrent()

      val bytes = withContext(Dispatchers.Default) {
        pdfGenerator.generatePdfBytes(
          ClinicalReportInput(
            patient = patient,
            screening = screening,
            assessment = assessment,
            responses = responses,
            followUps = followUps,
            instrumentEn = en.instrument.definition,
            instrumentBn = bn.instrument.definition,
            clinicianName = prefs.clinicianName,
            clinicName = prefs.clinicName,
            languageMode = mode,
          ),
        )
      }

      val pages = withContext(Dispatchers.IO) { renderPdfPages(bytes) }
      val fileName = "MCHAT_${patient.patientCode}_${screening.screeningDate}_${mode.name.lowercase()}.pdf"

      _uiState.update {
        it.copy(
          isGenerating = false,
          pdfBytes = bytes,
          renderedPages = pages,
          suggestedFileName = fileName,
        )
      }
    }
  }

  fun prepareSecureShareFile(): File? {
    val bytes = _uiState.value.pdfBytes ?: return null
    val dir = File(appContext.cacheDir, "shared_reports").apply { mkdirs() }
    val outFile = File(dir, _uiState.value.suggestedFileName)
    outFile.writeBytes(bytes)
    viewModelScope.launch {
      auditRepository.recordEvent(
        eventType = AuditEventType.EXPORT,
        entityType = "REPORT_PDF",
        entityId = screeningId,
        metadataJson = "{\"action\":\"SECURE_SHARE\",\"lang\":\"${_uiState.value.languageMode.name}\"}",
      )
    }
    return outFile
  }

  fun recordLocalSaveAudit() {
    viewModelScope.launch {
      auditRepository.recordEvent(
        eventType = AuditEventType.EXPORT,
        entityType = "REPORT_PDF",
        entityId = screeningId,
        metadataJson = "{\"action\":\"SAF_SAVE\",\"lang\":\"${_uiState.value.languageMode.name}\"}",
      )
      _uiState.update { it.copy(statusMessage = "PDF saved securely to chosen folder.") }
    }
  }

  fun queueEncryptedDriveUpload() {
    val bytes = _uiState.value.pdfBytes ?: return
    val name = _uiState.value.suggestedFileName
    viewModelScope.launch {
      val queued = driveSyncCoordinator.enqueueEncryptedArtifactUpload(
        entityType = "REPORT_PDF",
        baseFileName = name,
        plainBytes = bytes,
      )
      _uiState.update {
        it.copy(
          statusMessage = if (queued) {
            "PDF encrypted locally (AES-256-GCM) and queued for Google Drive /reports upload."
          } else {
            "Unlock your Recovery Passphrase in Settings → Encrypted Drive Backup before uploading encrypted PDFs."
          },
        )
      }
    }
  }

  private fun renderPdfPages(bytes: ByteArray): List<ImageBitmap> {
    val temp = File(appContext.cacheDir, "preview_render.pdf")
    temp.writeBytes(bytes)
    val bitmaps = mutableListOf<ImageBitmap>()
    runCatching {
      ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { page ->
              val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
              bmp.eraseColor(android.graphics.Color.WHITE)
              page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              bitmaps += bmp.asImageBitmap()
            }
          }
        }
      }
    }
    return bitmaps
  }
}

@Composable
fun ReportViewerRoute(
  onBack: () -> Unit,
  viewModel: ReportViewerViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  val savePdfLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/pdf"),
  ) { uri ->
    val bytes = state.pdfBytes ?: return@rememberLauncherForActivityResult
    if (uri != null) {
      context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
      viewModel.recordLocalSaveAudit()
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 48.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("report_back_button")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column {
          Text("Clinical PDF Report", style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "Embedded Noto Sans Bengali • Secure local & encrypted export",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = state.languageMode == ReportLanguageMode.ENGLISH,
          onClick = { viewModel.selectLanguageMode(ReportLanguageMode.ENGLISH) },
          label = { Text("English") },
          modifier = Modifier.testTag("report_lang_en"),
        )
        FilterChip(
          selected = state.languageMode == ReportLanguageMode.BANGLA,
          onClick = { viewModel.selectLanguageMode(ReportLanguageMode.BANGLA) },
          label = { Text("বাংলা") },
          modifier = Modifier.testTag("report_lang_bn"),
        )
        FilterChip(
          selected = state.languageMode == ReportLanguageMode.BILINGUAL,
          onClick = { viewModel.selectLanguageMode(ReportLanguageMode.BILINGUAL) },
          label = { Text("Bilingual") },
          modifier = Modifier.testTag("report_lang_bilingual"),
        )
      }
    }

    item {
      GlassCard(contentPadding = PaddingValues(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          PrimaryButton(
            text = "Save PDF",
            icon = Icons.Rounded.SaveAlt,
            enabled = state.pdfBytes != null,
            onClick = { savePdfLauncher.launch(state.suggestedFileName) },
            modifier = Modifier.weight(1f),
            testTag = "report_save_pdf_button",
          )
          SecondaryGlassButton(
            text = "Secure Share",
            icon = Icons.Rounded.Share,
            enabled = state.pdfBytes != null,
            onClick = {
              val file = viewModel.prepareSecureShareFile() ?: return@SecondaryGlassButton
              val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
              val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
              context.startActivity(Intent.createChooser(shareIntent, "Share Clinical PDF Securely"))
            },
            modifier = Modifier.weight(1f),
            testTag = "report_share_pdf_button",
          )
        }
        SecondaryGlassButton(
          text = "Encrypt & Upload to Google Drive",
          icon = Icons.Rounded.CloudUpload,
          enabled = state.pdfBytes != null,
          onClick = viewModel::queueEncryptedDriveUpload,
          modifier = Modifier.fillMaxWidth(),
          testTag = "report_upload_drive_button",
        )
        state.statusMessage?.let { msg ->
          Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        state.errorMessage?.let { err ->
          Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    }

    itemsIndexed(state.renderedPages) { index, pageBitmap ->
      GlassCard(contentPadding = PaddingValues(8.dp)) {
        Text(
          text = "Page ${index + 1} of ${state.renderedPages.size}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 6.dp),
        )
        Image(
          bitmap = pageBitmap,
          contentDescription = "PDF Report Page ${index + 1}",
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(595f / 842f)
            .background(Color.White),
        )
      }
    }

    item { Spacer(modifier = Modifier.height(16.dp)) }
  }
}
