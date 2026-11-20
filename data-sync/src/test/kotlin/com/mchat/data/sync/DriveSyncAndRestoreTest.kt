package com.mchat.data.sync

import com.mchat.core.security.backup.BackupCryptoEngine
import com.mchat.core.security.backup.BackupCryptoException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class DriveSyncAndRestoreTest {

  private class FakeDriveApi(
    private val uploadStatusCode: Int = 200,
    private val throwNetworkError: Boolean = false,
    private val tamperReportedSize: Boolean = false,
  ) : GoogleDriveApi {
    override suspend fun listFiles(
      bearerAuth: String,
      query: String,
      fields: String,
      orderBy: String,
      spaces: String,
    ): Response<ResponseBody> = Response.success(
      """{"files":[]}""".toResponseBody("application/json".toMediaType()),
    )

    override suspend fun createMetadataOnly(
      bearerAuth: String,
      metadataJson: RequestBody,
    ): Response<ResponseBody> = Response.success(
      """{"id":"folder_1","name":"M-CHAT Medical Records","mimeType":"application/vnd.google-apps.folder"}"""
        .toResponseBody("application/json".toMediaType()),
    )

    override suspend fun uploadMultipart(
      bearerAuth: String,
      metadata: MultipartBody.Part,
      media: MultipartBody.Part,
    ): Response<ResponseBody> {
      if (throwNetworkError) throw IOException("Airplane mode / interrupted upload")
      if (uploadStatusCode != 200) {
        return Response.error(uploadStatusCode, "error".toResponseBody("text/plain".toMediaType()))
      }
      val reportedSize = if (tamperReportedSize) 1 else media.body.contentLength()
      return Response.success(
        """{"id":"file_99","name":"backup.mchatenc","size":"$reportedSize"}"""
          .toResponseBody("application/json".toMediaType()),
      )
    }

    override suspend fun downloadFile(
      bearerAuth: String,
      fileId: String,
    ): Response<ResponseBody> = Response.success(ByteArray(0).toResponseBody("application/octet-stream".toMediaType()))
  }

  @Test
  fun upload_handles_tokenExpiry_401_cleanly() = runTest {
    val manager = DriveFolderManager(FakeDriveApi(uploadStatusCode = 401))
    val outcome = manager.uploadEncryptedPayload("expired", "folder_1", "test.mchatenc", byteArrayOf(1, 2, 3))
    assertTrue(outcome is DriveUploadOutcome.TokenExpired)
    assertEquals(401, (outcome as DriveUploadOutcome.TokenExpired).httpCode)
  }

  @Test
  fun upload_handles_airplaneMode_interruptedNetwork() = runTest {
    val manager = DriveFolderManager(FakeDriveApi(throwNetworkError = true))
    val outcome = manager.uploadEncryptedPayload("tok", "folder_1", "test.mchatenc", byteArrayOf(1, 2, 3))
    assertTrue(outcome is DriveUploadOutcome.Failure)
    assertEquals("NETWORK_IO", (outcome as DriveUploadOutcome.Failure).code)
  }

  @Test
  fun upload_detects_remoteSizeMismatch_onTruncatedUpload() = runTest {
    val manager = DriveFolderManager(FakeDriveApi(tamperReportedSize = true))
    val outcome = manager.uploadEncryptedPayload("tok", "folder_1", "test.mchatenc", ByteArray(64) { 7 })
    assertTrue(outcome is DriveUploadOutcome.Failure)
    assertEquals("SIZE_MISMATCH", (outcome as DriveUploadOutcome.Failure).code)
  }

  @Test
  fun restore_rejects_corruptedBackup_and_wrongPassphrase() {
    val crypto = BackupCryptoEngine()
    val payload = """{"schemaVersion":1,"patients":[]}""".toByteArray(Charsets.UTF_8)
    val blob = crypto.encrypt(
      plaintext = payload,
      recoveryPassphrase = "CorrectPassphrase!2025",
      memoryKiB = 4096,
      iterations = 1,
    )

    // Corrupted byte fails SHA-256 pre-decryption check
    val corrupted = blob.copyOf().also { it[it.lastIndex - 5] = (it[it.lastIndex - 5].toInt() xor 0xFF).toByte() }
    val corruptThrown = runCatching {
      crypto.decrypt(corrupted, "CorrectPassphrase!2025")
    }.exceptionOrNull()
    assertTrue(corruptThrown is BackupCryptoException.CorruptedIntegrity)

    // Wrong passphrase fails GCM authentication
    val wrongThrown = runCatching {
      crypto.decrypt(blob, "WrongPassphrase!9999")
    }.exceptionOrNull()
    assertTrue(wrongThrown is BackupCryptoException.InvalidPassphrase)

    // Valid passphrase restores exact bytes
    val ok = crypto.decrypt(blob, "CorrectPassphrase!2025")
    assertEquals(payload.toString(Charsets.UTF_8), ok.toString(Charsets.UTF_8))
  }
}
