/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.util.diagnostics.DiagnosticManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Strict Air-Gapped / Zero Network Security Audit Test Suite
 *
 * Guarantees that:
 * 1. The application requests ZERO network-related permissions (INTERNET, ACCESS_NETWORK_STATE, etc.)
 *    so Android App Info displays no network access / mobile data & Wi-Fi permission toggles.
 * 2. DiagnosticManager executes safely under zero network permissions without SecurityException.
 */
@RunWith(RobolectricTestRunner::class)
class ZeroNetworkSecurityAuditTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun test_zeroNetworkPermissions_manifestHasNoInternetOrNetworkAccess() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()

        assertFalse(
            "INTERNET permission must not be present in the manifest",
            requestedPermissions.contains("android.permission.INTERNET")
        )
        assertFalse(
            "ACCESS_NETWORK_STATE permission must not be present in the manifest",
            requestedPermissions.contains("android.permission.ACCESS_NETWORK_STATE")
        )
        assertFalse(
            "ACCESS_WIFI_STATE permission must not be present in the manifest",
            requestedPermissions.contains("android.permission.ACCESS_WIFI_STATE")
        )
        assertFalse(
            "CHANGE_NETWORK_STATE permission must not be present in the manifest",
            requestedPermissions.contains("android.permission.CHANGE_NETWORK_STATE")
        )
        assertFalse(
            "CHANGE_WIFI_STATE permission must not be present in the manifest",
            requestedPermissions.contains("android.permission.CHANGE_WIFI_STATE")
        )
        assertFalse(
            "NEARBY_WIFI_DEVICES permission must not be present in the manifest",
            requestedPermissions.contains("android.permission.NEARBY_WIFI_DEVICES")
        )
    }

    @Test
    fun test_diagnosticManager_executesSafelyWithoutNetworkPermissions() {
        // DiagnosticManager should produce a telemetry environment summary without crashing
        val summary = DiagnosticManager.captureEnvironmentSummary(context)
        assertNotNull(summary)
        assertTrue("Summary should contain application telemetry header", summary.contains("AYVA / FOCUS"))
        assertTrue("Summary should contain network connection line", summary.contains("Network Connection :"))
    }
}
