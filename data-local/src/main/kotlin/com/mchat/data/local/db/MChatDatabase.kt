package com.mchat.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.mchat.core.security.keystore.DatabasePassphraseProvider
import com.mchat.data.local.dao.PatientDao
import com.mchat.data.local.dao.ScreeningDao
import com.mchat.data.local.dao.StatsDao
import com.mchat.data.local.dao.SystemDao
import com.mchat.data.local.entity.AppSettingsEntity
import com.mchat.data.local.entity.AssessmentEntity
import com.mchat.data.local.entity.AuditLogEntity
import com.mchat.data.local.entity.CaregiverEntity
import com.mchat.data.local.entity.ConsentRecordEntity
import com.mchat.data.local.entity.FollowUpResponseEntity
import com.mchat.data.local.entity.PatientEntity
import com.mchat.data.local.entity.QuestionnaireResponseEntity
import com.mchat.data.local.entity.ScreeningEntity
import com.mchat.data.local.entity.ScreeningRevisionEntity
import com.mchat.data.local.entity.SyncRecordEntity
import com.mchat.domain.scoring.model.FinalOutcome
import com.mchat.domain.scoring.model.InitialCategory
import com.mchat.domain.scoring.model.ScreeningStatus
import com.mchat.domain.scoring.model.SyncStatus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

class MChatTypeConverters {
  @TypeConverter
  fun fromScreeningStatus(value: ScreeningStatus): String = value.name

  @TypeConverter
  fun toScreeningStatus(value: String): ScreeningStatus = ScreeningStatus.valueOf(value)

  @TypeConverter
  fun fromInitialCategory(value: InitialCategory?): String? = value?.name

  @TypeConverter
  fun toInitialCategory(value: String?): InitialCategory? = value?.let(InitialCategory::valueOf)

  @TypeConverter
  fun fromFinalOutcome(value: FinalOutcome): String = value.name

  @TypeConverter
  fun toFinalOutcome(value: String): FinalOutcome = FinalOutcome.valueOf(value)

  @TypeConverter
  fun fromSyncStatus(value: SyncStatus): String = value.name

  @TypeConverter
  fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}

@Database(
  entities = [
    PatientEntity::class,
    CaregiverEntity::class,
    ConsentRecordEntity::class,
    ScreeningEntity::class,
    ScreeningRevisionEntity::class,
    QuestionnaireResponseEntity::class,
    FollowUpResponseEntity::class,
    AssessmentEntity::class,
    SyncRecordEntity::class,
    AuditLogEntity::class,
    AppSettingsEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
@TypeConverters(MChatTypeConverters::class)
abstract class MChatDatabase : RoomDatabase() {
  abstract fun patientDao(): PatientDao
  abstract fun screeningDao(): ScreeningDao
  abstract fun statsDao(): StatsDao
  abstract fun systemDao(): SystemDao

  companion object {
    const val DB_NAME = "mchat_clinical_encrypted.db"
  }
}

/**
 * Explicit non-destructive migration registry starting at schema v1.
 * Destructive fallback is intentionally NEVER enabled for clinical records.
 */
object MChatMigrations {
  val ALL_MIGRATIONS: Array<Migration> = emptyArray()
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
  @Provides
  @Singleton
  fun provideEncryptedDatabase(
    @ApplicationContext context: Context,
    passphraseProvider: DatabasePassphraseProvider,
  ): MChatDatabase {
    System.loadLibrary("sqlcipher")
    val passphrase = passphraseProvider.getOrCreatePassphrase()
    val factory = SupportOpenHelperFactory(passphrase)
    return Room.databaseBuilder(
      context,
      MChatDatabase::class.java,
      MChatDatabase.DB_NAME,
    )
      .openHelperFactory(factory)
      .addMigrations(*MChatMigrations.ALL_MIGRATIONS)
      .build()
  }

  @Provides fun providePatientDao(db: MChatDatabase): PatientDao = db.patientDao()
  @Provides fun provideScreeningDao(db: MChatDatabase): ScreeningDao = db.screeningDao()
  @Provides fun provideStatsDao(db: MChatDatabase): StatsDao = db.statsDao()
  @Provides fun provideSystemDao(db: MChatDatabase): SystemDao = db.systemDao()
}
