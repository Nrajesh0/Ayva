package com.focusbyrj.app.ui.screens.notes

import androidx.test.core.app.ApplicationProvider
import androidx.core.content.FileProvider
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.NoteImageHelper
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import java.io.File
import java.util.UUID
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=FocusApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteImageIntegrityRegressionTest {
    @Before fun clearTestProviderRoot() {
        val cache=FileProvider::class.java.getDeclaredField("sCache")
        cache.isAccessible=true
        (cache.get(null) as MutableMap<*,*>).clear()
    }
    private val app get() = ApplicationProvider.getApplicationContext<FocusApplication>()
    private fun capture(): Pair<File,Uri> {
        val file=File(File(app.cacheDir,"note_capture").apply{mkdirs()}, UUID.randomUUID().toString()+".jpg")
        return file to FileProvider.getUriForFile(app,app.packageName+".fileprovider",file)
    }
    @Test fun animatedWebpKeepsOriginal() {
        val (file,uri)=capture()
        file.writeBytes(android.util.Base64.decode("UklGRoQAAABXRUJQVlA4WAoAAAACAAAAFwAAEAAAQU5JTQYAAAAAAAAAAABBTk1GKAAAAAAAAAAAABcAABAAACwBAAJWUDhMDwAAAC8XAAQABxD9j/4HIqL/AQBBTk1GKAAAAAAAAAAAABcAABAAACwBAABWUDhMDwAAAC8XAAQABxDR//4HIqL/AQA=", android.util.Base64.DEFAULT))
        assertNull(NoteImageHelper.processAndSaveImage(app,uri))
        assertTrue(file.exists())
    }
    @Test fun batchImportsEveryImage() {
        val img=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
        img.eraseColor(Color.RED)
        val inputs=(1..21).map{capture().also{ (file,_) ->
            file.outputStream().use{img.compress(Bitmap.CompressFormat.PNG,100,it)}
        }}
        val out=NoteImageHelper.processAndSaveMultipleImages(app,inputs.map { it.second })
        assertEquals(21,out.size)
        assertTrue(out.all { File(it).exists() })
        img.recycle()
    }
    @Test fun failedConversionKeepsCameraSource() {
        val (file,uri)=capture()
        file.writeBytes(byteArrayOf(1,2,3))
        assertNull(NoteImageHelper.processAndSaveImage(app,uri))
        assertTrue(file.exists())
    }
}
