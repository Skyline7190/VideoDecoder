package com.example.videodecoder

import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

interface LiquidActions {
    fun onSelect()
    fun onDecode()
    fun onPlay()
    fun onPause()
    fun onSpeed(speed: Float)
    fun onSeekTo(positionMs: Int)
}

object LiquidGlassHelper {
    
    private var isPlayingState = mutableStateOf(false)
    private var selectedSpeedState = mutableStateOf(1.0f)
    private var currentPositionState = mutableStateOf(0)
    private var durationState = mutableStateOf(0)
    private var playbackStateLabel = mutableStateOf("IDLE")
    private var statusTextState = mutableStateOf("Native Player")
    private var actions: LiquidActions? = null

    fun setup(composeView: ComposeView) {
        composeView.setContent {
            LiquidControlsOverlay(
                isPlaying = isPlayingState.value,
                selectedSpeed = selectedSpeedState.value,
                currentPositionMs = currentPositionState.value,
                durationMs = durationState.value,
                playbackStateLabel = playbackStateLabel.value,
                statusText = statusTextState.value,
                onSelect = { actions?.onSelect() },
                onDecode = { actions?.onDecode() },
                onPlay = { actions?.onPlay() },
                onPause = { actions?.onPause() },
                onSpeed = { actions?.onSpeed(it) },
                onSeekTo = { actions?.onSeekTo(it) }
            )
        }
    }

    fun setPlaying(isPlaying: Boolean) {
        isPlayingState.value = isPlaying
    }

    fun setSelectedSpeed(speed: Float) {
        selectedSpeedState.value = speed
    }

    fun setProgress(currentPositionMs: Int, durationMs: Int) {
        currentPositionState.value = currentPositionMs.coerceAtLeast(0)
        durationState.value = durationMs.coerceAtLeast(0)
    }

    fun setPlaybackStateLabel(label: String) {
        playbackStateLabel.value = label
    }

    fun setStatusText(text: String) {
        statusTextState.value = text
    }

    fun setActions(newActions: LiquidActions) {
        actions = newActions
    }
}
