package com.neonrush.game.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Showpiece helmets and boots (premium, real-money pieces). Same painter rules as PilotParts.kt: everything is drawn in
 * helmet-radius units around the anchor (helmet = HEAD anchor, boots = one anchor per foot), y points down, the pilot
 * faces right. Everything here is animated from [PartPainter.time].
 *
 * HELMETS: Ronin Kabuto, Jade Dragon Helm, Stormcaller Helm, Glacier Crown
 * BOOTS:   Thunder Striders, Jet Hover Boots, Crimson Talons, Prism Runners
 */
internal fun paintPartById2(id: String, P: PartPainter): Boolean {
    when (id) {
        "helm_samurai" -> npSamurai(P)
        "helm_dragon" -> npDragon(P)
        "helm_storm" -> npStorm(P)
        "helm_glacier" -> npGlacier(P)
        "boots_thunder" -> npThunder(P)
        "boots_jet" -> npJet(P)
        "boots_talon" -> npTalon(P)
        "boots_prism" -> npPrism(P)
        else -> return false
    }
    return true
}

// ------------------------------------------------------------------------------------------------ helpers

private fun npA(c: Long, a: Float): Long = (((a.coerceIn(0f, 1f) * 255f).toInt().toLong()) shl 24) or (c and 0xFFFFFFL)
private const val INK = 0xFF0B0A14

private fun npRnd(a: Int, b: Int): Float {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177.toInt()
    return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
}

private fun npHue(h: Float): Long = (Color.hsv(((h % 1f) + 1f) % 1f * 360f, 0.85f, 1f).toArgb().toLong()) and 0xFFFFFFFFL

/** A crackling bolt: dark outline, coloured body, white core. The shape re-rolls ~14 times a second. */
private fun npBolt(P: PartPainter, x0: Float, y0: Float, x1: Float, y1: Float, seed: Int, col: Long, w: Float, jag: Float = 0.2f, segs: Int = 5, alpha: Float = 1f) {
    val frame = (P.time * 14f).toInt()
    var px = x0; var py = y0
    val dx = x1 - x0; val dy = y1 - y0
    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
    val nx = -dy / len; val ny = dx / len
    for (i in 1..segs) {
        val f = i.toFloat() / segs
        val o = if (i == segs) 0f else (npRnd(frame + seed, i) - 0.5f) * 2f * jag
        val qx = x0 + dx * f + nx * o; val qy = y0 + dy * f + ny * o
        P.line(px, py, qx, qy, npA(INK, alpha), w * 2.1f)
        px = qx; py = qy
    }
    px = x0; py = y0
    for (i in 1..segs) {
        val f = i.toFloat() / segs
        val o = if (i == segs) 0f else (npRnd(frame + seed, i) - 0.5f) * 2f * jag
        val qx = x0 + dx * f + nx * o; val qy = y0 + dy * f + ny * o
        P.line(px, py, qx, qy, npA(col, alpha), w * 1.2f)
        P.line(px, py, qx, qy, npA(0xFFFFFFFF, alpha), w * 0.45f)
        px = qx; py = qy
    }
}

private fun npGlint(P: PartPainter, x: Float, y: Float, s: Float, col: Long, a: Float) {
    if (a <= 0.02f) return
    P.line(x - s, y, x + s, y, npA(col, a), s * 0.28f)
    P.line(x, y - s, x, y + s, npA(col, a), s * 0.28f)
    P.circle(x, y, s * 0.28f, npA(0xFFFFFFFF, a), 0L, 0f)
}

// ------------------------------------------------------------------------------------------------ HELMETS

/** RONIN KABUTO: lacquered war helm, golden crescent crest with a burning jewel, sparks, swaying neck plates. */
private fun npSamurai(P: PartPainter) {
    val t = P.time
    // neck guard plates, swaying behind the head
    P.sway(3.5f, 2.6f)
    P.poly(0xFF7A0C1A, INK, 0.09f, -0.9f, -0.15f, -1.7f, 0.05f, -1.62f, 0.42f, -0.8f, 0.3f)
    P.poly(0xFF8E1020, INK, 0.09f, -0.85f, 0.3f, -1.62f, 0.45f, -1.5f, 0.82f, -0.75f, 0.68f)
    P.poly(0xFF7A0C1A, INK, 0.09f, -0.8f, 0.68f, -1.5f, 0.85f, -1.36f, 1.2f, -0.7f, 1.05f)
    P.line(-0.9f, -0.15f, -1.7f, 0.05f, 0xFFFFD23F, 0.06f)
    P.line(-0.85f, 0.3f, -1.62f, 0.45f, 0xFFFFD23F, 0.06f)
    P.line(-0.8f, 0.68f, -1.5f, 0.85f, 0xFFFFD23F, 0.06f)
    P.sway(0f, 0f)
    // lacquered dome
    P.shadow(0f, -0.4f, 1.3f, 0.35f)
    P.gpoly(0xFFD0293C, 0xFF4A0A12, INK, 0.12f,
        -1.03f, -0.1f, -0.95f, -0.55f, -0.6f, -0.92f, 0f, -1.12f, 0.6f, -0.95f, 0.95f, -0.55f, 1.0f, -0.25f,
        0.6f, -0.45f, 0f, -0.52f, -0.65f, -0.42f)
    // gloss streak + riveted ridge
    P.poly(npA(0xFFFFFFFF, 0.32f), 0L, 0f, -0.55f, -0.8f, -0.1f, -1.0f, 0.1f, -0.93f, -0.4f, -0.72f)
    for (k in 0..4) P.circle(-0.7f + k * 0.35f, -0.5f + abs(k - 2) * 0.05f, 0.045f, 0xFFFFD23F, INK, 0.02f)
    // gold brow band
    P.line(-0.9f, -0.42f, 0.9f, -0.32f, INK, 0.2f)
    P.line(-0.9f, -0.42f, 0.9f, -0.32f, 0xFFFFD23F, 0.11f)
    // maedate: golden crescent crest
    val bright = 0.5f + 0.5f * sin(t * 3f)
    P.gpoly(0xFFFFF3A8, 0xFFE0A100, INK, 0.1f, -0.05f, -1.02f, -0.95f, -2.35f, -0.55f, -1.45f, -0.2f, -1.12f)
    P.gpoly(0xFFFFF3A8, 0xFFE0A100, INK, 0.1f, 0.1f, -1.02f, 1.0f, -2.2f, 0.62f, -1.4f, 0.28f, -1.1f)
    // jewel: pulsing ember
    P.circle(0.04f, -1.2f, 0.4f + 0.05f * bright, npA(0xFFFF6D00, 0.28f), 0L, 0f)
    P.circle(0.04f, -1.2f, 0.22f, 0xFFFF3D00, INK, 0.06f)
    P.circle(0.0f, -1.25f, 0.08f, npA(0xFFFFFFFF, 0.9f), 0L, 0f)
    // light running up the blades
    val run = (t * 0.9f) % 1f
    npGlint(P, 0.04f - 0.96f * run * 0.9f - 0.05f, -1.15f - 1.15f * run, 0.16f, 0xFFFFF8E1, (1f - run))
    npGlint(P, 0.1f + 0.9f * run * 0.9f, -1.1f - 1.05f * run, 0.16f, 0xFFFFF8E1, (1f - run))
    // rising embers
    for (k in 0..5) {
        val ph = (t * 0.55f + k * 0.17f) % 1f
        val x = 0.04f + (npRnd(k, 3) - 0.5f) * 0.8f + sin(t * 3f + k) * 0.12f
        val y = -1.3f - ph * 1.6f
        P.circle(x, y, 0.06f * (1f - ph * 0.6f), npA(if (k % 2 == 0) 0xFFFFAB40 else 0xFFFF5722, 1f - ph), 0L, 0f)
    }
}

/** JADE DRAGON HELM: a snarling dragon head crowns the helmet, horns sweep back, eye burns, smoke curls from the nostrils. */
private fun npDragon(P: PartPainter) {
    val t = P.time
    // spine ridge running back along the crown
    P.sway(5f, 3f)
    for (k in 0..4) {
        val x = -0.1f - k * 0.3f
        val h = 0.55f - k * 0.06f
        P.poly(0xFF15A37C, INK, 0.07f, x, -0.9f, x - 0.22f, -0.9f - h, x - 0.42f, -0.75f)
    }
    // swept-back horns
    P.poly(0xFFFFE9B0, INK, 0.1f, -0.25f, -0.95f, -1.7f, -1.55f, -1.4f, -1.9f, -0.6f, -1.25f, 0.0f, -1.1f)
    P.poly(0xFFFFE9B0, INK, 0.1f, 0.05f, -0.98f, -1.15f, -2.05f, -0.85f, -2.35f, -0.1f, -1.4f)
    P.sway(0f, 0f)
    // the head: snout over the brow
    P.shadow(0.4f, -0.8f, 1.2f, 0.3f)
    P.gpoly(0xFF45F0B8, 0xFF0A5E4A, INK, 0.12f,
        -0.5f, -0.85f, 0.1f, -1.3f, 0.8f, -1.35f, 1.5f, -1.15f, 1.78f, -0.95f, 1.5f, -0.78f, 0.9f, -0.82f, 0.3f, -0.68f, -0.3f, -0.62f)
    // jaw line and teeth
    P.line(0.35f, -0.76f, 1.62f, -0.88f, INK, 0.08f)
    for (k in 0..3) P.poly(0xFFFFFFFF, INK, 0.03f, 0.75f + k * 0.26f, -0.8f - k * 0.02f, 0.85f + k * 0.26f, -0.62f, 0.95f + k * 0.26f, -0.82f - k * 0.02f)
    // scale marks
    for (k in 0..4) P.circle(-0.05f + k * 0.25f, -1.03f - 0.02f * k, 0.05f, 0L, npA(0xFF0A5E4A, 0.9f), 0.03f)
    // eye: glows and blinks
    val blink = if ((t * 0.9f) % 3.2f > 3.05f) 0.15f else 1f
    P.circle(0.5f, -1.08f, 0.2f, npA(0xFFFFD600, 0.35f), 0L, 0f)
    P.ellipse(0.5f, -1.08f, 0.15f, 0.08f * blink, 0xFFFFEA00, INK, 0.04f)
    P.ellipse(0.52f, -1.08f, 0.03f, 0.06f * blink, INK, 0L, 0f)
    // nostril smoke curls
    for (k in 0..3) {
        val ph = (t * 0.5f + k * 0.25f) % 1f
        val x = 1.62f + ph * 0.9f + sin(ph * 7f + k) * 0.12f
        val y = -1.12f - ph * 0.9f
        P.circle(x, y, 0.08f + 0.17f * ph, npA(0xFFB0FFE6, 0.55f * (1f - ph)), 0L, 0f)
    }
    // occasional ember flash at the mouth
    val br = (sin(t * 1.4f) - 0.6f).coerceAtLeast(0f) * 2.5f
    if (br > 0f) P.circle(1.7f, -0.9f, 0.16f * br, npA(0xFFFF9100, 0.8f), 0L, 0f)
}

/** STORMCALLER HELM: steel winged helm. Two electrodes throw lightning between them and out into the sky. */
private fun npStorm(P: PartPainter) {
    val t = P.time
    // side fins swept back
    P.gpoly(0xFFB0BEC5, 0xFF37474F, INK, 0.1f, -0.7f, -0.35f, -1.75f, -0.95f, -1.35f, -0.4f, -0.8f, -0.05f)
    P.gpoly(0xFFB0BEC5, 0xFF37474F, INK, 0.1f, -0.75f, 0.0f, -1.6f, -0.2f, -1.25f, 0.2f, -0.8f, 0.2f)
    P.line(-1.1f, -0.62f, -1.6f, -0.9f, 0xFF00E5FF, 0.05f)
    // dome
    P.shadow(0f, -0.4f, 1.3f, 0.3f)
    P.gpoly(0xFFECEFF1, 0xFF455A64, INK, 0.12f,
        -1.0f, -0.1f, -0.9f, -0.62f, -0.4f, -1.0f, 0.3f, -1.08f, 0.8f, -0.8f, 1.0f, -0.3f, 0.6f, -0.45f, 0f, -0.5f, -0.65f, -0.4f)
    P.poly(npA(0xFFFFFFFF, 0.35f), 0L, 0f, -0.55f, -0.8f, -0.1f, -1.0f, 0.15f, -0.98f, -0.35f, -0.72f)
    // central bolt crest
    P.poly(0xFFFFEA00, INK, 0.08f, 0.0f, -0.98f, -0.28f, -0.5f, -0.05f, -0.55f, -0.2f, -0.1f, 0.18f, -0.62f, -0.02f, -0.6f, 0.25f, -0.98f)
    // electrodes
    val ex0 = -0.45f; val ex1 = 0.55f; val ey = -1.9f
    P.poly(0xFF90A4AE, INK, 0.07f, ex0 - 0.1f, -1.0f, ex0, ey + 0.15f, ex0 + 0.1f, -1.0f)
    P.poly(0xFF90A4AE, INK, 0.07f, ex1 - 0.1f, -1.0f, ex1, ey + 0.15f, ex1 + 0.1f, -1.0f)
    val flash = 0.55f + 0.45f * sin(t * 18f)
    P.circle(ex0, ey, 0.17f + 0.05f * flash, npA(0xFF00E5FF, 0.4f), 0L, 0f)
    P.circle(ex1, ey, 0.17f + 0.05f * flash, npA(0xFF00E5FF, 0.4f), 0L, 0f)
    P.circle(ex0, ey, 0.07f, 0xFFFFFFFF, 0L, 0f)
    P.circle(ex1, ey, 0.07f, 0xFFFFFFFF, 0L, 0f)
    // lightning: between the tips, and strikes into the sky
    npBolt(P, ex0, ey, ex1, ey, 1, 0xFF4DD0FF, 0.07f, 0.22f, 5)
    val strike = (t * 2.3f) % 1f
    if (strike < 0.45f) {
        val sx = if (((t * 2.3f).toInt()) % 2 == 0) ex0 else ex1
        npBolt(P, sx, ey, sx + (npRnd((t * 2.3f).toInt(), 9) - 0.5f) * 1.4f, ey - 1.3f - 0.5f * strike, 7, 0xFF80DEEA, 0.065f, 0.3f, 6, 1f - strike * 1.6f)
    }
    // crackle sparks around the dome
    for (k in 0..3) {
        val f = (t * 6f + k * 1.7f)
        if (sin(f) > 0.6f) npGlint(P, -0.9f + npRnd((f / 6.28f).toInt(), k) * 1.8f, -0.2f - npRnd(k, 5) * 0.9f, 0.14f, 0xFF80F0FF, 1f)
    }
}

/** GLACIER CROWN: spires of living ice. A prism glare sweeps across them and snow-sparkle drifts around the head. */
private fun npGlacier(P: PartPainter) {
    val t = P.time
    val spires = floatArrayOf(-0.85f, 0.7f, -0.45f, 1.2f, 0f, 1.85f, 0.45f, 1.25f, 0.8f, 0.75f)
    P.circle(0f, -1.3f, 1.5f, npA(0xFF80DEEA, 0.14f + 0.05f * sin(t * 2f)), 0L, 0f)
    P.shadow(0f, -0.9f, 1.3f, 0.25f)
    // back spires first (taller ones in the middle), then a shared base band
    for (k in 0..4) {
        val x = spires[k * 2]; val hh = spires[k * 2 + 1]
        val w = 0.26f
        P.gpoly(0xFFF4FEFF, 0xFF4FC3F7, 0xFF01579B, 0.07f, x - w, -0.95f, x - w * 0.55f, -0.95f - hh * 0.8f, x, -0.95f - hh, x + w * 0.6f, -0.95f - hh * 0.75f, x + w, -0.95f)
        P.poly(npA(0xFFFFFFFF, 0.55f), 0L, 0f, x - w * 0.5f, -0.95f, x - w * 0.3f, -0.95f - hh * 0.7f, x, -0.95f - hh * 0.95f, x - w * 0.05f, -0.95f)
        P.line(x + 0.02f, -0.95f, x + w * 0.35f, -0.95f - hh * 0.6f, npA(0xFF0277BD, 0.6f), 0.03f)
    }
    P.gpoly(0xFFE1F5FE, 0xFF29B6F6, 0xFF01579B, 0.09f, -1.0f, -0.55f, -0.85f, -1.0f, 0.9f, -1.0f, 1.0f, -0.55f, 0.5f, -0.7f, -0.5f, -0.7f)
    // prism glare sweeping left -> right
    val sw = (t * 0.45f) % 1.6f
    val gx = -1.2f + sw * 2.6f
    P.poly(npA(0xFFFFFFFF, 0.5f), 0L, 0f, gx, -0.9f, gx + 0.22f, -0.9f, gx + 0.5f, -2.2f, gx + 0.28f, -2.2f)
    // gem at the brow, cycling through ice colours
    P.circle(0f, -0.82f, 0.13f, npHue(0.5f + 0.12f * sin(t)), INK, 0.04f)
    // snow sparkle
    for (k in 0..6) {
        val ph = (t * 0.35f + k * 0.14f) % 1f
        val x = -1.2f + npRnd(k, 11) * 2.4f
        val y = -2.6f + ph * 3.0f
        npGlint(P, x + sin(t * 2f + k) * 0.1f, y, 0.09f + 0.05f * npRnd(k, 4), 0xFFE1F5FE, sin(ph * 3.14f))
    }
}

// ------------------------------------------------------------------------------------------------ BOOTS

/** THUNDER STRIDERS: armoured greaves wrapped in arcing current; every stride throws a shock ring and sparks. */
private fun npThunder(P: PartPainter) {
    val t = P.time
    P.shadow(0.1f, 0.5f, 1.2f, 0.3f)
    // boot shell
    P.gpoly(0xFF546E7A, 0xFF1C2833, INK, 0.1f, -0.55f, -0.55f, 0.65f, -0.55f, 0.75f, 0.2f, 1.1f, 0.62f, 1.08f, 0.95f, -0.6f, 0.95f, -0.62f, 0.3f)
    // cuff
    P.gpoly(0xFF90A4AE, 0xFF37474F, INK, 0.09f, -0.62f, -0.75f, 0.72f, -0.75f, 0.68f, -0.4f, -0.58f, -0.4f)
    // lightning glyph on the shin
    P.poly(0xFFFFEA00, INK, 0.05f, 0.05f, -0.35f, -0.2f, 0.25f, 0.02f, 0.22f, -0.1f, 0.65f, 0.28f, 0.12f, 0.08f, 0.15f, 0.22f, -0.35f)
    // glowing sole
    val pulse = 0.6f + 0.4f * sin(t * 7f)
    P.line(-0.6f, 0.97f, 1.1f, 0.97f, npA(0xFF00E5FF, pulse), 0.14f)
    // current coiling round the ankle
    npBolt(P, -0.7f, -0.55f, 0.8f, -0.45f, 11, 0xFF4DD0FF, 0.05f, 0.16f, 5)
    npBolt(P, 0.75f, -0.7f, -0.6f, -0.35f, 23, 0xFF80F0FF, 0.045f, 0.2f, 5, 0.85f)
    // stride shock rings and trailing sparks
    val ph = (t * 1.8f) % 1f
    P.ellipse(0.2f, 1.0f, 0.5f + 1.5f * ph, 0.12f + 0.3f * ph, 0L, npA(0xFF80F0FF, 1f - ph), 0.05f)
    for (k in 0..4) {
        val f = (t * 1.5f + k * 0.2f) % 1f
        P.circle(-0.7f - f * 1.6f, 0.9f - f * 0.5f + (npRnd(k, 2) - 0.5f) * 0.3f, 0.06f * (1f - f), npA(0xFFB2EBF2, 1f - f), 0L, 0f)
    }
}

/** JET HOVER BOOTS: turbine boots with a roaring blue-white flame, heat haze, and a fierce core. */
private fun npJet(P: PartPainter) {
    val t = P.time
    // flame first, so the boot sits in front of it
    val flick = 0.82f + 0.18f * sin(t * 30f) + 0.1f * sin(t * 47f)
    val len = 2.4f * flick
    for (layer in 0..2) {
        val s = when (layer) { 0 -> 1f; 1 -> 0.68f; else -> 0.38f }
        val col = when (layer) { 0 -> npA(0xFFFF6D00, 0.85f); 1 -> npA(0xFFFFD54F, 0.95f); else -> 0xFFE3F9FF }
        P.poly(col, 0L, 0f,
            -0.5f, 0.55f, -0.95f - len * 0.5f * s, 0.9f + len * 0.55f * s + 0.2f * sin(t * 22f + layer),
            -1.0f - len * 0.78f * s, 1.15f + len * 0.78f * s,
            -0.45f, 1.0f)
    }
    for (k in 0..3) {   // shock diamonds in the exhaust
        val f = 0.25f + k * 0.2f
        P.circle(-0.7f - len * 0.55f * f, 0.75f + len * 0.6f * f, 0.1f * (1f - f * 0.6f), npA(0xFFFFFFFF, 0.7f), 0L, 0f)
    }
    // boot body
    P.shadow(0.1f, 0.5f, 1.2f, 0.3f)
    P.gpoly(0xFFFF8A3D, 0xFF4E1B00, INK, 0.1f, -0.55f, -0.55f, 0.65f, -0.55f, 0.72f, 0.2f, 1.1f, 0.62f, 1.08f, 0.92f, -0.6f, 0.92f, -0.62f, 0.3f)
    P.gpoly(0xFFB0BEC5, 0xFF37474F, INK, 0.09f, -0.62f, -0.75f, 0.72f, -0.75f, 0.68f, -0.4f, -0.58f, -0.4f)
    // turbine pod at the heel
    P.circle(-0.55f, 0.55f, 0.42f, 0xFF263238, INK, 0.08f)
    for (k in 0..2) {
        val a = t * 24f + k * 2.094f
        P.line(-0.55f, 0.55f, -0.55f + cos(a) * 0.36f, 0.55f + sin(a) * 0.36f, npA(0xFF80F0FF, 0.9f), 0.07f)
    }
    P.circle(-0.55f, 0.55f, 0.1f, 0xFFFFFFFF, 0L, 0f)
    // vents
    for (k in 0..2) P.line(0.1f + k * 0.28f, 0.1f, 0.2f + k * 0.28f, 0.5f, npA(0xFFFFD54F, 0.6f + 0.4f * sin(t * 8f + k)), 0.06f)
    // heat haze
    P.ellipse(-1.4f, 1.2f, 0.9f, 0.3f, npA(0xFFFFAB40, 0.08f + 0.05f * sin(t * 9f)), 0L, 0f)
}

/** CRIMSON TALONS: predator boots, a curved claw at every toe, veins of red light and dripping embers. */
private fun npTalon(P: PartPainter) {
    val t = P.time
    P.shadow(0.2f, 0.5f, 1.2f, 0.3f)
    // boot
    P.gpoly(0xFF5A1018, 0xFF14070A, INK, 0.1f, -0.55f, -0.55f, 0.65f, -0.55f, 0.75f, 0.2f, 1.15f, 0.6f, 1.1f, 0.95f, -0.6f, 0.95f, -0.62f, 0.3f)
    // armour plates
    P.gpoly(0xFFB71C1C, 0xFF3E0A0E, INK, 0.08f, -0.6f, -0.7f, 0.7f, -0.7f, 0.66f, -0.3f, -0.55f, -0.25f)
    P.gpoly(0xFF8E1020, 0xFF2A070C, INK, 0.08f, -0.5f, -0.2f, 0.62f, -0.15f, 0.7f, 0.25f, -0.52f, 0.3f)
    // claws
    P.poly(0xFFFFF3E0, INK, 0.07f, 0.85f, 0.55f, 1.7f, 0.65f, 1.95f, 1.15f, 1.5f, 0.95f, 1.0f, 0.85f)
    P.poly(0xFFFFF3E0, INK, 0.07f, 0.7f, 0.3f, 1.55f, 0.15f, 1.95f, 0.5f, 1.45f, 0.5f, 0.95f, 0.6f)
    P.poly(0xFFFFF3E0, INK, 0.06f, 0.75f, 0.85f, 1.45f, 1.1f, 1.55f, 1.4f, 1.1f, 1.15f, 0.8f, 1.0f)
    // heel spur
    P.poly(0xFFFFF3E0, INK, 0.07f, -0.55f, 0.55f, -1.4f, 0.75f, -1.2f, 1.05f, -0.5f, 0.85f)
    // pulsing veins
    val glow = 0.55f + 0.45f * sin(t * 4.5f)
    P.line(-0.45f, -0.55f, -0.1f, 0.0f, npA(0xFFFF1744, glow), 0.07f)
    P.line(-0.1f, 0.0f, 0.3f, 0.4f, npA(0xFFFF1744, glow), 0.07f)
    P.line(0.4f, -0.5f, 0.55f, 0.05f, npA(0xFFFF1744, glow), 0.06f)
    P.circle(0.1f, 0.15f, 0.55f, npA(0xFFFF1744, 0.1f * glow), 0L, 0f)
    // dripping embers
    for (k in 0..3) {
        val f = (t * 0.7f + k * 0.25f) % 1f
        P.circle(0.2f + k * 0.28f - 0.3f, 0.95f + f * 0.9f, 0.06f * (1f - f), npA(0xFFFF5722, 1f - f), 0L, 0f)
    }
}

/** PRISM RUNNERS: neon sneakers whose sole cycles through every colour; star dust and glass wings stream behind. */
private fun npPrism(P: PartPainter) {
    val t = P.time
    // glass wing at the heel
    P.sway(7f, 4f)
    P.poly(npA(npHue(t * 0.2f), 0.45f), npA(0xFFFFFFFF, 0.8f), 0.04f, -0.5f, 0.45f, -1.9f, -0.35f, -1.55f, 0.15f)
    P.poly(npA(npHue(t * 0.2f + 0.3f), 0.45f), npA(0xFFFFFFFF, 0.8f), 0.04f, -0.5f, 0.55f, -1.7f, 0.2f, -1.2f, 0.55f)
    P.sway(0f, 0f)
    P.shadow(0.1f, 0.5f, 1.2f, 0.25f)
    // sneaker
    P.gpoly(0xFFFFFFFF, 0xFFB0BEC5, INK, 0.1f, -0.55f, -0.5f, 0.6f, -0.5f, 0.72f, 0.2f, 1.12f, 0.6f, 1.08f, 0.9f, -0.6f, 0.9f, -0.62f, 0.3f)
    // colour-cycling panels
    P.poly(npA(npHue(t * 0.25f), 0.95f), 0L, 0f, -0.5f, -0.1f, 0.55f, -0.05f, 0.62f, 0.3f, -0.55f, 0.35f)
    P.poly(npA(npHue(t * 0.25f + 0.33f), 0.95f), 0L, 0f, 0.65f, 0.35f, 1.05f, 0.62f, 1.02f, 0.82f, 0.55f, 0.7f)
    P.line(-0.55f, -0.38f, 0.6f, -0.38f, npHue(t * 0.25f + 0.66f), 0.09f)
    // rainbow sole
    for (k in 0..5) {
        val x0 = -0.62f + k * 0.29f
        P.line(x0, 0.97f, x0 + 0.3f, 0.97f, npHue(t * 0.4f + k * 0.12f), 0.13f)
    }
    // star dust trail
    for (k in 0..6) {
        val f = (t * 1.1f + k * 0.15f) % 1f
        npGlint(P, -0.6f - f * 2.3f, 0.7f + (npRnd(k, 6) - 0.5f) * 0.7f, 0.12f * (1f - f * 0.4f), npHue(t * 0.4f + k * 0.14f), 1f - f)
    }
}
