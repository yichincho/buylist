package tw.yc.smartshopping;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Small view toolkit following the Astryx design language: semantic colour tokens
 * (res/values/colors.xml, with a dark set in values-night), a 6dp spacing grid and
 * container / element / inner corner radii of 18 / 12 / 6dp.
 */
final class Ui {
    static final int RADIUS_CONTAINER = 18;
    static final int RADIUS_ELEMENT = 12;
    static final int RADIUS_INNER = 6;
    static final int RADIUS_FULL = 999;

    private final Context context;

    Ui(Context context) { this.context = context; }

    int color(int res) { return context.getColor(res); }

    int dp(float value) { return (int) (value * context.getResources().getDisplayMetrics().density + .5f); }

    GradientDrawable shape(int fill, int radiusDp, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    /** Wraps a background so it shows the touch ripple, clipped to the same shape. */
    Drawable pressable(GradientDrawable content) {
        return new RippleDrawable(ColorStateList.valueOf(color(R.color.ripple)), content, content);
    }

    /** Rectangular touch ripple for list rows. */
    Drawable rowRipple() {
        return new RippleDrawable(ColorStateList.valueOf(color(R.color.ripple)), null, new ColorDrawable(Color.WHITE));
    }

    /** A rounded surface whose children are clipped to its corners. */
    LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shape(color(R.color.card), RADIUS_CONTAINER, color(R.color.border_subtle)));
        card.setClipToOutline(true);
        return card;
    }

    TextView text(String value, float sizeSp, int colorRes, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color(colorRes));
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    Button primaryButton(String label) {
        return button(label, color(R.color.accent), color(R.color.on_accent), 0);
    }

    Button tonalButton(String label) {
        return button(label, color(R.color.accent_tonal), color(R.color.accent), 0);
    }

    Button textButton(String label, int colorRes) {
        Button button = button(label, Color.TRANSPARENT, color(colorRes), 0);
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    Button outlineButton(String label, int colorRes) {
        return button(label, Color.TRANSPARENT, color(colorRes), color(R.color.border));
    }

    private Button button(String label, int fill, int textColor, int stroke) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(textColor);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setStateListAnimator(null);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setBackground(pressable(shape(fill, RADIUS_ELEMENT, stroke)));
        return button;
    }

    /** Pill-shaped filter chip; the selected one is filled with the accent colour. */
    TextView chip(String label, boolean selected) {
        TextView chip = text(label, 15, selected ? R.color.on_accent : R.color.text_primary, selected);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(42));
        chip.setPadding(dp(16), 0, dp(16), 0);
        chip.setBackground(pressable(selected
                ? shape(color(R.color.accent), RADIUS_FULL, 0)
                : shape(color(R.color.card), RADIUS_FULL, color(R.color.border))));
        chip.setClickable(true);
        return chip;
    }

    /** Small rounded count badge, e.g. "2/5". */
    TextView badge(String label) {
        TextView badge = text(label, 13, R.color.text_secondary, true);
        badge.setPadding(dp(9), dp(2), dp(9), dp(2));
        badge.setBackground(shape(color(R.color.accent_tonal), RADIUS_FULL, 0));
        return badge;
    }

    void styleField(EditText field) {
        field.setTextColor(color(R.color.text_primary));
        field.setHintTextColor(color(R.color.text_disabled));
        field.setTextSize(16);
        field.setPadding(dp(14), dp(12), dp(14), dp(12));
        field.setBackground(shape(color(R.color.field), RADIUS_ELEMENT, color(R.color.border_subtle)));
    }

    void tint(CompoundButton check) {
        check.setButtonTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{color(R.color.accent), color(R.color.text_secondary)}));
    }

    LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }
}
