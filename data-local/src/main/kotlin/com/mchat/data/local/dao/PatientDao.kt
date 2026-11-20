package com.mchat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mchat.data.local.entity.CaregiverEntity
import com.mchat.data.local.entity.ConsentRecordEntity
import com.mchat.data.local.entity.PatientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
  @Query("SELECT * FROM patients WHERE is_archived = :archived ORDER BY updated_at DESC")
  fun observePatients(archived: Boolean = false): Flow<List<PatientEntity>>

  @Query("SELECT * FROM patients ORDER BY updated_at DESC")
  fun observeAllPatients(): Flow<List<PatientEntity>>

  @Query("SELECT * FROM patients WHERE id = :id LIMIT 1")
  fun observePatientById(id: String): Flow<PatientEntity?>

  @Query("SELECT * FROM patients WHERE id = :id LIMIT 1")
  suspend fun getPatientById(id: String): PatientEntity?

  @Query("SELECT * FROM patients WHERE LOWER(patient_code) = LOWER(:code) LIMIT 1")
  suspend fun getPatientByCode(code: String): PatientEntity?

  @Query("SELECT COUNT(*) FROM patients WHERE is_archived = 0")
  fun observeActivePatientCount(): Flow<Int>

  @Query("SELECT * FROM patients")
  suspend fun getAllPatientsSnapshot(): List<PatientEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertPatient(patient: PatientEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPatient(patient: PatientEntity)

  @Update
  suspend fun updatePatient(patient: PatientEntity)

  @Query("UPDATE patients SET is_archived = :archived, archived_at = :archivedAt, updated_at = :updatedAt, record_version = record_version + 1 WHERE id = :id")
  suspend fun setPatientArchived(id: String, archived: Boolean, archivedAt: Long?, updatedAt: Long)

  // Caregivers
  @Query("SELECT * FROM caregivers WHERE patient_id = :patientId ORDER BY is_primary DESC, updated_at DESC")
  fun observeCaregiversForPatient(patientId: String): Flow<List<CaregiverEntity>>

  @Query("SELECT * FROM caregivers WHERE patient_id = :patientId ORDER BY is_primary DESC LIMIT 1")
  suspend fun getPrimaryCaregiver(patientId: String): CaregiverEntity?

  @Query("SELECT * FROM caregivers")
  suspend fun getAllCaregiversSnapshot(): List<CaregiverEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCaregiver(caregiver: CaregiverEntity)

  // Consent Records
  @Query("SELECT * FROM consent_records WHERE patient_id = :patientId ORDER BY consented_at DESC")
  fun observeConsentsForPatient(patientId: String): Flow<List<ConsentRecordEntity>>

  @Query("SELECT * FROM consent_records WHERE patient_id = :patientId AND is_withdrawn = 0 ORDER BY consented_at DESC LIMIT 1")
  suspend fun getLatestActiveConsent(patientId: String): ConsentRecordEntity?

  @Query("SELECT * FROM consent_records")
  suspend fun getAllConsentsSnapshot(): List<ConsentRecordEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertConsent(consent: ConsentRecordEntity)
}
