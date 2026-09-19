/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.focusbyrj.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppIconOption(
    val id: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val previewRes: Int,
    val aliasName: String // full class component name or relative
)

object AppIconManager {
    private const val PREFS_NAME = "focus_prefs"
    private const val KEY_APP_ICON = "selected_app_icon_id"
    private const val DEFAULT_ICON_ID = "default"

    val iconOptions = listOf(
        AppIconOption(
            id = "default",
            title = "Default Focus",
            subtitle = "Classic Minimalist Emblem",
            previewRes = R.drawable.ic_app_logo,
            aliasName = "com.focusbyrj.app.MainActivity"
        ),
        AppIconOption(
            id = "wanderer",
            title = "The Wanderer",
            subtitle = "Tier 1 • Explorer",
            previewRes = R.drawable.avatar_wanderer,
            aliasName = "com.focusbyrj.app.MainActivityAliasWanderer"
        ),
        AppIconOption(
            id = "scholar",
            title = "The Scholar",
            subtitle = "Tier 2 • Knowledge",
            previewRes = R.drawable.avatar_scholar,
            aliasName = "com.focusbyrj.app.MainActivityAliasScholar"
        ),
        AppIconOption(
            id = "knight",
            title = "The Knight",
            subtitle = "Tier 3 • Iron Will",
            previewRes = R.drawable.avatar_knight,
            aliasName = "com.focusbyrj.app.MainActivityAliasKnight"
        ),
        AppIconOption(
            id = "noble",
            title = "The Noble",
            subtitle = "Tier 4 • High Prestige",
            previewRes = R.drawable.avatar_noble,
            aliasName = "com.focusbyrj.app.MainActivityAliasNoble"
        ),
        AppIconOption(
            id = "emperor",
            title = "The Emperor",
            subtitle = "Tier 5 • Sovereign",
            previewRes = R.drawable.avatar_emperor,
            aliasName = "com.focusbyrj.app.MainActivityAliasEmperor"
        ),
        AppIconOption(
            id = "companion",
            title = "Companion",
            subtitle = "Warmth & Care",
            previewRes = R.drawable.avatar_companion,
            aliasName = "com.focusbyrj.app.MainActivityAliasCompanion"
        ),
        AppIconOption(
            id = "inferno",
            title = "Inferno",
            subtitle = "Blazing Will",
            previewRes = R.drawable.avatar_inferno,
            aliasName = "com.focusbyrj.app.MainActivityAliasInferno"
        ),
        AppIconOption(
            id = "champion",
            title = "Champion",
            subtitle = "Trophy Winner",
            previewRes = R.drawable.avatar_champion,
            aliasName = "com.focusbyrj.app.MainActivityAliasChampion"
        ),
        AppIconOption(
            id = "prestige",
            title = "Prestige",
            subtitle = "Elite Honors",
            previewRes = R.drawable.avatar_prestige,
            aliasName = "com.focusbyrj.app.MainActivityAliasPrestige"
        ),
        AppIconOption(
            id = "crown",
            title = "Crown",
            subtitle = "Imperial Gem",
            previewRes = R.drawable.avatar_crown,
            aliasName = "com.focusbyrj.app.MainActivityAliasCrown"
        ),
        AppIconOption(
            id = "phoenix",
            title = "Phoenix",
            subtitle = "Eternal Rebirth",
            previewRes = R.drawable.avatar_phoenix,
            aliasName = "com.focusbyrj.app.MainActivityAliasPhoenix"
        )
    )

    private val _currentIconFlow = MutableStateFlow(DEFAULT_ICON_ID)
    val currentIconFlow: StateFlow<String> = _currentIconFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_APP_ICON, DEFAULT_ICON_ID) ?: DEFAULT_ICON_ID
        _currentIconFlow.value = savedId
    }

    fun getCurrentIcon(context: Context): AppIconOption {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_APP_ICON, DEFAULT_ICON_ID) ?: DEFAULT_ICON_ID
        return iconOptions.find { it.id == id } ?: iconOptions.first()
    }

    fun setAppIcon(context: Context, iconId: String): Boolean {
        val targetOption = iconOptions.find { it.id == iconId } ?: return false
        val pm = context.packageManager
        val packageName = context.packageName

        try {
            // Enable the target component if not already enabled
            val targetComponent = ComponentName(packageName, targetOption.aliasName)
            if (pm.getComponentEnabledSetting(targetComponent) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            // Disable all other components if they are not already disabled
            iconOptions.filter { it.id != iconId }.forEach { option ->
                val comp = ComponentName(packageName, option.aliasName)
                val currentState = pm.getComponentEnabledSetting(comp)
                if (currentState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        comp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }

            // Save preference
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_APP_ICON, iconId)
                .apply()

            _currentIconFlow.value = iconId
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
