package com.example.videodecoder

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import kotlin.math.sin
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
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
    modifier: Modifier = Modifier
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val activeProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquidActiveProgress"
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
    val breathingScale = if (active) (1f + 0.014f * ((sin(phase) + 1f) * 0.5f)) else 1f

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        vibrancy()
                        blur(2f.dp.toPx())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val activeRefractionHeight = 6f.dp.toPx() * activeProgress
                        val activeRefractionAmount = 8f.dp.toPx() * activeProgress
                        lens(
                            12f.dp.toPx() + activeRefractionHeight,
                            24f.dp.toPx() + activeRefractionAmount,
                            chromaticAberration = chromaticAberration
                        )
                    }
                },
                highlight = {
                    Highlight.Default.copy(alpha = lerp(0.5f, 1f, activeProgress))
                },
                shadow = {
                    Shadow(alpha = lerp(0.65f, 0.95f, activeProgress))
                },
                innerShadow = {
                    InnerShadow(alpha = lerp(0.45f, 0.9f, activeProgress))
                },
                layerBlock = {
                    val width = size.width
                    val height = size.height

                    val progress = interactiveHighlight.pressProgress
                    val activeScale = lerp(1f, 1f + 2f.dp.toPx() / size.height, activeProgress)
                    val scale = lerp(activeScale, activeScale + 4f.dp.toPx() / size.height, progress)

                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    val offset = interactiveHighlight.offset
                    val offsetX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    val offsetY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)
                    translationX = offsetX
                    translationY = offsetY
                    rotationZ = lerp(0f, 1.25f, progress) * (offsetX / maxOffset)

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
                        drawRect(tint.copy(alpha = lerp(0.72f, 0.84f, activeProgress)))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(
                            surfaceColor.copy(alpha = surfaceColor.alpha + 0.06f * activeProgress)
                        )
                    }
                    if (activeProgress > 0f) {
                        drawRect(
                            Color.White.copy(alpha = 0.08f * activeProgress),
                            blendMode = BlendMode.Plus
                        )
                    }
                }
            )
            .graphicsLayer {
                val interactionDamping = 1f - interactiveHighlight.pressProgress * 0.85f
                val scale = 1f + (breathingScale - 1f) * interactionDamping
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
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
            fontSize = 15.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun LiquidControlsOverlay(
    isPlaying: Boolean,
    selectedSpeed: Float,
    onSelect: () -> Unit,
    onDecode: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSpeed: (Float) -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val backdrop = rememberLayerBackdrop()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(236.dp)
                .padding(top = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(backdrop)
                    // 直接去掉原本带有割裂感的背景和光斑，让容器层完全透明
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidButton(
                        "Select Video",
                        onSelect,
                        backdrop,
                        tint = Color(0xFF2A84D4),
                        active = false,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidButton(
                        "Decode Video",
                        onDecode,
                        backdrop,
                        tint = Color(0xFF2A84D4),
                        chromaticAberration = true,
                        active = false,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidButton(
                        text = if (isPlaying) "Playing" else "Play",
                        onClick = onPlay,
                        backdrop = backdrop,
                        tint = Color(0xFF2A84D4),
                        chromaticAberration = true,
                        active = isPlaying,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidButton(
                        text = "Pause",
                        onClick = onPause,
                        backdrop = backdrop,
                        tint = Color(0xFF2A84D4),
                        chromaticAberration = true,
                        active = !isPlaying,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiquidButton(
                        "x0.5",
                        { onSpeed(0.5f) },
                        backdrop,
                        surfaceColor = Color.White.copy(alpha = 0.30f),
                        textColor = Color.Black,
                        active = selectedSpeed == 0.5f,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidButton(
                        "x1.0",
                        { onSpeed(1.0f) },
                        backdrop,
                        surfaceColor = Color.White.copy(alpha = 0.30f),
                        textColor = Color.Black,
                        active = selectedSpeed == 1.0f,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidButton(
                        "x2.0",
                        { onSpeed(2.0f) },
                        backdrop,
                        surfaceColor = Color.White.copy(alpha = 0.30f),
                        textColor = Color.Black,
                        active = selectedSpeed == 2.0f,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidButton(
                        "x3.0",
                        { onSpeed(3.0f) },
                        backdrop,
                        surfaceColor = Color.White.copy(alpha = 0.30f),
                        textColor = Color.Black,
                        active = selectedSpeed == 3.0f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
