package app.galaxywifi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

/** Vẽ meter vòng cung kiểu Galaxy 5G Mobile Wi‑Fi (SCR01). */
object WidgetMeterBitmap {
    fun draw(
        sizePx: Int,
        progress: Float, // 0..1
        trackColor: Int,
        progressColor: Int,
        centerText: String,
        subText: String,
        labelColor: Int,
        bgColor: Int = 0
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (bgColor != 0) c.drawColor(bgColor)

        val stroke = sizePx * 0.09f
        val pad = stroke / 2f + sizePx * 0.06f
        val oval = RectF(pad, pad, sizePx - pad, sizePx - pad)

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = trackColor
        }
        val prog = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = progressColor
        }
        // SCR01: cung ~270° mở dưới
        val start = 135f
        val sweep = 270f
        c.drawArc(oval, start, sweep, false, track)
        c.drawArc(oval, start, sweep * progress.coerceIn(0f, 1f), false, prog)

        val main = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = sizePx * 0.18f
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            alpha = 160
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.10f
        }
        val cy = sizePx / 2f + sizePx * 0.02f
        c.drawText(centerText, sizePx / 2f, cy, main)
        if (subText.isNotBlank()) {
            c.drawText(subText, sizePx / 2f, cy + sizePx * 0.14f, sub)
        }
        return bmp
    }

    fun signalBars(
        widthPx: Int,
        heightPx: Int,
        level: Int, // 0..4
        onColor: Int,
        offColor: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val n = 4
        val gap = widthPx * 0.08f
        val barW = (widthPx - gap * (n - 1)) / n
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until n) {
            val h = heightPx * (0.35f + 0.65f * (i + 1) / n)
            val left = i * (barW + gap)
            val top = heightPx - h
            paint.color = if (i < level) onColor else offColor
            c.drawRoundRect(RectF(left, top, left + barW, heightPx.toFloat()), 4f, 4f, paint)
        }
        return bmp
    }

    fun batteryPill(
        widthPx: Int,
        heightPx: Int,
        percent: Int,
        charging: Boolean,
        fillColor: Int,
        trackColor: Int,
        textColor: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = heightPx / 2f
        val body = RectF(0f, 0f, widthPx - heightPx * 0.18f, heightPx.toFloat())
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = trackColor
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
        c.drawRoundRect(body, r, r, track)
        val inset = 3f
        val maxW = body.width() - inset * 2
        val w = maxW * (percent.coerceIn(0, 100) / 100f)
        if (w > 0) {
            c.drawRoundRect(
                RectF(body.left + inset, body.top + inset, body.left + inset + w, body.bottom - inset),
                r - 2, r - 2, fill
            )
        }
        // tip
        val tip = RectF(body.right, heightPx * 0.28f, widthPx.toFloat(), heightPx * 0.72f)
        c.drawRoundRect(tip, 3f, 3f, track)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textAlign = Paint.Align.CENTER
            textSize = heightPx * 0.55f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val label = if (charging) "$percent%" else "$percent%"
        c.drawText(label, body.centerX(), body.centerY() + text.textSize * 0.35f, text)
        return bmp
    }
}
