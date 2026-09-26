package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Manages Owner Authentication using Firebase Auth and Android Credential Manager (Google Sign-In).
 */
object AuthManager {

    private const val TAG = "AuthManager"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun isOwnerSignedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * Executes modern Credential Manager Google Sign-In flow and links with Firebase Auth.
     */
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        return try {
            val credentialManager = CredentialManager.create(context)

            val serverClientId = try {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) context.getString(resId) else "310586173790"
            } catch (_: Exception) {
                "310586173790"
            }

            val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request: GetCredentialRequest = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user
                if (user != null) {
                    _currentUser.value = user
                    Log.i(TAG, "Google sign-in successful: ${user.email} (${user.uid})")
                    Result.success(user)
                } else {
                    Result.failure(Exception("Firebase authentication returned null user"))
                }
            } else {
                Result.failure(Exception("Unsupported credential type: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google sign-in cancelled by user")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error during Google Sign-in", e)
            Result.failure(e)
        }
    }

    /**
     * Signs out the current owner and clears credential state.
     */
    suspend fun signOut(context: Context? = null) {
        try {
            auth.signOut()
            _currentUser.value = null
            if (context != null) {
                try {
                    val credentialManager = CredentialManager.create(context)
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Error clearing credential state", e)
                }
            }
            Log.i(TAG, "Owner signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
        }
    }
}
