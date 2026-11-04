/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;
import java.util.HashSet;

public class GraySectionCell extends FrameLayout implements Theme.Colorable {

    private AnimatedEmojiSpan.TextViewEmojis textView;
    private AnimatedTextView rightTextView;
    private AnimatedTextView additionalRightTextView;
    private LinearLayout rightTextLayout;
    private FrameLayout.LayoutParams rightTextViewLayoutParams;
    private final Theme.ResourcesProvider resourcesProvider;
    private int layerHeight = 32;

    public GraySectionCell(Context context) {
        this(context, null);
    }

    public GraySectionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, 16, resourcesProvider);
    }

    public GraySectionCell(Context context, int padding, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        ThemeBind.backgroundColor(this, Theme.key_graySection, resourcesProvider);

        textView = new AnimatedEmojiSpan.TextViewEmojis(getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTypeface(AndroidUtilities.bold());
        ThemeBind.textColor(textView, Theme.key_graySectionText, resourcesProvider);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, padding, 0, padding, 0));

        rightTextView = createRightTextView();
        addView(rightTextView, rightTextViewLayoutParams = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, padding, 0, padding, 0));

        ViewCompat.setAccessibilityHeading(this, true);
    }

    private AnimatedTextView createRightTextView() {
        AnimatedTextView view = new AnimatedTextView(getContext(), true, true, true) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };
        view.setPadding(dp(2), 0, dp(2), 0);
        view.setAnimationProperties(.9f, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        view.setTextSize(dp(14));
        ThemeBind.textColor(view, Theme.key_graySectionText, resourcesProvider);
        view.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        return view;
    }

    private boolean noBackground;
    public void setNoBackground(boolean no) {
        this.noBackground = no;
        updateColors();
    }

    public void updateColors() {
        setBackgroundColor(noBackground ? 0 : getThemedColor(Theme.key_graySection));
        textView.setTextColor(getThemedColor(Theme.key_graySectionText));
        rightTextView.setTextColor(getThemedColor(Theme.key_graySectionText));
        if (additionalRightTextView != null) additionalRightTextView.setTextColor(getThemedColor(Theme.key_graySectionText));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(layerHeight), MeasureSpec.EXACTLY));
        if (additionalRightTextView != null && additionalRightTextView.getVisibility() == VISIBLE) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) textView.getLayoutParams();
            int margin = LocaleController.isRTL ? params.rightMargin + rightTextViewLayoutParams.leftMargin : params.leftMargin + rightTextViewLayoutParams.rightMargin;
            int width = Math.max(0, getMeasuredWidth() - margin - rightTextLayout.getMeasuredWidth() - dp(8));
            textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(layerHeight), MeasureSpec.EXACTLY));
        }
    }

    public void setLayerHeight(int dp){
        layerHeight = dp;
        requestLayout();
    }

    public void setTextColor(int key) {
        ThemeBind.textColor(textView, key, resourcesProvider);
        ThemeBind.textColor(rightTextView, key, resourcesProvider);
        if (additionalRightTextView != null) ThemeBind.textColor(additionalRightTextView, key, resourcesProvider);
    }

    public CharSequence getText() {
        return textView.getText();
    }

    public void setText(CharSequence text) {
        setAdditionalRightText(null, null);
        textView.setText(text);
        rightTextView.setVisibility(GONE);
        rightTextView.setOnClickListener(null);
    }

    public void setText(CharSequence left, CharSequence right, OnClickListener onClickListener) {
        setAdditionalRightText(null, null);
        textView.setText(left);
        rightTextView.setText(right, false);
        rightTextView.setOnClickListener(onClickListener);
        rightTextView.setVisibility(VISIBLE);
    }

    public void setRightText(CharSequence right) {
        setRightText(right, true);
    }

    public void setRightTextMargin(int marginDp) {
        rightTextViewLayoutParams.leftMargin = dp(marginDp);
        rightTextViewLayoutParams.rightMargin = dp(marginDp);
        (rightTextLayout == null ? rightTextView : rightTextLayout).setLayoutParams(rightTextViewLayoutParams);
    }

    public void setAdditionalRightText(CharSequence text, OnClickListener onClickListener) {
        boolean visible = !TextUtils.isEmpty(text);
        if (additionalRightTextView == null) {
            if (!visible) return;
            rightTextLayout = new LinearLayout(getContext());
            rightTextLayout.setLayoutDirection(LocaleController.isRTL ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
            removeView(rightTextView);
            rightTextLayout.addView(rightTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
            additionalRightTextView = createRightTextView();
            LinearLayout.LayoutParams params = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT);
            params.setMarginStart(dp(12));
            rightTextLayout.addView(additionalRightTextView, params);
            addView(rightTextLayout, rightTextViewLayoutParams);
        }
        textView.setSingleLine(visible);
        textView.setEllipsize(visible ? TextUtils.TruncateAt.END : null);
        additionalRightTextView.setText(text, false);
        additionalRightTextView.setOnClickListener(visible ? onClickListener : null);
        additionalRightTextView.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setRightText(CharSequence right, boolean moveDown) {
        rightTextView.setText(right, true, moveDown);
        rightTextView.setVisibility(VISIBLE);
    }

    public void setRightText(CharSequence right, OnClickListener onClickListener) {
        rightTextView.setText(right, false);
        rightTextView.setOnClickListener(onClickListener);
        rightTextView.setVisibility(VISIBLE);
    }

    public void setRightText(CharSequence right, boolean moveDown, OnClickListener onClickListener) {
        rightTextView.setText(right, true, moveDown);
        rightTextView.setOnClickListener(onClickListener);
        rightTextView.setVisibility(VISIBLE);
    }

    public static void createThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView listView) {
    }

    public TextView getTextView() {
        return textView;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
