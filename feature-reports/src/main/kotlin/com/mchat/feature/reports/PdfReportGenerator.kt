package com.mchat.feature.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.mchat.core.design.R as DesignR
import com.mchat.data.local.entity.AssessmentEntity
import com.mchat.data.local.entity.FollowUpResponseEntity
import com.mchat.data.local.entity.PatientEntity
import com.mchat.data.local.entity.QuestionnaireResponseEntity
import com.mchat.data.local.entity.ScreeningEntity
import com.mchat.domain.scoring.model.InstrumentDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ReportLanguageMode {
  ENGLISH,
  BANGLA,
  BILINGUAL,
}

data class ClinicalReportInput(
  val patient: PatientEntity,
  val screening: ScreeningEntity,
  val assessment: AssessmentEntity?,
  val responses: List<QuestionnaireResponseEntity>,
  val followUps: List<FollowUpResponseEntity>,
  val instrumentEn: InstrumentDefinition,
  val instrumentBn: InstrumentDefinition,
  val clinicianName: String,
  val clinicName: String,
  val languageMode: ReportLanguageMode,
)

enum class ReportBlockStyle {
  TITLE,
  HEADING,
  BODY,
  MUTED,
  DIVIDER,
}

data class ReportBlock(
  val style: ReportBlockStyle,
  val text: String = "",
  val spacingAfter: Float = 6f,
)

/**
 * Generates multi-page A4 clinical screening PDF reports in English, Bangla, or Bilingual format
 * using Android's native PdfDocument + StaticLayout + bundled Noto Sans Bengali font for accurate
 * Bangla conjunct (যুক্তবর্ণ) shaping with zero external native binaries.
 */
@Singleton
class PdfReportGenerator @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  companion object {
    private const val PAGE_WIDTH = 595 // A4 width in PostScript points (72 DPI)
    private const val PAGE_HEIGHT = 842 // A4 height
    private const val MARGIN_X = 42f
    private const val MARGIN_TOP = 44f
    private const val MARGIN_BOTTOM = 46f
    private const val CONTENT_WIDTH = (PAGE_WIDTH - 2 * MARGIN_X).toInt()
  }

  fun loadBengaliTypeface(): Typeface = runCatching {
    ResourcesCompat.getFont(context, DesignR.font.noto_sans_bengali)
  }.getOrNull() ?: Typeface.SANS_SERIF

  fun loadLatinTypeface(): Typeface = runCatching {
    ResourcesCompat.getFont(context, DesignR.font.plus_jakarta_sans)
  }.getOrNull() ?: Typeface.SANS_SERIF

  fun buildReportBlocks(input: ClinicalReportInput): List<ReportBlock> {
    val blocks = mutableListOf<ReportBlock>()

    val headerTitle = when (input.languageMode) {
      ReportLanguageMode.ENGLISH -> "M-CHAT-R/F Clinical Screening Report"
      ReportLanguageMode.BANGLA -> "এম-চ্যাট-আর/এফ (M-CHAT-R/F) ক্লিনিক্যাল স্ক্রিনিং রিপোর্ট"
      ReportLanguageMode.BILINGUAL -> "M-CHAT-R/F Clinical Screening Report / ক্লিনিক্যাল স্ক্রিনিং রিপোর্ট"
    }
    blocks += ReportBlock(ReportBlockStyle.TITLE, headerTitle, spacingAfter = 4f)

    val clinicHeader = listOfNotNull(
      input.clinicName.ifBlank { null },
      input.clinicianName.ifBlank { null }?.let { "Clinician: $it" },
      "Screening Date: ${input.screening.screeningDate} • Revision ${input.screening.currentRevisionNumber}",
    ).joinToString("  |  ")
    blocks += ReportBlock(ReportBlockStyle.MUTED, clinicHeader, spacingAfter = 8f)
    blocks += ReportBlock(ReportBlockStyle.DIVIDER)

    val childHeader = when (input.languageMode) {
      ReportLanguageMode.ENGLISH -> "Child & Screening Summary"
      ReportLanguageMode.BANGLA -> "শিশুর তথ্য ও স্ক্রিনিং সারসংক্ষেপ"
      ReportLanguageMode.BILINGUAL -> "Child & Screening Summary / শিশুর তথ্য ও সারসংক্ষেপ"
    }
    blocks += ReportBlock(ReportBlockStyle.HEADING, childHeader, spacingAfter = 4f)
    blocks += ReportBlock(
      ReportBlockStyle.BODY,
      "Child Name: ${input.patient.fullName}    |    Patient ID: ${input.patient.patientCode}\n" +
        "DOB: ${input.patient.dateOfBirth}    |    Age at Screening: ${input.screening.childAgeMonths} months    |    Sex: ${input.patient.sex}\n" +
        "Instrument: ${input.screening.instrumentVersion} (SHA-256: ${input.screening.instrumentChecksum.take(16)}…)\n" +
        "Initial Score: ${input.screening.initialScore ?: "—"}/20 (${input.screening.initialCategory?.name ?: "—"})    |    " +
        "Follow-Up Score: ${input.screening.followupScore?.toString() ?: "N/A"}    |    " +
        "Final Outcome: ${input.screening.finalOutcome.name}",
      spacingAfter = 8f,
    )
    blocks += ReportBlock(ReportBlockStyle.DIVIDER)

    val recTitle = when (input.languageMode) {
      ReportLanguageMode.ENGLISH -> "Official Recommendation & Next Steps"
      ReportLanguageMode.BANGLA -> "অফিশিয়াল নির্দেশনা ও পরবর্তী করণীয়"
      ReportLanguageMode.BILINGUAL -> "Official Recommendation / অফিশিয়াল নির্দেশনা"
    }
    blocks += ReportBlock(ReportBlockStyle.HEADING, recTitle, spacingAfter = 4f)
    val recKey = input.assessment?.recommendationCode
    when (input.languageMode) {
      ReportLanguageMode.ENGLISH -> {
        val text = recKey?.let { input.instrumentEn.recommendations[it]?.body }
          ?: input.assessment?.recommendationTextSnapshot.orEmpty()
        blocks += ReportBlock(ReportBlockStyle.BODY, text, spacingAfter = 8f)
      }
      ReportLanguageMode.BANGLA -> {
        val text = recKey?.let { input.instrumentBn.recommendations[it]?.body }
          ?: input.assessment?.recommendationTextSnapshot.orEmpty()
        blocks += ReportBlock(ReportBlockStyle.BODY, text, spacingAfter = 8f)
      }
      ReportLanguageMode.BILINGUAL -> {
        val enText = recKey?.let { input.instrumentEn.recommendations[it]?.body }.orEmpty()
        val bnText = recKey?.let { input.instrumentBn.recommendations[it]?.body }.orEmpty()
        blocks += ReportBlock(ReportBlockStyle.BODY, "EN: $enText", spacingAfter = 4f)
        blocks += ReportBlock(ReportBlockStyle.BODY, "BN: $bnText", spacingAfter = 8f)
      }
    }

    val disclaimer = when (input.languageMode) {
      ReportLanguageMode.ENGLISH -> input.instrumentEn.nonDiagnosticDisclaimer
      ReportLanguageMode.BANGLA -> input.instrumentBn.nonDiagnosticDisclaimer
      ReportLanguageMode.BILINGUAL -> "${input.instrumentEn.nonDiagnosticDisclaimer}\n${input.instrumentBn.nonDiagnosticDisclaimer}"
    }
    blocks += ReportBlock(ReportBlockStyle.MUTED, "NON-DIAGNOSTIC DISCLAIMER: $disclaimer", spacingAfter = 8f)
    blocks += ReportBlock(ReportBlockStyle.DIVIDER)

    val itemsTitle = when (input.languageMode) {
      ReportLanguageMode.ENGLISH -> "Stage 1 — 20-Item Questionnaire Responses"
      ReportLanguageMode.BANGLA -> "ধাপ ১ — ২০টি প্রশ্নের উত্তর"
      ReportLanguageMode.BILINGUAL -> "Stage 1 — 20-Item Responses / ধাপ ১ — ২০টি প্রশ্নের উত্তর"
    }
    blocks += ReportBlock(ReportBlockStyle.HEADING, itemsTitle, spacingAfter = 6f)

    val respByNum = input.responses.associateBy { it.itemNumber }
    val enItems = input.instrumentEn.items.associateBy { it.officialNumber }
    val bnItems = input.instrumentBn.items.associateBy { it.officialNumber }

    for (num in 1..20) {
      val resp = respByNum[num]
      val flag = if (resp?.isAtRisk == true) "[AT-RISK]" else "[OK]"
      val ans = resp?.responseValue ?: "—"
      val line = when (input.languageMode) {
        ReportLanguageMode.ENGLISH ->
          "${"%02d".format(num)}. ${enItems[num]?.text.orEmpty()} — $ans $flag"
        ReportLanguageMode.BANGLA -> {
          val bnAns = when (ans) {
            "YES" -> input.instrumentBn.responseOptions.initial.yesLabel
            "NO" -> input.instrumentBn.responseOptions.initial.noLabel
            else -> "—"
          }
          "${"%02d".format(num)}. ${bnItems[num]?.text.orEmpty()} — $bnAns $flag"
        }
        ReportLanguageMode.BILINGUAL ->
          "${"%02d".format(num)}. ${enItems[num]?.text.orEmpty()}\n    ${bnItems[num]?.text.orEmpty()} — $ans $flag"
      }
      blocks += ReportBlock(ReportBlockStyle.BODY, line, spacingAfter = 3f)
    }

    if (input.followUps.isNotEmpty()) {
      blocks += ReportBlock(ReportBlockStyle.DIVIDER)
      blocks += ReportBlock(ReportBlockStyle.HEADING, "Stage 2 — Follow-Up Interview Results", spacingAfter = 4f)
      input.followUps.sortedBy { it.itemNumber }.forEach { fu ->
        val notesPart = fu.clinicianNotes?.let { " | Notes: $it" }.orEmpty()
        blocks += ReportBlock(
          ReportBlockStyle.BODY,
          "Item #${"%02d".format(fu.itemNumber)}: ${fu.itemResult} (Point: ${fu.pointValue})$notesPart",
          spacingAfter = 3f,
        )
      }
    }

    blocks += ReportBlock(ReportBlockStyle.DIVIDER)
    val footerText = when (input.languageMode) {
      ReportLanguageMode.ENGLISH -> listOfNotNull(
        input.instrumentEn.copyrightNotice,
        input.instrumentEn.permissionNotice,
      ).joinToString("\n")
      ReportLanguageMode.BANGLA -> listOfNotNull(
        input.instrumentBn.copyrightNotice,
        input.instrumentBn.translatorCredit,
        input.instrumentBn.permissionNotice,
      ).joinToString("\n")
      ReportLanguageMode.BILINGUAL -> listOfNotNull(
        input.instrumentEn.copyrightNotice,
        input.instrumentBn.translatorCredit,
        input.instrumentEn.permissionNotice,
        input.instrumentBn.permissionNotice,
      ).joinToString("\n")
    }
    blocks += ReportBlock(ReportBlockStyle.MUTED, footerText, spacingAfter = 4f)

    return blocks
  }

  fun generatePdfBytes(input: ClinicalReportInput): ByteArray {
    val primaryTypeface = when (input.languageMode) {
      ReportLanguageMode.ENGLISH -> loadLatinTypeface()
      ReportLanguageMode.BANGLA, ReportLanguageMode.BILINGUAL -> loadBengaliTypeface()
    }

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = Typeface.create(primaryTypeface, Typeface.BOLD)
      textSize = 15f
      color = Color.rgb(43, 89, 75) // Sage Deep
    }
    val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = Typeface.create(primaryTypeface, Typeface.BOLD)
      textSize = 11.5f
      color = Color.rgb(22, 33, 30)
    }
    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = primaryTypeface
      textSize = 9.5f
      color = Color.rgb(34, 45, 42)
    }
    val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = primaryTypeface
      textSize = 8.2f
      color = Color.rgb(85, 99, 95)
    }
    val dividerPaint = Paint().apply {
      color = Color.rgb(200, 214, 208)
      strokeWidth = 0.8f
    }

    val blocks = buildReportBlocks(input)
    val doc = PdfDocument()
    try {
      var pageNumber = 1
      var currentPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
      var canvas: Canvas = currentPage.canvas
      var cursorY = MARGIN_TOP

      fun ensureSpace(neededHeight: Float) {
        if (cursorY + neededHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
          doc.finishPage(currentPage)
          pageNumber++
          currentPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
          canvas = currentPage.canvas
          cursorY = MARGIN_TOP
        }
      }

      fun drawParagraph(text: String, paint: TextPaint, spacingAfter: Float) {
        if (text.isBlank()) return
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
          .setAlignment(Layout.Alignment.ALIGN_NORMAL)
          .setLineSpacing(2f, 1f)
          .setIncludePad(false)
          .build()
        ensureSpace(layout.height + spacingAfter)
        canvas.save()
        canvas.translate(MARGIN_X, cursorY)
        layout.draw(canvas)
        canvas.restore()
        cursorY += layout.height + spacingAfter
      }

      blocks.forEach { block ->
        when (block.style) {
          ReportBlockStyle.TITLE -> drawParagraph(block.text, titlePaint, block.spacingAfter)
          ReportBlockStyle.HEADING -> drawParagraph(block.text, headingPaint, block.spacingAfter)
          ReportBlockStyle.BODY -> drawParagraph(block.text, bodyPaint, block.spacingAfter)
          ReportBlockStyle.MUTED -> drawParagraph(block.text, mutedPaint, block.spacingAfter)
          ReportBlockStyle.DIVIDER -> {
            ensureSpace(10f)
            canvas.drawLine(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY, dividerPaint)
            cursorY += 10f
          }
        }
      }

      doc.finishPage(currentPage)

      return ByteArrayOutputStream().use { baos ->
        doc.writeTo(baos)
        baos.toByteArray()
      }
    } finally {
      doc.close()
    }
  }
}
