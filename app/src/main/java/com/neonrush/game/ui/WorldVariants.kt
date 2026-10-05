package com.neonrush.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.World
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Rift variants": zero-new-asset way to make later zones feel like brand-new
 * worlds. A variant re-colors the existing art (hue shift), swaps the sky and
 * foreground layers with ones borrowed from other worlds, and adds a code-drawn
 * weather effect. Variants only exist beyond zone 40 / in Phase 2-4 worlds,
 * which are Pro content, so this is automatically a Pro-tier feature.
 */
enum class Weather { NONE, EMBERS, RAIN, SNOW, FOG, GLITCH }

data class WorldVariant(
    val key: String,          // changes whenever a "new world" begins (drives the entry card)
    val baseFamily: Int,      // which main world's art/boss is the base (1-5)
    val suffix: String,
    val hueDegrees: Float,
    val brightness: Float,
    val weather: Weather,
    val accent: Color,
    val skyDonor: Int,        // world whose sky replaces the base sky
    val frontDonor: Int,      // world whose foreground replaces the base foreground
    val isRift: Boolean,      // true = zones past the defined worlds (cycled)
    val riftNumber: Int
) {
    /** Hue-rotate + brightness as a 4x5 color matrix (alpha untouched). */
    fun colorMatrix(): ColorMatrix {
        val r = hueDegrees * PI.toFloat() / 180f
        val c = cos(r); val s = sin(r); val b = brightness
        return ColorMatrix(floatArrayOf(
            (0.213f + c * 0.787f - s * 0.213f) * b, (0.715f - c * 0.715f - s * 0.715f) * b, (0.072f - c * 0.072f + s * 0.928f) * b, 0f, 0f,
            (0.213f - c * 0.213f + s * 0.143f) * b, (0.715f + c * 0.285f + s * 0.140f) * b, (0.072f - c * 0.072f - s * 0.283f) * b, 0f, 0f,
            (0.213f - c * 0.213f - s * 0.787f) * b, (0.715f - c * 0.715f + s * 0.715f) * b, (0.072f + c * 0.928f + s * 0.072f) * b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
    }
}

object WorldVariants {
    const val FIRST_RIFT_ZONE = 41
    const val RIFT_BLOCK = 8 // zones per "new world" in the cycled stretch

    private data class Palette(val suffix: String, val hue: Float, val bright: Float, val weather: Weather, val accent: Color)

    private val palettes = listOf(
        Palette("EMBERFALL",     -25f, 1.05f, Weather.EMBERS, Color(0xFFFF7A1A)),
        Palette("TOXIC DRIFT",   110f, 1.00f, Weather.FOG,    Color(0xFF7CFF3A)),
        Palette("COLD STATIC",   190f, 1.05f, Weather.SNOW,   Color(0xFF7FDBFF)),
        Palette("SOLAR FLARE",   -70f, 1.10f, Weather.EMBERS, Color(0xFFFFD23F)),
        Palette("VOID BLOOM",     60f, 1.00f, Weather.GLITCH, Color(0xFFC060FF)),
        Palette("AURORA SECTOR", 150f, 1.05f, Weather.SNOW,   Color(0xFF3AFFC8)),
        Palette("BLOOD MOON",   -140f, 0.95f, Weather.RAIN,   Color(0xFFFF2B4D)),
        Palette("GHOST GRID",    230f, 0.90f, Weather.RAIN,   Color(0xFF9AA8FF))
    )

    // Free tier gets 3 of the 8 looks, no weather, and only free-world art (1-3).
    private val freePaletteIdx = listOf(0, 2, 6) // EMBERFALL, COLD STATIC, BLOOD MOON

    /**
     * null = normal (un-remixed) rendering.
     * isPro = Pro subscriber (or the testing bypass). Free players still get
     * the cycled "new world" feel in the free-tier zones past 40 (that is where
     * the Pro gate redirects them), but with the lite set: free-world art only,
     * 3 looks, no weather. Pro gets all 5 worlds, 8 looks, weather, and variants
     * inside the Phase 2-4 worlds.
     */
    fun variantFor(world: World, zone: Int, isPro: Boolean): WorldVariant? {
        // Special-mission / New Game+ worlds (ids 6-11) have their own art.
        if (world.id in 6..11) return null
        val inGap = zone >= FIRST_RIFT_ZONE && zone !in world.startZone..world.endZone
        if (!inGap && world.phase < 2) return null
        // Phase 2-4 worlds are Pro-only; a free player only sees the 4-second
        // gate preview there, which keeps the plain base art.
        if (!inGap && !isPro) return null

        val block = if (inGap) (zone - FIRST_RIFT_ZONE) / RIFT_BLOCK else 0
        val familyCount = if (isPro) 5 else 3
        val family = if (inGap) block % familyCount + 1 else world.worldFamily
        val pal = when {
            !inGap -> palettes[(world.id * 3 + world.phase) % palettes.size]
            isPro -> palettes[block % palettes.size]
            else -> palettes[freePaletteIdx[(block + block / 3) % freePaletteIdx.size]]
        }
        val seed = if (inGap) block * 7 + 3 else world.id

        // Donor art: Pro borrows from any main world 2-5; free only from 2-3.
        // (6/7/8 stay mission rewards; world 1's full-bleed layers aren't used as donors.)
        val pool = (2..(if (isPro) 5 else 3)).filter { it != family }
        val skyDonor = pool.getOrElse(seed % pool.size.coerceAtLeast(1)) { family }
        val frontDonor = pool.getOrElse((seed / 3 + 1) % pool.size.coerceAtLeast(1)) { family }

        return WorldVariant(
            key = if (inGap) "rift$block" else "world${world.id}",
            baseFamily = family, suffix = pal.suffix, hueDegrees = pal.hue,
            brightness = pal.bright,
            weather = if (isPro) pal.weather else Weather.NONE,
            accent = pal.accent,
            skyDonor = skyDonor, frontDonor = frontDonor,
            isRift = inGap, riftNumber = block + 1
        )
    }
}

// --- Weather overlay (all drawn in code, no assets) ------------------------

private fun rnd(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177.toInt()
    x = x xor (x ushr 16)
    return (x and 0x7fffffff) / 2147483647f
}

@Composable
fun WorldWeatherOverlay(weather: Weather, accent: Color, modifier: Modifier = Modifier) {
    if (weather == Weather.NONE) return
    val transition = rememberInfiniteTransition(label = "weather")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "weatherT"
    )
    val twoPi = (2 * PI).toFloat()
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        when (weather) {
            Weather.EMBERS -> repeat(36) { i ->
                val k = 1 + (rnd(i, 3) * 2).toInt() // integer speed => seamless loop
                val y = h - ((rnd(i, 2) + t * k) % 1f) * h
                val x = rnd(i, 1) * w + sin(t * twoPi * k + i) * 18f
                drawCircle(accent.copy(alpha = 0.75f), radius = 2f + rnd(i, 4) * 3f, center = Offset(x, y))
            }
            Weather.RAIN -> repeat(70) { i ->
                val k = 2 + (rnd(i, 3) * 3).toInt()
                val y = ((rnd(i, 2) + t * k) % 1f) * h
                val x = rnd(i, 1) * w
                drawLine(accent.copy(alpha = 0.35f), Offset(x, y), Offset(x - 6f, y + 28f), strokeWidth = 2f)
            }
            Weather.SNOW -> repeat(50) { i ->
                val y = ((rnd(i, 2) + t) % 1f) * h
                val x = rnd(i, 1) * w + sin(t * twoPi + i) * 22f
                drawCircle(Color.White.copy(alpha = 0.7f), radius = 2f + rnd(i, 4) * 2.5f, center = Offset(x, y))
            }
            Weather.FOG -> repeat(3) { i ->
                val a = 0.07f + 0.06f * sin(twoPi * (t + i / 3f))
                val top = h * (0.25f + 0.25f * i)
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, accent.copy(alpha = a), Color.Transparent),
                        startY = top, endY = top + h * 0.35f
                    ),
                    topLeft = Offset(0f, top), size = Size(w, h * 0.35f)
                )
            }
            Weather.GLITCH -> {
                val step = (t * 24).toInt()
                repeat(6) { j ->
                    if (rnd(step, j * 7 + 1) > 0.72f) {
                        drawRect(
                            accent.copy(alpha = 0.22f),
                            topLeft = Offset(0f, rnd(step, j * 7 + 2) * h),
                            size = Size(w, 3f + rnd(step, j * 7 + 3) * 10f)
                        )
                    }
                }
            }
            Weather.NONE -> Unit
        }
    }
}

// --- "You entered a new world" moment: warp flash + title card -------------

@Composable
fun WorldEntryOverlay(key: String, title: String, subtitle: String, accent: Color, proHint: String? = null) {
    var lastKey by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }

    LaunchedEffect(key) {
        val first = lastKey == null
        lastKey = key
        if (first) return@LaunchedEffect // no card at the very start of a run
        flash.snapTo(0.55f)
        visible = true
        launch { flash.animateTo(0f, tween(700)) }
        delay(2600)
        visible = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (flash.value > 0.01f) {
            Box(modifier = Modifier.fillMaxSize().background(accent.copy(alpha = flash.value)))
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.9f),
            exit = fadeOut(tween(500)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 150.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                    .border(1.dp, accent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("NEW WORLD", fontSize = 11.sp, color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(title, fontSize = 20.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text(subtitle, fontSize = 12.sp, color = accent, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                // Soft upsell for free players. Deliberately NOT clickable: the
                // player is mid-flight and an accidental tap must never open the paywall.
                if (proHint != null) {
                    Text(
                        proHint,
                        fontSize = 10.sp,
                        color = Color(0xFFFFD23F),
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
