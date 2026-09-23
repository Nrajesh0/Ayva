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

    /**
     * Signs up a new user account on Supabase with Zero-Knowledge keys.
     *
     * CHANGED: Now uses a single deriveKeys() call (Argon2id + HKDF) to get all three subkeys.
     * The vault_salt stored in Supabase user_metadata is required to re-derive the same keys on sign-in.
     * REMOVED: separate deriveDataKeyWithSalt() call (it was a separate PBKDF2 derivation).
     */
    suspend fun signUp(
        context: Context,
        email: String,
        masterPassword: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        if (email.isBlank() || masterPassword.length < 6) {
            return@withContext Result.failure(Exception("Please provide a valid email and master password (min 6 chars)."))
        }

        var derived: SupabaseKeyManager.DerivedKeys? = null
        var conn: HttpsURLConnection? = null
        try {
            // Single Argon2id derivation → HKDF fan-out to three domain-separated subkeys
            derived = SupabaseKeyManager.deriveKeys(email, masterPassword.toCharArray())

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
                        userSalt = null
                    )
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
        }
    }

    /**
     * Signs in an existing user and initializes the local Data Encryption Key and HMAC Key.
     *
     * CHANGED: Uses the vault_salt from Supabase user_metadata to re-derive all three HKDF subkeys
     * (authPassword, dataEncryptionKey, hmacKey) via a single Argon2id + HKDF call.
     * REMOVED: separate deriveDataKeyWithSalt() call.
     */
    suspend fun signIn(
        context: Context,
        email: String,
        masterPassword: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        if (email.isBlank() || masterPassword.isBlank()) {
            return@withContext Result.failure(Exception("Please enter email and master password."))
        }

        var derived: SupabaseKeyManager.DerivedKeys? = null
        var conn: HttpsURLConnection? = null
        try {
            derived = SupabaseKeyManager.deriveKeys(email, masterPassword.toCharArray())
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

                SupabaseKeyManager.saveSession(
                    context = context,
                    userId = userId,
                    email = email,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSeconds = expiresIn,
                    dataEncryptionKey = derived.dataEncryptionKey,
                    hmacKey = derived.hmacKey,
                    userSalt = null
                )

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
     */
    suspend fun refreshSession(context: Context): Result<String> = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            // Check if another concurrent thread already refreshed the token
            if (!SupabaseKeyManager.isTokenExpiring(context)) {
                val activeToken = SupabaseKeyManager.getSessionState(context).accessToken
                if (!activeToken.isNullOrBlank()) {
                    return@withLock Result.success(activeToken)
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
