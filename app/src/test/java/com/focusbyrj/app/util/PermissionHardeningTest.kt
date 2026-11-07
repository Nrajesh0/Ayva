package com.focusbyrj.app.util

import android.Manifest
import android.app.Application
import android.app.admin.DeviceAdminInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.focusbyrj.app.service.FocusDeviceAdminReceiver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 32, 34, 36], application = Application::class)
class PermissionHardeningTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val permission get() = "${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

    @Test fun manifestKeepsFeaturePermissionsAndRemovesUnusedOrOverbroadGrants() {
        val requested = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        listOf(Manifest.permission.CAMERA, "android.permission.USE_EXACT_ALARM", "android.permission.USE_FULL_SCREEN_INTENT",
            Manifest.permission.INTERNET, "android.permission.MANAGE_EXTERNAL_STORAGE").forEach { assertFalse(it, it in requested) }
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.SCHEDULE_EXACT_ALARM, Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.PACKAGE_USAGE_STATS, Manifest.permission.QUERY_ALL_PACKAGES, Manifest.permission.FOREGROUND_SERVICE,
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE", "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECEIVE_BOOT_COMPLETED, Manifest.permission.WAKE_LOCK,
            Manifest.permission.VIBRATE, Manifest.permission.HIDE_OVERLAY_WINDOWS, permission).forEach { assertTrue(it, it in requested) }
    }

    @Test fun optionalCaptureHardwareDoesNotExcludeDevicesThatCanUseOtherFeatures() {
        val features = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_CONFIGURATIONS).reqFeatures.orEmpty()
        for (name in listOf(PackageManager.FEATURE_CAMERA, PackageManager.FEATURE_MICROPHONE)) {
            val feature = features.single { it.name == name }
            assertEquals(name, 0, feature.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED)
        }
    }

    @Test fun internalBroadcastRegistrationRequiresOurSignatureAndStillDeliversOwnCommands() {
        val info = app.packageManager.getPermissionInfo(permission, 0)
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE, info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
        // Android grants an app its own signature permission at installation.
        shadowOf(app).grantPermissions(permission)
        var delivered = 0
        val action = "${app.packageName}.PRIVATE_PERMISSION_TEST"
        val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { delivered++ } }
        InternalBroadcasts.register(app, receiver, IntentFilter(action))
        try {
            val registration = shadowOf(app).registeredReceivers.single { it.broadcastReceiver === receiver }
            assertEquals(permission, registration.broadcastPermission)
            if (Build.VERSION.SDK_INT >= 33) {
                assertTrue(registration.flags and ContextCompat.RECEIVER_NOT_EXPORTED != 0)
                assertEquals(0, registration.flags and ContextCompat.RECEIVER_EXPORTED)
            }
            assertEquals(PackageManager.PERMISSION_DENIED, app.packageManager.checkPermission(permission, "untrusted.other.app"))
            app.sendBroadcast(Intent(action).setPackage(app.packageName))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, delivered)
        } finally { app.unregisterReceiver(receiver) }
    }

    @Test fun uninstallProtectionRequestsNoDeviceControlPolicies() {
        val receiver = app.packageManager.getReceiverInfo(ComponentName(app, FocusDeviceAdminReceiver::class.java), PackageManager.GET_META_DATA)
        assertEquals(Manifest.permission.BIND_DEVICE_ADMIN, receiver.permission)
        val info = DeviceAdminInfo(app, ResolveInfo().apply { activityInfo = receiver })
        listOf(DeviceAdminInfo.USES_POLICY_FORCE_LOCK, DeviceAdminInfo.USES_POLICY_WIPE_DATA,
            DeviceAdminInfo.USES_POLICY_RESET_PASSWORD, DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD,
            DeviceAdminInfo.USES_POLICY_WATCH_LOGIN, DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD,
            DeviceAdminInfo.USES_ENCRYPTED_STORAGE, DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA,
            DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES).forEach { assertFalse(info.usesPolicy(it)) }
    }
}
