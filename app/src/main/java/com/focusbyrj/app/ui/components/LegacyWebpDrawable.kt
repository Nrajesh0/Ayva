package com.focusbyrj.app.ui.components

import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock

/** Android 7–8 fallback using the platform's lossless WebP bitmap decoder.
 * Android 9+ continues to use ImageDecoder/AnimatedImageDrawable. No native
 * library, network asset or older-device quality reduction is required.
 */
internal class LegacyWebpDrawable(private val encoded: ByteArray) : Drawable(), Animatable {
    private data class Frame(val x: Int, val y: Int, val width: Int, val height: Int, val duration: Int, val dispose: Boolean, val replace: Boolean, val image: ByteArray)
    private val frames = mutableListOf<Frame>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val replacePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private var canvasWidth = 0
    private var canvasHeight = 0
    private val composite: Bitmap
    private val compositor: Canvas
    private var index = 0
    private var playing = false
    private var nextDeadline = 0L
    private fun u24(p: Int): Int = (encoded[p].toInt() and 255) or ((encoded[p+1].toInt() and 255) shl 8) or ((encoded[p+2].toInt() and 255) shl 16)
    private fun u32(p: Int): Int = u24(p) or ((encoded[p+3].toInt() and 255) shl 24)
    private fun tag(p: Int) = String(encoded,p,4,Charsets.US_ASCII)

    init {
        require(encoded.size >= 12 && tag(0)=="RIFF" && tag(8)=="WEBP")
        var p=12
        while(p+8<=encoded.size) {
            val length=u32(p+4);val data=p+8
            require(length>=0 && data.toLong()+length<=encoded.size)
            when(tag(p)) {
                "VP8X" -> { require(length>=10);canvasWidth=u24(data+4)+1;canvasHeight=u24(data+7)+1 }
                "ANMF" -> {
                    require(length>=24)
                    val payload=encoded.copyOfRange(data+16,data+length)
                    require(String(payload,0,4,Charsets.US_ASCII)=="VP8L") { "Fallback expects the lossless VP8L format used by our exporter" }
                    val size=payload.size+4
                    val header=byteArrayOf(82,73,70,70,size.toByte(),(size shr 8).toByte(),(size shr 16).toByte(),(size shr 24).toByte(),87,69,66,80)
                    val flags=encoded[data+15].toInt()
                    frames+=Frame(u24(data)*2,u24(data+3)*2,u24(data+6)+1,u24(data+9)+1,u24(data+12).coerceAtLeast(1),flags and 1!=0,flags and 2!=0,header+payload)
                }
            }
            p=data+length+(length and 1)
        }
        require(canvasWidth>0 && canvasHeight>0 && frames.isNotEmpty())
        composite=Bitmap.createBitmap(canvasWidth,canvasHeight,Bitmap.Config.ARGB_8888)
        compositor=Canvas(composite)
        render(frames[0])
    }

    private fun render(frame: Frame) {
        val bitmap=requireNotNull(BitmapFactory.decodeByteArray(frame.image,0,frame.image.size))
        require(bitmap.width==frame.width && bitmap.height==frame.height)
        compositor.drawBitmap(bitmap,frame.x.toFloat(),frame.y.toFloat(),if(frame.replace)replacePaint else null)
        bitmap.recycle()
    }
    internal fun advanceFrame() {
        val previous=frames[index]
        if(previous.dispose) compositor.drawRect(previous.x.toFloat(),previous.y.toFloat(),(previous.x+previous.width).toFloat(),(previous.y+previous.height).toFloat(),clearPaint)
        index=(index+1)%frames.size
        if(index==0) composite.eraseColor(Color.TRANSPARENT)
        render(frames[index])
    }
    private val tick=object:Runnable {
        override fun run() {
            if(!playing)return
            advanceFrame();invalidateSelf()
            nextDeadline+=frames[index].duration
            scheduleSelf(this,maxOf(nextDeadline,SystemClock.uptimeMillis()))
        }
    }
    override fun start() {
        if(playing)return
        playing=true;nextDeadline=SystemClock.uptimeMillis()+frames[index].duration
        scheduleSelf(tick,nextDeadline);invalidateSelf()
    }
    override fun stop() {playing=false;unscheduleSelf(tick)}
    override fun isRunning()=playing
    override fun draw(canvas: Canvas) {canvas.drawBitmap(composite,null,bounds,paint)}
    override fun getIntrinsicWidth()=canvasWidth
    override fun getIntrinsicHeight()=canvasHeight
    override fun setAlpha(alpha: Int) {paint.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter: ColorFilter?) {paint.colorFilter=filter;invalidateSelf()}
    @Deprecated("Deprecated in Android")
    override fun getOpacity()=PixelFormat.TRANSLUCENT
}
