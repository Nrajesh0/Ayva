package com.focusbyrj.app.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.ByteBuffer
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LosslessAnimationTest {
    @Test fun compatibilityDecoderPreservesEveryOriginalGifFramePixel() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val json=javaClass.classLoader!!.getResourceAsStream("vizag-frame-digests.json")!!.bufferedReader().use { JSONObject(it.readText()) }
        for(scene in VizagScene.entries) {
            val key=context.resources.getResourceEntryName(scene.animation)
            val expected=json.getJSONArray(key)
            val drawable=LegacyWebpDrawable(context.resources.openRawResource(scene.animation).use { it.readBytes() })
            val bitmap=Bitmap.createBitmap(drawable.intrinsicWidth,drawable.intrinsicHeight,Bitmap.Config.ARGB_8888)
            drawable.setBounds(0,0,bitmap.width,bitmap.height)
            val pixels=IntArray(bitmap.width*bitmap.height)
            for(i in 0 until expected.length()) {
                bitmap.eraseColor(0);drawable.draw(Canvas(bitmap))
                bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                val bytes=ByteBuffer.allocate(pixels.size*4)
                for(p in pixels)bytes.putInt(p)
                val hash=MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString(""){"%02x".format(it.toInt() and 255)}
                assertEquals("${scene.placeName}, frame $i",expected.getString(i),hash)
                drawable.advanceFrame()
            }
            bitmap.recycle()
        }
    }
}
