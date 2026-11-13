package org.telegram.ui.Components.chat;

/** Keeps recorded caption bounds stable while the viewport moves within the same text lines. */
public final class CaptionDrawRange {
    private float top, bottom;
    private float viewportTop, viewportBottom;
    private float nextTop, nextBottom;
    private boolean valid;

    public void beginUpdate(float viewportTop, float viewportBottom) {
        this.viewportTop = viewportTop;
        this.viewportBottom = viewportBottom;
        nextTop = Float.POSITIVE_INFINITY;
        nextBottom = Float.NEGATIVE_INFINITY;
    }

    public void includeBlock(float blockTop, float blockBottom) {
        if (viewportTop < viewportBottom && blockTop <= viewportBottom && blockBottom >= viewportTop) {
            nextTop = Math.min(nextTop, blockTop);
            nextBottom = Math.max(nextBottom, blockBottom);
        }
    }

    public boolean finishUpdate() {
        if (nextTop == Float.POSITIVE_INFINITY) {
            nextTop = nextBottom = 0f;
        }
        final boolean changed = !valid || top != nextTop || bottom != nextBottom;
        top = nextTop;
        bottom = nextBottom;
        valid = true;
        return changed;
    }

    public float getTop() {
        return top;
    }

    public float getBottom() {
        return bottom;
    }

    public void reset() {
        valid = false;
    }
}
