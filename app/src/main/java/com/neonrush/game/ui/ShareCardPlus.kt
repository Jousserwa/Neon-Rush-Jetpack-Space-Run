package com.neonrush.game.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.neonrush.game.AuraRank
import com.neonrush.game.HullCatalog
import com.neonrush.game.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class RunRarity(val label: String, val argb: Int) {
    COMMON("COMMON RUN", 0xFF9AA4B2.toInt()), RARE("RARE RUN", 0xFF3FA9FF.toInt()),
    EPIC("EPIC RUN", 0xFFBC00DD.toInt()), LEGENDARY("LEGENDARY RUN", 0xFFFFD23F.toInt()),
    MYTHIC("MYTHIC RUN", 0xFFFF2BD6.toInt());

    companion object {
        /** Zone-based tier; a new personal best bumps it one tier. */
        fun of(zoneReached: Int, newPb: Boolean): RunRarity {
            val base = when { zoneReached >= 25 -> MYTHIC; zoneReached >= 15 -> LEGENDARY
                zoneReached >= 10 -> EPIC; zoneReached >= 5 -> RARE; else -> COMMON }
            return if (newPb) values()[(base.ordinal + 1).coerceAtMost(MYTHIC.ordinal)] else base
        }
    }
}

/** Extra data for the v2 card. Everything has a safe default so old call sites still work. */
data class ShareCardExtras(
    val hullId: String = "cyan_diamond",
    val rank: AuraRank = AuraRank.NONE,
    val isLegend: Boolean = false,
    val ownedHullIds: Set<String> = emptySet(),
    val totalHulls: Int = 0,
    val zoneReached: Int = 1,
    val challengeCode: String = "",       // ghost id for "Beat my ghost"
    val hullLevel: Int = 0,
    val title: String = ""
)

object ShareCardPlus {
    private const val W = 1080
    private const val H = 1350
    private val CYAN = Color.parseColor("#00F0FF")
    private val MAGENTA = Color.parseColor("#FF2BD6")
    private val GOLD = Color.parseColor("#FFD23F")

    fun challengeLink(code: String) = "neonrush://companion?ghost=$code"

    fun render(context: Context, d: com.neonrush.game.ui.ShareCardData, x: ShareCardExtras): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        fun paint(col: Int, size: Float, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; textSize = size; typeface = mono; textAlign = align }

        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), Color.parseColor("#0A0420"), Color.parseColor("#02010A"), Shader.TileMode.CLAMP) })

        // frame: Diamond rainbow, Pro gold->magenta, free cyan
        c.drawRoundRect(30f, 30f, W - 30f, H - 30f, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 12f
            shader = when {
                x.rank == AuraRank.DIAMOND -> LinearGradient(0f, 0f, W.toFloat(), H.toFloat(),
                    intArrayOf(Color.parseColor("#7DF9FF"), MAGENTA, GOLD, Color.parseColor("#7DF9FF")), null, Shader.TileMode.CLAMP)
                d.isPro -> LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), GOLD, MAGENTA, Shader.TileMode.CLAMP)
                else -> LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), CYAN, CYAN, Shader.TileMode.CLAMP)
            }
        })

        val cx = W / 2f
        c.drawText("NEON RUSH", cx, 140f, paint(CYAN, 84f, Paint.Align.CENTER))
        if (d.isNewPersonalBest) c.drawText("★ NEW PERSONAL BEST ★", cx, 205f, paint(GOLD, 40f, Paint.Align.CENTER))

        // ---- hull showcase stage
        val sx = 90f; val sy = 235f; val sw = W - 180f; val sh = 270f
        c.drawRoundRect(RectF(sx, sy, sx + sw, sy + sh), 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, sy + sh / 2, sw / 1.6f, Color.parseColor("#2A1250"), Color.parseColor("#0A0420"), Shader.TileMode.CLAMP) })
        drawHull(context, c, x.hullId, sx.roundToInt(), sy.roundToInt(), sw.roundToInt(), sh.roundToInt())
        val hull = HullCatalog.byId(x.hullId)
        val hullLabel = (hull?.let { "${it.emoji} ${it.name}" } ?: x.hullId.replace('_', ' ')).uppercase()
        c.drawText(hullLabel + if (x.hullLevel > 0) "  ·  LV ${x.hullLevel}" else "", cx, sy + sh + 50f, paint(Color.WHITE, 38f, Paint.Align.CENTER))

        // ---- score
        c.drawText("SCORE", cx, 640f, paint(Color.WHITE, 46f, Paint.Align.CENTER))
        var size = 200f; val str = d.score.toString(); val sp = Paint().apply { typeface = mono }
        while (size > 80f) { sp.textSize = size; if (sp.measureText(str) < W - 200f) break; size -= 8f }
        c.drawText(str, cx, 640f + size * 0.95f, paint(MAGENTA, size, Paint.Align.CENTER))

        // ---- rarity stamp
        val rarity = RunRarity.of(x.zoneReached, d.isNewPersonalBest)
        c.save(); c.rotate(-12f, W - 260f, 560f)
        val sp2 = paint(rarity.argb, 44f, Paint.Align.CENTER)
        val tw = sp2.measureText(rarity.label)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f; color = rarity.argb }
        c.drawRoundRect(RectF(W - 260f - tw / 2 - 24f, 520f, W - 260f + tw / 2 + 24f, 586f), 12f, 12f, stroke)
        c.drawText(rarity.label, W - 260f, 566f, sp2); c.restore()

        // ---- stats (left aligned so the QR can sit right)
        val lx = 90f
        c.drawText("DISTANCE  ${d.distanceMeters}m", lx, 930f, paint(CYAN, 46f))
        c.drawText("ZONE  ${d.zoneName.take(20)}", lx, 990f, paint(CYAN, 42f))
        c.drawText("PILOT  ${d.pilotName.take(16)}", lx, 1060f, paint(Color.WHITE, 46f))
        val tag = buildString {
            if (x.rank != AuraRank.NONE) append("${x.rank.emoji} ${x.rank.label.uppercase()} PRO")
            else if (d.isPro) append("⚡ PRO")
            if (x.isLegend) append("  👑 LEGEND")
        }
        if (tag.isNotEmpty()) c.drawText(tag, lx, 1115f, paint(GOLD, 38f))
        else if (d.masteryLevel > 0) c.drawText("MASTERY LV ${d.masteryLevel}", lx, 1115f, paint(CYAN, 38f))
        if (x.title.isNotBlank()) c.drawText(x.title.take(26), lx, 1165f, paint(Color.parseColor("#C9B8FF"), 34f))

        // ---- QR (store) + challenge
        c.drawBitmap(qr(d.storeUrl, 220), W - 220f - 80f, 900f, null)
        if (x.challengeCode.isNotBlank())
            c.drawText("BEAT MY GHOST ▸ ${x.challengeCode.take(10)}", W - 190f, 1160f, paint(GOLD, 26f, Paint.Align.CENTER))

        // ---- collection flex strip
        val ownedPrem = HullCatalog.ALL.filter { it.id in x.ownedHullIds }
        if (x.totalHulls > 0) {
            val strip = ownedPrem.take(10).joinToString(" ") { it.emoji }
            c.drawText("${x.ownedHullIds.size}/${x.totalHulls} HULLS  $strip", cx, 1230f, paint(Color.WHITE, 32f, Paint.Align.CENTER))
        }
        c.drawText(ShareCard.WATERMARK, cx, H - 60f, paint(Color.WHITE, 30f, Paint.Align.CENTER))
        return bmp
    }

    /** Draw the real animated hull (a frozen frame) into the Android canvas via an offscreen Compose scope. */
    private fun drawHull(context: Context, c: Canvas, hullId: String, left: Int, top: Int, w: Int, h: Int) {
        try {
            val img = ImageBitmap(w, h)
            val pilot = ImageBitmap.imageResource(context.resources, R.drawable.pilot_run_1)
            val scope = CanvasDrawScope()
            scope.draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img),
                androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat())) {
                val hh = h * 0.8f; val px = w * 0.55f; val py = h / 2f
                // freeze on a flattering frame and fire a couple of reactions so layers are visible
                HullFx.gemTick = 296; HullFx.closeCallTick = 292
                drawHullEffect(hullId, px, py, hh, 300, 6)
                val pw = hh * pilot.width / pilot.height
                drawImage(pilot, dstOffset = IntOffset((px - pw / 2f).roundToInt(), (py - hh / 2f).roundToInt()),
                    dstSize = IntSize(pw.roundToInt(), hh.roundToInt()))
            }
            c.drawBitmap(img.asAndroidBitmap(), left.toFloat(), top.toFloat(), null)
            HullFx.reset()
        } catch (e: Exception) { /* showcase is decorative; never block sharing */ }
    }

    private fun qr(url: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) b.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return b
    }

    fun share(context: Context, d: ShareCardData, x: ShareCardExtras) {
        val bmp = render(context, d, x)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "neon_rush_score.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val text = buildString {
            append(ShareCard.WATERMARK).append('\n').append(d.storeUrl)
            if (x.challengeCode.isNotBlank()) append("\nBeat my ghost: ").append(challengeLink(x.challengeCode))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share your score"))
    }
}
