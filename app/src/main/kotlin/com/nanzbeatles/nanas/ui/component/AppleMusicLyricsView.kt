/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.nanzbeatles.nanas.LocalDatabase
import com.nanzbeatles.nanas.LocalPlayerConnection
import com.nanzbeatles.nanas.R
import com.nanzbeatles.nanas.constants.LyricsClickKey
import com.nanzbeatles.nanas.constants.LyricsRomanizeBelarusianKey
import com.nanzbeatles.nanas.constants.LyricsRomanizeBulgarianKey
import com.nanzbeatles.nanas.constants.LyricsRomanizeChineseKey
import com.nanzbeatles.nanas.constants.LyricsRomanizeCyrillicByLineKey
import com.nanzbeatles.nanas.constants.LyricsRomanizeJapaneseKey
import com.nanzbeatles.nanas.constants.LyricsRomanizeKoreanKey
import com.nanzbeatles.nanas.constants.LyricsRomanizeKyrgyzKey
import com.nanzbeatles.nanas.constants.LyricsRomanizeMacedonianKey
import com.nanzbeatles.nanas.constants.LyricsRomanizeRussianKey
import com.nanzbeatles.nanas.constants.LyricsRomanizeSerbianKey
import com.nanzbeatles.nanas.constants.LyricsRomanizeUkrainianKey
import com.nanzbeatles.nanas.constants.ShowIntervalIndicatorKey
import com.nanzbeatles.nanas.db.entities.LyricsEntity
import com.nanzbeatles.nanas.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.nanzbeatles.nanas.lyrics.LyricsEntry
import com.nanzbeatles.nanas.lyrics.WordTimestamp
import com.nanzbeatles.nanas.lyrics.findActiveLineIndices
import com.nanzbeatles.nanas.lyrics.lyricsTextLooksSynced
import com.nanzbeatles.nanas.ui.theme.PlayerColorExtractor
import com.nanzbeatles.nanas.ui.utils.fadingEdge
import com.nanzbeatles.nanas.utils.rememberPreference
import com.nanzbeatles.nanas.viewmodels.LyricsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Apple Music Style Lyrics View.
 *
 * Features:
 * - Dynamic blurred atmospheric mesh gradient backdrop derived from album art palette
 * - Large, bold typography with high contrast
 * - Inactive lines dimmed, softened, and scaled down
 * - Active line scaled up with bouncy spring physics and crisp pure white
 * - Real-time word-by-word karaoke sweep gradient animation
 * - Instrumental break 3-dot pulsating countdown indicator
 * - Auto-scroll with viewport vertical center anchor (~36%)
 * - Interactive tap-to-seek and sleek "Sync to lyric" floating action button
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppleMusicLyricsView(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyrics: Boolean,
    disableInteractiveFeatures: Boolean = false,
    lyricsViewModel: LyricsViewModel = hiltViewModel()
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val changeLyrics by rememberPreference(LyricsClickKey, true)
    val showIntervalIndicator by rememberPreference(ShowIntervalIndicatorKey, true)

    val romanizeJa by rememberPreference(LyricsRomanizeJapaneseKey, true)
    val romanizeKo by rememberPreference(LyricsRomanizeKoreanKey, true)
    val romanizeZh by rememberPreference(LyricsRomanizeChineseKey, true)
    val romanizeRu by rememberPreference(LyricsRomanizeRussianKey, true)
    val romanizeUk by rememberPreference(LyricsRomanizeUkrainianKey, true)
    val romanizeSr by rememberPreference(LyricsRomanizeSerbianKey, true)
    val romanizeBg by rememberPreference(LyricsRomanizeBulgarianKey, true)
    val romanizeBe by rememberPreference(LyricsRomanizeBelarusianKey, true)
    val romanizeKy by rememberPreference(LyricsRomanizeKyrgyzKey, true)
    val romanizeMk by rememberPreference(LyricsRomanizeMacedonianKey, true)
    val romanizeCyrillicByLine by rememberPreference(LyricsRomanizeCyrillicByLineKey, false)

    val enabledLanguages = remember(
        romanizeJa, romanizeKo, romanizeZh, romanizeRu, romanizeUk, romanizeSr,
        romanizeBg, romanizeBe, romanizeKy, romanizeMk
    ) {
        buildList {
            if (romanizeJa) add("ja")
            if (romanizeKo) add("ko")
            if (romanizeZh) add("zh")
            if (romanizeRu) add("ru")
            if (romanizeUk) add("uk")
            if (romanizeSr) add("sr")
            if (romanizeBg) add("bg")
            if (romanizeBe) add("be")
            if (romanizeKy) add("ky")
            if (romanizeMk) add("mk")
        }
    }

    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentLyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val lyrics = remember(currentLyricsEntity) { currentLyricsEntity?.lyrics?.trim() }

    val lines by lyricsViewModel.lines.collectAsStateWithLifecycle()
    val mergedLyricsList by lyricsViewModel.mergedLyricsList.collectAsStateWithLifecycle()

    LaunchedEffect(lyrics, enabledLanguages, romanizeCyrillicByLine, showIntervalIndicator) {
        lyricsViewModel.processLyrics(lyrics, enabledLanguages, romanizeCyrillicByLine, showIntervalIndicator)
    }

    val isSynced = remember(lyrics) { lyricsTextLooksSynced(lyrics) }
    var currentPositionState by remember { mutableLongStateOf(0L) }
    var activeLineIndices by remember { mutableStateOf(emptySet<Int>()) }

    // 60fps smooth playback sync loop
    LaunchedEffect(lyrics, lines) {
        if (lyrics.isNullOrEmpty() || lines.isEmpty()) {
            activeLineIndices = emptySet()
            return@LaunchedEffect
        }

        var lastPlayerPos = playerConnection.player.currentPosition
        var lastUpdateTime = System.currentTimeMillis()

        while (isActive) {
            delay(16)
            val now = System.currentTimeMillis()
            val sliderPosition = sliderPositionProvider()

            val position = if (sliderPosition != null) {
                sliderPosition
            } else {
                val playerPos = playerConnection.player.currentPosition
                if (playerPos != lastPlayerPos) {
                    lastPlayerPos = playerPos
                    lastUpdateTime = now
                }
                val elapsed = now - lastUpdateTime
                lastPlayerPos + (if (playerConnection.player.isPlaying) elapsed else 0)
            }

            currentPositionState = position
            val lyricsOffset = currentSong?.song?.lyricsOffset ?: 0
            val effectivePosition = position + lyricsOffset

            val initialActiveIndices = findActiveLineIndices(lines, effectivePosition)
            activeLineIndices = initialActiveIndices
        }
    }

    val listState = rememberLazyListState()
    var isAutoScrollEnabled by rememberSaveable { mutableStateOf(true) }
    var lastUserScrollTime by rememberSaveable { mutableLongStateOf(0L) }

    // Detect manual user touch scroll
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isAutoScrollEnabled = false
            lastUserScrollTime = System.currentTimeMillis()
        }
    }

    // Auto-resume scrolling after 5 seconds of inactivity
    LaunchedEffect(isAutoScrollEnabled, lastUserScrollTime) {
        if (!isAutoScrollEnabled) {
            delay(5000L)
            isAutoScrollEnabled = true
        }
    }

    // Determine current active item index in mergedLyricsList
    val activeMergedIndex by remember(activeLineIndices, mergedLyricsList) {
        derivedStateOf {
            if (activeLineIndices.isEmpty()) -1
            else {
                val maxActiveLineIndex = activeLineIndices.maxOrNull() ?: -1
                mergedLyricsList.indexOfFirst {
                    it is LyricsListItem.Line && it.index == maxActiveLineIndex
                }
            }
        }
    }

    // Centered auto-scroll animation
    LaunchedEffect(activeMergedIndex, isAutoScrollEnabled) {
        if (isAutoScrollEnabled && activeMergedIndex >= 0) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val targetOffset = -(viewportHeight * 0.36f).roundToInt()
            listState.animateScrollToItem(
                index = activeMergedIndex,
                scrollOffset = targetOffset
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Dynamic blurred album art background
        AppleMusicAmbientBackground(
            thumbnailUrl = mediaMetadata?.thumbnailUrl,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient top & bottom edge fade
        val topFadeBrush = remember {
            Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
            )
        }
        val bottomFadeBrush = remember {
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
            )
        }

        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 140.dp, bottom = 220.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                itemsIndexed(
                    items = mergedLyricsList,
                    key = { _, item ->
                        when (item) {
                            is LyricsListItem.Line -> "line_${item.index}_${item.entry.time}"
                            is LyricsListItem.Indicator -> "indicator_${item.afterLineIndex}_${item.gapStartMs}"
                        }
                    }
                ) { _, listItem ->
                    when (listItem) {
                        is LyricsListItem.Indicator -> {
                            val isActive = currentPositionState in listItem.gapStartMs..listItem.gapEndMs
                            AppleMusicInstrumentalDots(
                                gapStartMs = listItem.gapStartMs,
                                gapEndMs = listItem.gapEndMs,
                                currentPosMs = currentPositionState,
                                isActive = isActive
                            )
                        }
                        is LyricsListItem.Line -> {
                            val lineIndex = listItem.index
                            val entry = listItem.entry
                            val isActiveLine = activeLineIndices.contains(lineIndex)

                            AppleMusicLyricLine(
                                entry = entry,
                                isActive = isActiveLine,
                                currentPositionMs = currentPositionState,
                                lyricsOffset = (currentSong?.song?.lyricsOffset ?: 0).toLong(),
                                disableInteractive = disableInteractiveFeatures || !changeLyrics,
                                onLineClick = {
                                    if (changeLyrics && !disableInteractiveFeatures) {
                                        val lyricsOffset = (currentSong?.song?.lyricsOffset ?: 0).toLong()
                                        playerConnection.seekTo((entry.time - lyricsOffset).coerceAtLeast(0L))
                                        isAutoScrollEnabled = true
                                    }
                                },
                                onLineLongClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Lyrics", entry.text))
                                    Toast.makeText(context, "Lirik disalin", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Top & bottom subtle fade gradients for smooth text entry/exit
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(topFadeBrush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
                .background(bottomFadeBrush)
        )

        // Sleek floating "Sync to lyric" pill button when user scrolled away
        AnimatedVisibility(
            visible = !isAutoScrollEnabled && activeMergedIndex >= 0,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) {
            ElevatedButton(
                onClick = {
                    isAutoScrollEnabled = true
                },
                shape = CircleShape,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = Color(0xCC2C2C2E),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.sync),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Lirik Saat Ini",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Ambient dynamic fluid animated mesh background inspired by Apple Music.
 */
@Composable
private fun AppleMusicAmbientBackground(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var gradientColors by remember {
        mutableStateOf(listOf(Color(0xFF2E1C4E), Color(0xFF1E3A5F), Color(0xFF0F172A)))
    }

    LaunchedEffect(thumbnailUrl) {
        if (!thumbnailUrl.isNullOrBlank()) {
            val request = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .size(120, 120)
                .allowHardware(false)
                .build()

            val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
            val bitmap = result?.image?.toBitmap()
            if (bitmap != null) {
                val palette = withContext(Dispatchers.Default) {
                    Palette.from(bitmap)
                        .maximumColorCount(10)
                        .resizeBitmapArea(100 * 100)
                        .generate()
                }
                val extracted = PlayerColorExtractor.extractGradientColors(
                    palette = palette,
                    fallbackColor = android.graphics.Color.DKGRAY
                )
                if (extracted.isNotEmpty()) {
                    gradientColors = extracted
                }
            }
        }
    }

    val c0 = gradientColors.getOrNull(0) ?: Color(0xFF2E1C4E)
    val c1 = gradientColors.getOrNull(1) ?: Color(0xFF1E3A5F)
    val c2 = gradientColors.getOrNull(2) ?: Color(0xFF0F172A)

    val transition = rememberInfiniteTransition(label = "appleMusicMeshTransition")

    val t1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t1"
    )
    val t2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t2"
    )
    val breath by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 70.dp else 40.dp)
        ) {
            val w = size.width
            val h = size.height
            val maxDim = max(w, h)
            val radius = maxDim * 0.85f * breath

            // Top-left primary orb
            val cx1 = w * (0.30f + 0.22f * cos(t1))
            val cy1 = h * (0.28f + 0.18f * sin(t1 * 0.8f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c0.copy(alpha = 0.90f), c0.copy(alpha = 0f)),
                    center = Offset(cx1, cy1),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx1, cy1)
            )

            // Top-right secondary orb
            val cx2 = w * (0.70f + 0.20f * sin(t2))
            val cy2 = h * (0.35f + 0.18f * cos(t2 * 0.9f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c1.copy(alpha = 0.85f), c1.copy(alpha = 0f)),
                    center = Offset(cx2, cy2),
                    radius = radius * 0.95f
                ),
                radius = radius * 0.95f,
                center = Offset(cx2, cy2)
            )

            // Bottom rich depth orb
            val cx3 = w * (0.50f + 0.25f * cos(t2 * 0.7f))
            val cy3 = h * (0.78f + 0.15f * sin(t1))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c2.copy(alpha = 0.80f), c2.copy(alpha = 0f)),
                    center = Offset(cx3, cy3),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx3, cy3)
            )
        }

        // Apple Music dark scrim overlay for crisp typography contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
        )
    }
}

/**
 * Single Apple Music Lyric Line with word sweep karaoke, spring physics, and focus scaling.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AppleMusicLyricLine(
    entry: LyricsEntry,
    isActive: Boolean,
    currentPositionMs: Long,
    lyricsOffset: Long,
    disableInteractive: Boolean,
    onLineClick: () -> Unit,
    onLineLongClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.06f else 0.94f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "appleMusicLineScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.38f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "appleMusicLineAlpha"
    )

    val romanizedText by entry.romanizedTextFlow.collectAsState()
    val translatedText by entry.translatedTextFlow.collectAsState()

    val effectiveTime = currentPositionMs + lyricsOffset
    val hasWords = !entry.words.isNullOrEmpty()

    val blurRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isActive) 0.dp else 0.8.dp
    } else 0.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .blur(blurRadius)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                enabled = !disableInteractive,
                onClick = onLineClick,
                onLongClick = onLineLongClick
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = if (entry.isBackground) Alignment.End else Alignment.Start
    ) {
        if (isActive && hasWords) {
            // Active word-by-word karaoke sweep!
            FlowRow(
                horizontalArrangement = if (entry.isBackground) Arrangement.End else Arrangement.Start,
                verticalArrangement = Arrangement.Center
            ) {
                entry.words!!.forEach { word ->
                    AppleMusicKaraokeWord(
                        word = word,
                        effectivePositionMs = effectiveTime,
                        isBackground = entry.isBackground
                    )
                }
            }
        } else {
            // Standard line highlight
            Text(
                text = entry.text,
                fontSize = if (entry.isBackground) 22.sp else 28.sp,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                color = Color.White.copy(alpha = alpha),
                lineHeight = if (entry.isBackground) 30.sp else 38.sp,
                style = TextStyle(
                    shadow = if (isActive) Shadow(
                        color = Color.Black.copy(alpha = 0.45f),
                        offset = Offset(0f, 2f),
                        blurRadius = 6f
                    ) else null
                ),
                textAlign = if (entry.isBackground) TextAlign.End else TextAlign.Start
            )
        }

        // Secondary text: Romanization
        if (!romanizedText.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = romanizedText!!,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = if (isActive) 0.80f else 0.28f),
                textAlign = if (entry.isBackground) TextAlign.End else TextAlign.Start
            )
        }

        // Secondary text: Translation
        if (!translatedText.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = translatedText!!,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = if (isActive) 0.70f else 0.25f),
                textAlign = if (entry.isBackground) TextAlign.End else TextAlign.Start
            )
        }
    }
}

/**
 * Individual word with real-time progressive horizontal gradient sweep.
 */
@Composable
private fun AppleMusicKaraokeWord(
    word: WordTimestamp,
    effectivePositionMs: Long,
    isBackground: Boolean
) {
    val wordStartMs = (word.startTime * 1000).toLong()
    val wordEndMs = (word.endTime * 1000).toLong()
    val wordDuration = (wordEndMs - wordStartMs).coerceAtLeast(1L)

    val progress = when {
        effectivePositionMs >= wordEndMs -> 1f
        effectivePositionMs < wordStartMs -> 0f
        else -> ((effectivePositionMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
    }

    val displayText = if (word.hasTrailingSpace) "${word.text} " else word.text

    val textStyle = TextStyle(
        fontSize = if (isBackground) 22.sp else 28.sp,
        fontWeight = FontWeight.ExtraBold,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.35f),
            offset = Offset(0f, 2f),
            blurRadius = 5f
        )
    )

    if (progress >= 1f) {
        // Fully sung word: Solid vibrant white
        Text(
            text = displayText,
            color = Color.White,
            style = textStyle
        )
    } else if (progress <= 0f) {
        // Upcoming word: Dimmed white
        Text(
            text = displayText,
            color = Color.White.copy(alpha = 0.45f),
            style = textStyle
        )
    } else {
        // Active word being sung: Smooth horizontal sweep gradient fill!
        val spread = 0.08f
        val pStart = (progress - spread).coerceAtLeast(0f)
        val pEnd = (progress + spread).coerceAtMost(1f)

        val sweepBrush = Brush.horizontalGradient(
            0f to Color.White,
            pStart to Color.White,
            pEnd to Color.White.copy(alpha = 0.45f),
            1f to Color.White.copy(alpha = 0.45f)
        )

        Text(
            text = displayText,
            style = textStyle.copy(brush = sweepBrush)
        )
    }
}

/**
 * Apple Music signature 3 pulsating dots for instrumental breaks.
 */
@Composable
private fun AppleMusicInstrumentalDots(
    gapStartMs: Long,
    gapEndMs: Long,
    currentPosMs: Long,
    isActive: Boolean
) {
    val transition = rememberInfiniteTransition(label = "dotsWave")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val containerAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.3f,
        animationSpec = tween(400),
        label = "dotsAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f * containerAlpha))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                val offsetPhase = phase + (i * (Math.PI / 2.5)).toFloat()
                val dotScale = if (isActive) (0.85f + 0.35f * sin(offsetPhase)).coerceIn(0.6f, 1.3f) else 0.8f
                val dotAlpha = if (isActive) (0.50f + 0.50f * sin(offsetPhase)).coerceIn(0.3f, 1.0f) else 0.4f

                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .scale(dotScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = dotAlpha * containerAlpha))
                )
            }
        }
    }
}
