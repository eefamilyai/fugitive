package app.fugitive.core.map.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.DisplayMetrics
import androidx.core.graphics.createBitmap
import kotlin.math.max

/** Draws marker icons. Emoji render with the system colour-emoji font. */
internal object MarkerBitmaps {

    fun avatar(
        density: Float,
        emoji: String,
        ringColor: Int,
        fillColor: Int,
        label: String?,
        badge: String?,
        ghost: Boolean,
        dimmed: Boolean,
        isMe: Boolean,
        selected: Boolean,
        crossed: Boolean,
    ): Bitmap {
        val d = density
        val r = (if (isMe) 21f else 19f) * d * (if (selected) 1.15f else 1f)
        val stroke = (if (selected) 4f else 3f) * d
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f * d; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.WHITE
        }
        val labelW = label?.let { textPaint.measureText(it) + 14 * d } ?: 0f
        val labelH = if (label != null) 18 * d else 0f
        val gap = if (label != null) 3 * d else 0f
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f * d; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE }
        val badgeW = badge?.let { badgePaint.measureText(it) + 10 * d } ?: 0f
        val w = max(max(2 * r + 2 * stroke, labelW), 2 * r + badgeW) + 4 * d
        // Symmetric vertical layout so the avatar centre is the bitmap centre (icon-anchor = center).
        val half = r + stroke + gap + labelH + 2 * d
        val h = 2 * half
        val bmp = createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
        bmp.density = (DisplayMetrics.DENSITY_DEFAULT * d).toInt()
        val c = Canvas(bmp)
        val cx = w / 2
        val cy = half
        val alpha = when { ghost -> 165; dimmed -> 120; else -> 255 }

        // Drop shadow.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; this.alpha = (alpha * 0.35f).toInt() }
        c.drawCircle(cx, cy + 1.5f * d, r + stroke * 0.6f, shadow)
        // Fill.
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor; this.alpha = alpha }
        c.drawCircle(cx, cy, r, fill)
        // Ring.
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke
            color = if (dimmed) Color.GRAY else ringColor; this.alpha = alpha
            if (ghost) pathEffect = DashPathEffect(floatArrayOf(5 * d, 3.5f * d), 0f)
        }
        c.drawCircle(cx, cy, r, ring)
        if (isMe) {
            val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * d; color = Color.WHITE }
            c.drawCircle(cx, cy, r + stroke, inner)
        }
        // Emoji.
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = r * 1.15f; textAlign = Paint.Align.CENTER; this.alpha = alpha }
        val fm = emojiPaint.fontMetrics
        c.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2, emojiPaint)
        if (crossed) {
            val x = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 239, 68, 68); strokeWidth = 3.5f * d; strokeCap = Paint.Cap.ROUND }
            val k = r * 0.62f
            c.drawLine(cx - k, cy - k, cx + k, cy + k, x)
            c.drawLine(cx + k, cy - k, cx - k, cy + k, x)
        }
        // Badge (e.g. "2m" age of a ping) at top-right.
        if (badge != null) {
            val bx = cx + r * 0.55f
            val by = cy - r - stroke * 0.2f
            val rect = RectF(bx, by - 7 * d, bx + badgeW, by + 7 * d)
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 15, 23, 42) }
            c.drawRoundRect(rect, 7 * d, 7 * d, bp)
            val bfm = badgePaint.fontMetrics
            c.drawText(badge, rect.left + 5 * d, rect.centerY() - (bfm.ascent + bfm.descent) / 2, badgePaint)
        }
        // Name label below.
        if (label != null) {
            val top = cy + r + stroke + gap
            val rect = RectF(cx - labelW / 2, top, cx + labelW / 2, top + labelH)
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((alpha * 0.85f).toInt(), 10, 14, 22) }
            c.drawRoundRect(rect, labelH / 2, labelH / 2, lp)
            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ringColor; this.alpha = alpha; style = Paint.Style.STROKE; strokeWidth = 1.2f * d }
            c.drawRoundRect(rect, labelH / 2, labelH / 2, accent)
            textPaint.alpha = alpha
            textPaint.textAlign = Paint.Align.CENTER
            val tfm = textPaint.fontMetrics
            c.drawText(label, cx, rect.centerY() - (tfm.ascent + tfm.descent) / 2, textPaint)
        }
        return bmp
    }

    /** Small arrow pointing up (rotated by heading at render time). */
    fun heading(density: Float, color: Int): Bitmap {
        val d = density
        val size = (64 * d).toInt()
        val bmp = createBitmap(size, size)
        bmp.density = (DisplayMetrics.DENSITY_DEFAULT * d).toInt()
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val cx = size / 2f
        val path = Path().apply {
            moveTo(cx, 1 * d); lineTo(cx + 7 * d, 11 * d); lineTo(cx, 8.5f * d); lineTo(cx - 7 * d, 11 * d); close()
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.5f * d }
        c.drawPath(path, p); c.drawPath(path, outline)
        return bmp
    }

    /** A labelled pin, e.g. an extraction point or a dropped ping. */
    fun pin(density: Float, emoji: String, color: Int, label: String?): Bitmap {
        val d = density
        val r = 17 * d
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f * d; typeface = Typeface.DEFAULT_BOLD; this.color = Color.WHITE; textAlign = Paint.Align.CENTER }
        val labelW = label?.let { textPaint.measureText(it) + 14 * d } ?: 0f
        val labelH = if (label != null) 18 * d else 0f
        val w = max(2 * r + 8 * d, labelW + 4 * d)
        val half = r + 4 * d + labelH + 4 * d
        val bmp = createBitmap(w.toInt(), (2 * half).toInt())
        bmp.density = (DisplayMetrics.DENSITY_DEFAULT * d).toInt()
        val c = Canvas(bmp)
        val cx = w / 2; val cy = half
        c.drawCircle(cx, cy + 1.5f * d, r + 2 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(90, 0, 0, 0) })
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f * d })
        val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = r * 1.1f; textAlign = Paint.Align.CENTER }
        val fm = ep.fontMetrics
        c.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2, ep)
        if (label != null) {
            val top = cy + r + 4 * d
            val rect = RectF(cx - labelW / 2, top, cx + labelW / 2, top + labelH)
            c.drawRoundRect(rect, labelH / 2, labelH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(225, 10, 14, 22) })
            val tfm = textPaint.fontMetrics
            c.drawText(label, cx, rect.centerY() - (tfm.ascent + tfm.descent) / 2, textPaint)
        }
        return bmp
    }
}
