package com.focusbyrj.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.widget.RemoteViews
import com.focusbyrj.app.R

/** Resolve SP once in the app, before a launcher applies or recycles the RemoteViews. */
internal object NoteWidgetTypography {
    fun apply(context: Context, views: RemoteViews, viewId: Int, sizeSp: Float) {
        // Auto-size is disabled in the XML; its setter is not RemoteViews-safe.
        val pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, context.resources.displayMetrics)
        views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_PX, pixels)
    }

    fun applyHeader(context: Context, views: RemoteViews, config: NoteWidgetConfig) {
        apply(context, views, R.id.widget_note_title, (config.textSize.spValue + 2f).coerceAtLeast(14f))
        apply(context, views, R.id.widget_note_add_hint, (config.textSize.spValue - 2f).coerceAtLeast(12f))
        apply(context, views, R.id.widget_note_text_content, config.textSize.spValue)
        apply(context, views, R.id.widget_note_empty_title, config.textSize.spValue)
        apply(context, views, R.id.widget_note_empty_subtext, (config.textSize.spValue - 2f).coerceAtLeast(12f))
    }

    fun adapterIntent(context: Context, widgetId: Int, config: NoteWidgetConfig): Intent =
        Intent(context, NoteWidgetService::class.java).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // Extras do not participate in RemoteViewsService/launcher cache identity. A new
            // size or system font/display scale must bind a fresh collection, including on upgrade.
            data = Uri.Builder().scheme("widget").authority("note").appendPath(widgetId.toString())
                .appendQueryParameter("typography", "2")
                .appendQueryParameter("size", config.textSize.name)
                .appendQueryParameter("density", context.resources.configuration.densityDpi.toString())
                .appendQueryParameter("fontScale", context.resources.configuration.fontScale.toString())
                .build()
        }
}
