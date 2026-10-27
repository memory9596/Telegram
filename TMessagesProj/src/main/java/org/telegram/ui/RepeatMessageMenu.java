package org.telegram.ui;

import android.view.View;

import java.util.List;

import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.SwipeBackSubmenu;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;

public class RepeatMessageMenu {
    public static boolean needsSwipeBack(List<Integer> options) {
        return options.contains(ChatActivity.OPTION_REPEAT);
    }

    public static boolean open(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout, View anchorCell, ChatActivity activity, Theme.ResourcesProvider themeDelegate, BlurredBackgroundDrawableViewFactory scrimBlur3Factory, SwipeBackSubmenu.ContentBuilder contentBuilder) {
        return SwipeBackSubmenu.open(popupLayout, anchorCell, activity.getContext(), themeDelegate, scrimBlur3Factory, contentBuilder);
    }
}