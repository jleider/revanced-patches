package app.revanced.extension.baconreader.settings;

import static app.revanced.extension.shared.StringRef.str;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceFragment;
import android.view.View;
import android.view.Window;
import android.widget.Toolbar;

import androidx.annotation.RequiresApi;

import app.revanced.extension.baconreader.errors.ErrorDialog;
import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.ResourceType;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.settings.BaseActivityHook;

/**
 * Hooks GoogleApiActivity to show the ReVanced settings, and adds an entry for them
 * at the end of BaconReader's settings menu.
 */
@SuppressWarnings("unused")
public class BaconReaderActivityHook extends BaseActivityHook {
    private static final String SETTINGS_ACTIVITY_CLASS = "com.google.android.gms.common.api.GoogleApiActivity";
    private static final String SETTINGS_INTENT_DATA = "revanced_settings_intent";

    /** Number of sections in BaconReader's settings menu, captured when the ReVanced entry is added. */
    private static int appSectionCount = -1;

    /**
     * BaconReader's toolbars have white text and icons in all themes.
     */
    static final int TOOLBAR_FOREGROUND_COLOR = Color.WHITE;

    /**
     * Colors of BaconReader's current theme, set when the settings are opened.
     */
    static int backgroundColor = Color.BLACK;
    private static int toolbarColor = Color.BLACK;
    private static int statusBarColor = Color.BLACK;

    /**
     * Injection point.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void initialize(Activity parentActivity) {
        // Load the settings, so the preferences can find their values.
        Logger.printDebug(() -> "Show images: " + Settings.SHOW_IMAGES.get());

        BaseActivityHook.initialize(new BaconReaderActivityHook(), parentActivity);
    }

    /**
     * Injection point.
     *
     * @return If the original finish method should not run.
     */
    public static boolean handleFinish() {
        return false;
    }

    /**
     * Injection point. Called by SettingsMenuFragment.initAdapter with the titles of the
     * settings sections: the menu shows them plus the ReVanced settings.
     */
    public static String[] addSettingsMenuEntry(String[] sections) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return sections;
        appSectionCount = sections.length;
        String[] result = new String[sections.length + 1];
        System.arraycopy(sections, 0, result, 0, sections.length);
        result[sections.length] = str("revanced_settings_title");
        return result;
    }

    /**
     * Injection point. Called at the start of SettingsActivity.openFragment(int).
     *
     * @return If the click was on the ReVanced entry (the original method must then return).
     */
    public static boolean handleSettingsMenuClick(Activity activity, int position) {
        if (appSectionCount < 0 || position != appSectionCount) return false;
        try {
            Intent intent = new Intent();
            intent.setClassName(activity, SETTINGS_ACTIVITY_CLASS);
            intent.setData(Uri.parse(SETTINGS_INTENT_DATA));
            activity.startActivity(intent);
        } catch (Exception ex) {
            ErrorDialog.printException(() -> "Could not open the ReVanced settings", ex);
        }
        return true;
    }

    /**
     * Uses BaconReader's current theme (light, dark or black).
     */
    @Override
    protected void customizeActivityTheme(Activity activity) {
        try {
            int theme = (Integer) Class.forName("com.onelouder.baconreader.Preferences")
                    .getMethod("getTheme").invoke(null);
            activity.setTheme(theme);
        } catch (Exception ex) {
            ErrorDialog.printException(() -> "Could not apply the BaconReader theme", ex);
        }

        backgroundColor = appThemeColor(activity, "bgColor",
                themeColor(activity, android.R.attr.colorBackground, Color.WHITE));
        toolbarColor = appThemeColor(activity, "colorPrimary", backgroundColor);
        statusBarColor = appThemeColor(activity, "colorPrimaryDark", toolbarColor);
        Utils.setIsDarkModeEnabled(isDark(backgroundColor));

        // The host activity is translucent.
        activity.getWindow().setBackgroundDrawable(new ColorDrawable(backgroundColor));
        styleWindow(activity.getWindow());
    }

    @Override
    protected int getToolbarBackgroundColor() {
        return toolbarColor;
    }

    @Override
    @SuppressLint("UseCompatLoadingForDrawables")
    protected Drawable getNavigationIcon() {
        Context context = Utils.getContext();
        Drawable icon = context.getResources().getDrawable(
                Utils.getResourceIdentifierOrThrow(ResourceType.DRAWABLE, "revanced_settings_toolbar_arrow_left"),
                context.getTheme());
        icon.setColorFilter(TOOLBAR_FOREGROUND_COLOR, PorterDuff.Mode.SRC_IN);
        return icon;
    }

    @Override
    protected View.OnClickListener getNavigationClickListener(Activity activity) {
        return view -> activity.finish();
    }

    @Override
    protected void onPostToolbarSetup(Activity activity, Toolbar toolbar, PreferenceFragment fragment) {
        BaconReaderPreferenceFragment.styleToolbarContent(toolbar);
    }

    @Override
    protected PreferenceFragment createPreferenceFragment() {
        return new BaconReaderPreferenceFragment();
    }

    /**
     * Toolbar of the sub screens.
     */
    static void styleToolbar(Toolbar toolbar) {
        toolbar.setBackgroundColor(toolbarColor);
        BaconReaderPreferenceFragment.styleToolbarContent(toolbar);
    }

    /**
     * Status and navigation bars, like in BaconReader's own screens.
     */
    @SuppressWarnings("deprecation")
    static void styleWindow(Window window) {
        if (window == null) return;
        window.setStatusBarColor(statusBarColor);
        window.setNavigationBarColor(backgroundColor);
    }

    /**
     * A color attribute of BaconReader's theme.
     */
    private static int appThemeColor(Context context, String attributeName, int fallback) {
        int attribute = context.getResources().getIdentifier(attributeName, "attr", context.getPackageName());
        if (attribute == 0) return fallback;
        return themeColor(context, attribute, fallback);
    }

    private static int themeColor(Context context, int attribute, int fallback) {
        TypedArray array = context.obtainStyledAttributes(new int[]{attribute});
        try {
            return array.getColor(0, fallback);
        } finally {
            array.recycle();
        }
    }

    private static boolean isDark(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return luminance < 0.5;
    }
}
