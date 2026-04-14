package com.example.videodecoder;

final class PlaybackUiPolicy {
    enum PlaybackUiState {
        IDLE,
        READY,
        PLAYING,
        PAUSED
    }

    static final class ControlState {
        final boolean decodeEnabled;
        final String decodeText;
        final boolean playEnabled;
        final boolean pauseEnabled;
        final boolean speedEnabled;
        final boolean seekEnabled;

        ControlState(
                boolean decodeEnabled,
                String decodeText,
                boolean playEnabled,
                boolean pauseEnabled,
                boolean speedEnabled,
                boolean seekEnabled
        ) {
            this.decodeEnabled = decodeEnabled;
            this.decodeText = decodeText;
            this.playEnabled = playEnabled;
            this.pauseEnabled = pauseEnabled;
            this.speedEnabled = speedEnabled;
            this.seekEnabled = seekEnabled;
        }
    }

    private PlaybackUiPolicy() {}

    static ControlState resolve(PlaybackUiState state) {
        boolean running = (state == PlaybackUiState.PLAYING || state == PlaybackUiState.PAUSED);
        return new ControlState(
                !running,
                running ? "解析中..." : "解析视频",
                state == PlaybackUiState.PAUSED,
                state == PlaybackUiState.PLAYING,
                running,
                running
        );
    }
}
