package com.example.videodecoder

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
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
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
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
    chromaticAberration: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val activeProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquidActiveProgress"
    )
    val directPressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessLow
        ),
        label = "liquidDirectPressProgress"
    )
    val breathingPhase = rememberInfiniteTransition(label = "liquidBreathing")
    val phase by breathingPhase.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquidBreathingPhase"
    )
    val breathingScale = if (active && enabled) 1f + 0.014f * ((sin(phase) + 1f) * 0.5f) else 1f
    val liquidProgress = max(interactiveHighlight.pressProgress, directPressProgress)

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.46f }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        vibrancy()
                        blur((3f + 8f * liquidProgress).dp.toPx())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val activeRefractionHeight = 6f.dp.toPx() * activeProgress
                        val activeRefractionAmount = 8f.dp.toPx() * activeProgress
                        val pressRefractionHeight = 22f.dp.toPx() * liquidProgress
                        val pressRefractionAmount = 32f.dp.toPx() * liquidProgress
                        lens(
                            12f.dp.toPx() + activeRefractionHeight + pressRefractionHeight,
                            24f.dp.toPx() + activeRefractionAmount + pressRefractionAmount,
                            depthEffect = active || liquidProgress > 0.05f,
                            chromaticAberration = chromaticAberration || liquidProgress > 0.08f
                        )
                    }
                },
                highlight = {
                    Highlight.Default.copy(alpha = lerp(0.55f, 1f, activeProgress).coerceAtLeast(liquidProgress))
                },
                shadow = {
                    Shadow(alpha = lerp(0.58f, 0.96f, activeProgress).coerceAtLeast(0.92f * liquidProgress))
                },
                innerShadow = {
                    InnerShadow(
                        radius = 8.dp + 12.dp * liquidProgress,
                        alpha = lerp(0.48f, 0.94f, activeProgress).coerceAtLeast(liquidProgress)
                    )
                },
                layerBlock = {
                    val width = size.width
                    val height = size.height
                    val progress = liquidProgress
                    val activeScale = lerp(1f, 1f + 2f.dp.toPx() / size.height, activeProgress)
                    val scale = lerp(activeScale, activeScale + 18f.dp.toPx() / size.height, progress)
                    val maxOffset = size.minDimension
                    val offset = interactiveHighlight.offset
                    val offsetX = maxOffset * tanh(0.12f * offset.x / maxOffset)
                    val offsetY = maxOffset * tanh(0.12f * offset.y / maxOffset)

                    translationX = offsetX
                    translationY = offsetY
                    rotationZ = lerp(0f, 3.2f, progress) * (offsetX / maxOffset)

                    val maxDragScale = 18f.dp.toPx() / size.height
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
                        drawRect(tint.copy(alpha = lerp(0.22f, 0.36f, activeProgress)))
                        drawRect(Color.White.copy(alpha = 0.12f + 0.14f * liquidProgress), blendMode = BlendMode.Plus)
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(
                            surfaceColor.copy(
                                alpha = surfaceColor.alpha + 0.06f * activeProgress + 0.14f * liquidProgress
                            )
                        )
                    }
                    if (activeProgress > 0f) {
                        drawRect(Color.White.copy(alpha = 0.08f * activeProgress), blendMode = BlendMode.Plus)
                    }
                    if (liquidProgress > 0f) {
                        drawRect(Color.Black.copy(alpha = 0.10f * liquidProgress), blendMode = BlendMode.Multiply)
                    }
                }
            )
            .graphicsLayer {
                val interactionDamping = 1f - liquidProgress * 0.85f
                val scale = 1f + (breathingScale - 1f) * interactionDamping
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .then(if (enabled) interactiveHighlight.modifier else Modifier)
            .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
            .height(48.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val resolvedTextColor =
            when {
                textColor.isSpecified -> textColor
                tint.isSpecified -> Color.White
                else -> Color.Black
            }

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
    MaterialTheme(colorScheme = darkColorScheme()) {
        val backdrop = rememberLayerBackdrop()
        val progressEnabled = durationMs > 0
        val safeDuration = durationMs.coerceAtLeast(1)
        val safeCurrent = currentPositionMs.coerceIn(0, safeDuration)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            val pageHorizontalPadding = 16.dp
            val pageTopPadding = 14.dp
            val headerHeight = 96.dp
            val headerToVideoGap = 12.dp
            val videoToProgressGap = 4.dp
            val progressPanelHeight = 54.dp
            val progressToControlsGap = 8.dp
            val videoStageHeight = (maxWidth - pageHorizontalPadding * 2f) * 9f / 16f
            val progressTopPadding =
                pageTopPadding + headerHeight + headerToVideoGap + videoStageHeight + videoToProgressGap
            val controlsTopPadding = progressTopPadding + progressPanelHeight + progressToControlsGap

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(backdrop)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.04f)
                        )
                    )
            )

            HeaderPanel(
                backdrop = backdrop,
                topPadding = pageTopPadding,
                horizontalPadding = pageHorizontalPadding,
                height = headerHeight,
                playbackStateLabel = playbackStateLabel,
                statusText = statusText
            )

            ProgressPanel(
                backdrop = backdrop,
                topPadding = progressTopPadding,
                horizontalPadding = 20.dp
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

            GlassPanel(
                backdrop = backdrop,
                topPadding = controlsTopPadding,
                horizontalPadding = 16.dp
            ) { controlsBackdrop ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidButton(
                        "Select",
                        onSelect,
                        controlsBackdrop,
                        surfaceColor = Color.White.copy(alpha = 0.22f),
                        textColor = Color.White,
                        chromaticAberration = true,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidButton(
                        "Decode",
                        onDecode,
                        controlsBackdrop,
                        surfaceColor = Color.White.copy(alpha = 0.22f),
                        textColor = Color.White,
                        chromaticAberration = true,
                        active = isPlaying,
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
                        controlsBackdrop,
                        surfaceColor = Color.White.copy(alpha = 0.22f),
                        textColor = Color.White,
                        chromaticAberration = true,
                        active = isPlaying,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidButton(
                        "Pause",
                        onPause,
                        controlsBackdrop,
                        surfaceColor = Color.White.copy(alpha = 0.22f),
                        textColor = Color.White,
                        chromaticAberration = true,
                        active = !isPlaying && playbackStateLabel == "PAUSED",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedBottomTabs(
                        selectedSpeed = selectedSpeed,
                        backdrop = controlsBackdrop,
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
    statusText: String
) {
    val headerBackdrop = rememberLayerBackdrop()
    Row(
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
                        blur(6f.dp.toPx())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(18f.dp.toPx(), 34f.dp.toPx(), depthEffect = true, chromaticAberration = true)
                    }
                },
                highlight = { Highlight.Plain },
                exportedBackdrop = headerBackdrop,
                shadow = { Shadow(radius = 18.dp, alpha = 0.20f) },
                innerShadow = { InnerShadow(radius = 12.dp, alpha = 0.32f) },
                onDrawSurface = {
                    drawRect(Color.Black.copy(alpha = 0.14f), blendMode = BlendMode.Multiply)
                    drawRect(Color.White.copy(alpha = 0.20f), blendMode = BlendMode.Plus)
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "Video Decoder",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusText.ifBlank { "Native Player" },
                color = Color.White.copy(alpha = 0.84f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeaderChip(playbackStateLabel, headerBackdrop)
            HeaderChip("RENDER", headerBackdrop)
        }
    }
}

@Composable
private fun HeaderChip(
    text: String,
    backdrop: Backdrop
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
                highlight = { Highlight.Default },
                shadow = { Shadow(alpha = 0.42f) },
                innerShadow = { InnerShadow(alpha = 0.42f) },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.24f))
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
                highlight = { Highlight.Plain },
                exportedBackdrop = progressBackdrop,
                shadow = { Shadow(radius = 16.dp, alpha = 0.18f) },
                innerShadow = { InnerShadow(radius = 10.dp, alpha = 0.30f) },
                onDrawSurface = {
                    drawRect(Color.Black.copy(alpha = 0.14f), blendMode = BlendMode.Multiply)
                    drawRect(Color.White.copy(alpha = 0.20f), blendMode = BlendMode.Plus)
                }
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        content = { content(progressBackdrop) }
    )
}

@Composable
private fun BoxScope.GlassPanel(
    backdrop: Backdrop,
    topPadding: Dp,
    horizontalPadding: Dp,
    content: @Composable ColumnScope.(Backdrop) -> Unit
) {
    val controlsBackdrop = rememberLayerBackdrop()
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = topPadding)
            .padding(horizontal = horizontalPadding)
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(32.dp) },
                effects = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        vibrancy()
                        blur(8f.dp.toPx())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true, chromaticAberration = true)
                    }
                },
                highlight = { Highlight.Plain },
                exportedBackdrop = controlsBackdrop,
                shadow = { Shadow(radius = 24.dp, alpha = 0.24f) },
                innerShadow = { InnerShadow(radius = 14.dp, alpha = 0.42f) },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.20f))
                    drawRect(Color.Black.copy(alpha = 0.16f), blendMode = BlendMode.Multiply)
                }
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content(controlsBackdrop) }
    )
}

@Composable
private fun StatusPill(
    label: String,
    backdrop: Backdrop,
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
                    highlight = { Highlight.Default },
                    shadow = { Shadow(alpha = 0.55f) },
                    innerShadow = { InnerShadow(alpha = 0.55f) },
                    onDrawSurface = {
                        drawRect(Color.Black.copy(alpha = 0.16f))
                        drawRect(Color.White.copy(alpha = 0.20f), blendMode = BlendMode.Plus)
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
                valueDampingRatio = 0.78f,
                valueStiffness = 760f,
                velocityDampingRatio = 0.42f,
                velocityStiffness = 220f,
                initialScale = 1f,
                pressedScale = 1.46f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    onSpeed(speedValues[targetIndex])
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(0.68f, 240f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    snapToValue(
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
            dampedDragAnimation.animateToValue(selectedIndex.toFloat())
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            vibrancy()
                            blur(8f.dp.toPx())
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(24f.dp.toPx(), 24f.dp.toPx(), depthEffect = true)
                        }
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.24f))
                        drawRect(Color.Black.copy(alpha = 0.08f), blendMode = BlendMode.Multiply)
                    }
                )
                .then(interactiveHighlight.modifier)
                .height(56.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            speedLabels.forEachIndexed { index, label ->
                SpeedBottomTab(
                    label = label,
                    active = currentIndex == index,
                    onClick = {
                        currentIndex = index
                        dampedDragAnimation.animateToValue(index.toFloat())
                        onSpeed(speedValues[index])
                    }
                )
            }
        }

        CompositionLocalProvider(
            LocalSpeedTabScale provides {
                lerp(1f, 1.18f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                vibrancy()
                                blur(8f.dp.toPx())
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                            }
                        },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = lerp(0.55f, 1f, progress))
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.18f)) }
                )
                    .then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                speedLabels.forEachIndexed { index, label ->
                    SpeedBottomTab(
                        label = label,
                        active = currentIndex == index,
                        onClick = {
                            currentIndex = index
                            dampedDragAnimation.animateToValue(index.toFloat())
                            onSpeed(speedValues[index])
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(
                                10f.dp.toPx() * progress,
                                14f.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = lerp(0.65f, 1f, progress))
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = lerp(0.22f, 1f, progress))
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 8.dp * progress, alpha = lerp(0.18f, 1f, progress))
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
                        drawRect(Color.White.copy(alpha = 0.30f), alpha = 1f - progress)
                        drawRect(Color.White.copy(alpha = 0.16f * progress), blendMode = BlendMode.Plus)
                        drawRect(Color.Black.copy(alpha = 0.02f * progress))
                    }
                )
                .height(48.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

private val LocalSpeedTabScale = staticCompositionLocalOf { { 1f } }

@Composable
private fun RowScope.SpeedBottomTab(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val scale = LocalSpeedTabScale.current
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .graphicsLayer {
                val tabScale = scale()
                scaleX = tabScale
                scaleY = tabScale
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.58f),
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
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

private fun formatClock(ms: Int): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
