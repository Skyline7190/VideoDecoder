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
}

object LiquidGlassHelper {
    
    private var isPlayingState = mutableStateOf(false)
    private var selectedSpeedState = mutableStateOf(1.0f)
    private var actions: LiquidActions? = null

    fun setup(composeView: ComposeView) {
        composeView.setContent {
            LiquidControlsOverlay(
                isPlaying = isPlayingState.value,
                selectedSpeed = selectedSpeedState.value,
                onSelect = { actions?.onSelect() },
                onDecode = { actions?.onDecode() },
                onPlay = { actions?.onPlay() },
                onPause = { actions?.onPause() },
                onSpeed = { actions?.onSpeed(it) }
            )
        }
    }

    fun setPlaying(isPlaying: Boolean) {
        isPlayingState.value = isPlaying
    }

    fun setSelectedSpeed(speed: Float) {
        selectedSpeedState.value = speed
    }

    fun setActions(newActions: LiquidActions) {
        actions = newActions
    }
}
