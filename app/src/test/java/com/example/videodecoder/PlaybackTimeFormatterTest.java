package com.example.videodecoder;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class PlaybackTimeFormatterTest {

    @Test
    public void formatTime_negative_returns_zero() {
        assertEquals("00:00", PlaybackTimeFormatter.formatTime(-1, Locale.US));
    }

    @Test
    public void formatTime_under_one_hour_uses_mm_ss() {
        assertEquals("02:05", PlaybackTimeFormatter.formatTime(125000, Locale.US));
    }

    @Test
    public void formatTime_over_one_hour_uses_h_mm_ss() {
        assertEquals("1:01:01", PlaybackTimeFormatter.formatTime(3661000, Locale.US));
    }
}
