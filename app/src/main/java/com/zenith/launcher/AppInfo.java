package com.zenith.launcher;

import android.graphics.drawable.Drawable;

public class AppInfo implements Comparable<AppInfo> {
    private final String packageName;
    private final String activityName;
    private final String label;
    private final Drawable icon;

    public AppInfo(String packageName, String activityName, String label, Drawable icon) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.label = label;
        this.icon = icon;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getLabel() {
        return label;
    }

    public Drawable getIcon() {
        return icon;
    }

    @Override
    public int compareTo(AppInfo o) {
        return this.label.compareToIgnoreCase(o.label);
    }
}
