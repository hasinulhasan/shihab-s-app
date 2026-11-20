package com.mchat.feature.patients

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class ExtraFieldInputType {
  TEXT,
  NUMBER,
}

data class PatientExtraFieldSpec(
  val key: String,
  val labelEn: String,
  val labelBn: String,
  val placeholderEn: String,
  val inputType: ExtraFieldInputType = ExtraFieldInputType.TEXT,
)

/**
 * Single configurable registry for optional patient intake fields.
 * Adding or reordering fields here automatically updates registration, edit, and profile displays.
 */
object PatientFieldRegistry {
  val fields: List<PatientExtraFieldSpec> = listOf(
    PatientExtraFieldSpec(
      key = "gestational_age_weeks",
      labelEn = "Gestational Age (weeks)",
      labelBn = "গর্ভকালীন বয়স (সপ্তাহ)",
      placeholderEn = "e.g., 38",
      inputType = ExtraFieldInputType.NUMBER,
    ),
    PatientExtraFieldSpec(
      key = "birth_weight_kg",
      labelEn = "Birth Weight (kg)",
      labelBn = "জন্মকালীন ওজন (কেজি)",
      placeholderEn = "e.g., 3.1",
      inputType = ExtraFieldInputType.NUMBER,
    ),
    PatientExtraFieldSpec(
      key = "referral_source",
      labelEn = "Referral Source",
      labelBn = "রেফারেল উৎস",
      placeholderEn = "e.g., Pediatrician / Self",
    ),
    PatientExtraFieldSpec(
      key = "home_language",
      labelEn = "Primary Language at Home",
      labelBn = "বাড়িতে প্রধান ভাষা",
      placeholderEn = "e.g., Bangla / English",
    ),
    PatientExtraFieldSpec(
      key = "district_city",
      labelEn = "District / City",
      labelBn = "জেলা / শহর",
      placeholderEn = "e.g., Dhaka",
    ),
  )

  private val json = Json { ignoreUnknownKeys = true }

  fun encode(values: Map<String, String>): String {
    val cleaned = values.filterValues { it.isNotBlank() }
      .mapValues { (_, v) -> JsonPrimitive(v.trim()) }
    return JsonObject(cleaned).toString()
  }

  fun decode(rawJson: String?): Map<String, String> {
    if (rawJson.isNullOrBlank()) return emptyMap()
    return runCatching {
      val obj = json.decodeFromString<JsonObject>(rawJson)
      obj.mapValues { (_, element) -> element.jsonPrimitive.contentOrNull.orEmpty() }
    }.getOrDefault(emptyMap())
  }
}
