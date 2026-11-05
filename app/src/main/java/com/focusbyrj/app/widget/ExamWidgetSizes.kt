package com.focusbyrj.app.widget

import android.appwidget.AppWidgetManager as A
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.SizeF

/** Android 12 supplies size pairs. The old minimum-width/minimum-height pair may never exist. */
internal object ExamWidgetSizes {
    @Suppress("DEPRECATION")
    fun provided(options: Bundle): List<SizeF> = options.getParcelableArrayList<SizeF>(A.OPTION_APPWIDGET_SIZES).orEmpty()
        .filter { it.width.isFinite() && it.height.isFinite() && it.width in 1f..2000f && it.height in 1f..2000f }.distinct().take(16)
    fun current(context: Context, options: Bundle): SizeF {
        val landscape=context.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        fun positive(key:String,fallback:Int)=options.getInt(key,fallback).takeIf{it>0}?:fallback
        val minWidth=positive(A.OPTION_APPWIDGET_MIN_WIDTH,240)
        val minHeight=positive(A.OPTION_APPWIDGET_MIN_HEIGHT,180)
        val fallback=SizeF((if(landscape) positive(A.OPTION_APPWIDGET_MAX_WIDTH,minWidth) else minWidth).coerceIn(140,2000).toFloat(),
            (if(landscape) minHeight else positive(A.OPTION_APPWIDGET_MAX_HEIGHT,minHeight)).coerceIn(110,2000).toFloat())
        val selected=provided(options).minByOrNull { kotlin.math.abs(it.width-fallback.width)+kotlin.math.abs(it.height-fallback.height) } ?: fallback
        return SizeF(selected.width.coerceIn(140f,2000f),selected.height.coerceIn(110f,2000f))
    }
}
