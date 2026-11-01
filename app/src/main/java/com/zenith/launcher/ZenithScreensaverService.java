package com.zenith.launcher;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.widget.TextView;

import java.util.Random;

public class ZenithScreensaverService extends DreamService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private View contentContainer;
    private final Random random = new Random();

    private final Runnable driftRunnable = new Runnable() {
        @Override
        public void run() {
            if (MediaUtils.isMediaPlaying(ZenithScreensaverService.this)) {
                finish();
                return;
            }
            if (contentContainer != null) {
                // Gentle anti burn-in drift (within ±40dp)
                float targetX = (random.nextFloat() * 80f) - 40f;
                float targetY = (random.nextFloat() * 60f) - 30f;
                contentContainer.animate()
                        .translationX(targetX)
                        .translationY(targetY)
                        .setDuration(4000)
                        .start();
            }
            handler.postDelayed(this, 45000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Check if media is currently playing (Prime Video, Netflix, YouTube, Spotify, etc.)
        if (MediaUtils.isMediaPlaying(this)) {
            finish();
            return;
        }

        setInteractive(false);
        setFullscreen(true);
        setContentView(R.layout.screensaver_view);

        contentContainer = findViewById(R.id.screensaver_content);
        TextView tvWeather = findViewById(R.id.sc_weather);
        if (tvWeather != null) {
            tvWeather.setText(WeatherHelper.getCachedWeather(this));
        }

        handler.postDelayed(driftRunnable, 30000);
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(driftRunnable);
        super.onDetachedFromWindow();
    }
}
