package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;

public class CachedStaticLayout {

    public final StaticLayout layout;
    private boolean disabled;

    public CachedStaticLayout(StaticLayout source) {
        this.layout = source;
    }

    public CachedStaticLayout disableCache() {
        disabled = true;
        return this;
    }

    private RenderNode renderNode;
    private AnimatedEmojiSpan.CachedPositions emojiPositions;

    public CachedStaticLayout cacheEmojiPositions() {
        emojiPositions = new AnimatedEmojiSpan.CachedPositions(layout.getText());
        // This opt-in is for stable layouts whose span positions are cached as well.
        cachedEmojiSpans = getText() instanceof Spanned
                ? ((Spanned) getText()).getSpans(0, getText().length(), Emoji.EmojiSpan.class)
                : new Emoji.EmojiSpan[0];
        return this;
    }

    public void clear() {
        if (renderNode != null) {
            renderNode.discardDisplayList();
            renderNode = null;
        }
    }

    public void draw(Canvas canvas) {
        if (emojiPositions != null && !emojiPositions.canCache()) {
            clear();
            layout.draw(canvas);
            return;
        }
        if (!disabled && canvas.isHardwareAccelerated() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && canvas.quickReject(0, 0, layout.getWidth(), layout.getHeight())) {
                if (emojiPositions != null) emojiPositions.restore();
                return;
            }
            if (hasChanges() || renderNode == null || !renderNode.hasDisplayList()) {
                if (renderNode == null) {
                    renderNode = new RenderNode("CachedStaticLayout");
                    renderNode.setClipToBounds(false);
                }
                renderNode.setPosition(getLayoutBounds());
                Canvas cacheCanvas = renderNode.beginRecording();
                int color = layout.getPaint().getColor();
                try {
                    layout.getPaint().setColor(ColorUtils.setAlphaComponent(color, 0xFF));
                    layout.draw(cacheCanvas);
                    if (emojiPositions != null) emojiPositions.record();
                } finally {
                    layout.getPaint().setColor(color);
                    renderNode.endRecording();
                }
            }

            renderNode.setAlpha(layout.getPaint().getAlpha() / 255.0f);
            canvas.drawRenderNode(renderNode);
            if (emojiPositions != null) emojiPositions.restore();
            return;
        }

        layout.draw(canvas);
    }

    private int textColor;
    private int linkColor;
    private Typeface typeface;
    private float textSize;
    private final Rect lastLayoutBounds = new Rect();
    private boolean[] lastEmojiLoaded;
    private boolean[] tempEmojiLoaded;
    private Emoji.EmojiSpan[] cachedEmojiSpans;

    private boolean[] getEmojiLoaded() {
        if (!(getText() instanceof Spanned)) {
            return null;
        }
        Emoji.EmojiSpan[] spans = cachedEmojiSpans != null ? cachedEmojiSpans
                : ((Spanned) getText()).getSpans(0, getText().length(), Emoji.EmojiSpan.class);
        if (spans == null || spans.length <= 0)
            return null;
        if (tempEmojiLoaded == null || tempEmojiLoaded.length != spans.length) {
            tempEmojiLoaded = new boolean[spans.length];
        }
        for (int i = 0; i < spans.length; ++i) {
            tempEmojiLoaded[i] = spans[i].getDrawable() instanceof Emoji.EmojiDrawable && ((Emoji.EmojiDrawable) spans[i].getDrawable()).isLoaded();
        }
        return tempEmojiLoaded;
    }

    private boolean emojiLoadedEquals(boolean[] a, boolean[] b) {
        if (a == null && b == null) {
            return true;
        }
        if ((a == null ? 0 : a.length) != (b == null ? 0 : b.length)) {
            return false;
        }
        int n = a == null ? 0 : a.length;
        for (int i = 0; i < n; ++i) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    private boolean hasChanges() {
        // Snapshot emoji loading even when a paint change already invalidates the display list.
        // Otherwise the next draw unnecessarily rebuilds the same freshly recorded layout.
        boolean[] emojiLoaded = getEmojiLoaded();
        if (
            (ColorUtils.setAlphaComponent(textColor, 0xFF) != ColorUtils.setAlphaComponent(layout.getPaint().getColor(), 0xFF)) ||
            (ColorUtils.setAlphaComponent(linkColor, 0xFF) != ColorUtils.setAlphaComponent(layout.getPaint().linkColor, 0xFF)) ||
            (Math.abs(textSize - layout.getPaint().getTextSize()) > 0.1f) ||
            (typeface != layout.getPaint().getTypeface()) ||
            (!lastLayoutBounds.equals(getLayoutBounds())) ||
            (!emojiLoadedEquals(emojiLoaded, lastEmojiLoaded))
        ) {
            textColor = layout.getPaint().getColor();
            linkColor = layout.getPaint().linkColor;
            textSize = layout.getPaint().getTextSize();
            typeface = layout.getPaint().getTypeface();
            lastLayoutBounds.set(getLayoutBounds());
            if (emojiLoaded == null) {
                lastEmojiLoaded = null;
            } else {
                if (lastEmojiLoaded == null || lastEmojiLoaded.length != emojiLoaded.length) {
                    lastEmojiLoaded = new boolean[emojiLoaded.length];
                }
                System.arraycopy(emojiLoaded, 0, lastEmojiLoaded, 0, emojiLoaded.length);
            }
            return true;
        }
        return false;
    }


    private final Rect bounds = new Rect();

    private Rect getLayoutBounds() {
        bounds.set(0, 0, layout.getWidth(), layout.getHeight());
        return bounds;
    }

    public CharSequence getText() {
        return layout.getText();
    }

    public TextPaint getPaint() {
        return layout.getPaint();
    }

    public int getWidth() {
        return layout.getWidth();
    }

    public int getHeight() {
        return layout.getHeight();
    }

    public int getLineCount() {
        return layout.getLineCount();
    }

    public int getLineTop(int line) {
        return layout.getLineTop(line);
    }

    public int getLineBottom(int line) {
        return layout.getLineBottom(line);
    }

    public float getLineLeft(int line) {
        return layout.getLineLeft(line);
    }

    public float getLineRight(int line) {
        return layout.getLineRight(line);
    }

    public float getLineWidth(int line) {
        return layout.getLineWidth(line);
    }

    public float getPrimaryHorizontal(int x) {
        return layout.getPrimaryHorizontal(x);
    }

    public int getLineEnd(int line) {
        return layout.getLineEnd(line);
    }

    public int getLineStart(int line) {
        return layout.getLineStart(line);
    }

}
