package com.example.videodecoder;

import java.util.Locale;

final class PlaybackTimeFormatter {
    private PlaybackTimeFormatter() {}

    static String formatTime(int milliseconds, Locale locale) {
        int safeMs = Math.max(0, milliseconds);
        int seconds = safeMs / 1000;
        int minutes = seconds / 60;
        int hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format(locale, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(locale, "%02d:%02d", minutes, seconds);
    }
}
