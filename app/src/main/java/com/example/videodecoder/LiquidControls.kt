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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
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
import com.kyant.backdrop.highlight.HighlightStyle
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
    embedded: Boolean = false,
    highlightAngle: Float = 45f,
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
    val breathingScale = if (active && enabled) LocalBreathingScale.current else 1f
    val liquidProgress = max(interactiveHighlight.pressProgress, directPressProgress)
    val glassProgress = if (embedded) max(activeProgress * 0.75f, liquidProgress) else liquidProgress
    val shape = if (embedded) RoundedRectangle(18.dp) else Capsule()

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.46f }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        vibrancy()
                        val baseBlur = if (embedded) 0.5f else 2f
                        val pressBlur = if (embedded) 4f else 7f
                        blur((baseBlur + pressBlur * glassProgress).dp.toPx())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val activeRefractionHeight = if (embedded) 3f.dp.toPx() * activeProgress else 6f.dp.toPx() * activeProgress
                        val activeRefractionAmount = if (embedded) 5f.dp.toPx() * activeProgress else 8f.dp.toPx() * activeProgress
                        val pressRefractionHeight = (if (embedded) 12f else 18f).dp.toPx() * glassProgress
                        val pressRefractionAmount = (if (embedded) 18f else 26f).dp.toPx() * glassProgress
                        lens(
                            (if (embedded) 6f else 12f).dp.toPx() + activeRefractionHeight + pressRefractionHeight,
                            (if (embedded) 12f else 24f).dp.toPx() + activeRefractionAmount + pressRefractionAmount,
                            depthEffect = active || glassProgress > 0.05f,
                            chromaticAberration = chromaticAberration || glassProgress > 0.10f
                        )
                    }
                },
                highlight = {
                    Highlight.Default.copy(
                        alpha = if (embedded) {
                            lerp(0.12f, 0.55f, activeProgress).coerceAtLeast(0.58f * liquidProgress)
                        } else {
                            lerp(0.55f, 1f, activeProgress).coerceAtLeast(liquidProgress)
                        },
                        style = HighlightStyle.Default(angle = highlightAngle, falloff = if (embedded) 1.7f else 1.35f)
                    )
                },
                shadow = {
                    Shadow(
                        alpha = if (embedded) {
                            lerp(0.06f, 0.28f, activeProgress).coerceAtLeast(0.30f * liquidProgress)
                        } else {
                            lerp(0.34f, 0.78f, activeProgress).coerceAtLeast(0.72f * liquidProgress)
                        }
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = if (embedded) 4.dp + 6.dp * glassProgress else 6.dp + 10.dp * liquidProgress,
                        alpha = if (embedded) {
                            lerp(0.08f, 0.34f, activeProgress).coerceAtLeast(0.42f * liquidProgress)
                        } else {
                            lerp(0.32f, 0.76f, activeProgress).coerceAtLeast(0.82f * liquidProgress)
                        }
                    )
                },
                layerBlock = {
                    val width = size.width
                    val height = size.height
                    val progress = glassProgress
                    val activeScale = lerp(1f, 1f + 2f.dp.toPx() / size.height, activeProgress)
                    val scale = lerp(
                        activeScale,
                        activeScale + (if (embedded) 7f else 12f).dp.toPx() / size.height,
                        progress
                    )
                    val maxOffset = size.minDimension
                    val offset = interactiveHighlight.offset
                    val offsetX = maxOffset * tanh(0.12f * offset.x / maxOffset)
                    val offsetY = maxOffset * tanh(0.12f * offset.y / maxOffset)

                    translationX = offsetX
                    translationY = offsetY
                    rotationZ = lerp(0f, 3.2f, progress) * (offsetX / maxOffset)

                    val maxDragScale = (if (embedded) 7f else 12f).dp.toPx() / size.height
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
                        drawRect(tint.copy(alpha = lerp(if (embedded) 0.08f else 0.16f, if (embedded) 0.18f else 0.28f, activeProgress)))
                        drawRect(
                            Color.White.copy(alpha = (if (embedded) 0.04f else 0.09f) + (if (embedded) 0.08f else 0.10f) * liquidProgress),
                            blendMode = BlendMode.Plus
                        )
                    }
                    if (surfaceColor.isSpecified) {
                        val baseAlphaScale = if (embedded) 0.36f else 1f
                        drawRect(
                            surfaceColor.copy(
                                alpha = surfaceColor.alpha * baseAlphaScale +
                                    (if (embedded) 0.08f else 0.04f) * activeProgress +
                                    (if (embedded) 0.05f else 0.08f) * liquidProgress
                            )
                        )
                    }
                    if (activeProgress > 0f) {
                        drawRect(Color.White.copy(alpha = (if (embedded) 0.10f else 0.06f) * activeProgress), blendMode = BlendMode.Plus)
                    }
                    if (liquidProgress > 0f) {
                        drawRect(Color.Black.copy(alpha = (if (embedded) 0.03f else 0.06f) * liquidProgress), blendMode = BlendMode.Multiply)
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
                indication = glassRipple(color = Color.White),
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
                active && enabled -> Color(0xFF0091FF)
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
        val progressEnabled = durationMs > 0
        val safeDuration = durationMs.coerceAtLeast(1)
        val safeCurrent = currentPositionMs.coerceIn(0, safeDuration)

        val breathingTransition = rememberInfiniteTransition(label = "liquidBreathing")
        val breathingPhaseValue by breathingTransition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2.0).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "liquidBreathingPhase"
        )
        val breathingScaleValue = 1f + 0.014f * ((sin(breathingPhaseValue) + 1f) * 0.5f)

        CompositionLocalProvider(LocalBreathingScale provides breathingScaleValue) {
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

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(backdrop)
            )

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
                        surfaceColor = Color.White.copy(alpha = 0.14f),
                        textColor = Color.White,
                        chromaticAberration = true,
                        highlightAngle = highlightAngle,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidButton(
                        "Decode",
                        onDecode,
                        backdrop,
                        surfaceColor = Color.White.copy(alpha = 0.14f),
                        textColor = Color.White,
                        chromaticAberration = true,
                        active = isPlaying,
                        highlightAngle = highlightAngle,
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
                        surfaceColor = Color.White.copy(alpha = 0.14f),
                        textColor = Color.White,
                        chromaticAberration = true,
                        active = isPlaying,
                        highlightAngle = highlightAngle,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidButton(
                        "Pause",
                        onPause,
                        backdrop,
                        surfaceColor = Color.White.copy(alpha = 0.14f),
                        textColor = Color.White,
                        chromaticAberration = true,
                        active = !isPlaying && playbackStateLabel == "PAUSED",
                        highlightAngle = highlightAngle,
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
                        highlightAngle = highlightAngle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
    highlightAngle: Float,
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
                pressedScale = 78f / 56f,
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
                    highlight = {
                        Highlight(
                            alpha = 0.64f,
                            style = HighlightStyle.Default(angle = highlightAngle, falloff = 1.8f)
                        )
                    },
                    onDrawSurface = {
                        drawRect(Color.Black.copy(alpha = 0.4f))
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
                    .graphicsLayer {
                        translationX = panelOffset
                        val accentColor = Color(0xFF0091FF)
                        this.colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accentColor)
                    }
                    .height(64.dp)
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
                        Highlight.Default.copy(alpha = progress)
                            .copy(style = HighlightStyle.Default(angle = highlightAngle, falloff = 1.6f))
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
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
                        drawRect(Color.White.copy(alpha = 0.1f), alpha = 1f - progress)
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(48.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

private val LocalSpeedTabScale = staticCompositionLocalOf { { 1f } }
private val LocalBreathingScale = staticCompositionLocalOf { 1f }

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
                interactionSource = remember { MutableInteractionSource() },
                indication = glassRipple(color = Color.White),
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
            color = if (active) Color(0xFF0091FF) else Color.White.copy(alpha = 0.58f),
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
