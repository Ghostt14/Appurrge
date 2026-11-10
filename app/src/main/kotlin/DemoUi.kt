// Appurrge, GPL-3.0-only. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.delay

/** Icons for the made-up apps: a coloured rounded square with the app's first letter. */
object DemoIcons {
    private val colors = intArrayOf(
        0xFFE57373.toInt(), 0xFFF06292.toInt(), 0xFFBA68C8.toInt(), 0xFF9575CD.toInt(),
        0xFF7986CB.toInt(), 0xFF64B5F6.toInt(), 0xFF4DB6AC.toInt(), 0xFF81C784.toInt(),
        0xFFFFB74D.toInt(), 0xFFFF8A65.toInt(), 0xFFA1887F.toInt(), 0xFF90A4AE.toInt()
    )

    fun make(name: String, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val size = sizePx.toFloat()
        paint.color = colors[(name.hashCode() and 0x7fffffff) % colors.size]
        canvas.drawRoundRect(0f, 0f, size, size, size * 0.22f, size * 0.22f, paint)
        paint.color = Color.WHITE
        paint.textSize = size * 0.5f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(name.take(1), size / 2, size / 2 - (paint.ascent() + paint.descent()) / 2, paint)
        return bitmap
    }
}

/** Pretends to uninstall: waits a moment, so the progress dialog is visible in a recording, then succeeds. */
object DemoUninstaller : UninstallBackend {
    override suspend fun uninstall(app: AppInfo): Boolean {
        delay(450)
        return true
    }
}
