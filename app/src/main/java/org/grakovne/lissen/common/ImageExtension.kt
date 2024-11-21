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
): Bitmap = try {
    val density = context.resources.displayMetrics.density
    val cornerRadiusPx = cornerRadiusDp.value * density

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val paint = Paint().apply {
        isAntiAlias = true
    }

    val rectF = RectF(0f, 0f, width.toFloat(), height.toFloat())

    val path = android.graphics.Path().apply {
        addRoundRect(
            rectF,
            floatArrayOf(
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx
            ),
            android.graphics.Path.Direction.CW
        )
    }

    canvas.drawPath(path, paint)

    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(this, 0f, 0f, paint)

    output
} catch (e: Exception) {
    this
}

fun String.fromBase64(): Bitmap? = try {
    val bytes = Base64.decode(this, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (ex: Exception) {
    null
}

fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.DEFAULT)
