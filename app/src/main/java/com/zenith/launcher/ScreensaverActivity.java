package com.zenith.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import java.util.Random;

public class ScreensaverActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private View contentContainer;
    private final Random random = new Random();

    private final Runnable driftRunnable = new Runnable() {
        @Override
        public void run() {
            if (MediaUtils.isMediaPlaying(ScreensaverActivity.this)) {
                finish();
                overridePendingTransition(0, 0);
                return;
            }
            if (contentContainer != null) {
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (MediaUtils.isMediaPlaying(this)) {
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        overridePendingTransition(0, 0);
        setContentView(R.layout.screensaver_view);

        contentContainer = findViewById(R.id.screensaver_content);
        TextView tvWeather = findViewById(R.id.sc_weather);
        if (tvWeather != null) {
            tvWeather.setText(WeatherHelper.getCachedWeather(this));
        }

        handler.postDelayed(driftRunnable, 30000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MediaUtils.isMediaPlaying(this)) {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            finish();
            overridePendingTransition(0, 0);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(driftRunnable);
        super.onDestroy();
    }
}
