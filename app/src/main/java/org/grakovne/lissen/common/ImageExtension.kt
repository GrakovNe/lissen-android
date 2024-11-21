package org.grakovne.lissen.common

import android.annotation.SuppressLint
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
import java.io.ByteArrayOutputStream

fun Bitmap.clip(
    context: Context,
    cornerRadiusDp: Dp
): Bitmap = try {
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

@SuppressLint("UseCompatLoadingForDrawables")
fun Int.toBase64(context: Context): String {
    try {
        val drawable = context.getDrawable(this) ?: return ""
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()

        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    } catch (ex: Exception) {
        return ""
    }
}