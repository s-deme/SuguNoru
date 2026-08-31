package jp.sugunoru.app.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;

import static jp.sugunoru.app.ui.UiPalette.*;

/**
 * Shared programmatic-view styling for the app's screens.
 *
 * <p>Keeping these primitives together ensures that controls retain the same
 * spacing, accessibility sizing, focus treatment, and theme colors.
 */
public abstract class StyledActivity extends Activity {
    protected final void addField(LinearLayout parent, String label, View input, String hint) {
        TextView labelView = text(label, 14, INK, Typeface.BOLD);
        labelView.setPadding(0, 0, 0, dp(7));
        if (input.getId() != View.NO_ID) labelView.setLabelFor(input.getId());
        parent.addView(labelView);
        parent.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (hint != null) {
            TextView hintView = text(hint, 14, MUTED, Typeface.NORMAL);
            hintView.setPadding(0, dp(5), 0, 0);
            parent.addView(hintView);
        }
        parent.addView(space(16));
    }

    protected final EditText input(String hint, int inputType, boolean multiline) {
        EditText input = new EditText(this);
        input.setTextSize(16);
        input.setTextColor(INK);
        input.setHintTextColor(HINT);
        input.setHint(hint);
        input.setInputType(inputType | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setGravity(multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        input.setPadding(dp(14), multiline ? dp(12) : 0, dp(14), multiline ? dp(12) : 0);
        input.setMinHeight(dp(56));
        input.setBackgroundTintList(null);
        input.setBackground(focusableInputBackground(13));
        if (multiline) {
            input.setMinLines(3);
            input.setMaxLines(7);
        } else {
            input.setSingleLine(true);
        }
        return input;
    }

    protected final Spinner spinner(String[] choices) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(INK);
                view.setTextSize(16);
                view.setPadding(dp(12), 0, dp(12), 0);
                return view;
            }

            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(INK);
                view.setTextSize(16);
                view.setBackgroundColor(SURFACE);
                view.setMinHeight(dp(52));
                view.setGravity(Gravity.CENTER_VERTICAL);
                view.setPadding(dp(16), dp(8), dp(16), dp(8));
                return view;
            }
        };
        spinner.setAdapter(adapter);
        spinner.setPadding(dp(10), 0, dp(10), 0);
        spinner.setMinimumHeight(dp(56));
        spinner.setBackgroundTintList(null);
        spinner.setBackground(focusableInputBackground(13));
        return spinner;
    }

    protected final Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(54));
        button.setLetterSpacing(0.02f);
        button.setBackgroundTintList(null);
        button.setBackground(interactiveBackground(BRAND, 16, 0, 0));
        button.setElevation(dp(2));
        return button;
    }

    protected final Button segmentButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(selected ? WHITE : MUTED);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setMinHeight(dp(52));
        button.setBackgroundTintList(null);
        button.setBackground(interactiveBackground(selected ? BRAND : Color.TRANSPARENT,
                14, 0, 0));
        button.setElevation(0);
        button.setContentDescription(label + (selected ? "、選択中" : ""));
        button.setSelected(selected);
        return button;
    }

    protected final Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(BRAND_DARK);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinWidth(dp(62));
        button.setMinHeight(dp(48));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackgroundTintList(null);
        button.setBackground(interactiveBackground(SURFACE_VARIANT, 13, 0, 0));
        return button;
    }

    protected final TextView pill(String label, int foreground, int background) {
        TextView view = text(label, 13, foreground, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        view.setMinHeight(dp(28));
        view.setBackground(roundRect(background, 30, 0, 0));
        return view;
    }

    protected final TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        view.setLineSpacing(dp(1), 1.04f);
        return view;
    }

    protected final TextView centerText(String value, float size, int color, int style) {
        TextView view = text(value, size, color, style);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    protected final LinearLayout vertical(int background) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(background);
        return layout;
    }

    protected final LinearLayout horizontal(int gravity) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(gravity);
        return layout;
    }

    protected final Space space(int heightDp) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(dp(1), dp(heightDp)));
        return space;
    }

    protected final GradientDrawable roundRect(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    protected final GradientDrawable roundGradient(int start, int end, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    protected final Drawable focusableInputBackground(int radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                roundRect(SURFACE, radiusDp, BRAND, 2));
        states.addState(new int[]{android.R.attr.state_enabled},
                roundRect(SURFACE, radiusDp, CONTROL, 1));
        states.addState(new int[]{}, roundRect(CANVAS, radiusDp, CONTROL, 1));
        return states;
    }

    protected final Drawable interactiveBackground(int color, int radiusDp, int strokeColor, int strokeDp) {
        int focusColor = color == BRAND || color == BRAND_DARK ? WHITE : BRAND;
        StateListDrawable content = new StateListDrawable();
        content.addState(new int[]{android.R.attr.state_focused},
                roundRect(color, radiusDp, focusColor, 2));
        content.addState(new int[]{}, roundRect(color, radiusDp, strokeColor, strokeDp));
        GradientDrawable mask = roundRect(WHITE, radiusDp, 0, 0);
        return new RippleDrawable(ColorStateList.valueOf(0x33006B4F), content, mask);
    }

    protected final int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    protected final boolean isConstrainedContent() {
        Configuration configuration = getResources().getConfiguration();
        return configuration.screenWidthDp < 380 || configuration.fontScale >= 1.2f;
    }

    protected final int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected final void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    protected final void markAsHeading(TextView view) {
        if (android.os.Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
    }
}
