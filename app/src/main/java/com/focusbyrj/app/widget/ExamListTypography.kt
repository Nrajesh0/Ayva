package com.focusbyrj.app.widget

import kotlin.math.ceil

/** One sizing policy shared by pagination and the actual list rows. */
internal data class ExamListTypography(
    val name: Int, val count: Int, val detail: Int, val unit: Int,
    val titleLines: Int, val dateLines: Int, val padding: Int, val compact: Boolean,
    val title: Boolean, val date: Boolean, val badge: Boolean, val venue: Boolean,
    val message: Boolean, val progress: Boolean, val percent: Boolean, val caption: Boolean
) {
    fun height(scale: Float): Int {
        fun line(size: Int, lines: Int = 1) = size * scale * 1.4f * lines
        val left = (if(title) line(name,titleLines) else 0f) + (if(date) line(detail,dateLines)+3 else 0f) +
            (if(badge) line(unit)+3 else 0f) + (if(venue) line(detail) else 0f) + (if(message) line(detail) else 0f)
        val right = line(count) + if(caption) line(unit) else 0f
        val total=padding*2 + (if(compact) left+right else maxOf(left,right)) + (if(progress) 10 else 0) + (if(percent) line(unit) else 0f)
        return ceil(total-.001f).toInt()
    }
    fun fit(budget: Int, scale: Float): ExamListTypography {
        var value=this
        if(value.height(scale)>budget) value=value.copy(message=false,venue=false,badge=false,percent=false,progress=false)
        if(value.height(scale)>budget) value=value.copy(date=false,titleLines=1,padding=2)
        if(value.height(scale)>budget) value=value.copy(caption=false)
        while(value.height(scale)>budget && (value.name>10 || value.count>12)) {
            value=value.copy(name=(value.name-1).coerceAtLeast(10),count=(value.count-1).coerceAtLeast(12),detail=(value.detail-1).coerceAtLeast(10),unit=(value.unit-1).coerceAtLeast(10))
        }
        if(value.height(scale)>budget) value=value.copy(title=false)
        return value
    }
    companion object {
        fun forWidget(config: ExamCountdown, height: Int, width: Int, scale: Float, events: List<ExamEvent>): ExamListTypography {
            val name=(config.listTextSize + if(height>=220) 2 else 0).coerceAtMost(22)
            val progress=config.showProgress && height>=280*scale
            return ExamListTypography(name,name+5,(name-2).coerceAtLeast(11),(name-4).coerceIn(11,14),
                if(height>=240) 2 else 1,if(config.dateStyle=="LONG" || config.showDate && config.showTime) 2 else 1,
                if(height<150) 4 else 8,width<240 || scale>=1.5f,
                config.showTitle,(config.showDate || config.showTime) && height>=150*scale,
                config.showBadge && events.any{it.badge.isNotBlank()} && height>=220*scale,
                config.showVenue && events.any{it.venue.isNotBlank()} && height>=320*scale,
                config.showMessage && events.any{it.message.isNotBlank()} && height>=360*scale,
                progress,progress && config.showPercent,height>=150*scale)
        }
    }
}
