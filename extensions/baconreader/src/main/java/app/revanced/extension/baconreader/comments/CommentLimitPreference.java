package app.revanced.extension.baconreader.comments;

import static app.revanced.extension.shared.StringRef.str;

import android.app.Dialog;
import android.content.Context;
import android.preference.Preference;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import app.revanced.extension.baconreader.errors.ErrorDialog;
import app.revanced.extension.baconreader.settings.Settings;
import app.revanced.extension.shared.ui.CustomDialog;

/**
 * "Comments to load": how many comments a post requests from Reddit when it opens, from Reddit's default (200)
 * to its maximum (500). A number box and a slider, kept in sync.
 */
@SuppressWarnings({"unused", "deprecation"})
public class CommentLimitPreference extends Preference {
    public static final int MIN = 200;
    public static final int MAX = 500;

    public CommentLimitPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public CommentLimitPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public CommentLimitPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CommentLimitPreference(Context context) {
        super(context);
        init();
    }

    /** The setting, within Reddit's limits. */
    public static int limit() {
        return clamp(Settings.COMMENT_LIMIT.get());
    }

    private static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    private void init() {
        updateSummary();
        setOnPreferenceClickListener(preference -> {
            showDialog();
            return true;
        });
    }

    private void updateSummary() {
        setSummary(str("revanced_baconreader_comment_limit_summary", limit()));
    }

    private void showDialog() {
        try {
            Context context = getContext();
            final EditText number = new EditText(context);
            number.setInputType(InputType.TYPE_CLASS_NUMBER);
            number.setText(String.valueOf(limit()));
            number.setSelection(number.getText().length());

            final SeekBar slider = new SeekBar(context);
            slider.setMax(MAX - MIN);
            slider.setProgress(limit() - MIN);

            // Each one follows the other; the number box only moves the slider with a value in range.
            final boolean[] updating = {false};
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (updating[0] || !fromUser) return;
                    updating[0] = true;
                    number.setText(String.valueOf(MIN + progress));
                    number.setSelection(number.getText().length());
                    updating[0] = false;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            number.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (updating[0]) return;
                    Integer value = parse(s.toString());
                    if (value == null || value < MIN || value > MAX) return;
                    updating[0] = true;
                    slider.setProgress(value - MIN);
                    updating[0] = false;
                }
            });

            Pair<Dialog, LinearLayout> dialog = CustomDialog.create(
                    context,
                    getTitle() != null ? getTitle().toString() : "",
                    null,
                    number,
                    null,
                    () -> {
                        // Out of range or empty: the nearest allowed value.
                        Integer value = parse(number.getText().toString());
                        int limit = value == null ? slider.getProgress() + MIN : clamp(value);
                        Settings.COMMENT_LIMIT.save(limit);
                        updateSummary();
                    },
                    () -> {
                    },
                    str("revanced_settings_reset"),
                    () -> {
                        updating[0] = true;
                        number.setText(String.valueOf(Settings.COMMENT_LIMIT.defaultValue));
                        number.setSelection(number.getText().length());
                        slider.setProgress(clamp(Settings.COMMENT_LIMIT.defaultValue) - MIN);
                        updating[0] = false;
                    },
                    false
            );

            // The slider right below the number box.
            LinearLayout layout = dialog.second;
            View numberRow = number;
            while (numberRow.getParent() != null && numberRow.getParent() != layout) {
                numberRow = (View) numberRow.getParent();
            }
            int index = layout.indexOfChild(numberRow);
            int padding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
                    context.getResources().getDisplayMetrics()));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = padding;
            params.bottomMargin = padding;
            layout.addView(slider, index >= 0 ? index + 1 : layout.getChildCount(), params);

            dialog.first.show();
        } catch (Exception ex) {
            ErrorDialog.printException(() -> "Could not show the comment limit", ex);
        }
    }

    private static Integer parse(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
