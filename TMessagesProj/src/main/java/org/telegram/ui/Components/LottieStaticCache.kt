package org.telegram.ui.Components

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.RawRes
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog

object LottieStaticCache {

    private val cache = HashMap<Long, Bitmap>()

    @JvmStatic
    fun drawable(@RawRes rawRes: Int, sizeDp: Int): Drawable =
        drawablePx(rawRes, dp(sizeDp.toFloat()))

    @JvmStatic
    @Synchronized
    fun drawablePx(@RawRes rawRes: Int, rawSizePx: Int): Drawable {
        val sizePx = if (rawSizePx > 0) rawSizePx else 1
        val key = (rawRes.toLong() shl 32) or (sizePx.toLong() and 0xFFFFFFFFL)

        var bmp = cache[key]
        if (bmp == null || bmp.isRecycled) {
            bmp = generate(rawRes, sizePx)
                ?: Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            cache[key] = bmp
        }

        val res = ApplicationLoader.applicationContext?.resources ?: Resources.getSystem()
        return BitmapDrawable(res, bmp)
    }

    private fun generate(@RawRes rawRes: Int, sizePx: Int): Bitmap? {
        val lottie = RLottieDrawable(rawRes, sizePx, sizePx, /* startDecode = */ false, null)
        return try {
            lottie.setCurrentFrame(0, false, true)

            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            lottie.setBounds(0, 0, sizePx, sizePx)
            lottie.draw(canvas)
            bmp
        } catch (t: Throwable) {
            FileLog.e(t)
            null
        } finally {
            lottie.recycle(true)
        }
    }

    @JvmStatic
    @Synchronized
    fun clear() {
        cache.clear()
    }
}