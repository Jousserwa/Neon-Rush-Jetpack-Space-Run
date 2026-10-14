package com.neonrush.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vector painter for pilot parts. Every part is drawn in "helmet-radius" units around an anchor on the pilot
 * (y points down), so the same part sits correctly on any of the six running frames and scales with the pilot.
 * [t] drives the flutter of scarves, capes and antennae.
 */
class PartPainter(private val ds: DrawScope, private val ax: Float, private val ay: Float, private val r: Float, private val t: Float) {
    val time: Float get() = t
    private var swDeg = 0f
    private var swF = 1f
    fun sway(deg: Float, f: Float) { swDeg = deg; swF = f }

    private fun pt(x: Float, y: Float): Offset {
        var px = x; var py = y
        if (swDeg != 0f) {
            val a = (sin(t * swF) * swDeg * Math.PI / 180.0).toFloat()
            val c = cos(a); val s = sin(a)
            px = x * c - y * s; py = x * s + y * c
        }
        return Offset(ax + px * r, ay + py * r)
    }

    fun poly(fill: Long, stroke: Long, w: Float, vararg xy: Float) {
        if (xy.size < 6) return
        val path = Path()
        var i = 0
        while (i + 1 < xy.size) {
            val o = pt(xy[i], xy[i + 1])
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            i += 2
        }
        path.close()
        if (fill != 0L) ds.drawPath(path, Color(fill))
        if (stroke != 0L && w > 0f) ds.drawPath(path, Color(stroke), style = Stroke(width = w * r, join = StrokeJoin.Round))
    }

    fun circle(cx: Float, cy: Float, rad: Float, fill: Long, stroke: Long, w: Float) {
        val c = pt(cx, cy)
        if (fill != 0L) ds.drawCircle(Color(fill), radius = rad * r, center = c)
        if (stroke != 0L && w > 0f) ds.drawCircle(Color(stroke), radius = rad * r, center = c, style = Stroke(width = w * r))
    }

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, fill: Long, stroke: Long, w: Float) {
        val c = pt(cx, cy)
        val tl = Offset(c.x - rx * r, c.y - ry * r)
        val sz = Size(rx * 2f * r, ry * 2f * r)
        if (fill != 0L) ds.drawOval(Color(fill), topLeft = tl, size = sz)
        if (stroke != 0L && w > 0f) ds.drawOval(Color(stroke), topLeft = tl, size = sz, style = Stroke(width = w * r))
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Long, w: Float) {
        ds.drawLine(Color(color), pt(x0, y0), pt(x1, y1), strokeWidth = w * r, cap = StrokeCap.Round)
    }

    /** A waving strip of cloth. angDeg is the direction it trails in (y down), amp/freq shape the flutter. */
    fun ribbon(ox: Float, oy: Float, angDeg: Float, len: Float, w0: Float, w1: Float, amp: Float, freq: Float, fill: Long, stroke: Long, sw: Float) {
        val n = 10
        val rad = Math.toRadians(angDeg.toDouble())
        val dx = cos(rad).toFloat(); val dy = sin(rad).toFloat()
        val nx = -dy; val ny = dx
        val top = FloatArray((n + 1) * 2); val bot = FloatArray((n + 1) * 2)
        for (i in 0..n) {
            val f = i.toFloat() / n
            val bx = ox + dx * len * f; val by = oy + dy * len * f
            val wv = (sin(t * freq - f * 5f) * amp * f)
            val cx = bx + nx * wv; val cy = by + ny * wv
            val hw = (w0 + (w1 - w0) * f) / 2f
            top[i * 2] = cx + nx * hw; top[i * 2 + 1] = cy + ny * hw
            bot[i * 2] = cx - nx * hw; bot[i * 2 + 1] = cy - ny * hw
        }
        val all = FloatArray((n + 1) * 4)
        for (i in 0..n) { all[i * 2] = top[i * 2]; all[i * 2 + 1] = top[i * 2 + 1] }
        for (i in 0..n) { val j = n - i; all[(n + 1 + i) * 2] = bot[j * 2]; all[(n + 1 + i) * 2 + 1] = bot[j * 2 + 1] }
        poly(fill, stroke, sw, *all)
    }
}

/** Paints one part by id with the given painter. Unknown ids draw nothing. */
internal fun paintPartById(id: String, P: PartPainter) {
    when (id) {
        "crest_fin" -> part_crest_fin(P)
        "halo_ring" -> part_halo_ring(P)
        "antenna_array" -> part_antenna_array(P)
        "oni_horns" -> part_oni_horns(P)
        "flag_scarf" -> part_flag_scarf(P)
        "neon_scarf" -> part_neon_scarf(P)
        "gold_chain" -> part_gold_chain(P)
        "spike_pauldron" -> part_spike_pauldron(P)
        "gold_epaulette" -> part_gold_epaulette(P)
        "crystal_shard" -> part_crystal_shard(P)
        "skull_guard" -> part_skull_guard(P)
        "royal_cape" -> part_royal_cape(P)
        "mech_wings" -> part_mech_wings(P)
        "twin_pods" -> part_twin_pods(P)
        "banner_pole" -> part_banner_pole(P)
        "power_gauntlet" -> part_power_gauntlet(P)
        "chain_shackle" -> part_chain_shackle(P)
        "blade_bracer" -> part_blade_bracer(P)
        "wing_boots" -> part_wing_boots(P)
        "spur_boots" -> part_spur_boots(P)
        "magnet_cuffs" -> part_magnet_cuffs(P)
        "sun_crown" -> part_sun_crown(P)
        "reaper_hood" -> part_reaper_hood(P)
        "flower_crest" -> part_flower_crest(P)
        "gold_crown" -> part_gold_crown(P)
        "headband" -> part_headband(P)
        "vine_scarf" -> part_vine_scarf(P)
        "void_cloak" -> part_void_cloak(P)
        "claw_spikes" -> part_claw_spikes(P)
        "shackle_chain" -> part_shackle_chain(P)
        "ember_horns" -> part_ember_horns(P)
        "phoenix_wings" -> part_phoenix_wings(P)
        "orbital_drones" -> part_orbital_drones(P)
        "singularity_crown" -> part_singularity_crown(P)
        "fire_inferno" -> flameSet(P, 0xFFD32F2F, 0xFFFF6D00, 0xFFFFEA00, 0)
        "fire_azure" -> flameSet(P, 0xFF1565C0, 0xFF29B6F6, 0xFFE1F5FE, 1)
        "fire_violet" -> flameSet(P, 0xFF6A1B9A, 0xFFE040FB, 0xFFFFE6FF, 2)
        else -> {}
    }
}

private fun part_crest_fin(P: PartPainter) {
    P.poly(0xFFFF2D95, 0xFF0B0A14, 0.14f, 0.066f, -0.948f, -0.274f, -1.728f, -0.23f, -0.922f)
    P.poly(0xFFFF2D95, 0xFF0B0A14, 0.14f, -0.309f, -0.898f, -1.06f, -1.696f, -0.572f, -0.759f)
    P.poly(0xFFFF2D95, 0xFF0B0A14, 0.14f, -0.636f, -0.706f, -1.393f, -0.975f, -0.823f, -0.475f)
    P.poly(0xFFFFD6EC, 0L, 0f, 0.033f, -0.949f, -0.506f, -1.252f, -0.848f, -0.53f)
}

private fun part_halo_ring(P: PartPainter) {
    P.ellipse(0f, -1.65f, 1.05f, 0.3f, 0L, 0xFF0B0A14, 0.42f)
    P.ellipse(0f, -1.65f, 1.05f, 0.3f, 0L, 0xFFFFD23F, 0.2f)
    P.ellipse(0f, -1.65f, 1.05f, 0.3f, 0L, 0xFFFFF59D, 0.06f)
}

private fun part_antenna_array(P: PartPainter) {
    P.sway(8f, 3f)
    P.line(-0.5f, -0.85f, -1.15f, -2.05f, 0xFF0B0A14, 0.3f)
    P.line(-0.5f, -0.85f, -1.15f, -2.05f, 0xFFB0BEC5, 0.14f)
    P.circle(-1.15f, -2.1f, 0.24f, 0xFFFF1744, 0xFF0B0A14, 0.1f)
    P.line(-0.1f, -1f, -0.3f, -2.35f, 0xFF0B0A14, 0.3f)
    P.line(-0.1f, -1f, -0.3f, -2.35f, 0xFFB0BEC5, 0.14f)
    P.circle(-0.3f, -2.4f, 0.2f, 0xFF00E5FF, 0xFF0B0A14, 0.1f)
    P.sway(0f, 0f)
}

private fun part_oni_horns(P: PartPainter) {
    P.poly(0xFFEF5350, 0xFF0B0A14, 0.14f, -0.8f, -0.75f, -1.3f, -1.2f, -1.6f, -2f, -0.85f, -1.45f, -0.35f, -0.88f)
    P.poly(0xFFEF5350, 0xFF0B0A14, 0.14f, 0.4f, -0.92f, 0.25f, -1.55f, -0.3f, -2.25f, -0.05f, -1.4f, -0.12f, -0.97f)
    P.poly(0xFFFFE082, 0L, 0f, -1.6f, -2f, -1.45f, -1.65f, -1.25f, -1.55f)
    P.poly(0xFFFFE082, 0L, 0f, -0.3f, -2.25f, -0.2f, -1.85f, -0.05f, -1.75f)
}

private fun part_flag_scarf(P: PartPainter) {
    P.ribbon(0f, 0f, 190f, 4.6f, 0.7f, 0.3f, 0.55f, 6f, 0xFFE53935, 0xFF0B0A14, 0.12f)
    P.ribbon(0.05f, 0.18f, 194f, 3.7f, 0.45f, 0.18f, 0.45f, 6f, 0xFFFF8A80, 0L, 0f)
    P.circle(0.05f, 0.05f, 0.38f, 0xFFC62828, 0xFF0B0A14, 0.12f)
}

private fun part_neon_scarf(P: PartPainter) {
    P.ribbon(0f, 0f, 188f, 4.4f, 0.7f, 0.3f, 0.6f, 5f, 0xFF00E5FF, 0xFF0B0A14, 0.12f)
    P.ribbon(0f, 0f, 188f, 4.4f, 0.22f, 0.1f, 0.6f, 5f, 0xFF004D5C, 0L, 0f)
    P.circle(0.05f, 0.05f, 0.36f, 0xFF00ACC1, 0xFF0B0A14, 0.12f)
}

private fun part_gold_chain(P: PartPainter) {
    P.line(-0.94f, 0.939f, -0.793f, 1.126f, 0xFF0B0A14, 0.3f)
    P.line(-0.793f, 1.126f, -0.574f, 1.273f, 0xFF0B0A14, 0.3f)
    P.line(-0.574f, 1.273f, -0.301f, 1.368f, 0xFF0B0A14, 0.3f)
    P.line(-0.301f, 1.368f, 0f, 1.4f, 0xFF0B0A14, 0.3f)
    P.line(0f, 1.4f, 0.301f, 1.368f, 0xFF0B0A14, 0.3f)
    P.line(0.301f, 1.368f, 0.574f, 1.273f, 0xFF0B0A14, 0.3f)
    P.line(0.574f, 1.273f, 0.793f, 1.126f, 0xFF0B0A14, 0.3f)
    P.line(0.793f, 1.126f, 0.94f, 0.939f, 0xFF0B0A14, 0.3f)
    P.line(-0.94f, 0.939f, -0.793f, 1.126f, 0xFFFFC107, 0.16f)
    P.line(-0.793f, 1.126f, -0.574f, 1.273f, 0xFFFFC107, 0.16f)
    P.line(-0.574f, 1.273f, -0.301f, 1.368f, 0xFFFFC107, 0.16f)
    P.line(-0.301f, 1.368f, 0f, 1.4f, 0xFFFFC107, 0.16f)
    P.line(0f, 1.4f, 0.301f, 1.368f, 0xFFFFC107, 0.16f)
    P.line(0.301f, 1.368f, 0.574f, 1.273f, 0xFFFFC107, 0.16f)
    P.line(0.574f, 1.273f, 0.793f, 1.126f, 0xFFFFC107, 0.16f)
    P.line(0.793f, 1.126f, 0.94f, 0.939f, 0xFFFFC107, 0.16f)
    P.poly(0xFFFF1744, 0xFF0B0A14, 0.1f, 0f, 0.95f, 0.28f, 1.3f, 0f, 1.75f, -0.28f, 1.3f)
}

private fun part_spike_pauldron(P: PartPainter) {
    P.poly(0xFF78909C, 0xFF0B0A14, 0.14f, -1f, 0.5f, -0.9f, -0.2f, -0.4f, -0.7f, 0.4f, -0.7f, 0.95f, -0.1f, 1.05f, 0.5f, 0.2f, 0.8f, -0.6f, 0.8f)
    P.poly(0xFFCFD8DC, 0xFF0B0A14, 0.12f, -0.8f, -0.3f, -0.5f, -1.7f, -0.2f, -0.6f)
    P.poly(0xFFCFD8DC, 0xFF0B0A14, 0.12f, -0.15f, -0.65f, 0.25f, -1.95f, 0.55f, -0.65f)
    P.poly(0xFFCFD8DC, 0xFF0B0A14, 0.12f, 0.6f, -0.5f, 1.05f, -1.35f, 1f, -0.05f)
    P.poly(0xFF455A64, 0L, 0f, -0.7f, 0.1f, 0f, -0.35f, 0.7f, 0.05f, 0f, 0.2f)
}

private fun part_gold_epaulette(P: PartPainter) {
    P.ellipse(0f, 0.05f, 1.25f, 0.85f, 0xFFFFC107, 0xFF0B0A14, 0.14f)
    P.ellipse(0f, -0.05f, 0.8f, 0.5f, 0xFFFFE082, 0L, 0f)
    P.line(-1f, 0.7f, -1.05f, 1.45f, 0xFF0B0A14, 0.2f)
    P.line(-1f, 0.7f, -1.05f, 1.45f, 0xFFFFD23F, 0.1f)
    P.line(-0.67f, 0.7f, -0.72f, 1.45f, 0xFF0B0A14, 0.2f)
    P.line(-0.67f, 0.7f, -0.72f, 1.45f, 0xFFFFD23F, 0.1f)
    P.line(-0.34f, 0.7f, -0.39f, 1.45f, 0xFF0B0A14, 0.2f)
    P.line(-0.34f, 0.7f, -0.39f, 1.45f, 0xFFFFD23F, 0.1f)
    P.line(-0.01f, 0.7f, -0.06f, 1.45f, 0xFF0B0A14, 0.2f)
    P.line(-0.01f, 0.7f, -0.06f, 1.45f, 0xFFFFD23F, 0.1f)
    P.line(0.32f, 0.7f, 0.27f, 1.45f, 0xFF0B0A14, 0.2f)
    P.line(0.32f, 0.7f, 0.27f, 1.45f, 0xFFFFD23F, 0.1f)
    P.line(0.65f, 0.7f, 0.6f, 1.45f, 0xFF0B0A14, 0.2f)
    P.line(0.65f, 0.7f, 0.6f, 1.45f, 0xFFFFD23F, 0.1f)
    P.line(0.98f, 0.7f, 0.93f, 1.45f, 0xFF0B0A14, 0.2f)
    P.line(0.98f, 0.7f, 0.93f, 1.45f, 0xFFFFD23F, 0.1f)
    P.circle(0f, 0f, 0.2f, 0xFFFF1744, 0xFF0B0A14, 0.08f)
}

private fun part_crystal_shard(P: PartPainter) {
    P.poly(0xFF4DD0E1, 0xFF0B0A14, 0.14f, -0.3f, 0.6f, -0.55f, -0.4f, -0.2f, -1.9f, 0.15f, -0.4f, 0.2f, 0.6f)
    P.poly(0xFF80DEEA, 0xFF0B0A14, 0.14f, 0.2f, 0.6f, 0.15f, -0.2f, 0.7f, -1.35f, 0.95f, -0.1f, 0.85f, 0.6f)
    P.poly(0xFF26C6DA, 0xFF0B0A14, 0.14f, -0.9f, 0.6f, -0.95f, -0.1f, -0.7f, -1f, -0.4f, -0.1f, -0.35f, 0.6f)
    P.poly(0xFFFFFFFF, 0L, 0f, -0.2f, -1.7f, -0.1f, -0.5f, 0f, -0.5f)
}

private fun part_skull_guard(P: PartPainter) {
    P.circle(0f, 0f, 1f, 0xFFECEFF1, 0xFF0B0A14, 0.14f)
    P.circle(-0.35f, -0.1f, 0.26f, 0xFF263238, 0L, 0f)
    P.circle(0.35f, -0.1f, 0.26f, 0xFF263238, 0L, 0f)
    P.poly(0xFF263238, 0L, 0f, 0f, 0.15f, -0.12f, 0.42f, 0.12f, 0.42f)
    P.line(-0.3f, 0.62f, -0.3f, 0.85f, 0xFF263238, 0.07f)
    P.line(-0.1f, 0.62f, -0.1f, 0.85f, 0xFF263238, 0.07f)
    P.line(0.1f, 0.62f, 0.1f, 0.85f, 0xFF263238, 0.07f)
    P.line(0.3f, 0.62f, 0.3f, 0.85f, 0xFF263238, 0.07f)
}

private fun part_royal_cape(P: PartPainter) {
    P.ribbon(0f, 0f, 176f, 5.4f, 1.5f, 2.6f, 0.8f, 3.6f, 0xFFC62828, 0xFF0B0A14, 0.14f)
    P.ribbon(0f, 0f, 176f, 5.4f, 0.5f, 1f, 0.8f, 3.6f, 0xFF8E0000, 0L, 0f)
    P.ribbon(0f, -0.62f, 176f, 5.4f, 0.14f, 0.22f, 0.8f, 3.6f, 0xFFFFD23F, 0L, 0f)
}

private fun part_mech_wings(P: PartPainter) {
    P.sway(5f, 2.4f)
    P.poly(0xFFB0BEC5, 0xFF0B0A14, 0.14f, -0.239f, 0.658f, -3.571f, -1.3f, 0.239f, -0.658f)
    P.poly(0xFF00E5FF, 0L, 0f, -0.12f, 0.329f, -2.857f, -1.04f, 0.12f, -0.329f)
    P.poly(0xFFB0BEC5, 0xFF0B0A14, 0.14f, -0.435f, 0.483f, -2.601f, -2.342f, 0.435f, -0.483f)
    P.poly(0xFF00E5FF, 0L, 0f, -0.217f, 0.242f, -2.081f, -1.874f, 0.217f, -0.242f)
    P.poly(0xFFB0BEC5, 0xFF0B0A14, 0.14f, -0.539f, 0.263f, -1.315f, -2.696f, 0.539f, -0.263f)
    P.poly(0xFF00E5FF, 0L, 0f, -0.27f, 0.132f, -1.052f, -2.157f, 0.27f, -0.132f)
    P.poly(0xFFB0BEC5, 0xFF0B0A14, 0.14f, -0.499f, 0.035f, -0.16f, -2.294f, 0.499f, -0.035f)
    P.poly(0xFF00E5FF, 0L, 0f, -0.249f, 0.017f, -0.128f, -1.836f, 0.249f, -0.017f)
    P.sway(0f, 0f)
}

private fun part_twin_pods(P: PartPainter) {
    P.poly(0xFF607D8B, 0xFF0B0A14, 0.14f, -0.95f, -0.1f, 0.15f, -0.1f, 0.15f, 0.6f, -0.95f, 0.6f)
    P.circle(-1.05f, 0.25f, 0.32f, 0xFF263238, 0xFF0B0A14, 0.1f)
    P.circle(-1.05f, 0.25f, 0.16f, 0xFF00E5FF, 0L, 0f)
    P.line(-0.7f, 0f, -0.7f, 0.5f, 0xFFB0BEC5, 0.1f)
    P.poly(0xFF607D8B, 0xFF0B0A14, 0.14f, -0.95f, 0.95f, 0.15f, 0.95f, 0.15f, 1.65f, -0.95f, 1.65f)
    P.circle(-1.05f, 1.3f, 0.32f, 0xFF263238, 0xFF0B0A14, 0.1f)
    P.circle(-1.05f, 1.3f, 0.16f, 0xFF00E5FF, 0L, 0f)
    P.line(-0.7f, 1.05f, -0.7f, 1.55f, 0xFFB0BEC5, 0.1f)
}

private fun part_banner_pole(P: PartPainter) {
    P.line(-0.3f, 0.4f, -1f, -3.4f, 0xFF0B0A14, 0.3f)
    P.line(-0.3f, 0.4f, -1f, -3.4f, 0xFFCFD8DC, 0.14f)
    P.ribbon(-1f, -3.3f, 190f, 3.2f, 1.5f, 0.9f, 0.5f, 5f, 0xFF7C4DFF, 0xFF0B0A14, 0.12f)
    P.circle(-1.02f, -3.5f, 0.2f, 0xFFFFD23F, 0xFF0B0A14, 0.08f)
}

private fun part_power_gauntlet(P: PartPainter) {
    P.poly(0xFF546E7A, 0xFF0B0A14, 0.1f, -0.5f, -0.42f, 0.3f, -0.5f, 0.42f, 0.42f, -0.45f, 0.5f)
    P.poly(0xFF00E5FF, 0L, 0f, -0.34f, -0.2f, 0.2f, -0.25f, 0.25f, 0.22f, -0.3f, 0.27f)
}

private fun part_chain_shackle(P: PartPainter) {
    P.ellipse(0f, 0f, 0.42f, 0.46f, 0L, 0xFF0B0A14, 0.26f)
    P.ellipse(0f, 0f, 0.42f, 0.46f, 0L, 0xFF9E9E9E, 0.12f)
    P.ellipse(0f, 0.65f, 0.14f, 0.2f, 0L, 0xFF0B0A14, 0.14f)
    P.ellipse(0f, 0.65f, 0.14f, 0.2f, 0L, 0xFFBDBDBD, 0.07f)
    P.ellipse(0f, 1.05f, 0.14f, 0.2f, 0L, 0xFF0B0A14, 0.14f)
    P.ellipse(0f, 1.05f, 0.14f, 0.2f, 0L, 0xFFBDBDBD, 0.07f)
    P.ellipse(0f, 1.45f, 0.14f, 0.2f, 0L, 0xFF0B0A14, 0.14f)
    P.ellipse(0f, 1.45f, 0.14f, 0.2f, 0L, 0xFFBDBDBD, 0.07f)
}

private fun part_blade_bracer(P: PartPainter) {
    P.poly(0xFF37474F, 0xFF0B0A14, 0.1f, -0.5f, -0.38f, 0.3f, -0.42f, 0.4f, 0.4f, -0.45f, 0.45f)
    P.poly(0xFF80F0FF, 0xFF0B0A14, 0.08f, 0.3f, -0.15f, 2.3f, -0.4f, 0.35f, 0.18f)
}

private fun part_wing_boots(P: PartPainter) {
    P.sway(6f, 3.5f)
    P.poly(0xFFFFFFFF, 0xFF0B0A14, 0.1f, -0.172f, 0.246f, -1.835f, -0.492f, -0.026f, -0.299f)
    P.poly(0xFFFFFFFF, 0xFF0B0A14, 0.1f, -0.246f, 0.172f, -1.393f, -0.975f, 0.078f, -0.29f)
    P.poly(0xFFFFFFFF, 0xFF0B0A14, 0.1f, -0.29f, 0.078f, -0.803f, -1.147f, 0.172f, -0.246f)
    P.sway(0f, 0f)
    P.circle(0f, 0f, 0.2f, 0xFFFFD23F, 0xFF0B0A14, 0.07f)
}

private fun part_spur_boots(P: PartPainter) {
    P.poly(0xFFFFD23F, 0xFF0B0A14, 0.1f, -0.9f, -0.05f, -1.7f, 0.15f, -1f, 0.35f)
    P.poly(0xFFFFD23F, 0xFF0B0A14, 0.1f, -0.9f, 0.4f, -1.6f, 0.7f, -0.95f, 0.75f)
    P.ellipse(0f, -0.6f, 0.6f, 0.28f, 0L, 0xFF0B0A14, 0.38f)
    P.ellipse(0f, -0.6f, 0.6f, 0.28f, 0L, 0xFFFFD23F, 0.18f)
}

private fun part_magnet_cuffs(P: PartPainter) {
    P.ellipse(0.1f, -0.9f, 0.62f, 0.24f, 0L, 0xFF0B0A14, 0.36f)
    P.ellipse(0.1f, -0.9f, 0.62f, 0.24f, 0L, 0xFF00E5FF, 0.16f)
    P.ellipse(0.1f, -0.3f, 0.62f, 0.24f, 0L, 0xFF0B0A14, 0.36f)
    P.ellipse(0.1f, -0.3f, 0.62f, 0.24f, 0L, 0xFF00E5FF, 0.16f)
    P.poly(0xFF00B8D4, 0xFF0B0A14, 0.08f, -0.5f, 0.7f, 0.7f, 0.7f, 0.5f, 0.95f, -0.3f, 0.95f)
}

private fun part_sun_crown(P: PartPainter) {
    P.poly(0xFFFFB300, 0xFF0B0A14, 0.1f, 0.783f, -0.166f, 1.855f, -0.603f, 0.731f, -0.325f)
    P.poly(0xFFFFB300, 0xFF0B0A14, 0.1f, 0.693f, -0.4f, 1.214f, -0.882f, 0.595f, -0.535f)
    P.poly(0xFFFFB300, 0xFF0B0A14, 0.1f, 0.535f, -0.595f, 1.146f, -1.578f, 0.4f, -0.693f)
    P.poly(0xFFFFB300, 0xFF0B0A14, 0.1f, 0.325f, -0.731f, 0.464f, -1.427f, 0.166f, -0.783f)
    P.poly(0xFFFFB300, 0xFF0B0A14, 0.1f, 0.084f, -0.796f, 0f, -1.95f, -0.084f, -0.796f)
    P.poly(0xFFFFB300, 0xFF0B0A14, 0.1f, -0.166f, -0.783f, -0.464f, -1.427f, -0.325f, -0.731f)
    P.poly(0xFFFFB300, 0xFF0B0A14, 0.1f, -0.4f, -0.693f, -1.146f, -1.578f, -0.535f, -0.595f)
    P.poly(0xFFFFB300, 0xFF0B0A14, 0.1f, -0.595f, -0.535f, -1.214f, -0.882f, -0.693f, -0.4f)
    P.poly(0xFFFFB300, 0xFF0B0A14, 0.1f, -0.731f, -0.325f, -1.855f, -0.603f, -0.783f, -0.166f)
    P.poly(0xFFFFE082, 0xFF0B0A14, 0.08f, -0.929f, -0.198f, -0.675f, -0.804f, 0f, -1.08f, 0.675f, -0.804f, 0.929f, -0.198f, 0.566f, -0.674f, 0f, -0.9f, -0.566f, -0.674f)
}

private fun part_reaper_hood(P: PartPainter) {
    P.poly(0xFF1C1B26, 0xFF0B0A14, 0.16f, 0f, -1.15f, -0.9f, -1.3f, -1.9f, -0.9f, -2.9f, -0.1f, -2.2f, 0.1f, -2.6f, 0.9f, -1.5f, 0.55f, -0.9f, 1.5f, -0.4f, 0.9f, 0.4f, 0.3f)
    P.poly(0xFF3A3950, 0L, 0f, -0.5f, -1f, -1.3f, -1f, -2f, -0.5f, -1.1f, -0.5f)
}

private fun part_flower_crest(P: PartPainter) {
    P.poly(0xFF2E7D32, 0xFF0B0A14, 0.1f, -0.823f, -0.475f, -1.41f, -0.513f, -0.6f, -1.039f, 0.174f, -0.985f, 0.728f, -0.611f)
    P.circle(-0.558f, -0.72f, 0.23f, 0xFFFF4081, 0xFF0B0A14, 0.06f)
    P.circle(-0.765f, -1.005f, 0.23f, 0xFFFF4081, 0xFF0B0A14, 0.06f)
    P.circle(-1.101f, -0.896f, 0.23f, 0xFFFF4081, 0xFF0B0A14, 0.06f)
    P.circle(-1.101f, -0.544f, 0.23f, 0xFFFF4081, 0xFF0B0A14, 0.06f)
    P.circle(-0.765f, -0.435f, 0.23f, 0xFFFF4081, 0xFF0B0A14, 0.06f)
    P.circle(-0.858f, -0.72f, 0.17f, 0xFFFFFFFF, 0xFF0B0A14, 0.05f)
    P.circle(0.074f, -1.28f, 0.23f, 0xFFFFD54F, 0xFF0B0A14, 0.06f)
    P.circle(-0.133f, -1.566f, 0.23f, 0xFFFFD54F, 0xFF0B0A14, 0.06f)
    P.circle(-0.468f, -1.457f, 0.23f, 0xFFFFD54F, 0xFF0B0A14, 0.06f)
    P.circle(-0.468f, -1.104f, 0.23f, 0xFFFFD54F, 0xFF0B0A14, 0.06f)
    P.circle(-0.133f, -0.995f, 0.23f, 0xFFFFD54F, 0xFF0B0A14, 0.06f)
    P.circle(-0.226f, -1.28f, 0.17f, 0xFFFFFFFF, 0xFF0B0A14, 0.05f)
    P.circle(0.86f, -0.97f, 0.23f, 0xFFE040FB, 0xFF0B0A14, 0.06f)
    P.circle(0.653f, -1.255f, 0.23f, 0xFFE040FB, 0xFF0B0A14, 0.06f)
    P.circle(0.317f, -1.146f, 0.23f, 0xFFE040FB, 0xFF0B0A14, 0.06f)
    P.circle(0.317f, -0.794f, 0.23f, 0xFFE040FB, 0xFF0B0A14, 0.06f)
    P.circle(0.653f, -0.685f, 0.23f, 0xFFE040FB, 0xFF0B0A14, 0.06f)
    P.circle(0.56f, -0.97f, 0.17f, 0xFFFFFFFF, 0xFF0B0A14, 0.05f)
}

private fun part_gold_crown(P: PartPainter) {
    P.poly(0xFFFFC107, 0xFF0B0A14, 0.14f, -0.947f, -0.254f, -1.623f, -0.656f, -0.929f, -0.836f, -0.915f, -1.722f, -0.181f, -1.287f, 0.405f, -1.907f, 0.662f, -1.06f, 1.379f, -1.077f, 0.84f, -0.505f, 0.45f, -0.779f, -0.156f, -0.886f, -0.689f, -0.579f)
    P.circle(-0.704f, -1.324f, 0.15f, 0xFFFF1744, 0xFF0B0A14, 0.05f)
    P.circle(0.795f, -1.272f, 0.14f, 0xFF00E5FF, 0xFF0B0A14, 0.05f)
    P.circle(0.045f, -1.279f, 0.13f, 0xFFFFFFFF, 0xFF0B0A14, 0.05f)
}

private fun part_headband(P: PartPainter) {
    P.poly(0xFFFFFFFF, 0xFF0B0A14, 0.1f, -0.98f, -0.45f, 0.98f, -0.55f, 0.95f, -0.2f, -0.97f, -0.1f)
    P.circle(0.2f, -0.38f, 0.2f, 0xFFE53935, 0L, 0f)
    P.ribbon(-0.97f, -0.3f, 195f, 2.8f, 0.35f, 0.15f, 0.5f, 6f, 0xFFFFFFFF, 0xFF0B0A14, 0.08f)
    P.ribbon(-0.97f, -0.22f, 185f, 2.3f, 0.3f, 0.12f, 0.5f, 6f, 0xFFE53935, 0xFF0B0A14, 0.08f)
}

private fun part_vine_scarf(P: PartPainter) {
    P.ribbon(0f, 0f, 188f, 4f, 0.5f, 0.25f, 0.5f, 5f, 0xFF388E3C, 0xFF0B0A14, 0.1f)
    P.circle(-0.9f, 0.4f, 0.22f, 0xFF76FF03, 0xFF0B0A14, 0.06f)
    P.circle(-1.8f, -0.1f, 0.22f, 0xFF76FF03, 0xFF0B0A14, 0.06f)
    P.circle(-2.7f, 0.4f, 0.22f, 0xFF76FF03, 0xFF0B0A14, 0.06f)
    P.circle(-3.6f, -0.1f, 0.22f, 0xFF76FF03, 0xFF0B0A14, 0.06f)
}

private fun part_void_cloak(P: PartPainter) {
    P.ribbon(0f, 0f, 178f, 5.2f, 1.6f, 2.8f, 0.9f, 3.2f, 0xFF14122B, 0xFF0B0A14, 0.14f)
    P.circle(-0.8f, 0.65f, 0.09f, 0xFFFFFFFF, 0L, 0f)
    P.circle(-1.45f, -0.45f, 0.09f, 0xFFFFFFFF, 0L, 0f)
    P.circle(-2.1f, 0.65f, 0.09f, 0xFFFFFFFF, 0L, 0f)
    P.circle(-2.75f, -0.45f, 0.09f, 0xFFFFFFFF, 0L, 0f)
    P.circle(-3.4f, 0.65f, 0.09f, 0xFFFFFFFF, 0L, 0f)
    P.circle(-4.05f, -0.45f, 0.09f, 0xFFFFFFFF, 0L, 0f)
    P.circle(-4.7f, 0.65f, 0.09f, 0xFFFFFFFF, 0L, 0f)
}

private fun part_claw_spikes(P: PartPainter) {
    P.poly(0xFFFFE0B2, 0xFF0B0A14, 0.12f, -0.7f, -0.2f, -0.2f, -2f, 0f, -0.5f)
    P.poly(0xFFFFE0B2, 0xFF0B0A14, 0.12f, 0.1f, -0.5f, 0.7f, -1.9f, 0.9f, -0.1f)
    P.poly(0xFFFFE0B2, 0xFF0B0A14, 0.12f, 0.8f, -0.4f, 1.5f, -1.2f, 1.3f, 0.2f)
    P.ellipse(0f, 0.1f, 0.8f, 0.4f, 0xFF6D4C41, 0xFF0B0A14, 0.14f)
}

private fun part_shackle_chain(P: PartPainter) {
    P.ellipse(0f, 0f, 0.42f, 0.46f, 0L, 0xFF0B0A14, 0.24f)
    P.ellipse(0f, 0f, 0.42f, 0.46f, 0L, 0xFF9E9E9E, 0.11f)
}

private fun part_ember_horns(P: PartPainter) {
    P.poly(0xFF1A1A22, 0xFF0B0A14, 0.14f, -0.8f, -0.75f, -1.3f, -1.2f, -1.6f, -2f, -0.85f, -1.45f, -0.35f, -0.88f)
    P.poly(0xFF1A1A22, 0xFF0B0A14, 0.14f, 0.4f, -0.92f, 0.25f, -1.55f, -0.3f, -2.25f, -0.05f, -1.4f, -0.12f, -0.97f)
    P.poly(0xFFFF1744, 0L, 0f, -1.6f, -2f, -1.45f, -1.65f, -1.25f, -1.55f)
    P.poly(0xFFFF1744, 0L, 0f, -0.3f, -2.25f, -0.2f, -1.85f, -0.05f, -1.75f)
}




// ---------------------------------------------------------------------------------------------- animated showpieces

private fun argbA(c: Long, a: Float): Long = (((a.coerceIn(0f, 1f) * 255f).toInt().toLong()) shl 24) or (c and 0xFFFFFFL)
private fun pol(a: Float, r: Float): Pair<Float, Float> {
    val rad = Math.toRadians(a.toDouble())
    return Pair((Math.cos(rad) * r).toFloat(), (-Math.sin(rad) * r).toFloat())
}

/** Huge flame-feather wings that beat. Far wing is darker and a beat behind for depth. */
private fun part_phoenix_wings(P: PartPainter) {
    val cols = longArrayOf(0xFFB71C1C, 0xFFD32F2F, 0xFFE64A19, 0xFFF57C00, 0xFFFFA000, 0xFFFFC107, 0xFFFFE082)
    for (wing in 0..1) {
        val far = wing == 0
        P.sway(if (far) 12f else 15f, if (far) 2.0f else 2.3f)
        val sc = if (far) 0.82f else 1f
        val dx = if (far) 0.5f else 0f
        for (i in 0..6) {
            val a = 104f + i * 11f
            val len = (2.7f + i * 0.34f) * sc
            val b1 = pol(a + 90f, 0.42f); val b2 = pol(a - 90f, 0.42f)
            val d = pol(a + 4f, len * 0.58f); val n = pol(a + 90f, 0.5f * sc)
            val tip = pol(a, len)
            val col = if (far) (((cols[i] shr 1) and 0x7F7F7FL) or 0xFF000000L) else cols[i]
            P.poly(col, 0xFF0B0A14, 0.1f,
                b1.first + dx, b1.second, d.first + n.first + dx, d.second + n.second, tip.first + dx, tip.second,
                d.first - n.first + dx, d.second - n.second, b2.first + dx, b2.second)
            if (!far) {
                val m = pol(a, len * 0.9f); val r0 = pol(a, 0.5f)
                P.line(r0.first, r0.second, m.first, m.second, argbA(0xFFFFF3B0, 0.7f), 0.1f)
                val fl = 0.5f + 0.5f * kotlin.math.sin(P.time * 5f + i * 1.3f)
                P.circle(tip.first, tip.second, 0.12f + 0.08f * fl, argbA(0xFFFFEE58, 0.5f + 0.5f * fl), 0L, 0f)
            }
        }
    }
    P.sway(0f, 0f)
}

/** Three drones orbit the pilot's body, blink and fire pulse beams. */
private fun part_orbital_drones(P: PartPainter) {
    val t = P.time
    P.ellipse(0f, 0f, 3.5f, 1.6f, 0L, argbA(0xFF00E5FF, 0.22f), 0.05f)
    val order = (0..2).sortedBy { k -> kotlin.math.sin(t * 1.7f + k * 2.0944f) }   // far ones first
    for (k in order) {
        val th = t * 1.7f + k * 2.0944f
        val x = kotlin.math.cos(th) * 3.4f
        val y = kotlin.math.sin(th) * 1.5f
        val depth = kotlin.math.sin(th)
        val sc = 0.85f + 0.2f * depth
        val dim = if (depth < 0f) 0.65f else 1f
        val body = if (depth < 0f) 0xFF263238 else 0xFF455A64
        // pulse beam forward on a rhythm
        val beam = (kotlin.math.sin(t * 3.1f + k * 1.9f) - 0.55f).coerceAtLeast(0f) * 2.2f
        if (beam > 0f) P.line(x + 0.35f * sc, y, x + 0.35f * sc + 1.6f * beam, y, argbA(0xFF00E5FF, 0.9f * dim), 0.12f * sc)
        P.poly(body, 0xFF0B0A14, 0.1f, x - 0.5f * sc, y, x - 0.15f * sc, y - 0.42f * sc, x + 0.3f * sc, y - 0.3f * sc, x + 0.45f * sc, y, x + 0.3f * sc, y + 0.3f * sc, x - 0.15f * sc, y + 0.42f * sc)
        P.poly(0xFF78909C, 0L, 0f, x - 0.4f * sc, y - 0.55f * sc, x + 0.1f * sc, y - 0.55f * sc, x, y - 0.4f * sc)
        val blink = 0.55f + 0.45f * kotlin.math.sin(t * 6f + k * 2f)
        P.circle(x + 0.18f * sc, y, 0.15f * sc, argbA(0xFF00E5FF, blink), 0xFF0B0A14, 0.04f)
        P.circle(x + 0.18f * sc, y, 0.06f * sc, 0xFFFFFFFF, 0L, 0f)
    }
}

/** A miniature black hole over the helmet: dark disc, spinning accretion ring, orbiting stars, faint light shafts. */
private fun part_singularity_crown(P: PartPainter) {
    val t = P.time
    val cy = -2.0f
    for (i in 0..2) {
        val a = 70f + i * 20f
        val tip = pol(a, 1.9f); val b1 = pol(a + 8f, 0.4f); val b2 = pol(a - 8f, 0.4f)
        P.poly(argbA(0xFFB388FF, 0.14f + 0.08f * kotlin.math.sin(t * 2f + i)), 0L, 0f, b1.first, cy + b1.second, tip.first, cy + tip.second - 1.2f, b2.first, cy + b2.second)
    }
    P.ellipse(0f, cy, 1.55f, 0.46f, argbA(0xFF7C4DFF, 0.35f), 0L, 0f)
    P.ellipse(0f, cy, 1.4f, 0.4f, 0L, 0xFFFF9100, 0.14f)
    P.ellipse(0f, cy, 1.1f, 0.3f, 0L, 0xFFFFD180, 0.07f)
    P.ellipse(0f, cy, 0.8f, 0.22f, 0xFF000000, 0xFF0B0A14, 0.06f)
    for (k in 0..9) {
        val th = t * (1.2f + (k % 3) * 0.35f) + k * 0.628f
        val rr = 1.0f + (k % 4) * 0.16f
        val x = kotlin.math.cos(th) * rr * 1.1f
        val y = cy + kotlin.math.sin(th) * rr * 0.32f
        val a = if (kotlin.math.sin(th) > 0f) 1f else 0.5f
        P.circle(x, y, 0.07f + 0.03f * (k % 3), argbA(if (k % 2 == 0) 0xFFFFE0B2 else 0xFFB388FF, a), 0L, 0f)
    }
    P.circle(0f, cy - 0.02f, 0.14f, argbA(0xFFFFFFFF, 0.5f + 0.4f * kotlin.math.sin(t * 3f)), 0L, 0f)
}

/**
 * Flame clusters on both fists and both heels. style 0 = Inferno (fast flicker, rising embers),
 * 1 = Azure (tall, steady, sparks drift sideways), 2 = Violet (S-curve ghost-fire with floating orbs).
 */
private fun flameSet(P: PartPainter, c1: Long, c2: Long, c3: Long, style: Int) {
    val t = P.time
    val speed = when (style) { 0 -> 1.0f; 1 -> 0.55f; else -> 0.8f }
    val tall = if (style == 1) 1.35f else 1f
    P.circle(0f, -0.3f, 1.0f, argbA(c1, 0.16f), 0L, 0f)
    for (k in 0..3) {
        val bx = (k - 1.5f) * 0.3f
        val hgt = tall * (1.55f + 0.5f * kotlin.math.sin(t * speed * (6f + k * 1.3f) + k * 1.7f)) * (if (k == 1 || k == 2) 1.15f else 0.85f)
        var lean = -0.55f * hgt
        if (style == 2) lean += kotlin.math.sin(t * 3f + k) * 0.55f
        if (style == 1) lean *= 0.55f
        for (layer in 0..2) {
            val sc = when (layer) { 0 -> 1f; 1 -> 0.7f; else -> 0.4f }
            val col = when (layer) { 0 -> c1; 1 -> c2; else -> c3 }
            val h = hgt * sc; val w = 0.24f * (if (layer == 0) 1f else 0.8f * sc)
            val wob = if (style == 2) kotlin.math.sin(t * 4f + k + layer) * 0.18f else 0f
            P.poly(col, 0L, 0f,
                bx - w, 0.05f,
                bx - w * 0.85f + lean * 0.3f * sc + wob, -h * 0.45f,
                bx + lean * sc + wob * 2f, -h,
                bx + w * 0.85f + lean * 0.35f * sc + wob, -h * 0.5f,
                bx + w, 0.05f)
        }
    }
    for (k in 0..4) {
        val ph = (t * (0.6f + (k % 3) * 0.2f) * speed + k * 0.23f) % 1f
        val x: Float; val y: Float; val r: Float
        when (style) {
            1 -> { x = -ph * 2.2f; y = -0.6f - ph * 1.2f; r = 0.06f * (1f - ph) }
            2 -> { x = kotlin.math.sin(ph * 6f + k) * 0.5f - ph * 0.5f; y = -0.4f - ph * 2.6f; r = 0.11f * (1f - ph * 0.5f) }
            else -> { x = -ph * 1.3f + (k - 2) * 0.15f; y = -0.4f - ph * 2.3f; r = 0.07f * (1f - ph) }
        }
        P.circle(x, y, r, argbA(c3, 1f - ph), 0L, 0f)
    }
}
