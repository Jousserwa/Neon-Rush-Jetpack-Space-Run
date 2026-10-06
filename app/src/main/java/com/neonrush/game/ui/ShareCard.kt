package com.neonrush.game.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

/** Everything the card needs. Built from SimulationState + GameProfile at tap time. */
data class ShareCardData(
    val pilotName: String,
    val score: Int,
    val distanceMeters: Int,
    val zoneName: String,
    val isNewPersonalBest: Boolean,
    val isPro: Boolean,
    val storeUrl: String,
    val masteryLevel: Int = 0
)

object ShareCard {

    const val WATERMARK = "Beat my score on Neon Rush (No Wi-Fi needed!)"
    private const val W = 1080
    private const val H = 1350 // 4:5, displays well in most feeds

    private val CYAN = Color.parseColor("#00F0FF")
    private val MAGENTA = Color.parseColor("#FF2BD6")
    private val GOLD = Color.parseColor("#FFD23F")

    fun render(d: ShareCardData): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        // Background
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(),
                Color.parseColor("#0A0420"), Color.parseColor("#02010A"), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // Frame (Pro gets a gold/magenta gradient frame, free gets cyan)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            if (d.isPro) shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), GOLD, MAGENTA, Shader.TileMode.CLAMP)
            else color = CYAN
        }
        c.drawRoundRect(30f, 30f, W - 30f, H - 30f, 36f, 36f, frame)

        fun text(s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; textSize = size; typeface = mono; textAlign = align
            }
            c.drawText(s, x, y, p)
        }

        val cx = W / 2f
        text("NEON RUSH", cx, 170f, 96f, CYAN)
        if (d.isNewPersonalBest) text("★ NEW PERSONAL BEST ★", cx, 250f, 44f, GOLD)

        text("SCORE", cx, 450f, 54f, Color.WHITE)
        // Auto-shrink long scores so they never overflow the card
        var scoreSize = 220f
        val scoreStr = d.score.toString()
        val sp = Paint().apply { typeface = mono }
        while (scoreSize > 80f) { sp.textSize = scoreSize; if (sp.measureText(scoreStr) < W - 160f) break; scoreSize -= 8f }
        text(scoreStr, cx, 650f, scoreSize, MAGENTA)

        text("DISTANCE  ${d.distanceMeters}m", cx, 790f, 52f, CYAN)
        text("ZONE  ${d.zoneName.take(24)}", cx, 870f, 46f, CYAN)
        text("PILOT  ${d.pilotName.take(18)}", cx, 950f, 46f, Color.WHITE)
        if (d.isPro) text("⚡ PRO PILOT", cx, 1020f, 40f, GOLD)
        if (d.masteryLevel > 0) text("MASTERY LV ${d.masteryLevel}", cx, 1090f, 40f, CYAN)

        // QR to the store page (works as a scan target from a screenshot)
        val qr = qr(d.storeUrl, 230)
        c.drawBitmap(qr, W - 230f - 70f, H - 230f - 190f, null)

        // Watermark
        text(WATERMARK, cx, H - 90f, 36f, Color.WHITE)
        return bmp
    }

    private fun qr(url: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size)
            b.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return b
    }

    /** Renders, saves to cache, and opens the Android share sheet. Call from a click handler. */
    fun share(context: Context, d: ShareCardData) {
        val bmp = render(d)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "neon_rush_score.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${WATERMARK}\n${d.storeUrl}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share your score"))
    }
}
