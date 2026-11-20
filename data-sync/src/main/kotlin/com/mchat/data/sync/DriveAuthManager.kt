package com.mchat.data.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface DriveSignInResult {
  data class SignedIn(val email: String, val displayName: String?) : DriveSignInResult
  data class Error(val code: String, val message: String) : DriveSignInResult
}

sealed interface DriveAuthTokenResult {
  data class Authorized(val accessToken: String) : DriveAuthTokenResult
  data class NeedsUserConsent(val pendingIntent: PendingIntent) : DriveAuthTokenResult
  data class Error(val code: String, val message: String) : DriveAuthTokenResult
}

/**
 * Handles clinician Google Sign-In via Jetpack Credential Manager and Drive authorization via
 * Google Play Services AuthorizationClient using STRICTLY the least-privilege `drive.file` scope.
 */
@Singleton
class DriveAuthManager @Inject constructor(
  @ApplicationContext private val appContext: Context,
) {
  companion object {
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
  }

  @Volatile
  private var cachedAccessToken: String? = null

  fun getCachedAccessToken(): String? = cachedAccessToken

  fun clearCachedToken() {
    cachedAccessToken = null
  }

  suspend fun signInWithCredentialManager(
    activityContext: Context,
    webClientId: String,
  ): DriveSignInResult {
    if (webClientId.isBlank()) {
      return DriveSignInResult.Error(
        code = "MISSING_WEB_CLIENT_ID",
        message = "Configure your Google Cloud OAuth Client ID first (see Cloud Console Setup Guide below).",
      )
    }
    return runCatching {
      val credentialManager = CredentialManager.create(activityContext)
      val googleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
      val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleOption)
        .build()
      val response = credentialManager.getCredential(activityContext, request)
      val credential = response.credential
      if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
      ) {
        val parsed = GoogleIdTokenCredential.createFrom(credential.data)
        DriveSignInResult.SignedIn(email = parsed.id, displayName = parsed.displayName)
      } else {
        DriveSignInResult.Error("UNSUPPORTED_CREDENTIAL", "Unexpected credential type returned.")
      }
    }.getOrElse { ex ->
      DriveSignInResult.Error(
        code = "SIGN_IN_FAILED",
        message = ex.localizedMessage ?: "Google Sign-In cancelled or unavailable.",
      )
    }
  }

  suspend fun authorizeDriveFileScope(): DriveAuthTokenResult = suspendCancellableCoroutine { cont ->
    val request = AuthorizationRequest.builder()
      .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
      .build()

    Identity.getAuthorizationClient(appContext)
      .authorize(request)
      .addOnSuccessListener { result ->
        if (result.hasResolution() && result.pendingIntent != null) {
          cont.resume(DriveAuthTokenResult.NeedsUserConsent(result.pendingIntent!!))
        } else {
          val token = result.accessToken
          if (!token.isNullOrBlank()) {
            cachedAccessToken = token
            cont.resume(DriveAuthTokenResult.Authorized(token))
          } else {
            cont.resume(
              DriveAuthTokenResult.Error(
                code = "TOKEN_UNAVAILABLE",
                message = "Drive access token not granted. Re-authorize Drive access.",
              ),
            )
          }
        }
      }
      .addOnFailureListener { ex ->
        cont.resume(
          DriveAuthTokenResult.Error(
            code = "AUTH_ERROR",
            message = ex.localizedMessage ?: "Google Drive authorization failed.",
          ),
        )
      }
  }

  fun parseAuthorizationResultFromIntent(data: Intent?): DriveAuthTokenResult {
    return runCatching {
      val result = Identity.getAuthorizationClient(appContext).getAuthorizationResultFromIntent(data)
      val token = result.accessToken
      if (!token.isNullOrBlank()) {
        cachedAccessToken = token
        DriveAuthTokenResult.Authorized(token)
      } else {
        DriveAuthTokenResult.Error("TOKEN_EMPTY", "Authorization completed without an access token.")
      }
    }.getOrElse { ex ->
      DriveAuthTokenResult.Error("AUTH_INTENT_ERROR", ex.localizedMessage ?: "Failed to parse Drive authorization.")
    }
  }
}
