package com.example.videodecoder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackUiPolicyTest {

    @Test
    public void idle_state_controls_are_correct() {
        PlaybackUiPolicy.ControlState state = PlaybackUiPolicy.resolve(
                PlaybackUiPolicy.PlaybackUiState.IDLE);

        assertTrue(state.decodeEnabled);
        assertEquals("解析视频", state.decodeText);
        assertFalse(state.playEnabled);
        assertFalse(state.pauseEnabled);
        assertFalse(state.speedEnabled);
        assertFalse(state.seekEnabled);
    }

    @Test
    public void ready_state_controls_are_correct() {
        PlaybackUiPolicy.ControlState state = PlaybackUiPolicy.resolve(
                PlaybackUiPolicy.PlaybackUiState.READY);

        assertTrue(state.decodeEnabled);
        assertEquals("解析视频", state.decodeText);
        assertFalse(state.playEnabled);
        assertFalse(state.pauseEnabled);
        assertFalse(state.speedEnabled);
        assertFalse(state.seekEnabled);
    }

    @Test
    public void playing_state_controls_are_correct() {
        PlaybackUiPolicy.ControlState state = PlaybackUiPolicy.resolve(
                PlaybackUiPolicy.PlaybackUiState.PLAYING);

        assertFalse(state.decodeEnabled);
        assertEquals("解析中...", state.decodeText);
        assertFalse(state.playEnabled);
        assertTrue(state.pauseEnabled);
        assertTrue(state.speedEnabled);
        assertTrue(state.seekEnabled);
    }

    @Test
    public void paused_state_controls_are_correct() {
        PlaybackUiPolicy.ControlState state = PlaybackUiPolicy.resolve(
                PlaybackUiPolicy.PlaybackUiState.PAUSED);

        assertFalse(state.decodeEnabled);
        assertEquals("解析中...", state.decodeText);
        assertTrue(state.playEnabled);
        assertFalse(state.pauseEnabled);
        assertTrue(state.speedEnabled);
        assertTrue(state.seekEnabled);
    }
}
