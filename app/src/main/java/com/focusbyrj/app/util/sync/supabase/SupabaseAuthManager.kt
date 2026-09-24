/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.focusbyrj.app.util.sync.supabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection

/**
 * Handles Supabase Zero-Knowledge Authentication (Sign Up, Sign In, Sign Out).
 *
 * Security Principle:
 * Raw user master passwords NEVER touch the network.
 * Only the HKDF-derived authPassword token is sent to Supabase Auth.
 *
 * Pattern 1 key derivation flow:
 *   Argon2id(password, randomSalt) → masterKey
 *   HKDF-Expand(masterKey, "ayva_auth_v1")  → authPassword  (sent to server)
 *   HKDF-Expand(masterKey, "ayva_vault_v1") → dataEncryptionKey (never leaves device)
 *   HKDF-Expand(masterKey, "ayva_hmac_v1")  → hmacKey (never leaves device)
 */
object SupabaseAuthManager {

    private const val TAG = "SupabaseAuthManager"
    private val refreshMutex = Mutex()

    data class AuthResponse(
        val success: Boolean,
        val userId: String?,
        val email: String?,
        val message: String? = null
    )

    // ---- Private helpers for per-account random vault salt management (B2-P3-002) ----

    /**
     * Generates a 32-byte cryptographically random per-account vault salt.
     * Used only at sign-up time; stored locally and pushed to Supabase user_metadata.
     */
    private fun generateRandomVaultSalt(): ByteArray =
        ByteArray(32).also { SecureRandom().nextBytes(it) }

    /**
     * Stores [vaultSalt] in Supabase user_metadata so it can be recovered from any device.
     * Failure is non-fatal: local prefs already hold the salt for the current device.
     */
    private fun updateVaultSaltInMetadata(accessToken: String, vaultSalt: ByteArray): Boolean {
        var conn: HttpsURLConnection? = null
        return try {
            val saltB64 = android.util.Base64.encodeToString(vaultSalt, android.util.Base64.NO_WRAP)
            val body = JSONObject().apply {
                put("data", JSONObject().apply { put("vault_salt", saltB64) })
            }
            conn = (URL(SupabaseConfig.AUTH_USER).openConnection() as HttpsURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist vault_salt in Supabase user metadata: ${e.message}")
            false
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Fetches the [vaultSalt] stored in Supabase user_metadata.
     * Returns null for legacy accounts that were created before the random-salt migration
     * (those accounts use the deterministic email-based salt as fallback).
     */
    private fun fetchVaultSaltFromMetadata(accessToken: String): ByteArray? {
        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(SupabaseConfig.AUTH_USER).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) return null
            val json = JSONObject(BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() })
            val saltB64 = json.optJSONObject("user_metadata")?.optString("vault_salt")?.takeIf { it.isNotBlank() }
                ?: return null
            android.util.Base64.decode(saltB64, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.d(TAG, "fetchVaultSalt: ${e.message}")
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
    /**
     * Signs up a new user account on Supabase with Zero-Knowledge keys.
     *
     * B2-P3-002 FIX: Generates a cryptographically random 32-byte [vaultSalt] at sign-up.
     * The vaultSalt is mixed into the DEK and HMAC HKDF info strings (not auth), making
     * both keys unique per account and defeating pre-computed dictionary attacks.
     * The salt is stored locally in KEY_USER_SALT and pushed to Supabase user_metadata
     * for cross-device recovery.
     *
     * B2-P3-003 NOTE: [masterPassword] should ideally be a CharArray from the UI layer to
     * prevent JVM String interning from keeping the password in heap. Full fix requires UI changes.
     * TODO: Change callers (SignUpViewModel / etc.) to pass CharArray directly.
     */
    suspend fun signUp(
        context: Context,
        email: String,
        masterPassword: String
    ): Result<AuthResponse> {
        val chars = masterPassword.toCharArray()
        try {
            return signUp(context, email, chars)
        } finally {
            java.util.Arrays.fill(chars, '\u0000')
        }
    }

    suspend fun signUp(
        context: Context,
        email: String,
        masterPasswordChars: CharArray
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        if (email.isBlank() || masterPasswordChars.size < 6) {
            return@withContext Result.failure(Exception("Please provide a valid email and master password (min 6 chars)."))
        }

        var derived: SupabaseKeyManager.DerivedKeys? = null
        var vaultSalt: ByteArray? = null
        var conn: HttpsURLConnection? = null
        try {
            // B2-P3-002: Generate a per-account random vault salt for DEK/HMAC uniqueness.
            // Auth password derivation in deriveKeys() is unaffected (deterministic by design).
            vaultSalt = generateRandomVaultSalt()
            derived = SupabaseKeyManager.deriveKeys(email, masterPasswordChars, vaultSalt = vaultSalt)

            val body = JSONObject().apply {
                put("email", email.trim().lowercase(java.util.Locale.ROOT))
                put("password", derived.authPassword)
            }

            conn = (URL(SupabaseConfig.AUTH_SIGNUP).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val responseCode = conn.responseCode
            val responseStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val respText = BufferedReader(InputStreamReader(responseStream)).use { it.readText() }

            if (responseCode in 200..299) {
                val json = JSONObject(respText)
                val userObj = json.optJSONObject("user") ?: json
                val userId = userObj.optString("id", "")
                val accessToken = json.optString("access_token", "")
                val refreshToken = if (json.isNull("refresh_token")) null else json.optString("refresh_token").takeIf { it.isNotBlank() }
                val expiresIn = json.optLong("expires_in", 3600L)

                if (accessToken.isNotBlank()) {
                    SupabaseKeyManager.saveSession(
                        context = context,
                        userId = userId,
                        email = email,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresInSeconds = expiresIn,
                        dataEncryptionKey = derived.dataEncryptionKey,
                        hmacKey = derived.hmacKey,
                        userSalt = vaultSalt  // Persisted locally in KEY_USER_SALT
                    )
                    // Push vault_salt to Supabase user_metadata for cross-device recovery.
                    // Non-fatal: local KEY_USER_SALT already provides resilience on this device.
                    updateVaultSaltInMetadata(accessToken, vaultSalt)
                }

                Result.success(
                    AuthResponse(
                        success = true,
                        userId = userId,
                        email = email,
                        message = if (accessToken.isNotBlank()) "Account created and vault initialized!"
                                  else "Confirmation email sent! Please verify your email to log in."
                    )
                )
            } else {
                val errJson = try { JSONObject(respText) } catch (e: Exception) { null }
                val errorMsg = errJson?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: errJson?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: errJson?.optString("error_description")?.takeIf { it.isNotBlank() }
                    ?: "Registration failed (HTTP $responseCode)"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignUp error", e)
            Result.failure(e)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
            derived?.let {
                java.util.Arrays.fill(it.dataEncryptionKey, 0.toByte())
                java.util.Arrays.fill(it.hmacKey, 0.toByte())
            }
            vaultSalt?.let { java.util.Arrays.fill(it, 0.toByte()) }
        }
    }

    /**
     * Signs in an existing user and initializes the local Data Encryption Key and HMAC Key.
     *
     * B2-P3-002 FIX: Auth password is always derived with the deterministic email-based salt
     * so sign-in works after app reinstall. After a successful auth handshake:
     *  1. If KEY_USER_SALT exists locally → re-derive DEK+HMAC immediately (single extra HKDF call).
     *  2. Otherwise → fetch vault_salt from Supabase user_metadata (cross-device restore).
     *  3. If no vault_salt anywhere → legacy account; use deterministic keys as-is.
     *
     * B2-P3-003 NOTE: [masterPassword] should ideally be a CharArray. Full fix requires UI changes.
     * TODO: Change callers (SignInViewModel / etc.) to pass CharArray directly.
     */
    suspend fun signIn(
        context: Context,
        email: String,
        masterPassword: String
    ): Result<AuthResponse> {
        val chars = masterPassword.toCharArray()
        try {
            return signIn(context, email, chars)
        } finally {
            java.util.Arrays.fill(chars, '\u0000')
        }
    }

    suspend fun signIn(
        context: Context,
        email: String,
        masterPasswordChars: CharArray
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        if (email.isBlank() || masterPasswordChars.isEmpty()) {
            return@withContext Result.failure(Exception("Please enter email and master password."))
        }

        var derived: SupabaseKeyManager.DerivedKeys? = null
        var conn: HttpsURLConnection? = null
        try {
            // Check for locally stored vault_salt first (fast path — no server round-trip needed).
            // Auth password is deterministic and does NOT use vaultSalt, so we can always authenticate.
            val localVaultSalt = SupabaseKeyManager.getUserSalt(context)
            derived = SupabaseKeyManager.deriveKeys(email, masterPasswordChars, vaultSalt = localVaultSalt)

            val body = JSONObject().apply {
                put("email", email.trim().lowercase(java.util.Locale.ROOT))
                put("password", derived.authPassword)
            }

            conn = (URL(SupabaseConfig.AUTH_TOKEN).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val responseCode = conn.responseCode
            val responseStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val respText = BufferedReader(InputStreamReader(responseStream)).use { it.readText() }

            if (responseCode in 200..299) {
                val json = JSONObject(respText)
                val accessToken = json.getString("access_token")
                val refreshToken = if (json.isNull("refresh_token")) null else json.optString("refresh_token").takeIf { it.isNotBlank() }
                val expiresIn = json.optLong("expires_in", 3600L)
                val userObj = json.getJSONObject("user")
                val userId = userObj.getString("id")

                // B2-P3-002: Resolve the effective vault salt for DEK + HMAC.
                // Priority: (1) local prefs, (2) Supabase user_metadata, (3) null (legacy account).
                val effectiveVaultSalt: ByteArray? = when {
                    localVaultSalt != null -> localVaultSalt  // already used in derivation above
                    else -> fetchVaultSaltFromMetadata(accessToken) // cross-device restore path
                }

                val finalDerived = if (effectiveVaultSalt != null && localVaultSalt == null) {
                    // Server had a vault_salt that was absent locally (e.g. after reinstall).
                    // Re-derive DEK + HMAC using the recovered salt.
                    // Auth password is unaffected — deterministic derivation already used above.
                    SupabaseKeyManager.deriveKeys(email, masterPasswordChars, vaultSalt = effectiveVaultSalt)
                } else {
                    // Local salt was already incorporated, or this is a legacy account (null).
                    derived
                }

                SupabaseKeyManager.saveSession(
                    context = context,
                    userId = userId,
                    email = email,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSeconds = expiresIn,
                    dataEncryptionKey = finalDerived.dataEncryptionKey,
                    hmacKey = finalDerived.hmacKey,
                    userSalt = effectiveVaultSalt  // Persist locally if recovered from server
                )

                // Clean up re-derived keys if they differ from the original derived object
                if (finalDerived !== derived) {
                    java.util.Arrays.fill(finalDerived.dataEncryptionKey, 0.toByte())
                    java.util.Arrays.fill(finalDerived.hmacKey, 0.toByte())
                }

                Result.success(
                    AuthResponse(
                        success = true,
                        userId = userId,
                        email = email,
                        message = "Welcome back! Vault connected."
                    )
                )
            } else {
                val errJson = try { JSONObject(respText) } catch (e: Exception) { null }
                val errorMsg = errJson?.optString("error_description")?.takeIf { it.isNotBlank() }
                    ?: errJson?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: errJson?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: "Invalid email or password (HTTP $responseCode)"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignIn error", e)
            Result.failure(e)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
            derived?.let {
                java.util.Arrays.fill(it.dataEncryptionKey, 0.toByte())
                java.util.Arrays.fill(it.hmacKey, 0.toByte())
            }
        }
    }

    /**
     * Refreshes the session using the stored refresh token.
     * Uses Mutex to ensure single-flight refresh and prevent token collision on refresh token rotation.
     * If [force] is true, bypasses the token expiry check and executes a network refresh.
     */
    suspend fun refreshSession(context: Context, force: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        val initialToken = SupabaseKeyManager.getSessionState(context).accessToken
        refreshMutex.withLock {
            val currentToken = SupabaseKeyManager.getSessionState(context).accessToken
            // If another concurrent thread already refreshed the token while waiting for lock, return it immediately to prevent RTR race
            if (!currentToken.isNullOrBlank() && currentToken != initialToken) {
                Log.d(TAG, "Token was already refreshed by concurrent caller, skipping network refresh to prevent RTR race.")
                return@withLock Result.success(currentToken)
            }

            // Check if another concurrent thread already refreshed the token (unless forcing refresh)
            if (!force && !SupabaseKeyManager.isTokenExpiring(context)) {
                if (!currentToken.isNullOrBlank()) {
                    return@withLock Result.success(currentToken)
                }
            }

            val refreshToken = SupabaseKeyManager.getRefreshToken(context)
                ?: return@withLock Result.failure(Exception("No refresh token available. Please sign in again."))

            var conn: HttpsURLConnection? = null
            try {
                val body = JSONObject().apply {
                    put("refresh_token", refreshToken)
                }

                conn = (URL(SupabaseConfig.AUTH_REFRESH).openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val responseCode = conn.responseCode
                val responseStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val respText = BufferedReader(InputStreamReader(responseStream)).use { it.readText() }

                if (responseCode in 200..299) {
                    val json = JSONObject(respText)
                    val newAccessToken = json.getString("access_token")
                    val newRefreshToken = json.optString("refresh_token", refreshToken)
                    val expiresIn = json.optLong("expires_in", 3600L)

                    SupabaseKeyManager.updateAccessToken(
                        context = context,
                        newAccessToken = newAccessToken,
                        newRefreshToken = newRefreshToken,
                        expiresInSeconds = expiresIn
                    )
                    Log.d(TAG, "Successfully refreshed Supabase session token.")
                    Result.success(newAccessToken)
                } else {
                    val errJson = try { JSONObject(respText) } catch (_: Exception) { null }
                    val msg = errJson?.optString("error_description")
                        ?: errJson?.optString("message")
                        ?: "Token refresh failed (HTTP $responseCode)"
                    Log.e(TAG, "Refresh session failed: $msg")
                    Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh session network error", e)
                Result.failure(e)
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Returns a valid access token, automatically refreshing it if it has expired.
     */
    suspend fun getValidAccessToken(context: Context): String? {
        val session = SupabaseKeyManager.getSessionState(context)
        if (!session.isSignedIn || session.accessToken.isNullOrBlank()) return null

        if (SupabaseKeyManager.isTokenExpiring(context)) {
            val refreshResult = refreshSession(context)
            if (refreshResult.isSuccess) {
                return refreshResult.getOrNull()
            }
        }
        return session.accessToken
    }

    /**
     * Signs out the current user and purges all cryptographic keys from memory.
     */
    suspend fun signOut(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        val session = SupabaseKeyManager.getSessionState(context)
        if (!session.accessToken.isNullOrBlank()) {
            var conn: HttpsURLConnection? = null
            try {
                conn = (URL(SupabaseConfig.AUTH_LOGOUT).openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                    setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                }
                conn.responseCode // execute
            } catch (_: Exception) {
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
        SupabaseKeyManager.clearSession(context)
        AutoSyncManager.resetState()
        Result.success(Unit)
    }
}
