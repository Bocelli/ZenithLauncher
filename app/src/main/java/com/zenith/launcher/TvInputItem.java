package com.zenith.launcher;

public class TvInputItem {
    public int id;
    public String title;
    public String subtitle;
    public String iconType; // "hdmi", "gamepad", "pc", "tv"
    public String inputId;

    public TvInputItem(int id, String title, String subtitle, String iconType, String inputId) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.iconType = iconType;
        this.inputId = inputId;
    }
}
