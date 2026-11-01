package com.zenith.launcher;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Build;
import java.util.List;

public class MediaUtils {

    /**
     * Checks if any media playback is currently active across the system.
     * Prevents screensaver activation during video streaming (Prime Video, Netflix, YouTube, Streamflix)
     * or music playback.
     */
    public static boolean isMediaPlaying(Context context) {
        if (context == null) return false;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;

            if (am.isMusicActive()) {
                return true;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
                if (configs != null && !configs.isEmpty()) {
                    for (AudioPlaybackConfiguration cfg : configs) {
                        if (cfg == null) continue;
                        android.media.AudioAttributes attr = cfg.getAudioAttributes();
                        if (attr != null) {
                            int usage = attr.getUsage();
                            if (usage == android.media.AudioAttributes.USAGE_MEDIA
                                    || usage == android.media.AudioAttributes.USAGE_GAME
                                    || usage == android.media.AudioAttributes.USAGE_UNKNOWN) {
                                return true;
                            }
                        } else {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
