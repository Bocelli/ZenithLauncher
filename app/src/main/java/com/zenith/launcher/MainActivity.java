package com.zenith.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private ImageView ivBackground;
    private LinearLayout llMainContainer;
    private LinearLayout llHomeContent;
    private TextClock tcTime;
    private ImageButton btnSettings;
    private ImageButton btnWifi;
    private View vTabIndicator;
    private TextView tabHome;
    private TextView tabApp;

    private TextView tvDateHeader;
    private TextView tvGreeting;
    private TextView tvWeather;

    private HorizontalScrollView hsvFavorites;
    private LinearLayout llFavoritesContainer;
    private HorizontalScrollView hsvInputs;
    private LinearLayout llInputsContainer;

    // App Screen Page
    private View flDrawer;
    private GridView gvAllApps;

    // Overlays
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

    private FrameLayout flSettingsOverlay;
    private TextView btnSetScreensaverTimeout;
    private TextView btnSetScreensaverTest;
    private TextView btnSetChangePin;
    private TextView btnSetFireTv;
    private TextView btnSetClose;

    // Rename Input Overlay
    private FrameLayout flRenameOverlay;
    private TextView tvRenameTarget;
    private EditText etInputTitle;
    private EditText etInputSubtitle;
    private TextView btnIconHdmi;
    private TextView btnIconGamepad;
    private TextView btnIconPc;
    private TextView btnIconTv;
    private TextView btnSaveInput;
    private TextView btnCancelInput;
    private TvInputItem currentRenameItem = null;
    private String selectedRenameIcon = "hdmi";

    private final List<AppInfo> allAppsList = new ArrayList<>();
    private GridAdapter gridAdapter;
    private SharedPreferences prefs;

    // Preference keys
    private static final String PREF_FAVORITES = "favorite_packages";
    private static final String PREF_LOCKED_PKGS = "locked_packages";
    private static final String PREF_MASTER_PIN = "master_pin";
    private static final String DEFAULT_PIN = "0000";

    // Screensaver timeout
    private static final String PREF_SCREENSAVER_TIMEOUT_SEC = "screensaver_timeout_sec";
    private static final int DEFAULT_TIMEOUT_SEC = 180;
    private static final int[] TIMEOUT_OPTIONS = {60, 180, 300, 600, 0};
    private static final String[] TIMEOUT_LABELS = {"1 minuto", "3 minuti", "5 minuti", "10 minuti", "Disattivato (Mai)"};

    private static final String[] DEFAULT_ORDERED_FAVS = {
            "com.netflix.ninja",
            "com.amazon.firetv.youtube",
            "com.disney.disneyplus",
            "com.amazon.firebat",
            "com.streamflixreborn.streamflix",
            "com.aurora.store"
    };

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
        updateGreetingAndDate();
        loadCustomWallpaper();
        refreshApps();
        buildInputs();
        loadWeather();
        resetIdleTimer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        overridePendingTransition(0, 0);
        updateGreetingAndDate();
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
        hideZenithSettings();
        hideRenameInputDialog();
        showHomeDashboard();
        resetIdleTimer();
    }

    private void resetIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable);
        int timeoutSec = getScreensaverTimeoutSec();
        if (timeoutSec > 0) {
            idleHandler.postDelayed(idleRunnable, timeoutSec * 1000L);
        }
    }

    private void startScreensaver() {
        if (isFinishing()) return;
        int timeoutSec = getScreensaverTimeoutSec();
        if (timeoutSec <= 0) return; // Disabled

        // Check active media playback
        if (MediaUtils.isMediaPlaying(this)) {
            resetIdleTimer();
            return;
        }

        startScreensaverActivity();
    }

    private void startScreensaverActivity() {
        try {
            Intent intent = new Intent(this, ScreensaverActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception ignored) {}
    }

    private void setOverlayModal(boolean isModal) {
        if (llMainContainer != null) {
            llMainContainer.setDescendantFocusability(
                    isModal ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
    }

    private void initViews() {
        ivBackground = findViewById(R.id.iv_background);
        llMainContainer = findViewById(R.id.ll_main_container);
        llHomeContent = findViewById(R.id.ll_home_content);

        btnSettings = findViewById(R.id.btn_settings);
        btnWifi = findViewById(R.id.btn_wifi);
        vTabIndicator = findViewById(R.id.v_tab_indicator);
        tabHome = findViewById(R.id.tab_home);
        tabApp = findViewById(R.id.tab_app);

        tvDateHeader = findViewById(R.id.tv_date_header);
        tvGreeting = findViewById(R.id.tv_greeting);
        tvWeather = findViewById(R.id.tv_weather);

        hsvFavorites = findViewById(R.id.hsv_favorites);
        llFavoritesContainer = findViewById(R.id.ll_favorites_container);
        hsvInputs = findViewById(R.id.hsv_inputs);
        llInputsContainer = findViewById(R.id.ll_inputs_container);

        flDrawer = findViewById(R.id.fl_drawer);
        gvAllApps = findViewById(R.id.gv_all_apps);

        gvAllApps.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < allAppsList.size()) {
                    requestLaunchApp(allAppsList.get(position));
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

        gvAllApps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private View lastSelected = null;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (lastSelected != null && lastSelected != view) {
                    lastSelected.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
                if (view != null) {
                    view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
                    lastSelected = view;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (lastSelected != null) {
                    lastSelected.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                    lastSelected = null;
                }
            }
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void updateTabIndicator(final boolean isAppTab, final boolean animate) {
        if (vTabIndicator == null || tabHome == null || tabApp == null) return;
        final TextView activeTab = isAppTab ? tabApp : tabHome;
        final TextView inactiveTab = isAppTab ? tabHome : tabApp;

        activeTab.post(new Runnable() {
            @Override
            public void run() {
                float targetX = isAppTab ? (tabHome.getWidth() + dpToPx(4)) : 0f;
                if (animate) {
                    vTabIndicator.animate()
                            .translationX(targetX)
                            .setDuration(200)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .start();
                } else {
                    vTabIndicator.setTranslationX(targetX);
                }
                activeTab.setTextColor(0xFF0E121E);
                inactiveTab.setTextColor(0xB0FFFFFF);
            }
        });
    }

    private void applyRoundedCorners(View view, final float radiusDp) {
        if (view == null) return;
        final float radiusPx = radiusDp * getResources().getDisplayMetrics().density;
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radiusPx);
            }
        });
        view.setClipToOutline(true);
    }

    private void setupTopBar() {
        if (btnSettings != null) {
            btnSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showZenithSettings();
                }
            });
        }

        if (btnWifi != null) {
            btnWifi.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openWifiSettings();
                }
            });
        }

        if (tabHome != null) {
            tabHome.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showHomeDashboard();
                }
            });
            tabHome.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        updateTabIndicator(false, true);
                        if (flDrawer != null && flDrawer.getVisibility() == View.VISIBLE) {
                            showHomeDashboard();
                            tabHome.requestFocus();
                        }
                    }
                }
            });
        }

        if (tabApp != null) {
            tabApp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAllAppsDrawer();
                }
            });
            tabApp.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        updateTabIndicator(true, true);
                        if (flDrawer != null && flDrawer.getVisibility() != View.VISIBLE) {
                            showAllAppsDrawer();
                            tabApp.requestFocus();
                        }
                    }
                }
            });
        }

        // Top bar D-Pad focus chaining
        btnSettings.setNextFocusLeftId(R.id.btn_settings);
        btnSettings.setNextFocusRightId(R.id.btn_wifi);
        btnSettings.setNextFocusDownId(R.id.hsv_favorites);

        btnWifi.setNextFocusLeftId(R.id.btn_settings);
        btnWifi.setNextFocusRightId(R.id.tab_home);
        btnWifi.setNextFocusDownId(R.id.hsv_favorites);

        tabHome.setNextFocusLeftId(R.id.btn_wifi);
        tabHome.setNextFocusRightId(R.id.tab_app);
        tabHome.setNextFocusDownId(R.id.hsv_favorites);

        tabApp.setNextFocusLeftId(R.id.tab_home);
        tabApp.setNextFocusRightId(R.id.tab_app);
        tabApp.setNextFocusDownId(R.id.gv_all_apps);

        if (gvAllApps != null) {
            gvAllApps.setNextFocusUpId(R.id.tab_app);
        }

        updateTabIndicator(false, false);
    }

    private void showHomeDashboard() {
        if (flDrawer != null) flDrawer.setVisibility(View.GONE);
        if (llHomeContent != null) llHomeContent.setVisibility(View.VISIBLE);

        updateTabIndicator(false, true);

        if (llFavoritesContainer != null && llFavoritesContainer.getChildCount() > 0) {
            llFavoritesContainer.getChildAt(0).requestFocus();
        }
    }

    private void showAllAppsDrawer() {
        if (llHomeContent != null) llHomeContent.setVisibility(View.GONE);
        if (flDrawer != null) {
            flDrawer.setVisibility(View.VISIBLE);
            if (gvAllApps != null) {
                gvAllApps.requestFocus();
                gvAllApps.setSelection(0);
            }
        }
        updateTabIndicator(true, true);
    }

    private void updateGreetingAndDate() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN);
            if (tvDateHeader != null) {
                tvDateHeader.setText(sdf.format(new Date()).toUpperCase(Locale.ITALIAN));
            }
        } catch (Exception ignored) {}

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour >= 5 && hour < 13) {
            greeting = "Buongiorno.";
        } else if (hour >= 13 && hour < 18) {
            greeting = "Buon pomeriggio.";
        } else if (hour >= 18 && hour < 23) {
            greeting = "Buonasera.";
        } else {
            greeting = "Buonanotte.";
        }
        if (tvGreeting != null) {
            tvGreeting.setText(greeting);
        }
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
                        AppInfo appToUnlock = selectedOptionApp;
                        hideAppOptions();
                        showPinPrompt(appToUnlock, PinMode.UNLOCK_APP);
                    } else {
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

        // Settings Overlay
        flSettingsOverlay = findViewById(R.id.fl_settings_overlay);
        btnSetScreensaverTimeout = findViewById(R.id.btn_set_screensaver_timeout);
        btnSetScreensaverTest = findViewById(R.id.btn_set_screensaver_test);
        btnSetChangePin = findViewById(R.id.btn_set_change_pin);
        btnSetFireTv = findViewById(R.id.btn_set_firetv);
        btnSetClose = findViewById(R.id.btn_set_close);

        btnSetScreensaverTimeout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleScreensaverTimeout();
            }
        });

        btnSetScreensaverTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideZenithSettings();
                startScreensaverActivity();
            }
        });

        btnSetChangePin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideZenithSettings();
                showPinPrompt(null, PinMode.VERIFY_FOR_CHANGE);
            }
        });

        btnSetFireTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideZenithSettings();
                openSettings();
            }
        });

        btnSetClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideZenithSettings();
            }
        });

        // Rename Input Overlay
        flRenameOverlay = findViewById(R.id.fl_rename_overlay);
        tvRenameTarget = findViewById(R.id.tv_rename_target);
        etInputTitle = findViewById(R.id.et_input_title);
        etInputSubtitle = findViewById(R.id.et_input_subtitle);
        btnIconHdmi = findViewById(R.id.btn_icon_hdmi);
        btnIconGamepad = findViewById(R.id.btn_icon_gamepad);
        btnIconPc = findViewById(R.id.btn_icon_pc);
        btnIconTv = findViewById(R.id.btn_icon_tv);
        btnSaveInput = findViewById(R.id.btn_save_input);
        btnCancelInput = findViewById(R.id.btn_cancel_input);

        setupRenameDialogListeners();
    }

    private void showSoftKeyboard(final View view) {
        if (view == null) return;
        view.requestFocus();
        view.post(new Runnable() {
            @Override
            public void run() {
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(view, InputMethodManager.SHOW_FORCED);
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void hideSoftKeyboard(View view) {
        if (view == null) return;
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        } catch (Exception ignored) {}
    }

    private void setupRenameDialogListeners() {
        if (btnIconHdmi != null) {
            btnIconHdmi.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectRenameIcon("hdmi");
                }
            });
        }
        if (btnIconGamepad != null) {
            btnIconGamepad.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectRenameIcon("gamepad");
                }
            });
        }
        if (btnIconPc != null) {
            btnIconPc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectRenameIcon("pc");
                }
            });
        }
        if (btnIconTv != null) {
            btnIconTv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectRenameIcon("tv");
                }
            });
        }

        if (btnSaveInput != null) {
            btnSaveInput.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveInputRename();
                }
            });
        }

        if (btnCancelInput != null) {
            btnCancelInput.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideRenameInputDialog();
                }
            });
        }

        // Soft keyboard & editor actions
        if (etInputTitle != null) {
            etInputTitle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSoftKeyboard(etInputTitle);
                }
            });
            etInputTitle.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                        if (etInputSubtitle != null) {
                            etInputSubtitle.requestFocus();
                            showSoftKeyboard(etInputSubtitle);
                        }
                        return true;
                    }
                    return false;
                }
            });
        }

        if (etInputSubtitle != null) {
            etInputSubtitle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSoftKeyboard(etInputSubtitle);
                }
            });
            etInputSubtitle.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        hideSoftKeyboard(v);
                        if (btnSaveInput != null) {
                            btnSaveInput.requestFocus();
                        }
                        return true;
                    }
                    return false;
                }
            });
        }

        View.OnKeyListener editTextDpadListener = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (v == etInputTitle) {
                            if (etInputSubtitle != null) etInputSubtitle.requestFocus();
                            return true;
                        } else if (v == etInputSubtitle) {
                            if (btnIconGamepad != null && btnIconGamepad.isSelected()) {
                                btnIconGamepad.requestFocus();
                            } else if (btnIconPc != null && btnIconPc.isSelected()) {
                                btnIconPc.requestFocus();
                            } else if (btnIconTv != null && btnIconTv.isSelected()) {
                                btnIconTv.requestFocus();
                            } else if (btnIconHdmi != null) {
                                btnIconHdmi.requestFocus();
                            }
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (v == etInputSubtitle) {
                            if (etInputTitle != null) etInputTitle.requestFocus();
                            return true;
                        } else if (v == etInputTitle) {
                            return true; // Keep bounded inside dialog
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                        showSoftKeyboard(v);
                        return false;
                    }
                }
                return false;
            }
        };

        if (etInputTitle != null) etInputTitle.setOnKeyListener(editTextDpadListener);
        if (etInputSubtitle != null) etInputSubtitle.setOnKeyListener(editTextDpadListener);

        // Strict focus containment inside dialog
        if (etInputTitle != null) {
            etInputTitle.setNextFocusUpId(R.id.et_input_title);
            etInputTitle.setNextFocusDownId(R.id.et_input_subtitle);
            etInputTitle.setNextFocusLeftId(R.id.et_input_title);
            etInputTitle.setNextFocusRightId(R.id.et_input_title);
        }
        if (etInputSubtitle != null) {
            etInputSubtitle.setNextFocusUpId(R.id.et_input_title);
            etInputSubtitle.setNextFocusDownId(R.id.btn_icon_hdmi);
            etInputSubtitle.setNextFocusLeftId(R.id.et_input_subtitle);
            etInputSubtitle.setNextFocusRightId(R.id.et_input_subtitle);
        }
        if (btnIconHdmi != null) {
            btnIconHdmi.setNextFocusUpId(R.id.et_input_subtitle);
            btnIconHdmi.setNextFocusLeftId(R.id.btn_icon_hdmi);
            btnIconHdmi.setNextFocusRightId(R.id.btn_icon_gamepad);
            btnIconHdmi.setNextFocusDownId(R.id.btn_save_input);
        }
        if (btnIconGamepad != null) {
            btnIconGamepad.setNextFocusUpId(R.id.et_input_subtitle);
            btnIconGamepad.setNextFocusLeftId(R.id.btn_icon_hdmi);
            btnIconGamepad.setNextFocusRightId(R.id.btn_icon_pc);
            btnIconGamepad.setNextFocusDownId(R.id.btn_save_input);
        }
        if (btnIconPc != null) {
            btnIconPc.setNextFocusUpId(R.id.et_input_subtitle);
            btnIconPc.setNextFocusLeftId(R.id.btn_icon_gamepad);
            btnIconPc.setNextFocusRightId(R.id.btn_icon_tv);
            btnIconPc.setNextFocusDownId(R.id.btn_cancel_input);
        }
        if (btnIconTv != null) {
            btnIconTv.setNextFocusUpId(R.id.et_input_subtitle);
            btnIconTv.setNextFocusLeftId(R.id.btn_icon_pc);
            btnIconTv.setNextFocusRightId(R.id.btn_icon_tv);
            btnIconTv.setNextFocusDownId(R.id.btn_cancel_input);
        }
        if (btnSaveInput != null) {
            btnSaveInput.setNextFocusUpId(R.id.btn_icon_hdmi);
            btnSaveInput.setNextFocusLeftId(R.id.btn_save_input);
            btnSaveInput.setNextFocusRightId(R.id.btn_cancel_input);
            btnSaveInput.setNextFocusDownId(R.id.btn_save_input);
        }
        if (btnCancelInput != null) {
            btnCancelInput.setNextFocusUpId(R.id.btn_icon_pc);
            btnCancelInput.setNextFocusLeftId(R.id.btn_save_input);
            btnCancelInput.setNextFocusRightId(R.id.btn_cancel_input);
            btnCancelInput.setNextFocusDownId(R.id.btn_cancel_input);
        }
    }

    private void selectRenameIcon(String iconType) {
        selectedRenameIcon = iconType;
        if (btnIconHdmi != null) btnIconHdmi.setSelected("hdmi".equals(iconType));
        if (btnIconGamepad != null) btnIconGamepad.setSelected("gamepad".equals(iconType));
        if (btnIconPc != null) btnIconPc.setSelected("pc".equals(iconType));
        if (btnIconTv != null) btnIconTv.setSelected("tv".equals(iconType));
    }

    private void showRenameInputDialog(TvInputItem item) {
        if (item == null) return;
        currentRenameItem = item;
        selectedRenameIcon = item.iconType;

        if (tvRenameTarget != null) {
            tvRenameTarget.setText("Modifica porta HDMI " + item.id);
        }
        if (etInputTitle != null) {
            etInputTitle.setText(item.title);
            etInputTitle.setSelection(item.title.length());
        }
        if (etInputSubtitle != null) {
            etInputSubtitle.setText(item.subtitle);
            etInputSubtitle.setSelection(item.subtitle.length());
        }
        selectRenameIcon(item.iconType);

        if (flRenameOverlay != null) {
            setOverlayModal(true);
            flRenameOverlay.setVisibility(View.VISIBLE);
            if (etInputTitle != null) {
                etInputTitle.requestFocus();
            }
        }
    }

    private void hideRenameInputDialog() {
        if (flRenameOverlay != null && flRenameOverlay.getVisibility() == View.VISIBLE) {
            hideSoftKeyboard(etInputTitle);
            hideSoftKeyboard(etInputSubtitle);
            flRenameOverlay.setVisibility(View.GONE);
            currentRenameItem = null;
            setOverlayModal(false);
            if (llInputsContainer != null && llInputsContainer.getChildCount() > 0) {
                llInputsContainer.getChildAt(0).requestFocus();
            }
        }
    }

    private void saveInputRename() {
        if (currentRenameItem == null) return;
        String newTitle = etInputTitle.getText().toString().trim();
        String newSubtitle = etInputSubtitle.getText().toString().trim();
        if (newTitle.isEmpty()) newTitle = "HDMI " + currentRenameItem.id;

        int targetIdx = currentRenameItem.id - 1;

        prefs.edit()
                .putString("input_" + currentRenameItem.id + "_title", newTitle)
                .putString("input_" + currentRenameItem.id + "_sub", newSubtitle)
                .putString("input_" + currentRenameItem.id + "_icon", selectedRenameIcon)
                .apply();

        hideRenameInputDialog();
        buildInputs();
        if (llInputsContainer != null && targetIdx >= 0 && targetIdx < llInputsContainer.getChildCount()) {
            llInputsContainer.getChildAt(targetIdx).requestFocus();
        }
        Toast.makeText(this, "Ingresso aggiornato!", Toast.LENGTH_SHORT).show();
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
            btnOptFavorite.setText("⭐ Rimuovi dai Preferiti");
        } else {
            btnOptFavorite.setText("⭐ Aggiungi ai Preferiti");
        }

        Set<String> locked = getLockedPackagesSet();
        if (locked.contains(app.getPackageName())) {
            btnOptLock.setText("🔓 Rimuovi Blocco PIN");
        } else {
            btnOptLock.setText("🔒 Blocca con PIN");
        }

        setOverlayModal(true);
        flOptionsOverlay.setVisibility(View.VISIBLE);
        btnOptFavorite.requestFocus();
    }

    private void hideAppOptions() {
        if (flOptionsOverlay != null && flOptionsOverlay.getVisibility() == View.VISIBLE) {
            flOptionsOverlay.setVisibility(View.GONE);
            selectedOptionApp = null;
            setOverlayModal(false);
            if (flDrawer.getVisibility() == View.VISIBLE) {
                gvAllApps.requestFocus();
            } else if (llFavoritesContainer.getChildCount() > 0) {
                llFavoritesContainer.getChildAt(0).requestFocus();
            }
        }
    }

    private void showZenithSettings() {
        updateScreensaverTimeoutLabel();
        setOverlayModal(true);
        flSettingsOverlay.setVisibility(View.VISIBLE);
        btnSetScreensaverTimeout.requestFocus();
    }

    private void hideZenithSettings() {
        if (flSettingsOverlay != null && flSettingsOverlay.getVisibility() == View.VISIBLE) {
            flSettingsOverlay.setVisibility(View.GONE);
            setOverlayModal(false);
            btnSettings.requestFocus();
        }
    }

    private int getScreensaverTimeoutSec() {
        return prefs.getInt(PREF_SCREENSAVER_TIMEOUT_SEC, DEFAULT_TIMEOUT_SEC);
    }

    private void updateScreensaverTimeoutLabel() {
        int cur = getScreensaverTimeoutSec();
        String label = "Disattivato (Mai)";
        for (int i = 0; i < TIMEOUT_OPTIONS.length; i++) {
            if (TIMEOUT_OPTIONS[i] == cur) {
                label = TIMEOUT_LABELS[i];
                break;
            }
        }
        if (btnSetScreensaverTimeout != null) {
            btnSetScreensaverTimeout.setText("🌙 Salvaschermo: " + label);
        }
    }

    private void cycleScreensaverTimeout() {
        int cur = getScreensaverTimeoutSec();
        int nextIndex = 0;
        for (int i = 0; i < TIMEOUT_OPTIONS.length; i++) {
            if (TIMEOUT_OPTIONS[i] == cur) {
                nextIndex = (i + 1) % TIMEOUT_OPTIONS.length;
                break;
            }
        }
        int nextTimeout = TIMEOUT_OPTIONS[nextIndex];
        prefs.edit().putInt(PREF_SCREENSAVER_TIMEOUT_SEC, nextTimeout).apply();
        updateScreensaverTimeoutLabel();
        resetIdleTimer();
        Toast.makeText(this, "Salvaschermo: " + TIMEOUT_LABELS[nextIndex], Toast.LENGTH_SHORT).show();
    }

    private void showPinPrompt(AppInfo targetApp, PinMode mode) {
        this.pendingLaunchApp = targetApp;
        this.currentPinMode = mode;
        this.enteredPin = "";
        tvPinError.setVisibility(View.INVISIBLE);
        updatePinDots();

        switch (mode) {
            case LAUNCH_APP:
                tvPinTitle.setText("🔒 Inserisci PIN");
                tvPinSubtitle.setText(targetApp != null ? "Inserisci il PIN per aprire " + targetApp.getLabel() : "Inserisci il PIN");
                break;
            case UNLOCK_APP:
                tvPinTitle.setText("🔓 Sblocco Applicazione");
                tvPinSubtitle.setText("Inserisci il PIN Master per rimuovere la protezione");
                break;
            case VERIFY_FOR_CHANGE:
                tvPinTitle.setText("🔑 Modifica PIN");
                tvPinSubtitle.setText("Inserisci il PIN Master attuale per proseguire");
                break;
            case SET_NEW_PIN:
                tvPinTitle.setText("✨ Nuovo PIN");
                tvPinSubtitle.setText("Inserisci il nuovo PIN a 4 cifre");
                break;
        }

        setOverlayModal(true);
        flPinOverlay.setVisibility(View.VISIBLE);
        View key5 = findViewById(R.id.btn_key_5);
        if (key5 != null) key5.requestFocus();
    }

    private void hidePinPrompt() {
        if (flPinOverlay != null && flPinOverlay.getVisibility() == View.VISIBLE) {
            flPinOverlay.setVisibility(View.GONE);
            enteredPin = "";
            pendingLaunchApp = null;
            setOverlayModal(false);
            if (flDrawer.getVisibility() == View.VISIBLE) {
                gvAllApps.requestFocus();
            } else if (llFavoritesContainer.getChildCount() > 0) {
                llFavoritesContainer.getChildAt(0).requestFocus();
            }
        }
    }

    private void onDigitEntered(int digit) {
        if (enteredPin.length() < 4) {
            enteredPin += digit;
            updatePinDots();
            tvPinError.setVisibility(View.INVISIBLE);

            if (enteredPin.length() == 4) {
                handlePinSubmit(enteredPin);
            }
        }
    }

    private void onDelPressed() {
        if (enteredPin.length() > 0) {
            enteredPin = enteredPin.substring(0, enteredPin.length() - 1);
            updatePinDots();
            tvPinError.setVisibility(View.INVISIBLE);
        }
    }

    private void updatePinDots() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i < enteredPin.length()) {
                sb.append(" ● ");
            } else {
                sb.append(" ○ ");
            }
        }
        tvPinDots.setText(sb.toString().trim());
    }

    private void handlePinSubmit(String pin) {
        String masterPin = prefs.getString(PREF_MASTER_PIN, DEFAULT_PIN);

        if (currentPinMode == PinMode.LAUNCH_APP) {
            if (checkPin(pin, masterPin)) {
                AppInfo appToLaunch = pendingLaunchApp;
                hidePinPrompt();
                if (appToLaunch != null) {
                    if (flDrawer.getVisibility() == View.VISIBLE) {
                        flDrawer.setVisibility(View.GONE);
                    }
                    launchApp(appToLaunch);
                }
            } else {
                showPinError("PIN errato!");
            }
        } else if (currentPinMode == PinMode.UNLOCK_APP) {
            if (checkPin(pin, masterPin)) {
                if (pendingLaunchApp != null) {
                    removeAppLock(pendingLaunchApp.getPackageName());
                    Toast.makeText(this, "Blocco rimosso!", Toast.LENGTH_SHORT).show();
                }
                hidePinPrompt();
            } else {
                showPinError("PIN errato!");
            }
        } else if (currentPinMode == PinMode.VERIFY_FOR_CHANGE) {
            if (checkPin(pin, masterPin)) {
                enteredPin = "";
                showPinPrompt(null, PinMode.SET_NEW_PIN);
            } else {
                showPinError("PIN errato!");
            }
        } else if (currentPinMode == PinMode.SET_NEW_PIN) {
            prefs.edit().putString(PREF_MASTER_PIN, pin).apply();
            Toast.makeText(this, "PIN Master aggiornato con successo!", Toast.LENGTH_SHORT).show();
            hidePinPrompt();
        }
    }

    private boolean checkPin(String entered, String master) {
        return DEFAULT_PIN.equals(entered) || master.equals(entered);
    }

    private void showPinError(String message) {
        tvPinError.setText(message);
        tvPinError.setVisibility(View.VISIBLE);
        enteredPin = "";
        updatePinDots();
    }

    private Set<String> getLockedPackagesSet() {
        return prefs.getStringSet(PREF_LOCKED_PKGS, new HashSet<String>());
    }

    private boolean isAppLocked(String pkg) {
        return getLockedPackagesSet().contains(pkg);
    }

    private void addAppLock(String pkg) {
        Set<String> cur = new HashSet<>(getLockedPackagesSet());
        cur.add(pkg);
        prefs.edit().putStringSet(PREF_LOCKED_PKGS, cur).apply();
        buildFavorites();
        if (gridAdapter != null) {
            gridAdapter.updateState(getFavoritesSet(), cur);
        }
        Toast.makeText(this, "App bloccata con PIN", Toast.LENGTH_SHORT).show();
    }

    private void removeAppLock(String pkg) {
        Set<String> cur = new HashSet<>(getLockedPackagesSet());
        cur.remove(pkg);
        prefs.edit().putStringSet(PREF_LOCKED_PKGS, cur).apply();
        buildFavorites();
        if (gridAdapter != null) {
            gridAdapter.updateState(getFavoritesSet(), cur);
        }
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

    private void openWifiSettings() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.tv.network.NetworkActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e1) {
            try {
                Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e2) {
                openSettings();
            }
        }
    }

    private void openSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void loadWeather() {
        String cached = WeatherHelper.getCachedWeather(this);
        if (tvWeather != null) {
            tvWeather.setText(cached);
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
            public void onError() {}
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
                Drawable d = Drawable.createFromPath(target.getAbsolutePath());
                if (d != null) {
                    ivBackground.setImageDrawable(d);
                    return;
                }
            }
        } catch (Exception ignored) {}
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

        buildFavorites();
    }

    private Set<String> getFavoritesSet() {
        Set<String> favoritePkgs = prefs.getStringSet(PREF_FAVORITES, null);
        if (favoritePkgs == null) {
            favoritePkgs = new LinkedHashSet<>();
            for (String defPkg : DEFAULT_ORDERED_FAVS) {
                favoritePkgs.add(defPkg);
            }
            prefs.edit().putStringSet(PREF_FAVORITES, favoritePkgs).apply();
        }
        return favoritePkgs;
    }

    private void toggleFavorite(String pkg) {
        Set<String> cur = new LinkedHashSet<>(getFavoritesSet());
        if (cur.contains(pkg)) {
            cur.remove(pkg);
            Toast.makeText(this, "Rimossa dai Preferiti", Toast.LENGTH_SHORT).show();
        } else {
            cur.add(pkg);
            Toast.makeText(this, "Aggiunta ai Preferiti!", Toast.LENGTH_SHORT).show();
        }
        prefs.edit().putStringSet(PREF_FAVORITES, cur).apply();
        buildFavorites();
        if (gridAdapter != null) {
            gridAdapter.updateState(cur, getLockedPackagesSet());
        }
    }

    private int getCustomBannerRes(String pkg) {
        if (pkg == null) return 0;
        String p = pkg.toLowerCase();
        if (p.contains("netflix")) return R.drawable.banner_netflix;
        if (p.contains("youtube")) return R.drawable.banner_youtube;
        if (p.contains("disney")) return R.drawable.banner_disney;
        if (p.contains("firebat") || p.contains("amazonvideo") || p.contains("primevideo")) return R.drawable.banner_prime;
        if (p.contains("streamflix")) return R.drawable.banner_streamflix;
        if (p.contains("aurora")) return R.drawable.banner_aurora;
        return 0;
    }

    private void buildFavorites() {
        llFavoritesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        Set<String> favoritePkgs = getFavoritesSet();
        PackageManager pm = getPackageManager();

        List<AppInfo> favApps = new ArrayList<>();

        for (String favPkg : favoritePkgs) {
            AppInfo matched = null;
            for (AppInfo a : allAppsList) {
                if (a.getPackageName().equals(favPkg)) {
                    matched = a;
                    break;
                }
            }
            if (matched != null) {
                favApps.add(matched);
            } else {
                // Synthesize favorite if known (e.g. Netflix / Disney / Prime) so card renders cleanly
                String label = favPkg;
                if (favPkg.contains("netflix")) label = "Netflix";
                else if (favPkg.contains("youtube")) label = "YouTube";
                else if (favPkg.contains("disney")) label = "Disney+";
                else if (favPkg.contains("firebat") || favPkg.contains("amazonvideo")) label = "Prime Video";
                else if (favPkg.contains("streamflix")) label = "Streamflix";
                else if (favPkg.contains("aurora")) label = "Aurora Store";
                favApps.add(new AppInfo(favPkg, "", label, null));
            }
        }

        for (int i = 0; i < favApps.size(); i++) {
            final AppInfo app = favApps.get(i);
            View card = inflater.inflate(R.layout.item_favorite_card, llFavoritesContainer, false);
            ImageView ivBanner = card.findViewById(R.id.iv_banner);
            View llFallback = card.findViewById(R.id.ll_fallback);
            ImageView ivAppIcon = card.findViewById(R.id.iv_app_icon);
            TextView tvAppLabel = card.findViewById(R.id.tv_app_label);
            TextView tvLockBadge = card.findViewById(R.id.tv_lock_badge);
            View cardInner = card.findViewById(R.id.card_inner);

            // Clip corners to perfectly match 14dp radius - no square edges
            applyRoundedCorners(cardInner, 14f);
            applyRoundedCorners(ivBanner, 14f);
            applyRoundedCorners(llFallback, 14f);

            int bannerRes = getCustomBannerRes(app.getPackageName());
            if (bannerRes != 0) {
                ivBanner.setImageResource(bannerRes);
                ivBanner.setVisibility(View.VISIBLE);
                llFallback.setVisibility(View.GONE);
            } else {
                // Try Leanback banner
                Drawable lbBanner = null;
                try {
                    lbBanner = pm.getApplicationBanner(app.getPackageName());
                } catch (Exception ignored) {}

                if (lbBanner != null) {
                    ivBanner.setImageDrawable(lbBanner);
                    ivBanner.setVisibility(View.VISIBLE);
                    llFallback.setVisibility(View.GONE);
                } else {
                    ivBanner.setVisibility(View.GONE);
                    llFallback.setVisibility(View.VISIBLE);
                    if (app.getIcon() != null) {
                        ivAppIcon.setImageDrawable(app.getIcon());
                    } else {
                        ivAppIcon.setImageResource(R.drawable.ic_apps);
                    }
                    tvAppLabel.setText(app.getLabel());
                }
            }

            if (isAppLocked(app.getPackageName())) {
                tvLockBadge.setVisibility(View.VISIBLE);
            } else {
                tvLockBadge.setVisibility(View.GONE);
            }

            card.setTag(app.getPackageName());
            setupCardScaleAnimation(card);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Check if package actually installed
                    boolean installed = false;
                    for (AppInfo a : allAppsList) {
                        if (a.getPackageName().equals(app.getPackageName())) {
                            installed = true;
                            requestLaunchApp(a);
                            break;
                        }
                    }
                    if (!installed) {
                        Toast.makeText(MainActivity.this, app.getLabel() + " non è installata. Apri Aurora Store per scaricarla.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showAppOptions(app);
                    return true;
                }
            });

            card.setId(View.generateViewId());
            llFavoritesContainer.addView(card);
        }

        // Link favorite cards horizontal navigation
        int count = llFavoritesContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            View card = llFavoritesContainer.getChildAt(i);
            int leftId = (i > 0) ? llFavoritesContainer.getChildAt(i - 1).getId() : card.getId();
            int rightId = (i < count - 1) ? llFavoritesContainer.getChildAt(i + 1).getId() : card.getId();

            card.setNextFocusLeftId(leftId);
            card.setNextFocusRightId(rightId);
            card.setNextFocusUpId(R.id.tab_home);
            card.setNextFocusDownId(R.id.hsv_inputs);
        }
    }

    private List<TvInputItem> getTvInputItems() {
        List<TvInputItem> list = new ArrayList<>();
        list.add(new TvInputItem(1,
                prefs.getString("input_1_title", "Android TV"),
                prefs.getString("input_1_sub", "HDMI 1 • Connesso"),
                prefs.getString("input_1_icon", "hdmi"),
                "com.mediatek.tis/.HdmiInputService/HW2"));

        list.add(new TvInputItem(2,
                prefs.getString("input_2_title", "Switch 2"),
                prefs.getString("input_2_sub", "HDMI 2 • Connesso"),
                prefs.getString("input_2_icon", "gamepad"),
                "com.mediatek.tis/.HdmiInputService/HW3"));

        list.add(new TvInputItem(3,
                prefs.getString("input_3_title", "Series X"),
                prefs.getString("input_3_sub", "HDMI 3 • Connesso"),
                prefs.getString("input_3_icon", "gamepad"),
                "com.mediatek.tis/.HdmiInputService/HW4"));

        return list;
    }

    private int getInputIconRes(String iconType) {
        if ("gamepad".equalsIgnoreCase(iconType)) return R.drawable.ic_gamepad;
        if ("pc".equalsIgnoreCase(iconType)) return R.drawable.ic_pc;
        if ("tv".equalsIgnoreCase(iconType)) return R.drawable.ic_tv;
        return R.drawable.ic_hdmi;
    }

    private void buildInputs() {
        llInputsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        final List<TvInputItem> inputs = getTvInputItems();

        for (int i = 0; i < inputs.size(); i++) {
            final TvInputItem item = inputs.get(i);
            View card = inflater.inflate(R.layout.item_input_card, llInputsContainer, false);
            ImageView ivIcon = card.findViewById(R.id.iv_input_icon);
            TextView tvTitle = card.findViewById(R.id.tv_input_title);
            TextView tvSubtitle = card.findViewById(R.id.tv_input_subtitle);

            ivIcon.setImageResource(getInputIconRes(item.iconType));
            tvTitle.setText(item.title);
            tvSubtitle.setText(item.subtitle);

            card.setTag(item);
            setupCardScaleAnimation(card);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchTvInput(item);
                }
            });

            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showRenameInputDialog(item);
                    return true;
                }
            });

            card.setId(View.generateViewId());
            llInputsContainer.addView(card);
        }

        // Link input cards horizontal navigation
        int count = llInputsContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            View card = llInputsContainer.getChildAt(i);
            int leftId = (i > 0) ? llInputsContainer.getChildAt(i - 1).getId() : card.getId();
            int rightId = (i < count - 1) ? llInputsContainer.getChildAt(i + 1).getId() : card.getId();

            card.setNextFocusLeftId(leftId);
            card.setNextFocusRightId(rightId);
            card.setNextFocusUpId(R.id.hsv_favorites);
            card.setNextFocusDownId(card.getId());
        }
    }

    private void launchTvInput(final TvInputItem item) {
        if (item == null) return;
        Toast.makeText(this, "Apertura " + item.title + "...", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, HdmiActivity.class);
        intent.putExtra("input_id", item.inputId);
        intent.putExtra("input_title", item.title);
        startActivity(intent);
    }

    private void setupCardScaleAnimation(final View card) {
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            }
        });
    }

    private void launchApp(AppInfo app) {
        if (app == null) return;

        String pkg = app.getPackageName().toLowerCase();
        String lbl = app.getLabel().toLowerCase();
        if (pkg.contains("screensaver") || lbl.contains("salvaschermo")) {
            startScreensaver();
            return;
        }

        // Special Leanback launch intent for Prime Video
        if ("com.amazon.firebat".equals(app.getPackageName()) || "com.amazon.amazonvideo.livingroom".equals(app.getPackageName())) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                intent.setComponent(new ComponentName("com.amazon.firebat", "com.amazon.firebatcore.deeplink.DeepLinkRoutingActivity"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return;
            } catch (Exception ignored) {}
        }

        // 1. Explicit Leanback launcher
        if (app.getActivityName() != null && !app.getActivityName().isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                intent.setComponent(new ComponentName(app.getPackageName(), app.getActivityName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return;
            } catch (Exception ignored) {}

            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(new ComponentName(app.getPackageName(), app.getActivityName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return;
            } catch (Exception ignored) {}
        }

        // 2. PackageManager fallback
        try {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(app.getPackageName());
            if (intent == null) {
                intent = pm.getLaunchIntentForPackage(app.getPackageName());
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return;
            }
        } catch (Exception ignored) {}

        Toast.makeText(this, "Impossibile avviare " + app.getLabel(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        resetIdleTimer();

        // Physical numpad & back keys while PIN prompt is showing
        if (flPinOverlay != null && flPinOverlay.getVisibility() == View.VISIBLE) {
            int keyCode = event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() > 0) return true;

                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    onDigitEntered(keyCode - KeyEvent.KEYCODE_0);
                    return true;
                }
                if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                    onDigitEntered(keyCode - KeyEvent.KEYCODE_NUMPAD_0);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    onDelPressed();
                    return true;
                }
            }
        }

        // Rename dialog D-Pad control
        if (flRenameOverlay != null && flRenameOverlay.getVisibility() == View.VISIBLE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (etInputTitle != null && etInputTitle.hasFocus()) {
                        if (etInputSubtitle != null) etInputSubtitle.requestFocus();
                        return true;
                    }
                    if (etInputSubtitle != null && etInputSubtitle.hasFocus()) {
                        focusSelectedRenameIcon();
                        return true;
                    }
                    if (isAnyRenameIconFocused()) {
                        if (btnSaveInput != null) btnSaveInput.requestFocus();
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (btnSaveInput != null && btnSaveInput.hasFocus()) {
                        focusSelectedRenameIcon();
                        return true;
                    }
                    if (btnCancelInput != null && btnCancelInput.hasFocus()) {
                        focusSelectedRenameIcon();
                        return true;
                    }
                    if (isAnyRenameIconFocused()) {
                        if (etInputSubtitle != null) etInputSubtitle.requestFocus();
                        return true;
                    }
                    if (etInputSubtitle != null && etInputSubtitle.hasFocus()) {
                        if (etInputTitle != null) etInputTitle.requestFocus();
                        return true;
                    }
                    if (etInputTitle != null && etInputTitle.hasFocus()) {
                        return true; // Keep inside dialog
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (btnIconHdmi != null && btnIconHdmi.hasFocus()) return true;
                    if (btnSaveInput != null && btnSaveInput.hasFocus()) return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (btnIconTv != null && btnIconTv.hasFocus()) return true;
                    if (btnCancelInput != null && btnCancelInput.hasFocus()) return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (etInputTitle != null && etInputTitle.hasFocus()) {
                        showSoftKeyboard(etInputTitle);
                        return true;
                    }
                    if (etInputSubtitle != null && etInputSubtitle.hasFocus()) {
                        showSoftKeyboard(etInputSubtitle);
                        return true;
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private boolean isAnyRenameIconFocused() {
        return (btnIconHdmi != null && btnIconHdmi.hasFocus())
                || (btnIconGamepad != null && btnIconGamepad.hasFocus())
                || (btnIconPc != null && btnIconPc.hasFocus())
                || (btnIconTv != null && btnIconTv.hasFocus());
    }

    private void focusSelectedRenameIcon() {
        if (btnIconGamepad != null && btnIconGamepad.isSelected()) {
            btnIconGamepad.requestFocus();
        } else if (btnIconPc != null && btnIconPc.isSelected()) {
            btnIconPc.requestFocus();
        } else if (btnIconTv != null && btnIconTv.isSelected()) {
            btnIconTv.requestFocus();
        } else if (btnIconHdmi != null) {
            btnIconHdmi.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        if (flPinOverlay != null && flPinOverlay.getVisibility() == View.VISIBLE) {
            hidePinPrompt();
            return;
        }
        if (flRenameOverlay != null && flRenameOverlay.getVisibility() == View.VISIBLE) {
            hideRenameInputDialog();
            return;
        }
        if (flOptionsOverlay != null && flOptionsOverlay.getVisibility() == View.VISIBLE) {
            hideAppOptions();
            return;
        }
        if (flSettingsOverlay != null && flSettingsOverlay.getVisibility() == View.VISIBLE) {
            hideZenithSettings();
            return;
        }
        if (flDrawer != null && flDrawer.getVisibility() == View.VISIBLE) {
            showHomeDashboard();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            event.startTracking();
        }

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            // Check focused input card
            for (int i = 0; i < llInputsContainer.getChildCount(); i++) {
                View child = llInputsContainer.getChildAt(i);
                if (child.hasFocus() && child.getTag() instanceof TvInputItem) {
                    showRenameInputDialog((TvInputItem) child.getTag());
                    return true;
                }
            }
            // Check focused favorite card
            for (int i = 0; i < llFavoritesContainer.getChildCount(); i++) {
                View child = llFavoritesContainer.getChildAt(i);
                if (child.hasFocus() && child.getTag() instanceof String) {
                    String pkg = (String) child.getTag();
                    for (AppInfo a : allAppsList) {
                        if (a.getPackageName().equals(pkg)) {
                            showAppOptions(a);
                            return true;
                        }
                    }
                }
            }
            // Check focused drawer app
            if (flDrawer.getVisibility() == View.VISIBLE) {
                int pos = gvAllApps.getSelectedItemPosition();
                if (pos != AdapterView.INVALID_POSITION && pos < allAppsList.size()) {
                    showAppOptions(allAppsList.get(pos));
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            // Check focused input card
            for (int i = 0; i < llInputsContainer.getChildCount(); i++) {
                View child = llInputsContainer.getChildAt(i);
                if (child.hasFocus() && child.getTag() instanceof TvInputItem) {
                    showRenameInputDialog((TvInputItem) child.getTag());
                    return true;
                }
            }
            // Check focused favorite card
            for (int i = 0; i < llFavoritesContainer.getChildCount(); i++) {
                View child = llFavoritesContainer.getChildAt(i);
                if (child.hasFocus() && child.getTag() instanceof String) {
                    String pkg = (String) child.getTag();
                    for (AppInfo a : allAppsList) {
                        if (a.getPackageName().equals(pkg)) {
                            showAppOptions(a);
                            return true;
                        }
                    }
                }
            }
            // Check focused drawer app
            if (flDrawer.getVisibility() == View.VISIBLE) {
                int pos = gvAllApps.getSelectedItemPosition();
                if (pos != AdapterView.INVALID_POSITION && pos < allAppsList.size()) {
                    showAppOptions(allAppsList.get(pos));
                    return true;
                }
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }
}
