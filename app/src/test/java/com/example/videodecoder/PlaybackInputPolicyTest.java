package com.example.videodecoder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackInputPolicyTest {

    @Test
    public void sanitizeSpeed_nan_returns_default() {
        assertEquals(PlaybackInputPolicy.DEFAULT_SPEED,
                PlaybackInputPolicy.sanitizeSpeed(Float.NaN), 0.0001f);
    }

    @Test
    public void sanitizeSpeed_negative_infinity_returns_default() {
        assertEquals(PlaybackInputPolicy.DEFAULT_SPEED,
                PlaybackInputPolicy.sanitizeSpeed(Float.NEGATIVE_INFINITY), 0.0001f);
    }

    @Test
    public void sanitizeSpeed_lower_than_min_clamps_to_min() {
        assertEquals(PlaybackInputPolicy.MIN_SPEED,
                PlaybackInputPolicy.sanitizeSpeed(0.1f), 0.0001f);
    }

    @Test
    public void sanitizeSpeed_higher_than_max_clamps_to_max() {
        assertEquals(PlaybackInputPolicy.MAX_SPEED,
                PlaybackInputPolicy.sanitizeSpeed(10.0f), 0.0001f);
    }

    @Test
    public void sanitizeSpeed_valid_value_is_kept() {
        assertEquals(2.0f, PlaybackInputPolicy.sanitizeSpeed(2.0f), 0.0001f);
    }
}
