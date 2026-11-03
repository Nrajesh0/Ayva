package com.focusbyrj.app.util

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import com.focusbyrj.app.service.BubbleService

data class BubbleIconOption(val id: String, val title: String, @DrawableRes val previewRes: Int)

/** Bubble artwork shares the existing assets, but never changes launcher aliases. */
object BubbleIconManager {
    const val PREFS_NAME = "bubble_prefs"
    const val KEY_ICON = "bubble_icon_id"
    const val DEFAULT_ID = "default"

    val options: List<BubbleIconOption> = AppIconManager.iconOptions.map {
        BubbleIconOption(
            id = it.id,
            title = it.title,
            previewRes = it.previewRes
        )
    }

    fun option(id: String?): BubbleIconOption = options.firstOrNull { it.id == id } ?: options.first()

    @DrawableRes
    fun currentIconRes(context: Context): Int = option(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ICON, DEFAULT_ID)
    ).previewRes

    fun setIcon(context: Context, id: String): Boolean {
        if (options.none { it.id == id }) return false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ICON, id).apply()
        // Update an existing overlay only; choosing an icon must not start/resume the service.
        context.sendBroadcast(Intent(BubbleService.ACTION_ICON_CHANGED).setPackage(context.packageName))
        return true
    }
}
