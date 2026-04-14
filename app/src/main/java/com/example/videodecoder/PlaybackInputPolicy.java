package com.example.videodecoder;

final class PlaybackInputPolicy {
    static final float MIN_SPEED = 0.5f;
    static final float MAX_SPEED = 3.0f;
    static final float DEFAULT_SPEED = 1.0f;

    private PlaybackInputPolicy() {}

    static float sanitizeSpeed(float speed) {
        if (!Float.isFinite(speed)) {
            return DEFAULT_SPEED;
        }
        if (speed < MIN_SPEED) {
            return MIN_SPEED;
        }
        if (speed > MAX_SPEED) {
            return MAX_SPEED;
        }
        return speed;
    }
}
