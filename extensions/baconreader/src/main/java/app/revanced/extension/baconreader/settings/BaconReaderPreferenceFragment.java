package app.revanced.extension.baconreader.settings;

import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceScreen;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toolbar;

import androidx.annotation.RequiresApi;

import app.revanced.extension.baconreader.errors.ErrorDialog;
import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.settings.preference.ToolbarPreferenceFragment;

/**
 * The ReVanced settings, with a toolbar on the sub screens and BaconReader's theme colors.
 */
@SuppressWarnings("deprecation")
@RequiresApi(api = Build.VERSION_CODES.O)
public class BaconReaderPreferenceFragment extends ToolbarPreferenceFragment {
    /**
     * White at 87%, the color of BaconReader's toolbar titles.
     */
    private static final int TOOLBAR_TITLE_COLOR = 0xDEFFFFFF;

    @Override
    protected void initialize() {
        super.initialize();

        try {
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            Utils.sortPreferenceGroups(preferenceScreen);
            setPreferenceScreenToolbar(preferenceScreen);
        } catch (Exception ex) {
            ErrorDialog.printException(() -> "initialize failure", ex);
        }
    }

    @Override
    protected void customizeDialogBackground(ViewGroup rootView) {
        rootView.setBackgroundColor(BaconReaderActivityHook.backgroundColor);
    }

    @Override
    protected void customizeToolbar(Toolbar toolbar) {
        super.customizeToolbar(toolbar);
        BaconReaderActivityHook.styleToolbar(toolbar);
    }

    @Override
    protected void onPostToolbarSetup(Toolbar toolbar, Dialog preferenceScreenDialog) {
        BaconReaderActivityHook.styleWindow(preferenceScreenDialog.getWindow());
    }

    /**
     * The back button and title, like in BaconReader's toolbars.
     */
    static void styleToolbarContent(Toolbar toolbar) {
        Drawable icon = toolbar.getNavigationIcon();
        if (icon != null) icon.setTint(BaconReaderActivityHook.TOOLBAR_FOREGROUND_COLOR);
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            if (toolbar.getChildAt(i) instanceof TextView) {
                TextView title = (TextView) toolbar.getChildAt(i);
                title.setTextColor(TOOLBAR_TITLE_COLOR);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            }
        }
    }
}
