package com.neonrush.game.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.pow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import com.neonrush.game.R
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay 
import com.neonrush.game.StreakReward
import android.content.Intent
import android.net.Uri
import com.neonrush.game.ReferralRepository
import kotlinx.coroutines.launch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.AdMobManager
import com.neonrush.game.RewardedSlot
import com.neonrush.game.DailyMutations
import com.neonrush.game.MutationDay
import com.neonrush.game.NeonRushViewModel
import com.neonrush.game.RevenueCatManager
import com.neonrush.game.SimulationState
import com.neonrush.game.ZoneGenerator
import com.neonrush.game.TESTING_DISABLE_PRO_GATE
import com.neonrush.game.StoryBannerHost
import com.neonrush.game.Skins
import com.neonrush.game.Upgrades
import com.neonrush.game.DailyCrate
import com.neonrush.game.RunGoals
import com.neonrush.game.db.GameProfile
import com.neonrush.game.ui.theme.*
import com.neonrush.game.MissionTier
import com.neonrush.game.MissionManager
import com.neonrush.game.MissionTemplate
import com.neonrush.game.Worlds

// --- Parallax world backgrounds -------------------------------------------
// Each world can define either a single static background (speedFactor = 0f,
// old behavior) or a stack of depth layers that scroll at different rates to
// sell a 2.5D sense of depth. Layer order = back to front; front layers get
// a higher speedFactor so they scroll faster than the ones behind them.
private data class BgLayer(val res: Int, val speedFactor: Float, val seamless: Boolean = true)

// Tune this to taste: higher = more scroll movement per meter traveled.
private const val PARALLAX_PX_PER_METER = 3f

private val worldBackgroundLayers: Map<Int, List<BgLayer>> = mapOf(
    1 to listOf(
        BgLayer(R.drawable.bg_world1_l1_sky, 0.02f),
        BgLayer(R.drawable.bg_world1_l2_sky_elements, 0.06f),
        BgLayer(R.drawable.bg_world1_l3_far_world, 0.18f),
        BgLayer(R.drawable.bg_world1_l4_world_ground, 0.45f),
        BgLayer(R.drawable.bg_world1_l5_foreground, 0.85f)
    ),
    // World 2 (Derelict Signal): real 3-layer parallax depth restored. This
    // art is a centered "hero scene" (not an edge-to-edge tiling strip), so
    // seamless=false tells the renderer to pan each layer back and forth
    // within its own bounds instead of repeating it — that repeat was what
    // exposed the empty transparent margins as a seam before.
    2 to listOf(
        BgLayer(R.drawable.bg_world2_l1_sky, 0.02f, seamless = false),
        BgLayer(R.drawable.bg_world2_l2_derelict_hull, 0.35f, seamless = false),
        BgLayer(R.drawable.bg_world2_l3_platform_debris, 0.85f, seamless = false)
    ),
    // World 3 (Cell Block Zero): same real-depth fix.
    3 to listOf(
        BgLayer(R.drawable.bg_world3_l1_sky, 0.02f, seamless = false),
        BgLayer(R.drawable.bg_world3_l2_cell_block, 0.35f, seamless = false),
        BgLayer(R.drawable.bg_world3_l3_road_debris, 0.85f, seamless = false)
    ),
    // World 4 (Green Hell): 6-layer parallax — one extra layer beyond the
    // usual 5, since the matched art set had 6 genuinely distinct depths.
    // Non-seamless "hero scene" art like Worlds 2/3/5, so it pans-then-
    // crossfades rather than tiling. l6 foreground is the crashed,
    // jungle-reclaimed helicopter — a nice "wild escape" story beat.
    4 to listOf(
        BgLayer(R.drawable.bg_world4_l1_sky, 0.02f, seamless = false),
        BgLayer(R.drawable.bg_world4_l2_far_ridge, 0.15f, seamless = false),
        BgLayer(R.drawable.bg_world4_l3_mid_valley, 0.35f, seamless = false),
        BgLayer(R.drawable.bg_world4_l4_framing_far, 0.55f, seamless = false),
        BgLayer(R.drawable.bg_world4_l5_framing_near, 0.75f, seamless = false),
        BgLayer(R.drawable.bg_world4_l6_foreground, 0.9f, seamless = false)
    ),
    // World 5 (Red Protocol): full 5-layer parallax, same treatment as
    // Worlds 2-3 — non-seamless "hero scene" art (content centered,
    // transparent margins), so it pans-then-crossfades rather than tiling.
    // l1 sky shows the hunter-drones from the boss intro lore directly.
    5 to listOf(
        BgLayer(R.drawable.bg_world5_l1_sky, 0.02f, seamless = false),
        BgLayer(R.drawable.bg_world5_l2_skyline, 0.15f, seamless = false),
        BgLayer(R.drawable.bg_world5_l3_street_walls, 0.35f, seamless = false),
        BgLayer(R.drawable.bg_world5_l4_road, 0.55f, seamless = false),
        BgLayer(R.drawable.bg_world5_l5_foreground, 0.85f, seamless = false)
    ),
    // World 6 (Signal Fracture): full 5-layer parallax, cyberspace/data-heist
    // theme matching its lore ("disappear into the wire"). Same non-seamless
    // "hero scene" treatment as Worlds 2-5. l1 sky includes the rogue
    // trace-daemon from the boss intro, flying through the data-void itself.
    6 to listOf(
        BgLayer(R.drawable.bg_world6_l1_sky, 0.02f, seamless = false),
        BgLayer(R.drawable.bg_world6_l2_far_firewalls, 0.15f, seamless = false),
        BgLayer(R.drawable.bg_world6_l3_mid_corridor, 0.35f, seamless = false),
        BgLayer(R.drawable.bg_world6_l4_fragments, 0.55f, seamless = false),
        BgLayer(R.drawable.bg_world6_l5_foreground, 0.85f, seamless = false)
    ),
    // World 7 (Frozen Veil): 5-layer parallax, arctic infiltration theme.
    // Same non-seamless "hero scene" treatment as Worlds 2-6. l1 aurora sky,
    // l2 distant ice-citadel, l3 frozen cavern + facility, l4 ruined ice
    // ground with radar dish, l5 hanging icicles framing the foreground.
    7 to listOf(
        BgLayer(R.drawable.bg_world7_l1_sky, 0.02f, seamless = false),
        BgLayer(R.drawable.bg_world7_l2_far_citadel, 0.15f, seamless = false),
        BgLayer(R.drawable.bg_world7_l3_ice_cavern, 0.35f, seamless = false),
        BgLayer(R.drawable.bg_world7_l4_ruins_ground, 0.55f, seamless = false),
        BgLayer(R.drawable.bg_world7_l5_icicles_foreground, 0.85f, seamless = false)
    )
    // Special-mode world 8 (Apex Signal) has no entry yet —
    // ParallaxWorldBackground falls back to a themed color gradient for any
    // world id missing here. Add an 8 entry (same pattern) once its art exists.
)

// Parses a "#RRGGBB" (or "#AARRGGBB") hex string, e.g. ZoneDNA.environmentColor,
// into a Compose Color. Falls back to a neutral cyber-purple if parsing fails.
private fun hexToColor(hex: String): Color {
    return try {
        val clean = hex.removePrefix("#")
        val argb = if (clean.length == 6) "FF$clean" else clean
        Color(argb.toLong(16))
    } catch (e: Exception) {
        Color(0xFF9D00FF)
    }
}

// How strongly to dim the whole parallax stack so gameplay elements (pickups,
// obstacles, the pilot) stay readable against busier background art. 0f =
// no dimming, 1f = fully black. Tune to taste.
private const val BACKGROUND_SCRIM_ALPHA = 0.32f

// Shield barrier look evolves with the Shield Core upgrade level (0-5).
private fun DrawScope.drawShieldAura(level: Int, x: Float, y: Float, r: Float, tick: Int) {
    val c = Offset(x, y)
    val base = Color(0xFF3A86FF)
    drawCircle(base, radius = r, center = c, style = Stroke(width = 2.dp.toPx() + level * 0.5f.dp.toPx()))
    if (level >= 2) drawCircle(base.copy(alpha = 0.12f), radius = r * 0.95f, center = c)
    if (level >= 3) { // rotating arcs
        for (k in 0 until 3) {
            drawArc(
                color = Color(0xFF7FDBFF), startAngle = tick * 4f + k * 120f, sweepAngle = 40f, useCenter = false,
                topLeft = Offset(x - r * 1.15f, y - r * 1.15f), size = Size(r * 2.3f, r * 2.3f),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
    if (level >= 4) { // counter-rotating outer arcs
        for (k in 0 until 4) {
            drawArc(
                color = Color.White.copy(alpha = 0.7f), startAngle = -tick * 3f + k * 90f, sweepAngle = 28f, useCenter = false,
                topLeft = Offset(x - r * 1.35f, y - r * 1.35f), size = Size(r * 2.7f, r * 2.7f),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
    if (level >= 5) { // pulsing glow + orbiting sparks
        val pulse = 0.25f + 0.15f * sin(tick * 0.2f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF7FDBFF).copy(alpha = pulse), Color.Transparent), center = c, radius = r * 1.8f),
            radius = r * 1.8f, center = c
        )
        for (k in 0 until 6) {
            val a = tick * 0.1f + k * (2f * kotlin.math.PI.toFloat() / 6f)
            drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(x + cos(a) * r * 1.5f, y + sin(a) * r * 1.5f))
        }
    }
}

// Ship hulls are cosmetic: each one gives the pilot a different thruster trail
// and aura, drawn in code (no image assets). The pilot suit is separate.
internal fun DrawScope.drawHullEffect(hullId: String, x: Float, y: Float, h: Float, tick: Int, speedLevel: Int = 0) {
    if (drawExtraHull(hullId, x, y, h, tick, speedLevel)) return // newer code-drawn hulls
    val color: Color
    val shape: Int // 0 diamond, 1 square, 2 triangle, 3 pulse, 4 gold, 5 grid
    when (hullId) {
        "purple_square" -> { color = Color(0xFFB266FF); shape = 1 }
        "green_triangle" -> { color = Color(0xFF39FF14); shape = 2 }
        "magenta_pulse" -> { color = Color(0xFFFF2BD6); shape = 3 }
        "gold_transcendence" -> { color = Color(0xFFFFD23F); shape = 4 }
        "matrix_grid" -> { color = Color(0xFF00FF9C); shape = 5 }
        else -> { color = Color(0xFF00E5FF); shape = 0 }
    }
    val unit = h * 0.06f

    // Premium auras
    if (shape == 3) {
        val r = h * 0.5f + sin(tick * 0.25f) * h * 0.04f
        drawCircle(color.copy(alpha = 0.55f), radius = r, center = Offset(x, y), style = Stroke(width = 3f))
        drawCircle(color.copy(alpha = 0.15f), radius = r * 0.85f, center = Offset(x, y))
    }
    if (shape == 4) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(x, y), radius = h * 0.65f
            ),
            radius = h * 0.65f, center = Offset(x, y)
        )
        for (k in 0 until 5) { // orbiting sparkles
            val a = tick * 0.08f + k * (2f * kotlin.math.PI.toFloat() / 5f)
            drawCircle(Color.White.copy(alpha = 0.8f), radius = unit * 0.35f,
                center = Offset(x + cos(a) * h * 0.45f, y + sin(a) * h * 0.45f))
        }
    }

    // Trail streaming behind the pilot (to the left)
    val n = 8 + speedLevel * 2 // Afterburner upgrade: longer trail per level
    for (i in 1..n) {
        val a = 0.6f * (1f - i / (n + 1f))
        val s = unit * (1.3f - i * 0.1f)
        val cx = x - h * 0.18f - i * unit * 1.6f
        val cy = y + h * 0.05f + sin((tick + i * 3) * 0.3f) * unit * 0.6f
        val c = color.copy(alpha = a)
        when (shape) {
            1 -> drawRect(c, Offset(cx - s, cy - s), Size(s * 2, s * 2))
            5 -> drawRect(c, Offset(cx - s, cy - s), Size(s * 2, s * 2), style = Stroke(width = 2f))
            2 -> drawPath(Path().apply {
                moveTo(cx - s, cy); lineTo(cx + s, cy - s); lineTo(cx + s, cy + s); close()
            }, c)
            else -> drawPath(Path().apply {
                moveTo(cx, cy - s); lineTo(cx + s, cy); lineTo(cx, cy + s); lineTo(cx - s, cy); close()
            }, c)
        }
    }
}

// Base layers for a world, optionally remixed: the sky and the foreground are
// swapped for ones borrowed from other main worlds (see WorldVariants).
private fun layersFor(worldId: Int, variant: WorldVariant?): List<BgLayer>? {
    val base = worldBackgroundLayers[worldId] ?: return null
    if (variant == null || base.size < 2) return base
    val sky = worldBackgroundLayers[variant.skyDonor]?.firstOrNull()
    val front = worldBackgroundLayers[variant.frontDonor]?.lastOrNull()
    return base.mapIndexed { i, l ->
        when {
            i == 0 && sky != null -> sky
            i == base.lastIndex && front != null -> front
            else -> l
        }
    }
}

@Composable
private fun ParallaxWorldBackground(
    worldId: Int,
    distanceMeters: Float,
    fallbackColorHex: String,
    variant: WorldVariant? = null
) {
    val layers = layersFor(worldId, variant)
    val cf = remember(variant?.key) {
        variant?.let { ColorFilter.colorMatrix(it.colorMatrix()) }
    }
    if (layers == null) {
        // No dedicated art for this world yet (currently: special-mode worlds
        // 6-8, and any future world before its layers are added to the map
        // above). Show a themed gradient instead of leaving the screen blank.
        val themeColor = hexToColor(fallbackColorHex)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(themeColor.copy(alpha = 0.35f), Color(0xFF030206))
                    )
                )
                .background(Color.Black.copy(alpha = BACKGROUND_SCRIM_ALPHA))
        )
        return
    }
    // Loaded per-recomposition to match this file's existing imageResource
    // pattern (see pilot/obstacle bitmaps below). Wrap in remember(worldId)
    // if you want to avoid reloading these every frame.
    val bitmaps = layers.map { ImageBitmap.imageResource(id = it.res) }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cw = size.width
        val ch = size.height

        // Base fill so transparent gaps between hero-scene layers show the
        // world's theme color instead of plain black.
        drawRect(
            brush = Brush.verticalGradient(
                listOf(hexToColor(fallbackColorHex).copy(alpha = 0.35f), Color(0xFF030206))
            )
        )

        layers.forEachIndexed { idx, layer ->
            val bmp = bitmaps[idx]
            // Scale each layer to fill the canvas height, preserving aspect.
            val displayWidth = ch * (bmp.width.toFloat() / bmp.height.toFloat())

            if (layer.speedFactor == 0f) {
                // Static layer: single centered/cropped draw, no scrolling.
                drawImage(
                    image = bmp,
                            colorFilter = cf,
                    dstOffset = IntOffset(((cw - displayWidth) / 2f).roundToInt(), 0),
                    dstSize = IntSize(displayWidth.roundToInt(), ch.roundToInt())
                )
            } else if (layer.seamless) {
                // Seamless tiling strip art (e.g. World 1): content spans
                // edge-to-edge, so repeating copies side by side loops cleanly.
                val scrollPx = distanceMeters * layer.speedFactor * PARALLAX_PX_PER_METER
                var x = (-scrollPx).mod(displayWidth) - displayWidth
                while (x < cw) {
                    drawImage(
                        image = bmp,
                            colorFilter = cf,
                        dstOffset = IntOffset(x.roundToInt(), 0),
                        dstSize = IntSize(displayWidth.roundToInt(), ch.roundToInt())
                    )
                    x += displayWidth
                }
            } else {
                // Non-seamless "hero scene" art (centered content, transparent
                // margins): repeating it side by side would expose the empty
                // margins as a seam, so instead pan the single oversized copy
                // back and forth within its own bounds. Each layer's distinct
                // speedFactor still produces real multi-plane depth — it just
                // never wraps/repeats.
                val scrollPx = distanceMeters * layer.speedFactor * PARALLAX_PX_PER_METER
                val panRange = (displayWidth - cw).coerceAtLeast(0f)
                if (panRange <= 0f) {
                    // Image isn't wide enough to pan (narrower than the
                    // screen even at full-height scale) — just center it.
                    drawImage(
                        image = bmp,
                            colorFilter = cf,
                        dstOffset = IntOffset(((cw - displayWidth) / 2f).roundToInt(), 0),
                        dstSize = IntSize(displayWidth.roundToInt(), ch.roundToInt())
                    )
                } else {
                    // One-directional pan, matching travel direction. Since
                    // this art can't tile edge-to-edge, a hard loop or a
                    // permanent freeze once panRange is exhausted are both
                    // bad — freezing is what was happening here before (the
                    // fast foreground layer ran out of its ~1600px of pan
                    // room within the first ~600m of a run that can span
                    // tens of thousands of meters, so it sat frozen almost
                    // the entire time). Instead, loop continuously and
                    // cross-dissolve through a short window at the wrap
                    // point so motion never stops, without a visible seam.
                    val wrapWindow = (panRange * 0.15f).coerceIn(40f, 220f)
                    val cycle = panRange + wrapWindow
                    val cyclePos = scrollPx % cycle
                    if (cyclePos <= panRange) {
                        drawImage(
                            image = bmp,
                            colorFilter = cf,
                            dstOffset = IntOffset((-cyclePos).roundToInt(), 0),
                            dstSize = IntSize(displayWidth.roundToInt(), ch.roundToInt())
                        )
                    } else {
                        val progress = (cyclePos - panRange) / wrapWindow
                        drawImage(
                            image = bmp,
                            colorFilter = cf,
                            dstOffset = IntOffset((-panRange).roundToInt(), 0),
                            dstSize = IntSize(displayWidth.roundToInt(), ch.roundToInt()),
                            alpha = 1f - progress
                        )
                        drawImage(
                            image = bmp,
                            colorFilter = cf,
                            dstOffset = IntOffset(0, 0),
                            dstSize = IntSize(displayWidth.roundToInt(), ch.roundToInt()),
                            alpha = progress
                        )
                    }
                }
            }
        }

        // Dim the whole stack uniformly so gameplay elements read clearly on top.
        drawRect(color = Color.Black.copy(alpha = BACKGROUND_SCRIM_ALPHA))
    }
}
// ---------------------------------------------------------------------------

@Composable
fun NeonRushApp(viewModel: NeonRushViewModel) {
    val activeProfile by viewModel.profile.collectAsState(initial = GameProfile())
    val currentProfile = activeProfile ?: GameProfile()

    var activeTab by remember { mutableStateOf("arcade") }
    var showGhostSelection by remember { mutableStateOf(false) }
    val simState by viewModel.simState.collectAsState()
    
    val isPro by RevenueCatManager.isPro.collectAsState()
    LaunchedEffect(isPro, currentProfile.adsRemoved) {
        AdMobManager.setAdState(isPro, currentProfile.adsRemoved)
    }
    LaunchedEffect(simState.isStarted, simState.isCompleted) {
        AdMobManager.refresh()
    }
    var showPaywall by remember { mutableStateOf(false) }
    var paywallReason by remember { mutableStateOf("generic") }
    
    var showStreakFreezeOffer by remember { mutableStateOf(false) }
    // Polite "check for updates" nudge, throttled to roughly once every 2
    // weeks. Keyed off activeProfile (not currentProfile's fallback
    // default) so it only evaluates once real profile data has loaded.
    var showUpdateReminder by remember { mutableStateOf(false) }
    LaunchedEffect(activeProfile) {
        activeProfile?.let { prof ->
            val twoWeeksMillis = 14L * 24 * 60 * 60 * 1000
            if (System.currentTimeMillis() - prof.lastUpdateReminderShownAt >= twoWeeksMillis) {
                showUpdateReminder = true
                viewModel.recordUpdateReminderShown()
            }
        }
    }
LaunchedEffect(Unit) {
    if (viewModel.isStreakFreezeEligible()) {
        showStreakFreezeOffer = true
    } else {
        viewModel.checkDailyStreak()
    }
}

if (showStreakFreezeOffer) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("🔥 Streak at risk!", fontFamily = FontFamily.Monospace) },
        text = { Text("You missed a day. Spend 15 gems to freeze your streak and keep it going?") },
        confirmButton = {
            Button(onClick = {
                viewModel.freezeStreak()
                showStreakFreezeOffer = false
            }) {
                Text("💎 FREEZE (15 GEMS)")
            }
        },
        dismissButton = {
            Button(onClick = {
                viewModel.checkDailyStreak()
                showStreakFreezeOffer = false
            }) {
                Text("No thanks")
            }
        }
    )
}

    var streakBannerReward by remember { mutableStateOf<StreakReward?>(null) }
    LaunchedEffect(Unit) {
        viewModel.streakRewardEvent.collect { reward ->
            streakBannerReward = reward
            delay(if (reward.bonusText != null) 6500 else 4000)
            streakBannerReward = null
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.purchaseErrorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!simState.isStarted || simState.isCompleted) {
                Column {
                    // Show home screen banner if user is NOT PRO
                    if (!isPro && !currentProfile.adsRemoved) {
                        AdMobBannerView(
                            adUnitId = AdMobManager.BANNER_AD_UNIT_ID,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    NavigationBar(
                        containerColor = CyberSurface,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .border(1.dp, CyberPrimary.copy(alpha = 0.15f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    ) {
                        NavigationBarItem(
                            selected = activeTab == "arcade",
                            onClick = { 
                                activeTab = "arcade" 
                                showGhostSelection = false
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberPrimary,
                                unselectedIconColor = CyberOnSurface.copy(alpha = 0.5f),
                                indicatorColor = CyberPrimary.copy(alpha = 0.1f)
                            ),
                            icon = {
                                if (DailyCrate.isReady(currentProfile, isPro)) {
                                    BadgedBox(badge = { Badge(containerColor = Color(0xFFFFD23F)) }) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Arcade")
                                    }
                                } else {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Arcade")
                                }
                            },
                            label = { Text("Arcade", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = activeTab == "rankings",
                            onClick = { activeTab = "rankings" },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberPrimary,
                                unselectedIconColor = CyberOnSurface.copy(alpha = 0.5f),
                                indicatorColor = CyberPrimary.copy(alpha = 0.1f)
                            ),
                            icon = { Icon(Icons.Filled.List, contentDescription = "Rankings") },
                            label = { Text("Rankings", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = activeTab == "social",
                            onClick = { activeTab = "social" },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberPrimary,
                                unselectedIconColor = CyberOnSurface.copy(alpha = 0.5f),
                                indicatorColor = CyberPrimary.copy(alpha = 0.1f)
                            ),
                            icon = { Icon(Icons.Filled.Star, contentDescription = "Social") },
                            label = { Text("Social", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                        )
                        if (currentProfile.specialWorldTier >= 1) {
                            NavigationBarItem(
                                selected = activeTab == "special",
                                onClick = { activeTab = "special" },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF9C27B0),
                                    unselectedIconColor = CyberOnSurface.copy(alpha = 0.5f),
                                    indicatorColor = Color(0xFF9C27B0).copy(alpha = 0.1f)
                                ),
                                icon = { Text("⚡", fontSize = 18.sp) },
                                label = { Text("Special", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                            )
                        }
                        NavigationBarItem(
                            selected = activeTab == "skins",
                            onClick = { activeTab = "skins" },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberPrimary,
                                unselectedIconColor = CyberOnSurface.copy(alpha = 0.5f),
                                indicatorColor = CyberPrimary.copy(alpha = 0.1f)
                            ),
                            icon = {
                                BadgedBox(badge = { Badge(containerColor = CyberSecondary) }) {
                                    Icon(Icons.Filled.ShoppingCart, contentDescription = "Skins")
                                }
                            },
                            label = { Text("Skins", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = activeTab == "profile",
                            onClick = { activeTab = "profile" },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberPrimary,
                                unselectedIconColor = CyberOnSurface.copy(alpha = 0.5f),
                                indicatorColor = CyberPrimary.copy(alpha = 0.1f)
                            ),
                            icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                            label = { Text("Profile", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(CyberBackground, Color(0xFF030206))
                    )
                )
                .padding(innerPadding)
        ) {
            if (simState.isStarted && !simState.isCompleted) {
                RacingSimulatorScreen(
    simState = simState,
    viewModel = viewModel,
    isPro = isPro,
    profile = currentProfile,
    onShowPaywall = { showPaywall = true; paywallReason = "world4" }
)
            } else if (simState.isStarted && simState.isCompleted) {
                GameOverOverlayScreen(
                    simState = simState,
                    viewModel = viewModel,
                    isPro = isPro,
                    profile = currentProfile,
                    onShowPaywall = { showPaywall = true }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "MainTabsAnim"
                    ) { tab ->
                        when (tab) {
                            "arcade" -> {
                                if (showGhostSelection) {
                                    GhostRacerTab(
                                        viewModel = viewModel,
                                        onBack = { showGhostSelection = false },
                                        onRequireTutorial = { action -> action() }
                                    )
                                } else {
                                    ArcadeHomeView(
                                        profile = currentProfile,
                                        viewModel = viewModel,
                                        onStartRush = {
                                            val defaultGhost = com.neonrush.game.db.GhostChallengeEntity(
                                                "ghost_cyberrunner", 
                                                "CyberRunner", 
                                                850, 
                                                4, 
                                                ZoneGenerator.generateTelemetryCsv(850, 111)
                                            )
                                            viewModel.startRacingSimulation(defaultGhost)
                                        },
                                        onShowGhostSelection = { showGhostSelection = true },
                                        onNavigateToGlobal = { activeTab = "rankings" },
                                        onNavigateToSkins = { activeTab = "skins" },
                                        onNavigateToSocial = { activeTab = "social" },
                                        onStartSpecialMode = {
                                            val specialGhost = com.neonrush.game.db.GhostChallengeEntity(
                                                "ghost_cyberrunner",
                                                "CyberRunner",
                                                850,
                                                4,
                                                ZoneGenerator.generateTelemetryCsv(850, 111)
                                            )
                                            viewModel.startSpecialModeRun(specialGhost)
                                        },
                                        isPro = isPro
                                    
                                
                                    )
                                }
                            }
                            "rankings" -> LeaderboardsTab(viewModel = viewModel, playerProfile = currentProfile)
                            "social" -> DailyChallengeTab(viewModel = viewModel, profile = currentProfile)
                            "skins" -> SkinsDeckTab(viewModel = viewModel, profile = currentProfile)
                            "special" -> SpecialModeTab(
                                profile = currentProfile,
                                viewModel = viewModel,
                                onStartSpecialMode = {
                                    val specialGhost = com.neonrush.game.db.GhostChallengeEntity(
                                        "ghost_cyberrunner",
                                        "CyberRunner",
                                        850,
                                        4,
                                        ZoneGenerator.generateTelemetryCsv(850, 111)
                                    )
                                    viewModel.startSpecialModeRun(specialGhost)
                                }
                            )
                            "profile" -> ProfileTab(profile = currentProfile, viewModel = viewModel)
                        }
                    }
                }
            }

            streakBannerReward?.let { reward ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp, start = 20.dp, end = 20.dp)
                        .background(Color(0xE6120324), RoundedCornerShape(12.dp))
                        .border(1.dp, CyberPrimary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "🔥 ${reward.label}",
                            color = CyberPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "+${reward.gems} GEMS",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp
                        )
                        reward.bonusText?.let { bonus ->
                            Text(
                                text = bonus,
                                color = Color(0xFFFFD23F),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }   
        }
    }

    if (showPaywall) {
        PaywallDialog(onDismiss = { showPaywall = false }, reason = paywallReason)
    }


    if (showUpdateReminder) {
        val context = LocalContext.current
        Dialog(
            onDismissRequest = { showUpdateReminder = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurface, RoundedCornerShape(14.dp))
                        .border(1.dp, CyberPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "📲", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "A newer version may be available.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Kindly check for updates for the best experience. Thanks!",
                            color = CyberOnSurface.copy(alpha = 0.8f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                        )
                        Row {
                            Button(
                                onClick = {
                                    showUpdateReminder = false
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL)))
                                    } catch (e: Exception) { }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("CHECK FOR UPDATES", color = CyberBackground, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { showUpdateReminder = false }, modifier = Modifier.height(34.dp)) {
                                Text("Maybe later", color = CyberOnSurface.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderProfileDeck(profile: GameProfile, viewModel: NeonRushViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(CyberPrimary, CyberSecondary)),
                RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🚀",
                        fontSize = 24.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text = profile.username,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CyberPrimary
                        )
                        Text(
                            text = "TRANSCENDENCE LEVEL: ${profile.transcendenceCount}",
                            fontSize = 11.sp,
                            color = CyberSecondary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "💎",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = "${profile.gems} GEMS",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "BEST: ${profile.bestScore} PTS",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyberPrimary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun LeaderboardsTab(viewModel: NeonRushViewModel, playerProfile: GameProfile) {
    val rankingList by viewModel.leaderboard.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "⚡ NEURAL LEADERBOARD RUSH",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = CyberPrimary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Follow and challenge ghost trails of active pilots sync'd from the host.",
            color = CyberOnSurface.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(rankingList) { pilot ->
                val isSelf = pilot.name == playerProfile.username
                val borderBrush = if (isSelf) {
                    Brush.horizontalGradient(listOf(CyberPrimary, CyberSecondary))
                } else {
                    Brush.horizontalGradient(listOf(CyberSurface, CyberSurface))
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelf) CyberSurface.copy(alpha = 0.8f) else CyberSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderBrush, RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val rankColor = when (pilot.rank) {
                                1 -> GoldAccent
                                2 -> SilverAccent
                                3 -> BronzeAccent
                                else -> CyberOnSurface.copy(alpha = 0.5f)
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(rankColor.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "#${pilot.rank}",
                                    color = rankColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = pilot.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isSelf) CyberPrimary else Color.White
                                    )
                                    if (pilot.isFollowed) {
                                        Text(
                                            text = " ✓ Followed",
                                            fontSize = 9.sp,
                                            color = CyberSecondary,
                                            modifier = Modifier.padding(start = 6.dp),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                Text(
                                    text = "Active Zone: ${pilot.activeZone}",
                                    fontSize = 11.sp,
                                    color = CyberOnSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "${pilot.bestScore}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberPrimary
                                )
                                Text(
                                    text = "PTS",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberOnSurface.copy(alpha = 0.4f)
                                )
                            }

                            if (!isSelf) {
                                IconButton(
                                    onClick = { viewModel.toggleFollowUser(pilot.name) },
                                    modifier = Modifier.testTag("follow_pilot_button")
                                ) {
                                    Icon(
                                        imageVector = if (pilot.isFollowed) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = "Follow Pilot",
                                        tint = if (pilot.isFollowed) CyberSecondary else CyberOnSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GhostRacerTab(viewModel: NeonRushViewModel, onBack: () -> Unit, onRequireTutorial: (() -> Unit) -> Unit = { it() }) {
    var selectedPlayerId by remember { mutableStateOf("ghost_cyberrunner") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back to home dashboard",
                    tint = CyberPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "👾 INVICIBLE RACE",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = CyberPrimary,
                fontFamily = FontFamily.Monospace
            )
        }

        Text(
            text = "Race against the exact flight paths recorded by top pilots. Slide to match their offsets in the light stream.",
            color = CyberOnSurface.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Select Competitor Ghost:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val competitors = listOf(
            Pair("ghost_retro", "RetroWave (240 pts)"),
            Pair("ghost_zeroglitch", "ZeroGlitch (480 pts)"),
            Pair("ghost_cyberrunner", "CyberRunner (850 pts)")
        )

        competitors.forEach { comp ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedPlayerId == comp.first) CyberPrimary.copy(alpha = 0.15f) else CyberSurface)
                    .border(
                        1.dp,
                        if (selectedPlayerId == comp.first) CyberPrimary else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { selectedPlayerId = comp.first }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedPlayerId == comp.first,
                    onClick = { selectedPlayerId = comp.first },
                    colors = RadioButtonDefaults.colors(selectedColor = CyberPrimary)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = comp.second,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Click to select neural race path telemetry data stream.",
                        fontSize = 10.sp,
                        color = CyberOnSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val selectedGhost = when (selectedPlayerId) {
                    "ghost_retro" -> com.neonrush.game.db.GhostChallengeEntity("ghost_retro", "RetroWave", 240, 2, ZoneGenerator.generateTelemetryCsv(240, 42))
                    "ghost_zeroglitch" -> com.neonrush.game.db.GhostChallengeEntity("ghost_zeroglitch", "ZeroGlitch", 480, 3, ZoneGenerator.generateTelemetryCsv(480, 84))
                    else -> com.neonrush.game.db.GhostChallengeEntity("ghost_cyberrunner", "CyberRunner", 850, 4, ZoneGenerator.generateTelemetryCsv(850, 111))
                }
                onRequireTutorial { viewModel.startRacingSimulation(selectedGhost) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sync_ghosts_button")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Launch", tint = CyberBackground)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "▶ START RUSH",
                    color = CyberBackground,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        
Spacer(modifier = Modifier.height(8.dp))
    }
}
@Composable
fun SpecialModeTab(profile: GameProfile, viewModel: NeonRushViewModel, onStartSpecialMode: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "⚡ SPECIAL MODE",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF9C27B0),
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Qualify through missions to unlock a completely different world.",
            fontSize = 12.sp,
            color = CyberOnSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(20.dp))

        val tiers = listOf(
            Triple(1, "Signal Fracture", "Complete all 3 Daily Missions"),
            Triple(2, "Frozen Veil", "Complete all 3 Weekly Missions"),
            Triple(3, "Apex Signal", "Complete all 3 Monthly Missions")
        )

        var expandedTier by remember { mutableStateOf<Int?>(null) }

        tiers.forEach { (tierNum, name, requirement) ->
            val unlocked = profile.specialWorldTier >= tierNum

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (unlocked) Color(0xFF9C27B0).copy(alpha = 0.15f) else CyberSurface)
                    .border(
                        1.dp,
                        if (unlocked) Color(0xFF9C27B0) else CyberOnSurface.copy(alpha = 0.2f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = !unlocked) { expandedTier = tierNum }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
            
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (unlocked) "✅ $name" else "🔒 $name",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = requirement,
                        fontSize = 11.sp,
                        color = CyberOnSurface.copy(alpha = 0.6f)
                    )
                }
                if (unlocked && tierNum == profile.specialWorldTier) {
                    Button(
                        onClick = onStartSpecialMode,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("ENTER", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (expandedTier != null) {
            val tierNum = expandedTier!!
            val (missions, claimedCsv) = when (tierNum) {
                1 -> MissionManager.currentDailyMissions(profile.dailyRerollCount) to profile.dailyMissionsClaimedCsv
                2 -> MissionManager.currentWeeklyMissions(profile.weeklyRerollCount) to profile.weeklyMissionsClaimedCsv
                else -> MissionManager.currentMonthlyMissions(profile.monthlyRerollCount) to profile.monthlyMissionsClaimedCsv
            }
            val claimed = MissionManager.parseClaimedCsv(claimedCsv)
            AlertDialog(
                onDismissRequest = { expandedTier = null },
                title = { Text("What's still needed", fontFamily = FontFamily.Monospace) },
                text = {
                    Column {
                        missions.forEach { mission ->
                            val done = claimed.contains(mission.id)
                            Text(
                                text = if (done) "✅ ${mission.description}" else "◻️ ${mission.description}",
                                color = if (done) Color(0xFF4CAF50) else Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { expandedTier = null }) {
                        Text("Got it")
                    }
                }
            )
        }
    }
}

@Composable
fun ArcadeHomeView(
    profile: GameProfile,
    viewModel: NeonRushViewModel,
    onStartRush: () -> Unit,
    onShowGhostSelection: () -> Unit,
    onNavigateToGlobal: () -> Unit,
    onNavigateToSkins: () -> Unit,
    onNavigateToSocial: () -> Unit,
    onStartSpecialMode: () -> Unit,
    isPro: Boolean
) {
    var showCrate by remember { mutableStateOf(false) }
    var showCheckpoints by remember { mutableStateOf(false) }
    if (showCrate) {
        DailyCrateDialog(profile = profile, isPro = isPro, viewModel = viewModel, onDismiss = { showCrate = false })
    }
    if (showCheckpoints) {
        CheckpointsDialog(profile = profile, viewModel = viewModel, onDismiss = { showCheckpoints = false })
    }
    // Safety net: if the content is ever taller than the screen it scrolls instead of
    // squeezing/hiding the buttons. On normal screens the layout is unchanged
    // (min height = screen height, so SpaceBetween still spreads items out).
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val minContentHeight = maxHeight
    Column(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = minContentHeight)
        .verticalScroll(rememberScrollState())
        .padding(bottom = 12.dp),
    verticalArrangement = Arrangement.SpaceBetween
) {
    PromoPopup(onNavigate = { tab ->
        if (tab == "skins") onNavigateToSkins() else onNavigateToSocial()
    })

    Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BEST SCORE: ${profile.bestScore}",
                color = CyberSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            val crateReady = DailyCrate.isReady(profile, isPro)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (crateReady) Color(0xFFFFD23F) else CyberSurface)
                    .border(1.dp, Color(0xFFFFD23F), RoundedCornerShape(12.dp))
                    .clickable { showCrate = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (crateReady) "🎁 READY" else "🎁",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (crateReady) Color.Black else Color(0xFFFFD23F)
                )
            }
            Text(
                text = "💎 ${profile.gems} GEMS",
                color = CyberPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = "🎯 " + RunGoals.forRun(profile.totalRuns).joinToString("  ") { "${it.tier.emoji}${it.short}" } +
                "  •  Mastery Lv ${RunGoals.level(profile.masteryPoints)}",
            fontSize = 9.sp,
            color = CyberOnSurface.copy(alpha = 0.75f),
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
          val activeMutation = DailyMutations.getActiveMutation()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .border(1.dp, CyberPrimary, RoundedCornerShape(8.dp))
                .background(CyberSurface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activeMutation.emoji,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "ACTIVE DAILY MODIFIER: ${activeMutation.title}",
                        color = CyberPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = activeMutation.description,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEON ",
                    color = Color(0xFFEC4899),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    style = LocalTextStyle.current.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0xFFEC4899).copy(alpha = 0.9f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                            blurRadius = 14f
                        )
                    )
                )
                Text(
                    text = "RUSH",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    style = LocalTextStyle.current.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.White.copy(alpha = 0.9f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                            blurRadius = 14f
                        )
                    )
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "How far can you go?",
                color = Color(0xFF06B6D4),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color(0xFF06B6D4).copy(alpha = 0.9f),
                        offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                        blurRadius = 8f
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
        ) {
            NeonPilotScreensaver()
        }

        if (profile.transcendenceCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(8.dp))
                    .background(Color(0xFFD4AF37).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👑 TRANSCENDENCE RANK ACTIVE: LVL ${profile.transcendenceCount} 👑",
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, CyberSecondary, RoundedCornerShape(8.dp))
                    .background(CyberSecondary.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                    .clickable { onShowGhostSelection() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👻", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = "INVICIBLE RACE",
                                color = CyberSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Race against pilot ghost telemetry trails",
                                color = CyberOnSurface.copy(alpha = 0.65f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Forward arrow",
                        tint = CyberSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(
                NeonRushViewModel.DifficultyTier.EASY,
                NeonRushViewModel.DifficultyTier.MEDIUM,
                NeonRushViewModel.DifficultyTier.HARD,
                NeonRushViewModel.DifficultyTier.LEGENDARY
            ).forEach { tier ->
                val isLocked = tier == NeonRushViewModel.DifficultyTier.LEGENDARY && !isPro
                val isSelected = viewModel.selectedDifficulty == tier
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CyberPrimary else CyberSurface)
                        .clickable {
                            if (!isLocked) {
                                viewModel.setDifficulty(tier)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isLocked) "\uD83D\uDD12 ${tier.label}" else tier.label,
                        color = if (isSelected) Color.Black else Color.White.copy(alpha = if (isLocked) 0.4f else 1f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Button(
            onClick = onStartRush,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Color(0xFFFF0055),
                    spotColor = Color(0xFFFF0055)
                )
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFFEC4899), Color(0xFFFF0055))),
                    RoundedCornerShape(12.dp)
                )
                .testTag("start_rush_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Start Rush",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "START RUSH",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

           Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onNavigateToGlobal,
                colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, CyberPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .testTag("quick_global_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.List, contentDescription = "Global", tint = CyberPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GLOBAL", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onNavigateToSkins,
                colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, CyberSecondary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .testTag("quick_skins_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = "Skins", tint = CyberSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SKINS", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // One compact entry point for every saved checkpoint (opens its own page).
            if (profile.checkpointsReachedCsv.isNotEmpty()) {
                val cpCount = profile.checkpointsReachedCsv.split(",").count { it.isNotEmpty() }
                Button(
                    onClick = { showCheckpoints = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, Color(0xFFFFD23F).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .testTag("quick_checkpoints_button")
                ) {
                    Text(
                        "🏁 SAVES ($cpCount)",
                        color = Color(0xFFFFD23F),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val specialWorld = Worlds.specialWorldForTier(profile.specialWorldTier)
        var showSpecialLockInfo by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onNavigateToSkins,
                colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, CyberPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            ) {
                Text(
                    text = "⛽ FUEL TANK",
                    color = CyberPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = {
                    if (specialWorld != null) onStartSpecialMode() else showSpecialLockInfo = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (specialWorld != null) Color(0xFF9C27B0) else CyberSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, Color(0xFF9C27B0).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            ) {
                Text(
                    text = if (specialWorld != null) "⚡ WORLD" else "🔒 MISSIONS",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showSpecialLockInfo) {
            val nextTier = profile.specialWorldTier + 1
            val (missions, claimedCsv) = when (nextTier) {
                1 -> MissionManager.currentDailyMissions(profile.dailyRerollCount) to profile.dailyMissionsClaimedCsv
                2 -> MissionManager.currentWeeklyMissions(profile.weeklyRerollCount) to profile.weeklyMissionsClaimedCsv
                else -> MissionManager.currentMonthlyMissions(profile.monthlyRerollCount) to profile.monthlyMissionsClaimedCsv
            }
            val claimed = MissionManager.parseClaimedCsv(claimedCsv)
            AlertDialog(
                onDismissRequest = { showSpecialLockInfo = false },
                title = { Text("What's still needed", fontFamily = FontFamily.Monospace) },
                text = {
                    Column {
                        missions.forEach { mission ->
                            val done = claimed.contains(mission.id)
                            Text(
                                text = if (done) "✅ ${mission.description}" else "◻️ ${mission.description}",
                                color = if (done) Color(0xFF4CAF50) else Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showSpecialLockInfo = false }) {
                        Text("Got it")
                    }
                }
            )
        }
    }
    }
}
@Composable
fun CheckpointsDialog(profile: GameProfile, viewModel: NeonRushViewModel, onDismiss: () -> Unit) {
    val gold = Color(0xFFFFD23F)
    val reached = profile.checkpointsReachedCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.sortedDescending()
    val activated = profile.checkpointsActivatedCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberBackground)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏁 CHECKPOINTS", color = gold, fontWeight = FontWeight.Black, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                Text(
                    "✕", color = Color.White, fontSize = 22.sp,
                    modifier = Modifier.clickable { onDismiss() }.padding(8.dp)
                )
            }
            Text(
                "Each checkpoint you reach is saved here. Unlock one once with gems, then restart from it any time.",
                color = CyberOnSurface.copy(alpha = 0.75f), fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reached.forEach { cp ->
                    val isActivated = cp in activated
                    val cost = cp * 3
                    val canAfford = profile.gems >= cost
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSurface, RoundedCornerShape(10.dp))
                            .border(1.dp, if (isActivated) CyberPrimary.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Zone $cp", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(
                                text = when {
                                    isActivated -> "✓ Unlocked — start here any time"
                                    canAfford -> "Unlock once for $cost 💎"
                                    else -> "Need ${cost - profile.gems} more 💎 to unlock"
                                },
                                color = if (isActivated) CyberPrimary else CyberOnSurface.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = { viewModel.startFromCheckpoint(cp); onDismiss() },
                            enabled = isActivated || canAfford,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isActivated) CyberPrimary else gold),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (isActivated) "START" else "💎 $cost",
                                color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                            )
                        }
                    }
                }
                if (reached.isEmpty()) {
                    Text("No checkpoints yet. Reach a checkpoint zone in a run to save it here.", color = CyberOnSurface.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, CyberPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            ) { Text("BACK", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun MissionsSection(viewModel: NeonRushViewModel, profile: GameProfile) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        MissionTierBlock("📅 DAILY MISSIONS", MissionTier.DAILY, MissionManager.currentDailyMissions(profile.dailyRerollCount), profile.dailyMissionProgressCsv, profile.dailyMissionsClaimedCsv, viewModel, profile)
        Spacer(modifier = Modifier.height(12.dp))
        MissionTierBlock("🗓️ WEEKLY MISSIONS", MissionTier.WEEKLY, MissionManager.currentWeeklyMissions(profile.weeklyRerollCount), profile.weeklyMissionProgressCsv, profile.weeklyMissionsClaimedCsv, viewModel, profile)
        Spacer(modifier = Modifier.height(12.dp))
        MissionTierBlock("🏆 MONTHLY MISSIONS", MissionTier.MONTHLY, MissionManager.currentMonthlyMissions(profile.monthlyRerollCount), profile.monthlyMissionProgressCsv, profile.monthlyMissionsClaimedCsv, viewModel, profile)
    }
}
@Composable
fun MissionTierBlock(
    title: String,
    tier: MissionTier,
    missions: List<MissionTemplate>,
    progressCsv: String,
    claimedCsv: String,
    viewModel: NeonRushViewModel,
    profile: GameProfile
) {
    val progress = remember(progressCsv) { MissionManager.parseProgressCsv(progressCsv) }
    val claimed = remember(claimedCsv) { MissionManager.parseClaimedCsv(claimedCsv) }
    val rerollCost = when (tier) {
        MissionTier.DAILY -> 10
        MissionTier.WEEKLY -> 20
        MissionTier.MONTHLY -> 30
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = CyberPrimary,
            fontFamily = FontFamily.Monospace
        )
        Button(
            onClick = { viewModel.rerollMissions(tier) },
            enabled = profile.gems >= rerollCost,
            colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = "🔄 $rerollCost💎", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    missions.forEach { mission ->
        val current = (progress[mission.id] ?: 0).coerceAtMost(mission.target)
        val isClaimed = claimed.contains(mission.id)
        val isComplete = current >= mission.target

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CyberSurface)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = mission.description, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = current.toFloat() / mission.target.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isComplete) Color(0xFF4CAF50) else CyberPrimary,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                Text(
                    text = "$current / ${mission.target}  •  💎${mission.rewardGems}",
                    color = CyberOnSurface.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isClaimed) {
                Text(text = "✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else if (isComplete) {
                Button(
                    onClick = { viewModel.claimMission(tier, mission.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(text = "CLAIM", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }
    }
}
@Composable
fun DailyChallengeTab(viewModel: NeonRushViewModel, profile: GameProfile) {
    val comments by viewModel.socialComments.collectAsState()
    var alertMsg by remember { mutableStateOf("") }

    Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(androidx.compose.foundation.rememberScrollState())
) {
    MissionsSection(viewModel = viewModel, profile = profile)

    Card(
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, CyberSecondary, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(CyberSecondary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ACTIVE TODAY",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "Attempts Used: ${profile.dailyAttemptsToday}/3",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyberPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = viewModel.dailyChallengeTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = viewModel.dailyChallengeDesc,
                    fontSize = 13.sp,
                    color = CyberOnSurface.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
    onClick = {
        viewModel.runDailyRushChallenge { success, attempt ->
            if (!success) {
                alertMsg = "Maximum of 3 Daily Rush attempts reached. Buy an extra attempt below, or wait till tomorrow!"
            }
        }
    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("daily_rush_button")
                ) {
                    Text(
                        text = "LAUNCH DAILY STORM ATTEMPT",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (alertMsg.isNotEmpty()) {
    Text(
        text = alertMsg,
        color = CyberSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 8.dp)
    )
}

if (profile.dailyAttemptsToday >= 3) {
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = { viewModel.buyExtraAttempt() },
        enabled = profile.gems >= 25,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "💎 BUY EXTRA ATTEMPT (25 GEMS)",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CommunityAndShareCard(profile = profile)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "💬 COMPANION PILOT CHAT",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = CyberPrimary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CyberSurface.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                comments.forEach { chat ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "@" + chat.username,
                                fontWeight = FontWeight.Bold,
                                color = CyberPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = chat.timeAgo,
                                color = CyberOnSurface.copy(alpha = 0.4f),
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = chat.comment,
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Divider(
                            color = CyberOnSurface.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
// TODO: swap these for your real invite/subreddit URLs once created
private const val DISCORD_INVITE_URL = "https://discord.gg/GmBWCp2WuN"
private const val REDDIT_URL = "https://reddit.com/r/NeonRushGame"
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.neonrushinfinite.game"

// Generates a QR code on-device from ZXing — no network call, no static
// image asset. Requires the ZXing core library: add
//   implementation("com.google.zxing:core:3.5.3")
// to the app module's build.gradle if it isn't already there.
@Composable
private fun QrCodeImage(content: String, sizeDp: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.roundToPx() }
    val qrBitmap = remember(content, sizePx) {
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp.asImageBitmap()
    }
    Image(
        bitmap = qrBitmap,
        contentDescription = "QR code",
        modifier = modifier.size(sizeDp)
    )
}

@Composable
fun CommunityAndShareCard(profile: GameProfile) {
    val context = LocalContext.current

    // Referral code persisted per-device via SharedPreferences.
    // NOTE: actually crediting Gems to both the referrer and the new player
    // when this code is used requires the Play Install Referrer API (to read
    // the code back out on first launch) plus a backend check to confirm
    // the referred player reached your reward threshold. This wiring only
    // covers generating and sharing the code/link.
    val prefs = remember { context.getSharedPreferences("neonrush_prefs", 0) }
    val referralCode = remember {
        var code = prefs.getString("referral_code", null)
        if (code == null) {
            code = profile.hashCode().toString(36).takeLast(6).uppercase()
            prefs.edit().putString("referral_code", code).apply()
        }
        code
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, CyberPrimary, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🌐 JOIN THE CREW",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CyberPrimary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Get patch notes, join events, and vote on what we build next.",
                fontSize = 12.sp,
                color = CyberOnSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_INVITE_URL)))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("DISCORD", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REDDIT_URL)))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("REDDIT", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = CyberOnSurface.copy(alpha = 0.08f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "🎁 SHARE & EARN GEMS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CyberSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Invite a friend with your code. When they join and start playing, you both get Gems.",
                fontSize = 12.sp,
                color = CyberOnSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your code: $referralCode",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val shareText = "I'm playing Neon Rush — how far can you go? 🚀\n" +
                        "Use my code $referralCode when you install and we both get Gems!\n" +
                        "$PLAY_STORE_URL&referrer=$referralCode"
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Neon Rush"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SHARE MY CODE", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "OR SCAN TO INSTALL",
                    fontSize = 10.sp,
                    color = CyberOnSurface.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    QrCodeImage(
                        content = "$PLAY_STORE_URL&referrer=$referralCode",
                        sizeDp = 140.dp
                    )
                }
            }
        }
    }
}
@Composable
fun RunGoalsHud(simState: SimulationState) {
    val goals = remember(simState.runGoalIds) { RunGoals.parseIds(simState.runGoalIds) }
    val shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 6f)
    goals.forEach { g ->
        val p = RunGoals.progress(g, simState)
        val done = p >= g.target
        Text(
            text = (if (done) "✓ " else "") + "${g.tier.emoji} ${g.text} ${minOf(p, g.target)}/${g.target}",
            color = if (done) Color(0xFF39FF14) else Color.White.copy(alpha = 0.75f),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            style = TextStyle(shadow = shadow)
        )
    }
}

@Composable
fun RunGoalsSummary(simState: SimulationState, profile: GameProfile) {
    val goals = remember(simState.runGoalIds) { RunGoals.parseIds(simState.runGoalIds) }
    if (goals.isEmpty()) return
    val done = goals.count { RunGoals.isComplete(it, simState) }
    val level = RunGoals.level(profile.masteryPoints)
    val stars = RunGoals.stars(profile.masteryPoints)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "🎯 RUN GOALS  $done/${goals.size}",
            color = Color(0xFFFFD23F), fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace
        )
        goals.forEach { g ->
            val p = RunGoals.progress(g, simState)
            val ok = p >= g.target
            Text(
                "${if (ok) "✓" else "✗"} ${g.tier.emoji} ${g.text} (${minOf(p, g.target)}/${g.target})",
                color = if (ok) Color(0xFF39FF14) else Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp, fontFamily = FontFamily.Monospace
            )
        }
        if (simState.masteryEarnedLastRun > 0) {
            Text(
                "+${simState.masteryEarnedLastRun} MASTERY",
                color = CyberPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            text = when {
                stars > 0 -> "MASTERY ★$stars  •  +${RunGoals.MAX_LEVEL}% score (MAX)"
                else -> "MASTERY LV $level  •  +$level% score  •  ${RunGoals.pointsIntoLevel(profile.masteryPoints)}/${RunGoals.POINTS_PER_LEVEL} to next"
            },
            color = CyberSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun DailyCrateDialog(profile: GameProfile, isPro: Boolean, viewModel: NeonRushViewModel, onDismiss: () -> Unit) {
    val result by viewModel.crateResult.collectAsState()
    val activity = LocalContext.current as? Activity
    val gold = Color(0xFFFFD23F)
    val ready = DailyCrate.isReady(profile, isPro)
    val noAd = isPro || profile.adsRemoved
    val openedToday = DailyCrate.cratesOpenedToday(profile)
    val bob by rememberInfiniteTransition(label = "crateBob").animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bob"
    )
    val reveal = remember { Animatable(0.3f) }
    LaunchedEffect(result) {
        if (result != null) {
            reveal.snapTo(0.3f)
            reveal.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 200f))
        }
    }
    fun hullName(id: String) = viewModel.shopSkins.find { it.first == id }?.second ?: id
    fun close() { viewModel.dismissCrateResult(); onDismiss() }

    Dialog(onDismissRequest = { close() }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSurface, RoundedCornerShape(16.dp))
                .border(1.dp, gold, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            val res = result
            if (res == null) {
                Text("🎁", fontSize = 64.sp, modifier = Modifier.offset(y = bob.dp))
                Text("DAILY CRATE", color = gold, fontWeight = FontWeight.Black, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                Text(
                    "Gems, hull shards or a quote. Collect shards to unlock hulls for free.",
                    color = CyberOnSurface.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                val streak = profile.crateStreak
                Text(
                    "🔥 Crate streak: $streak day${if (streak == 1) "" else "s"}  •  every 7th day is a bonus",
                    color = CyberPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                // Shard progress for hulls not yet owned
                val owned = profile.unlockedSkinsCsv.split(",").toSet()
                DailyCrate.HULL_SHARDS.forEach { (id, need) ->
                    if (id !in owned) {
                        val have = DailyCrate.shards(profile.crateShardsCsv, id)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(hullName(id), color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier.width(80.dp).height(6.dp)
                                    .background(CyberOnSurface.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth((have.toFloat() / need).coerceIn(0f, 1f)).height(6.dp)
                                        .background(gold, RoundedCornerShape(3.dp))
                                )
                            }
                            Text("  $have/$need", color = gold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (ready) {
                    Button(
                        onClick = {
                            if (noAd) {
                                viewModel.openDailyCrate(isPro)
                            } else {
                                activity?.let { act ->
                                    AdMobManager.showRewardedIfReady(act, RewardedSlot.DAILY_CRATE) {
                                        viewModel.openDailyCrate(isPro)
                                        viewModel.recordAdWatched()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = gold),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when {
                                !noAd -> adButtonLabel(RewardedSlot.DAILY_CRATE, "🎬 WATCH AD TO OPEN")
                                openedToday == 0 -> "🎁 OPEN CRATE"
                                else -> "🎁 OPEN BONUS CRATE ⚡PRO"
                            },
                            color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text(
                        "Next crate in ${DailyCrate.countdownText()}",
                        color = CyberOnSurface.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 13.sp
                    )
                }
                if (!isPro) {
                    Text(
                        "⚡ Pro opens crates with no ad and gets a 2nd crate every day.",
                        color = gold.copy(alpha = 0.85f), fontSize = 10.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            } else {
                Text(
                    "🎉", fontSize = 64.sp,
                    modifier = Modifier.graphicsLayer(scaleX = reveal.value, scaleY = reveal.value)
                )
                if (res.isStreakBonus) {
                    Text(res.message ?: "", color = gold, fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                if (res.gems > 0) {
                    Text("+${res.gems} 💎", color = CyberPrimary, fontWeight = FontWeight.Black, fontSize = 26.sp, fontFamily = FontFamily.Monospace)
                }
                if (res.shardHullId != null && res.shardCount > 0) {
                    Text(
                        "+${res.shardCount} 🧩 ${hullName(res.shardHullId)} shards",
                        color = gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center
                    )
                }
                if (res.unlockedHullId != null) {
                    Text(
                        "🎊 ${hullName(res.unlockedHullId)} UNLOCKED!",
                        color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (!res.isStreakBonus && res.message != null) {
                    Text(
                        "“${res.message}”",
                        color = CyberOnSurface.copy(alpha = 0.9f), fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { close() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("NICE!", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

@Composable
fun UpgradesPanel(viewModel: NeonRushViewModel, profile: GameProfile) {
    val isPro = profile.subscriptionPro || TESTING_DISABLE_PRO_GATE
    val cap = Upgrades.maxLevelFor(isPro)
    val pulse by rememberInfiniteTransition(label = "maxPulse").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "maxPulseA"
    )

    Text(
        text = "⚙️ UPGRADES",
        fontSize = 16.sp, fontWeight = FontWeight.Bold,
        color = CyberPrimary, fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    Text(
        text = "Permanent boosts bought with gems. Effects are small and prices climb steeply with every level. " +
            if (isPro) "Pro: all 5 levels unlocked." else "Free pilots reach Lv ${Upgrades.FREE_MAX_LEVEL}; Pro unlocks Lv 4-5.",
        color = CyberOnSurface.copy(alpha = 0.7f), fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    Upgrades.ALL.forEach { def ->
        val lvl = Upgrades.level(profile.upgradesCsv, def.id)
        val maxed = lvl >= Upgrades.MAX_LEVEL
        val proLocked = !maxed && lvl >= cap
        val cost = Upgrades.nextCost(def, lvl)
        val borderColor = if (maxed) CyberPrimary.copy(alpha = pulse) else Color.Transparent

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (maxed) CyberPrimary.copy(alpha = 0.12f) else CyberSurface)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "${def.emoji} ${def.name}",
                    fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(def.desc, fontSize = 11.sp, color = CyberOnSurface.copy(alpha = 0.7f))
                Row(modifier = Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..Upgrades.MAX_LEVEL) {
                        Box(
                            modifier = Modifier
                                .size(width = 22.dp, height = 6.dp)
                                .background(
                                    when {
                                        i <= lvl -> CyberPrimary
                                        i > cap -> Color(0xFFFFD23F).copy(alpha = 0.25f)
                                        else -> CyberOnSurface.copy(alpha = 0.2f)
                                    },
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
                Text(
                    text = if (lvl == 0) "Next: ${Upgrades.effectText(def.id, 1)}"
                    else if (maxed) "MAX: ${Upgrades.effectText(def.id, lvl)}"
                    else "Now: ${Upgrades.effectText(def.id, lvl)}  →  ${Upgrades.effectText(def.id, lvl + 1)}",
                    fontSize = 11.sp, color = CyberPrimary, fontFamily = FontFamily.Monospace
                )
            }
            when {
                maxed -> Text("MAX", color = CyberPrimary, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                proLocked -> Text(
                    "⚡ PRO\nLv ${lvl + 1}", color = Color(0xFFFFD23F), fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center
                )
                else -> Button(
                    onClick = { viewModel.purchaseUpgrade(def.id, isPro) },
                    enabled = profile.gems >= cost,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("💎$cost", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(80.dp))
}

@Composable
fun SkinsDeckTab(viewModel: NeonRushViewModel, profile: GameProfile) {
    val unlockedSkins = remember(profile.unlockedSkinsCsv) {
        profile.unlockedSkinsCsv.split(",").toSet()
    }
    val activity = LocalContext.current as? Activity
    var selectedTab by remember { mutableStateOf("pilots") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        Text(
            text = "⛽ FUEL TANK UPGRADES",
            color = CyberSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (profile.fuelTiersOwned > 0) {
            Text(
                text = "Current longevity: +${profile.fuelTiersOwned * 20}%",
                color = CyberPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tierPrices = listOf("$2.99", "$4.99", "$7.99", "$11.99", "$15.99")
            val tierProductIds = listOf("fuel_tier_1", "fuel_tier_2", "fuel_tier_3", "fuel_tier_4", "fuel_tier_5")
            for (i in 1..5) {
                val isOwned = profile.fuelTiersOwned >= i
                val isBuyable = profile.fuelTiersOwned == i - 1
                Button(
                    onClick = {
                        if (isBuyable && activity != null) {
                            viewModel.purchaseFuelTier(activity, i, tierProductIds[i - 1])
                        }
                    },
                    enabled = isBuyable || isOwned,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isOwned -> CyberPrimary
                            isBuyable -> CyberSurface
                            else -> CyberSurface.copy(alpha = 0.4f)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(
                        1.dp,
                        if (isBuyable) CyberPrimary else CyberPrimary.copy(alpha = 0.25f),
                        RoundedCornerShape(8.dp)
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isOwned) "TIER $i ✓" else "TIER $i",
                            color = if (isOwned) Color.Black else Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "+${i * 20}%",
                            color = if (isOwned) Color.Black else CyberSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        if (!isOwned) {
                            Text(
                                text = tierPrices[i - 1],
                                color = if (isBuyable) CyberPrimary else Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CyberSurface),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "PILOT SUITS",
                color = if (selectedTab == "pilots") CyberBackground else CyberPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(if (selectedTab == "pilots") CyberPrimary else Color.Transparent)
                    .clickable { selectedTab = "pilots" }
                    .padding(vertical = 10.dp)
            )
            Text(
                text = "SHIP HULLS",
                color = if (selectedTab == "ships") CyberBackground else CyberPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(if (selectedTab == "ships") CyberPrimary else Color.Transparent)
                    .clickable { selectedTab = "ships" }
                    .padding(vertical = 10.dp)
            )
            Text(
                text = "UPGRADES",
                color = if (selectedTab == "upgrades") CyberBackground else CyberPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(if (selectedTab == "upgrades") CyberPrimary else Color.Transparent)
                    .clickable { selectedTab = "upgrades" }
                    .padding(vertical = 10.dp)
            )
        }
        if (selectedTab == "upgrades") {
            UpgradesPanel(viewModel, profile)
        }
        if (selectedTab == "pilots") {
            val unlockedPilotSkins = remember(profile.unlockedPilotSkinsCsv) {
                profile.unlockedPilotSkinsCsv.split(",").toSet()
            }

            Text(
                text = "🧑‍🚀 PILOT SUITS",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = CyberPrimary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Text(
                text = "Suit up your pilot. Some are earned by completing Worlds, others can be bought outright.",
                color = CyberOnSurface.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Skins.ALL.forEach { skin ->
                val isUnlocked = unlockedPilotSkins.contains(skin.id)
                val isActive = profile.activePilotSkinId == skin.id
                val isStorySkin = skin.unlockWorldId != null

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) CyberPrimary.copy(alpha = 0.15f) else CyberSurface)
                        .border(
                            1.dp,
                            if (isActive) CyberPrimary else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SuitPreview(skin.id, Modifier.size(64.dp).padding(end = 10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = skin.name,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = skin.description,
                            fontSize = 11.sp,
                            color = CyberPrimary.copy(alpha = 0.7f)
                        )
                    }

                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .background(CyberPrimary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = CyberBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else if (isUnlocked) {
                        Button(
                            onClick = { viewModel.equipPilotSkin(skin.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberSurface.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.border(1.dp, CyberPrimary, RoundedCornerShape(4.dp))
                        ) {
                            Text(text = "EQUIP", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    } else if (isStorySkin) {
                        Text(
                            text = "🔒 World ${skin.unlockWorldId}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                        } else if (!skin.purchasable) {
    Text(
        text = "🏆 Mission Reward",
        fontSize = 11.sp,
        color = Color(0xFFD4AF37),
        fontFamily = FontFamily.Monospace
    )
                    } else {
                        Button(
                            onClick = {
                                activity?.let {
                                    viewModel.purchasePilotSkin(it, skin.id, "neonrush_skin_${skin.id}")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(text = "BUY ${skin.priceUsd}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (selectedTab == "ships") {
        Text(
            text = "🎨 SHIP CUSTOMIZATION DECK",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = CyberPrimary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Text(
            text = "Exchange hard-earned gems for advanced cyberpunk ship hulls. Higher level metrics unlock legendary cosmetics.",
            color = CyberOnSurface.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(CyberSurface, RoundedCornerShape(8.dp))
                .border(1.dp, CyberPrimary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AVAILABLE BALANCE",
                color = Color.White.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            
        Text(
                text = "💎 ${profile.gems}",
                color = CyberPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }

        viewModel.shopSkins.forEach { (id, name, cost) ->
            val isUnlocked = unlockedSkins.contains(id)
            val isActive = profile.activeSkinId == id

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isActive) CyberPrimary.copy(alpha = 0.15f) else CyberSurface)
                    .border(
                        1.dp,
                        if (isActive) CyberPrimary else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        if (isUnlocked) {
                            viewModel.purchaseSkin(id, 0)
                        }
                    }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (id != "cyan_diamond") HullPreview(id)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (id) {
                                "cyan_diamond" -> "💠"
                                "purple_square" -> "🔮"
                                "green_triangle" -> "🔺"
                                "magenta_pulse" -> "⚡"
                                "gold_transcendence" -> "🏆"
                                "solar_comet" -> "☄️"
                                "ice_shard" -> "❄️"
                                "static_storm" -> "🌩️"
                                "vaporwave_wave" -> "🌊"
                                "phantom_echo" -> "👻"
                                else -> "🪐"
                            },
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = if (cost == 0) "Free Starter Hull" else "$cost GEMS Cost",
                        fontSize = 11.sp,
                        color = CyberPrimary.copy(alpha = 0.7f)
                    )
                    val shardNeed = DailyCrate.HULL_SHARDS[id]
                    if (!isUnlocked && shardNeed != null) {
                        Text(
                            text = "🧩 ${DailyCrate.shards(profile.crateShardsCsv, id)}/$shardNeed shards (Daily Crate)",
                            fontSize = 10.sp,
                            color = Color(0xFFFFD23F).copy(alpha = 0.85f)
                        )
                    }
                }

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .background(CyberPrimary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = CyberBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else if (isUnlocked) {
                    Button(
                        onClick = { viewModel.purchaseSkin(id, 0) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurface.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.border(1.dp, CyberPrimary, RoundedCornerShape(4.dp))
                    ) {
                        Text(text = "EQUIP", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                } else {
                    Button(
                        onClick = { viewModel.purchaseSkin(id, cost) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                        shape = RoundedCornerShape(4.dp),
                        enabled = profile.gems >= cost,
                        modifier = Modifier.testTag("buy_skin_button")
                    ) {
                        Text(text = "BUY 💎$cost", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(20.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberSurface.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberTertiary, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🌌 TRANSCENDENCE PROTOCOL",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTertiary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "Exchange 100+ points of highscore data for instant Transcendence level ranks and a +150 bonus gem payout. Relive the cyber alley!",
                    fontSize = 11.sp,
                    color = CyberOnSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.triggerTranscendence() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTertiary),
                    shape = RoundedCornerShape(6.dp),
                    enabled = profile.bestScore >= 100,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transcendence_button")
                ) {
                    Text(
                        text = if (profile.bestScore >= 100) "INITIATE TRANSCENDENCE" else "REQUIRES 100+ SCORE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    

        
Spacer(modifier = Modifier.height(20.dp))
        if (!profile.adsRemoved) {
    Text(
        text = "🚫 REMOVE ADS",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = CyberPrimary,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CyberSurface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Remove all ads",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "One-time purchase, forever",
                color = CyberOnSurface.copy(alpha = 0.65f),
                fontSize = 11.sp
            )
        }
        Button(
            onClick = {
                activity?.let { viewModel.purchaseRemoveAds(it) }
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(text = "BUY ${RevenueCatManager.REMOVE_ADS_PRICE_USD}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

Text(
    text = "⚡ STARTER PACK",
    fontSize = 16.sp,
    fontWeight = FontWeight.Bold,
    color = CyberPrimary,
    fontFamily = FontFamily.Monospace,
    modifier = Modifier.padding(bottom = 6.dp)
)
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(CyberSurface)
        .padding(14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Column {
        Text(
            text = "💎 ${RevenueCatManager.STARTER_PACK_GEMS_AMOUNT} Gems",
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Limited-time offer for new pilots",
            color = CyberOnSurface.copy(alpha = 0.65f),
            fontSize = 11.sp
        )
    }
    Button(
        onClick = {
            activity?.let { viewModel.purchaseStarterPack(it) }
        },
        colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(text = "BUY ${RevenueCatManager.STARTER_PACK_PRICE_USD}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}
Spacer(modifier = Modifier.height(16.dp))

Text(
    text = "💎 GET MORE GEMS",
    fontSize = 16.sp,    fontWeight = FontWeight.Bold,
    color = CyberPrimary,
    fontFamily = FontFamily.Monospace,
    modifier = Modifier.padding(bottom = 6.dp)
)

        

        Text(
            text = "Skip the grind — top up your gem balance directly.",
            color = CyberOnSurface.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        listOf(
            Triple(RevenueCatManager.PRODUCT_ID_GEMS_SMALL, RevenueCatManager.GEMS_SMALL_AMOUNT, RevenueCatManager.GEMS_SMALL_PRICE_USD),
            Triple(RevenueCatManager.PRODUCT_ID_GEMS_MEDIUM, RevenueCatManager.GEMS_MEDIUM_AMOUNT, RevenueCatManager.GEMS_MEDIUM_PRICE_USD),
            Triple(RevenueCatManager.PRODUCT_ID_GEMS_LARGE, RevenueCatManager.GEMS_LARGE_AMOUNT, RevenueCatManager.GEMS_LARGE_PRICE_USD)
        ).forEach { (productId, amount, price) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberSurface)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💎 $amount Gems",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Button(
                    onClick = {
                        activity?.let { viewModel.purchaseGemPack(it, productId, amount) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(text = "BUY $price", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun RacingSimulatorScreen(
    simState: SimulationState,
    viewModel: NeonRushViewModel,
    isPro: Boolean,
    profile: GameProfile,
    onShowPaywall: () -> Unit
) {
    var controlOffset by remember { mutableStateOf(50f) }
    var previousUserYPos by remember { mutableStateOf(simState.userYPos) }
    val tiltAngle = (simState.userYPos - previousUserYPos).toFloat().coerceIn(-10f, 10f) * 1.8f
    SideEffect { previousUserYPos = simState.userYPos }
    val activity = LocalContext.current as? Activity

    if (simState.proGateActive && !simState.proGateTriggered) {
        val ticksLeft = (simState.proGateGraceUntilTick - simState.tickIndex).coerceAtLeast(0)
        val secondsLeft = ((ticksLeft * 120) / 1000f).coerceAtLeast(0f)
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "👑", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PRO ZONE AHEAD — %.1fs".format(secondsLeft),
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    if (simState.proGateTriggered) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(24.dp)
                        .background(CyberSurface, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Text(text = "👑", fontSize = 36.sp)
                    Text(
                        text = "PRO WORLD",
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "This world is part of Neon Rush Pro. Subscribe to fly through it, or head back to free-tier zones and keep flying.",
                        color = CyberOnSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
                    )
                    Button(
                        onClick = onShowPaywall,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SUBSCRIBE TO PRO", color = CyberBackground, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.redirectToFreeZone() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("BACK TO FREE ZONES", color = CyberPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Default running frames (used when the equipped skin has no custom frames yet)
    val pf1 = ImageBitmap.imageResource(id = R.drawable.pilot_run_1)
    val pf2 = ImageBitmap.imageResource(id = R.drawable.pilot_run_2)
    val pf3 = ImageBitmap.imageResource(id = R.drawable.pilot_run_3)
    val pf4 = ImageBitmap.imageResource(id = R.drawable.pilot_run_4)
    val pf5 = ImageBitmap.imageResource(id = R.drawable.pilot_run_5)
    val pf6 = ImageBitmap.imageResource(id = R.drawable.pilot_run_6)
    val defaultFrameIds = listOf(pf1, pf2, pf3, pf4, pf5, pf6)

    // Look up the equipped skin's custom running frames, if it has any
    val equippedSkin = remember(profile.activePilotSkinId) {
        Skins.ALL.find { it.id == profile.activePilotSkinId }
    }
    val overrideIds = equippedSkin?.pilotFrameOverrides
    val pilotFrames = if (overrideIds != null && overrideIds.size == 6) {
        overrideIds.map { resId -> ImageBitmap.imageResource(id = resId) }
    } else {
        defaultFrameIds
    }
        
    val shieldLevel = remember(profile.upgradesCsv) { Upgrades.level(profile.upgradesCsv, "shield") }
    val afterburnerLevel = remember(profile.upgradesCsv) { Upgrades.level(profile.upgradesCsv, "afterburner") }

    val gemImg = ImageBitmap.imageResource(id = R.drawable.gem)
    val coinImg = ImageBitmap.imageResource(id = R.drawable.coin)
    val spikesImg = ImageBitmap.imageResource(id = R.drawable.spikes)
    val laserImg = ImageBitmap.imageResource(id = R.drawable.laser1)
    val sawbladeImg = ImageBitmap.imageResource(id = R.drawable.sawblade)
    val droneImg = ImageBitmap.imageResource(id = R.drawable.drone)
    val spikesFlippedImg = ImageBitmap.imageResource(id = R.drawable.spikes_flipped)
    val barrierImg = ImageBitmap.imageResource(id = R.drawable.obstacle_barrier)
    val zapFieldImg = ImageBitmap.imageResource(id = R.drawable.obstacle_zap_field)
    val phantomImg = ImageBitmap.imageResource(id = R.drawable.obstacle_phantom)
    val splitterImg = ImageBitmap.imageResource(id = R.drawable.obstacle_splitter_v2)
    val tunnelTopImg = ImageBitmap.imageResource(id = R.drawable.obstacle_tunnel_top)
    val tunnelBottomImg = ImageBitmap.imageResource(id = R.drawable.obstacle_tunnel_bottom)
    val standardImg = ImageBitmap.imageResource(id = R.drawable.obstacle_standard)
    val puShieldImg = ImageBitmap.imageResource(id = R.drawable.powerup_shield)
val puMagnetImg = ImageBitmap.imageResource(id = R.drawable.powerup_magnet)
val puTimeSlowImg = ImageBitmap.imageResource(id = R.drawable.powerup_time_slow)
val puGhostImg = ImageBitmap.imageResource(id = R.drawable.powerup_ghost)
val puScoreX2Img = ImageBitmap.imageResource(id = R.drawable.powerup_score_x2)
val puScoreX5Img = ImageBitmap.imageResource(id = R.drawable.powerup_score_x5)
val puInvincibilityImg = ImageBitmap.imageResource(id = R.drawable.powerup_invincibility)
val puBoomClearImg = ImageBitmap.imageResource(id = R.drawable.powerup_boom_clear)
val puShrinkImg = ImageBitmap.imageResource(id = R.drawable.powerup_shrink)
val puLaneWarpImg = ImageBitmap.imageResource(id = R.drawable.powerup_lane_warp)
val puZoneSkipImg = ImageBitmap.imageResource(id = R.drawable.powerup_zone_skip)
val puLegendaryAuraImg = ImageBitmap.imageResource(id = R.drawable.powerup_legendary_aura)
val bulletImg = ImageBitmap.imageResource(id = R.drawable.hazard_bullet_boss)
val ghostMarkerImg = ImageBitmap.imageResource(id = R.drawable.marker_ghost_rival)
// Blink Strike hazard creatures (kindIdx 0-4). All 5 now have real art.
val blinkWolfImg = ImageBitmap.imageResource(id = R.drawable.hazard_blink_wolf)
val blinkRaptorImg = ImageBitmap.imageResource(id = R.drawable.hazard_blink_raptor)
val blinkHornetImg = ImageBitmap.imageResource(id = R.drawable.hazard_blink_hornet)
val blinkArachnidImg = ImageBitmap.imageResource(id = R.drawable.hazard_blink_arachnid)
val blinkWraithImg = ImageBitmap.imageResource(id = R.drawable.hazard_blink_wraith)
// Boss characters — one per world. Previously there was no boss sprite at
// all, only its bullets; this map gives each world its own boss art.
val bossImagesByWorld = mapOf(
    1 to ImageBitmap.imageResource(id = R.drawable.boss_blackout_front),
    2 to ImageBitmap.imageResource(id = R.drawable.boss_derelict_signal),
    3 to ImageBitmap.imageResource(id = R.drawable.boss_cell_block_zero),
    4 to ImageBitmap.imageResource(id = R.drawable.boss_green_hell),
    5 to ImageBitmap.imageResource(id = R.drawable.boss_red_protocol),
    6 to ImageBitmap.imageResource(id = R.drawable.boss_signal_fracture),
    7 to ImageBitmap.imageResource(id = R.drawable.boss_frozen_veil),
    8 to ImageBitmap.imageResource(id = R.drawable.boss_apex_signal)
)
    
    val textMeasurer = rememberTextMeasurer()
    var previousScoreForPopups by remember { mutableStateOf(simState.score) }
    val scorePopups = remember { mutableStateListOf<Triple<Int, Int,Int>>() }
    val scoreDelta = simState.score - previousScoreForPopups
    if (scoreDelta > 0) {
        scorePopups.add(Triple(scoreDelta, simState.tickIndex, simState.userYPos))
    }
    previousScoreForPopups = simState.score
    scorePopups.removeAll { (_, spawnTick, _) -> simState.tickIndex - spawnTick > 25 }

    var previousShakeMagnitude by remember { mutableStateOf(0f) }
    var flashStartTick by remember { mutableStateOf(-100) }
    val currentShakeMagnitude = kotlin.math.abs(simState.screenShakeX) + kotlin.math.abs(simState.screenShakeY)
    if (currentShakeMagnitude > 3f && previousShakeMagnitude <= 3f) {
        flashStartTick = simState.tickIndex
    }
    previousShakeMagnitude = currentShakeMagnitude
    
    Box(modifier = Modifier.fillMaxSize()) {
        val currentWorld by viewModel.currentWorld.collectAsState()
        val zoneInsideWorld = simState.currentZoneNumber in currentWorld.startZone..currentWorld.endZone
        LaunchedEffect(currentWorld.id, zoneInsideWorld) {
            // TESTING ONLY — gated by TESTING_DISABLE_PRO_GATE (see
            // NeonRushViewModel.kt) so this older, separate paywall trigger
            // doesn't fire during QA. Remove the "&& !TESTING_DISABLE_PRO_GATE"
            // when told to restore normal behavior.
            // zoneInsideWorld: free-tier zones past 40 fall back to a Pro world's
            // data but are NOT Pro content, so they must not trigger the paywall.
            if (currentWorld.requiresPro && zoneInsideWorld && !isPro && !TESTING_DISABLE_PRO_GATE) {
                viewModel.triggerPaywallTeaser(currentWorld)
                delay(2500)
                onShowPaywall()
            }
        }
        // Rift variants (re-color + layer remix + weather + entry card) apply past
        // zone 40. Free players (the gate redirects them to those zones) get the
        // lite set; Pro gets everything. TESTING_DISABLE_PRO_GATE counts as Pro
        // here, so turning the gate back on also restores the free/Pro split.
        val variant = remember(currentWorld.id, simState.currentZoneNumber, isPro) {
            WorldVariants.variantFor(
                currentWorld,
                simState.currentZoneNumber,
                isPro = isPro || TESTING_DISABLE_PRO_GATE
            )
        }
        ParallaxWorldBackground(
            // worldFamily groups Phase 2-4 worlds (ids 12-26) with their base
            // world (1-5) so they reuse its art. For worlds 1-11 it equals id.
            worldId = variant?.baseFamily ?: currentWorld.worldFamily,
            distanceMeters = simState.distanceMeters,
            fallbackColorHex = simState.zoneDNA.environmentColor,
            variant = variant
        )
        variant?.let { WorldWeatherOverlay(weather = it.weather, accent = it.accent) }
        run {
            val familyTitle = variant?.let { v -> Worlds.ALL.find { it.id == v.baseFamily }?.title }
                ?: currentWorld.title
            WorldEntryOverlay(
                key = variant?.key ?: "world${currentWorld.id}",
                title = if (variant?.isRift == true) "$familyTitle: ${variant.suffix}" else currentWorld.title,
                subtitle = when {
                    variant == null -> currentWorld.subtitle
                    variant.isRift -> "RIFT SECTOR ${variant.riftNumber}"
                    else -> "${currentWorld.subtitle} · ${variant.suffix}"
                },
                accent = variant?.accent ?: CyberPrimary,
                // Free players only, and only on every other rift sector so it
                // reads as a teaser rather than nagging. Uses the REAL isPro (not
                // the testing bypass) so you can see it while testing.
                proHint = if (!isPro && variant?.isRift == true && variant.riftNumber % 2 == 1)
                    "⚡ PRO UNLOCKS: WEATHER • 5 WORLDS • 8 LOOKS" else null
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Telemetry top panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hudShadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 6f)
                Column {
                    Text(
                        text = "STORM ZONE: " + simState.currentZoneName,
                        color = CyberPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        style = TextStyle(shadow = hudShadow)
                    )
                    Text(
                        text = "${simState.distanceMeters.toInt()}m",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        style = TextStyle(shadow = hudShadow)
                    )
                    RunGoalsHud(simState)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${simState.speedKmh} KM/H",
                        color = CyberSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        style = TextStyle(shadow = hudShadow)
                    )
                    Text(
                        text = "${simState.score} PTS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        style = TextStyle(shadow = hudShadow)
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = simState.tickIndex < 167 && profile.fuelTiersOwned > 0,
                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(500)),
                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(800)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(CyberSurface.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .border(1.dp, CyberPrimary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⛽ CURRENT LONGEVITY: +${profile.fuelTiersOwned * 20}%",
                        color = CyberPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = simState.tickIndex < simState.sectorBannerUntilTick ||
                    (simState.sectorBonusPending > 0 && simState.tickIndex < simState.sectorBonusExpiresAtTick),
                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(500)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val canDoubleBonus = simState.sectorBonusPending > 0 &&
                        simState.tickIndex < simState.sectorBonusExpiresAtTick && !isPro
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .then(
                                if (canDoubleBonus) Modifier.clickable {
                                    // Tapping the banner itself works too — players
                                    // naturally tap the thing they're looking at.
                                    activity?.let {
                                        AdMobManager.showRewardedIfReady(it, RewardedSlot.DOUBLE_GEMS) {
                                            viewModel.claimSectorAdBonus()
                                        }
                                    }
                                } else Modifier
                            )
                            .padding(vertical = 6.dp, horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = simState.sectorBannerText,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (simState.sectorBonusPending > 0 && simState.tickIndex < simState.sectorBonusExpiresAtTick && !isPro) {
                        Button(
                            onClick = {
                                activity?.let {
                                    AdMobManager.showRewardedIfReady(it, RewardedSlot.DOUBLE_GEMS) {
                                        viewModel.claimSectorAdBonus()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = adButtonLabel(RewardedSlot.DOUBLE_GEMS, "🎬 WATCH AD: DOUBLE TO +${simState.sectorBonusPending * 2}💎"),
                                color = Color.Black,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaPercent = (dragAmount.y / size.height) * 100f
                                viewModel.adjustUserY(deltaPercent.toInt())
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cw = size.width
                        val ch = size.height

                        drawContext.canvas.translate(simState.screenShakeX, simState.screenShakeY)

                        val gridLines = 7
                        for (i in 1..gridLines) {
                            val x = cw * i / (gridLines + 1)
                            drawLine(
                                color = CyberPrimary.copy(alpha = 0.08f),
                                start = Offset(x, 0f),
                                end = Offset(x, ch),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        val path = Path().apply {
                            moveTo(0f, ch * 0.5f)
                            val ticksSize = simState.ghostYPath.size
                            for (i in 0 until ticksSize) {
                                val posX = cw * i / (ticksSize - 1).coerceAtLeast(1)
                                val posY = ch * (simState.ghostYPath[i] / 100f)
                                lineTo(posX, posY)
                            }
                        }
                        drawPath(
                            path = path,
                            color = CyberPrimary.copy(alpha = 0.15f),
                            style = Stroke(width = 2.dp.toPx())
                        )

                        for (elem in simState.activeTrackElements) {
                            val x = cw * elem.xOffsetFraction
                            val y = ch * (elem.yMatchPos / 100f)
                            
                            when (elem.type) {
                                "gem" -> {
                                    val pulse = 1f + 0.15f * sin(simState.tickIndex * 0.3f)
                                    val baseSize = ch * 0.07f * pulse
                                    val w = baseSize * (gemImg.width.toFloat() / gemImg.height.toFloat())
                                    val h = baseSize
                                    drawImage(
                                        image = gemImg,
                                        dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - h / 2f).roundToInt()),
                                        dstSize = IntSize(w.roundToInt(), h.roundToInt())
                                    )
                                }
                                "fuel" -> {
                                    val angle = (simState.tickIndex * 6f) % 360f
                                    val baseSize = ch * 0.065f
                                    val w = baseSize * (coinImg.width.toFloat() / coinImg.height.toFloat())
                                    rotate(degrees = angle, pivot = Offset(x, y)) {
                                        drawImage(
                                            image = coinImg,
                                            dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - baseSize / 2f).roundToInt()),
                                            dstSize = IntSize(w.roundToInt(), baseSize.roundToInt())
                                        )
                                    }
                                }
                                "powerup" -> {
    val puImg = when (elem.subType) {
        "PU1" -> puShieldImg
        "PU2" -> puMagnetImg
        "PU3" -> puTimeSlowImg
        "PU4" -> puGhostImg
        "PU5" -> puScoreX2Img
        "PU6" -> puScoreX5Img
        "PU7" -> puInvincibilityImg
        "PU8" -> puBoomClearImg
        "PU9" -> puShrinkImg
        "PU10" -> puLaneWarpImg
        "PU11" -> puZoneSkipImg
        "PU12" -> puLegendaryAuraImg
        else -> puScoreX2Img
    }
    val pulse = 1f + 0.15f * sin(simState.tickIndex * 0.3f)
    val baseSize = ch * 0.08f * pulse
    val w = baseSize * (puImg.width.toFloat() / puImg.height.toFloat())
    drawImage(
        image = puImg,
        dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - baseSize / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), baseSize.roundToInt())
    )
}
                                "obstacle" -> {
                                    val obsColor = Color(0xFFFF0055)
                                    when (elem.subType) {
                                        "PILLAR_TOP" -> {
                                            drawRect(
                                                color = obsColor,
                                                topLeft = Offset(x - cw * 0.029f, 0f),
                                                size = Size(cw * 0.058f, y)
                                            )
                                            drawRect(
                                                color = Color.White.copy(alpha = 0.4f),
                                                topLeft = Offset(x - cw * 0.029f, 0f),
                                                size = Size(cw * 0.058f, y),
                                                style = Stroke(1.dp.toPx())
                                            )
                                        }
                                        "PILLAR_BOTTOM" -> {
                                            drawRect(
                                                color = obsColor,
                                                topLeft = Offset(x - cw * 0.029f, y),
                                                size = Size(cw * 0.058f, ch - y)
                                            )
                                            drawRect(
                                                color = Color.White.copy(alpha = 0.4f),
                                                topLeft = Offset(x - cw * 0.029f, y),
                                                size = Size(cw * 0.058f, ch - y),
                                                style = Stroke(1.dp.toPx())
                                            )
                                        }
                                        "STALACTITE" -> {
                                            val baseSize = ch * 0.105f
                                            val w = baseSize * (spikesFlippedImg.width.toFloat() / spikesFlippedImg.height.toFloat())
                                            val glowPulse = 0.7f + 0.3f * sin(simState.tickIndex * 0.4f)
                                            drawImage(
                                                image = spikesFlippedImg,
                                                dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - baseSize / 2f).roundToInt()),
                                                dstSize = IntSize(w.roundToInt(), baseSize.roundToInt()),
                                                alpha = glowPulse
                                            )
                                        }
                                        "STALAGMITE" -> {
                                            val baseSize = ch * 0.105f
                                            val w = baseSize * (spikesImg.width.toFloat() / spikesImg.height.toFloat())
                                            val glowPulse = 0.7f + 0.3f * sin(simState.tickIndex * 0.4f)
                                            drawImage(
                                                image = spikesImg,
                                                dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - baseSize / 2f).roundToInt()),
                                                dstSize = IntSize(w.roundToInt(), baseSize.roundToInt()),
                                                alpha = glowPulse
                                            )
                                        }
                                        "LASER" -> {
                                            val glowPulse = 0.6f + 0.4f * sin(simState.tickIndex * 0.5f)
                                            val h = ch * 0.25f
                                            val w = h * (laserImg.width.toFloat() / laserImg.height.toFloat())
                                            drawImage(
                                                image = laserImg,
                                                dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - h / 2f).roundToInt()),
                                                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                                                alpha = glowPulse
                                            )
                                        }
                                        "BLADE" -> {
                                            val angle = (simState.tickIndex * 12f) % 360f
                                            val size = ch * 0.095f
                                            rotate(degrees = angle, pivot = Offset(x, y)) {
                                                drawImage(
                                                    image = sawbladeImg,
                                                    dstOffset = IntOffset((x - size / 2f).roundToInt(), (y - size / 2f).roundToInt()),
                                                    dstSize = IntSize(size.roundToInt(), size.roundToInt())
                                                )
                                            }
                                        }
                                        "BARRIER" -> {
                                            val baseSize = ch * 0.185f
                                            val w = baseSize * (barrierImg.width.toFloat() / barrierImg.height.toFloat())
                                            drawImage(
                                                image = barrierImg,
                                                dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - baseSize / 2f).roundToInt()),
                                                dstSize = IntSize(w.roundToInt(), baseSize.roundToInt())
                                            )
                                        }
                                        "ZAP_FIELD" -> {
                                            val glowPulse = 0.6f + 0.4f * sin(simState.tickIndex * 0.5f)
                                            val baseSize = ch * 0.175f
                                            val w = baseSize * (zapFieldImg.width.toFloat() / zapFieldImg.height.toFloat())
                                            drawImage(
                                                image = zapFieldImg,
                                                dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - baseSize / 2f).roundToInt()),
                                                dstSize = IntSize(w.roundToInt(), baseSize.roundToInt()),
                                                alpha = glowPulse
                                            )
                                        }
                                        "PHANTOM" -> {
                                            val bob = sin(simState.tickIndex * 0.2f) * ch * 0.015f
                                            val flicker = 0.5f + 0.5f * sin(simState.tickIndex * 0.3f)
                                            val baseSize = ch * 0.185f
                                            val w = baseSize * (phantomImg.width.toFloat() / phantomImg.height.toFloat())
                                            drawImage(
                                                image = phantomImg,
                                                dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - baseSize / 2f + bob).roundToInt()),
                                                dstSize = IntSize(w.roundToInt(), baseSize.roundToInt()),
                                                alpha = flicker
                                            )
                                        }
                                        "SPLITTER" -> {
                                            val angle = (simState.tickIndex * 8f) % 360f
                                            val baseSize = ch * 0.15f
                                            val w = baseSize * (splitterImg.width.toFloat() / splitterImg.height.toFloat())
                                            rotate(degrees = angle, pivot = Offset(x, y)) {
                                                drawImage(
                                                    image = splitterImg,
                                                    dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - baseSize / 2f).roundToInt()),
                                                    dstSize = IntSize(w.roundToInt(), baseSize.roundToInt())
                                                )
                                            }
                                        }
                                        "TUNNEL_TOP" -> {
                                            val baseSize = ch * 0.20f
                                            val w = baseSize * (tunnelTopImg.width.toFloat() / tunnelTopImg.height.toFloat())
                                            drawImage(
                                                image = tunnelTopImg,
                                                dstOffset = IntOffset((x - w / 2f).roundToInt(), 0),
                                                dstSize = IntSize(w.roundToInt(), baseSize.roundToInt())
                                            )
                                        }
                                        "TUNNEL_BOTTOM" -> {
                                            val baseSize = ch * 0.20f
                                            val w = baseSize * (tunnelBottomImg.width.toFloat() / tunnelBottomImg.height.toFloat())
                                            drawImage(
                                                image = tunnelBottomImg,
                                                dstOffset = IntOffset((x - w / 2f).roundToInt(), (ch - baseSize).roundToInt()),
                                                dstSize = IntSize(w.roundToInt(), baseSize.roundToInt())
                                            )
                                        }
                                        "BLINK_HAZARD" -> {
                                            val idParts = elem.id.split("_")
                                            val spawnTick = idParts.getOrNull(1)?.toIntOrNull() ?: 0
                                            val kindIdx = idParts.getOrNull(2)?.toIntOrNull() ?: 0
                                            val cycleLength = 25
                                            val phase = ((simState.tickIndex - spawnTick) % cycleLength + cycleLength) % cycleLength
                                            val isVisible = phase < 8
                                            val hazardColor = hexToColor(simState.zoneDNA.environmentColor)
                                            val baseSize = ch * 0.15f
                                            // All 5 variants now have real generated creature art.
                                            val creatureImg = when (kindIdx) {
                                                0 -> blinkWolfImg
                                                1 -> blinkRaptorImg
                                                2 -> blinkHornetImg
                                                3 -> blinkArachnidImg
                                                4 -> blinkWraithImg
                                                else -> null
                                            }
                                            if (isVisible) {
                                                val flicker = 0.75f + 0.25f * sin(simState.tickIndex * 0.9f)
                                                // Outer soft halo — big, faint, sells "this thing radiates energy"
                                                drawCircle(
                                                    color = hazardColor.copy(alpha = 0.14f * flicker),
                                                    radius = baseSize * 1.05f,
                                                    center = Offset(x, y)
                                                )
                                                // Mid glow ring
                                                drawCircle(
                                                    color = hazardColor.copy(alpha = 0.30f * flicker),
                                                    radius = baseSize * 0.65f,
                                                    center = Offset(x, y)
                                                )
                                                // Bright white-hot core flash directly behind the creature
                                                drawCircle(
                                                    color = Color.White.copy(alpha = 0.18f * flicker),
                                                    radius = baseSize * 0.28f,
                                                    center = Offset(x, y)
                                                )
                                                // Orbiting energy sparks — 4 small bright motes circling the
                                                // creature, giving a "charged/alive" feel that a static image
                                                // alone can't. Angle offset per-hazard (via spawnTick) so
                                                // multiple on screen don't all spin in perfect unison.
                                                val orbitAngleBase = (simState.tickIndex * 0.12f) + spawnTick * 0.7f
                                                for (i in 0 until 4) {
                                                    val angle = orbitAngleBase + (i * (kotlin.math.PI.toFloat() / 2f))
                                                    val orbitRadius = baseSize * 0.55f
                                                    val sparkX = x + kotlin.math.cos(angle) * orbitRadius
                                                    val sparkY = y + kotlin.math.sin(angle) * orbitRadius * 0.6f
                                                    drawCircle(
                                                        color = Color.White.copy(alpha = 0.7f * flicker),
                                                        radius = baseSize * 0.035f,
                                                        center = Offset(sparkX, sparkY)
                                                    )
                                                    drawCircle(
                                                        color = hazardColor.copy(alpha = 0.4f * flicker),
                                                        radius = baseSize * 0.07f,
                                                        center = Offset(sparkX, sparkY)
                                                    )
                                                }
                                                // Jagged electric arcs flickering at the rim — reuses the
                                                // tick index as a pseudo-random jitter seed so the arcs
                                                // shimmer rather than sit static.
                                                repeat(3) { i ->
                                                    val seed = (simState.tickIndex + i * 37 + spawnTick)
                                                    if (seed % 5 == 0) {
                                                        val arcAngle = (seed * 47 % 360) * (kotlin.math.PI.toFloat() / 180f)
                                                        val arcStart = baseSize * 0.5f
                                                        val arcEnd = baseSize * (0.75f + (seed % 3) * 0.08f)
                                                        val sx = x + kotlin.math.cos(arcAngle) * arcStart
                                                        val sy = y + kotlin.math.sin(arcAngle) * arcStart * 0.6f
                                                        val ex = x + kotlin.math.cos(arcAngle) * arcEnd
                                                        val ey = y + kotlin.math.sin(arcAngle) * arcEnd * 0.6f
                                                        drawLine(
                                                            color = Color.White.copy(alpha = 0.6f),
                                                            start = Offset(sx, sy),
                                                            end = Offset(ex, ey),
                                                            strokeWidth = 1.5.dp.toPx()
                                                        )
                                                    }
                                                }
                                                if (creatureImg != null) {
                                                    val cw2 = baseSize * (creatureImg.width.toFloat() / creatureImg.height.toFloat())
                                                    drawImage(
                                                        image = creatureImg,
                                                        dstOffset = IntOffset((x - cw2 / 2f).roundToInt(), (y - baseSize / 2f).roundToInt()),
                                                        dstSize = IntSize(cw2.roundToInt(), baseSize.roundToInt()),
                                                        alpha = flicker
                                                    )
                                                } else {
                                                    val spread = baseSize * (0.35f + 0.05f * kindIdx)
                                                    val boltPath = Path().apply {
                                                        moveTo(x - spread * 0.3f, y - baseSize * 0.5f)
                                                        lineTo(x + spread * 0.15f, y - baseSize * 0.1f)
                                                        lineTo(x - spread * 0.05f, y)
                                                        lineTo(x + spread * 0.3f, y + baseSize * 0.5f)
                                                        lineTo(x, y + baseSize * 0.05f)
                                                        lineTo(x - spread * 0.25f, y + baseSize * 0.15f)
                                                        close()
                                                    }
                                                    drawPath(path = boltPath, color = hazardColor.copy(alpha = 0.9f * flicker))
                                                    drawPath(
                                                        path = boltPath,
                                                        color = Color.White.copy(alpha = 0.5f * flicker),
                                                        style = Stroke(1.dp.toPx())
                                                    )
                                                }
                                            } else {
                                                // Faint telltale during the "invisible" phase: harmless to
                                                // touch, but gives sharp-eyed players a way to track its lane.
                                                // A gentle pulsing ring (rather than a flat dot) reads as
                                                // "still there, just phased out" instead of "gone."
                                                val telltalePulse = 0.5f + 0.5f * sin(simState.tickIndex * 0.3f)
                                                drawCircle(
                                                    color = hazardColor.copy(alpha = 0.08f + 0.06f * telltalePulse),
                                                    radius = baseSize * (0.35f + 0.1f * telltalePulse),
                                                    center = Offset(x, y)
                                                )
                                            }
                                        }
                                        "DRONE" -> {
                                            // Hovering drone: a slightly faster,
                                            // more erratic bob than STANDARD's
                                            // gentle drift, since it's meant to
                                            // read as a small tracking machine
                                            // rather than a static obstacle.
                                            val bob = sin(simState.tickIndex * 0.35f) * ch * 0.02f
                                            val baseSize = ch * 0.15f
                                            val w = baseSize * (droneImg.width.toFloat() / droneImg.height.toFloat())
                                            val droneY = y - baseSize / 2f + bob
                                            // Thruster trail streaking behind it (toward the right, where it
                                            // came from) — sells "actively flying/hunting," not just floating.
                                            val trailAlpha = 0.35f + 0.15f * sin(simState.tickIndex * 0.5f)
                                            drawLine(
                                                color = Color(0xFF00E5FF).copy(alpha = trailAlpha),
                                                start = Offset(x + w * 0.65f, droneY + baseSize / 2f),
                                                end = Offset(x + w * 0.35f, droneY + baseSize / 2f),
                                                strokeWidth = (baseSize * 0.12f)
                                            )
                                            // Faint red targeting-scanner ring — a quiet "it's locked onto
                                            // you" tell, reinforcing the homing behavior visually.
                                            val scanPulse = 0.5f + 0.5f * sin(simState.tickIndex * 0.25f)
                                            drawCircle(
                                                color = Color(0xFFFF3355).copy(alpha = 0.18f * scanPulse),
                                                radius = baseSize * 0.6f,
                                                center = Offset(x, droneY + baseSize / 2f),
                                                style = Stroke(1.5.dp.toPx())
                                            )
                                            drawImage(
                                                image = droneImg,
                                                dstOffset = IntOffset((x - w / 2f).roundToInt(), droneY.roundToInt()),
                                                dstSize = IntSize(w.roundToInt(), baseSize.roundToInt())
                                            )
                                        }
                                        else -> {
                                            val bob = sin(simState.tickIndex * 0.2f) * ch * 0.015f
                                            val baseSize = ch * 0.16f
                                            val w = baseSize * (standardImg.width.toFloat() / standardImg.height.toFloat())
                                            drawImage(
                                                image = standardImg,
                                                dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - baseSize / 2f + bob).roundToInt()),
                                                dstSize = IntSize(w.roundToInt(), baseSize.roundToInt())
                                            )
                                        }
                                    }
                                }
                               "bullet" -> {
    if (elem.subType == "DRONE_SHOT") {
        // Small glowing energy bolt with a motion trail, distinct from the
        // boss's bullet sprite — reads as "fast small threat" at a glance.
        val boltColor = Color(0xFF00E5FF)
        val boltLen = ch * 0.045f
        drawLine(
            color = boltColor.copy(alpha = 0.35f),
            start = Offset(x + boltLen * 1.6f, y),
            end = Offset(x, y),
            strokeWidth = 4.dp.toPx()
        )
        drawCircle(color = boltColor.copy(alpha = 0.9f), radius = ch * 0.014f, center = Offset(x, y))
        drawCircle(color = Color.White.copy(alpha = 0.8f), radius = ch * 0.006f, center = Offset(x, y))
    } else {
        val baseSize = ch * 0.07f
        val w = baseSize * (bulletImg.width.toFloat() / bulletImg.height.toFloat())
        // Tapered tracer trail — thicker near the bullet, fading to a point
        // behind it. No soft glow halo: it should read as a sharp, fast
        // projectile, not a glowing blob.
        val trailPath = Path().apply {
            moveTo(x + w * 0.3f, y - baseSize * 0.16f)
            lineTo(x + w * 0.3f, y + baseSize * 0.16f)
            lineTo(x + w * 1.5f, y)
            close()
        }
        drawPath(path = trailPath, color = Color(0xFFFF4433).copy(alpha = 0.55f))
        drawImage(
            image = bulletImg,
            dstOffset = IntOffset((x - w / 2f).roundToInt(), (y - baseSize / 2f).roundToInt()),
            dstSize = IntSize(w.roundToInt(), baseSize.roundToInt())
        )
    }
} 
                            }
                        }

                        for (p in simState.particles) {
                            val px = cw * p.x
                            val py = ch * (p.y / 100f)
                            val lifeFrac = (1f - (p.age.toFloat() / p.maxAge.toFloat())).coerceIn(0f, 1f)
                            val particleColor = Color(p.colorArgb).copy(alpha = lifeFrac)
                            val radius = if (p.kind == "explosion") (3f + 4f * lifeFrac).dp.toPx() else (2f + 2f * lifeFrac).dp.toPx()
                            drawCircle(
                                color = particleColor,
                                radius = radius,
                                center = Offset(px, py)
                            )
                        }

                        val ticksCount = simState.ghostYPath.size
                        val userX = cw * 0.2f
                        
                        if (ticksCount > 0) {
                            val ghostY = ch * (simState.ghostYPos / 100f)
                            val ghostX = cw * 0.2f

                            val markerSize = ch * 0.065f
    val markerPulse = 0.5f + 0.5f * sin(simState.tickIndex * 0.2f)
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = 0.25f + 0.15f * markerPulse),
        radius = markerSize * (0.75f + 0.15f * markerPulse),
        center = Offset(ghostX, ghostY)
    )
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = 0.5f),
        radius = markerSize * 0.55f,
        center = Offset(ghostX, ghostY),
        style = Stroke(1.5.dp.toPx())
    )
    drawImage(
        image = ghostMarkerImg,
        dstOffset = IntOffset((ghostX - markerSize / 2f).roundToInt(), (ghostY - markerSize / 2f).roundToInt()),
        dstSize = IntSize(markerSize.roundToInt(), markerSize.roundToInt())
    )
                                
                        }

                        val userY = ch * (simState.userYPos / 100f)

                        if (simState.bossActive) {
                            val bossImg = bossImagesByWorld[variant?.baseFamily ?: currentWorld.worldFamily] ?: bossImagesByWorld[1]!!
                            val bossX = cw * 0.88f
                            val bossYPx = ch * (simState.bossY / 100f)
                            val bossDisplayHeight = ch * 0.32f
                            val bossDisplayWidth = bossDisplayHeight * (bossImg.width.toFloat() / bossImg.height.toFloat())
                            val bossFlicker = 0.9f + 0.1f * sin(simState.tickIndex * 0.5f)
                            val bossColor = hexToColor(simState.zoneDNA.environmentColor)

                            // Layered aura behind the boss — sells "imposing,
                            // powered-up enemy" rather than a flat sprite.
                            drawCircle(
                                color = bossColor.copy(alpha = 0.10f * bossFlicker),
                                radius = bossDisplayHeight * 0.85f,
                                center = Offset(bossX, bossYPx)
                            )
                            drawCircle(
                                color = bossColor.copy(alpha = 0.20f * bossFlicker),
                                radius = bossDisplayHeight * 0.55f,
                                center = Offset(bossX, bossYPx)
                            )
                            // Periodic "power surge" — a brief brighter flash
                            // on a slow rhythm, so the boss doesn't just sit
                            // there but visibly pulses with power over time.
                            val surgePhase = simState.tickIndex % 60
                            if (surgePhase < 6) {
                                val surgeStrength = 1f - (surgePhase / 6f)
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.25f * surgeStrength),
                                    radius = bossDisplayHeight * 0.65f,
                                    center = Offset(bossX, bossYPx)
                                )
                            }
                            // Orbiting energy motes — larger, slower, more
                            // menacing than the Blink Strike sparks.
                            val bossOrbitAngle = simState.tickIndex * 0.05f
                            for (i in 0 until 3) {
                                val angle = bossOrbitAngle + (i * (2f * kotlin.math.PI.toFloat() / 3f))
                                val orbitRadius = bossDisplayHeight * 0.6f
                                val moteX = bossX + kotlin.math.cos(angle) * orbitRadius * 0.5f
                                val moteY = bossYPx + kotlin.math.sin(angle) * orbitRadius
                                drawCircle(
                                    color = bossColor.copy(alpha = 0.5f * bossFlicker),
                                    radius = bossDisplayHeight * 0.025f,
                                    center = Offset(moteX, moteY)
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.6f * bossFlicker),
                                    radius = bossDisplayHeight * 0.01f,
                                    center = Offset(moteX, moteY)
                                )
                            }
                            drawImage(
                                image = bossImg,
                                dstOffset = IntOffset(
                                    (bossX - bossDisplayWidth / 2f).roundToInt(),
                                    (bossYPx - bossDisplayHeight / 2f).roundToInt()
                                ),
                                dstSize = IntSize(bossDisplayWidth.roundToInt(), bossDisplayHeight.roundToInt()),
                                alpha = bossFlicker
                            )

                            // Boss health bar, top-center of the screen —
                            // color shifts green->amber->red as it depletes,
                            // with a glowing border for a more premium feel.
                            val barWidth = cw * 0.6f
                            val barHeight = ch * 0.022f
                            val barLeft = (cw - barWidth) / 2f
                            val barTop = ch * 0.1f
                            val healthFrac = simState.bossHealth.coerceIn(0f, 1f)
                            val healthColor = when {
                                healthFrac > 0.5f -> Color(0xFF4CAF50)
                                healthFrac > 0.25f -> Color(0xFFFFC107)
                                else -> Color(0xFFFF3355)
                            }
                            drawRect(
                                color = healthColor.copy(alpha = 0.25f),
                                topLeft = Offset(barLeft - 2.dp.toPx(), barTop - 2.dp.toPx()),
                                size = Size(barWidth + 4.dp.toPx(), barHeight + 4.dp.toPx())
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.5f),
                                topLeft = Offset(barLeft, barTop),
                                size = Size(barWidth, barHeight)
                            )
                            drawRect(
                                color = healthColor,
                                topLeft = Offset(barLeft, barTop),
                                size = Size(barWidth * healthFrac, barHeight)
                            )
                            drawRect(
                                color = Color.White.copy(alpha = 0.6f),
                                topLeft = Offset(barLeft, barTop),
                                size = Size(barWidth, barHeight),
                                style = Stroke(1.dp.toPx())
                            )
                            val bossLabel = textMeasurer.measure(
                                "BOSS",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            drawText(
                                textLayoutResult = bossLabel,
                                topLeft = Offset(cw / 2f - bossLabel.size.width / 2f, barTop - bossLabel.size.height - 4.dp.toPx())
                            )
                        }

                        if (simState.activePowerupDurations.containsKey("PU1")) {
                            drawShieldAura(shieldLevel, userX, userY, 26.dp.toPx(), simState.tickIndex)
                        }
                        if (simState.activePowerupDurations.containsKey("PU7")) {
                            drawCircle(
                                color = Color(0xFFF72585),
                                radius = 28.dp.toPx(),
                                center = Offset(userX, userY),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        val frameIdx = (simState.tickIndex / 3) % pilotFrames.size
                        val currentFrameImg = pilotFrames[frameIdx]

                        val displayHeight = ch * 0.23f
                        val aspect = currentFrameImg.width.toFloat() / currentFrameImg.height.toFloat()
                        val displayWidth = displayHeight * aspect

                        // Equipped ship hull: thruster trail + aura behind the pilot.
                        drawHullEffect(profile.activeSkinId, userX, userY, displayHeight, simState.tickIndex, afterburnerLevel)

                        rotate(degrees = tiltAngle, pivot = Offset(userX, userY)) {
                            // Equipped pilot suit: colour grade + aura/particles on the shared frames.
                            drawPilotWithSuit(profile.activePilotSkinId, currentFrameImg, userX, userY, displayWidth, displayHeight, simState.tickIndex)
                        }

                        for ((scoreDelta, spawnTick, spawnY) in scorePopups) {
                            val popupAge = simState.tickIndex - spawnTick
                            val popupAlpha = 1f - (popupAge / 25f)
                            val popupY = ch * (spawnY / 100f) - (popupAge * 2f)
                            val textLayout = textMeasurer.measure(
                                "+$scoreDelta",
                                style = TextStyle(
                                    color = CyberPrimary.copy(alpha = popupAlpha),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(userX + 20f, popupY)
                            )
                        }

                        if (simState.tickIndex - flashStartTick < 5) {
                            drawRect(
                                color = Color.White.copy(alpha = 0.3f),
                                size = Size(cw, ch)
                            )
                        }
                    }
                }
                // Invisible onboarding: non-blocking contextual prompts.
                HintOverlay(hint = simState.hint, userYPos = simState.userYPos)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isPro && !profile.adsRemoved) {
                AdMobBannerView(
                    adUnitId = AdMobManager.BANNER_AD_UNIT_ID,
                    modifier = Modifier.fillMaxWidth()
                )
            }

                        Spacer(modifier = Modifier.height(8.dp))

            Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(48.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    Button(
        onClick = { viewModel.resetSimulation() },
        colors = ButtonDefaults.buttonColors(containerColor = CyberSurface.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .weight(0.3f)
            .fillMaxHeight()
            .border(1.dp, CyberPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
    ) {
        Text(
            "QUIT",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 6f))
        )
    }
    
    FuelBar(
    fuelPercent = simState.fuelLevelPercent,
    refillCount = simState.fuelRefillCount,
    isPro = isPro,
    cost = viewModel.fuelRefillCostForCurrentRun(),
    onRefuel = { viewModel.refuelWithGems(isPro) },
    onFuelTierChanged = { tier -> viewModel.onFuelTierChanged(tier) },
    highlight = simState.hint.lesson == "LOWFUEL",
    modifier = Modifier
        .weight(0.7f)
        .fillMaxHeight()
)
}
}
}
}
 @Composable
fun GameOverOverlayScreen(
    simState: SimulationState,
    viewModel: NeonRushViewModel,
    isPro: Boolean,
    profile: GameProfile,
    onShowPaywall: () -> Unit
) {
    val activity = LocalContext.current as? Activity
    val revivesExhausted = if (isPro) false else simState.reviveCount >= 3
    var showSummary by remember(simState.reviveCount) { mutableStateOf(revivesExhausted) }
    var secondsLeft by remember(simState.reviveCount) { mutableStateOf(5) }

    LaunchedEffect(simState.reviveCount, revivesExhausted) {
        if (!revivesExhausted) {
            secondsLeft = 5
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
            showSummary = true
        }
    }

    LaunchedEffect(showSummary) {
        if (showSummary) {
            viewModel.finalizeRunStats()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!showSummary) {
            // ---------- SCREEN 1: Crash / Revive ----------
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "SYSTEM FAILURE",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = CyberPrimary,
                    fontFamily = FontFamily.Monospace
                )

                if (simState.firstFlightReward) {
                    FirstFlightBanner()
                }

                Text(
                    text = "SCORE: ${simState.score}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )

                Box(
    modifier = Modifier.size(56.dp),
    contentAlignment = Alignment.Center
) {
    CircularProgressIndicator(
        progress = secondsLeft / 5f,
        modifier = Modifier.fillMaxSize(),
        color = CyberPrimary,
        trackColor = CyberSurface,
        strokeWidth = 4.dp
    )
    Text(
        text = "$secondsLeft",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = CyberPrimary,
        fontFamily = FontFamily.Monospace
    )
}

                if (!isPro) {
    Button(
        onClick = {
            activity?.let {
                AdMobManager.showRewardedIfReady(it, RewardedSlot.REVIVE) {
                    viewModel.reviveSimulation()
                    viewModel.recordAdWatched()
                }
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = adButtonLabel(RewardedSlot.REVIVE, "🎬 WATCH AD TO REVIVE"),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

                Button(
                    onClick = { viewModel.reviveWithGems(isPro) },
                    enabled = profile.gems >= viewModel.reviveCostForCurrentRun(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💎 REVIVE FOR ${viewModel.reviveCostForCurrentRun()} GEMS",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { showSummary = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "QUIT",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // ---------- SCREEN 2: Final Summary ----------
            // Scrollable: with the run goals, share button, ads and Pro card this
            // screen is taller than a phone. The bottom padding keeps the last
            // button (BACK TO MENU) clear of the bottom navigation bar.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 110.dp)
            ) {
                Text(
                    text = "GAME OVER",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = CyberPrimary,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "FINAL SCORE: ${simState.score}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "DISTANCE: ${simState.distanceMeters.toInt()}m",
                    fontSize = 14.sp,
                    color = CyberSecondary,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "ZONE REACHED: ${simState.currentZoneName}",
                    fontSize = 14.sp,
                    color = CyberSecondary,
                    fontFamily = FontFamily.Monospace
                )
                RunGoalsSummary(simState, profile)
                if (simState.dailyBonusLabel.isNotEmpty()) {
                    Text(
                        text = simState.dailyBonusLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD23F),
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (profile.fuelTiersOwned < 5) {
                    Text(
                        text = "⛽ Out of fuel again? Upgrade your Fuel Tank for longer runs — permanent!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberPrimary,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }

                // One-tap share card (image + watermark + QR). Highlighted on a new PB.
                val shareContext = LocalContext.current
                val isNewPB = simState.score > 0 && simState.score >= profile.bestScore
                Button(
                    onClick = {
                        ShareCard.share(
                            shareContext,
                            ShareCardData(
                                pilotName = profile.username,
                                score = simState.score,
                                distanceMeters = simState.distanceMeters.toInt(),
                                zoneName = simState.currentZoneName,
                                isNewPersonalBest = isNewPB,
                                isPro = isPro,
                                storeUrl = PLAY_STORE_URL,
                                masteryLevel = RunGoals.level(profile.masteryPoints)
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNewPB) Color(0xFFFFD23F) else CyberSecondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isNewPB) "📸 SHARE NEW BEST" else "📸 SHARE SCORE",
                        color = if (isNewPB) Color.Black else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isPro) {
    Button(
        onClick = {
            activity?.let {
                AdMobManager.showRewardedIfReady(it, RewardedSlot.DOUBLE_GEMS) {
                    viewModel.doubleGemsForRun()
                    viewModel.recordAdWatched()
                }
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
       Text(
            text = adButtonLabel(RewardedSlot.DOUBLE_GEMS, "🎬 DOUBLE GEMS (${profile.currentRunGemsCredited} 💎)"),
            color = Color.Black,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
            if (!isPro) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyberPrimary, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⚡ GO PRO",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Remove ads, unlock Legendary difficulty, and access all Worlds!",
                                fontSize = 12.sp,
                                color = CyberOnSurface.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                            // Free-tier teaser: name the rift world they just flew through
                            // and what Pro would have added to it.
                            val endedVariant = remember(simState.currentZoneNumber) {
                                WorldVariants.variantFor(
                                    viewModel.currentWorld.value,
                                    simState.currentZoneNumber,
                                    isPro = false
                                )
                            }
                            if (endedVariant?.isRift == true) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "🌌 You flew through ${endedVariant.suffix}. Pro adds weather effects, all 5 worlds and 8 looks.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD23F),
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onShowPaywall,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "UPGRADE TO PRO",
                                    color = CyberBackground,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (!isPro && !profile.adsRemoved && AdMobManager.isInterstitialDue()) {
                            activity?.let {
                                AdMobManager.showInterstitialIfReady(it) {
                                    viewModel.resetSimulation()
                                }
                            }
                        } else {
                            viewModel.resetSimulation()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "BACK TO MENU",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    }



@Composable
fun ProfileTab(profile: GameProfile, viewModel: NeonRushViewModel) {
    var showTutorial by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderProfileDeck(profile = profile, viewModel = viewModel)

        Card(
            colors = CardDefaults.cardColors(containerColor = CyberPrimary.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .clickable { showTutorial = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "❓", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HOW TO PLAY",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Controls, power-ups, hazards, worlds & more",
                        fontSize = 11.sp,
                        color = CyberOnSurface.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = CyberPrimary)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📊 STATISTICS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberPrimary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                StatRow("Total Runs", "${profile.totalRuns}")
                StatRow("Average Score", "${profile.averageScore}")
                StatRow("Total Gems Earned", "${profile.totalGemsEarned}")
                StatRow("Favorite Skin", profile.activeSkinId.replace("_", " ").capitalize())
                StatRow("Transcendence Level", "${profile.transcendenceCount}")
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberSecondary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "⚙️ SETTINGS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberSecondary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                var soundEnabled by remember { mutableStateOf(viewModel.getSoundEffectsEnabled()) }
                var musicEnabled by remember { mutableStateOf(viewModel.getAmbientEnabled()) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sound Effects", color = Color.White, fontFamily = FontFamily.Monospace)
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            viewModel.setSoundEffectsEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberPrimary,
                            checkedTrackColor = CyberPrimary.copy(alpha = 0.5f)
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Music", color = Color.White, fontFamily = FontFamily.Monospace)
                    Switch(
                        checked = musicEnabled,
                        onCheckedChange = {
                            musicEnabled = it
                            viewModel.setAmbientEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberPrimary,
                            checkedTrackColor = CyberPrimary.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        Button(
            onClick = { RevenueCatManager.restorePurchases {} },
            colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
        ) {
            Text(
                text = "RESTORE PURCHASES",
                color = CyberPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
    if (showTutorial) {
        TutorialScreen(onBack = { showTutorial = false })
    }
    }
}

@Composable
private fun TutorialCard(
    icon: String,
    title: String,
    accent: Color = CyberPrimary,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Text(text = icon, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    fontFamily = FontFamily.Monospace
                )
            }
            content()
        }
    }
}

@Composable
private fun TutorialBullet(text: String) {
    Row(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            text = "• ",
            color = CyberPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            text = text,
            color = CyberOnSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

// One row = a real in-game picture + name + what it does, so players learn
// to recognise the actual sprites instead of guessing from emoji.
@Composable
private fun TutorialIconRow(
    image: ImageBitmap,
    name: String,
    description: String,
    accent: Color = CyberPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = image,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                text = description,
                color = CyberOnSurface.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

// Horizontal strip of labelled thumbnails (Blink Strike creatures, bosses).
@Composable
private fun TutorialThumbStrip(
    items: List<Pair<ImageBitmap, String>>,
    accent: Color = CyberPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { (img, label) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(78.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = img,
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = CyberOnSurface,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun TutorialScreen(onBack: () -> Unit) {
    // Real in-game sprites, so what players see here matches what they'll see mid-run.
    val pilotImg = ImageBitmap.imageResource(id = R.drawable.pilot_run_1)
    val ghostImg = ImageBitmap.imageResource(id = R.drawable.marker_ghost_rival)
    val gemPic = ImageBitmap.imageResource(id = R.drawable.gem)
    val coinPic = ImageBitmap.imageResource(id = R.drawable.coin)

    val puShield = ImageBitmap.imageResource(id = R.drawable.powerup_shield)
    val puMagnet = ImageBitmap.imageResource(id = R.drawable.powerup_magnet)
    val puSlow = ImageBitmap.imageResource(id = R.drawable.powerup_time_slow)
    val puGhost = ImageBitmap.imageResource(id = R.drawable.powerup_ghost)
    val puX2 = ImageBitmap.imageResource(id = R.drawable.powerup_score_x2)
    val puX5 = ImageBitmap.imageResource(id = R.drawable.powerup_score_x5)
    val puInvinc = ImageBitmap.imageResource(id = R.drawable.powerup_invincibility)
    val puBoom = ImageBitmap.imageResource(id = R.drawable.powerup_boom_clear)
    val puShrink = ImageBitmap.imageResource(id = R.drawable.powerup_shrink)
    val puWarp = ImageBitmap.imageResource(id = R.drawable.powerup_lane_warp)
    val puSkip = ImageBitmap.imageResource(id = R.drawable.powerup_zone_skip)
    val puAura = ImageBitmap.imageResource(id = R.drawable.powerup_legendary_aura)

    val spikes = ImageBitmap.imageResource(id = R.drawable.spikes)
    val laser = ImageBitmap.imageResource(id = R.drawable.laser1)
    val saw = ImageBitmap.imageResource(id = R.drawable.sawblade)
    val barrier = ImageBitmap.imageResource(id = R.drawable.obstacle_barrier)
    val zap = ImageBitmap.imageResource(id = R.drawable.obstacle_zap_field)
    val phantom = ImageBitmap.imageResource(id = R.drawable.obstacle_phantom)
    val splitter = ImageBitmap.imageResource(id = R.drawable.obstacle_splitter_v2)
    val tunnel = ImageBitmap.imageResource(id = R.drawable.obstacle_tunnel_top)
    val standard = ImageBitmap.imageResource(id = R.drawable.obstacle_standard)
    val drone = ImageBitmap.imageResource(id = R.drawable.drone)
    val bossBullet = ImageBitmap.imageResource(id = R.drawable.hazard_bullet_boss)

    val blinkWolf = ImageBitmap.imageResource(id = R.drawable.hazard_blink_wolf)
    val blinkRaptor = ImageBitmap.imageResource(id = R.drawable.hazard_blink_raptor)
    val blinkHornet = ImageBitmap.imageResource(id = R.drawable.hazard_blink_hornet)
    val blinkArachnid = ImageBitmap.imageResource(id = R.drawable.hazard_blink_arachnid)
    val blinkWraith = ImageBitmap.imageResource(id = R.drawable.hazard_blink_wraith)

    val boss1 = ImageBitmap.imageResource(id = R.drawable.boss_blackout_front)
    val boss2 = ImageBitmap.imageResource(id = R.drawable.boss_derelict_signal)
    val boss3 = ImageBitmap.imageResource(id = R.drawable.boss_cell_block_zero)
    val boss4 = ImageBitmap.imageResource(id = R.drawable.boss_green_hell)
    val boss5 = ImageBitmap.imageResource(id = R.drawable.boss_red_protocol)
    val boss6 = ImageBitmap.imageResource(id = R.drawable.boss_signal_fracture)
    val boss7 = ImageBitmap.imageResource(id = R.drawable.boss_frozen_veil)
    val boss8 = ImageBitmap.imageResource(id = R.drawable.boss_apex_signal)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = CyberPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HOW TO PLAY",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Everything you need to fly like a pro",
                        fontSize = 10.sp,
                        color = CyberOnSurface.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(CyberPrimary.copy(alpha = 0.28f), CyberSecondary.copy(alpha = 0.18f))
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .border(1.dp, CyberPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = pilotImg,
                        contentDescription = "Pilot",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "FLY. DODGE. MASTER THE LINE.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Quick start: 1) Drag to steer  2) Stay on the ghost marker  3) Tap the fuel bar when it runs low",
                            color = CyberOnSurface.copy(alpha = 0.85f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                TutorialCard("🎮", "THE BASICS") {
                    TutorialBullet("Drag up or down anywhere on the screen to steer. Your pilot flies forward on its own.")
                    TutorialBullet("Survive as long as you can — distance, zones cleared and score all grow the further you fly.")
                    TutorialIconRow(
                        ghostImg, "Ghost marker",
                        "This glowing marker is your guide line. The closer you stay to it, the more points you earn every moment."
                    )
                    TutorialIconRow(
                        gemPic, "Gems",
                        "Fly through gems to collect them. Gems are your main currency."
                    )
                    TutorialIconRow(
                        coinPic, "Fuel canister",
                        "Spinning fuel canisters top your tank up for free — grab them whenever you can."
                    )
                }

                TutorialCard("⛽", "FUEL & REFUELING", accent = Color(0xFFFF9800)) {
                    TutorialBullet("The fuel bar at the bottom of the screen shows your tank. It drains as you fly.")
                    TutorialBullet("TAP THE FUEL BAR to refuel. It is a button: each tap spends gems and refills your tank.")
                    TutorialBullet("The bar text shows your fuel %, the gem cost (TAP: 20💎) and how many refuels you have left.")
                    TutorialBullet("The cost goes up with each refuel in the same life. Free players get 6 refuels per life — every revive gives you a fresh set of 6.")
                    TutorialBullet("Bar color = urgency: cyan is fine, orange is getting low, flashing red means refuel now!")
                    TutorialBullet("If your fuel hits 0, your run ends and you're offered a revive.")
                }

                TutorialCard("🛢️", "FUEL TANK UPGRADES", accent = Color(0xFFFFC107)) {
                    TutorialBullet("Permanent upgrades that make every tank last longer — for every run, forever.")
                    TutorialBullet("There are 5 tiers. Each tier adds +20% fuel longevity, up to +100% at tier 5.")
                    TutorialBullet("Tiers are bought in order: you need tier 1 before tier 2, and so on.")
                    TutorialBullet("Find them in the Skins tab, under \"FUEL TANK UPGRADES\".")
                    TutorialBullet("Once you own one, every run starts with a \"CURRENT LONGEVITY\" reminder.")
                }

                TutorialCard("💔", "REVIVES", accent = Color(0xFFFF5252)) {
                    TutorialBullet("When your run ends you can revive with gems or by watching a short ad and keep flying from where you fell.")
                    TutorialBullet("Free players get up to 3 revives per run. After that it's game over and your run is saved to your profile.")
                }

                TutorialCard("⚡", "POWER-UPS", accent = Color(0xFF69F0AE)) {
                    TutorialBullet("Fly through a power-up icon to activate it. Most last a few seconds.")
                    TutorialIconRow(puShield, "Shield", "Blocks one hit, then breaks.", Color(0xFF69F0AE))
                    TutorialIconRow(puMagnet, "Magnet", "Pulls nearby gems, fuel and power-ups toward you.", Color(0xFF69F0AE))
                    TutorialIconRow(puSlow, "Time Slow", "Slows the track down so you have more time to react.", Color(0xFF69F0AE))
                    TutorialIconRow(puGhost, "Ghost Mode", "Fly straight through obstacles for a short time.", Color(0xFF69F0AE))
                    TutorialIconRow(puX2, "Score x2", "Doubles the points you earn for a while.", Color(0xFF69F0AE))
                    TutorialIconRow(puX5, "Score x5", "Five times the points — chain it with a combo!", Color(0xFF69F0AE))
                    TutorialIconRow(puInvinc, "Invincibility", "Nothing can hurt you... except a Blink Strike creature while it is visible.", Color(0xFF69F0AE))
                    TutorialIconRow(puBoom, "Boom Clear", "Instantly wipes out the obstacles and shots directly ahead of you.", Color(0xFF69F0AE))
                    TutorialIconRow(puShrink, "Shrink", "Shrinks your hitbox so you can slip through tighter gaps.", Color(0xFF69F0AE))
                    TutorialIconRow(puWarp, "Lane Warp", "Instantly snaps you onto the ghost marker's safe line.", Color(0xFF69F0AE))
                    TutorialIconRow(puSkip, "Zone Skip", "Leaps you forward to the next zone.", Color(0xFF69F0AE))
                    TutorialIconRow(puAura, "Legendary Aura", "Rare! Grants a whole bundle of power-ups at the same time.", Color(0xFF69F0AE))
                }

                TutorialCard("🚧", "OBSTACLES", accent = Color(0xFFFF7043)) {
                    TutorialBullet("Every zone mixes different obstacles. Any contact costs you fuel, so learn to recognise them:")
                    TutorialIconRow(spikes, "Spikes", "Jut out of the ceiling or floor — slip through the gap.", Color(0xFFFF7043))
                    TutorialIconRow(laser, "Laser", "A beam that cuts across your lane.", Color(0xFFFF7043))
                    TutorialIconRow(saw, "Sawblade", "Spinning blade — keep your distance.", Color(0xFFFF7043))
                    TutorialIconRow(barrier, "Barrier", "Comes in pairs — thread the gap between them.", Color(0xFFFF7043))
                    TutorialIconRow(zap, "Zap Field", "An electric field that shocks on contact.", Color(0xFFFF7043))
                    TutorialIconRow(phantom, "Phantom", "A shadowy obstacle that hovers close to your guide line.", Color(0xFFFF7043))
                    TutorialIconRow(splitter, "Splitter", "Splits the lane in two — pick a side.", Color(0xFFFF7043))
                    TutorialIconRow(tunnel, "Tunnel", "Ceiling and floor close in, leaving one narrow corridor.", Color(0xFFFF7043))
                    TutorialIconRow(standard, "Block", "A basic obstacle straight in your path.", Color(0xFFFF7043))
                }

                TutorialCard("☠️", "SPECIAL THREATS", accent = Color(0xFFFF1744)) {
                    Text(
                        text = "BLINK STRIKE CREATURES",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    TutorialThumbStrip(
                        listOf(
                            blinkWolf to "Wolf",
                            blinkRaptor to "Raptor",
                            blinkHornet to "Hornet",
                            blinkArachnid to "Arachnid",
                            blinkWraith to "Wraith"
                        ),
                        accent = Color(0xFFFF1744)
                    )
                    TutorialBullet("They flicker: visible for about 1 second, then a faint ghost for about 2 seconds.")
                    TutorialBullet("They only hurt you while VISIBLE. While faded they are harmless — time your pass!")
                    TutorialBullet("Shields, Ghost Mode and Invincibility do NOT stop a visible Blink Strike creature. Dodge it.")
                    Spacer(modifier = Modifier.height(6.dp))
                    TutorialIconRow(
                        drone, "Drone",
                        "Hovers and slowly homes in on your lane, and sometimes fires a fast energy shot. A red ring around it means it's locked on.",
                        Color(0xFFFF1744)
                    )
                    TutorialIconRow(
                        bossBullet, "Boss shot",
                        "Bosses fire these at you — they glow red and leave a trail. Keep moving.",
                        Color(0xFFFF1744)
                    )
                }

                TutorialCard("👹", "BOSSES", accent = Color(0xFFE040FB)) {
                    TutorialBullet("A boss appears around every 5th zone, with its health bar across the top of the screen.")
                    TutorialBullet("Every world has its own boss. Beat it for bonus rewards — and it counts toward finishing that world.")
                    TutorialThumbStrip(
                        listOf(
                            boss1 to "Blackout Front",
                            boss2 to "Derelict Signal",
                            boss3 to "Cell Block Zero",
                            boss4 to "Green Hell",
                            boss5 to "Red Protocol",
                            boss6 to "Signal Fracture",
                            boss7 to "Frozen Veil",
                            boss8 to "Apex Signal"
                        ),
                        accent = Color(0xFFE040FB)
                    )
                }

                TutorialCard("🔥", "COMBO & MASTERY") {
                    TutorialBullet("Stay tight on the ghost marker and your combo streak grows. Every streak point adds +0.5% to your score multiplier, up to +100%.")
                    TutorialBullet("Any hit resets your combo to zero — clean flying is what really drives big scores.")
                    TutorialBullet("Your best-ever streak is saved on your profile as a permanent skill record.")
                }

                TutorialCard("🚀", "SECTORS", accent = Color(0xFFFFD700)) {
                    TutorialBullet("Every 2 zones is a new sector: the obstacle mix, mechanics and environment all change, and a banner announces it.")
                    TutorialBullet("Every 4 zones the banner also pays out +2💎, and a WATCH AD button appears for a few seconds — tap it to double the bonus.")
                    TutorialBullet("Pro players still get the bonus, without the ad prompt.")
                }

                TutorialCard("📅", "SPECIAL DAYS") {
                    TutorialBullet("Wednesday — Precision Day: tight, accurate flying pays out extra points.")
                    TutorialBullet("Friday — Golden Day: your whole score is multiplied x3.")
                    TutorialBullet("Saturday — Boss Day: bosses can show up much more often.")
                }

                TutorialCard("🌍", "WORLDS & PROGRESSION") {
                    TutorialBullet("5 main worlds carry the story. Each one comes back later in tougher phases, with new story beats.")
                    TutorialBullet("Special Mission worlds unlock as you complete Daily, Weekly and Monthly missions.")
                    TutorialBullet("New Game+ worlds unlock after you beat the Apex Signal boss — the true endgame.")
                    TutorialBullet("The game also gets sharper the more you play: your experience raises the baseline speed and density a little every run.")
                }

                TutorialCard("🎯", "MISSIONS") {
                    TutorialBullet("Daily, Weekly and Monthly missions give gems and unlock Special Mission worlds. Check the Special tab.")
                }

                TutorialCard("👑", "PRO", accent = Color(0xFFFFD700)) {
                    TutorialBullet("Unlocks Worlds 4 and 5 and every world's later phases.")
                    TutorialBullet("No revive limit, no refuel limit, and no ad prompts.")
                    TutorialBullet("Everything you've earned stays yours either way.")
                }

                TutorialCard("💡", "PRO TIPS", accent = Color(0xFF69F0AE)) {
                    TutorialBullet("Look ahead, not at your pilot — react to what's coming.")
                    TutorialBullet("Refuel early in a calm stretch, not in the middle of a boss fight.")
                    TutorialBullet("Save Shield and Boom Clear for boss zones and dense sectors.")
                    TutorialBullet("Blink Strike creatures: wait for the fade, then go.")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = CyberOnSurface.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun PaywallDialog(onDismiss: () -> Unit, reason: String) {
    val activity = LocalContext.current as? Activity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "⚡ NEON RUSH PRO",
                color = CyberPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        },
        // All three buttons live inside the content as one vertical stack. Putting two
        // buttons in confirmButton and one in dismissButton made the dialog's button
        // row overlap (ANNUAL was drawn under MAYBE LATER).
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = when (reason) {
                        "world4" -> "World 4: Green Hell requires Pro subscription. Unlock all Worlds, remove ads, and get Legendary difficulty!"
                        else -> "Upgrade to Pro to unlock all features: remove ads, access all Worlds, Legendary difficulty, and exclusive skins!"
                    },
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Monthly: ${RevenueCatManager.SUBSCRIPTION_PRICE_MONTHLY_USD}",
                    color = CyberSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Annual: ${RevenueCatManager.SUBSCRIPTION_PRICE_ANNUAL_USD} (Save 50%)",
                    color = CyberSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        activity?.let {
                            RevenueCatManager.purchaseProSubscription(it) { success ->
                                if (success) onDismiss()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("SUBSCRIBE MONTHLY", color = CyberBackground, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        activity?.let {
                            RevenueCatManager.purchaseProSubscriptionAnnual(it) { success ->
                                if (success) onDismiss()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("SUBSCRIBE ANNUAL", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1)
                }
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("MAYBE LATER", color = CyberOnSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {},
        containerColor = CyberSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun NeonPilotScreensaver() {
    val infiniteTransition = rememberInfiniteTransition(label = "screensaver")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )
    // Load the same running-pilot animation frames used in the real game
val pf1 = ImageBitmap.imageResource(id = R.drawable.pilot_run_1)
val pf2 = ImageBitmap.imageResource(id = R.drawable.pilot_run_2)
val pf3 = ImageBitmap.imageResource(id = R.drawable.pilot_run_3)
val pf4 = ImageBitmap.imageResource(id = R.drawable.pilot_run_4)
val pf5 = ImageBitmap.imageResource(id = R.drawable.pilot_run_5)
val pf6 = ImageBitmap.imageResource(id = R.drawable.pilot_run_6)
val pilotFrames = remember(pf1, pf2, pf3, pf4, pf5, pf6) {
    listOf(pf1, pf2, pf3, pf4, pf5, pf6)
}

// Drives the run-cycle animation, independent of offsetX/offsetY drifting
var frameTick by remember { mutableStateOf(0) }
LaunchedEffect(Unit) {
    while (true) {
        delay(80)
        frameTick++
    }
}
val frameIdx = frameTick % pilotFrames.size
val currentFrameImg = pilotFrames[frameIdx]
   // Continuously scrolling background — sells the sense of forward motion
val scrollX by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1000f,
    animationSpec = infiniteRepeatable(
        animation = tween(6000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    ),
    label = "scrollX"
) 


    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cw = size.width
            val ch = size.height

           // Draw scrolling stars, two layers for a parallax depth effect
for (i in 0..50) {
    val baseX = (i * 73 % 100) / 100f * cw
    val y = (i * 37 % 100) / 100f * ch
    val alpha = 0.3f + 0.7f * ((i * 13 % 10) / 10f)
    val speed = 0.4f + (i % 3) * 0.3f // varying speeds = parallax depth
    val x = (baseX - scrollX * speed).mod(cw)
    drawCircle(
        color = Color.White.copy(alpha = alpha),
        radius = (1f + (i % 3)).dp.toPx(),
        center = Offset(x, y)
    )
}

// Scrolling speed-lines to sell velocity
for (i in 0..8) {
    val baseX = (i * 137 % 100) / 100f * cw
    val y = ch * 0.15f + (i * 91 % 100) / 100f * ch * 0.7f
    val lineSpeed = 1.6f
    val x = (baseX - scrollX * lineSpeed).mod(cw)
    drawLine(
        color = CyberPrimary.copy(alpha = 0.25f),
        start = Offset(x, y),
        end = Offset(x + 24f, y),
        strokeWidth = 2.dp.toPx()
    )
} 

            // Draw pilot silhouette
            val pilotX = cw / 2f + offsetX
            val pilotY = ch / 2f + offsetY

            drawCircle(
                color = CyberPrimary.copy(alpha = 0.3f),
                radius = 40.dp.toPx(),
                center = Offset(pilotX, pilotY)
            )

            // Draw trail
            val trailPath = Path().apply {
                moveTo(pilotX - 60, pilotY)
                for (i in 1..5) {
                    val tx = pilotX - 60 - i * 30
                    val ty = pilotY + kotlin.math.sin(i * 0.5f) * 10
                    lineTo(tx, ty)
                }
            }
            drawPath(
                path = trailPath,
                color = CyberPrimary.copy(alpha = 0.5f),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        val displayHeight = 90.dp
        val aspect = currentFrameImg.width.toFloat() / currentFrameImg.height.toFloat()
        val displayWidth = displayHeight * aspect

        Image(
            bitmap = currentFrameImg,
            contentDescription = "Pilot running",
            modifier = Modifier
                .size(width = displayWidth, height = displayHeight)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        )
    }
}
