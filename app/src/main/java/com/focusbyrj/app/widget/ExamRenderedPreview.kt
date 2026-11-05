package com.focusbyrj.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.focusbyrj.app.R
import java.time.ZoneId
import kotlin.math.roundToInt

internal fun ExamCountdown.safePreview(): ExamCountdown {
    fun zone(value:String)=runCatching{ZoneId.of(value.trim()).id}.getOrDefault(ZoneId.systemDefault().id)
    fun color(value:String,fallback:String)=value.trim().takeIf{it.matches(Regex("#[0-9a-fA-F]{6}"))}?:fallback
    return copy(zone=zone(zone),extraExams=extraExams.map{it.copy(zone=zone(it.zone))},background=color(background,"#151619"),
        foreground=color(foreground,"#F3F3F1"),accent=color(accent,"#B9C8DF"))
}

/** The card image is a scaled render of the production RemoteViews, not an illustration. */
internal fun examPreviewBitmap(context:Context,value:ExamCountdown,width:Int,height:Int,active:String,light:Boolean,now:java.time.Instant=java.time.Instant.now()):Bitmap {
    val density=context.resources.displayMetrics.density
    val w=(width*density).roundToInt().coerceAtLeast(1);val h=(height*density).roundToInt().coerceAtLeast(1)
    val host=FrameLayout(context)
    if(value.design in ExamCountdown.GLASS_DESIGNS) host.background=ExamPreviewWallpaper(light)
    host.addView(ExamCountdownProvider.render(context,-99,value,width,height,active,now).apply(context,host),FrameLayout.LayoutParams(-1,-1))
    host.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));host.layout(0,0,w,h)
    val scale=minOf(1f,512f/maxOf(w,h))
    val bitmap=Bitmap.createBitmap((w*scale).roundToInt().coerceAtLeast(1),(h*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
    val canvas=Canvas(bitmap);canvas.scale(scale,scale);host.draw(canvas);canvas.setBitmap(null)
    return bitmap
}

@Composable
internal fun ExamRenderedThumbnail(value:ExamCountdown,width:Int,height:Int,active:String,light:Boolean) {
    val context=LocalContext.current
    val config=LocalConfiguration.current
    val snapshotContext=remember(context,config.fontScale,config.uiMode,config.locales) {
        context.createConfigurationContext(Configuration(config).apply{densityDpi=240})
    }
    val safe=value.safePreview()
    val palette=ExamCountdownProvider.colors(context,safe).contentHashCode()
    val bitmap=remember(snapshotContext,safe,width,height,active,light,palette) {
        runCatching{examPreviewBitmap(snapshotContext,safe,width,height,active,light).asImageBitmap()}.getOrNull()
    }
    Box(Modifier.fillMaxWidth().height(100.dp),contentAlignment=Alignment.Center) {
        if(bitmap!=null) Image(bitmap,null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        else Text("Complete exam details",style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun CountdownEditorPreview(draft:ExamCountdown,width:Int,height:Int,mode:String,actual:Boolean,
    selectMode:(String)->Unit,active:String,selectActive:(String)->Unit,light:Boolean,selectLight:(Boolean)->Unit) {
    val context=LocalContext.current
    val safe=draft.safePreview()
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text("Live preview",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            (if(actual) listOf("Actual","Wide","Square","Tall") else listOf("Wide","Square","Tall")).forEach { title ->
                FilterChip(mode==title,onClick={selectMode(title)},label={Text(title,style=MaterialTheme.typography.labelSmall)})
            }
        }
    }
    if(draft.design in ExamCountdown.GLASS_DESIGNS) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text("Sample wallpaper",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(!light,onClick={selectLight(false)},label={Text("Dark",style=MaterialTheme.typography.labelSmall)})
            FilterChip(light,onClick={selectLight(true)},label={Text("Light",style=MaterialTheme.typography.labelSmall)})
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        val scale=minOf(1f,maxWidth.value/width)
        AndroidView(factory={FrameLayout(it)},modifier=Modifier.width((width*scale).dp).height((height*scale).dp).testTag("exam_preview"),update={host ->
            host.removeAllViews()
            host.background=if(draft.design in ExamCountdown.GLASS_DESIGNS) ExamPreviewWallpaper(light) else null
            runCatching {
                val view=ExamCountdownProvider.render(context,-99,safe,width,height,active).apply(context,host)
                val density=context.resources.displayMetrics.density
                view.pivotX=0f;view.pivotY=0f;view.scaleX=scale;view.scaleY=scale
                host.addView(view,FrameLayout.LayoutParams((width*density).roundToInt(),(height*density).roundToInt()))
                val page=ExamWidgetPresentation.page(safe,active,height,fontScale=context.resources.configuration.fontScale,width=width)
                view.findViewById<View>(R.id.exam_next)?.setOnClickListener{selectActive(page.adjacentId(true))}
                view.findViewById<View>(R.id.exam_previous)?.setOnClickListener{selectActive(page.adjacentId(false))}
                view.findViewById<View>(R.id.exam_root)?.setOnClickListener(null)
                view.findViewById<View>(R.id.exam_edit)?.setOnClickListener(null)
            }.onFailure{host.addView(android.widget.TextView(context).apply{text="Complete the exam details to preview."})}
        })
    }
}
