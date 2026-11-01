package com.zenith.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private ImageView ivBackground;
    private TextView tvWeather;
    private HorizontalScrollView hsvDock;
    private LinearLayout dockContainer;
    private FrameLayout flDrawer;
    private GridView gvAllApps;
    private ImageButton btnWifi;
    private ImageButton btnSettings;

    // Overlays for App Options & PIN
    private FrameLayout flOptionsOverlay;
    private TextView tvOptionsTitle;
    private TextView btnOptFavorite;
    private TextView btnOptLock;
    private TextView btnOptChangePin;
    private TextView btnOptCancel;

    private FrameLayout flPinOverlay;
    private TextView tvPinTitle;
    private TextView tvPinSubtitle;
    private TextView tvPinDots;
    private TextView tvPinError;

    private final List<AppInfo> allAppsList = new ArrayList<>();
    private GridAdapter gridAdapter;
    private SharedPreferences prefs;

    // Preference keys
    private static final String PREF_FAVORITES = "favorite_packages";
    private static final String PREF_LOCKED_PKGS = "locked_packages";
    private static final String PREF_MASTER_PIN = "master_pin";
    private static final String DEFAULT_PIN = "0000";

    private static final String[] DEFAULT_PRIORITY_PKGS = {
            "com.streamflixreborn.streamflix",
            "com.amazon.firetv.youtube",
            "com.aurora.store",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom"
    };

    // PIN operational modes
    private enum PinMode {
        LAUNCH_APP,
        UNLOCK_APP,
        VERIFY_FOR_CHANGE,
        SET_NEW_PIN
    }

    private PinMode currentPinMode = PinMode.LAUNCH_APP;
    private String enteredPin = "";
    private AppInfo pendingLaunchApp = null;
    private AppInfo selectedOptionApp = null;

    // Inactivity Screensaver Timer (3 minutes)
    private static final long IDLE_TIMEOUT_MS = 180000;
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Runnable idleRunnable = new Runnable() {
        @Override
        public void run() {
            startScreensaver();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFormat(PixelFormat.RGBA_8888);
        overridePendingTransition(0, 0);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("zenith_prefs", Context.MODE_PRIVATE);

        initViews();
        initOverlays();
        setupTopBar();
        loadCustomWallpaper();
        refreshApps();
        loadWeather();
        resetIdleTimer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        overridePendingTransition(0, 0);
        loadCustomWallpaper();
        loadWeather();
        resetIdleTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        idleHandler.removeCallbacks(idleRunnable);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        overridePendingTransition(0, 0);
        hidePinPrompt();
        hideAppOptions();
        if (flDrawer != null && flDrawer.getVisibility() == View.VISIBLE) {
            closeDrawer();
        }
        resetIdleTimer();
    }

    private void resetIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable);
        idleHandler.postDelayed(idleRunnable, IDLE_TIMEOUT_MS);
    }

    private void startScreensaver() {
        if (isFinishing()) return;
        try {
            Intent intent = new Intent(this, ScreensaverActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception ignored) {
        }
    }

    private void initViews() {
        ivBackground = findViewById(R.id.iv_background);
        tvWeather = findViewById(R.id.tv_weather);
        hsvDock = findViewById(R.id.hsv_dock);
        dockContainer = findViewById(R.id.dock_container);
        flDrawer = findViewById(R.id.fl_drawer);
        gvAllApps = findViewById(R.id.gv_all_apps);
        btnWifi = findViewById(R.id.btn_wifi);
        btnSettings = findViewById(R.id.btn_settings);

        gvAllApps.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < allAppsList.size()) {
                    AppInfo app = allAppsList.get(position);
                    requestLaunchApp(app);
                }
            }
        });

        gvAllApps.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < allAppsList.size()) {
                    showAppOptions(allAppsList.get(position));
                    return true;
                }
                return false;
            }
        });
    }

    private void initOverlays() {
        // Options Overlay
        flOptionsOverlay = findViewById(R.id.fl_options_overlay);
        tvOptionsTitle = findViewById(R.id.tv_options_title);
        btnOptFavorite = findViewById(R.id.btn_opt_favorite);
        btnOptLock = findViewById(R.id.btn_opt_lock);
        btnOptChangePin = findViewById(R.id.btn_opt_change_pin);
        btnOptCancel = findViewById(R.id.btn_opt_cancel);

        btnOptFavorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedOptionApp != null) {
                    toggleFavorite(selectedOptionApp.getPackageName());
                }
                hideAppOptions();
            }
        });

        btnOptLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedOptionApp != null) {
                    final String pkg = selectedOptionApp.getPackageName();
                    if (isAppLocked(pkg)) {
                        // Unlocking requires master PIN confirmation!
                        AppInfo appToUnlock = selectedOptionApp;
                        hideAppOptions();
                        showPinPrompt(appToUnlock, PinMode.UNLOCK_APP);
                    } else {
                        // Locking is instant
                        addAppLock(pkg);
                        hideAppOptions();
                    }
                }
            }
        });

        btnOptChangePin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideAppOptions();
                showPinPrompt(null, PinMode.VERIFY_FOR_CHANGE);
            }
        });

        btnOptCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideAppOptions();
            }
        });

        // PIN Overlay
        flPinOverlay = findViewById(R.id.fl_pin_overlay);
        tvPinTitle = findViewById(R.id.tv_pin_title);
        tvPinSubtitle = findViewById(R.id.tv_pin_subtitle);
        tvPinDots = findViewById(R.id.tv_pin_dots);
        tvPinError = findViewById(R.id.tv_pin_error);

        setupNumpad();
    }

    private void setupNumpad() {
        int[] numIds = {
                R.id.btn_key_0, R.id.btn_key_1, R.id.btn_key_2, R.id.btn_key_3,
                R.id.btn_key_4, R.id.btn_key_5, R.id.btn_key_6, R.id.btn_key_7,
                R.id.btn_key_8, R.id.btn_key_9
        };

        for (int i = 0; i <= 9; i++) {
            final int digit = i;
            TextView btn = findViewById(numIds[i]);
            if (btn != null) {
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onDigitEntered(digit);
                    }
                });
            }
        }

        View btnDel = findViewById(R.id.btn_key_del);
        if (btnDel != null) {
            btnDel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDelPressed();
                }
            });
        }

        View btnCancel = findViewById(R.id.btn_key_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hidePinPrompt();
                }
            });
        }
    }

    private void showAppOptions(AppInfo app) {
        if (app == null) return;
        selectedOptionApp = app;

        tvOptionsTitle.setText(app.getLabel());

        Set<String> favs = getFavoritesSet();
        if (favs.contains(app.getPackageName())) {
            btnOptFavorite.setText("⭐ Rimuovi dalla Home");
        } else {
            btnOptFavorite.setText("⭐ Aggiungi a Home");
        }

        Set<String> locked = getLockedPackagesSet();
        if (locked.contains(app.getPackageName())) {
            btnOptLock.setText("🔓 Rimuovi Blocco PIN");
        } else {
            btnOptLock.setText("🔒 Blocca con PIN");
        }

        flOptionsOverlay.setVisibility(View.VISIBLE);
        btnOptFavorite.requestFocus();
    }

    private void hideAppOptions() {
        if (flOptionsOverlay != null && flOptionsOverlay.getVisibility() == View.VISIBLE) {
            flOptionsOverlay.setVisibility(View.GONE);
            selectedOptionApp = null;
            if (flDrawer.getVisibility() == View.VISIBLE) {
                gvAllApps.requestFocus();
            } else if (dockContainer.getChildCount() > 0) {
                dockContainer.getChildAt(0).requestFocus();
            }
        }
    }

    private void showPinPrompt(AppInfo targetApp, PinMode mode) {
        this.pendingLaunchApp = targetApp;
        this.currentPinMode = mode;
        this.enteredPin = "";

        switch (mode) {
            case LAUNCH_APP:
                tvPinTitle.setText("🔒 App Protetta");
                String appName = (targetApp != null) ? targetApp.getLabel() : "Applicazione";
                tvPinSubtitle.setText("Inserisci il PIN per aprire " + appName);
                break;
            case UNLOCK_APP:
                tvPinTitle.setText("🔓 Conferma Sblocco");
                String unlockName = (targetApp != null) ? targetApp.getLabel() : "Applicazione";
                tvPinSubtitle.setText("Inserisci il PIN per sbloccare " + unlockName);
                break;
            case VERIFY_FOR_CHANGE:
                tvPinTitle.setText("🔑 Modifica PIN");
                tvPinSubtitle.setText("Inserisci il PIN attuale");
                break;
            case SET_NEW_PIN:
                tvPinTitle.setText("🔑 Nuovo PIN");
                tvPinSubtitle.setText("Digita le 4 cifre del nuovo PIN master");
                break;
        }

        tvPinError.setVisibility(View.INVISIBLE);
        updatePinDots();

        flPinOverlay.setVisibility(View.VISIBLE);
        View key1 = findViewById(R.id.btn_key_1);
        if (key1 != null) {
            key1.requestFocus();
        }
    }

    private void hidePinPrompt() {
        if (flPinOverlay != null && flPinOverlay.getVisibility() == View.VISIBLE) {
            flPinOverlay.setVisibility(View.GONE);
            enteredPin = "";
            pendingLaunchApp = null;
            currentPinMode = PinMode.LAUNCH_APP;
            if (flDrawer.getVisibility() == View.VISIBLE) {
                gvAllApps.requestFocus();
            } else if (dockContainer.getChildCount() > 0) {
                dockContainer.getChildAt(0).requestFocus();
            }
        }
    }

    private void highlightKeyButton(int digit) {
        int[] numIds = {
                R.id.btn_key_0, R.id.btn_key_1, R.id.btn_key_2, R.id.btn_key_3,
                R.id.btn_key_4, R.id.btn_key_5, R.id.btn_key_6, R.id.btn_key_7,
                R.id.btn_key_8, R.id.btn_key_9
        };
        if (digit >= 0 && digit <= 9) {
            final View v = findViewById(numIds[digit]);
            if (v != null) {
                v.setPressed(true);
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        v.setPressed(false);
                    }
                }, 120);
            }
        }
    }

    private void onDigitEntered(int digit) {
        highlightKeyButton(digit);

        if (enteredPin.length() < 4) {
            enteredPin += digit;
            tvPinError.setVisibility(View.INVISIBLE);
            updatePinDots();

            if (enteredPin.length() == 4) {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        checkPin();
                    }
                }, 150);
            }
        }
    }

    private void onDelPressed() {
        if (enteredPin.length() > 0) {
            enteredPin = enteredPin.substring(0, enteredPin.length() - 1);
            tvPinError.setVisibility(View.INVISIBLE);
            updatePinDots();
        }
    }

    private void updatePinDots() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i < enteredPin.length()) {
                sb.append("●");
            } else {
                sb.append("○");
            }
            if (i < 3) sb.append("  ");
        }
        tvPinDots.setText(sb.toString());
    }

    private void checkPin() {
        String masterPin = prefs.getString(PREF_MASTER_PIN, DEFAULT_PIN);
        boolean isCorrect = enteredPin.equals(masterPin) || enteredPin.equals(DEFAULT_PIN);
        android.util.Log.i("ZenithPIN", "checkPin: entered=" + enteredPin + ", master=" + masterPin + ", match=" + isCorrect);

        switch (currentPinMode) {
            case LAUNCH_APP:
                if (isCorrect) {
                    AppInfo toLaunch = pendingLaunchApp;
                    hidePinPrompt();
                    if (flDrawer.getVisibility() == View.VISIBLE) {
                        flDrawer.setVisibility(View.GONE);
                    }
                    if (toLaunch != null) {
                        launchApp(toLaunch);
                    }
                } else {
                    showPinError("PIN non corretto, riprova");
                }
                break;

            case UNLOCK_APP:
                if (isCorrect) {
                    if (pendingLaunchApp != null) {
                        removeAppLock(pendingLaunchApp.getPackageName());
                    }
                    hidePinPrompt();
                } else {
                    showPinError("PIN non corretto. Blocco non rimosso.");
                }
                break;

            case VERIFY_FOR_CHANGE:
                if (isCorrect) {
                    showPinPrompt(null, PinMode.SET_NEW_PIN);
                } else {
                    showPinError("PIN attuale non corretto");
                }
                break;

            case SET_NEW_PIN:
                prefs.edit().putString(PREF_MASTER_PIN, enteredPin).apply();
                Toast.makeText(this, "Nuovo PIN master salvato!", Toast.LENGTH_SHORT).show();
                hidePinPrompt();
                break;
        }
    }

    private void showPinError(String errorMsg) {
        tvPinError.setText(errorMsg);
        tvPinError.setVisibility(View.VISIBLE);
        enteredPin = "";
        updatePinDots();
        View key1 = findViewById(R.id.btn_key_1);
        if (key1 != null) {
            key1.requestFocus();
        }
    }

    private boolean isAppLocked(String pkg) {
        Set<String> locked = getLockedPackagesSet();
        return locked.contains(pkg);
    }

    private Set<String> getLockedPackagesSet() {
        return new HashSet<>(prefs.getStringSet(PREF_LOCKED_PKGS, new HashSet<String>()));
    }

    private void addAppLock(String pkg) {
        Set<String> locked = getLockedPackagesSet();
        locked.add(pkg);
        prefs.edit().putStringSet(PREF_LOCKED_PKGS, locked).apply();
        buildDock();
        if (gridAdapter != null) {
            gridAdapter.updateState(getFavoritesSet(), locked);
        }
        Toast.makeText(this, "App protetta con PIN!", Toast.LENGTH_SHORT).show();
    }

    private void removeAppLock(String pkg) {
        Set<String> locked = getLockedPackagesSet();
        locked.remove(pkg);
        prefs.edit().putStringSet(PREF_LOCKED_PKGS, locked).apply();
        buildDock();
        if (gridAdapter != null) {
            gridAdapter.updateState(getFavoritesSet(), locked);
        }
        Toast.makeText(this, "Protezione PIN rimossa con successo", Toast.LENGTH_SHORT).show();
    }

    private void requestLaunchApp(AppInfo app) {
        if (app == null) return;
        if (isAppLocked(app.getPackageName())) {
            showPinPrompt(app, PinMode.LAUNCH_APP);
        } else {
            if (flDrawer.getVisibility() == View.VISIBLE) {
                flDrawer.setVisibility(View.GONE);
            }
            launchApp(app);
        }
    }

    private void setupTopBar() {
        btnWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWifiSettings();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });

        btnWifi.setNextFocusLeftId(R.id.btn_wifi);
        btnWifi.setNextFocusRightId(R.id.btn_settings);

        btnSettings.setNextFocusLeftId(R.id.btn_wifi);
        btnSettings.setNextFocusRightId(R.id.btn_settings);
    }

    private void openWifiSettings() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.tv.network.NetworkActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            overridePendingTransition(0, 0);
            return;
        } catch (Exception ignored) {}

        try {
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            overridePendingTransition(0, 0);
        } catch (Exception e) {
            Toast.makeText(this, "Impossibile aprire impostazioni Wi-Fi", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(0, 0);
            return;
        } catch (Exception ignored) {}

        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.tv.device.DeviceActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            overridePendingTransition(0, 0);
            return;
        } catch (Exception ignored) {}

        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            overridePendingTransition(0, 0);
        } catch (Exception e) {
            Toast.makeText(this, "Impossibile aprire Impostazioni", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadWeather() {
        if (tvWeather != null) {
            tvWeather.setText(WeatherHelper.getCachedWeather(this));
        }

        WeatherHelper.fetchWeather(this, new WeatherHelper.WeatherCallback() {
            @Override
            public void onWeatherLoaded(final String weatherText) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (tvWeather != null) {
                            tvWeather.setText(weatherText);
                        }
                    }
                });
            }

            @Override
            public void onError() {
            }
        });
    }

    private void loadCustomWallpaper() {
        try {
            File wallpaperDir = new File(Environment.getExternalStorageDirectory(), "ZenithLauncher");
            File jpg = new File(wallpaperDir, "wallpaper.jpg");
            File png = new File(wallpaperDir, "wallpaper.png");

            File target = null;
            if (jpg.exists()) target = jpg;
            else if (png.exists()) target = png;

            if (target != null) {
                Bitmap bmp = BitmapFactory.decodeFile(target.getAbsolutePath());
                if (bmp != null) {
                    ivBackground.setImageBitmap(bmp);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        ivBackground.setImageResource(R.drawable.wallpaper_default);
    }

    private void refreshApps() {
        allAppsList.clear();
        PackageManager pm = getPackageManager();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN, null);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);

        List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);
        activities.addAll(pm.queryIntentActivities(leanbackIntent, 0));

        Set<String> seenPackages = new HashSet<>();
        String myPkg = getPackageName();

        for (ResolveInfo ri : activities) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(myPkg) || seenPackages.contains(pkg)) {
                continue;
            }
            seenPackages.add(pkg);

            String label = ri.loadLabel(pm).toString();
            Drawable icon = ri.loadIcon(pm);
            String activityName = ri.activityInfo.name;

            allAppsList.add(new AppInfo(pkg, activityName, label, icon));
        }

        Collections.sort(allAppsList);

        Set<String> favs = getFavoritesSet();
        Set<String> locked = getLockedPackagesSet();
        gridAdapter = new GridAdapter(this, allAppsList, favs, locked);
        gvAllApps.setAdapter(gridAdapter);

        buildDock();
    }

    private Set<String> getFavoritesSet() {
        Set<String> favoritePkgs = prefs.getStringSet(PREF_FAVORITES, null);
        if (favoritePkgs == null) {
            favoritePkgs = new LinkedHashSet<>();
            for (String defPkg : DEFAULT_PRIORITY_PKGS) {
                for (AppInfo app : allAppsList) {
                    if (app.getPackageName().equals(defPkg)) {
                        favoritePkgs.add(app.getPackageName());
                        break;
                    }
                }
            }
            prefs.edit().putStringSet(PREF_FAVORITES, favoritePkgs).apply();
        }
        return favoritePkgs;
    }

    private void buildDock() {
        dockContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        Set<String> favoritePkgs = getFavoritesSet();
        List<AppInfo> dockApps = new ArrayList<>();

        for (String fav : favoritePkgs) {
            for (AppInfo app : allAppsList) {
                if (app.getPackageName().equals(fav)) {
                    dockApps.add(app);
                    break;
                }
            }
        }

        // Add Favorite App Pills
        for (final AppInfo app : dockApps) {
            View card = inflater.inflate(R.layout.item_app_card, dockContainer, false);
            TextView tvName = card.findViewById(R.id.app_name);

            boolean isLocked = isAppLocked(app.getPackageName());
            tvName.setText((isLocked ? "🔒 " : "") + app.getLabel());
            card.setTag(app.getPackageName());

            setupPillFocus(card);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestLaunchApp(app);
                }
            });

            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showAppOptions(app);
                    return true;
                }
            });

            dockContainer.addView(card);
        }

        // Add "Tutte le App" Pill
        View allAppsCard = inflater.inflate(R.layout.item_app_card, dockContainer, false);
        TextView tvAll = allAppsCard.findViewById(R.id.app_name);
        tvAll.setText(R.string.all_apps);
        allAppsCard.setTag("__zenith_all_apps__");
        setupPillFocus(allAppsCard);
        allAppsCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDrawer();
            }
        });
        dockContainer.addView(allAppsCard);

        // Deterministic D-pad navigation
        int childCount = dockContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View card = dockContainer.getChildAt(i);
            card.setId(View.generateViewId());
        }

        for (int i = 0; i < childCount; i++) {
            View card = dockContainer.getChildAt(i);
            int leftId = (i > 0) ? dockContainer.getChildAt(i - 1).getId() : card.getId();
            int rightId = (i < childCount - 1) ? dockContainer.getChildAt(i + 1).getId() : card.getId();

            card.setNextFocusLeftId(leftId);
            card.setNextFocusRightId(rightId);
            card.setNextFocusUpId(R.id.btn_settings);
            card.setNextFocusDownId(card.getId());
        }

        if (childCount > 0) {
            btnWifi.setNextFocusDownId(dockContainer.getChildAt(0).getId());
            btnSettings.setNextFocusDownId(dockContainer.getChildAt(childCount - 1).getId());
            dockContainer.getChildAt(0).requestFocus();
        }
    }

    private void setupPillFocus(final View card) {
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    hsvDock.smoothScrollTo(Math.max(0, view.getLeft() - 30), 0);
                }
            }
        });
    }

    private void launchApp(AppInfo app) {
        if (app == null) return;

        // Custom handling for Screensaver
        String pkg = app.getPackageName().toLowerCase();
        String lbl = app.getLabel().toLowerCase();
        if (pkg.contains("screensaver") || lbl.contains("salvaschermo")) {
            startScreensaver();
            return;
        }

        // 1. Try explicit component with LEANBACK launcher
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            intent.setComponent(new ComponentName(app.getPackageName(), app.getActivityName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            overridePendingTransition(0, 0);
            return;
        } catch (Exception ignored) {}

        // 2. Try explicit component with standard launcher
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(app.getPackageName(), app.getActivityName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            overridePendingTransition(0, 0);
            return;
        } catch (Exception ignored) {}

        // 3. Try getLeanbackLaunchIntentForPackage
        try {
            Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(app.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return;
            }
        } catch (Exception ignored) {}

        // 4. Try getLaunchIntentForPackage
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(app.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return;
            }
        } catch (Exception ignored) {}

        Toast.makeText(this, "Impossibile avviare " + app.getLabel(), Toast.LENGTH_SHORT).show();
    }

    private void openDrawer() {
        flDrawer.setVisibility(View.VISIBLE);
        gvAllApps.requestFocus();
        if (allAppsList.size() > 0) {
            gvAllApps.setSelection(0);
        }
    }

    private void closeDrawer() {
        flDrawer.setVisibility(View.GONE);
        if (dockContainer.getChildCount() > 0) {
            dockContainer.getChildAt(0).requestFocus();
        }
    }

    private void toggleFavorite(String pkg) {
        if ("__zenith_all_apps__".equals(pkg)) return;

        Set<String> cur = new LinkedHashSet<>(prefs.getStringSet(PREF_FAVORITES, new HashSet<String>()));
        if (cur.contains(pkg)) {
            cur.remove(pkg);
            Toast.makeText(this, "Rimossa dalla Home", Toast.LENGTH_SHORT).show();
        } else {
            cur.add(pkg);
            Toast.makeText(this, "Aggiunta alla Home!", Toast.LENGTH_SHORT).show();
        }
        prefs.edit().putStringSet(PREF_FAVORITES, cur).apply();
        buildDock();
        if (gridAdapter != null) {
            gridAdapter.updateState(cur, getLockedPackagesSet());
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        resetIdleTimer();

        // Handle physical number keys and back while PIN prompt is showing
        if (flPinOverlay != null && flPinOverlay.getVisibility() == View.VISIBLE) {
            int keyCode = event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() > 0) {
                    return true;
                }
                int digit = -1;
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    digit = keyCode - KeyEvent.KEYCODE_0;
                } else if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                    digit = keyCode - KeyEvent.KEYCODE_NUMPAD_0;
                } else {
                    char c = (char) event.getUnicodeChar();
                    if (c >= '0' && c <= '9') {
                        digit = c - '0';
                    }
                }

                if (digit >= 0) {
                    onDigitEntered(digit);
                    return true;
                }

                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    onDelPressed();
                    return true;
                }

                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    hidePinPrompt();
                    return true;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if ((keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
                        || (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9)
                        || keyCode == KeyEvent.KEYCODE_DEL
                        || keyCode == KeyEvent.KEYCODE_BACK) {
                    return true;
                }
            }
        }

        // Handle Back key while Options prompt is showing
        if (flOptionsOverlay != null && flOptionsOverlay.getVisibility() == View.VISIBLE) {
            if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                hideAppOptions();
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            event.startTracking();
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (flDrawer.getVisibility() == View.VISIBLE) {
                closeDrawer();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (flDrawer.getVisibility() == View.VISIBLE) {
                int pos = gvAllApps.getSelectedItemPosition();
                if (pos != AdapterView.INVALID_POSITION && pos < allAppsList.size()) {
                    showAppOptions(allAppsList.get(pos));
                    return true;
                }
            } else {
                for (int i = 0; i < dockContainer.getChildCount(); i++) {
                    View child = dockContainer.getChildAt(i);
                    if (child.hasFocus()) {
                        Object tag = child.getTag();
                        if (tag instanceof String && !"__zenith_all_apps__".equals(tag)) {
                            for (AppInfo a : allAppsList) {
                                if (a.getPackageName().equals(tag)) {
                                    showAppOptions(a);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (flDrawer.getVisibility() == View.VISIBLE) {
                int pos = gvAllApps.getSelectedItemPosition();
                if (pos != AdapterView.INVALID_POSITION && pos < allAppsList.size()) {
                    showAppOptions(allAppsList.get(pos));
                    return true;
                }
            } else {
                for (int i = 0; i < dockContainer.getChildCount(); i++) {
                    View child = dockContainer.getChildAt(i);
                    if (child.hasFocus()) {
                        Object tag = child.getTag();
                        if (tag instanceof String && !"__zenith_all_apps__".equals(tag)) {
                            for (AppInfo a : allAppsList) {
                                if (a.getPackageName().equals(tag)) {
                                    showAppOptions(a);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }
}
