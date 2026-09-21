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
 * Only mathematically derived auth hashes are sent to Supabase Auth.
 */
object SupabaseAuthManager {

    private const val TAG = "SupabaseAuthManager"

    data class AuthResponse(
        val success: Boolean,
        val userId: String?,
        val email: String?,
        val message: String? = null
    )

    /**
     * Signs up a new user account on Supabase with Zero-Knowledge keys.
     */
    suspend fun signUp(
        context: Context,
        email: String,
        masterPassword: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        if (email.isBlank() || masterPassword.length < 6) {
            return@withContext Result.failure(Exception("Please provide a valid email and master password (min 6 chars)."))
        }

        try {
            val secureRandom = java.security.SecureRandom()
            val randomSalt = ByteArray(16).also { secureRandom.nextBytes(it) }
            val randomSaltB64 = android.util.Base64.encodeToString(randomSalt, android.util.Base64.NO_WRAP)

            val derived = SupabaseKeyManager.deriveKeys(email, masterPassword.toCharArray())
            val vaultDataKey = SupabaseKeyManager.deriveDataKeyWithSalt(masterPassword.toCharArray(), randomSalt)

            val body = JSONObject().apply {
                put("email", email.trim().lowercase(java.util.Locale.ROOT))
                put("password", derived.authPassword)
                put("data", JSONObject().apply {
                    put("vault_salt", randomSaltB64)
                })
            }

            val conn = (URL(SupabaseConfig.AUTH_SIGNUP).openConnection() as HttpsURLConnection).apply {
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
                val refreshToken = json.optString("refresh_token", null)
                val expiresIn = json.optLong("expires_in", 3600L)

                if (accessToken.isNotBlank()) {
                    SupabaseKeyManager.saveSession(
                        context = context,
                        userId = userId,
                        email = email,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresInSeconds = expiresIn,
                        dataEncryptionKey = vaultDataKey,
                        userSalt = randomSalt
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
                val errJson = try { JSONObject(respText) } catch (_: Exception) { null }
                val errorMsg = errJson?.optString("msg")
                    ?: errJson?.optString("message")
                    ?: errJson?.optString("error_description")
                    ?: "Registration failed (HTTP $responseCode)"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignUp error", e)
            Result.failure(e)
        }
    }

    /**
     * Signs in an existing user and initializes the local Data Encryption Key.
     */
    suspend fun signIn(
        context: Context,
        email: String,
        masterPassword: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        if (email.isBlank() || masterPassword.isBlank()) {
            return@withContext Result.failure(Exception("Please enter email and master password."))
        }

        try {
            val derived = SupabaseKeyManager.deriveKeys(email, masterPassword.toCharArray())
            val body = JSONObject().apply {
                put("email", email.trim().lowercase(java.util.Locale.ROOT))
                put("password", derived.authPassword)
            }

            val conn = (URL(SupabaseConfig.AUTH_TOKEN).openConnection() as HttpsURLConnection).apply {
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
                val refreshToken = json.optString("refresh_token", null)
                val expiresIn = json.optLong("expires_in", 3600L)
                val userObj = json.getJSONObject("user")
                val userId = userObj.getString("id")

                val userMetadata = userObj.optJSONObject("user_metadata")
                val vaultSaltB64 = userMetadata?.optString("vault_salt", null)
                val (finalDataKey, accountSalt) = if (!vaultSaltB64.isNullOrBlank()) {
                    val s = android.util.Base64.decode(vaultSaltB64, android.util.Base64.NO_WRAP)
                    Pair(SupabaseKeyManager.deriveDataKeyWithSalt(masterPassword.toCharArray(), s), s)
                } else {
                    val cachedSalt = SupabaseKeyManager.getUserSalt(context)
                    if (cachedSalt != null) {
                        Pair(SupabaseKeyManager.deriveDataKeyWithSalt(masterPassword.toCharArray(), cachedSalt), cachedSalt)
                    } else {
                        Pair(derived.dataEncryptionKey, null)
                    }
                }

                SupabaseKeyManager.saveSession(
                    context = context,
                    userId = userId,
                    email = email,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSeconds = expiresIn,
                    dataEncryptionKey = finalDataKey,
                    userSalt = accountSalt
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
                val errJson = try { JSONObject(respText) } catch (_: Exception) { null }
                val errorMsg = errJson?.optString("error_description")
                    ?: errJson?.optString("message")
                    ?: errJson?.optString("msg")
                    ?: "Invalid credentials (HTTP $responseCode)"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "SignIn error", e)
            Result.failure(e)
        }
    }

    /**
     * Refreshes the session using the stored refresh token.
     */
    suspend fun refreshSession(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val refreshToken = SupabaseKeyManager.getRefreshToken(context)
            ?: return@withContext Result.failure(Exception("No refresh token available. Please sign in again."))

        try {
            val body = JSONObject().apply {
                put("refresh_token", refreshToken)
            }

            val conn = (URL(SupabaseConfig.AUTH_REFRESH).openConnection() as HttpsURLConnection).apply {
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
            try {
                val conn = (URL(SupabaseConfig.AUTH_LOGOUT).openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                    setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                }
                conn.responseCode // execute
            } catch (_: Exception) {}
        }
        SupabaseKeyManager.clearSession(context)
        Result.success(Unit)
    }
}
