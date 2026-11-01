package com.zenith.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class HomeInterceptorService extends AccessibilityService {

    private static final String STOCK_LAUNCHER = "com.amazon.tv.launcher";
    private static final String FIRE_HOME_STARTER = "com.amazon.firehomestarter";

    private long lastLaunchTime = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        // ONLY intercept explicit window state changes (not background content updates)
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return;

        String pkgStr = pkg.toString();
        String clsStr = cls.toString();

        // Only redirect if the foreground window specifically belongs to Amazon's stock home screen
        if (STOCK_LAUNCHER.equals(pkgStr) || FIRE_HOME_STARTER.equals(pkgStr)) {
            // Never intercept if the user is inside Fire TV Settings, Preferences, or System Dialogs
            if (clsStr.contains("Settings") || clsStr.contains("Preference") || clsStr.contains("Dialog")) {
                return;
            }

            // Only trigger if Amazon stock Home screen is actively in the foreground
            if (clsStr.contains("HomeActivity") || clsStr.contains("HomeStarter")) {
                long now = System.currentTimeMillis();
                if (now - lastLaunchTime > 500) {
                    lastLaunchTime = now;
                    launchLauncher();
                }
            }
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                launchLauncher();
            }
            return true;
        }
        return super.onKeyEvent(event);
    }

    private void launchLauncher() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED 
                | Intent.FLAG_ACTIVITY_CLEAR_TOP 
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {
    }
}
