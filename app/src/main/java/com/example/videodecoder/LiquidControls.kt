package com.example.videodecoder

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun LiquidButton(
    text: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
    active: Boolean = false,
    activeTextColor: Color = Color(0xFF0091FF),
    chromaticAberration: Boolean = false,
    enabled: Boolean = true,
    embedded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    Row(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.46f }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { if (embedded) RoundedRectangle(18.dp) else Capsule() },
                effects = {
                    vibrancy()
                    blur(if (embedded) 1f.dp.toPx() else 2f.dp.toPx())
                    lens(
                        if (embedded) 8f.dp.toPx() else 12f.dp.toPx(),
                        if (embedded) 16f.dp.toPx() else 24f.dp.toPx(),
                        chromaticAberration = chromaticAberration
                    )
                },
                layerBlock = {
                    val width = size.width
                    val height = size.height

                    val progress = interactiveHighlight.pressProgress
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                    val maxOffset = size.minDimension
                    val offset = interactiveHighlight.offset
                    val initialDerivative = 0.05f
                    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                    val maxDragScale = 4f.dp.toPx() / size.height
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX =
                        scale +
                                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                (width / height).fastCoerceAtMost(1f)
                    scaleY =
                        scale +
                                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                (height / width).fastCoerceAtMost(1f)
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                }
            )
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    interactiveHighlight.pulse()
                    onClick()
                }
            )
            .then(if (enabled) interactiveHighlight.modifier else Modifier)
            .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
            .height(48.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val baseTextColor =
            when {
                textColor.isSpecified -> textColor
                tint.isSpecified -> Color.White
                else -> Color.Black
            }
        val resolvedTextColor by animateColorAsState(
            targetValue = if (active) activeTextColor else baseTextColor,
            label = "liquidButtonTextColor"
        )

        Text(
            text = text,
            color = resolvedTextColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun LiquidControlsOverlay(
    isPlaying: Boolean,
    selectedSpeed: Float,
    currentPositionMs: Int,
    durationMs: Int,
    playbackStateLabel: String,
    statusText: String,
    onSelect: () -> Unit,
    onDecode: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSpeed: (Float) -> Unit,
    onSeekTo: (Int) -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme().copy(
        surface = Color.Transparent,
        surfaceVariant = Color.Transparent,
        surfaceContainerLow = Color.Transparent,
        surfaceContainer = Color.Transparent,
        surfaceContainerHigh = Color.Transparent,
        surfaceContainerHighest = Color.Transparent,
        surfaceDim = Color.Transparent
    )) {
        val uiSensor = rememberUISensor()
        val highlightAngle = uiSensor.gravityAngle
        val backdrop = rememberLayerBackdrop()
        val wallpaperBitmap = ImageBitmap.imageResource(id = R.drawable.wallpaper_light)
        val progressEnabled = durationMs > 0
        val safeDuration = durationMs.coerceAtLeast(1)
        val safeCurrent = currentPositionMs.coerceIn(0, safeDuration)
        val activeButtonTextColor =
            if (!isSystemInDarkTheme()) Color(0xFF007AFF) else Color(0xFF38A7FF)
        val isDecodingState = playbackStateLabel.equals("PLAYING", ignoreCase = true)
        val isPausedState = playbackStateLabel.equals("PAUSED", ignoreCase = true)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            val pageHorizontalPadding = 16.dp
            val pageTopPadding =
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp
            val headerHeight = 96.dp
            val headerToVideoGap = 12.dp
            val videoToProgressGap = 4.dp
            val progressPanelHeight = 54.dp
            val progressToControlsGap = 8.dp
            val videoStageHeight = (maxWidth - pageHorizontalPadding * 2f) * 9f / 16f
            val progressTopPadding =
                pageTopPadding + headerHeight + headerToVideoGap + videoStageHeight + videoToProgressGap
            val controlsTopPadding = progressTopPadding + progressPanelHeight + progressToControlsGap

            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(backdrop)
            ) {
                val videoLeft = pageHorizontalPadding.toPx()
                val videoTop = (pageTopPadding + headerHeight + headerToVideoGap).toPx()
                val videoRight = size.width - videoLeft
                val videoBottom = videoTop + videoStageHeight.toPx()

                clipRect(0f, 0f, size.width, videoTop) {
                    drawCroppedBackdropImage(wallpaperBitmap)
                }
                clipRect(0f, videoBottom, size.width, size.height) {
                    drawCroppedBackdropImage(wallpaperBitmap)
                }
                clipRect(0f, videoTop, videoLeft, videoBottom) {
                    drawCroppedBackdropImage(wallpaperBitmap)
                }
                clipRect(videoRight, videoTop, size.width, videoBottom) {
                    drawCroppedBackdropImage(wallpaperBitmap)
                }
            }

            HeaderPanel(
                backdrop = backdrop,
                topPadding = pageTopPadding,
                horizontalPadding = pageHorizontalPadding,
                height = headerHeight,
                playbackStateLabel = playbackStateLabel,
                statusText = statusText,
                highlightAngle = highlightAngle
            )

            ProgressPanel(
                backdrop = backdrop,
                topPadding = progressTopPadding,
                horizontalPadding = 20.dp,
                highlightAngle = highlightAngle
            ) { progressBackdrop ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatClock(safeCurrent),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    LiquidSlider(
                        value = { safeCurrent.toFloat() },
                        onValueChange = { onSeekTo(it.roundToInt()) },
                        valueRange = 0f..safeDuration.toFloat(),
                        visibilityThreshold = 1f,
                        backdrop = progressBackdrop,
                        enabled = progressEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = formatClock(durationMs),
                        color = Color.White.copy(alpha = if (progressEnabled) 0.92f else 0.52f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = controlsTopPadding)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidButton(
                        "Select",
                        onSelect,
                        backdrop,
                        surfaceColor = Color.White.copy(alpha = 0.3f),
                        textColor = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidButton(
                        "Decode",
                        onDecode,
                        backdrop,
                        surfaceColor = Color.White.copy(alpha = 0.3f),
                        textColor = Color.White,
                        active = isDecodingState,
                        activeTextColor = activeButtonTextColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidButton(
                        if (isPlaying) "Playing" else "Play",
                        onPlay,
                        backdrop,
                        surfaceColor = Color.White.copy(alpha = 0.3f),
                        textColor = Color.White,
                        active = isPlaying,
                        activeTextColor = activeButtonTextColor,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidButton(
                        "Pause",
                        onPause,
                        backdrop,
                        surfaceColor = Color.White.copy(alpha = 0.3f),
                        textColor = Color.White,
                        active = isPausedState,
                        activeTextColor = activeButtonTextColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedBottomTabs(
                        selectedSpeed = selectedSpeed,
                        backdrop = backdrop,
                        onSpeed = onSpeed,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.HeaderPanel(
    backdrop: Backdrop,
    topPadding: Dp,
    horizontalPadding: Dp,
    height: Dp,
    playbackStateLabel: String,
    statusText: String,
    highlightAngle: Float
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = horizontalPadding)
            .padding(top = topPadding)
            .fillMaxWidth()
            .height(height)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(24.dp) },
                effects = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        vibrancy()
                        blur(5f.dp.toPx())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(16f.dp.toPx(), 30f.dp.toPx(), depthEffect = true, chromaticAberration = true)
                    }
                },
                highlight = {
                    Highlight(
                        alpha = 0.72f,
                        style = HighlightStyle.Default(angle = highlightAngle, falloff = 2f)
                    )
                },
                shadow = { Shadow(radius = 14.dp, alpha = 0.12f) },
                innerShadow = { InnerShadow(radius = 10.dp, alpha = 0.18f) },
                onDrawSurface = {
                    drawRect(Color.Black.copy(alpha = 0.06f), blendMode = BlendMode.Multiply)
                    drawRect(Color.White.copy(alpha = 0.10f), blendMode = BlendMode.Plus)
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Video Decoder",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderChip(playbackStateLabel, backdrop, highlightAngle)
                HeaderChip("RENDER", backdrop, highlightAngle)
            }
        }

        Text(
            text = statusText.ifBlank { "Native Player" },
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HeaderChip(
    text: String,
    backdrop: Backdrop,
    highlightAngle: Float
) {
    Box(
        modifier = Modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        vibrancy()
                        blur(3f.dp.toPx())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(8f.dp.toPx(), 16f.dp.toPx(), chromaticAberration = true)
                    }
                },
                highlight = {
                    Highlight.Default.copy(
                        alpha = 0.70f,
                        style = HighlightStyle.Default(angle = highlightAngle, falloff = 1.6f)
                    )
                },
                shadow = { Shadow(alpha = 0.30f) },
                innerShadow = { InnerShadow(alpha = 0.32f) },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.18f))
                }
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BoxScope.ProgressPanel(
    backdrop: Backdrop,
    topPadding: Dp,
    horizontalPadding: Dp,
    highlightAngle: Float,
    content: @Composable ColumnScope.(Backdrop) -> Unit
) {
    val progressBackdrop = rememberLayerBackdrop()
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = topPadding)
            .padding(horizontal = horizontalPadding)
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(24.dp) },
                effects = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        vibrancy()
                        blur(6f.dp.toPx())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(18f.dp.toPx(), 34f.dp.toPx(), depthEffect = true, chromaticAberration = true)
                    }
                },
                highlight = {
                    Highlight(
                        alpha = 0.66f,
                        style = HighlightStyle.Default(angle = highlightAngle, falloff = 2f)
                    )
                },
                exportedBackdrop = progressBackdrop,
                shadow = { Shadow(radius = 16.dp, alpha = 0.14f) },
                innerShadow = { InnerShadow(radius = 10.dp, alpha = 0.24f) },
                onDrawSurface = {
                    drawRect(Color.Black.copy(alpha = 0.08f), blendMode = BlendMode.Multiply)
                    drawRect(Color.White.copy(alpha = 0.13f), blendMode = BlendMode.Plus)
                }
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        content = { content(progressBackdrop) }
    )
}

@Composable
private fun StatusPill(
    label: String,
    backdrop: Backdrop,
    highlightAngle: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = label.isNotBlank(), modifier = modifier) {
        Box(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            vibrancy()
                            blur(4f.dp.toPx())
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(10f.dp.toPx(), 22f.dp.toPx(), chromaticAberration = true)
                        }
                    },
                    highlight = {
                        Highlight.Default.copy(
                            alpha = 0.70f,
                            style = HighlightStyle.Default(angle = highlightAngle, falloff = 1.6f)
                        )
                    },
                    shadow = { Shadow(alpha = 0.38f) },
                    innerShadow = { InnerShadow(alpha = 0.40f) },
                    onDrawSurface = {
                        drawRect(Color.Black.copy(alpha = 0.10f))
                        drawRect(Color.White.copy(alpha = 0.14f), blendMode = BlendMode.Plus)
                    }
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SpeedBottomTabs(
    selectedSpeed: Float,
    backdrop: Backdrop,
    onSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val containerColor = Color.White.copy(alpha = if (isLightTheme) 0.34f else 0.24f)
    val containerSheenColor = Color.White.copy(alpha = if (isLightTheme) 0.12f else 0.08f)
    val indicatorRestColor = Color.White.copy(alpha = if (isLightTheme) 0.20f else 0.16f)
    val speedValues = remember { listOf(0.5f, 1.0f, 2.0f, 3.0f) }
    val speedLabels = remember { listOf("0.5x", "1x", "2x", "3x") }
    val selectedIndex = remember(selectedSpeed) { speedToIndex(selectedSpeed, speedValues) }
    val tabsCount = speedValues.size
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember { mutableIntStateOf(selectedIndex) }

        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }

        LaunchedEffect(selectedIndex) {
            currentIndex = selectedIndex
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onSpeed(speedValues[index])
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        drawRect(containerColor)
                        drawRect(containerSheenColor, blendMode = BlendMode.Plus)
                    }
                )
                .then(interactiveHighlight.modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            speedLabels.forEachIndexed { index, label ->
                SpeedBottomTab(
                    label = label,
                    color = contentColor,
                    onClick = {
                        currentIndex = index
                    }
                )
            }
        }

        CompositionLocalProvider(
            LocalSpeedTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(
                                24f.dp.toPx() * progress,
                                24f.dp.toPx() * progress
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = {
                            drawRect(containerColor)
                            drawRect(containerSheenColor, blendMode = BlendMode.Plus)
                        }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accentColor)
                    }
                ,
                verticalAlignment = Alignment.CenterVertically
            ) {
                speedLabels.forEachIndexed { index, label ->
                    SpeedBottomTab(
                        label = label,
                        color = contentColor,
                        onClick = {
                            currentIndex = index
                        }
                    )
                }
            }
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(indicatorRestColor, alpha = 1f - progress)
                        drawRect(accentColor.copy(alpha = 0.06f * progress))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

private val LocalSpeedTabScale = staticCompositionLocalOf { { 1f } }

@Composable
private fun RowScope.SpeedBottomTab(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    val scale = LocalSpeedTabScale.current
    Column(
        modifier = Modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val tabScale = scale()
                scaleX = tabScale
                scaleY = tabScale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun speedToIndex(selectedSpeed: Float, speeds: List<Float>): Int {
    var bestIndex = 0
    var bestDiff = Float.MAX_VALUE
    for (i in speeds.indices) {
        val diff = abs(selectedSpeed - speeds[i])
        if (diff < bestDiff) {
            bestDiff = diff
            bestIndex = i
        }
    }
    return bestIndex
}

private fun DrawScope.drawCroppedBackdropImage(image: ImageBitmap) {
    val dstWidth = size.width.roundToInt().coerceAtLeast(1)
    val dstHeight = size.height.roundToInt().coerceAtLeast(1)
    val scale = max(
        dstWidth.toFloat() / image.width.toFloat(),
        dstHeight.toFloat() / image.height.toFloat()
    )
    val srcWidth = (dstWidth / scale).roundToInt().coerceIn(1, image.width)
    val srcHeight = (dstHeight / scale).roundToInt().coerceIn(1, image.height)
    val srcLeft = ((image.width - srcWidth) / 2).coerceAtLeast(0)
    val srcTop = ((image.height - srcHeight) / 2).coerceAtLeast(0)

    drawImage(
        image = image,
        srcOffset = IntOffset(srcLeft, srcTop),
        srcSize = IntSize(srcWidth, srcHeight),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(dstWidth, dstHeight)
    )
}

private fun formatClock(ms: Int): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
