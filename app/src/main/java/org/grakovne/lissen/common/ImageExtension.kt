package org.grakovne.lissen.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.Base64
import androidx.compose.ui.unit.Dp

fun Bitmap.clip(
    context: Context,
    cornerRadiusDp: Dp
): Bitmap {
    val density = context.resources.displayMetrics.density
    val cornerRadiusPx = cornerRadiusDp.value * density

    val width = this.width
    val height = this.height

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val paint = Paint().apply {
        isAntiAlias = true
    }

    val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)

    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(this, 0f, 0f, paint)

    return output
}

fun String.fromBase64(): Bitmap? {
    val bytes = Base64.decode(this, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.DEFAULT)
