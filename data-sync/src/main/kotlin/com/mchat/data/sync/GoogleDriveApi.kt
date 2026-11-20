package com.mchat.data.sync

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class DriveFileItem(
  val id: String,
  val name: String,
  val mimeType: String = "",
  val modifiedTime: String? = null,
  val size: String? = null,
  val md5Checksum: String? = null,
)

@Serializable
data class DriveFileListResponse(
  val files: List<DriveFileItem> = emptyList(),
)

interface GoogleDriveApi {
  @GET("drive/v3/files")
  suspend fun listFiles(
    @Header("Authorization") bearerAuth: String,
    @Query("q") query: String,
    @Query("fields") fields: String = "files(id,name,mimeType,modifiedTime,size,md5Checksum)",
    @Query("orderBy") orderBy: String = "modifiedTime desc",
    @Query("spaces") spaces: String = "drive",
  ): Response<ResponseBody>

  @POST("drive/v3/files")
  suspend fun createMetadataOnly(
    @Header("Authorization") bearerAuth: String,
    @Body metadataJson: RequestBody,
  ): Response<ResponseBody>

  @Multipart
  @POST("upload/drive/v3/files?uploadType=multipart&fields=id,name,size,md5Checksum,modifiedTime")
  suspend fun uploadMultipart(
    @Header("Authorization") bearerAuth: String,
    @Part metadata: MultipartBody.Part,
    @Part media: MultipartBody.Part,
  ): Response<ResponseBody>

  @GET("drive/v3/files/{fileId}?alt=media")
  suspend fun downloadFile(
    @Header("Authorization") bearerAuth: String,
    @Path("fileId") fileId: String,
  ): Response<ResponseBody>
}

object GoogleDriveRetrofitFactory {
  /**
   * Builds a hardened OkHttp/Retrofit client with strictly NO body or header logging interceptors
   * so OAuth tokens and encrypted payloads are never written to logcat.
   */
  fun create(baseUrl: String = "https://www.googleapis.com/"): GoogleDriveApi {
    val client = OkHttpClient.Builder()
      .connectTimeout(25, TimeUnit.SECONDS)
      .readTimeout(45, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .build()

    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(client)
      .build()
      .create(GoogleDriveApi::class.java)
  }
}
