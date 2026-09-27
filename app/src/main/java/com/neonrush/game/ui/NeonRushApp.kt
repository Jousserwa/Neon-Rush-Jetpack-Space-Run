package com.neonrush.game.ui

import android.app.Activity
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
import com.neonrush.game.DailyMutations
import com.neonrush.game.MutationDay
import com.neonrush.game.NeonRushViewModel
import com.neonrush.game.RevenueCatManager
import com.neonrush.game.SimulationState
import com.neonrush.game.ZoneGenerator
import com.neonrush.game.StoryBannerHost
import com.neonrush.game.Skins
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
    // Worlds 4-5 keep their original single static background until they get
    // the same layered treatment — speedFactor 0f means "don't scroll".
    4 to listOf(BgLayer(R.drawable.bg_world4_green_hell, 0f)),
    5 to listOf(BgLayer(R.drawable.bg_world5_red_protocol, 0f))
    // Special-mode worlds 6 (Signal Fracture), 7 (Frozen Veil), and 8 (Apex
    // Signal) have no entry yet — ParallaxWorldBackground falls back to a
    // themed color gradient for any world id missing here. Add a 6/7/8 entry
    // (single image or a 5-layer list, same as world 1) once their art exists.
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

@Composable
private fun ParallaxWorldBackground(worldId: Int, distanceMeters: Float, fallbackColorHex: String) {
    val layers = worldBackgroundLayers[worldId]
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

        layers.forEachIndexed { idx, layer ->
            val bmp = bitmaps[idx]
            // Scale each layer to fill the canvas height, preserving aspect.
            val displayWidth = ch * (bmp.width.toFloat() / bmp.height.toFloat())

            if (layer.speedFactor == 0f) {
                // Static layer: single centered/cropped draw, no scrolling.
                drawImage(
                    image = bmp,
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
                            dstOffset = IntOffset((-cyclePos).roundToInt(), 0),
                            dstSize = IntSize(displayWidth.roundToInt(), ch.roundToInt())
                        )
                    } else {
                        val progress = (cyclePos - panRange) / wrapWindow
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset((-panRange).roundToInt(), 0),
                            dstSize = IntSize(displayWidth.roundToInt(), ch.roundToInt()),
                            alpha = 1f - progress
                        )
                        drawImage(
                            image = bmp,
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
    var showPaywall by remember { mutableStateOf(false) }
    var paywallReason by remember { mutableStateOf("generic") }
    
    var showStreakFreezeOffer by remember { mutableStateOf(false) }
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
            delay(4000)
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
                    if (!isPro) {
                        AdMobBannerView(
                            adUnitId = "ca-app-pub-3841327492203214/6533049489",
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
                            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Arcade") },
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
                                    GhostRacerTab(viewModel = viewModel, onBack = { showGhostSelection = false })
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
                    }
                }
            }   
        }
    }

    if (showPaywall) {
        PaywallDialog(onDismiss = { showPaywall = false }, reason = paywallReason)
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
fun GhostRacerTab(viewModel: NeonRushViewModel, onBack: () -> Unit) {
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
                viewModel.startRacingSimulation(selectedGhost)
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
    Column(
    modifier = Modifier.fillMaxSize(),
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
            Text(
                text = "💎 ${profile.gems} GEMS",
                color = CyberPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
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

        if (profile.checkpointsReachedCsv.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "🏁 CHECKPOINTS",
                color = CyberSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val reachedZones = profile.checkpointsReachedCsv.split(",").filter { it.isNotEmpty() }.map { it.toInt() }.sorted()
                val activatedZones = profile.checkpointsActivatedCsv.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
                for (cp in reachedZones) {
                    val isActivated = cp in activatedZones
                    val cost = cp * 3
                    Button(
                        onClick = { viewModel.startFromCheckpoint(cp) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isActivated) CyberPrimary else CyberSurface),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, CyberPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = if (isActivated) "Zone $cp ✓" else "Zone $cp (${cost}💎)",
                            color = if (isActivated) Color.Black else Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

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
        }
    }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (id) {
                                "cyan_diamond" -> "💠"
                                "purple_square" -> "🔮"
                                "green_triangle" -> "🔺"
                                "magenta_pulse" -> "⚡"
                                "gold_transcendence" -> "🏆"
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
        LaunchedEffect(currentWorld.id) {
            if (currentWorld.requiresPro && !isPro) {
                viewModel.triggerPaywallTeaser(currentWorld)
                delay(2500)
                onShowPaywall()
            }
        }
        ParallaxWorldBackground(
            worldId = currentWorld.id,
            distanceMeters = simState.distanceMeters,
            fallbackColorHex = simState.zoneDNA.environmentColor
        )
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
                visible = simState.tickIndex < simState.sectorBannerUntilTick,
                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(500)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
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
                                    AdMobManager.showRewardedIfReady(it) {
                                        viewModel.claimSectorAdBonus()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "🎬 WATCH AD: DOUBLE TO +${simState.sectorBonusPending * 2}💎",
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
        val baseSize = ch * 0.05f
        val w = baseSize * (bulletImg.width.toFloat() / bulletImg.height.toFloat())
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

                            val markerSize = ch * 0.05f
    drawImage(
        image = ghostMarkerImg,
        dstOffset = IntOffset((ghostX - markerSize / 2f).roundToInt(), (ghostY - markerSize / 2f).roundToInt()),
        dstSize = IntSize(markerSize.roundToInt(), markerSize.roundToInt())
    )
                                
                        }

                        val userY = ch * (simState.userYPos / 100f)

                        if (simState.bossActive) {
                            val bossImg = bossImagesByWorld[currentWorld.id] ?: bossImagesByWorld[1]!!
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
                            drawCircle(
                                color = Color(0xFF3A86FF),
                                radius = 26.dp.toPx(),
                                center = Offset(userX, userY),
                                style = Stroke(width = 2.dp.toPx())
                            )
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

                        rotate(degrees = tiltAngle, pivot = Offset(userX, userY)) {
                            drawImage(
                                image = currentFrameImg,
                                dstOffset = IntOffset((userX - displayWidth / 2f).roundToInt(), (userY - displayHeight / 2f).roundToInt()),
                                dstSize = IntSize(displayWidth.roundToInt(), displayHeight.roundToInt())
                            )
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isPro) {
                AdMobBannerView(
                    adUnitId = "ca-app-pub-3841327492203214/6533049489",
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
                AdMobManager.showRewardedIfReady(it) {
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
            text = "🎬 WATCH AD TO REVIVE",
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "GAME OVER",
                    fontSize = 32.sp,
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

                if (!isPro) {
    Button(
        onClick = {
            activity?.let {
                AdMobManager.showRewardedIfReady(it) {
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
            text = "🎬 DOUBLE GEMS (${profile.currentRunGemsCredited} 💎)",
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
                        if (!profile.adsRemoved && AdMobManager.isInterstitialDue()) {
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
private fun TutorialSection(icon: String, title: String, points: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$icon $title",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CyberPrimary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            points.forEach { point ->
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = "• ",
                        color = CyberPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Text(
                        text = point,
                        color = CyberOnSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TutorialScreen(onBack: () -> Unit) {
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
                Text(
                    text = "HOW TO PLAY",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberPrimary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TutorialSection("🎮", "THE BASICS", listOf(
                    "Drag up or down anywhere on screen to move — your pilot always flies forward on its own.",
                    "Survive as long as you can. Distance, zones cleared, and score all add up the further you fly.",
                    "The closer you stay to the glowing guide line, the more points you earn each moment — precision pays."
                ))
                TutorialSection("⛽", "FUEL", listOf(
                    "Your fuel bar drains as you fly. Run out and you'll need to refuel or revive to keep going.",
                    "Refuel with gems mid-run — the cost rises each time you refuel in the same life, up to 6 refuels per life.",
                    "Fly through floating fuel canisters to top up for free."
                ))
                TutorialSection("💔", "REVIVES", listOf(
                    "When you're hit with no fuel left, revive with gems or by watching a short ad to keep your run going.",
                    "You get up to 3 revives per run — after that, it's game over and your run is banked to your profile."
                ))
                TutorialSection("💎", "GEMS", listOf(
                    "Gems are the main currency — collect them mid-run, and earn more from sector bonuses, missions, and daily rewards.",
                    "Spend gems on refuels, revives, and pilot skins in the Skins shop."
                ))
                TutorialSection("⚡", "POWER-UPS", listOf(
                    "🛡️ Shield — blocks one hit, then breaks.",
                    "🧲 Magnet — pulls nearby gems, fuel, and power-ups toward you.",
                    "🐌 Time Slow — slows the whole track down for a few seconds.",
                    "👻 Ghost Mode — fly straight through obstacles for a short time.",
                    "2️⃣ Score x2 / 5️⃣ Score x5 — temporary score multipliers.",
                    "✨ Invincibility — nothing can hurt you, except a Blink Strike creature while it's visible.",
                    "💥 Boom Clear — instantly wipes out obstacles in the path directly ahead of you.",
                    "🔻 Shrink — shrinks your hitbox, making you harder to hit.",
                    "↔️ Lane Warp — instantly snaps you onto the safest line.",
                    "⏩ Zone Skip — instantly jumps you ahead to the next zone.",
                    "👑 Legendary Aura — grants every other power-up at once for a short time."
                ))
                TutorialSection("🚧", "OBSTACLES & HAZARDS", listOf(
                    "Pillars, lasers, blades, stalactites, barriers, zap fields and more — each zone mixes different hazard types together.",
                    "⚡ Blink Strike creatures (wolf, raptor, hornet, arachnid, wraith) flicker between visible and invisible. They can only hurt you while VISIBLE — while invisible they're harmless, so time your pass through the invisible phase.",
                    "Blink Strike creatures ignore shields and invincibility — while visible, dodging is the only way past.",
                    "🛸 Drones hover and slowly home in on your lane, and sometimes fire a quick energy shot — a red targeting ring means one's locked onto you."
                ))
                TutorialSection("👹", "BOSSES", listOf(
                    "A boss appears roughly every 5th zone, with its health bar shown at the top of the screen while active.",
                    "Each world has its own unique boss. Defeating it grants bonus rewards and counts toward that world's completion."
                ))
                TutorialSection("🔥", "COMBO & MASTERY", listOf(
                    "Fly precisely along the guide line and your combo streak builds, boosting your score multiplier the longer you hold it.",
                    "Any hit resets your combo streak to zero — clean, precise flying is what really drives your score.",
                    "Your best-ever streak is saved to your profile as a permanent skill record, separate from gems."
                ))
                TutorialSection("🚀", "SECTORS", listOf(
                    "Every 2 zones is a new \"sector\" — obstacles, mechanics, and the environment are guaranteed to shift into something different.",
                    "Crossing into a new sector gives you a small gem bonus, with the option to watch an ad to double it."
                ))
                TutorialSection("📅", "SPECIAL DAYS", listOf(
                    "Wednesdays — precision scoring day: tight, accurate flying pays out noticeably more points than usual.",
                    "Fridays — Golden Day: your entire score is multiplied x3 for the whole run.",
                    "Saturdays — Boss Day: bosses can show up far more often than usual, on top of the normal every-5th-zone pattern."
                ))
                TutorialSection("🌍", "WORLDS & PROGRESSION", listOf(
                    "5 main worlds carry the core story, each one returning in escalating phases the further you fly.",
                    "Special Mission worlds unlock as you complete Daily, Weekly, and Monthly missions.",
                    "New Game+ worlds unlock after you've beaten the final main-story boss — the true endgame for players who've mastered the run."
                ))
                TutorialSection("🎯", "MISSIONS", listOf(
                    "Check the Special tab for Daily, Weekly, and Monthly missions — completing them earns gems and unlocks Special World access."
                ))
                TutorialSection("👑", "PRO", listOf(
                    "Pro unlocks Worlds 4 and 5, every world's later phases, ad-free play, and more.",
                    "Everything you've earned stays yours either way — Pro just opens up more of the map."
                ))
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
        text = {
            Column {
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
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(
                    onClick = {
                        activity?.let {
                            RevenueCatManager.purchaseProSubscription(it) { success ->
                                if (success) onDismiss()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary)
                ) {
                    Text("SUBSCRIBE MONTHLY", color = CyberBackground, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        activity?.let {
                            RevenueCatManager.purchaseProSubscriptionAnnual(it) { success ->
                                if (success) onDismiss()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary)
                ) {
                    Text("SUBSCRIBE ANNUAL", color = CyberBackground, fontFamily = FontFamily.Monospace)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("MAYBE LATER", color = CyberOnSurface, fontFamily = FontFamily.Monospace)
            }
        },
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
