package com.mchat.data.sync

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class DriveFolderHierarchy(
  val rootId: String,
  val backupsId: String,
  val reportsId: String,
  val statisticsId: String,
)

enum class DriveSubfolder(val folderName: String) {
  BACKUPS("backups"),
  REPORTS("reports"),
  STATISTICS("statistics"),
}

sealed interface DriveUploadOutcome {
  data class Success(val fileId: String, val remoteName: String, val remoteSizeBytes: Long) : DriveUploadOutcome
  data class TokenExpired(val httpCode: Int) : DriveUploadOutcome
  data class Failure(val code: String, val message: String) : DriveUploadOutcome
}

@Singleton
class DriveFolderManager @Inject constructor(
  private val api: GoogleDriveApi,
) {
  companion object {
    const val ROOT_FOLDER_NAME = "M-CHAT Medical Records"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val ENCRYPTED_MIME = "application/octet-stream"
  }

  private val json = Json { ignoreUnknownKeys = true }

  suspend fun ensureFolderHierarchy(accessToken: String): Result<DriveFolderHierarchy> = runCatching {
    val auth = "Bearer $accessToken"
    val rootId = findOrCreateFolder(auth, ROOT_FOLDER_NAME, parentId = null)
    val backupsId = findOrCreateFolder(auth, DriveSubfolder.BACKUPS.folderName, parentId = rootId)
    val reportsId = findOrCreateFolder(auth, DriveSubfolder.REPORTS.folderName, parentId = rootId)
    val statsId = findOrCreateFolder(auth, DriveSubfolder.STATISTICS.folderName, parentId = rootId)
    DriveFolderHierarchy(
      rootId = rootId,
      backupsId = backupsId,
      reportsId = reportsId,
      statisticsId = statsId,
    )
  }

  suspend fun uploadEncryptedPayload(
    accessToken: String,
    parentFolderId: String,
    fileName: String,
    encryptedBytes: ByteArray,
  ): DriveUploadOutcome {
    val auth = "Bearer $accessToken"
    val metadataJson = """{"name":"$fileName","parents":["$parentFolderId"],"mimeType":"$ENCRYPTED_MIME"}"""
    val metaPart = MultipartBody.Part.createFormData(
      "metadata",
      "metadata.json",
      metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()),
    )
    val mediaPart = MultipartBody.Part.createFormData(
      "file",
      fileName,
      encryptedBytes.toRequestBody(ENCRYPTED_MIME.toMediaType()),
    )

    val response = runCatching { api.uploadMultipart(auth, metaPart, mediaPart) }.getOrElse { ex ->
      return DriveUploadOutcome.Failure("NETWORK_IO", ex.localizedMessage ?: "Interrupted upload")
    }

    if (response.code() == 401 || response.code() == 403) {
      return DriveUploadOutcome.TokenExpired(response.code())
    }
    if (!response.isSuccessful) {
      return DriveUploadOutcome.Failure("HTTP_${response.code()}", "Drive rejected upload (${response.code()})")
    }

    val bodyStr = response.body()?.string().orEmpty()
    val parsed = runCatching { json.decodeFromString<DriveFileItem>(bodyStr) }.getOrNull()
      ?: return DriveUploadOutcome.Failure("INVALID_RESPONSE", "Could not parse Drive upload response")

    val reportedSize = parsed.size?.toLongOrNull() ?: encryptedBytes.size.toLong()
    if (reportedSize != encryptedBytes.size.toLong()) {
      return DriveUploadOutcome.Failure(
        "SIZE_MISMATCH",
        "Uploaded size mismatch (expected ${encryptedBytes.size}, remote $reportedSize)",
      )
    }

    return DriveUploadOutcome.Success(
      fileId = parsed.id,
      remoteName = parsed.name,
      remoteSizeBytes = reportedSize,
    )
  }

  suspend fun listBackups(accessToken: String, backupsFolderId: String): Result<List<DriveFileItem>> = runCatching {
    val auth = "Bearer $accessToken"
    val q = "'$backupsFolderId' in parents and trashed = false"
    val response = api.listFiles(auth, q)
    if (!response.isSuccessful) error("HTTP_${response.code()}")
    val raw = response.body()?.string().orEmpty()
    if (raw.isBlank()) emptyList() else json.decodeFromString<DriveFileListResponse>(raw).files
  }

  suspend fun downloadFileBytes(accessToken: String, fileId: String): Result<ByteArray> = runCatching {
    val auth = "Bearer $accessToken"
    val response = api.downloadFile(auth, fileId)
    if (!response.isSuccessful) error("HTTP_${response.code()}")
    response.body()?.bytes() ?: error("EMPTY_BODY")
  }

  private suspend fun findOrCreateFolder(
    bearerAuth: String,
    name: String,
    parentId: String?,
  ): String {
    val parentFilter = if (parentId != null) " and '$parentId' in parents" else ""
    val query = "name = '$name' and mimeType = '$FOLDER_MIME' and trashed = false$parentFilter"
    val listResp = api.listFiles(bearerAuth, query)
    if (listResp.code() == 401 || listResp.code() == 403) {
      throw IllegalStateException("TOKEN_EXPIRED_${listResp.code()}")
    }
    val rawList = listResp.body()?.string().orEmpty()
    val existing = if (rawList.isNotBlank()) {
      runCatching { json.decodeFromString<DriveFileListResponse>(rawList).files.firstOrNull() }.getOrNull()
    } else {
      null
    }
    if (existing != null) return existing.id

    val parentsPart = if (parentId != null) ""","parents":["$parentId"]""" else ""
    val body = """{"name":"$name","mimeType":"$FOLDER_MIME"$parentsPart}"""
      .toRequestBody("application/json; charset=UTF-8".toMediaType())
    val createResp = api.createMetadataOnly(bearerAuth, body)
    if (!createResp.isSuccessful) {
      throw IllegalStateException("CREATE_FOLDER_FAILED_${createResp.code()}")
    }
    val created = json.decodeFromString<DriveFileItem>(createResp.body()?.string().orEmpty())
    return created.id
  }
}
