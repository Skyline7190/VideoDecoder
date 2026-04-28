package com.example.videodecoder

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()
    val safeRange = if (valueRange.endInclusive <= valueRange.start) 0f..1f else valueRange

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth.coerceAtLeast(1)

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        var isInteracting by remember { mutableStateOf(false) }
        val currentValue = value().coerceIn(safeRange)
        var dragValue by remember(safeRange.start, safeRange.endInclusive) {
            mutableFloatStateOf(currentValue)
        }

        fun valueAt(positionX: Float): Float {
            val x = positionX.fastCoerceIn(0f, trackWidth.toFloat())
            val delta = (safeRange.endInclusive - safeRange.start) * (x / trackWidth)
            return (if (isLtr) safeRange.start + delta else safeRange.endInclusive - delta)
                .coerceIn(safeRange)
        }

        val dampedDragAnimation = remember(animationScope, safeRange.start, safeRange.endInclusive) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = currentValue,
                valueRange = safeRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag && enabled) {
                        onValueChange(dragValue)
                    }
                    didDrag = false
                },
                onDrag = { _, dragAmount ->
                    if (enabled) {
                        if (!didDrag) {
                            didDrag = dragAmount.x != 0f
                        }
                        val delta = (safeRange.endInclusive - safeRange.start) * (dragAmount.x / trackWidth)
                        dragValue =
                            if (isLtr) (targetValue + delta).coerceIn(safeRange)
                            else (targetValue - delta).coerceIn(safeRange)
                        updateValue(dragValue)
                    }
                }
            )
        }
        LaunchedEffect(dampedDragAnimation, enabled, isInteracting, currentValue) {
            if (!isInteracting && dampedDragAnimation.targetValue != currentValue) {
                dragValue = currentValue
                dampedDragAnimation.updateValue(currentValue)
            }
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor.copy(alpha = trackColor.alpha * if (enabled) 1f else 0.5f))
                    .height(6.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor.copy(alpha = if (enabled) 1f else 0.35f))
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress)
                            .fastRoundToInt()
                            .coerceIn(0, constraints.maxWidth)
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(enabled, trackWidth, safeRange.start, safeRange.endInclusive, isLtr) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isInteracting = true
                        didDrag = false

                        val thumbX =
                            if (isLtr) trackWidth * dampedDragAnimation.progress
                            else trackWidth * (1f - dampedDragAnimation.progress)
                        val thumbHitRadius = 28.dp.toPx()
                        val pressedOnThumb = kotlin.math.abs(down.position.x - thumbX) <= thumbHitRadius

                        var gestureValue =
                            if (pressedOnThumb) dampedDragAnimation.value.coerceIn(safeRange)
                            else valueAt(down.position.x)
                        dragValue = gestureValue

                        dampedDragAnimation.press()
                        if (pressedOnThumb) {
                            dampedDragAnimation.snapToValue(gestureValue)
                        } else {
                            dampedDragAnimation.updateValue(gestureValue)
                        }

                        var commit = true
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                commit = false
                                break
                            }

                            if (change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                break
                            }

                            if (!change.pressed) {
                                commit = false
                                break
                            }

                            val positionChange = change.positionChange()
                            if (positionChange != Offset.Zero) {
                                didDrag = true
                                gestureValue = valueAt(change.position.x)
                                dragValue = gestureValue
                                dampedDragAnimation.snapToValue(gestureValue)
                                change.consume()
                            }
                        }

                        if (commit && (didDrag || !pressedOnThumb)) {
                            onValueChange(gestureValue)
                        }
                        isInteracting = false
                        didDrag = false
                        dampedDragAnimation.release()
                    }
                }
        )

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                            if (isLtr) 1f else -1f
                    alpha = if (enabled) 1f else 0.55f
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4.dp * progress,
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
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}
