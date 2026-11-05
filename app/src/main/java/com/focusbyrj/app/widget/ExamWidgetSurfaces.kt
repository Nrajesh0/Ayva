package com.focusbyrj.app.widget

import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

/** Procedural widget surfaces. Glass is translucent; no wallpaper is read or blurred. */
internal object ExamWidgetSurfaces {
    fun draw(canvas: Canvas, w: Float, h: Float, config: ExamCountdown, colors: IntArray, alpha: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = config.radius * 2f
        fun fill(color: Int, opacity: Int) { paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color; paint.alpha = opacity.coerceIn(0,255) }
        fun glow(x: Float, y: Float, r: Float, color: Int, opacity: Int) {
            fill(color, opacity)
            paint.shader = RadialGradient(x, y, r.coerceAtLeast(1f), color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,w,h,paint)
            paint.shader = null
        }
        when (config.design) {
            "AURORA" -> {
                glow(w*.9f, h*.05f, w*.85f, colors[2], alpha*50/100)
                glow(w*.05f,h*1.2f,w*.65f,ColorUtils.blendARGB(colors[2],Color.rgb(76,223,212),.65f),alpha*40/100)
            }
            "BENTO" -> {
                if (config.displayMode == "LIST") return
                fill(colors[2],alpha)
                canvas.drawRoundRect(12f,12f,w*.45f-4f,h-12f,28f,28f,paint)
                fill(ColorUtils.blendARGB(colors[0],colors[2],.065f),alpha)
                canvas.drawRoundRect(w*.45f+4f,12f,w-12f,h-12f,28f,28f,paint)
                fill(colors[1],alpha*12/100);paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f
                canvas.drawRoundRect(w*.45f+4f,12f,w-12f,h-12f,28f,28f,paint)
            }
            "SIGNAL" -> {
                fill(colors[2],alpha*7/100);paint.strokeWidth=1f
                var x=0f;while(x<w){canvas.drawLine(x,0f,x,h,paint);x+=36f}
                var y=0f;while(y<h){canvas.drawLine(0f,y,w,y,paint);y+=36f}
                fill(colors[2],alpha);canvas.drawRect(0f,0f,w,6f,paint)
                paint.strokeWidth=2f
                canvas.drawLine(w-44f,h-30f,w-20f,h-30f,paint);canvas.drawLine(w-32f,h-42f,w-32f,h-18f,paint)
            }
            "FROST", "PRISM" -> {
                if(config.design=="PRISM") {
                    glow(0f,0f,w*.8f,colors[2],alpha*16/100)
                    glow(w,h,w*.65f,ColorUtils.blendARGB(colors[2],Color.rgb(120,219,232),.7f),alpha*15/100)
                    fill(Color.WHITE,alpha*4/100)
                    canvas.drawPath(Path().apply{moveTo(w*.45f,0f);lineTo(w*.72f,0f);lineTo(w*.25f,h);lineTo(0f,h);close()},paint)
                }
                fill(Color.WHITE,alpha*16/100)
                paint.shader=LinearGradient(0f,0f,w,h,Color.WHITE,Color.TRANSPARENT,Shader.TileMode.CLAMP)
                canvas.drawRoundRect(0f,0f,w,h,radius,radius,paint)
                paint.shader=LinearGradient(0f,0f,w,h,intArrayOf(Color.WHITE,colors[2],Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
                paint.alpha=alpha*60/100;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
                canvas.drawRoundRect(1f,1f,w-1f,h-1f,radius,radius,paint)
            }
            "LAYERS" -> {
                fill(colors[1],alpha*10/100)
                canvas.drawRoundRect(12f,12f,w,h,radius,radius,paint)
                fill(colors[2],alpha*12/100)
                canvas.drawRoundRect(6f,6f,w-6f,h-6f,radius,radius,paint)
                fill(colors[0],alpha)
                canvas.drawRoundRect(0f,0f,w-12f,h-12f,radius,radius,paint)
                fill(Color.WHITE,alpha*12/100)
                paint.shader=LinearGradient(0f,0f,w,h,Color.WHITE,Color.TRANSPARENT,Shader.TileMode.CLAMP)
                canvas.drawRoundRect(0f,0f,w-12f,h-12f,radius,radius,paint)
                paint.shader=null;paint.color=colors[1];paint.alpha=alpha*40/100;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
                canvas.drawRoundRect(1f,1f,w-13f,h-13f,radius,radius,paint)
            }
        }
    }
}

/** Preview-only sample backdrop, outside the widget's RemoteViews bitmap. */
internal class ExamPreviewWallpaper(private val light: Boolean = false) : Drawable() {
    override fun draw(canvas: Canvas) {
        val w=bounds.width().toFloat();val h=bounds.height().toFloat()
        if(w<=0f || h<=0f)return
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        val palette=if(light) intArrayOf(0xFFE4DDED.toInt(),0xFFCADFEB.toInt(),0xFFEFDBD0.toInt())
            else intArrayOf(0xFF292840.toInt(),0xFF244954.toInt(),0xFF59384C.toInt())
        p.shader=LinearGradient(0f,0f,w,h,palette,null,Shader.TileMode.CLAMP)
        canvas.drawRect(bounds,p)
        p.shader=null;p.color=if(light)0x80FFFFFF.toInt() else 0x609F9AD3.toInt()
        canvas.drawPath(Path().apply{moveTo(0f,h*.6f);cubicTo(w*.25f,-h*.3f,w*.35f,h*1.3f,w,h*.15f);lineTo(w,h);lineTo(0f,h);close()},p)
    }
    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(colorFilter: ColorFilter?) { }
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.OPAQUE
}
