package com.focusbyrj.app.ui.components

import android.app.Application
import android.app.admin.DeviceAdminInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.service.FocusDeviceAdminReceiver
import com.focusbyrj.app.ui.screens.SecurityScreen
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceAdminPermissionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val manager get() = app.getSystemService(DevicePolicyManager::class.java)
    private val admin get() = ComponentName(app, FocusDeviceAdminReceiver::class.java)
    @Before fun setup() {
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN, true)
        com.focusbyrj.app.util.AppThemeManager.init(app)
    }
    @After fun cleanup() { android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
    private fun page() = compose.setContent { FocusByRjTheme { SecurityScreen(rememberNavController()) } }

    @Test fun legacyAuthorityIsExplainedAndOnlyDisabledByExplicitUserAction() {
        shadowOf(manager).setActiveAdmin(admin)
        shadowOf(manager).grantPolicy(admin, DeviceAdminInfo.USES_POLICY_FORCE_LOCK)
        page()
        compose.onNodeWithText("Earlier administrator access", substring = true).performScrollTo().assertIsDisplayed()
        assertTrue(manager.isAdminActive(admin))
        compose.onNodeWithText("Disable").performScrollTo().performClick()
        compose.waitUntil(5_000) { !manager.isAdminActive(admin) }
        compose.onNodeWithText("Enable").assertIsEnabled()
        compose.onNodeWithText("Earlier administrator access", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Enable").performClick()
        val intent = shadowOf(compose.activity).nextStartedActivityForResult.intent
        assertEquals(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, intent.action)
        assertEquals(admin, intent.getParcelableExtra<ComponentName>(DevicePolicyManager.EXTRA_DEVICE_ADMIN))
        assertFalse(manager.isAdminActive(admin)) // System consent is still required.
    }

    @Test fun unavailableAdminFeatureDoesNotPreventOtherSettingsFromOpening() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN, false)
        page()
        compose.onNodeWithText("Device-admin access is unavailable", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enable").assertIsNotEnabled()
        compose.onNodeWithText("Screenshot protection").assertExists()
    }

    @Test fun missingDevicePolicyServiceShowsUnavailableInsteadOfCrashing() {
        val context = object : android.content.ContextWrapper(compose.activity) {
            override fun getSystemService(name: String): Any? =
                if (name == android.content.Context.DEVICE_POLICY_SERVICE) null else super.getSystemService(name)
        }
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides context) {
                FocusByRjTheme { SecurityScreen(rememberNavController()) }
            }
        }
        compose.onNodeWithText("Device-admin access is unavailable", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enable").assertIsNotEnabled()
        compose.onNodeWithText("Screenshot protection").assertExists()
    }
}
