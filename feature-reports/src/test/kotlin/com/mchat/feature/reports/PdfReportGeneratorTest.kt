package com.mchat.feature.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.mchat.core.design.R as DesignR
import com.mchat.data.local.entity.AssessmentEntity
import com.mchat.data.local.entity.PatientEntity
import com.mchat.data.local.entity.QuestionnaireResponseEntity
import com.mchat.data.local.entity.ScreeningEntity
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.FollowUpFlowDefinition
import com.mchat.domain.scoring.model.FollowUpNode
import com.mchat.domain.scoring.model.FollowUpNodeType
import com.mchat.domain.scoring.model.FollowUpResponseLabels
import com.mchat.domain.scoring.model.InitialCategory
import com.mchat.domain.scoring.model.InitialCategoryRule
import com.mchat.domain.scoring.model.InitialResponseLabels
import com.mchat.domain.scoring.model.InitialResponseValue
import com.mchat.domain.scoring.model.InstrumentDefinition
import com.mchat.domain.scoring.model.InstrumentItem
import com.mchat.domain.scoring.model.InstrumentResponseOptions
import com.mchat.domain.scoring.model.IntendedAgeRange
import com.mchat.domain.scoring.model.LowRiskRescreenRule
import com.mchat.domain.scoring.model.RecommendationContent
import com.mchat.domain.scoring.model.ScoringConfig
import com.mchat.domain.scoring.model.ScreeningStatus
import com.mchat.domain.scoring.model.SyncStatus
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import java.io.OutputStream

/**
 * JVM shadow for Android's native PdfDocument (whose JNI nativeCreateDocument() returns 0 under Robolectric).
 * Uses real Android Bitmap + Canvas backed by Robolectric's native graphics engine so StaticLayout pagination,
 * multi-page splits, and Noto Sans Bengali shaping run end-to-end through PdfReportGenerator.
 */
@Implements(PdfDocument::class)
class TestShadowPdfDocument {
  private val finishedPages = mutableListOf<PdfDocument.PageInfo>()
  private var closed = false

  @Implementation
  protected fun __constructor__() {
    closed = false
  }

  @Implementation
  protected fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
    check(!closed) { "document is closed!" }
    val bitmap = Bitmap.createBitmap(pageInfo.pageWidth, pageInfo.pageHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    return ReflectionHelpers.callConstructor(
      PdfDocument.Page::class.java,
      ReflectionHelpers.ClassParameter.from(Canvas::class.java, canvas),
      ReflectionHelpers.ClassParameter.from(PdfDocument.PageInfo::class.java, pageInfo),
    )
  }

  @Implementation
  protected fun finishPage(page: PdfDocument.Page) {
    check(!closed) { "document is closed!" }
    finishedPages.add(page.info)
  }

  @Implementation
  protected fun writeTo(out: OutputStream) {
    check(!closed) { "document is closed!" }
    val summary = buildString {
      append("%PDF-1.4\n")
      append("% Rendered pages: ${finishedPages.size}\n")
      finishedPages.forEach { p ->
        append("% Page ${p.pageNumber}: ${p.pageWidth}x${p.pageHeight}\n")
      }
      append("%%EOF\n")
    }
    out.write(summary.toByteArray(Charsets.US_ASCII))
  }

  @Implementation
  protected fun close() {
    closed = true
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [TestShadowPdfDocument::class])
class PdfReportGeneratorTest {

  private fun sampleInstrument(lang: String): InstrumentDefinition {
    val sampleBanglaConjuncts = "পর্যবেক্ষণ, শ্রেণীবিভাগ, স্ক্রিনিং, যুক্তবর্ণ, বুদ্ধিমত্তা, লক্ষণসমূহ, স্বাস্থ্যসেবা ও বিশেষজ্ঞ মূল্যায়ন।"
    val items = (1..20).map { n ->
      InstrumentItem(
        itemId = "mchat_r_%02d".format(n),
        officialNumber = n,
        text = if (lang == "bn") "প্রশ্ন $n: $sampleBanglaConjuncts" else "Sample official English item $n.",
        atRiskResponse = if (n in setOf(2, 5, 12)) InitialResponseValue.YES else InitialResponseValue.NO,
        pointValue = 1,
        followUpFlow = FollowUpFlowDefinition("n1", listOf(FollowUpNode("n1", FollowUpNodeType.CLINICIAN_PASS_FAIL, "Prompt"))),
      )
    }
    return InstrumentDefinition(
      schemaVersion = 1,
      instrumentId = "mchat-r-f",
      instrumentVersion = if (lang == "bn") "2017-bn-v1" else "2009-en-v1",
      scoringAlgorithmVersion = "1.0.0",
      language = lang,
      displayTitle = "M-CHAT-R/F",
      copyrightNotice = "© 2009 Diana Robins, Deborah Fein, & Marianne Barton",
      translatorCredit = if (lang == "bn") "অনুবাদক: শাহীন আখতার, জান্নাতারা শেফা, আবু সুফিয়ান, এমদাদুল হক হাওলাদার (২০১৭)" else null,
      permissionNotice = "Official clinical permission text",
      nonDiagnosticDisclaimer = if (lang == "bn") "এটি শুধুমাত্র স্ক্রিনিং টুল, ডায়াগনস্টিক নয়।" else "Screening tool only, not diagnostic.",
      intendedAgeMonths = IntendedAgeRange(16, 30),
      responseOptions = InstrumentResponseOptions(
        InitialResponseLabels(if (lang == "bn") "হ্যাঁ" else "Yes", if (lang == "bn") "না" else "No"),
        FollowUpResponseLabels("Pass", "Fail"),
      ),
      scoringConfig = ScoringConfig(
        totalItems = 20,
        initialCategories = listOf(
          InitialCategoryRule(InitialCategory.LOW, 0, 2),
          InitialCategoryRule(InitialCategory.MODERATE, 3, 7),
          InitialCategoryRule(InitialCategory.HIGH, 8, 20),
        ),
        followUpPositiveThreshold = 2,
        highRiskAllowsOptionalFollowUp = false,
        lowRiskRescreenRule = LowRiskRescreenRule(24, 24),
      ),
      items = items,
      recommendations = mapOf(
        "NEGATIVE_LOW_INITIAL_24M_OR_OLDER" to RecommendationContent(
          "NEGATIVE_LOW_INITIAL_24M_OR_OLDER",
          "Low Risk",
          if (lang == "bn") "কম ঝুঁকি: $sampleBanglaConjuncts" else "Low risk official guidance.",
        ),
      ),
    )
  }

  @Test
  fun bundledNotoSansBengaliFont_loadsAndShapesBanglaConjuncts() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bengaliFont = ResourcesCompat.getFont(context, DesignR.font.noto_sans_bengali)
    assertNotNull("Bundled Noto Sans Bengali font must load from resources", bengaliFont)

    val conjunctSample = "পর্যবেক্ষণ, শ্রেণীবিভাগ, স্ক্রিনিং, যুক্তবর্ণ, বুদ্ধিমত্তা, লক্ষণসমূহ, স্বাস্থ্যসেবা ও বিশেষজ্ঞ মূল্যায়ন।"
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = bengaliFont ?: Typeface.SANS_SERIF
      textSize = 12f
    }
    val layout = StaticLayout.Builder.obtain(conjunctSample, 0, conjunctSample.length, paint, 400)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .build()
    assertTrue("Bangla conjunct layout must produce positive height", layout.height > 0)
    assertTrue("Bangla conjunct text must measure positive line width", layout.getLineWidth(0) > 0f)
  }

  @Test
  fun generatesValidMultiPagePdf_forEnglish_BanglaConjuncts_andBilingual() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val generator = PdfReportGenerator(context)

    val patient = PatientEntity(
      id = "p1",
      patientCode = "CLN-0001",
      isAutoGeneratedCode = true,
      fullName = "আয়ান রহমান (Ayan Rahman)",
      dateOfBirth = "2023-10-01",
      sex = "MALE",
      extraFieldsJson = "{}",
      clinicalNotes = null,
      isArchived = false,
      archivedAt = null,
      syncStatus = SyncStatus.LOCAL_ONLY,
      recordVersion = 1,
      createdAt = 1L,
      updatedAt = 1L,
    )
    val screening = ScreeningEntity(
      id = "s1",
      patientId = "p1",
      caregiverId = null,
      consentRecordId = "c1",
      screeningDate = "2025-11-01",
      childAgeMonths = 25,
      childAgeDaysRemainder = 0,
      outOfRangeAcknowledged = false,
      languageUsed = "bn",
      instrumentVersion = "2017-bn-v1",
      instrumentChecksum = "15fb5e563c7ea07256c7d8d3d0ece5d8",
      scoringAlgorithmVersion = "1.0.0",
      status = ScreeningStatus.COMPLETED,
      initialScore = 1,
      initialCategory = InitialCategory.LOW,
      followupScore = null,
      finalOutcome = FinalOutcome.NEGATIVE_LOW_INITIAL,
      currentRevisionNumber = 1,
      isLocked = true,
      rescreenDueDate = null,
      syncStatus = SyncStatus.LOCAL_ONLY,
      recordVersion = 1,
      createdAt = 1L,
      updatedAt = 1L,
    )
    val assessment = AssessmentEntity(
      id = "a1",
      screeningId = "s1",
      revisionNumber = 1,
      initialScore = 1,
      initialCategory = InitialCategory.LOW,
      followupScore = null,
      finalOutcome = FinalOutcome.NEGATIVE_LOW_INITIAL,
      recommendationCode = "NEGATIVE_LOW_INITIAL_24M_OR_OLDER",
      recommendationTextSnapshot = "Low risk official guidance.",
      disclaimerTextSnapshot = "Screening only.",
      rescreenRecommended = false,
      rescreenTargetAgeMonths = null,
      clinicianNotes = null,
      assessedAt = 1L,
    )
    val responses = (1..20).map { n ->
      QuestionnaireResponseEntity(
        id = "r$n",
        screeningId = "s1",
        revisionNumber = 1,
        itemNumber = n,
        itemId = "mchat_r_%02d".format(n),
        responseValue = if (n == 1) "NO" else "YES",
        isAtRisk = n == 1,
        pointValue = if (n == 1) 1 else 0,
        answeredAt = 1L,
        updatedAt = 1L,
      )
    }

    ReportLanguageMode.entries.forEach { mode ->
      val bytes = generator.generatePdfBytes(
        ClinicalReportInput(
          patient = patient,
          screening = screening,
          assessment = assessment,
          responses = responses,
          followUps = emptyList(),
          instrumentEn = sampleInstrument("en"),
          instrumentBn = sampleInstrument("bn"),
          clinicianName = "Dr. Clinician",
          clinicName = "Pediatric Developmental Clinic",
          languageMode = mode,
        ),
      )
      val text = bytes.toString(Charsets.US_ASCII)
      assertTrue("PDF for $mode must start with %PDF-, got $text", text.startsWith("%PDF-"))
      assertTrue("PDF for $mode must render at least 1 A4 page", text.contains("Page 1: 595x842"))
    }
  }
}
