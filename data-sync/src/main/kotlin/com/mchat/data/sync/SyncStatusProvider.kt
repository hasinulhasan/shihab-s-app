package com.mchat.data.sync

import com.mchat.data.local.dao.SystemDao
import com.mchat.domain.scoring.model.SyncStatus
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatusSummary(
  val status: SyncStatus = SyncStatus.LOCAL_ONLY,
  val lastSuccessfulSyncMillis: Long? = null,
  val pendingCount: Int = 0,
  val failureReasonCode: String? = null,
)

interface SyncStatusProvider {
  val syncSummary: Flow<SyncStatusSummary>
}

@Singleton
class RoomBackedSyncStatusProvider @Inject constructor(
  systemDao: SystemDao,
) : SyncStatusProvider {
  override val syncSummary: Flow<SyncStatusSummary> = combine(
    systemDao.observeSyncRecords(),
    systemDao.observeLastSuccessfulSync(),
    systemDao.observePendingSyncCount(),
  ) { records, lastSynced, pendingCount ->
    val latest = records.firstOrNull()
    val status = when {
      records.any { it.status == SyncStatus.IN_PROGRESS } -> SyncStatus.IN_PROGRESS
      latest?.status == SyncStatus.FAILED -> SyncStatus.FAILED
      pendingCount > 0 -> SyncStatus.PENDING_SYNC
      lastSynced != null -> SyncStatus.SYNCED
      else -> SyncStatus.LOCAL_ONLY
    }
    SyncStatusSummary(
      status = status,
      lastSuccessfulSyncMillis = lastSynced?.lastSyncedAt,
      pendingCount = pendingCount,
      failureReasonCode = if (status == SyncStatus.FAILED) latest?.errorCode else null,
    )
  }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindingsModule {
  @Binds
  @Singleton
  abstract fun bindSyncStatusProvider(
    impl: RoomBackedSyncStatusProvider,
  ): SyncStatusProvider
}

@Module
@InstallIn(SingletonComponent::class)
object SyncNetworkModule {
  @Provides
  @Singleton
  fun provideGoogleDriveApi(): GoogleDriveApi = GoogleDriveRetrofitFactory.create()
}
