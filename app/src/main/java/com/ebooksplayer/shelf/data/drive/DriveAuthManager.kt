package com.ebooksplayer.shelf.data.drive

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"

/**
 * Requests a Drive access token via Play Services' AuthorizationClient - the
 * current (2026) recommended way to authorize scoped access to a Google
 * service on Android, distinct from (and not requiring) a Credential
 * Manager sign-in, since this app never needs to know who the user is.
 *
 * authorize() may need to show the user a consent screen; when it does,
 * [consentRequest] emits the IntentSender the UI layer must launch via
 * ActivityResultContracts.StartIntentSenderForResult, then feed the result
 * back through [onConsentResult] to resume the suspended call.
 */
@Singleton
class DriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val authorizationClient = Identity.getAuthorizationClient(context)

    private val _consentRequest = MutableStateFlow<IntentSender?>(null)
    val consentRequest: StateFlow<IntentSender?> = _consentRequest.asStateFlow()

    private var pendingConsent: CompletableDeferred<Intent?>? = null

    suspend fun ensureAccessToken(): String? {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_READONLY_SCOPE)))
            .build()

        val result = authorizationClient.authorize(request).await()
        if (!result.hasResolution()) {
            return result.accessToken
        }

        val pendingIntent = result.pendingIntent ?: return null
        val deferred = CompletableDeferred<Intent?>()
        pendingConsent = deferred
        _consentRequest.value = pendingIntent.intentSender

        val resultIntent = deferred.await()
        _consentRequest.value = null
        val data = resultIntent ?: return null

        return runCatching {
            authorizationClient.getAuthorizationResultFromIntent(data).accessToken
        }.getOrNull()
    }

    fun onConsentResult(intent: Intent?) {
        pendingConsent?.complete(intent)
        pendingConsent = null
    }

    fun cancelConsent() {
        pendingConsent?.complete(null)
        pendingConsent = null
        _consentRequest.value = null
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
}
