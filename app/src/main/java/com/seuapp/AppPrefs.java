package com.seuapp;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CLICK_TARGET = "click_target";
    private static final String KEY_STATUS = "status";
    private static final String KEY_OVERLAY_PERMISSION_REQUESTED = "overlay_permission_requested";
    private static final String KEY_ACCESSIBILITY_PERMISSION_REQUESTED = "accessibility_permission_requested";
    private static final String KEY_CLICK_TARGET_X = "click_target_x";
    private static final String KEY_CLICK_TARGET_Y = "click_target_y";
    private static final String KEY_EXCELLENT_MINIMUM = "excellent_minimum";
    private static final String KEY_GOOD_MINIMUM = "good_minimum";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean enabled() {
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public boolean clickTarget() {
        return prefs.getBoolean(KEY_CLICK_TARGET, true);
    }

    public void setClickTarget(boolean show) {
        prefs.edit().putBoolean(KEY_CLICK_TARGET, show).apply();
    }

    public int clickTargetX() {
        return prefs.getInt(KEY_CLICK_TARGET_X, -1);
    }

    public int clickTargetY() {
        return prefs.getInt(KEY_CLICK_TARGET_Y, -1);
    }

    public void setClickTargetPosition(int x, int y) {
        prefs.edit()
                .putInt(KEY_CLICK_TARGET_X, x)
                .putInt(KEY_CLICK_TARGET_Y, y)
                .apply();
    }

    public double excellentMinimum() {
        return Double.longBitsToDouble(prefs.getLong(
                KEY_EXCELLENT_MINIMUM, Double.doubleToLongBits(3.00)));
    }

    public void setExcellentMinimum(double value) {
        prefs.edit().putLong(KEY_EXCELLENT_MINIMUM, Double.doubleToLongBits(value)).apply();
    }

    public double goodMinimum() {
        return Double.longBitsToDouble(prefs.getLong(
                KEY_GOOD_MINIMUM, Double.doubleToLongBits(2.00)));
    }

    public void setGoodMinimum(double value) {
        prefs.edit().putLong(KEY_GOOD_MINIMUM, Double.doubleToLongBits(value)).apply();
    }

    public String status() {
        return prefs.getString(KEY_STATUS, "Ativo");
    }

    public void status(String value) {
        prefs.edit().putString(KEY_STATUS, value).apply();
    }

    public boolean overlayPermissionRequested() {
        return prefs.getBoolean(KEY_OVERLAY_PERMISSION_REQUESTED, false);
    }

    public void setOverlayPermissionRequested(boolean requested) {
        prefs.edit().putBoolean(KEY_OVERLAY_PERMISSION_REQUESTED, requested).apply();
    }

    public boolean accessibilityPermissionRequested() {
        return prefs.getBoolean(KEY_ACCESSIBILITY_PERMISSION_REQUESTED, false);
    }

    public void setAccessibilityPermissionRequested(boolean requested) {
        prefs.edit().putBoolean(KEY_ACCESSIBILITY_PERMISSION_REQUESTED, requested).apply();
    }
}
