package com.focusbyrj.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.annotation.DrawableRes
import com.focusbyrj.app.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Five approved scenes, painted from small lossless layers rather than stored frames.
 * Uses the existing View's visibility/lifecycle/reduced-motion controls on Android 7+.
 * No WebView, per-frame image decoding, network access or finite video-loop reset.
 */
internal class LayeredVizagDrawable(
    context: Context,
    private val scene: String,
    @DrawableRes atlasResource: Int
) : Drawable(), Animatable {
    private val data = context.resources.openRawResource(R.raw.vizag_replacement_layers).bufferedReader().use {
        JSONObject(it.readText()).getJSONObject(scene)
    }
    private val atlas = requireNotNull(BitmapFactory.decodeResource(context.resources, atlasResource,
        BitmapFactory.Options().apply { inScaled = false }))
    private val top = data.getInt("top")
    private val bottom = data.getInt("bottom")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private var opacity = 255
    private var elapsed = 0L
    private var started = 0L
    private var playing = false
    private data class Sprite(val source: Rect, val x: Float, val y: Float, val w: Float, val h: Float)
    private val sprites = buildMap {
        val items = data.getJSONObject("sprites")
        for (id in items.keys()) {
            val item = items.getJSONObject(id); val src = item.getJSONArray("source"); val b = item.getJSONArray("bounds")
            put(id, Sprite(Rect(src.getInt(0), src.getInt(1), src.getInt(0) + src.getInt(2), src.getInt(1) + src.getInt(3)),
                b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), b.getDouble(3).toFloat()))
        }
    }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private val waves = data.getJSONArray("waves").objects()
    private val people = data.getJSONArray("people").objects()
    private val birds = data.getJSONArray("birds").objects()
    private val cabins = data.getJSONArray("cabins").objects()
    private data class Point(val x: Double, val y: Double, val distance: Double)
    private val cable = buildList<Point> {
        val source = data.getJSONArray("cable")
        var distance = 0.0
        for (i in 0 until source.length()) {
            val p = source.getJSONArray(i); val x = p.getDouble(0); val y = p.getDouble(1)
            if (isNotEmpty()) distance += hypot(x - last().x, y - last().y)
            add(Point(x, y, distance))
        }
    }
    private fun route(progress: Double): Point {
        val target = progress.coerceIn(0.0, 1.0) * cable.last().distance
        val i = cable.indexOfFirst { it.distance >= target }.coerceAtLeast(1)
        val a = cable[i - 1]; val b = cable[i]; val f = (target - a.distance) / (b.distance - a.distance)
        return Point(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, target)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!playing) return
            invalidateSelf()
            scheduleSelf(this, SystemClock.uptimeMillis() + 33L)
        }
    }
    override fun start() {
        if (playing) return
        started = SystemClock.uptimeMillis(); playing = true
        invalidateSelf(); scheduleSelf(tick, started + 33L)
    }
    override fun stop() {
        if (playing) elapsed += SystemClock.uptimeMillis() - started
        playing = false; unscheduleSelf(tick)
    }
    override fun isRunning() = playing
    override fun getIntrinsicWidth() = 995
    override fun getIntrinsicHeight() = bottom - top
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT

    private fun image(c: Canvas, id: String, dx: Double = 0.0, dy: Double = 0.0,
                      scale: Double = 1.0, angle: Double = 0.0, px: Float? = null, py: Float? = null,
                      alpha: Double = 1.0) {
        val l = sprites[id] ?: return
        val x = px ?: (l.x + l.w / 2); val y = py ?: (l.y + l.h)
        val save = c.save(); c.translate(x + dx.toFloat(), y + dy.toFloat())
        c.rotate((angle * 180 / PI).toFloat()); c.scale(scale.toFloat(), scale.toFloat())
        paint.alpha = (opacity * alpha).toInt().coerceIn(0, 255)
        c.drawBitmap(atlas, l.source, RectF(l.x - x, l.y - y, l.x - x + l.w, l.y - y + l.h), paint)
        c.restoreToCount(save); paint.alpha = opacity
    }
    private fun part(c: Canvas, l: Sprite, sx: Float, sy: Float, sw: Float, sh: Float,
                     dx: Float, dy: Float) {
        // Clip the full original sprite, retaining subpixel hip/wing coordinates.
        val save = c.save(); c.clipRect(dx, dy, dx + sw, dy + sh)
        c.drawBitmap(atlas, l.source, RectF(dx - sx, dy - sy, dx - sx + l.w, dy - sy + l.h), paint)
        c.restoreToCount(save)
    }
    private fun smooth(v: Double): Double { val x = v.coerceIn(0.0, 1.0); return x * x * (3 - 2 * x) }
    private fun shoreWash(t: Double): Double {
        fun value(time: Double): Double { val p = ((time / 7.8) % 1 + 1) % 1; return if (p < .4) smooth(p / .4) else 1 - smooth((p - .4) / .6) }
        return value(t) - value(0.0)
    }
    private fun sea(c: Canvas, t: Double) {
        val save = c.saveLayer(0f, top.toFloat(), 995f, bottom.toFloat(), null)
        for (wave in waves) {
            val id = wave.getString("id"); val l = sprites.getValue(id)
            val depth = wave.getDouble("depth"); val phase = wave.getDouble("phase")
            when (scene) {
                "rk_beach" -> {
                    val p0 = if (id == "rk-water-2") .35 else .65
                    val p = ((p0 + t / 8.2) % 1 + 1) % 1
                    val d = 2 * (p - p0)
                    image(c, id, -29 * depth * d, 18 * depth * d,
                        alpha = smooth(p / .17) * smooth((1 - p) / .22))
                }
                "rushikonda" -> {
                    if (t == 0.0) { image(c, id); continue }
                    for (copy in 0..1) {
                        val period = 7.6 + (.9 - depth) * .8
                        val p = ((.5 + t / period + copy * .5) % 1 + 1) % 1
                        image(c, id, -32 * depth * (2 * p - 1), 22 * depth * (2 * p - 1),
                            alpha = smooth(p / .18) * smooth((1 - p) / .28))
                    }
                }
                else -> {
                    // Broad offshore swells, one source line each. Three source
                    // pixels per strip are <1 dp at footer size and bound draw cost.
                    var x = 0f
                    while (x < l.w) {
                        val edge = (min(x, l.w - 1 - x) / 30).coerceIn(0f, 1f)
                        val p = (l.x + x) / 125 + phase * .45
                        val dy = edge * ((3.2 + 2.4 * depth) * (sin(p - t * .88) - sin(p)) + .3 * (sin(p * 2 - t * 1.2) - sin(p * 2)))
                        val clip = c.save(); c.clipRect(l.x + x, l.y - 14, l.x + min(x + 3, l.w), l.y + l.h + 14)
                        image(c, id, dy = dy); c.restoreToCount(clip); x += 3
                    }
                }
            }
        }
        if (scene == "rushikonda") { val wash = shoreWash(t); image(c, "rushi-foam", -6 * wash, 4 * wash) }
        val mask = sprites.getValue("sea-mask")
        c.drawBitmap(atlas, mask.source, RectF(mask.x, mask.y, mask.x + mask.w, mask.y + mask.h), maskPaint)
        c.restoreToCount(save)
    }
    private fun visitors(c: Canvas, t: Double) {
        for (person in people) {
            val id = person.getString("id"); val l = sprites.getValue(id)
            val walk = person.getDouble("walk"); val scale = person.getDouble("scale"); val phase = person.getDouble("phase")
            if (t == 0.0) { image(c, id, scale = scale); continue }
            val distance = walk * 2.5
            val dx = if (id.startsWith("rushi")) -distance * (1 - cos(t * .45)) / 2 else distance * sin(t * .45)
            val bob = if (walk != 0.0) abs(sin(t * 4.1)) * .8 else sin(t * .9) * .45
            val mid = l.w / 2; val hip = l.h * .6f
            val save = c.save(); c.translate(l.x + mid + dx.toFloat(), l.y + l.h + bob.toFloat())
            c.scale(scale.toFloat(), scale.toFloat()); c.translate(-mid, -l.h)
            part(c, l, 0f, 0f, l.w, hip, 0f, 0f)
            val swing = (if (walk != 0.0) .36 else .08) * sin(t * 4.1 + phase) * sin(min(1.0, t) * PI / 2)
            for (k in 0..1) {
                val sx = if (k == 1) mid else 0f; val sw = if (k == 1) l.w - mid else mid
                val leg = c.save(); c.translate(mid, hip)
                c.rotate(((if (k == 1) swing else -swing) * 180 / PI).toFloat())
                part(c, l, sx, hip, sw, l.h - hip, sx - mid, 0f); c.restoreToCount(leg)
            }
            c.restoreToCount(save)
        }
    }
    private fun flock(c: Canvas, t: Double) {
        for (bird in birds) {
            val l = sprites.getValue(bird.getString("id")); val phase = bird.getDouble("phase")
            val x = ((bird.getDouble("x") + 80 + bird.getDouble("speed") * t) % 1155) - 80
            val dy = 1.8 * (sin(t * .55 + phase) - sin(phase))
            val save = c.save(); c.translate((x + l.w / 2).toFloat(), (bird.getDouble("y") + dy + l.h * .65).toFloat())
            val flap = .82 * sin(t * 6.6 + phase) * sin(min(t, 1.0) * PI / 2)
            val mid = floor(l.w / 2)
            val left = c.save(); c.rotate((flap * 180 / PI).toFloat())
            part(c, l, 0f, 0f, mid, l.h, -l.w / 2, -l.h * .65f); c.restoreToCount(left)
            val right = c.save(); c.rotate((-flap * 180 / PI).toFloat())
            part(c, l, mid, 0f, l.w - mid, l.h, mid - l.w / 2, -l.h * .65f); c.restoreToCount(right)
            c.restoreToCount(save)
        }
    }
    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val t = (elapsed + if (playing) SystemClock.uptimeMillis() - started else 0L) / 1000.0
        val save = canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 995f, bounds.height().toFloat() / intrinsicHeight)
        canvas.clipRect(0f, 0f, 995f, intrinsicHeight.toFloat()); canvas.translate(0f, -top.toFloat())
        paint.alpha = opacity
        image(canvas, "base"); sea(canvas, t)
        when (scene) {
            "rk_beach" -> {
                val bus = canvas.save(); canvas.clipRect(0f, 288f, 515f, 356f)
                val x = ((37 + 130 + 13.5 * t) % 680) - 130
                image(canvas, "rk-bus", x - 37); canvas.restoreToCount(bus); image(canvas, "props")
                for (i in 0..2) image(canvas, "rk-boat-$i", 14 * (sin(t * .20 + i) - sin(i.toDouble())), 1.6 * (sin(t * 1.1 + i) - sin(i.toDouble())))
            }
            "kailasagiri" -> for (cabin in cabins) {
                val a = acos(1 - 2 * cabin.getDouble("p0")) * cabin.getDouble("direction") + t * 2 * PI / 72
                val pos = route((1 - cos(a)) / 2); val origin = cabin.getJSONObject("origin"); val anchor = cabin.getJSONArray("anchor")
                image(canvas, cabin.getString("id"), pos.x - origin.getDouble("x"), pos.y - origin.getDouble("y"),
                    angle = .007 * sin(t * .75), px = anchor.getDouble(0).toFloat(), py = anchor.getDouble(1).toFloat())
            }
            "dolphins_nose" -> image(canvas, "dolphin-boat", -95 * (1 - cos(t * .19)) / 2, 3 * sin(t * 1.15))
            "tenneti_park" -> image(canvas, "tenneti-grounded-ship")
            "rushikonda" -> { image(canvas, "rushi-blueboat-1"); image(canvas, "rushi-blueboat-2") }
        }
        visitors(canvas, t)
        if (scene == "rushikonda") {
            image(canvas, "palm-crowns", angle = .025 * sin(t * .65), px = 118f, py = 1355f)
            image(canvas, "rushi-umbrella-canopy", angle = .009 * sin(t * .7), px = 196f, py = 1493f)
            image(canvas, "windsurfer", -180 * (1 - cos(t * .23)) / 2, 3 * sin(t * 1.5), angle = .06 * sin(t * .8), px = 773f, py = 1475f)
            image(canvas, "paraglider", -280 * (1 - cos(t * .19)) / 2, 6 * sin(t * .45), angle = .025 * sin(t * .55), px = 884f, py = 1335f)
        }
        if (scene == "dolphins_nose") image(canvas, "lighthouse-beacon", angle = .10 * sin(t * .5), px = 351f, py = 714f,
            alpha = .7 + .3 * (.5 + .5 * cos(t * .8)))
        flock(canvas, t); image(canvas, "protect"); canvas.restoreToCount(save)
    }
}
