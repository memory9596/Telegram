/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.SparseIntArray;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Adapters.DialogsSearchAdapter;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HintDialogCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.ShareDialogCell;
import org.telegram.ui.Cells.ShareTopicCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceBitmap;
import org.telegram.utils.glass.source.GlassSourceRenderNode;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MessageStatisticActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.utils.glass.GlassEngine;
import org.telegram.utils.glass.GlassCaptureSourceCanvas;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.utils.glass.positions.GlassPositionsArray;
import org.telegram.utils.glass.positions.GlassPositionsMerger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;


public class ShareAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private static final int FILTER_TABS_HEIGHT = 50;
    private static final int SEARCH_BOTTOM_MARGIN = 2;

    private FrameLayout frameLayout;
    private FrameLayout frameLayout2;
    private EditTextEmoji commentTextView;
    private ChatActivityEnterView.SendButton writeButton;
    private ChatActivityEnterView.SendButton hideSendersNameButton;
    private ChatActivityEnterView.SendButton hideCaptionButton;
    private FrameLayout writeButtonContainer;
    private FrameLayout hideSendersNameButtonContainer;
    private FrameLayout hideCaptionButtonContainer;
    private FrameLayout pickerBottom;
    private float sheetBackgroundTop; // inner top of the sheet background, in containerView coordinates
    private float sheetPinnedToTopProgress;
    private View bottomFadeView;
    private FrameLayout pickerBottomLayout;
    private LinearLayout linkContainer;
    private TextView linkCopyButton;
    private SimpleTextView linkTextView;
    private FrameLayout bulletinContainer;
    public FrameLayout bulletinContainer2;
    private LinearLayout sharesCountLayout;
    private AnimatorSet animatorSet;
    private RecyclerListView topicsGridView;
    private RecyclerListView gridView;
    private RecyclerListView folderGridView;
    private RecyclerListView searchGridView;
    private GridLayoutManager layoutManager;
    private GridLayoutManager topicsLayoutManager;
    private FillLastGridLayoutManager searchLayoutManager;
    private ShareDialogsAdapter listAdapter;
    private ShareDialogsAdapter folderListAdapter;
    private GridLayoutManager folderLayoutManager;
    private int folderFromTabId = -1, folderTargetTabId = -1;
    private int folderSwipeDirection, folderSwipeWidth, folderSwipeHeight, folderGridTop;
    private boolean preparingFolderLayout;
    private ArrayList<TLRPC.Dialog> rootShareDialogs;
    private int shareDialogsGeneration;
    private final SparseIntArray filterDialogsGenerations = new SparseIntArray();
    private boolean folderTargetLocked, maybeFolderSwipe, trackingFolderSwipe, folderTouchConsumed, folderSwipeInterrupted;
    private float folderSwipeStartX, folderSwipeStartY;
    private int folderSwipePointerId;
    private VelocityTracker folderVelocityTracker;
    private ValueAnimator folderSwipeAnimator;
    private Runnable folderLockRunnable;
    private final ArrayList<FolderPage> folderPages = new ArrayList<>(3);
    private final FolderPagePreparer folderPagePreparer = new FolderPagePreparer();
    private FolderPage transitionFolderPage;
    private boolean pendingFolderCommit;
    private float folderPageTranslationY;
    private boolean lastFolderForward = true;
    private final Runnable requiredFolderLayout = () -> {
        FolderPage page = transitionFolderPage;
        if (page == null || this.shareClosed || folderTargetTabId < 0) return;
        layoutFolderPage(page, 0, true);
        if (transitionFolderPage != page || folderTargetTabId < 0) return;
        page.view.setVisibility(View.VISIBLE);
        folderPageTranslationY = folderGridTop - page.layoutTop;
        setFolderPageOffset(gridView.getTranslationX(), true);
        if (pendingFolderCommit) finishFolderTransition(true);
    };
    private ShareTopicsAdapter shareTopicsAdapter;
    private ShareSearchAdapter searchAdapter;
    private FilterTabsView filterTabsView;
    private boolean hasFilterTabs;
    private float filterTabsProgress;
    private ValueAnimator filterTabsAnimator;
    private int selectedDialogFilterId;
    protected ArrayList<MessageObject> sendingMessageObjects;
    private String[] sendingText = new String[2];
    private int hasPoll;
    private StickerEmptyView searchEmptyView;
    private Drawable shadowDrawable;
    private BlurredBackgroundDrawable sheetGlassBackground;
    private View sheetBackdropView;
    private GlassSourceRenderNode sheetBackdropSource;
    private GlassSourceRenderNode sheetBackdropFrostedSource;
    private GlassSourceColor sheetBackdropColor;
    private GlassSourceBitmap sheetBackdropBitmap;
    private int backdropThemeGeneration;
    private GlassCaptureSourceCanvas sheetBackdropCapture;
    private int sheetBackdropWidth, sheetBackdropHeight;
    private float sheetGlassRadius = -1;
    private final int[] backdropPosition = new int[2], sheetPosition = new int[2];
    private int sheetSourceOffsetX = Integer.MIN_VALUE, sheetSourceOffsetY = Integer.MIN_VALUE;
    private ViewTreeObserver sheetGlassObserver;
    private final ViewTreeObserver.OnPreDrawListener sheetGlassPositionListener = () -> {
        this.headerCaptureFrame++;
        updateSheetGeometry();
        updateSheetGlassPosition();
        return true;
    };
    private boolean sheetBackdropInvalidated = true;
    private boolean headerGlassNeedsCapture = true;
    private long lastHeaderCaptureFrame = Long.MIN_VALUE;
    private long headerCaptureFrame;
    private final IdentityHashMap<RecyclerListView, RenderNode> folderCaptureNodes = new IdentityHashMap<>();
    private final RectF folderCaptureBounds = new RectF();
    private static final int BOTTOM_SPACER_VIEW_TYPE = 2;
    private int topicsBottomSpacerHeight;
    private int pendingFolderGridTop = Integer.MIN_VALUE;
    private boolean capturingDialogList;
    private View headerFadeView;
    private BlurredBackgroundWithFadeDrawable headerFadeDrawable;
    private BlurredBackgroundWithFadeDrawable headerPinnedFadeDrawable;
    private BlurredBackgroundDrawable headerSurfaceGlass;
    private float headerSurfaceGlassRadius = -1;
    private static final int SHARE_FADE_HEIGHT_DP = 72;
    private static final int SHARE_GLASS_RADIUS_DP = 22;
    private final GlassPositionsArray headerCapturePositions = new GlassPositionsArray(4);
    private final GlassPositionsArray mergedHeaderCapturePositions = new GlassPositionsArray(4);
    private final GlassPositionsMerger headerCaptureMerger = new GlassPositionsMerger(Float.POSITIVE_INFINITY, dp(48));
    private View[] shadow = new View[2];
    private AnimatorSet[] shadowAnimation = new AnimatorSet[2];
    protected LongSparseArray<TLRPC.Dialog> selectedDialogs = new LongSparseArray<>();
    protected Map<TLRPC.Dialog, TLRPC.TL_forumTopic> selectedDialogTopics = new HashMap<>();
    private SwitchView switchView;
    private int containerViewTop = -1;
    private boolean fullyShown = false;
    private boolean includeStory;
    public boolean includeStoryFromMessage;

    public int timestamp;
    public FrameLayout timestampFrameLayout;
    public LinearLayout timestampLayout;
    public CheckBox2 timestampCheckbox;
    public TextView timestampTextView;

    private ChatActivity parentFragment;
    private Activity parentActivity;

    private boolean darkTheme;
    public boolean forceDarkThemeForHint;

    private RectF rect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private TLRPC.TL_exportedMessageLink exportedMessageLink;
    private boolean loadingLink;
    private boolean copyLinkOnEnd;

    private boolean isChannel;
    private String[] linkToCopy = new String[2];

    private int scrollOffsetY;
    private int previousScrollOffsetY;
    private int topBeforeSwitch;
    private boolean panTranslationMoveLayout;

    private ShareAlertDelegate delegate;
    private float currentPanTranslationY;

    private float captionEditTextTopOffset;
    private float chatActivityEnterViewAnimateFromTop;
    private ValueAnimator topBackgroundAnimator;

    RecyclerItemsEnterAnimator recyclerItemsEnterAnimator;
    FragmentSearchField searchView;
    ActionBar topicsBackActionBar;
    private boolean updateSearchAdapter;

    private SpringAnimation topicsAnimation;
    private TLRPC.Dialog selectedTopicDialog;

    private SizeNotifierFrameLayout sizeNotifierFrameLayout;
    private ArrayList<DialogsSearchAdapter.RecentSearchObject> recentSearchObjects = new ArrayList<>();
    private LongSparseArray<DialogsSearchAdapter.RecentSearchObject> recentSearchObjectsById = new LongSparseArray<>();
    TL_stories.StoryItem storyItem;

    private NotificationCenter.NotificationCenterDelegate pendingTopicsObserver;
    private Runnable pendingTopicsTimeout;
    private boolean shareClosed;

    public void setStoryToShare(TL_stories.StoryItem storyItem) {
        this.storyItem = storyItem;
    }

    public interface ShareAlertDelegate {
        default void didShare() {

        }

        default boolean didCopy() {
            return false;
        }
    }

    private boolean isFilterTabsVisible() {
        return hasFilterTabs && !searchIsVisible && selectedTopicDialog == null;
    }

    private int getFilterTabsHeight() {
        return Math.round(FILTER_TABS_HEIGHT * filterTabsProgress);
    }

    private int getTopPanelHeight() {
        return (darkTheme && linkToCopy[1] != null ? 102 : 49) + getFilterTabsHeight();
    }

    private int getTopSpacerHeight() {
        int heightWithoutTabs = darkTheme && linkToCopy[1] != null ? 109 : 56;
        int heightWithTabs = (darkTheme && linkToCopy[1] != null ? 102 : 49) + FILTER_TABS_HEIGHT - 2;
        return Math.round(AndroidUtilities.lerp(heightWithoutTabs, heightWithTabs, filterTabsProgress));
    }

    private MessagesController.DialogFilter getSelectedDialogFilter() {
        return getDialogFilter(selectedDialogFilterId);
    }

    private MessagesController.DialogFilter getDialogFilter(int filterId) {
        ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(currentAccount).getDialogFilters();
        for (int i = 0; i < filters.size(); i++) {
            MessagesController.DialogFilter filter = filters.get(i);
            if (filter != null && filter.id == filterId) {
                return filter;
            }
        }
        return null;
    }

    private void updateDialogAfterSearch(TLRPC.Dialog dialog, boolean moveToTop) {
        if (dialog == null || listAdapter == null) {
            return;
        }
        MessagesController.DialogFilter filter = getSelectedDialogFilter();
        if (filter != null && !filter.isDefault()) {
            listAdapter.fetchDialogs();
            return;
        }
        TLRPC.Dialog existingDialog = listAdapter.dialogsMap.get(dialog.id);
        if (existingDialog == null) {
            listAdapter.dialogsMap.put(dialog.id, dialog);
            listAdapter.dialogs.add(listAdapter.dialogs.isEmpty() ? 0 : 1, dialog);
        } else if (moveToTop && existingDialog.id != UserConfig.getInstance(currentAccount).clientUserId) {
            listAdapter.dialogs.remove(existingDialog);
            listAdapter.dialogs.add(listAdapter.dialogs.isEmpty() ? 0 : 1, existingDialog);
        }
        listAdapter.rootDialogsCount = listAdapter.dialogs.size();
        rootShareDialogs = new ArrayList<>(listAdapter.dialogs);
        shareDialogsGeneration++;
        filterDialogsGenerations.clear();
        listAdapter.loadedGeneration = shareDialogsGeneration;
        listAdapter.notifyDataSetChanged();
    }

    private void updateTopSpacerHeight(View view) {
        if (view == null || view.getLayoutParams() == null) {
            return;
        }
        int height = dp(getTopSpacerHeight());
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams.height != height) {
            layoutParams.height = height;
            view.setLayoutParams(layoutParams);
        }
    }

    private void updateTopPanelLayout() {
        int tabsHeight = getFilterTabsHeight();
        filterTabsView.setAlpha(filterTabsProgress);
        // Slide the tabs under search as the list takes over their reserved space.
        filterTabsView.setTranslationY(dp(FILTER_TABS_HEIGHT - tabsHeight) - dp(16) * (1f - filterTabsProgress));
        if (frameLayout != null && frameLayout.getLayoutParams() != null) {
            ViewGroup.LayoutParams layoutParams = frameLayout.getLayoutParams();
            layoutParams.height = dp(getTopPanelHeight());
            frameLayout.setLayoutParams(layoutParams);
        }
        if (searchView != null && searchView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) searchView.getLayoutParams();
            layoutParams.bottomMargin = dp(SEARCH_BOTTOM_MARGIN + tabsHeight);
            searchView.setLayoutParams(layoutParams);
        }
        if (topicsBackActionBar != null && topicsBackActionBar.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) topicsBackActionBar.getLayoutParams();
            layoutParams.bottomMargin = dp(tabsHeight);
            topicsBackActionBar.setLayoutParams(layoutParams);
        }
        if (shadow[0] != null && shadow[0].getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) shadow[0].getLayoutParams();
            layoutParams.topMargin = dp(getTopPanelHeight());
            shadow[0].setLayoutParams(layoutParams);
        }
        if (layoutManager != null) {
            updateTopSpacerHeight(layoutManager.findViewByPosition(0));
        }
        if (folderLayoutManager != null) {
            updateTopSpacerHeight(folderLayoutManager.findViewByPosition(0));
        }
        if (searchLayoutManager != null) {
            updateTopSpacerHeight(searchLayoutManager.findViewByPosition(0));
        }
        if (topicsLayoutManager != null) {
            updateTopSpacerHeight(topicsLayoutManager.findViewByPosition(0));
        }
        headerGlassNeedsCapture = true;
        if (containerView != null) {
            containerView.requestLayout();
        }
    }

    private void updateFilterTabsVisibility(boolean animated) {
        if (filterTabsView == null) {
            return;
        }
        boolean visible = isFilterTabsVisible();
        int visibility = visible ? View.VISIBLE : View.GONE;
        float progress = visible ? 1f : 0f;
        if (filterTabsAnimator != null) {
            filterTabsAnimator.removeAllListeners();
            filterTabsAnimator.cancel();
            filterTabsAnimator = null;
        }
        if (filterTabsProgress == progress && filterTabsView.getVisibility() == visibility) {
            return;
        }
        if (animated && fullyShown && !shareClosed) {
            filterTabsView.setVisibility(View.VISIBLE);
            filterTabsAnimator = ValueAnimator.ofFloat(filterTabsProgress, progress);
            filterTabsAnimator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
            filterTabsAnimator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
            filterTabsAnimator.addUpdateListener(animation -> {
                filterTabsProgress = (float) animation.getAnimatedValue();
                updateTopPanelLayout();
            });
            filterTabsAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    filterTabsAnimator = null;
                    filterTabsView.setVisibility(visibility);
                }
            });
            filterTabsAnimator.start();
        } else {
            filterTabsProgress = progress;
            filterTabsView.setVisibility(visibility);
            updateTopPanelLayout();
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (searchAdapter != null) {
            searchAdapter.notifyDataSetChanged();
        }
        if (shareTopicsAdapter != null) {
            shareTopicsAdapter.notifyDataSetChanged();
        }
    }

    private void updateFilterTabs(boolean animated) {
        if (filterTabsView == null) {
            return;
        }
        cancelFolderSwipe();
        ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(currentAccount).getDialogFilters();
        int defaultIndex = -1;
        int selectedIndex = -1;
        int tabsCount = 0;
        for (int i = 0; i < filters.size(); i++) {
            MessagesController.DialogFilter filter = filters.get(i);
            if (filter == null) {
                continue;
            }
            tabsCount++;
            if (filter.isDefault()) {
                defaultIndex = i;
            }
            if (filter.id == selectedDialogFilterId) {
                selectedIndex = i;
            }
        }
        if (defaultIndex < 0 && !filters.isEmpty()) {
            defaultIndex = 0;
        }
        if (selectedIndex < 0 || filters.get(selectedIndex) == null || filters.get(selectedIndex).locked) {
            selectedIndex = defaultIndex;
        }
        selectedDialogFilterId = selectedIndex >= 0 && filters.get(selectedIndex) != null ? filters.get(selectedIndex).id : 0;

        filterTabsView.stopAnimatingIndicator();
        filterTabsView.removeTabs();
        filterTabsView.resetTabId();
        for (int i = 0; i < filters.size(); i++) {
            MessagesController.DialogFilter filter = filters.get(i);
            if (filter == null) {
                continue;
            }
            if (filter.isDefault()) {
                filterTabsView.addTab(i, 0, LocaleController.getString(R.string.FilterAllChats), null, false, true, filter.locked);
            } else {
                filterTabsView.addTab(i, filter.localId, filter.name, filter.entities, filter.title_noanimate, false, filter.locked);
            }
        }
        if (selectedIndex >= 0 && selectedIndex < filters.size()) {
            MessagesController.DialogFilter filter = filters.get(selectedIndex);
            if (filter != null) {
                filterTabsView.selectTabWithStableId(filter.isDefault() ? 0 : filter.localId);
            }
        }
        filterTabsView.finishAddingTabs(animated);

        hasFilterTabs = tabsCount > 1;
        updateFilterTabsVisibility(false);
    }

    private FilterTabsView createFilterTabsView(Context context) {
        FilterTabsView tabsView = new FilterTabsView(context, resourcesProvider) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onInterceptTouchEvent(event);
            }
        };
        tabsView.setDelegate(new FilterTabsView.FilterTabsViewDelegate() {
            @Override
            public void onPageSelected(FilterTabsView.Tab tab, boolean forward) {
                if (tab.isLocked) {
                    filterTabsView.shakeLock(tab.id);
                    new LimitReachedBottomSheet(parentFragment, context, LimitReachedBottomSheet.TYPE_FOLDERS, currentAccount, resourcesProvider).show();
                    return;
                }
                ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(currentAccount).getDialogFilters();
                if (tab.id < 0 || tab.id >= filters.size()) {
                    return;
                }
                MessagesController.DialogFilter filter = filters.get(tab.id);
                if (filter == null || selectedDialogFilterId == filter.id) {
                    return;
                }
                filterTabsView.stopAnimatingIndicator();
                if (prepareFolderTransition(tab.id, forward)) {
                    filterTabsView.selectTabWithId(folderFromTabId, 1f);
                    filterTabsView.setEnabled(false);
                    animateFolderPageTo(folderSwipeDirection * folderSwipeWidth, 320, true);
                }
            }

            @Override
            public void onPageScrolled(float progress) {
                // The list and indicator share the same animator, after the next page is laid out.
            }

            @Override
            public void onSamePageSelected() {
                resetFolderGridKeepingSheetHeight();
            }

            @Override
            public int getTabCounter(int tabId) {
                return 0;
            }

            @Override
            public boolean didSelectTab(FilterTabsView.TabView tabView, boolean selected) {
                return false;
            }

            @Override
            public boolean isTabMenuVisible() {
                return false;
            }

            @Override
            public void onDeletePressed(int id) {

            }

            @Override
            public void onPageReorder(int fromId, int toId) {

            }

            @Override
            public boolean canPerformActions() {
                return !searchIsVisible && selectedTopicDialog == null && !trackingFolderSwipe && folderSwipeAnimator == null;
            }
        });
        BlurredBackgroundDrawable background = createShareGlass(tabsView);
        // Like COPY LINK, the glass body is 40dp high; text keeps its existing padding.
        background.setPadding(dp(5));
        tabsView.setPadding(0, dp(7), 0, dp(7));
        tabsView.setBlurredBackground(background);
        return tabsView;
    }

    private void resetFolderGridKeepingSheetHeight() {
        if (layoutManager == null || gridView == null) return;
        final View spacer = layoutManager.findViewByPosition(0);
        // A recycled spacer means the list is already expanded; keep the new folder at that height.
        pendingFolderGridTop = spacer != null ? Math.max(0, spacer.getTop()) : 0;
        headerGlassNeedsCapture = true;
        glassEngine.invalidate();
        layoutManager.scrollToPositionWithOffset(0, pendingFolderGridTop - gridView.getPaddingTop());
        containerView.requestLayout();
    }

    private RecyclerListView createDialogGridView(Context context, ShareDialogsAdapter adapter) {
        RecyclerListView list = new RecyclerListView(context, resourcesProvider) {
            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return folderTargetTabId < 0 && !folderTouchConsumed
                        && y >= getDialogListTop(this) && y < getDialogListBottom(this);
            }

        };
        list.setSelectorDrawableColor(0);
        list.setItemSelectorColorProvider(i -> 0);
        list.setPadding(0, 0, 0, dp(48));
        list.setClipToPadding(false);
        list.setClipChildren(false);
        list.setItemAnimator(null);
        GridLayoutManager manager = createDialogLayoutManager(context);
        list.setLayoutManager(manager);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == 0 ? 1 : manager.getSpanCount();
            }
        });
        list.setHorizontalScrollBarEnabled(false);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                RecyclerListView.Holder holder = (RecyclerListView.Holder) parent.getChildViewHolder(view);
                if (holder != null) {
                    int pos = holder.getAdapterPosition();
                    outRect.left = pos % 4 == 0 ? 0 : dp(4);
                    outRect.right = pos % 4 == 3 ? 0 : dp(4);
                } else {
                    outRect.left = dp(4);
                    outRect.right = dp(4);
                }
            }
        });
        list.setAdapter(adapter);
        ThemeBind.listGlowColor(list, Theme.key_dialogScrollGlow, resourcesProvider);
        list.setOnItemClickListener((view, position) -> {
            if (folderTargetTabId >= 0 || folderTouchConsumed || list != gridView || position < 0) return;
            TLRPC.Dialog dialog = adapter.getItem(position);
            if (dialog != null) selectDialog(view, dialog);
        });
        list.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int state) {
                if (recyclerView == gridView && state == RecyclerView.SCROLL_STATE_IDLE) folderPagePreparer.schedule();
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                folderCaptureNodes.remove((RecyclerListView) recyclerView);
                if (recyclerView != gridView) return;
                if (dy != 0 && folderTargetTabId < 0) {
                    updateLayout();
                    previousScrollOffsetY = scrollOffsetY;
                }
                if (Bulletin.getVisibleBulletin() != null && Bulletin.getVisibleBulletin().getLayout() != null
                        && Bulletin.getVisibleBulletin().getLayout().getParent() instanceof View
                        && ((View) Bulletin.getVisibleBulletin().getLayout().getParent()).getParent() == bulletinContainer2) {
                    Bulletin.hideVisible();
                }
            }
        });
        glassEngine.addScrolledView(list);
        return list;
    }

    private int getSelectedFolderTabId() {
        ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(currentAccount).getDialogFilters();
        for (int i = 0; i < filters.size(); i++) {
            if (filters.get(i) != null && filters.get(i).id == selectedDialogFilterId) return i;
        }
        return -1;
    }

    private int getNextFolderTabId(boolean forward) {
        final int fromId = getSelectedFolderTabId();
        for (int i = 0; i < filterTabsView.getTabsCount(); i++) {
            final FilterTabsView.Tab tab = filterTabsView.getTab(i);
            if (tab != null && tab.id == fromId) {
                final FilterTabsView.Tab next = filterTabsView.getTab(i + (forward ? 1 : -1));
                return next == null ? -1 : next.id;
            }
        }
        return -1;
    }

    private boolean prepareFolderTransition(int tabId, boolean forward) {
        ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(currentAccount).getDialogFilters();
        if (gridView == null || gridView.getWidth() == 0 || tabId < 0 || tabId >= filters.size()) return false;
        final MessagesController.DialogFilter filter = filters.get(tabId);
        if (filter == null || filter.id == selectedDialogFilterId) return false;
        if (folderTargetTabId >= 0) finishFolderTransition(false);
        cacheCurrentFolderPage();
        transitionFolderPage = getFolderPage(filter.id);
        folderGridView = transitionFolderPage.view;
        folderListAdapter = transitionFolderPage.adapter;
        folderLayoutManager = transitionFolderPage.manager;
        folderFromTabId = getSelectedFolderTabId();
        folderTargetTabId = tabId;
        folderTargetLocked = filter.locked;
        folderSwipeDirection = forward != LocaleController.isRTL ? -1 : 1;
        folderSwipeWidth = gridView.getWidth();
        folderSwipeHeight = gridView.getHeight();
        lastFolderForward = forward;
        pendingFolderCommit = false;
        final View spacer = layoutManager.findViewByPosition(0);
        folderGridTop = spacer == null ? 0 : Math.max(0, spacer.getTop());
        gridView.stopScroll();
        gridView.cancelClickRunnables(true);
        gridView.animate().setListener(null).cancel();
        gridView.setTag(1);
        gridView.setAlpha(1f);
        gridView.setScaleX(1f);
        gridView.setScaleY(1f);
        folderGridView.animate().setListener(null).cancel();
        folderGridView.setTag(1);
        folderGridView.setAlpha(1f);
        folderGridView.setScaleX(1f);
        folderGridView.setScaleY(1f);
        if (transitionFolderPage.isReady()) {
            refreshFolderPageSelection(folderGridView, folderListAdapter);
            folderGridView.setVisibility(View.VISIBLE);
        } else {
            // A cold page is laid out in the next traversal, never inside ACTION_MOVE.
            folderGridView.setVisibility(View.INVISIBLE);
            containerView.removeCallbacks(requiredFolderLayout);
            containerView.postOnAnimation(requiredFolderLayout);
        }
        folderPageTranslationY = folderGridTop - transitionFolderPage.layoutTop;
        setFolderPageOffset(0, false);
        return true;
    }

    private void refreshFolderPageSelection(RecyclerListView list, ShareDialogsAdapter adapter) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (!(child instanceof ShareDialogCell)) continue;
            ShareDialogCell cell = (ShareDialogCell) child;
            TLRPC.Dialog dialog = adapter.dialogsMap.get(cell.getCurrentDialog());
            if (dialog == null) continue;
            TLRPC.Dialog selected = selectedDialogs.get(dialog.id);
            cell.setChecked(selected != null, false);
            cell.setTopic(selectedDialogTopics.get(selected == null ? dialog : selected),
                    MessagesController.getInstance(currentAccount).isMonoForum(dialog.id), false);
        }
    }

    private FolderPage cacheCurrentFolderPage() {
        for (FolderPage page : folderPages) {
            if (page.view == gridView) {
                page.filterId = listAdapter.dialogFilterId;
                page.width = gridView.getWidth();
                page.height = gridView.getHeight();
                page.paddingTop = gridView.getPaddingTop();
                page.paddingBottom = gridView.getPaddingBottom();
                page.generation = listAdapter.loadedGeneration;
                View spacer = layoutManager.findViewByPosition(0);
                page.ready = spacer != null;
                page.layoutTop = spacer == null ? 0 : spacer.getTop();
                return page;
            }
        }
        FolderPage page = new FolderPage(gridView, listAdapter);
        folderPages.add(page);
        return cacheCurrentFolderPage();
    }

    private FolderPage getFolderPage(int filterId) {
        for (FolderPage page : folderPages) if (page.filterId == filterId) return page;
        if (folderPages.size() >= 3) {
            for (FolderPage page : folderPages) {
                if (page.view != gridView && page != transitionFolderPage) {
                    page.filterId = filterId;
                    page.ready = false;
                    page.preparationPosition = 0;
                    return page;
                }
            }
        }
        FolderPage page = new FolderPage(filterId);
        folderPages.add(page);
        return page;
    }

    private void layoutFolderPage(FolderPage page, int top, boolean refreshMetadata) {
        preparingFolderLayout = true;
        try {
            if (page.adapter.dialogFilterId != page.filterId || page.generation != shareDialogsGeneration) {
                // A retargeted or invalidated page cannot reuse cells bound to its previous data.
                page.clearPrepared();
            }
            boolean changed = page.adapter.fetchDialogs(page.filterId);
            page.view.stopScroll();
            page.view.setPadding(gridView.getPaddingLeft(), gridView.getPaddingTop(), gridView.getPaddingRight(), gridView.getPaddingBottom());
            page.manager.setNeedFixGap(containerView.getLayoutParams().height <= 0);
            updateDialogGridBottomSpacer(page.view, page.adapter, gridView.getHeight());
            if (!changed && refreshMetadata) refreshDialogGridCells(page.view, page.adapter);
            page.manager.scrollToPositionWithOffset(0, top - page.view.getPaddingTop());
            page.view.measure(View.MeasureSpec.makeMeasureSpec(gridView.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(gridView.getHeight(), View.MeasureSpec.EXACTLY));
            page.view.layout(gridView.getLeft(), gridView.getTop(), gridView.getRight(), gridView.getBottom());
            page.layoutTop = top;
            page.width = gridView.getWidth();
            page.height = gridView.getHeight();
            page.paddingTop = gridView.getPaddingTop();
            page.paddingBottom = gridView.getPaddingBottom();
            page.generation = shareDialogsGeneration;
            page.ready = true;
            folderCaptureNodes.remove(page.view);
        } finally {
            preparingFolderLayout = false;
        }
    }

    private final class FolderPage {
        final RecyclerListView view;
        final ShareDialogsAdapter adapter;
        final GridLayoutManager manager;
        final SparseArray<RecyclerView.ViewHolder> prepared = new SparseArray<>();
        int filterId, preparationPosition, width, height, paddingTop, paddingBottom, layoutTop;
        int generation = -1;
        boolean ready;

        FolderPage(RecyclerListView view, ShareDialogsAdapter adapter) {
            this.view = view;
            this.adapter = adapter;
            manager = (GridLayoutManager) view.getLayoutManager();
            filterId = adapter.dialogFilterId;
            installCacheExtension();
        }

        FolderPage(int filterId) {
            this.filterId = filterId;
            adapter = new ShareDialogsAdapter(getContext(), filterId, true);
            view = createDialogGridView(getContext(), adapter);
            manager = (GridLayoutManager) view.getLayoutManager();
            view.setVisibility(View.INVISIBLE);
            view.setRecycledViewPool(gridView.getRecycledViewPool());
            view.getRecycledViewPool().setMaxRecycledViews(0, 36);
            installCacheExtension();
            preparingFolderLayout = true;
            try {
                containerView.addView(view, containerView.indexOfChild(gridView) + 1,
                        LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            } finally {
                preparingFolderLayout = false;
            }
        }

        private void installCacheExtension() {
            view.setViewCacheExtension(new RecyclerView.ViewCacheExtension() {
                @Override
                public View getViewForPositionAndType(@NonNull RecyclerView.Recycler recycler, int position, int type) {
                    RecyclerView.ViewHolder holder = prepared.get(position);
                    if (holder == null || holder.getItemViewType() != type || generation != shareDialogsGeneration) return null;
                    prepared.remove(position);
                    return holder.itemView;
                }
            });
        }

        boolean isReady() {
            View sourceSpacer = layoutManager.findViewByPosition(0);
            int sourceTop = sourceSpacer == null ? 0 : Math.max(0, sourceSpacer.getTop());
            return ready && !view.isLayoutRequested() && generation == shareDialogsGeneration && adapter.dialogFilterId == filterId
                    && width == gridView.getWidth() && height == gridView.getHeight()
                    && paddingTop == gridView.getPaddingTop() && paddingBottom == gridView.getPaddingBottom()
                    && layoutTop <= sourceTop;
        }

        void clearPrepared() {
            for (int i = 0; i < prepared.size(); i++) view.mRecycler.recycleView(prepared.valueAt(i).itemView);
            prepared.clear();
        }
    }

    private final class FolderPagePreparer implements Runnable, Choreographer.FrameCallback {
        private long nextFrame;
        private boolean scheduled;

        void schedule() {
            if (scheduled || shareClosed || !fullyShown) return;
            scheduled = true;
            containerView.post(this);
        }

        void stop() {
            scheduled = false;
            containerView.removeCallbacks(this);
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override
        public void run() {
            if (!scheduled || shareClosed) return;
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!scheduled || shareClosed) return;
            if (!isFilterTabsVisible() || maybeFolderSwipe || trackingFolderSwipe
                    || gridView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE
                    || gridView.isComputingLayout()) {
                containerView.postDelayed(this, 80);
                return;
            }
            if (nextFrame == 0) nextFrame = frameTimeNanos;
            if (frameTimeNanos < nextFrame) { run(); return; }
            nextFrame += QuickShareCells.PREPARATION_INTERVAL_NS;
            if (nextFrame <= frameTimeNanos) nextFrame = frameTimeNanos + QuickShareCells.PREPARATION_INTERVAL_NS;
            cacheCurrentFolderPage();
            int fromId = folderTargetTabId >= 0 ? folderTargetTabId : getSelectedFolderTabId();
            int nextId = -1, previousId = -1;
            for (int i = 0; i < filterTabsView.getTabsCount(); i++) {
                FilterTabsView.Tab tab = filterTabsView.getTab(i);
                if (tab == null || tab.id != fromId) continue;
                FilterTabsView.Tab next = filterTabsView.getTab(i + (lastFolderForward ? 1 : -1));
                if (next != null) nextId = next.id;
                FilterTabsView.Tab previous = filterTabsView.getTab(i + (lastFolderForward ? -1 : 1));
                if (previous != null) previousId = previous.id;
                break;
            }
            ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(currentAccount).getDialogFilters();
            FolderPage page = null;
            for (int side = 0; side < 2; side++) {
                int candidate = side == 0 ? nextId : previousId;
                if (candidate < 0 || candidate >= filters.size() || filters.get(candidate) == null) continue;
                FolderPage possible = getFolderPage(filters.get(candidate).id);
                if (possible.view != gridView && possible != transitionFolderPage && !possible.isReady()) { page = possible; break; }
            }
            if (page == null) { scheduled = false; return; }
            if (page.ready) {
                page.ready = false;
                page.preparationPosition = 0;
            }
            if (page.adapter.dialogFilterId != page.filterId || page.generation != shareDialogsGeneration) {
                page.clearPrepared();
                preparingFolderLayout = true;
                try {
                    long recycleDeadline = System.nanoTime() + 2_000_000L;
                    int recycled = 0;
                    while (page.view.getChildCount() > 0 && recycled++ < 4) {
                        page.manager.removeAndRecycleViewAt(page.view.getChildCount() - 1, page.view.mRecycler);
                        if (System.nanoTime() >= recycleDeadline) break;
                    }
                    if (recycled > 0 || page.view.getChildCount() > 0) { run(); return; }
                    // Release the previous folder's attached cells before prebinding the next one.
                    // This lets rapid successive swipes reuse them instead of allocating a third set.
                    page.view.setAdapter(page.adapter);
                    page.adapter.fetchDialogs(page.filterId);
                    page.generation = shareDialogsGeneration;
                    page.preparationPosition = 0;
                    page.view.setPadding(gridView.getPaddingLeft(), gridView.getPaddingTop(), gridView.getPaddingRight(), gridView.getPaddingBottom());
                    updateDialogGridBottomSpacer(page.view, page.adapter, gridView.getHeight());
                } finally {
                    preparingFolderLayout = false;
                }
            }
            int rows = Math.max(1, (int) Math.ceil((gridView.getHeight() - gridView.getPaddingBottom() - dp(getTopSpacerHeight())) / (float) dp(103)));
            int end = Math.min(page.adapter.getItemCount(), 1 + rows * 4);
            if (page.preparationPosition < end) {
                long deadline = System.nanoTime() + 2_000_000L;
                int allocations = 0, count = 0;
                preparingFolderLayout = true;
                try {
                    while (page.preparationPosition < end && count++ < 4) {
                        int position = page.preparationPosition++;
                        RecyclerView.ViewHolder existing = page.view.findViewHolderForAdapterPosition(position);
                        if (existing != null) {
                            page.adapter.bindViewHolder(existing, position);
                        } else {
                            int type = page.adapter.getItemViewType(position);
                            RecyclerView.ViewHolder holder = page.view.getRecycledViewPool().getRecycledView(type);
                            if (holder == null) {
                                if (allocations > 0) { page.preparationPosition--; break; }
                                holder = page.adapter.createViewHolder(page.view, type);
                                allocations++;
                            }
                            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
                            params.mViewHolder = holder;
                            page.view.mRecycler.bindViewToPosition(holder.itemView, position);
                            int width = type == 0 ? Math.max(1, gridView.getWidth() / 4
                                    - (position % 4 == 0 ? 0 : dp(4)) - (position % 4 == 3 ? 0 : dp(4))) : gridView.getWidth();
                            holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(type == 0 ? dp(103) : params.height, View.MeasureSpec.EXACTLY));
                            holder.itemView.layout(0, 0, holder.itemView.getMeasuredWidth(), holder.itemView.getMeasuredHeight());
                            page.prepared.put(position, holder);
                        }
                        if (System.nanoTime() >= deadline) break;
                    }
                } finally {
                    preparingFolderLayout = false;
                }
                run();
            } else {
                layoutFolderPage(page, 0, false);
                scheduled = false;
                schedule();
            }
        }
    }

    private void refreshDialogGridCells(RecyclerListView list, ShareDialogsAdapter adapter) {
        for (int i = 0; i < list.getChildCount(); i++) {
            final View child = list.getChildAt(i);
            if (!(child instanceof ShareDialogCell)) continue;
            final ShareDialogCell cell = (ShareDialogCell) child;
            final long dialogId = cell.getCurrentDialog();
            final TLRPC.Dialog dialog = adapter.dialogsMap.get(dialogId);
            if (dialog == null) continue;
            final TLRPC.Dialog selected = selectedDialogs.get(dialogId);
            // Rebind current profile and selection values; unchanged names keep their measured layout.
            cell.setDialog(dialogId, selected != null, null);
            cell.setTopic(selectedDialogTopics.get(selected == null ? dialog : selected),
                    MessagesController.getInstance(currentAccount).isMonoForum(dialogId), false);
        }
    }

    private void setFolderPageOffset(float offset, boolean updateTabs) {
        if (folderTargetTabId < 0) return;
        final float distance = Math.max(0, Math.min(folderSwipeWidth, offset * folderSwipeDirection));
        offset = distance * folderSwipeDirection;
        final boolean moved = gridView.getTranslationX() != offset
                || folderGridView.getTranslationX() != offset - folderSwipeDirection * folderSwipeWidth
                || folderGridView.getTranslationY() != gridView.getTranslationY() + folderPageTranslationY;
        gridView.setTranslationX(offset);
        folderGridView.setTranslationX(offset - folderSwipeDirection * folderSwipeWidth);
        folderGridView.setTranslationY(gridView.getTranslationY() + folderPageTranslationY);
        if (updateTabs) {
            // Keep the source tab selected until the gesture commits, even at the far edge.
            filterTabsView.selectTabWithId(folderTargetTabId, Math.min(.999f, distance / folderSwipeWidth));
        }
        if (moved) {
            headerGlassNeedsCapture = true;
            glassEngine.invalidateScroll();
            containerView.invalidate();
        }
    }

    private void finishFolderTransition(boolean commit) {
        if (folderTargetTabId < 0) return;
        if (commit && transitionFolderPage != null && !transitionFolderPage.ready) {
            pendingFolderCommit = true;
            gridView.setTranslationX(0);
            return;
        }
        final int selectedTab = commit ? folderTargetTabId : folderFromTabId;
        if (commit) {
            if (transitionFolderPage != null) {
                final int delta = folderGridTop - transitionFolderPage.layoutTop;
                folderGridView.offsetChildrenVertical(delta);
                transitionFolderPage.layoutTop = folderGridTop;
                folderGridView.setTranslationY(gridView.getTranslationY());
                folderCaptureNodes.remove(folderGridView);
            }
            RecyclerListView previousGrid = gridView;
            ShareDialogsAdapter previousAdapter = listAdapter;
            GridLayoutManager previousLayoutManager = layoutManager;
            gridView = folderGridView;
            listAdapter = folderListAdapter;
            layoutManager = folderLayoutManager;
            folderGridView = previousGrid;
            folderListAdapter = previousAdapter;
            folderLayoutManager = previousLayoutManager;
            selectedDialogFilterId = listAdapter.dialogFilterId;
            pendingFolderGridTop = Integer.MIN_VALUE;
        }
        gridView.setTranslationX(0);
        folderGridView.setTranslationX(0);
        // Retain laid-out cells for the next switch without drawing the inactive page.
        folderGridView.setVisibility(View.INVISIBLE);
        folderTargetTabId = folderFromTabId = -1;
        transitionFolderPage = null;
        pendingFolderCommit = false;
        containerView.removeCallbacks(requiredFolderLayout);
        filterTabsView.stopAnimatingIndicator();
        filterTabsView.selectTabWithId(selectedTab, 1f);
        filterTabsView.setEnabled(!trackingFolderSwipe);
        headerGlassNeedsCapture = true;
        glassEngine.invalidate();
        if (!shareClosed) {
            updateLayout();
            containerView.invalidate();
            folderPagePreparer.schedule();
        }
    }

    private void recycleFolderVelocityTracker() {
        if (folderVelocityTracker != null) {
            folderVelocityTracker.recycle();
            folderVelocityTracker = null;
        }
        maybeFolderSwipe = false;
    }

    private void cancelFolderSwipe() {
        containerView.removeCallbacks(requiredFolderLayout);
        pendingFolderCommit = false;
        if (folderSwipeAnimator != null) {
            folderSwipeAnimator.removeAllListeners();
            folderSwipeAnimator.cancel();
            folderSwipeAnimator = null;
        }
        if (folderLockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(folderLockRunnable);
            folderLockRunnable = null;
        }
        trackingFolderSwipe = folderTouchConsumed = false;
        recycleFolderVelocityTracker();
        finishFolderTransition(false);
    }

    private boolean interceptFolderSwipe(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            folderTouchConsumed = false;
            final float y = event.getY() - (gridView == null ? 0 : gridView.getY());
            if (shareClosed || !isFilterTabsVisible() || gridView == null
                    || y < getDialogListTop(gridView) || y >= getDialogListBottom(gridView)) {
                recycleFolderVelocityTracker();
                return folderTargetTabId >= 0;
            }
            folderSwipeStartX = event.getX();
            folderSwipeStartY = event.getY();
            folderSwipePointerId = event.getPointerId(0);
            recycleFolderVelocityTracker();
            folderVelocityTracker = VelocityTracker.obtain();
            folderVelocityTracker.addMovement(event);
            folderSwipeInterrupted = folderTargetTabId >= 0;
            if (folderSwipeInterrupted) {
                if (folderSwipeAnimator != null) {
                    folderSwipeAnimator.removeAllListeners();
                    folderSwipeAnimator.cancel();
                    folderSwipeAnimator = null;
                }
                pendingFolderCommit = false;
                final boolean touchOnTarget = folderSwipeDirection < 0
                        ? event.getX() >= gridView.getX() + folderSwipeWidth
                        : event.getX() < folderGridView.getX() + folderSwipeWidth;
                if (touchOnTarget && !folderTargetLocked && transitionFolderPage != null && transitionFolderPage.ready) {
                    // As in DialogsActivity, the visible page under the new finger becomes active.
                    // Retain the unfinished offset so another swipe can continue to the next folder.
                    final int previousTabId = folderFromTabId;
                    final boolean wasForward = (folderSwipeDirection < 0) != LocaleController.isRTL;
                    final float remainingOffset = folderGridView.getTranslationX();
                    finishFolderTransition(true);
                    if (remainingOffset != 0 && prepareFolderTransition(previousTabId, !wasForward)) {
                        setFolderPageOffset(remainingOffset, true);
                    }
                } else {
                    filterTabsView.stopAnimatingIndicator();
                    filterTabsView.selectTabWithId(folderFromTabId, 1f);
                    setFolderPageOffset(gridView.getTranslationX(), true);
                }
                folderSwipeStartX -= gridView.getTranslationX();
                trackingFolderSwipe = folderTouchConsumed = true;
                filterTabsView.setEnabled(false);
                return true;
            }
            maybeFolderSwipe = filterTabsView.isEnabled() && !filterTabsView.isAnimatingIndicator();
        } else if (maybeFolderSwipe && action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() != 1 || event.getPointerId(0) != folderSwipePointerId || !isFilterTabsVisible()) {
                recycleFolderVelocityTracker();
                return false;
            }
            final float dx = event.getX() - folderSwipeStartX;
            final float dy = Math.abs(event.getY() - folderSwipeStartY);
            if (Math.abs(dx) >= AndroidUtilities.getPixelsInCM(.3f, true) && Math.abs(dx) > dy) {
                final boolean forward = LocaleController.isRTL ? dx > 0 : dx < 0;
                if (prepareFolderTransition(getNextFolderTabId(forward), forward)) {
                    folderSwipeStartX = event.getX();
                    maybeFolderSwipe = false;
                    trackingFolderSwipe = folderTouchConsumed = true;
                    filterTabsView.setEnabled(false);
                    return true;
                }
                recycleFolderVelocityTracker();
            } else if (dy > ViewConfiguration.get(getContext()).getScaledTouchSlop() && dy >= Math.abs(dx)) {
                recycleFolderVelocityTracker();
            } else {
                folderVelocityTracker.addMovement(event);
            }
        } else if (!trackingFolderSwipe && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_DOWN)) {
            recycleFolderVelocityTracker();
        }
        return trackingFolderSwipe || folderTouchConsumed || folderTargetTabId >= 0;
    }

    private boolean handleFolderSwipe(MotionEvent event) {
        final int action = event.getActionMasked();
        final boolean consumed = trackingFolderSwipe || folderTouchConsumed || folderTargetTabId >= 0;
        if (!consumed) return false;
        if (trackingFolderSwipe && action != MotionEvent.ACTION_DOWN) {
            folderVelocityTracker.addMovement(event);
            if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1) {
                float dx = event.getX() - folderSwipeStartX;
                if (dx != 0 && (folderTargetTabId < 0 || dx * folderSwipeDirection < 0)) {
                    final boolean forward = LocaleController.isRTL ? dx > 0 : dx < 0;
                    if (!prepareFolderTransition(getNextFolderTabId(forward), forward)) {
                        finishFolderTransition(false);
                    }
                }
                setFolderPageOffset(dx, true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_POINTER_DOWN || event.getPointerCount() != 1) {
                settleFolderSwipe(action != MotionEvent.ACTION_UP);
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            folderTouchConsumed = false;
            if (containerView.getParent() != null) containerView.getParent().requestDisallowInterceptTouchEvent(false);
        } else if (containerView.getParent() != null) {
            containerView.getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
    }

    private void settleFolderSwipe(boolean cancelled) {
        float velocityX = 0, velocityY = 0;
        if (!cancelled && folderVelocityTracker != null) {
            folderVelocityTracker.computeCurrentVelocity(1000, ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity());
            velocityX = folderVelocityTracker.getXVelocity(folderSwipePointerId);
            velocityY = folderVelocityTracker.getYVelocity(folderSwipePointerId);
        }
        trackingFolderSwipe = false;
        recycleFolderVelocityTracker();
        if (folderTargetTabId < 0) {
            filterTabsView.setEnabled(true);
            return;
        }
        final float offset = gridView.getTranslationX();
        final boolean back = cancelled || folderTargetLocked || (folderSwipeInterrupted
                ? (Math.abs(velocityX) > 1500 ? velocityX * folderSwipeDirection < 0 : Math.abs(offset) < folderSwipeWidth / 2f)
                : Math.abs(offset) < folderSwipeWidth / 3f && (Math.abs(velocityX) < 3500 || Math.abs(velocityX) < Math.abs(velocityY)));
        if (folderTargetLocked && !cancelled && (Math.abs(offset) > folderSwipeWidth * .3f || Math.abs(velocityX) >= 3000)) {
            filterTabsView.shakeLock(folderTargetTabId);
            folderLockRunnable = () -> {
                folderLockRunnable = null;
                if (!shareClosed) new LimitReachedBottomSheet(parentFragment, getContext(), LimitReachedBottomSheet.TYPE_FOLDERS, currentAccount, resourcesProvider).show();
            };
            AndroidUtilities.runOnUIThread(folderLockRunnable, 200);
        }
        final float target = back ? 0 : folderSwipeDirection * folderSwipeWidth;
        final float dx = Math.abs(target - offset);
        final float halfWidth = folderSwipeWidth / 2f;
        final float distance = halfWidth + halfWidth * AndroidUtilities.distanceInfluenceForSnapDuration(Math.min(1f, dx / folderSwipeWidth));
        final float speed = Math.abs(velocityX);
        int duration = speed > 0 ? 4 * Math.round(1000 * Math.abs(distance / speed)) : (int) ((dx / folderSwipeWidth + 1f) * 100);
        animateFolderPageTo(target, Math.max(150, Math.min(duration, 600)), !back);
    }

    private void animateFolderPageTo(float target, int duration, boolean commit) {
        if (Math.abs(target - gridView.getTranslationX()) < 1f) {
            setFolderPageOffset(target, true);
            finishFolderTransition(commit);
            return;
        }
        folderSwipeAnimator = ValueAnimator.ofFloat(gridView.getTranslationX(), target);
        folderSwipeAnimator.setDuration(duration);
        // Use the same quintic easing and snap timing as DialogsActivity.
        folderSwipeAnimator.setInterpolator(t -> { t -= 1f; return t * t * t * t * t + 1f; });
        folderSwipeAnimator.addUpdateListener(animation -> setFolderPageOffset((float) animation.getAnimatedValue(), true));
        folderSwipeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                folderSwipeAnimator = null;
                finishFolderTransition(commit);
            }
        });
        folderSwipeAnimator.start();
        folderPagePreparer.schedule();
    }

    private void updateSearchFieldBackground() {
        if (searchView != null) {
            searchView.setBlurredBackgroundVisibility(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 1f : 0f);
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private class SwitchView extends FrameLayout {

        private View searchBackground;
        private SimpleTextView rightTab;
        private SimpleTextView leftTab;
        private View slidingView;
        private int currentTab;
        private float tabSwitchProgress;
        private AnimatorSet animator;
        private LinearGradient linearGradient;
        private Paint paint;
        private RectF rect;
        private int lastColor;

        public SwitchView(Context context) {
            super(context);

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            rect = new RectF();

            searchBackground = new View(context);
            ThemeBind.fillBackground(searchBackground, Theme.createRoundRectDrawable(dp(18), getThemedColor(Theme.key_dialogSearchBackground)), ThemeBind.of(Theme.key_dialogSearchBackground), ShareAlert.this.resourcesProvider);
            addView(searchBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 0, 14, 0));

            slidingView = new View(context) {
                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    invalidate();
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);

                    int color01 = 0xff75CB6B;
                    int color02 = 0xff4FAFBE;
                    int color11 = 0xff5F94F5;
                    int color12 = 0xffB95A91;

                    int color0 = AndroidUtilities.getOffsetColor(color01, color11, getTranslationX() / getMeasuredWidth(), 1.0f);
                    int color1 = AndroidUtilities.getOffsetColor(color02, color12, getTranslationX() / getMeasuredWidth(), 1.0f);
                    if (color0 != lastColor) {
                        linearGradient = new LinearGradient(0, 0, getMeasuredWidth(), 0, new int[]{color0, color1}, null, Shader.TileMode.CLAMP);
                        paint.setShader(linearGradient);
                    }
                    rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    canvas.drawRoundRect(rect, dp(18), dp(18), paint);
                }
            };
            addView(slidingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 0, 14, 0));

            leftTab = new SimpleTextView(context);
            ThemeBind.textColor(leftTab, Theme.key_voipgroup_nameText, ShareAlert.this.resourcesProvider);
            leftTab.setTextSize(13);
            leftTab.setLeftDrawable(R.drawable.msg_tabs_mic1);
            leftTab.setText(LocaleController.getString(R.string.VoipGroupInviteCanSpeak));
            leftTab.setGravity(Gravity.CENTER);
            addView(leftTab, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 14, 0, 0, 0));
            leftTab.setOnClickListener(v -> switchToTab(0));

            rightTab = new SimpleTextView(context);
            ThemeBind.textColor(rightTab, Theme.key_voipgroup_nameText, ShareAlert.this.resourcesProvider);
            rightTab.setTextSize(13);
            rightTab.setLeftDrawable(R.drawable.msg_tabs_mic2);
            rightTab.setText(LocaleController.getString(R.string.VoipGroupInviteListenOnly));
            rightTab.setGravity(Gravity.CENTER);
            addView(rightTab, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 14, 0));
            rightTab.setOnClickListener(v -> switchToTab(1));
        }

        protected void onTabSwitch(int num) {

        }

        private void switchToTab(int tab) {
            if (currentTab == tab) {
                return;
            }
            currentTab = tab;
            if (animator != null) {
                animator.cancel();
            }
            animator = new AnimatorSet();
            animator.playTogether(ObjectAnimator.ofFloat(slidingView, View.TRANSLATION_X, currentTab == 0 ? 0 : slidingView.getMeasuredWidth()));
            animator.setDuration(180);
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animator = null;
                }
            });
            animator.start();
            onTabSwitch(currentTab);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec) - dp(28);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) leftTab.getLayoutParams();
            layoutParams.width = width / 2;

            layoutParams = (FrameLayout.LayoutParams) rightTab.getLayoutParams();
            layoutParams.width = width / 2;
            layoutParams.leftMargin = width / 2 + dp(14);

            layoutParams = (FrameLayout.LayoutParams) slidingView.getLayoutParams();
            layoutParams.width = width / 2;
            if (animator != null) {
                animator.cancel();
            }
            slidingView.setTranslationX(currentTab == 0 ? 0 : layoutParams.width);

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public static ShareAlert createShareAlert(final Context context, MessageObject messageObject, final String text, boolean channel, final String copyLink, boolean fullScreen) {
        ArrayList<MessageObject> arrayList;
        if (messageObject != null) {
            arrayList = new ArrayList<>();
            arrayList.add(messageObject);
        } else {
            arrayList = null;
        }
        return new ShareAlert(context, null, arrayList, text, null, channel, copyLink, null, fullScreen, false);
    }

    public ShareAlert(final Context context, ArrayList<MessageObject> messages, final String text, boolean channel, final String copyLink, boolean fullScreen) {
        this(context, messages, text, channel, copyLink, fullScreen, null);
    }

    public ShareAlert(final Context context, ArrayList<MessageObject> messages, final String text, boolean channel, final String copyLink, boolean fullScreen, Theme.ResourcesProvider resourcesProvider) {
        this(context, null, messages, text, null, channel, copyLink, null, fullScreen, false, false, null, resourcesProvider);
    }

    public ShareAlert(final Context context, ChatActivity fragment, ArrayList<MessageObject> messages, final String text, final String text2, boolean channel, final String copyLink, final String copyLink2, boolean fullScreen, boolean forCall) {
        this(context, fragment, messages, text, text2, channel, copyLink, copyLink2, fullScreen, forCall, false, null, null);
    }

    public ShareAlert(final Context context, ChatActivity fragment, ArrayList<MessageObject> messages, final String text, final String text2, boolean channel, final String copyLink, final String copyLink2, boolean fullScreen, boolean forCall, boolean includeStory, Integer video_timestamp, Theme.ResourcesProvider theme) {
        this(context, fragment, messages, text, text2, channel, copyLink, copyLink2, fullScreen, forCall, includeStory, video_timestamp, theme, false);
    }

    public ShareAlert(final Context context, ChatActivity fragment, ArrayList<MessageObject> messages, final String text, final String text2, boolean channel, final String copyLink, final String copyLink2, boolean fullScreen, boolean forCall, boolean includeStory, Integer video_timestamp, Theme.ResourcesProvider theme, boolean groupAnyItems) {
        super(context, true, theme);
        AndroidUtilities.enableEdgeToEdge(getWindow());

        iBlur3SourceColor = glassEngine.createThemedColorSource(() -> getThemedColor(Theme.key_windowBackgroundWhite));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedConfig.chatBlurEnabled()) {
            scrollableViewNoiseSuppressor = new DownscaleScrollableNoiseSuppressor();
            glassEngine.addNoiseSuppressor(scrollableViewNoiseSuppressor);

            iBlur3SourceGlass = new GlassSourceRenderNode(iBlur3SourceColor);
            iBlur3SourceGlass.setUnderSource(iBlur3SourceColor);
            iBlur3SourceGlass.setScrollableNoiseSuppressor(scrollableViewNoiseSuppressor, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);

            iBlur3SourceGlassFrosted = new GlassSourceRenderNode(iBlur3SourceColor);
            iBlur3SourceGlassFrosted.setUnderSource(iBlur3SourceColor);
            iBlur3SourceGlassFrosted.setScrollableNoiseSuppressor(scrollableViewNoiseSuppressor, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);

            iBlur3FactoryLiquidGlass = new BlurredBackgroundDrawableViewFactory(iBlur3SourceGlass);
            iBlur3FactoryLiquidGlass.setLiquidGlassEffectAllowed(true);
            iBlur3FactoryLiquidGlass.setOutset(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? dp(8) : dp(48));
            iBlur3FactoryLiquidGlass.setGlassEngine(glassEngine);

            iBlur3FactoryFrostedLiquidGlass = new BlurredBackgroundDrawableViewFactory(iBlur3SourceGlassFrosted);
            iBlur3FactoryFrostedLiquidGlass.setLiquidGlassEffectAllowed(true);
            iBlur3FactoryFrostedLiquidGlass.setOutset(dp(48));
            iBlur3FactoryFrostedLiquidGlass.setGlassEngine(glassEngine);

            iBlur3FactoryFade = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
        } else {
            scrollableViewNoiseSuppressor = null;
            iBlur3SourceGlassFrosted = null;
            iBlur3SourceGlass = null;

            final BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
            iBlur3FactoryFade = iBlur3FactoryLiquidGlass = iBlur3FactoryFrostedLiquidGlass  = factory;
        }

        this.resourcesProvider = theme;
        this.includeStory = includeStory;

        parentActivity = AndroidUtilities.findActivity(context);

        darkTheme = forCall;
        if (darkTheme) {
            this.resourcesProvider = new DarkThemeResourceProvider() {
                @Override
                public void appendColors() {
                    sparseIntArray.put(Theme.key_windowBackgroundGray, getColor(Theme.key_dialogBackground));
                    sparseIntArray.put(Theme.key_divider, 0xFF171615);
                    sparseIntArray.put(Theme.key_chat_messagePanelIcons, Theme.multAlpha(Color.WHITE, .45f));
                    sparseIntArray.put(Theme.key_dialogBackgroundGray, 0x14FFFFFF);

                    sparseIntArray.put(Theme.key_chat_emojiPanelBackground, getColor(Theme.key_dialogBackground));
                }
            };
        }

        iBlur3SourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));

        parentFragment = fragment;
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        int backgroundColor = getThemedColor(behindKeyboardColorKey = Theme.key_dialogBackground);
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));
//        fixNavigationBar(backgroundColor);
        occupyNavigationBarWithoutKeyboard = true;

        isFullscreen = fullScreen;
        linkToCopy[0] = copyLink;
        linkToCopy[1] = copyLink2;
        if (copyLink == null && copyLink2 == null && messages != null && messages.size() > 0) {
            final MessageObject message = messages.get(0);
            final String username = DialogObject.getPublicUsername(MessagesController.getInstance(currentAccount).getUserOrChat(message.getDialogId()));
            if (!TextUtils.isEmpty(username)) {
                linkToCopy[0] = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + username + "/" + message.getId();
                if (messages.size() == 1 && message.hasValidGroupId()) {
                    linkToCopy[0] += "?single";
                }
            }
        }
        sendingMessageObjects = messages;
        searchAdapter = new ShareSearchAdapter(context);
        isChannel = channel;
        sendingText[0] = text;
        sendingText[1] = text2;
        useSmoothKeyboard = true;

        super.setDelegate(new BottomSheetDelegate() {
            @Override
            public void onOpenAnimationEnd() {
                fullyShown = true;
                folderPagePreparer.schedule();
                updateSheetGlassPosition();
                glassEngine.invalidate();
                containerView.postInvalidateOnAnimation();
            }
        });

        if (sendingMessageObjects != null) {
            for (int a = 0, N = sendingMessageObjects.size(); a < N; a++) {
                MessageObject messageObject = sendingMessageObjects.get(a);
                if (messageObject.isTodo()) {
                    hasPoll = 3;
                } else if (messageObject.isPoll()) {
                    hasPoll = messageObject.isPublicPoll() ? 2 : 1;
                    if (hasPoll == 2) {
                        break;
                    }
                }
            }
        }

        if (channel) {
            loadingLink = true;
            TLRPC.TL_channels_exportMessageLink req = new TLRPC.TL_channels_exportMessageLink();
            req.id = messages.get(0).getId();
            req.channel = MessagesController.getInstance(currentAccount).getInputChannel(messages.get(0).messageOwner.peer_id.channel_id);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response != null) {
                    exportedMessageLink = (TLRPC.TL_exportedMessageLink) response;
                    updateLinkTextView();
                    if (copyLinkOnEnd) {
                        copyLink(context);
                    }
                }
                loadingLink = false;
            }));
        }


        sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            @Override
            public int measureKeyboardHeight() {
                return attachedFragment != null ? attachedKeyboardHeight : super.measureKeyboardHeight();
            }

            private boolean ignoreLayout = false;
            private RectF rect1 = new RectF();
            private final Paint dragHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private boolean fullHeight;
            private int topOffset;
            private int previousTopOffset;

            private int fromScrollY;
            private int toScrollY;

            private int fromOffsetTop;
            private int toOffsetTop;


            {
                adjustPanLayoutHelper = new AdjustPanLayoutHelper(this) {

                    private float filterTabsFrom = -1f;
                    private float filterTabsTo;

                    @Override
                    protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {
                        super.onTransitionStart(keyboardVisible, contentHeight);
                        // Hide from the first search keyboard frame, before the bottom controls move up.
                        hidePickerBottomDuringKeyboardTransition = keyboardVisible && searchView.editText.hasFocus();
                        filterTabsFrom = -1f;
                        if (hasFilterTabs && selectedTopicDialog == null && topicsAnimation == null) {
                            if (filterTabsAnimator != null) {
                                filterTabsAnimator.removeAllListeners();
                                filterTabsAnimator.cancel();
                                filterTabsAnimator = null;
                            }
                            filterTabsFrom = filterTabsProgress;
                            filterTabsTo = isFilterTabsVisible() ? 1f : 0f;
                            filterTabsView.setVisibility(View.VISIBLE);
                        }
                        if (previousScrollOffsetY != scrollOffsetY) {
                            fromScrollY = previousScrollOffsetY;
                            toScrollY = scrollOffsetY;
                            panTranslationMoveLayout = true;
                            scrollOffsetY = fromScrollY;
                        } else {
                            fromScrollY = -1;
                        }

                        if (topOffset != previousTopOffset) {
                            fromOffsetTop = 0;
                            toOffsetTop = 0;
                            panTranslationMoveLayout = true;

                            if (!keyboardVisible) {
                                toOffsetTop -= topOffset - previousTopOffset;
                            } else {
                                toOffsetTop += topOffset - previousTopOffset;
                            }
                            scrollOffsetY = keyboardVisible ? fromScrollY : toScrollY;
                        } else {
                            fromOffsetTop = -1;
                        }
                        gridView.setTopGlowOffset((int) (currentPanTranslationY + scrollOffsetY));
                        frameLayout.setTranslationY(currentPanTranslationY + scrollOffsetY);
                        searchEmptyView.setTranslationY(currentPanTranslationY + scrollOffsetY);
                        invalidate();
                    }

                    @Override
                    protected void onTransitionEnd() {
                        super.onTransitionEnd();
                        hidePickerBottomDuringKeyboardTransition = false;
                        if (filterTabsFrom >= 0) {
                            filterTabsFrom = -1f;
                            updateFilterTabsVisibility(false);
                        }
                        keyboardT = commentTextView != null && commentTextView.isPopupVisible() || keyboardSize2 > dp(20) ? 1.0f : 0.0f;
                        panTranslationMoveLayout = false;
                        previousScrollOffsetY = scrollOffsetY;
                        gridView.setTopGlowOffset(scrollOffsetY);
                        frameLayout.setTranslationY(scrollOffsetY);
                        searchEmptyView.setTranslationY(scrollOffsetY);
                        gridView.setTranslationY(0);
                        searchGridView.setTranslationY(0);
                        updateBottomOverlay();
                    }

                    @Override
                    protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {
                        keyboardT = progress;
                        super.onPanTranslationUpdate(y, progress, keyboardVisible);
                        if (filterTabsFrom >= 0 && !shareClosed && selectedTopicDialog == null) {
                            float p = keyboardVisible ? progress : 1f - progress;
                            filterTabsProgress = AndroidUtilities.lerp(filterTabsFrom, filterTabsTo, p);
                            updateTopPanelLayout();
                        }
                        for (int i = 0; i < containerView.getChildCount(); i++) {
                            final View child = containerView.getChildAt(i);
                            // The bottom fade follows the resized parent, just like the bottom controls.
                            if (child != bottomFadeView && child != pickerBottom && child != bulletinContainer && child != shadow[1] && child != sharesCountLayout && child != frameLayout2 && child != timestampFrameLayout && child != writeButtonContainer && child != hideSendersNameButtonContainer && child != hideCaptionButtonContainer) {
                                child.setTranslationY(y);
                            }
                        }
                        currentPanTranslationY = y;
                        if (fromScrollY != -1) {
                            float p = keyboardVisible ? progress : (1f - progress);
                            scrollOffsetY = (int) (fromScrollY * (1f - p) + toScrollY * p);
                            float translationY = currentPanTranslationY + (fromScrollY - toScrollY) * (1f - p);
                            // The outgoing list already has its starting offset; restore it only for the incoming list.
                            gridView.setTranslationY(keyboardVisible ? translationY - (fromScrollY - toScrollY) : translationY);
                            searchGridView.setTranslationY(keyboardVisible ? translationY : translationY + (toScrollY - fromScrollY));
                        } else if (fromOffsetTop != -1) {
                            scrollOffsetY = (int) (fromOffsetTop * (1f - progress) + toOffsetTop * progress);
                            float p = keyboardVisible ? (1f - progress) : progress;
                            if (keyboardVisible) {
                                gridView.setTranslationY(currentPanTranslationY - (fromOffsetTop - toOffsetTop) * progress);
                            } else {
                                gridView.setTranslationY(currentPanTranslationY + (toOffsetTop - fromOffsetTop) * p);
                            }
                        }
                        gridView.setTopGlowOffset((int) (scrollOffsetY + currentPanTranslationY));
                        frameLayout.setTranslationY(scrollOffsetY + currentPanTranslationY);
                        searchEmptyView.setTranslationY(scrollOffsetY + currentPanTranslationY);
                        frameLayout2.invalidate();
                        setCurrentPanTranslationY(currentPanTranslationY);
                        updateBottomOverlay();
                        invalidate();
                    }

                    @Override
                    protected boolean heightAnimationEnabled() {
                        if (isDismissed() || !fullyShown) {
                            return false;
                        }
                        return !commentTextView.isPopupVisible();
                    }
                };
            }

            @Override
            protected void drawList(Canvas blurCanvas, boolean top, ArrayList<IViewWithInvalidateCallback> views) {
                if (gridView.getVisibility() == View.VISIBLE && gridView.getAlpha() >= 0.0f) {
                    blurCanvas.save();
                    blurCanvas.translate(gridView.getX(), gridView.getY());
                    clipDialogListForDraw(blurCanvas, gridView);
                    gridView.draw(blurCanvas);
                    blurCanvas.restore();
                }
                if (folderGridView != null && folderGridView.getVisibility() == View.VISIBLE) {
                    blurCanvas.save();
                    blurCanvas.translate(folderGridView.getX(), folderGridView.getY());
                    clipDialogListForDraw(blurCanvas, folderGridView);
                    folderGridView.draw(blurCanvas);
                    blurCanvas.restore();
                }
                if (topicsGridView.getVisibility() == View.VISIBLE && topicsGridView.getAlpha() >= 0.0f) {
                    blurCanvas.save();
                    blurCanvas.translate(topicsGridView.getX(), topicsGridView.getY());
                    topicsGridView.draw(blurCanvas);
                    blurCanvas.restore();
                }
                if (searchGridView.getVisibility() == View.VISIBLE && searchGridView.getAlpha() >= 0.0f) {
                    blurCanvas.save();
                    blurCanvas.translate(searchGridView.getX(), searchGridView.getY());
                    searchGridView.draw(blurCanvas);
                    blurCanvas.restore();
                }
            }

            @Override
            protected Theme.ResourcesProvider getResourceProvider() {
                return ShareAlert.this.resourcesProvider;
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                if (sheetGlassBackground != null) {
                    sheetGlassObserver = getViewTreeObserver();
                    sheetGlassObserver.addOnPreDrawListener(sheetGlassPositionListener);
                }
                glassEngine.invalidate();
                postInvalidateOnAnimation();
                adjustPanLayoutHelper.setResizableView(this);
                adjustPanLayoutHelper.onAttach();
            }

            @Override
            protected void onDetachedFromWindow() {
                cancelFolderSwipe();
                if (sheetGlassObserver != null) {
                    if (sheetGlassObserver.isAlive()) sheetGlassObserver.removeOnPreDrawListener(sheetGlassPositionListener);
                    sheetGlassObserver = null;
                }
                super.onDetachedFromWindow();
                adjustPanLayoutHelper.onDetach();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight;
                if (getLayoutParams().height > 0) {
                    totalHeight = getLayoutParams().height;
                } else {
                    totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                }

                layoutManager.setNeedFixGap(getLayoutParams().height <= 0);
                if (folderLayoutManager != null) folderLayoutManager.setNeedFixGap(getLayoutParams().height <= 0);
                searchLayoutManager.setNeedFixGap(getLayoutParams().height <= 0);
                if (!isFullscreen) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, systemInsets.top, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                int availableHeight = totalHeight - getPaddingTop();

                // Keep the root sheet height based on all chats, regardless of the selected folder.
                int size = Math.max(searchAdapter.getItemCount(), listAdapter.rootDialogsCount);
                int bottomPadding = dp(100 + (timestampFrameLayout != null ? 48 : 0)) + systemInsets.bottom;
                int contentSize = dp(getTopSpacerHeight()) + Math.max(2, (int) Math.ceil(size / 4.0f)) * dp(103)
                        + bottomPadding + backgroundPaddingTop;
                if (topicsGridView.getVisibility() != View.GONE) {
                    int topicsSize = dp(getTopSpacerHeight()) + Math.max(2, (int) Math.ceil((shareTopicsAdapter.getItemCount() - 2) / 4.0f)) * dp(103)
                            + bottomPadding + backgroundPaddingTop;
                    if (topicsSize > contentSize) {
                        contentSize = AndroidUtilities.lerp(contentSize, topicsSize, topicsGridView.getAlpha());
                    }
                }
                int padding = contentSize < availableHeight ? Math.max(0, totalHeight - contentSize)
                        : availableHeight - (availableHeight / 5 * 3);
                if (gridView.getPaddingTop() != padding || gridView.getPaddingBottom() != bottomPadding
                        || topicsGridView.getPaddingTop() != padding || topicsGridView.getPaddingBottom() != bottomPadding) {
                    ignoreLayout = true;
                    gridView.setPadding(0, padding, 0, bottomPadding);
                    if (folderGridView != null) folderGridView.setPadding(0, padding, 0, bottomPadding);
                    topicsGridView.setPadding(0, padding, 0, bottomPadding);
                    ignoreLayout = false;
                }

                if (keyboardVisible && getLayoutParams().height <= 0 && searchGridView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    searchGridView.setPadding(0, 0, 0, dp(60 + (timestampFrameLayout != null ? 48 : 0)) + systemInsets.bottom);
                    ignoreLayout = false;
                }
                // Short lists expand the whole sheet through its top padding, using a full-height viewport.
                fullHeight = true;
                topOffset = 0;
                ignoreLayout = true;
                checkCurrentList(false);
                ignoreLayout = false;

                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight);
                onMeasureInternal(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
            }

            private void onMeasureInternal(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                widthSize -= backgroundPaddingLeft * 2;

                keyboardSize2 = measureKeyboardHeight();
                if (!commentTextView.isWaitingForKeyboardOpen() && keyboardSize2 <= dp(20) && !commentTextView.isPopupShowing() && !commentTextView.isAnimatePopupClosing()) {
                    ignoreLayout = true;
                    commentTextView.hideEmojiView();
                    ignoreLayout = false;
                }

                ignoreLayout = true;
                if (keyboardSize2 <= dp(20)) {
                    if (!AndroidUtilities.isInMultiwindow) {
                        int paddingBottom;
                        if (keyboardVisible) {
                            paddingBottom = 0;
                        } else {
                            paddingBottom = commentTextView.getEmojiPadding();
                        }
                        heightSize -= paddingBottom;
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                    }
                } else {
                    if (!commentTextView.isPopupVisible()) {
                        commentTextView.hideEmojiView();
                    }
                }
                ignoreLayout = false;

                updateGridBottomSpacers(Math.max(0, heightSize - getPaddingTop()));
                if (pendingFolderGridTop != Integer.MIN_VALUE) {
                    // Reapply after padding and footer measurements, which can change with the new data.
                    layoutManager.scrollToPositionWithOffset(0, pendingFolderGridTop - gridView.getPaddingTop());
                    pendingFolderGridTop = Integer.MIN_VALUE;
                }
                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE) {
                        continue;
                    }
                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(
                                    MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY),
                                    MeasureSpec.makeMeasureSpec(Math.min(
                                        dp(200),
                                        heightSize - systemInsets.top + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(
                                    MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY),
                                    MeasureSpec.makeMeasureSpec(heightSize - systemInsets.top + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        int heightUsed = child == gridView || child == folderGridView || child == searchGridView || child == topicsGridView
                                || child == searchEmptyView ? topOffset : 0;
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, heightUsed);
                    }
                }

                updateBottomOverlay();
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int keyboardSize = measureKeyboardHeight();
                int paddingBottom;
                if (keyboardVisible) {
                    paddingBottom = 0;
                } else {
                    paddingBottom = keyboardSize <= dp(20) && !AndroidUtilities.isInMultiwindow ? Math.max(systemInsets.bottom, commentTextView.getEmojiPadding()) : 0;
                }
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = (r - l) - width - lp.rightMargin - getPaddingRight() - backgroundPaddingLeft;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin + getPaddingLeft();
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop() + topOffset;
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - (t + topOffset) - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                        }
                    }
                    if (child == bottomFadeView) {
                        childTop += systemInsets.bottom;
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                if (folderTargetTabId >= 0 && (gridView.getWidth() != folderSwipeWidth
                        || gridView.getHeight() != folderSwipeHeight)) cancelFolderSwipe();
                notifyHeightChanged();
                updateBottomOverlay();
                updateLayout();
                primeSheetGlass();
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (!fullHeight) {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < topOffset - dp(30)) {
                        dismiss();
                        return true;
                    }
                } else {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY - dp(30)) {
                        dismiss();
                        return true;
                    }
                }
                return interceptFolderSwipe(ev) || super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && (handleFolderSwipe(e) || super.onTouchEvent(e));
            }

            @Override
            public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                if (disallowIntercept && maybeFolderSwipe) recycleFolderVelocityTracker();
                super.requestDisallowInterceptTouchEvent(disallowIntercept);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout || preparingFolderLayout) {
                    return;
                }
                super.requestLayout();
            }

            private boolean lightStatusBar = AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_dialogBackground)) > .721f;

            @Override
            protected void onDraw(Canvas canvas) {
                updateSheetGeometry();
                canvas.save();
                canvas.translate(0, currentPanTranslationY);
                int top = containerViewTop = Math.round(sheetBackgroundTop - currentPanTranslationY) - backgroundPaddingTop;
                int height = getMeasuredHeight() + dp(30 + 30) + backgroundPaddingTop;
                float pinAlpha = sheetPinnedToTopProgress;
                if (sheetGlassBackground != null) {
                    canvas.save();
                    canvas.translate(0, -currentPanTranslationY);
                    sheetGlassBackground.draw(canvas);
                    canvas.restore();
                } else {
                    shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                    shadowDrawable.draw(canvas);
                }
                primeHeaderGlass();

                if (bulletinContainer2 != null) {
                    if (top <= systemInsets.top && bulletinContainer2.getChildCount() > 0) {
                        bulletinContainer2.setTranslationY(0);
                        Bulletin bulletin = Bulletin.getVisibleBulletin();
                        if (bulletin != null) {
                            if (bulletin.getLayout() != null) {
                                bulletin.getLayout().setTop(true);
                            }
                            bulletin.hide();
                        }
                    } else {
                        bulletinContainer2.setTranslationY(Math.max(0, top + backgroundPaddingTop - bulletinContainer2.getTop() - bulletinContainer2.getMeasuredHeight()));
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int flags = getSystemUiVisibility();
                    lightStatusBar = AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_chat_messagePanelBackground)) > .721f;
                    boolean shouldBeLightStatusBar = pinAlpha > .5f && lightStatusBar;
                    boolean isLightStatusBar = (flags & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) > 0;
                    if (shouldBeLightStatusBar != isLightStatusBar) {
                        if (shouldBeLightStatusBar) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        } else {
                            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        }
                        setSystemUiVisibility(flags);
                    }
                }

                canvas.restore();
                previousTopOffset = topOffset;
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                canvas.clipRect(0, getPaddingTop() * (1f - sheetPinnedToTopProgress) + currentPanTranslationY,
                        getMeasuredWidth(), getMeasuredHeight() + currentPanTranslationY + dp(50));
                super.dispatchDraw(canvas);
                canvas.restore();
                if (sheetPinnedToTopProgress < 1) {
                    // Draw above the header material so it cannot cover the grab handle.
                    final int color = getThemedColor(Theme.key_sheet_scrollUp);
                    dragHandlePaint.setColor(color);
                    dragHandlePaint.setAlpha(Math.round(Color.alpha(color) * (1f - sheetPinnedToTopProgress)));
                    final int width = dp(36);
                    final float y = scrollOffsetY - backgroundPaddingTop + dp(6) + topOffset
                            + (isFullscreen ? 0 : systemInsets.top) + currentPanTranslationY;
                    rect1.set((getMeasuredWidth() - width) / 2f, y, (getMeasuredWidth() + width) / 2f, y + dp(4));
                    canvas.drawRoundRect(rect1, dp(2), dp(2), dragHandlePaint);
                }
            }

            @Override
            protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
                if (child == gridView || child == folderGridView) {
                    // Clip in the stationary parent: translating a cached child RenderNode must not
                    // translate a previously recorded sheet clip along with the dialog cells.
                    final int save = canvas.save();
                    canvas.clipRect(Math.max(child.getLeft(), child.getX()), sheetBackgroundTop,
                            Math.min(child.getRight(), child.getX() + child.getWidth()), child.getY() + child.getHeight());
                    if (sheetGlassBackground != null) canvas.clipPath(sheetGlassBackground.getPath());
                    final boolean result = super.drawChild(canvas, child, drawingTime);
                    canvas.restoreToCount(save);
                    return result;
                }
                if (child instanceof EmojiView) {
                    if (emojiViewChildBg != null) {
                        canvas.save();
                        emojiViewChildBg.setBounds(0, child.getTop(), getMeasuredWidth(), getMeasuredHeight());
                        canvas.clipPath(emojiViewChildBg.getPath());
                        emojiViewChildBg.draw(canvas);
                        boolean r = super.drawChild(canvas, child, drawingTime);
                        canvas.restore();
                        return r;
                    }
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        containerView = sizeNotifierFrameLayout;

        containerView.setWillNotDraw(false);
        containerView.setClipChildren(false);
        // dispatchDraw already applies the animated inset boundary; default padding clipping would
        // keep the header material below the status bar after the sheet reaches the top.
        containerView.setClipToPadding(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, systemInsets.bottom);
        setupSheetGlass();

        frameLayout = new FrameLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == filterTabsView) {
                    int save = canvas.save();
                    canvas.clipRect(0, searchView.getBottom(), getWidth(), getHeight() + dp(FILTER_TABS_HEIGHT));
                    boolean result = super.drawChild(canvas, child, drawingTime);
                    canvas.restoreToCount(save);
                    return result;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        frameLayout.setClipChildren(false);

        if (darkTheme && linkToCopy[1] != null) {
            switchView = new SwitchView(context) {
                @Override
                protected void onTabSwitch(int num) {
                    updateLinkTextView();
                }
            };
            frameLayout.addView(switchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 0, 11, 0, 0));
        }

        searchView = new FragmentSearchField(context, resourcesProvider);
        // FragmentSearchField expands its bounds by 4dp; this leaves the same 40dp glass body as COPY LINK.
        searchView.setupBlurredBackground(iBlur3FactoryLiquidGlass.create(searchView,
                BlurredBackgroundProviderImpl.inputFieldShareAlert(resourcesProvider)));
        updateSearchFieldBackground();
        searchView.editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.isEmpty(searchView.editText.getText())) {
                    checkCurrentList(false);
                }

                if (!updateSearchAdapter) {
                    return;
                }
                String text = searchView.editText.getText().toString();
                if (text.length() != 0) {
                    if (searchEmptyView != null) {
                        searchEmptyView.title.setText(LocaleController.getString(R.string.NoResult));
                    }
                } else {
                    if (gridView.getAdapter() != listAdapter) {
                        int top = getCurrentTop();
                        searchEmptyView.title.setText(LocaleController.getString(R.string.NoResult));
                        searchEmptyView.showProgress(false, true);
                        checkCurrentList(false);
                        listAdapter.notifyDataSetChanged();
                        if (top > 0) {
                            layoutManager.scrollToPositionWithOffset(0, -top);
                        }
                    }
                }
                if (searchAdapter != null) {
                    searchAdapter.searchDialogs(text);
                }
            }
        });
        searchView.editText.setHint(LocaleController.getString(R.string.ShareSendTo));
        searchView.editText.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                AndroidUtilities.hideKeyboard(searchView.editText);
            }
            return false;
        });
        searchView.setCloseButtonOnClickListener(() -> {
            updateSearchAdapter = true;
            searchView.editText.setText("");
            AndroidUtilities.showKeyboard(searchView.editText);
        });

        frameLayout.addView(searchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.BOTTOM | Gravity.LEFT, 11, 7, 11, SEARCH_BOTTOM_MARGIN + getFilterTabsHeight()));

        filterTabsView = createFilterTabsView(context);
        filterTabsView.setVisibility(View.GONE);
        frameLayout.addView(filterTabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, FILTER_TABS_HEIGHT, Gravity.BOTTOM, 4, 0, 4, 0));
        updateFilterTabs(false);

        topicsBackActionBar = new ActionBar(context);
        topicsBackActionBar.setOccupyStatusBar(false);
        topicsBackActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        ThemeBind.actionBarTitleColor(topicsBackActionBar, Theme.key_dialogTextBlack, resourcesProvider);
        ThemeBind.actionBarSubtitleColor(topicsBackActionBar, Theme.key_dialogTextGray2, resourcesProvider);
        ThemeBind.actionBarItemsColor(topicsBackActionBar, Theme.key_windowBackgroundWhiteGrayText2, false);
        ThemeBind.actionBarItemsBackgroundColor(topicsBackActionBar, Theme.key_actionBarWhiteSelector, false);
        topicsBackActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                onBackPressed();
            }
        });
        topicsBackActionBar.setVisibility(View.GONE);
        frameLayout.addView(topicsBackActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, getFilterTabsHeight()));

        topicsGridView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public void draw(Canvas canvas) {
                canvas.save();
                clipDialogListForDraw(canvas, this);
                super.draw(canvas);
                canvas.restore();
            }
        };
        topicsGridView.setLayoutManager(topicsLayoutManager = createDialogLayoutManager(context));
        topicsLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (shareTopicsAdapter.getItemViewType(position) != 0) {
                    return topicsLayoutManager.getSpanCount();
                }
                return 1;
            }
        });
        topicsGridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0) {
                    updateLayout();
                    previousScrollOffsetY = scrollOffsetY;
                }
            }
        });
        topicsGridView.setAdapter(shareTopicsAdapter = new ShareTopicsAdapter(context));
        glassEngine.addScrolledView(topicsGridView);
        ThemeBind.listGlowColor(topicsGridView, Theme.key_dialogScrollGlow, resourcesProvider);
        topicsGridView.setVerticalScrollBarEnabled(false);
        topicsGridView.setHorizontalScrollBarEnabled(false);
        topicsGridView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        topicsGridView.setSelectorDrawableColor(0);
        topicsGridView.setItemSelectorColorProvider(i -> 0);
        topicsGridView.setPadding(0, 0, 0, dp(48));
        topicsGridView.setClipToPadding(false);
        topicsGridView.setClipChildren(false);
        topicsGridView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                RecyclerListView.Holder holder = (RecyclerListView.Holder) parent.getChildViewHolder(view);
                if (holder != null) {
                    int pos = holder.getAdapterPosition();
                    outRect.left = pos % 4 == 0 ? 0 : dp(4);
                    outRect.right = pos % 4 == 3 ? 0 : dp(4);
                } else {
                    outRect.left = dp(4);
                    outRect.right = dp(4);
                }
            }
        });
        topicsGridView.setOnItemClickListener((view, position) -> {
            if (shareTopicsAdapter.isBotForum && position == 1) {
                onTopicCreateCellClick();
                return;
            }
            TLRPC.TL_forumTopic topic = shareTopicsAdapter.getItemTopic(position);
            if (topic != null) {
                onTopicCellClick(topic);
            }
        });
        topicsGridView.setVisibility(View.GONE);
        containerView.addView(topicsGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        listAdapter = new ShareDialogsAdapter(context);
        gridView = createDialogGridView(context, listAdapter);
        layoutManager = (GridLayoutManager) gridView.getLayoutManager();

        iBlur3Capture = (canvas, position) -> {
            RecyclerListView list = topicsGridView.getVisibility() == View.VISIBLE ? topicsGridView
                    : searchIsVisible ? searchGridView : gridView;
            capturingDialogList = true;
            try {
                captureHeaderBackdrop(canvas, position);
                captureDialogGrid(canvas, position, list);
                if (list == gridView && folderGridView != null && folderGridView.getVisibility() == View.VISIBLE) {
                    captureDialogGrid(canvas, position, folderGridView);
                }
            } finally {
                capturingDialogList = false;
            }
        };

        containerView.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

        searchGridView = new RecyclerListView(context, resourcesProvider) {

            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y >= getDialogListTop(this) && y < getDialogListBottom(this);
            }

            @Override
            public void draw(Canvas canvas) {
                canvas.save();
                clipDialogListForDraw(canvas, this);
                super.draw(canvas);
                canvas.restore();
            }
        };
        searchGridView.setItemSelectorColorProvider(i -> 0);
        searchGridView.setSelectorDrawableColor(0);
        searchGridView.setPadding(0, 0, 0, dp(48));
        searchGridView.setClipToPadding(false);
        searchGridView.setClipChildren(false);
        searchGridView.setLayoutManager(searchLayoutManager = new FillLastGridLayoutManager(getContext(), 4, 0, searchGridView) {
            @Override
            protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extraLayoutSpace) {
                super.calculateExtraLayoutSpace(state, extraLayoutSpace);
                extendDialogListLayoutSpace(extraLayoutSpace);
            }
        });
        searchLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return searchAdapter.getSpanSize(4, position);
            }
        });
        searchGridView.setOnItemClickListener((view, position) -> {
            if (position < 0) {
                return;
            }
            TLRPC.Dialog dialog = searchAdapter.getItem(position);
            if (dialog == null) {
                return;
            }
            selectDialog(view, dialog);
        });
        searchGridView.setHasFixedSize(true);
        searchGridView.setItemAnimator(null);
        searchGridView.setHorizontalScrollBarEnabled(false);
        searchGridView.setVerticalScrollBarEnabled(false);
        searchGridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0) {
                    updateLayout();
                    previousScrollOffsetY = scrollOffsetY;
                }
            }
        });
        searchGridView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                final RecyclerListView.Holder holder = (RecyclerListView.Holder) parent.getChildViewHolder(view);
                if (holder != null) {
                    if (holder.getItemViewType() != 5) {
                        outRect.left = outRect.right = 0;
                    } else {
                        final int pos = holder.getAdapterPosition();
                        outRect.left = pos % 4 == 0 ? 0 : dp(4);
                        outRect.right = pos % 4 == 3 ? 0 : dp(4);
                    }
                } else {
                    outRect.left = dp(4);
                    outRect.right = dp(4);
                }
            }
        });
        searchGridView.setAdapter(searchAdapter);
        glassEngine.addScrolledView(searchGridView);
        ThemeBind.listGlowColor(searchGridView, Theme.key_dialogScrollGlow, resourcesProvider);

        recyclerItemsEnterAnimator = new RecyclerItemsEnterAnimator(searchGridView, true);

        final FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context, resourcesProvider);
        flickerLoadingView.setViewType(FlickerLoadingView.SHARE_ALERT_TYPE);
        searchEmptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH, resourcesProvider);
        searchEmptyView.addView(flickerLoadingView, 0);
        searchEmptyView.setAnimateLayoutChange(true);
        searchEmptyView.showProgress(false, false);
        if (darkTheme) {
            ThemeBind.textColor(searchEmptyView.title, Theme.key_voipgroup_nameText, resourcesProvider);
        }
        searchEmptyView.title.setText(LocaleController.getString(R.string.NoResult));
        searchGridView.setEmptyView(searchEmptyView);
        searchGridView.setHideIfEmpty(false);
        searchGridView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        containerView.addView(searchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 52, 0, 0));
        containerView.addView(searchGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

        headerFadeView = new View(context) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                canvas.save();
                if (sheetGlassBackground != null) {
                    canvas.translate(-getX(), -getY());
                    canvas.clipPath(sheetGlassBackground.getPath());
                    canvas.translate(getX(), getY());
                } else {
                    canvas.clipRect(0, Math.max(0, sheetBackgroundTop - getY()), getWidth(), getHeight());
                }
                headerFadeDrawable.setAlpha(Math.round(255 * (1f - sheetPinnedToTopProgress)));
                headerFadeDrawable.draw(canvas);
                drawPinnedHeaderFade(canvas);
                canvas.restore();
            }
        };
        final BlurredBackgroundProvider headerColors = BlurredBackgroundProviderImpl.inputFieldShareAlert(resourcesProvider);
        headerSurfaceGlass = createShareGlass(headerFadeView);
        // One surface runs from the sheet's top through the folders; no internal rims divide it.
        headerSurfaceGlass.setColorProvider(new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> headerColors.getBackgroundColor()).build());
        headerSurfaceGlass.setThickness(dp(8));
        headerSurfaceGlass.setHasPadding(false);
        headerFadeDrawable = new BlurredBackgroundWithFadeDrawable(headerSurfaceGlass);
        headerFadeDrawable.setCallback(headerFadeView);
        // Keep the header glass complete above its transition, with the bottom's 72dp falloff range.
        headerFadeDrawable.setFadeHeight(-dp(SHARE_FADE_HEIGHT_DP), false);
        // Outside COPY LINK the original avatars show through a color fade, without a glass redraw.
        headerPinnedFadeDrawable = new BlurredBackgroundWithFadeDrawable(iBlur3FactoryFade.create(headerFadeView, null));
        headerPinnedFadeDrawable.setCallback(headerFadeView);
        headerPinnedFadeDrawable.setFadeHeight(-dp(SHARE_FADE_HEIGHT_DP), true);
        containerView.addView(headerFadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        bottomFadeView = new View(context) {
            @Override
            public void draw(@NonNull Canvas canvas) {
                super.draw(canvas);
                fadeDrawable.setBounds(0, getMeasuredHeight() - systemInsets.bottom - dp(SHARE_FADE_HEIGHT_DP), getMeasuredWidth(), getMeasuredHeight());
                fadeDrawable.draw(canvas);
            }
        };
        containerView.addView(bottomFadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 300, Gravity.BOTTOM));

        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        frameLayoutParams.topMargin = dp(getTopPanelHeight());
        shadow[0] = new View(context);
        ThemeBind.backgroundColor(shadow[0], Theme.key_dialogShadowLine, resourcesProvider);
        shadow[0].setAlpha(0.0f);
        shadow[0].setVisibility(View.GONE);
        shadow[0].setTag(1);
        containerView.addView(shadow[0], frameLayoutParams);

        containerView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, getTopPanelHeight(), Gravity.LEFT | Gravity.TOP));

        frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
        frameLayoutParams.bottomMargin = dp(48);
        shadow[1] = new View(context);
        ThemeBind.backgroundColor(shadow[1], Theme.key_dialogShadowLine, resourcesProvider);
        shadow[1].setLayoutParams(frameLayoutParams);

        if (isChannel || linkToCopy[0] != null) {
            if (darkTheme) {
                pickerBottom = new FrameLayout(context);

                pickerBottomLayout = new FrameLayout(context);
                pickerBottom.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, -2, 0, -2, 0));

                LinearLayout pickerBottomLinearLayout = new LinearLayout(context);
                pickerBottomLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
                pickerBottomLayout.addView(pickerBottomLinearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

                linkContainer = new LinearLayout(context);
                linkContainer.setOrientation(LinearLayout.HORIZONTAL);
                ScaleStateListAnimator.apply(linkContainer, 0.015f, 1.2f);
                linkContainer.setOnClickListener(v -> {
                    if (selectedDialogs.size() == 0 && (isChannel || linkToCopy[0] != null)) {
                        dismiss();
                        PhotoViewer.getInstance().closePhoto(true, false);
                        if (linkToCopy[0] == null && loadingLink) {
                            copyLinkOnEnd = true;
                            Toast.makeText(ShareAlert.this.getContext(), LocaleController.getString(R.string.Loading), Toast.LENGTH_SHORT).show();
                        } else {
                            copyLink(ShareAlert.this.getContext());
                        }
                    }
                });

                linkTextView = new SimpleTextView(context);
                linkTextView.setTextSize(15);
                ThemeBind.textColor(linkTextView, Theme.key_share_linkText, resourcesProvider);
                linkTextView.setEllipsizeByGradient(true);
                updateLinkTextView();
                linkContainer.addView(linkTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20, 1, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));

                linkCopyButton = new TextView(context);
                linkCopyButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                ThemeBind.textColor(linkCopyButton, Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
                linkCopyButton.setText(LocaleController.getString(R.string.Copy).toUpperCase());
                linkCopyButton.setPadding(dp(9), 0, dp(9), 0);
                linkCopyButton.setTypeface(AndroidUtilities.bold());
                linkCopyButton.setGravity(Gravity.CENTER);
                ThemeBind.selectorBackground(linkCopyButton, Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), 4, 4), null, ThemeBind.of(Theme.key_listSelector), resourcesProvider);
                linkContainer.addView(linkCopyButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, 0, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 7, 0));
                linkCopyButton.setOnClickListener(v -> {
                    if (selectedDialogs.size() == 0 && (isChannel || linkToCopy[0] != null)) {
                        dismiss();
                        if (linkToCopy[0] == null && loadingLink) {
                            copyLinkOnEnd = true;
                            Toast.makeText(ShareAlert.this.getContext(), LocaleController.getString(R.string.Loading), Toast.LENGTH_SHORT).show();
                        } else {
                            copyLink(ShareAlert.this.getContext());
                        }
                    }
                });
                ScaleStateListAnimator.apply(linkCopyButton);

                containerView.addView(pickerBottom, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44 + 7 + 7, Gravity.LEFT | Gravity.BOTTOM)); // (video_timestamp != null ? 0 : 11);

                LinearLayout sharesLayout = null;
                int rightMargin = 11;
                if (sendingMessageObjects != null && sendingMessageObjects.size() > 0 && sendingMessageObjects.get(0).messageOwner != null && sendingMessageObjects.get(0).messageOwner.forwards > 0) {
                    final MessageObject messageObject = sendingMessageObjects.get(0);
                    final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-messageObject.getDialogId());
                    if (ChatObject.hasAdminRights(chat) && !messageObject.isForwarded()) {
                        sharesLayout = new LinearLayout(context);
                        sharesLayout.setOrientation(LinearLayout.HORIZONTAL);
                        sharesLayout.setOnClickListener(v -> {
                            BaseFragment fragment2 = parentFragment;
                            if (fragment2 == null) fragment2 = LaunchActivity.getSafeLastFragment();
                            if (fragment2 == null) return;
                            dismiss();
                            fragment2.presentFragment(new MessageStatisticActivity(messageObject));
                        });
                        sharesLayout.setPadding(dp(8), dp(4), dp(8), dp(4));
                        ThemeBind.selectorBackground(sharesLayout, Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), 6, 6), null, ThemeBind.of(Theme.key_listSelector), resourcesProvider);
                        ScaleStateListAnimator.apply(sharesLayout);

                        final ImageView imageView = new ImageView(context);
                        imageView.setImageResource(R.drawable.mini_stats_shares);
                        imageView.setScaleType(ImageView.ScaleType.CENTER);
                        ThemeBind.colorFilter(imageView, Theme.key_share_icon, resourcesProvider, PorterDuff.Mode.SRC_IN);
                        sharesLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL));

                        final TextView textView = new TextView(context);
                        ThemeBind.textColor(textView, Theme.key_share_icon, resourcesProvider);
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                        textView.setGravity(Gravity.CENTER);
                        textView.setText(LocaleController.formatNumber(messageObject.messageOwner.forwards, ','));
                        sharesLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL, 4, -1, 0, 0));

                        rightMargin = 8;
                    }
                }

                pickerBottomLinearLayout.addView(linkContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 1, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL, 11, 0, rightMargin, 0));
                if (sharesLayout != null) {
                    pickerBottomLinearLayout.addView(sharesLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL, 0, 5, 8, 0));
                }
            } else {
                pickerBottom = new FrameLayout(context);
                pickerBottomLayout = new FrameLayout(context);
                pickerBottom.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, -2, 0, -2, 0));

                TextView pickerTextView = new TextView(context);
                // ripple within the rounded glass pill of pickerBottomLayout (40dp high inside its 9dp padding)
                ThemeBind.selectorBackground(pickerTextView, Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), 20, 20), null, ThemeBind.of(Theme.key_listSelector), resourcesProvider);
                ThemeBind.textColor(pickerTextView, darkTheme ? Theme.key_voipgroup_listeningText : Theme.key_dialogTextBlue2, resourcesProvider);
                pickerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                pickerTextView.setPadding(dp(18), 0, dp(18), 0);
                pickerTextView.setTypeface(AndroidUtilities.bold());
                pickerTextView.setGravity(Gravity.CENTER);
                if (darkTheme && linkToCopy[1] != null) {
                    pickerTextView.setText(LocaleController.getString(R.string.VoipGroupCopySpeakerLinkNoCaps).toUpperCase());
                } else {
                    pickerTextView.setText(LocaleController.getString(R.string.CopyLink).toUpperCase());
                }
                // ScaleStateListAnimator.apply(pickerTextView, 0.015f, 1.2f);
                pickerTextView.setOnClickListener(v -> {
                    if (selectedDialogs.size() == 0 && (isChannel || linkToCopy[0] != null)) {
                        dismiss();
                        if (linkToCopy[0] == null && loadingLink) {
                            copyLinkOnEnd = true;
                            Toast.makeText(ShareAlert.this.getContext(), LocaleController.getString(R.string.Loading), Toast.LENGTH_SHORT).show();
                        } else {
                            copyLink(ShareAlert.this.getContext());
                        }
                    }
                });
                pickerBottomLayout.addView(pickerTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
                containerView.addView(pickerBottom, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44 + 7 + 7, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

                if (sendingMessageObjects != null && sendingMessageObjects.size() > 0 && sendingMessageObjects.get(0).messageOwner != null && sendingMessageObjects.get(0).messageOwner.forwards > 0) {
                    final MessageObject messageObject = sendingMessageObjects.get(0);
                    final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-messageObject.getDialogId());
                    if (ChatObject.hasAdminRights(chat) && !messageObject.isForwarded()) {
                        sharesCountLayout = new LinearLayout(context);
                        sharesCountLayout.setOrientation(LinearLayout.HORIZONTAL);
                        sharesCountLayout.setGravity(Gravity.CENTER_VERTICAL);
                        ThemeBind.selectorBackground(sharesCountLayout, Theme.createRadSelectorDrawable(getThemedColor(darkTheme ? Theme.key_voipgroup_listSelector : Theme.key_listSelector), 20, 20), null, ThemeBind.of(darkTheme ? Theme.key_voipgroup_listSelector : Theme.key_listSelector), resourcesProvider);
                        pickerBottomLayout.addView(sharesCountLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
                        sharesCountLayout.setOnClickListener(v -> {
                            BaseFragment fragment2 = parentFragment;
                            if (fragment2 == null) fragment2 = LaunchActivity.getSafeLastFragment();
                            if (fragment2 == null) return;
                            dismiss();
                            fragment2.presentFragment(new MessageStatisticActivity(messageObject));
                        });

                        ImageView imageView = new ImageView(context);
                        imageView.setImageResource(R.drawable.share_arrow);
                        ThemeBind.colorFilter(imageView, darkTheme ? Theme.key_voipgroup_listeningText : Theme.key_dialogTextBlue2, resourcesProvider, PorterDuff.Mode.MULTIPLY);
                        sharesCountLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 20, 0, 0, 0));

                        TextView textView = new TextView(context);
                        textView.setText(String.format("%d", messageObject.messageOwner.forwards));
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                        ThemeBind.textColor(textView, darkTheme ? Theme.key_voipgroup_listeningText : Theme.key_dialogTextBlue2, resourcesProvider);
                        textView.setGravity(Gravity.CENTER_VERTICAL);
                        textView.setTypeface(AndroidUtilities.bold());
                        sharesCountLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 8, 0, 20, 0));
                    }
                }
            }
        } else {
            shadow[1].setAlpha(0.0f);
        }

        bulletinContainer = new FrameLayout(context);
        containerView.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, pickerBottomLayout != null ? 48 : 0));

        bulletinContainer2 = new FrameLayout(context);
        containerView.addView(bulletinContainer2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));

        frameLayout2 = new FrameLayout(context) {
            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                if (visibility != View.VISIBLE) {
                    shadow[1].setTranslationY(0);
                }
            }

            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                invalidate();
            }

            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                if (chatActivityEnterViewAnimateFromTop != 0 && chatActivityEnterViewAnimateFromTop != frameLayout2.getTop() + chatActivityEnterViewAnimateFromTop) {
                    if (topBackgroundAnimator != null) {
                        topBackgroundAnimator.cancel();
                    }
                    captionEditTextTopOffset = chatActivityEnterViewAnimateFromTop - (frameLayout2.getTop() + captionEditTextTopOffset);
                    topBackgroundAnimator = ValueAnimator.ofFloat(captionEditTextTopOffset, 0);
                    topBackgroundAnimator.addUpdateListener(valueAnimator -> {
                        captionEditTextTopOffset = (float) valueAnimator.getAnimatedValue();
                        frameLayout2.invalidate();
                        invalidate();
                    });
                    topBackgroundAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    topBackgroundAnimator.setDuration(200);
                    topBackgroundAnimator.start();
                    chatActivityEnterViewAnimateFromTop = 0;
                }
                float alphaOffset = (frameLayout2.getMeasuredHeight() - dp(48)) * (1f - getAlpha());
                shadow[1].setTranslationY(-(frameLayout2.getMeasuredHeight() - dp(48)) + captionEditTextTopOffset + currentPanTranslationY + alphaOffset);
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                captionContainerBg.setBounds(0, (int) captionEditTextTopOffset, getMeasuredWidth(), getMeasuredHeight());
                captionContainerBg.draw(canvas);
                canvas.save();
                canvas.clipRect(0, captionEditTextTopOffset, getMeasuredWidth(), getMeasuredHeight());
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };
        frameLayout2.setWillNotDraw(false);
        frameLayout2.setAlpha(0.0f);
        frameLayout2.setVisibility(View.INVISIBLE);
        containerView.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, -2, 0, -2, 0));
        frameLayout2.setOnTouchListener((v, event) -> true);

        AndroidUtilities.setLightNavigationBar(container, themedLightNavigationBar = AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_dialogBackground)) > .721f);

        commentTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, null, EditTextEmoji.STYLE_DIALOG, true, resourcesProvider) {

            @Override
            protected void bottomPanelTranslationY(float translation) {
                super.bottomPanelTranslationY(translation);
                updateBottomOverlay();
            }

            private boolean shouldAnimateEditTextWithBounds;
            private int messageEditTextPredrawHeigth;
            private int messageEditTextPredrawScrollY;
            private ValueAnimator messageEditTextAnimator;

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (shouldAnimateEditTextWithBounds) {
                    EditTextCaption editText = commentTextView.getEditText();
                    float dy = (messageEditTextPredrawHeigth - editText.getMeasuredHeight()) + (messageEditTextPredrawScrollY - editText.getScrollY());
                    editText.setOffsetY(editText.getOffsetY() - dy);
                    ValueAnimator a = ValueAnimator.ofFloat(editText.getOffsetY(), 0);
                    a.addUpdateListener(animation -> editText.setOffsetY((float) animation.getAnimatedValue()));
                    if (messageEditTextAnimator != null) {
                        messageEditTextAnimator.cancel();
                    }
                    messageEditTextAnimator = a;
                    a.setDuration(200);
                    a.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    a.start();
                    shouldAnimateEditTextWithBounds = false;
                }
                super.dispatchDraw(canvas);
            }

            @Override
            protected void onLineCountChanged(int oldLineCount, int newLineCount) {
                if (!TextUtils.isEmpty(getEditText().getText())) {
                    shouldAnimateEditTextWithBounds = true;
                    messageEditTextPredrawHeigth = getEditText().getMeasuredHeight();
                    messageEditTextPredrawScrollY = getEditText().getScrollY();
                    invalidate();
                } else {
                    getEditText().animate().cancel();
                    getEditText().setOffsetY(0);
                    shouldAnimateEditTextWithBounds = false;
                }
                chatActivityEnterViewAnimateFromTop = frameLayout2.getTop() + captionEditTextTopOffset;
                frameLayout2.invalidate();
            }

            @Override
            protected void showPopup(int show) {
                super.showPopup(show);
//                if (darkTheme) {
//                    navBarColorKey = -1;
//                    AndroidUtilities.setNavigationBarColor(ShareAlert.this.getWindow(), ShareAlert.this.getThemedColor(Theme.key_windowBackgroundGray), true, color -> {
//                        ShareAlert.this.setOverlayNavBarColor(navBarColor = color);
//                    });
//                }
            }

            @Override
            public void hidePopup(boolean byBackButton) {
                super.hidePopup(byBackButton);
//                if (darkTheme) {
//                    navBarColorKey = -1;
//                    AndroidUtilities.setNavigationBarColor(ShareAlert.this.getWindow(), ShareAlert.this.getThemedColor(Theme.key_voipgroup_inviteMembersBackground), true, color -> {
//                        ShareAlert.this.setOverlayNavBarColor(navBarColor = color);
//                    });
//                }
            }

            @Override
            protected void createEmojiView() {
                super.createEmojiView();
                EmojiView emojiView = getEmojiView();
                if (emojiView != null) {
                    emojiView.shouldLightenBackground = false;
                    emojiView.fixBottomTabContainerTranslation = false;
                    emojiView.setShouldDrawBackground(false);
                    emojiView.setBottomInset(systemInsets.bottom);
                }
                if (timestampFrameLayout != null) {
                    timestampFrameLayout.bringToFront();
                }
                if (frameLayout2 != null) {
                    frameLayout2.bringToFront();
                }
                if (writeButtonContainer != null) {
                    writeButtonContainer.bringToFront();
                }
            }
        };
        commentTextView.includeNavigationBar = true;
        if (darkTheme) {
            commentTextView.getEditText().setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            commentTextView.getEditText().setCursorColor(getThemedColor(Theme.key_voipgroup_nameText));
        }
//        commentTextView.setBackgroundColor(backgroundColor);
        commentTextView.setHint(LocaleController.getString(R.string.ShareComment));
        commentTextView.onResume();
        commentTextView.setPadding(0, 0, dp(84), 0);
        frameLayout2.addView(commentTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        frameLayout2.setClipChildren(false);
        frameLayout2.setClipToPadding(false);
        commentTextView.setClipChildren(false);
        commentTextView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                AndroidUtilities.runOnUIThread(() -> {
                    updateSelectedCount(1);
                });
            }
        });

        writeButtonContainer = new FrameLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setText(LocaleController.formatPluralString("AccDescrShareInChats", selectedDialogs.size()));
                info.setClassName(Button.class.getName());
                info.setLongClickable(true);
                info.setClickable(true);
            }
        };
        writeButtonContainer.setFocusable(true);
        writeButtonContainer.setFocusableInTouchMode(true);
        writeButtonContainer.setVisibility(View.INVISIBLE);
        writeButtonContainer.setScaleX(0.2f);
        writeButtonContainer.setScaleY(0.2f);
        writeButtonContainer.setAlpha(0.0f);
        containerView.addView(writeButtonContainer, LayoutHelper.createFrame(110, 50, Gravity.RIGHT | Gravity.BOTTOM));

        writeButton = new ChatActivityEnterView.SendButton(context, R.drawable.send_plane_24, resourcesProvider) {
            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public boolean isInScheduleMode() {
                return false;
            }

            @Override
            public boolean isInactive() {
                return false;
            }

            @Override
            public boolean shouldDrawBackground() {
                return true;
            }

            @Override
            public int getFillColor() {
                return getThemedColor(Theme.key_dialogFloatingButton);
            }
        };
        writeButton.setCircleSize(dp(52), dp(38));
        writeButton.setCirclePadding(dp(1), dp(6));
        writeButton.newCounterPos = true;
        writeButtonContainer.addView(writeButton, LayoutHelper.createFrameMatchParent());
        writeButton.setOnClickListener(v -> sendInternal(true));
        writeButton.setOnLongClickListener(v -> {
            return showSendOptions(writeButton,
                () -> sendInternal(true),
                () -> sendInternal(false));
        });

        hideSendersNameButtonContainer = new FrameLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setText(LocaleController.formatPluralString("AccDescrShareInChats", selectedDialogs.size()));
                info.setClassName(Button.class.getName());
                info.setLongClickable(true);
                info.setClickable(true);
            }
        };
        hideSendersNameButtonContainer.setFocusable(true);
        hideSendersNameButtonContainer.setFocusableInTouchMode(true);
        hideSendersNameButtonContainer.setVisibility(View.INVISIBLE);
        hideSendersNameButtonContainer.setScaleX(0.2f);
        hideSendersNameButtonContainer.setScaleY(0.2f);
        hideSendersNameButtonContainer.setAlpha(0.0f);
        containerView.addView(hideSendersNameButtonContainer, LayoutHelper.createFrame(110, 50, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 65, 0));

        // Drawable hideSendersNameDrawable = LottieStaticCache.drawable(R.raw.name_hide, 24);
        Drawable hideSendersNameDrawable = context.getResources().getDrawable(R.drawable.filled_sharing_off2_24).mutate();
        hideSendersNameButton = new ChatActivityEnterView.SendButton(context, hideSendersNameDrawable, resourcesProvider) {
            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public boolean isInScheduleMode() {
                return false;
            }

            @Override
            public boolean isInactive() {
                return false;
            }

            @Override
            public boolean shouldDrawBackground() {
                return true;
            }

            @Override
            public int getFillColor() {
                return getThemedColor(Theme.key_dialogFloatingButton);
            }
        };
        hideSendersNameButton.setCircleSize(dp(52), dp(38));
        hideSendersNameButton.setCirclePadding(dp(1), dp(6));
        hideSendersNameButton.newCounterPos = true;
        hideSendersNameButtonContainer.addView(hideSendersNameButton, LayoutHelper.createFrameMatchParent());
        hideSendersNameButton.setOnClickListener(v -> sendWithoutQuote(true, true, false));
        hideSendersNameButton.setOnLongClickListener(v -> {
            return showSendOptions(hideSendersNameButton,
                () -> sendWithoutQuote(true, true, false),
                () -> sendWithoutQuote(false, true, false));
        });

        hideCaptionButtonContainer = new FrameLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setText(LocaleController.formatPluralString("AccDescrShareInChats", selectedDialogs.size()));
                info.setClassName(Button.class.getName());
                info.setLongClickable(true);
                info.setClickable(true);
            }
        };
        hideCaptionButtonContainer.setFocusable(true);
        hideCaptionButtonContainer.setFocusableInTouchMode(true);
        hideCaptionButtonContainer.setVisibility(View.INVISIBLE);
        hideCaptionButtonContainer.setScaleX(0.2f);
        hideCaptionButtonContainer.setScaleY(0.2f);
        hideCaptionButtonContainer.setAlpha(0.0f);
        containerView.addView(hideCaptionButtonContainer, LayoutHelper.createFrame(110, 50, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 130, 0));

        // Drawable hideCaptionDrawable = LottieStaticCache.drawable(R.raw.caption_hide, 24);
        Drawable hideCaptionDrawable = context.getResources().getDrawable(R.drawable.msg_edit).mutate();
        hideCaptionButton = new ChatActivityEnterView.SendButton(context, hideCaptionDrawable, resourcesProvider) {
            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public boolean isInScheduleMode() {
                return false;
            }

            @Override
            public boolean isInactive() {
                return false;
            }

            @Override
            public boolean shouldDrawBackground() {
                return true;
            }

            @Override
            public int getFillColor() {
                return getThemedColor(Theme.key_dialogFloatingButton);
            }
        };
        hideCaptionButton.setCircleSize(dp(52), dp(38));
        hideCaptionButton.setCirclePadding(dp(1), dp(6));
        hideCaptionButton.newCounterPos = true;
        hideCaptionButtonContainer.addView(hideCaptionButton, LayoutHelper.createFrameMatchParent());
        hideCaptionButton.setOnClickListener(v -> sendWithoutQuote(true, !hideCaptionShowSendersName, true));
        hideCaptionButton.setOnLongClickListener(v -> {
            hideCaptionShowSendersName = false;
            final boolean[] showSendersForPopup = { false };
            return showSendOptions(
                    hideCaptionButton,
                    () -> showSendersForPopup[0],
                    value -> {
                        showSendersForPopup[0] = value;
                        hideCaptionShowSendersName = value;
                    },
                    () -> sendWithoutQuote(true, !showSendersForPopup[0], true),
                    () -> sendWithoutQuote(false, !showSendersForPopup[0], true));
        });

        textPaint.setTextSize(dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());

        if (video_timestamp != null) {
            timestamp = video_timestamp;

            timestampFrameLayout = new FrameLayout(context);

            timestampLayout = new LinearLayout(context);
            timestampLayout.setPadding(dp(12), dp(4), dp(12), dp(4));
            timestampLayout.setClipToPadding(false);
            timestampLayout.setOrientation(LinearLayout.HORIZONTAL);
            ThemeBind.selectorBackground(timestampLayout, Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), 6, 6), null, ThemeBind.of(Theme.key_listSelector), resourcesProvider);
            timestampCheckbox = new CheckBox2(context, 24, resourcesProvider);
            timestampCheckbox.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
            timestampCheckbox.setDrawUnchecked(true);
            timestampCheckbox.setChecked(false, false);
            timestampCheckbox.setDrawBackgroundAsArc(10);
            timestampLayout.addView(timestampCheckbox, LayoutHelper.createLinear(26, 26, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
            timestampTextView = new TextView(context);
            ThemeBind.textColor(timestampTextView, Theme.key_dialogTextBlack, resourcesProvider);
            timestampTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            timestampTextView.setText(LocaleController.formatString(R.string.VideoShareAddTimestamp, AndroidUtilities.formatShortDuration(video_timestamp)));
            timestampLayout.addView(timestampTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 9, 0, 0, 0));
            ScaleStateListAnimator.apply(timestampLayout, 0.025f, 1.5f);
            timestampLayout.setOnClickListener(v -> {
                timestampCheckbox.setChecked(!timestampCheckbox.isChecked(), true);
                updateLinkTextView();
            });
            timestampFrameLayout.addView(timestampLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            if (pickerBottom == null) {
                View shadow = new View(context);
                ThemeBind.backgroundColor(shadow, Theme.key_divider, resourcesProvider);
                timestampFrameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, darkTheme ? 0.66f : 1.0f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));
            }

            containerView.addView(timestampFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44 + 7 + 7, Gravity.LEFT | Gravity.BOTTOM));

            if (pickerBottom == null) {
                timestampFrameLayout.setAlpha(0.0f);
                timestampFrameLayout.setVisibility(View.INVISIBLE);
            }
        }


        fadeDrawable = new BlurredBackgroundWithFadeDrawable(
                iBlur3FactoryFade.create(bottomFadeView, null));
        if (!SharedConfig.chatBlurEnabled() || LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) || true) {
            fadeDrawable.setFadeHeight(dp(SHARE_FADE_HEIGHT_DP), true);
        }


        emojiViewChildBg = iBlur3FactoryFrostedLiquidGlass.create(sizeNotifierFrameLayout, BlurredBackgroundProviderImpl.inputFieldShareAlert(resourcesProvider));
        emojiViewChildBg.enableInAppKeyboardOptimization();
        emojiViewChildBg.setRadius(dp(29), dp(29), 0, 0);
        emojiViewChildBg.setThickness(dp(32));
        emojiViewChildBg.setIntensity(0.4f);

        captionContainerBg = createShareGlass(frameLayout2);
        captionContainerBg.setRadius(dp(22));
        captionContainerBg.setPadding(dp(9));
        frameLayout2.setPadding(dp(9), dp(7), dp(9), dp(7));

        if (pickerBottomLayout != null) {
            BlurredBackgroundDrawable d = createShareGlass(pickerBottomLayout);
            d.setPadding(dp(9));
            d.setRadius(dp(22));
            pickerBottomLayout.setBackground(d);
            pickerBottomLayout.setPadding(dp(9), dp(9), dp(9), dp(9));
        }

        if (timestampFrameLayout != null) {
            BlurredBackgroundDrawable d = createShareGlass(timestampFrameLayout);
            d.setPadding(dp(9));
            d.setRadius(dp(22));
            timestampFrameLayout.setBackground(d);
            timestampFrameLayout.setPadding(dp(9), dp(9), dp(9), dp(9));
        }

        updateSelectedCount(0);

        DialogsActivity.loadDialogs(AccountInstance.getInstance(currentAccount));
        if (listAdapter.dialogs.isEmpty()) {
            NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.dialogsNeedReload);
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogFiltersUpdated);

        DialogsSearchAdapter.loadRecentSearch(currentAccount, 0, new DialogsSearchAdapter.OnRecentSearchLoaded() {
            @Override
            public void setRecentSearch(ArrayList<DialogsSearchAdapter.RecentSearchObject> arrayList, LongSparseArray<DialogsSearchAdapter.RecentSearchObject> hashMap) {
                if (arrayList != null) {
                    for (int i = 0; i < arrayList.size(); ++i) {
                        DialogsSearchAdapter.RecentSearchObject recentSearchObject = arrayList.get(i);
                        if (recentSearchObject.object instanceof TLRPC.Chat && !ChatObject.canWriteToChat((TLRPC.Chat) recentSearchObject.object)) {
                            arrayList.remove(i);
                            i--;
                        }
                    }
                }
                recentSearchObjects = arrayList;
                recentSearchObjectsById = hashMap;
                for (int a = 0; a < recentSearchObjects.size(); a++) {
                    DialogsSearchAdapter.RecentSearchObject recentSearchObject = recentSearchObjects.get(a);
                    if (recentSearchObject.object instanceof TLRPC.User) {
                        MessagesController.getInstance(currentAccount).putUser((TLRPC.User) recentSearchObject.object, true);
                    } else if (recentSearchObject.object instanceof TLRPC.Chat) {
                        MessagesController.getInstance(currentAccount).putChat((TLRPC.Chat) recentSearchObject.object, true);
                    } else if (recentSearchObject.object instanceof TLRPC.EncryptedChat) {
                        MessagesController.getInstance(currentAccount).putEncryptedChat((TLRPC.EncryptedChat) recentSearchObject.object, true);
                    }
                }
                searchAdapter.notifyDataSetChanged();
            }
        });
        MediaDataController.getInstance(currentAccount).loadHints(true);

        glassEngine.setRoot(containerView);
        glassEngine.setGlassInvalidationListener(this::invalidateBlurCaptures);
        glassEngine.setPositionsMerger(new GlassPositionsMerger(Float.POSITIVE_INFINITY, dp(48)));

        AndroidUtilities.updateViewVisibilityAnimated(gridView, true, 1f, false);
        AndroidUtilities.updateViewVisibilityAnimated(searchGridView, false, 1f, false);

        ViewCompat.setOnApplyWindowInsetsListener(getContainer(), this::onApplyWindowInsets);
    }

    private Insets systemInsets = Insets.NONE;

    public boolean attachToChat(ChatActivity fragment) {
        setOpenAnimationAfterLayout();
        return makeAttachedToEdgeToEdgeFragment(fragment);
    }

    private QuickShareCells quickShareCells;

    public void setQuickShareCells(QuickShareCells cells) {
        if (cells != null) {
            cells.stop();
            if (cells.account != currentAccount) cells = null;
            else if (includeStory) {
                // Create the story tile on opening to preserve its delayed animation,
                // then reuse the prepared forwarding dialogs in their original order.
                cells.cells.add(0, null);
            }
        }
        quickShareCells = cells;
    }

    /** Detached cells prepared while the chat is idle and rebound on opening. */
    public static final class QuickShareCells implements Runnable, Choreographer.FrameCallback {
        private static final long PREPARATION_INTERVAL_NS = 1_000_000_000L / 60;
        private final View owner;
        private final Theme.ResourcesProvider resourcesProvider;
        private final BooleanSupplier canPrepare;
        private final ArrayList<ShareDialogCell> cells = new ArrayList<>();
        private long nextPreparationTimeNs;
        private final int account = UserConfig.selectedAccount;
        private boolean stopped;

        public QuickShareCells(View owner, Theme.ResourcesProvider resourcesProvider, BooleanSupplier canPrepare) {
            this.owner = owner;
            this.resourcesProvider = resourcesProvider;
            this.canPrepare = canPrepare;
            // The idle predicate waits for active transitions before optional work starts.
            owner.post(this);
        }

        @Override
        public void run() {
            if (!stopped) Choreographer.getInstance().postFrameCallback(this);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            final int capacity = Math.min(24, 4 * (1 + (int) Math.ceil(owner.getHeight() * .6f / dp(100))));
            if (stopped || !owner.isAttachedToWindow() || account != UserConfig.selectedAccount || cells.size() >= capacity) return;
            if (!canPrepare.getAsBoolean()) {
                owner.postDelayed(this, 80);
                return;
            }
            // Higher refresh rates must not multiply optional allocations on the UI thread.
            if (nextPreparationTimeNs == 0) nextPreparationTimeNs = frameTimeNanos;
            if (frameTimeNanos < nextPreparationTimeNs) {
                run();
                return;
            }
            nextPreparationTimeNs += PREPARATION_INTERVAL_NS;
            // Skip missed slots instead of catching up when the chat becomes idle again.
            if (nextPreparationTimeNs <= frameTimeNanos) {
                nextPreparationTimeNs = frameTimeNanos + PREPARATION_INTERVAL_NS;
            }
            ShareDialogCell cell = new ShareDialogCell(owner.getContext(), ShareDialogCell.TYPE_SHARE, resourcesProvider) {
                @Override
                protected String repostToCustomName() {
                    return LocaleController.getString(R.string.RepostToStory);
                }
            };
            ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(account).dialogsForward;
            long dialogId = cells.size() < dialogs.size() ? dialogs.get(cells.size()).id : UserConfig.getInstance(account).clientUserId;
            // Prepare real text measurement and font resources as well as the view.
            // The adapter always rebinds current data and selection on opening.
            cell.setDialog(dialogId, false, null);
            int cellWidth = Math.max(1, owner.getWidth() / 4 - dp(8));
            cell.measure(View.MeasureSpec.makeMeasureSpec(cellWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(100), View.MeasureSpec.EXACTLY));
            cell.layout(0, 0, cell.getMeasuredWidth(), cell.getMeasuredHeight());
            cells.add(cell);
            // At most one allocation per frame. Opening never waits for this queue.
            run();
        }

        public void stop() {
            stopped = true;
            owner.removeCallbacks(this);
            Choreographer.getInstance().removeFrameCallback(this);
        }

        private ShareDialogCell take() {
            return cells.isEmpty() ? null : cells.remove(0);
        }
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View ignoredV, @NonNull WindowInsetsCompat insets) {
        attachedKeyboardHeight = Math.max(0, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                - insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
        processLegacyContainerInsets(insets.toWindowInsets());

        final Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        if (!this.systemInsets.equals(systemInsets)) {
            this.systemInsets = systemInsets;
            container.requestLayout();
        }

        return WindowInsetsCompat.CONSUMED;
    }

    protected void onShareStory(View cell) {

    }
    
    private void showPremiumBlockedToast(View view, long dialogId) {
        AndroidUtilities.shakeViewSpring(view, shiftDp = -shiftDp);
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
        String username = "";
        if (dialogId >= 0) {
            username = UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(dialogId));
        }
        Bulletin bulletin;
        if (MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
            bulletin = BulletinFactory.of(bulletinContainer, resourcesProvider).createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserBlockedNonPremium, username)));
        } else {
            bulletin = BulletinFactory.of(bulletinContainer, resourcesProvider).createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserBlockedNonPremium, username)), LocaleController.getString(R.string.UserBlockedNonPremiumButton), () -> {
                Runnable openPremium = () -> {
                    BaseFragment lastFragment = LaunchActivity.getLastFragment();
                    if (lastFragment != null) {
                        BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                        params.transitionFromLeft = true;
                        params.allowNestedScroll = false;
                        lastFragment.showAsSheet(new PremiumPreviewFragment("noncontacts"), params);
                    }
                };
                if (isKeyboardVisible()) {
                    if (searchView != null) {
                        AndroidUtilities.hideKeyboard(searchView.editText);
                    }
                    AndroidUtilities.runOnUIThread(openPremium, 300);
                } else {
                    openPremium.run();
                }
            });
        }
        bulletin.show();
    }

    private int shiftDp = 4;
    private void selectDialog(View cell, TLRPC.Dialog dialog) {
        if (dialog instanceof ShareDialogsAdapter.MyStoryDialog) {
            onShareStory(cell);
            return;
        }
        if (dialog != null && (cell instanceof ShareDialogCell && ((ShareDialogCell) cell).isBlocked() || cell instanceof ProfileSearchCell && ((ProfileSearchCell) cell).isBlocked())) {
            showPremiumBlockedToast(cell, dialog.id);
            return;
        }
        if (topicsGridView.getVisibility() != View.GONE || parentActivity == null) {
            return;
        }

        if (DialogObject.isChatDialog(dialog.id)) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
            if (ChatObject.isChannel(chat) && !chat.megagroup && (!ChatObject.isCanWriteToChannel(-dialog.id, currentAccount) || hasPoll == 2 || hasPoll == 3)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                builder.setTitle(LocaleController.getString(R.string.SendMessageTitle));
                if (hasPoll == 3) {
                    if (ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_POLLS)) {
                        builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedTodoAll));
                    } else {
                        builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedTodo));
                    }
                } else if (hasPoll == 2) {
                    if (isChannel) {
                        builder.setMessage(LocaleController.getString(R.string.PublicPollCantForward));
                    } else if (ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_POLLS)) {
                        builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedPollsAll));
                    } else {
                        builder.setMessage(LocaleController.getString(R.string.ErrorSendRestrictedPolls));
                    }
                } else {
                    builder.setMessage(LocaleController.getString(R.string.ChannelCantSendMessage));
                }
                builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
                builder.show();
                return;
            }
        } else if (DialogObject.isEncryptedDialog(dialog.id) && (hasPoll != 0)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
            builder.setTitle(LocaleController.getString(R.string.SendMessageTitle));
            if (hasPoll == 3) {
                builder.setMessage(LocaleController.getString(R.string.TodoCantForwardSecretChat));
            } else if (hasPoll != 0) {
                builder.setMessage(LocaleController.getString(R.string.PollCantForwardSecretChat));
            } else {
                builder.setMessage(LocaleController.getString(R.string.InvoiceCantForwardSecretChat));
            }
            builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
            builder.show();
            return;
        }
        if (selectedDialogs.indexOfKey(dialog.id) >= 0) {
            TLRPC.Dialog selectedDialog = selectedDialogs.get(dialog.id);
            selectedDialogs.remove(dialog.id);
            selectedDialogTopics.remove(selectedDialog != null ? selectedDialog : dialog);
            if (cell instanceof ProfileSearchCell) {
                ((ProfileSearchCell) cell).setChecked(false, true);
            } else if (cell instanceof ShareDialogCell) {
                ((ShareDialogCell) cell).setChecked(false, true);
            }
            updateSelectedCount(1);
        } else {
            TLRPC.User fUser = MessagesController.getInstance(currentAccount).getUser(dialog.id);
            TLRPC.Chat fChat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
            if (isBotForumWithNotEmptyTopics(fUser) || DialogObject.isChatDialog(dialog.id) && (ChatObject.isForum(fChat) || ChatObject.isMonoForum(fChat) && ChatObject.canManageMonoForum(currentAccount, fChat))) {
                clearPendingTopicsLoad();
                selectedTopicDialog = dialog;
                updateFilterTabsVisibility(false);
                topicsLayoutManager.scrollToPositionWithOffset(0, scrollOffsetY - topicsGridView.getPaddingTop());
                AtomicReference<Runnable> timeoutRef = new AtomicReference<>();
                NotificationCenter.NotificationCenterDelegate delegate = new NotificationCenter.NotificationCenterDelegate() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    public void didReceivedNotification(int id, int account, Object... args) {
                        if (shareClosed || selectedTopicDialog != dialog) {
                            return;
                        }
                        long chatId = (long) args[0];
                        if (chatId == -dialog.id) {
                            boolean animate = shareTopicsAdapter.topics == null && MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialog.id) != null || timeoutRef.get() == null;

                            shareTopicsAdapter.topics = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialog.id);
                            shareTopicsAdapter.isBotForum = UserObject.isBotForum(currentAccount, dialog.id);
                            shareTopicsAdapter.isBotForumWithManageTopics = UserObject.isBotForumWithEditableTopics(currentAccount, dialog.id);
                            if (animate) {
                                shareTopicsAdapter.notifyDataSetChanged();
                            }

                            if (shareTopicsAdapter.topics != null) {
                                clearPendingTopicsLoad();
                            }

                            if (animate) {
                                topicsGridView.setVisibility(View.VISIBLE);
                                topicsGridView.setAlpha(0);
                                topicsBackActionBar.setVisibility(View.VISIBLE);
                                topicsBackActionBar.setAlpha(0);
                                if (UserObject.isBotForum(currentAccount, dialog.id)) {
                                    topicsBackActionBar.setTitle(DialogObject.getShortName(MessagesController.getInstance(currentAccount).getUser(dialog.id)));
                                    topicsBackActionBar.setSubtitle(LocaleController.getString(R.string.SelectChat));
                                } else if (ChatObject.isMonoForum(currentAccount, dialog.id)) {
                                    topicsBackActionBar.setTitle(ForumUtilities.getMonoForumTitle(currentAccount, MessagesController.getInstance(currentAccount).getChat(-dialog.id)));
                                    topicsBackActionBar.setSubtitle(LocaleController.getString(R.string.SelectChat));
                                } else {
                                    topicsBackActionBar.setTitle(MessagesController.getInstance(currentAccount).getChat(-dialog.id).title);
                                    topicsBackActionBar.setSubtitle(LocaleController.getString(R.string.SelectTopic));
                                }
                                searchWasVisibleBeforeTopics = searchIsVisible;

                                if (topicsAnimation != null) {
                                    topicsAnimation.cancel();
                                }

                                int[] loc = new int[2];
                                topicsAnimation = new SpringAnimation(new FloatValueHolder(0))
                                        .setSpring(new SpringForce(1000)
                                                .setStiffness(parentFragment != null && parentFragment.shareAlertDebugTopicsSlowMotion ? 10f : 800f)
                                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
                                topicsAnimation.addUpdateListener((animation, value, velocity) -> {
                                    value /= 1000;

                                    invalidateTopicsAnimation(cell, loc, value);
                                });
                                topicsAnimation.addEndListener((animation, canceled, value, velocity) -> {
                                    gridView.setVisibility(View.GONE);
                                    searchGridView.setVisibility(View.GONE);
                                    searchView.setVisibility(View.GONE);

                                    topicsAnimation = null;
                                });
                                topicsAnimation.start();

                                if (timeoutRef.get() != null) {
                                    AndroidUtilities.cancelRunOnUIThread(timeoutRef.get());
                                    timeoutRef.set(null);
                                }
                            }
                        }
                    }
                };
                final Runnable timeout = () -> {
                    timeoutRef.set(null);
                    if (!shareClosed && pendingTopicsObserver == delegate) {
                        pendingTopicsTimeout = null;
                        delegate.didReceivedNotification(
                                NotificationCenter.topicsDidLoaded,
                                currentAccount,
                                -dialog.id);
                    }
                };

                timeoutRef.set(timeout);
                pendingTopicsObserver = delegate;
                pendingTopicsTimeout = timeout;
                NotificationCenter.getInstance(currentAccount).addObserver(delegate, NotificationCenter.topicsDidLoaded);
                if (MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialog.id) != null) {
                    delegate.didReceivedNotification(NotificationCenter.topicsDidLoaded, currentAccount, -dialog.id);
                } else {
                    MessagesController.getInstance(currentAccount).getTopicsController().loadTopics(-dialog.id);
                    if (pendingTopicsObserver == delegate) {
                        AndroidUtilities.runOnUIThread(timeout, 300);
                    }
                }
                return;
            }

            selectedDialogs.put(dialog.id, dialog);
            if (cell instanceof ProfileSearchCell) {
                ((ProfileSearchCell) cell).setChecked(true, true);
            } else if (cell instanceof ShareDialogCell) {
                ((ShareDialogCell) cell).setChecked(true, true);
            }
            updateSelectedCount(2);
            if (searchIsVisible) {
                updateDialogAfterSearch(dialog, true);
                updateSearchAdapter = false;
                searchView.editText.setText("");
                checkCurrentList(false);
                AndroidUtilities.hideKeyboard(searchView.editText);
            }
        }
        if (searchAdapter != null && searchAdapter.categoryAdapter != null) {
            searchAdapter.categoryAdapter.notifyItemRangeChanged(0, searchAdapter.categoryAdapter.getItemCount());
        }
    }

    private boolean isBotForumWithNotEmptyTopics(TLRPC.User user) {
        if (!UserObject.isBotForum(user)) {
            return false;
        }

        final ArrayList<TLRPC.TL_forumTopic> topics = MessagesController.getInstance(currentAccount)
            .getTopicsController().getTopics(-user.id);

        return topics != null && !topics.isEmpty()
            || !MessagesController.getInstance(currentAccount).getTopicsController().endIsReached(-user.id);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void collapseTopics() {
        clearPendingTopicsLoad();
        if (selectedTopicDialog == null) {
            return;
        }
        TLRPC.Dialog dialog = selectedTopicDialog;
        selectedTopicDialog = null;
        updateFilterTabsVisibility(false);

        View cell = null;
        for (int i = 0; i < getMainGridView().getChildCount(); i++) {
            View child = getMainGridView().getChildAt(i);

            if (child instanceof ShareDialogCell && ((ShareDialogCell) child).getCurrentDialog() == dialog.id) {
                cell = child;
            }
        }

        if (cell == null) {
            return;
        }

        if (topicsAnimation != null) {
            topicsAnimation.cancel();
        }

        getMainGridView().setVisibility(View.VISIBLE);
        searchView.setVisibility(View.VISIBLE);

        if (searchIsVisible || searchWasVisibleBeforeTopics) {
            sizeNotifierFrameLayout.adjustPanLayoutHelper.ignoreOnce();
            searchView.editText.requestFocus();
            AndroidUtilities.showKeyboard(searchView.editText);
        }

        int[] loc = new int[2];
        View finalCell = cell;
        topicsAnimation = new SpringAnimation(new FloatValueHolder(1000))
                .setSpring(new SpringForce(0)
                        .setStiffness(parentFragment != null && parentFragment.shareAlertDebugTopicsSlowMotion ? 10f : 800f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        topicsAnimation.addUpdateListener((animation, value, velocity) -> {
            value /= 1000;

            invalidateTopicsAnimation(finalCell, loc, value);
        });
        topicsAnimation.addEndListener((animation, canceled, value, velocity) -> {
            topicsGridView.setVisibility(View.GONE);
            topicsBackActionBar.setVisibility(View.GONE);

            shareTopicsAdapter.topics = null;
            shareTopicsAdapter.notifyDataSetChanged();

            topicsAnimation = null;
            searchWasVisibleBeforeTopics = false;
        });
        topicsAnimation.start();
    }

    private void invalidateTopicsAnimation(View cell, int[] loc, float value) {
        topicsGridView.setPivotX(cell.getX() + cell.getWidth() / 2f);
        topicsGridView.setPivotY(cell.getY() + cell.getHeight() / 2f);
        topicsGridView.setScaleX(0.75f + value * 0.25f);
        topicsGridView.setScaleY(0.75f + value * 0.25f);
        topicsGridView.setAlpha(value);

        RecyclerListView mainGridView = getMainGridView();
        mainGridView.setPivotX(cell.getX() + cell.getWidth() / 2f);
        mainGridView.setPivotY(cell.getY() + cell.getHeight() / 2f);
        mainGridView.setScaleX(1f + value * 0.25f);
        mainGridView.setScaleY(1f + value * 0.25f);
        mainGridView.setAlpha(1f - value);

        searchView.setPivotX(searchView.getWidth() / 2f);
        searchView.setPivotY(0);
        searchView.setScaleX(0.9f + (1f - value) * 0.1f);
        searchView.setScaleY(0.9f + (1f - value) * 0.1f);
        searchView.setAlpha(1f - value);

        topicsBackActionBar.getBackButton().setTranslationX(-dp(16) * (1f - value));
        topicsBackActionBar.getTitleTextView().setTranslationY(dp(16) * (1f - value));
        topicsBackActionBar.getSubtitleTextView().setTranslationY(dp(16) * (1f - value));
        topicsBackActionBar.setAlpha(value);

        topicsGridView.getLocationInWindow(loc);
        float moveValue = CubicBezierInterpolator.EASE_OUT.getInterpolation(value);
        for (int i = 0; i < mainGridView.getChildCount(); i++) {
            View v = mainGridView.getChildAt(i);
            if (v instanceof ShareDialogCell) {
                v.setTranslationX((v.getX() - cell.getX()) * 0.5f * moveValue);
                v.setTranslationY((v.getY() - cell.getY()) * 0.5f * moveValue);

                if (v != cell) {
                    v.setAlpha(1f - Math.min(value, 0.5f) / 0.5f);
                } else {
                    v.setAlpha(1f - value);
                }
            }
        }
        for (int i = 0; i < topicsGridView.getChildCount(); i++) {
            View v = topicsGridView.getChildAt(i);
            if (v instanceof ShareTopicCell) {
                v.setTranslationX((float) (-(v.getX() - cell.getX()) * Math.pow(1f - moveValue, 2)));
                v.setTranslationY((float) (-(v.getY() + topicsGridView.getTranslationY() - cell.getY()) * Math.pow(1f - moveValue, 2)));
            }
        }
        containerView.requestLayout();
        mainGridView.invalidate();
    }

    @Override
    public int getContainerViewHeight() {
        return containerView.getMeasuredHeight() - containerViewTop;
    }

    private boolean checkSlowMode() {
        for (int a = 0; a < selectedDialogs.size(); a++) {
            long key = selectedDialogs.keyAt(a);
            if (AlertsCreator.checkSlowMode(getContext(), currentAccount, key, frameLayout2.getTag() != null && commentTextView.length() > 0)) {
                return true;
            }
        }
        return false;
    }

    private void sendWithoutQuote(boolean withSound, boolean hideSendersName, boolean hideCaption) {
        if (checkSlowMode()) return;

        final CharSequence[] text = new CharSequence[] { commentTextView.getText() };

        if (sendingMessageObjects != null) {
            for (int a = 0; a < selectedDialogs.size(); a++) {
                long key = selectedDialogs.keyAt(a);
                TLRPC.TL_forumTopic keyTopic = selectedDialogTopics.get(selectedDialogs.valueAt(a));

                if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                    boolean isMonoForum = MessagesController.getInstance(currentAccount).isMonoForum(key);
                    long monoForumPeerId = keyTopic != null && isMonoForum ? DialogObject.getPeerDialogId(keyTopic.from_id) : 0;
                    MessageObject replyTopMsg = keyTopic != null && !isMonoForum ? new MessageObject(currentAccount, keyTopic.topicStartMessage, false, false) : null;
                    if (replyTopMsg != null) replyTopMsg.isTopicMainMessage = true;

                    ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(currentAccount).getEntities(text, true);
                    SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                            text[0] == null ? null : text[0].toString(), key, replyTopMsg, replyTopMsg,
                            null, true, entities, null, null, withSound, 0, 0, null, false);
                    params.monoForumPeer = monoForumPeerId;
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                }

                boolean isMonoForum = MessagesController.getInstance(currentAccount).isMonoForum(key);
                long monoForumPeerId = keyTopic != null && isMonoForum ? DialogObject.getPeerDialogId(keyTopic.from_id) : 0;
                MessageObject replyTopMsg = keyTopic != null && !isMonoForum ? new MessageObject(currentAccount, keyTopic.topicStartMessage, false, false) : null;
                if (replyTopMsg != null) replyTopMsg.isTopicMainMessage = true;

                SendMessagesHelper.getInstance(currentAccount).sendMessage(
                        sendingMessageObjects, key,
                        hideSendersName,
                        hideCaption,
                        withSound, 0, 0, replyTopMsg, -1, 0, monoForumPeerId, null);
            }
            onSend(selectedDialogs, sendingMessageObjects.size(),
                    selectedDialogTopics.get(selectedDialogs.valueAt(0)), true);
            if (delegate != null) delegate.didShare();
            dismiss();
            return;
        }

        int num;
        if (switchView != null) {
            num = switchView.currentTab;
        } else {
            num = 0;
        }

        final boolean hasStory = storyItem != null;
        final String textToSend = sendingText[num];

        if (!hasStory && textToSend == null) {
            if (delegate != null) delegate.didShare();
            dismiss();
            return;
        }

        long totalPrice = 0;
        int messagesCount = 0;
        ArrayList<Long> paidDialogIds = new ArrayList<>();
        for (int a = 0; a < selectedDialogs.size(); a++) {
            final long did = selectedDialogs.keyAt(a);
            long thisPrice = MessagesController.getInstance(currentAccount).getSendPaidMessagesStars(did);
            if (thisPrice <= 0) {
                thisPrice = DialogObject.getMessagesStarsPrice(
                        MessagesController.getInstance(currentAccount).isUserContactBlocked(did));
            }
            if (hasStory) {
                if (frameLayout2.getTag() != null && commentTextView.length() > 0 && text[0] != null) {
                    if (thisPrice > 0) messagesCount++;
                    totalPrice += thisPrice;
                }
            } else {
                if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                    if (thisPrice > 0) messagesCount++;
                    totalPrice += thisPrice;
                }
            }
            if (thisPrice > 0) messagesCount++;
            totalPrice += thisPrice;
            if (thisPrice > 0 && !paidDialogIds.contains(did)) {
                paidDialogIds.add(did);
            }
        }

        AlertsCreator.ensurePaidMessagesMultiConfirmation(currentAccount, paidDialogIds, messagesCount, prices -> {
            boolean hadPaid = false;

            final ArrayList<TLRPC.MessageEntity> commentEntities =
                    frameLayout2.getTag() != null && commentTextView.length() > 0
                            ? MediaDataController.getInstance(currentAccount).getEntities(text, true)
                            : null;

            for (int a = 0; a < selectedDialogs.size(); a++) {
                long key = selectedDialogs.keyAt(a);
                boolean isMonoForum = MessagesController.getInstance(currentAccount).isMonoForum(key);
                final Long price = prices == null ? (Long) 0L : prices.get(key);
                if (price != null && price > 0) hadPaid = true;

                TLRPC.TL_forumTopic topic = selectedDialogTopics.get(selectedDialogs.get(key));
                long monoForumPeerId = topic != null && isMonoForum ? DialogObject.getPeerDialogId(topic.from_id) : 0;
                MessageObject replyTopMsg = topic != null && !isMonoForum
                        ? new MessageObject(currentAccount, topic.topicStartMessage, false, false)
                        : null;
                if (replyTopMsg != null) replyTopMsg.isTopicMainMessage = true;

                final long payStars = price == null ? 0 : price;

                if (hasStory) {
                    if (frameLayout2.getTag() != null && commentTextView.length() > 0 && text[0] != null) {
                        SendMessagesHelper.SendMessageParams commentParams = SendMessagesHelper.SendMessageParams.of(
                                text[0].toString(), key, null, replyTopMsg,
                                null, true, null, null, null, withSound, 0, 0, null, false);
                        commentParams.payStars = payStars;
                        commentParams.monoForumPeer = monoForumPeerId;
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(commentParams);
                    }
                    SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                            null, key, replyTopMsg, replyTopMsg, null, true, null,
                            null, null, withSound, 0, 0, null, false);
                    params.sendingStory = storyItem;
                    params.payStars = payStars;
                    params.monoForumPeer = monoForumPeerId;
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                } else {
                    if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                        SendMessagesHelper.SendMessageParams commentParams = SendMessagesHelper.SendMessageParams.of(
                                text[0] == null ? null : text[0].toString(),
                                key, replyTopMsg, replyTopMsg, null, true, commentEntities,
                                null, null, withSound, 0, 0, null, false);
                        commentParams.payStars = payStars;
                        commentParams.monoForumPeer = monoForumPeerId;
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(commentParams);
                    }
                    SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                            textToSend, key, replyTopMsg, replyTopMsg, null, true, null,
                            null, null, withSound, 0, 0, null, false);
                    params.payStars = payStars;
                    params.monoForumPeer = monoForumPeerId;
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                }
            }

            onSend(selectedDialogs, 1, selectedDialogTopics.get(selectedDialogs.valueAt(0)), !hadPaid);
            if (delegate != null) delegate.didShare();
            dismiss();
        });
    }

    private boolean showSendersName = true;
    private boolean hideCaptionShowSendersName = false;
    private ActionBarPopupWindow sendPopupWindow;

    private boolean showSendOptions(View view, Runnable withSoundAction, Runnable withoutSoundAction) {
        return showSendOptions(view, null, null, withSoundAction, withoutSoundAction);
    }

    private boolean showSendOptions(View view, BooleanSupplier getShowSenders, Consumer<Boolean> setShowSenders, Runnable withSoundAction, Runnable withoutSoundAction) {
        if (parentActivity == null || shareClosed) {
            return false;
        }
        closeSendPopup();
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        ActionBarPopupWindow.ActionBarPopupWindowLayout sendPopupLayout2 = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity, resourcesProvider);
        if (darkTheme) {
            ThemeBind.backgroundColor(sendPopupLayout2, Theme.key_voipgroup_inviteMembersBackground);
        }
        sendPopupLayout2.setAnimationEnabled(false);
        sendPopupLayout2.setOnTouchListener(new View.OnTouchListener() {
            private android.graphics.Rect popupRect = new android.graphics.Rect();

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        v.getHitRect(popupRect);
                        if (!popupRect.contains((int) event.getX(), (int) event.getY())) {
                            sendPopupWindow.dismiss();
                        }
                    }
                }
                return false;
            }
        });
        sendPopupLayout2.setDispatchKeyEventListener(keyEvent -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && sendPopupWindow != null && sendPopupWindow.isShowing()) {
                sendPopupWindow.dismiss();
            }
        });
        sendPopupLayout2.setShownFromBottom(false);

        if (getShowSenders != null) {
            final boolean showSenders = getShowSenders.getAsBoolean();

            ActionBarPopupWindow.ActionBarPopupWindowLayout sendPopupLayout1 = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity, resourcesProvider);
            if (darkTheme) {
                sendPopupLayout1.setBackgroundColor(getThemedColor(Theme.key_voipgroup_inviteMembersBackground));
            }
            sendPopupLayout1.setAnimationEnabled(false);
            sendPopupLayout1.setShownFromBottom(false);

            ActionBarMenuSubItem showSendersNameView = new ActionBarMenuSubItem(getContext(), true, true, false, resourcesProvider);
            if (darkTheme) {
                showSendersNameView.setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            }
            sendPopupLayout1.addView(showSendersNameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            showSendersNameView.setTextAndIcon(LocaleController.getString(R.string.ShowSendersName), 0);
            showSendersNameView.setChecked(showSenders);

            ActionBarMenuSubItem hideSendersNameView = new ActionBarMenuSubItem(getContext(), true, false, true, resourcesProvider);
            if (darkTheme) {
                hideSendersNameView.setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            }
            sendPopupLayout1.addView(hideSendersNameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            hideSendersNameView.setTextAndIcon(LocaleController.getString(R.string.HideSendersName), 0);
            hideSendersNameView.setChecked(!showSenders);

            showSendersNameView.setOnClickListener(e -> {
                showSendersNameView.setChecked(true);
                hideSendersNameView.setChecked(false);
                setShowSenders.accept(true);
            });
            hideSendersNameView.setOnClickListener(e -> {
                showSendersNameView.setChecked(false);
                hideSendersNameView.setChecked(true);
                setShowSenders.accept(false);
            });
            sendPopupLayout1.setupRadialSelectors(getThemedColor(Theme.key_dialogButtonSelector));

            layout.addView(sendPopupLayout1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, -8));
        }

        ActionBarMenuSubItem sendWithoutSoundItem = new ActionBarMenuSubItem(getContext(), true, true, resourcesProvider);
        if (darkTheme) {
            sendWithoutSoundItem.setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            sendWithoutSoundItem.setIconColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        }
        sendWithoutSoundItem.setTextAndIcon(LocaleController.getString(R.string.SendWithoutSound), R.drawable.input_notify_off);
        sendWithoutSoundItem.setMinimumWidth(dp(196));
        sendPopupLayout2.addView(sendWithoutSoundItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        sendWithoutSoundItem.setOnClickListener(v -> {
            if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                sendPopupWindow.dismiss();
            }
            withoutSoundAction.run();
        });

        ActionBarMenuSubItem sendMessageItem = new ActionBarMenuSubItem(getContext(), true, true, resourcesProvider);
        if (darkTheme) {
            sendMessageItem.setTextColor(getThemedColor(Theme.key_voipgroup_nameText));
            sendMessageItem.setIconColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        }
        sendMessageItem.setTextAndIcon(LocaleController.getString(R.string.SendMessage), R.drawable.msg_send);
        sendMessageItem.setMinimumWidth(dp(196));
        sendPopupLayout2.addView(sendMessageItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        sendMessageItem.setOnClickListener(v -> {
            if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                sendPopupWindow.dismiss();
            }
            withSoundAction.run();
        });
        setupThemedRadialSelectors(sendPopupLayout2, Theme.key_dialogButtonSelector, resourcesProvider);

        layout.addView(sendPopupLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sendPopupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        final ActionBarPopupWindow popup = sendPopupWindow;
        popup.setOnDismissListener(() -> {
            if (sendPopupWindow == popup) {
                sendPopupWindow = null;
                hideCaptionShowSendersName = false;
            }
        });
        sendPopupWindow.setAnimationEnabled(false);
        sendPopupWindow.setAnimationStyle(R.style.PopupContextAnimation2);
        sendPopupWindow.setOutsideTouchable(true);
        sendPopupWindow.setClippingEnabled(true);
        sendPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        sendPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        sendPopupWindow.getContentView().setFocusableInTouchMode(true);
        SharedConfig.removeScheduledOrNoSoundHint();

        layout.measure(View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
        sendPopupWindow.setFocusable(true);
        int[] location = new int[2];
        view.getLocationInWindow(location);
        int y;
        if (keyboardVisible && parentFragment != null && parentFragment.contentView.getMeasuredHeight() > dp(58)) {
            y = location[1] + view.getMeasuredHeight();
        } else {
            y = location[1] - layout.getMeasuredHeight() - dp(2);
        }
        sendPopupWindow.showAtLocation(view, Gravity.LEFT | Gravity.TOP, location[0] + view.getMeasuredWidth() - layout.getMeasuredWidth() + dp(8), y);
        sendPopupWindow.dimBehind();
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignored) {}

        return true;
    }

    protected void sendInternal(boolean withSound) {
        for (int a = 0; a < selectedDialogs.size(); a++) {
            long key = selectedDialogs.keyAt(a);
            if (AlertsCreator.checkSlowMode(getContext(), currentAccount, key, frameLayout2.getTag() != null && commentTextView.length() > 0)) {
                return;
            }
        }

        CharSequence[] text = new CharSequence[] { commentTextView.getText() };
        ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(currentAccount).getEntities(text, true);
        final int video_timestamp;
        if (timestampCheckbox != null && timestampCheckbox.isChecked()) {
            video_timestamp = timestamp;
        } else {
            video_timestamp = -1;
        }

        long totalPrice = 0;
        int messagesCount = 0;
        ArrayList<Long> paidDialogIds = new ArrayList<>();
        if (sendingMessageObjects != null) {
            for (int a = 0; a < selectedDialogs.size(); a++) {
                final long did = selectedDialogs.keyAt(a);
                long thisPrice = MessagesController.getInstance(currentAccount).getSendPaidMessagesStars(did);
                if (thisPrice <= 0) {
                    thisPrice = DialogObject.getMessagesStarsPrice(MessagesController.getInstance(currentAccount).isUserContactBlocked(did));
                }
                if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                    if (thisPrice > 0) messagesCount++;
                    totalPrice += thisPrice;
                }
                if (thisPrice > 0) messagesCount++;
                totalPrice += thisPrice;
                if (thisPrice > 0 && !paidDialogIds.contains(did)) {
                    paidDialogIds.add(did);
                }
            }
        } else {
            int num;
            if (switchView != null) {
                num = switchView.currentTab;
            } else {
                num = 0;
            }
            if (storyItem != null) {
                for (int a = 0; a < selectedDialogs.size(); a++) {
                    final long did = selectedDialogs.keyAt(a);
                    long thisPrice = MessagesController.getInstance(currentAccount).getSendPaidMessagesStars(did);
                    if (thisPrice <= 0) {
                        thisPrice = DialogObject.getMessagesStarsPrice(MessagesController.getInstance(currentAccount).isUserContactBlocked(did));
                    }
                    if (storyItem != null) {
                        if (frameLayout2.getTag() != null && commentTextView.length() > 0 && text[0] != null) {
                            if (thisPrice > 0) messagesCount++;
                            totalPrice += thisPrice;
                        }
                    }
                    if (thisPrice > 0) messagesCount++;
                    totalPrice += thisPrice;
                    if (thisPrice > 0 && !paidDialogIds.contains(did)) {
                        paidDialogIds.add(did);
                    }
                }
            } else if (sendingText[num] != null) {
                for (int a = 0; a < selectedDialogs.size(); a++) {
                    final long did = selectedDialogs.keyAt(a);
                    long thisPrice = MessagesController.getInstance(currentAccount).getSendPaidMessagesStars(did);
                    if (thisPrice <= 0) {
                        thisPrice = DialogObject.getMessagesStarsPrice(MessagesController.getInstance(currentAccount).isUserContactBlocked(did));
                    }
                    if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                        if (thisPrice > 0) messagesCount++;
                        totalPrice += thisPrice;
                    }
                    if (thisPrice > 0) messagesCount++;
                    totalPrice += thisPrice;
                    if (thisPrice > 0 && !paidDialogIds.contains(did)) {
                        paidDialogIds.add(did);
                    }
                }
            }
        }

        AlertsCreator.ensurePaidMessagesMultiConfirmation(currentAccount, paidDialogIds, messagesCount, prices -> {
            boolean hadPaid = false;
            if (sendingMessageObjects != null) {
                List<Long> removeKeys = new ArrayList<>();
                for (int a = 0; a < selectedDialogs.size(); a++) {
                    long key = selectedDialogs.keyAt(a);
                    boolean isMonoForum = MessagesController.getInstance(currentAccount).isMonoForum(key);
                    final Long price = prices == null ? (Long) 0L : prices.get(key);
                    if (price != null && price > 0) hadPaid = true;
                    TLRPC.TL_forumTopic topic = selectedDialogTopics.get(selectedDialogs.get(key));
                    long monoForumPeerId = topic != null && isMonoForum ? DialogObject.getPeerDialogId(topic.from_id) : 0;

                    MessageObject replyTopMsg = topic != null && !isMonoForum ? new MessageObject(currentAccount, topic.topicStartMessage, false, false) : null;
                    if (replyTopMsg != null) {
                        replyTopMsg.isTopicMainMessage = true;
                    }
                    int result;
                    if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(text[0] == null ? null : text[0].toString(), key, replyTopMsg, replyTopMsg, null, true, entities, null, null, withSound, 0, 0, null, false);
                        params.payStars = price == null ? 0 : price;
                        params.monoForumPeer = monoForumPeerId;
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                    }
                    result = SendMessagesHelper.getInstance(currentAccount).sendMessage(sendingMessageObjects, key, !showSendersName,false, withSound, 0, 0, replyTopMsg, video_timestamp, price == null ? 0 : price, monoForumPeerId, null);
                    if (result != 0) {
                        removeKeys.add(key);
                    }
                    if (selectedDialogs.size() == 1) {
                        AlertsCreator.showSendMediaAlert(result, parentFragment, null);

                        if (result != 0) {
                            break;
                        }
                    }
                }
                for (long key : removeKeys) {
                    TLRPC.Dialog dialog = selectedDialogs.get(key);
                    selectedDialogs.remove(key);
                    if (dialog != null) {
                        selectedDialogTopics.remove(dialog);
                    }
                }
                if (!selectedDialogs.isEmpty()) {
                    onSend(selectedDialogs, sendingMessageObjects.size(), selectedDialogs.size() == 1 ? selectedDialogTopics.get(selectedDialogs.valueAt(0)) : null, !hadPaid);
                }
            } else {
                int num;
                if (switchView != null) {
                    num = switchView.currentTab;
                } else {
                    num = 0;
                }
                if (storyItem != null) {
                    for (int a = 0; a < selectedDialogs.size(); a++) {
                        long key = selectedDialogs.keyAt(a);
                        boolean isMonoForum = MessagesController.getInstance(currentAccount).isMonoForum(key);
                        final Long price = prices == null ? (Long) 0L : prices.get(key);
                        if (price != null && price > 0) hadPaid = true;
                        TLRPC.TL_forumTopic topic = selectedDialogTopics.get(selectedDialogs.get(key));
                        long monoForumPeerId = topic != null && isMonoForum ? DialogObject.getPeerDialogId(topic.from_id) : 0;
                        MessageObject replyTopMsg = topic != null && !isMonoForum ? new MessageObject(currentAccount, topic.topicStartMessage, false, false) : null;

                        SendMessagesHelper.SendMessageParams params;
                        if (storyItem == null) {
                            if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                                params = SendMessagesHelper.SendMessageParams.of(text[0] == null ? null : text[0].toString(), key, replyTopMsg, replyTopMsg, null, true, entities, null, null, withSound, 0, 0, null, false);
                            } else {
                                params = SendMessagesHelper.SendMessageParams.of(sendingText[num], key, replyTopMsg, replyTopMsg, null, true, null, null, null, withSound, 0, 0, null, false);
                            }
                        } else {
                            if (frameLayout2.getTag() != null && commentTextView.length() > 0 && text[0] != null) {
                                SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(text[0].toString(), key, null, replyTopMsg, null, true, null, null, null, withSound, 0, 0, null, false));
                            }
                            params = SendMessagesHelper.SendMessageParams.of(null, key, replyTopMsg, replyTopMsg, null, true, null, null, null, withSound, 0, 0, null, false);
                            params.sendingStory = storyItem;
                        }
                        params.payStars = price == null ? 0 : price;
                        params.monoForumPeer = monoForumPeerId;
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                    }
                } else if (sendingText[num] != null) {
                    for (int a = 0; a < selectedDialogs.size(); a++) {
                        long key = selectedDialogs.keyAt(a);
                        boolean isMonoForum = MessagesController.getInstance(currentAccount).isMonoForum(key);
                        final Long price = prices == null ? (Long) 0L : prices.get(key);
                        if (price != null && price > 0) hadPaid = true;
                        TLRPC.TL_forumTopic topic = selectedDialogTopics.get(selectedDialogs.get(key));
                        long monoForumPeerId = topic != null && isMonoForum ? DialogObject.getPeerDialogId(topic.from_id) : 0;
                        MessageObject replyTopMsg = topic != null && !isMonoForum ? new MessageObject(currentAccount, topic.topicStartMessage, false, false) : null;

                        if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                            SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(text[0] == null ? null : text[0].toString(), key, replyTopMsg, replyTopMsg, null, true, entities, null, null, withSound, 0, 0, null, false);
                            params.payStars = price == null ? 0 : price;
                            params.monoForumPeer = monoForumPeerId;
                            SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                        }
                        SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(sendingText[num], key, replyTopMsg, replyTopMsg, null, true, null, null, null, withSound, 0, 0, null, false);
                        params2.payStars = price == null ? 0 : price;
                        params2.monoForumPeer = monoForumPeerId;
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(params2);
                    }
                }

                onSend(selectedDialogs, 1, selectedDialogTopics.get(selectedDialogs.valueAt(0)), !hadPaid);
            }
            if (delegate != null) {
                delegate.didShare();
            }
            dismiss();
        });
    }

    protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {

    }

    protected boolean doSend(LongSparseArray<TLRPC.Dialog> dids, TLRPC.TL_forumTopic topic) {
        return false;
    }

    private int getCurrentTop() {
        if (gridView.getChildCount() != 0) {
            View child = gridView.getChildAt(0);
            RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
            if (holder != null) {
                return gridView.getPaddingTop() - (holder.getLayoutPosition() == 0 && child.getTop() >= 0 ? child.getTop() : 0);
            }
        }
        return -1000;
    }

    private RecyclerListView getMainGridView() {
        return searchIsVisible || searchWasVisibleBeforeTopics ? searchGridView : gridView;
    }

    public void setDelegate(ShareAlertDelegate shareAlertDelegate) {
        delegate = shareAlertDelegate;
    }

    private void clearPendingTopicsLoad() {
        if (pendingTopicsObserver != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(
                    pendingTopicsObserver, NotificationCenter.topicsDidLoaded);
            pendingTopicsObserver = null;
        }

        if (pendingTopicsTimeout != null) {
            AndroidUtilities.cancelRunOnUIThread(pendingTopicsTimeout);
            pendingTopicsTimeout = null;
        }
    }

    private void closeSendPopup() {
        final ActionBarPopupWindow popup = sendPopupWindow;
        sendPopupWindow = null;
        hideCaptionShowSendersName = false;

        if (popup != null) {
            popup.dismiss(false);
        }
    }

    private void cleanupShareResources() {
        if (shareClosed) {
            return;
        }
        shareClosed = true;
        if (filterTabsAnimator != null) {
            filterTabsAnimator.removeAllListeners();
            filterTabsAnimator.cancel();
            filterTabsAnimator = null;
        }
        folderPagePreparer.stop();
        folderCaptureNodes.clear();
        for (FolderPage page : folderPages) page.clearPrepared();
        cancelFolderSwipe();
        if (quickShareCells != null) {
            quickShareCells.stop();
            quickShareCells = null;
        }
        clearPendingTopicsLoad();
        closeSendPopup();
        if (filterTabsView != null) {
            filterTabsView.stopAnimatingIndicator();
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(
                this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(currentAccount).removeObserver(
                this, NotificationCenter.dialogFiltersUpdated);
    }

    @Override
    public void dismissInternal() {
        cleanupShareResources();
        super.dismissInternal();
        if (commentTextView != null) {
            commentTextView.onDestroy();
        }
    }

    @Override
    public void onBackPressed() {
        if (selectedTopicDialog != null) {
            collapseTopics();
            return;
        }
        if (commentTextView != null && commentTextView.isPopupShowing()) {
            commentTextView.hidePopup(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (shareClosed) {
            return;
        }
        if (id == NotificationCenter.dialogsNeedReload) {
            cancelFolderSwipe();
            if (listAdapter != null) {
                listAdapter.fetchDialogs();
            }
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload);
        } else if (id == NotificationCenter.dialogFiltersUpdated) {
            updateFilterTabs(true);
            if (listAdapter != null) {
                listAdapter.fetchDialogs();
            }
        }
        folderPagePreparer.schedule();
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    int lastOffset = Integer.MAX_VALUE;

    @SuppressLint("NewApi")
    private void updateLayout() {
        if (panTranslationMoveLayout) {
            return;
        }
        View child;
        RecyclerListView.Holder holder = null;
        RecyclerListView listView = searchIsVisible ? searchGridView : gridView;

        if (listView.getChildCount() <= 0) {
            return;
        }
        child = listView.getChildAt(0);
        for (int i = 0; i < listView.getChildCount(); i++) {
            if (listView.getChildAt(i).getTop() < child.getTop()) {
                child = listView.getChildAt(i);
            }
        }
        holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);

        int top = child.getTop() - dp(8);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            lastOffset = child.getTop();
            newOffset = top;
            runShadowAnimation(0, false);
        } else {
            lastOffset = Integer.MAX_VALUE;
            runShadowAnimation(0, listView.canScrollVertically(-1));
        }

        if (topicsGridView.getVisibility() == View.VISIBLE) {
            listView = topicsGridView;

            if (listView.getChildCount() <= 0) {
                return;
            }
            child = listView.getChildAt(0);
            for (int i = 0; i < listView.getChildCount(); i++) {
                if (listView.getChildAt(i).getTop() < child.getTop()) {
                    child = listView.getChildAt(i);
                }
            }
            holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);

            int topicsTop = child.getTop() - dp(8);
            int topicsNewOffset = topicsTop > 0 && holder != null && holder.getAdapterPosition() == 0 ? topicsTop : 0;
            if (topicsTop >= 0 && holder != null && holder.getAdapterPosition() == 0) {
                lastOffset = child.getTop();
                topicsNewOffset = topicsTop;
                runShadowAnimation(0, false);
            } else {
                lastOffset = Integer.MAX_VALUE;
                runShadowAnimation(0, listView.canScrollVertically(-1));
            }
            newOffset = AndroidUtilities.lerp(newOffset, topicsNewOffset, topicsGridView.getAlpha());
        }

        if (scrollOffsetY != newOffset) {
            previousScrollOffsetY = scrollOffsetY;
            gridView.setTopGlowOffset(scrollOffsetY = (int) (newOffset + currentPanTranslationY));
            searchGridView.setTopGlowOffset(scrollOffsetY = (int) (newOffset + currentPanTranslationY));
            topicsGridView.setTopGlowOffset(scrollOffsetY = (int) (newOffset + currentPanTranslationY));
            frameLayout.setTranslationY(scrollOffsetY + currentPanTranslationY);
            searchEmptyView.setTranslationY(scrollOffsetY + currentPanTranslationY);
            containerView.invalidate();
        }
    }

    private void runShadowAnimation(final int num, final boolean show) {
        if (num == 0 && headerFadeView != null) {
            return;
        }
        if (show && shadow[num].getTag() != null || !show && shadow[num].getTag() == null) {
            shadow[num].setTag(show ? null : 1);
            if (show) {
                shadow[num].setVisibility(View.VISIBLE);
            }
            if (shadowAnimation[num] != null) {
                shadowAnimation[num].cancel();
            }
            shadowAnimation[num] = new AnimatorSet();
            shadowAnimation[num].playTogether(ObjectAnimator.ofFloat(shadow[num], View.ALPHA, show ? 1.0f : 0.0f));
            shadowAnimation[num].setDuration(150);
            shadowAnimation[num].addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                        if (!show) {
                            shadow[num].setVisibility(View.INVISIBLE);
                        }
                        shadowAnimation[num] = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                        shadowAnimation[num] = null;
                    }
                }
            });
            shadowAnimation[num].start();
        }
    }

    private void copyLink(Context context) {
        if (exportedMessageLink == null && linkToCopy[0] == null) {
            return;
        }
        try {
            final String link = getLink();
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("label", link != null ? link : exportedMessageLink.link);
            clipboard.setPrimaryClip(clip);
            if ((delegate == null || !delegate.didCopy()) && parentActivity instanceof LaunchActivity) {
                final boolean isPrivate = exportedMessageLink != null && exportedMessageLink.link.contains("/c/");
                ((LaunchActivity) parentActivity).showBulletin(factory -> factory.createCopyLinkBulletin(isPrivate));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private boolean showCommentTextView(final boolean show) {
        if (show == (frameLayout2.getTag() != null)) {
            return false;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        frameLayout2.setTag(show ? 1 : null);
        if (commentTextView.getEditText().isFocused()) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        commentTextView.hidePopup(true);
        if (show) {
            frameLayout2.setVisibility(View.VISIBLE);
            if (timestampFrameLayout != null && pickerBottom == null) {
                timestampFrameLayout.setVisibility(View.VISIBLE);
            }
            writeButtonContainer.setVisibility(View.VISIBLE);
            hideSendersNameButtonContainer.setVisibility(View.VISIBLE);
            hideCaptionButtonContainer.setVisibility(View.VISIBLE);
        } else {
            if (pickerBottom != null) {
                pickerBottom.setVisibility(View.VISIBLE);
            }
        }
        if (pickerBottom != null) {
            ViewCompat.setImportantForAccessibility(pickerBottom, show ? ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        if (sharesCountLayout != null) {
            ViewCompat.setImportantForAccessibility(sharesCountLayout, show ? ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(ObjectAnimator.ofFloat(frameLayout2, View.ALPHA, show ? 1.0f : 0.0f));
        if (timestampFrameLayout != null && pickerBottom == null) {
            animators.add(ObjectAnimator.ofFloat(timestampFrameLayout, View.ALPHA, show ? 1.0f : 0.0f));
        }
        animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_X, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_Y, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.ALPHA, show ? 1.0f : 0.0f));
        animators.add(ObjectAnimator.ofFloat(hideSendersNameButtonContainer, View.SCALE_X, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(hideSendersNameButtonContainer, View.SCALE_Y, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(hideSendersNameButtonContainer, View.ALPHA, show ? 1.0f : 0.0f));
        animators.add(ObjectAnimator.ofFloat(hideCaptionButtonContainer, View.SCALE_X, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(hideCaptionButtonContainer, View.SCALE_Y, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(hideCaptionButtonContainer, View.ALPHA, show ? 1.0f : 0.0f));
        if (pickerBottom == null || pickerBottom.getVisibility() != View.VISIBLE) {
            animators.add(ObjectAnimator.ofFloat(shadow[1], View.ALPHA, show ? 1.0f : 0.0f));
        }
        if (pickerBottomLayout != null) {
            animators.add(ObjectAnimator.ofFloat(pickerBottomLayout, View.TRANSLATION_Y, darkTheme ? (show ? dp(timestampLayout != null ? 5 : 16) : 0.0f) : 0));
        }

        animatorSet.playTogether(animators);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(180);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(animatorSet)) {
                    if (!show) {
                        frameLayout2.setVisibility(View.INVISIBLE);
                        if (timestampFrameLayout != null && pickerBottom == null) {
                            timestampFrameLayout.setVisibility(View.INVISIBLE);
                        }
                        writeButtonContainer.setVisibility(View.INVISIBLE);
                        hideSendersNameButtonContainer.setVisibility(View.INVISIBLE);
                        hideCaptionButtonContainer.setVisibility(View.INVISIBLE);
                    } else {
                        if (pickerBottom != null) {
                            pickerBottom.setVisibility(View.INVISIBLE);
                        }
                    }
                    animatorSet = null;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(animatorSet)) {
                    animatorSet = null;
                }
            }
        });
        animatorSet.start();
        return true;
    }

    public void updateSelectedCount(int animated) {
        if (selectedDialogs.size() == 0) {
            showCommentTextView(false);
        } else {
            int messagesCount = sendingMessageObjects == null ? 1 : sendingMessageObjects.size();
            if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                messagesCount++;
            }
            long price = 0;
            for (int i = 0; i < selectedDialogs.size(); ++i) {
                final long did = selectedDialogs.valueAt(i).id;
                long thisPrice = MessagesController.getInstance(currentAccount).getSendPaidMessagesStars(did);
                if (thisPrice <= 0) {
                    thisPrice = DialogObject.getMessagesStarsPrice(MessagesController.getInstance(currentAccount).isUserContactBlocked(did));
                }
                price += thisPrice;
            }
            writeButton.setCount(Math.max(1, selectedDialogs.size()), animated != 0);
            writeButton.setStarsPrice(price, messagesCount, animated != 0);
            hideSendersNameButton.setCount(Math.max(1, selectedDialogs.size()), animated != 0);
            hideSendersNameButton.setStarsPrice(price, messagesCount, animated != 0);
            hideCaptionButton.setCount(Math.max(1, selectedDialogs.size()), animated != 0);
            hideCaptionButton.setStarsPrice(price, messagesCount, animated != 0);
            showCommentTextView(true);

            commentTextView.setPadding(0, 0, Math.max(dp(84), writeButton.width()), 0);
        }
    }

    @Override
    public void dismiss() {
        cleanupShareResources();
        if (commentTextView != null) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        fullyShown = false;
        super.dismiss();
    }

    private class ShareDialogsAdapter extends RecyclerListView.SelectionAdapter {

        private class MyStoryDialog extends TLRPC.Dialog {
            { id = Long.MAX_VALUE; }
        }

        private Context context;
        private int currentCount;
        private int rootDialogsCount;
        private int dialogFilterId;
        private int loadedGeneration = -1;
        private int bottomSpacerHeight;
        private ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
        private LongSparseArray<TLRPC.Dialog> dialogsMap = new LongSparseArray<>();

        public ShareDialogsAdapter(Context context) {
            this(context, selectedDialogFilterId);
        }

        public ShareDialogsAdapter(Context context, int filterId) {
            this(context, filterId, false);
        }

        private ShareDialogsAdapter(Context context, int filterId, boolean deferLoad) {
            this.context = context;
            dialogFilterId = filterId;
            if (!deferLoad) fetchDialogs(filterId);
        }

        private void addDialog(TLRPC.Dialog dialog) {
            if (dialog == null || dialogsMap.indexOfKey(dialog.id) >= 0) {
                return;
            }
            dialogs.add(dialog);
            dialogsMap.put(dialog.id, dialog);
        }

        public void fetchDialogs() {
            folderCaptureNodes.clear();
            rootShareDialogs = null;
            shareDialogsGeneration++;
            filterDialogsGenerations.clear();
            fetchDialogs(selectedDialogFilterId);
        }

        public boolean fetchDialogs(int filterId) {
            if (dialogFilterId == filterId && loadedGeneration == shareDialogsGeneration) return false;
            dialogFilterId = filterId;
            dialogs.clear();
            dialogsMap.clear();
            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            MessagesController.DialogFilter filter = getDialogFilter(filterId);
            if (rootShareDialogs == null) {
                if (includeStory) {
                    addDialog(new MyStoryDialog());
                }
                long selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
                if (!messagesController.dialogsForward.isEmpty()) {
                    addDialog(messagesController.dialogsForward.get(0));
                }
                ArrayList<TLRPC.Dialog> archivedDialogs = new ArrayList<>();
                ArrayList<TLRPC.Dialog> allDialogs = messagesController.getAllDialogs();
                for (int a = 0; a < allDialogs.size(); a++) {
                    TLRPC.Dialog dialog = allDialogs.get(a);
                    if (!(dialog instanceof TLRPC.TL_dialog) || dialog.id == selfUserId || DialogObject.isEncryptedDialog(dialog.id)) {
                        continue;
                    }
                    if (DialogObject.isUserDialog(dialog.id)) {
                        if (dialog.folder_id == 1) {
                            archivedDialogs.add(dialog);
                        } else {
                            addDialog(dialog);
                        }
                    } else {
                        TLRPC.Chat chat = messagesController.getChat(-dialog.id);
                        if (!(chat == null || ChatObject.isNotInChat(chat) || chat.gigagroup && !ChatObject.hasAdminRights(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                            if (dialog.folder_id == 1) {
                                archivedDialogs.add(dialog);
                            } else {
                                addDialog(dialog);
                            }
                        }
                    }
                }
                for (int i = 0; i < archivedDialogs.size(); i++) {
                    addDialog(archivedDialogs.get(i));
                }
                applyDebugMode();
                rootShareDialogs = new ArrayList<>(dialogs);
            } else if (filter == null || filter.isDefault()) {
                dialogs.addAll(rootShareDialogs);
                for (int i = 0; i < dialogs.size(); i++) {
                    final TLRPC.Dialog dialog = dialogs.get(i);
                    dialogsMap.put(dialog.id, dialog);
                }
            }
            rootDialogsCount = rootShareDialogs.size();

            if (filter != null && !filter.isDefault()) {
                dialogs.clear();
                dialogsMap.clear();
                if (includeStory) {
                    addDialog(new MyStoryDialog());
                }
                if (filterDialogsGenerations.get(filter.id, -1) != shareDialogsGeneration) {
                    messagesController.updateFilterDialogs(filter);
                    filterDialogsGenerations.put(filter.id, shareDialogsGeneration);
                }
                for (int i = 0; i < filter.dialogsForward.size(); i++) {
                    addDialog(filter.dialogsForward.get(i));
                }
                applyDebugMode();
            }
            loadedGeneration = shareDialogsGeneration;
            notifyDataSetChanged();
            return true;
        }

        private void applyDebugMode() {
            if (parentFragment != null) {
                switch (parentFragment.shareAlertDebugMode) {
                    case ChatActivity.DEBUG_SHARE_ALERT_MODE_LESS:
                        List<TLRPC.Dialog> sublist = new ArrayList<>(dialogs.subList(0, Math.min(4, dialogs.size())));
                        dialogs.clear();
                        dialogs.addAll(sublist);
                        break;
                    case ChatActivity.DEBUG_SHARE_ALERT_MODE_MORE:
                        while (!dialogs.isEmpty() && dialogs.size() < 80) {
                            dialogs.add(dialogs.get(dialogs.size() - 1));
                        }
                        break;
                }
            }
        }

        @Override
        public int getItemCount() {
            return dialogs.size() + 2;
        }

        public TLRPC.Dialog getItem(int position) {
            position--;
            if (position < 0 || position >= dialogs.size()) {
                return null;
            }
            return dialogs.get(position);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = quickShareCells == null ? null : quickShareCells.take();
                    if (view == null) view = new ShareDialogCell(context, ShareDialogCell.TYPE_SHARE, resourcesProvider) {
                        @Override
                        protected String repostToCustomName() {
                            if (includeStoryFromMessage) {
                                return LocaleController.getString(R.string.RepostToStory);
                            }
                            return super.repostToCustomName();
                        }
                    };
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(100)));
                    break;
                }
                case BOTTOM_SPACER_VIEW_TYPE: {
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, bottomSpacerHeight));
                    break;
                }
                case 1:
                default: {
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(getTopSpacerHeight())));
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ShareDialogCell cell = (ShareDialogCell) holder.itemView;
                TLRPC.Dialog dialog = getItem(position);
                if (dialog == null) return;

                TLRPC.Dialog selectedDialog = selectedDialogs.get(dialog.id);
                cell.setTopic(selectedDialogTopics.get(selectedDialog != null ? selectedDialog : dialog), MessagesController.getInstance(currentAccount).isMonoForum(dialog.id), false);
                cell.setDialog(dialog.id, selectedDialogs.indexOfKey(dialog.id) >= 0, null);
            } else if (holder.getItemViewType() == BOTTOM_SPACER_VIEW_TYPE) {
                updateBottomSpacerHeight(holder.itemView, bottomSpacerHeight);
            } else {
                updateTopSpacerHeight(holder.itemView);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 1;
            }
            if (position == getItemCount() - 1) {
                return BOTTOM_SPACER_VIEW_TYPE;
            }
            return 0;
        }
    }

    private class ShareTopicsAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private boolean isBotForum;
        private boolean isBotForumWithManageTopics;
        private List<TLRPC.TL_forumTopic> topics;

        public ShareTopicsAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return (topics != null ? topics.size() : 0) + 2
                + (isBotForum ? 1 : 0);
        }

        public TLRPC.TL_forumTopic getItemTopic(int position) {
            position--;
            if (isBotForum) {
                position--;
            }
            if (topics == null || position < 0 || position >= topics.size()) {
                return null;
            }
            return topics.get(position);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new ShareTopicCell(context, resourcesProvider);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(100)));
                    break;
                }
                case BOTTOM_SPACER_VIEW_TYPE: {
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, topicsBottomSpacerHeight));
                    break;
                }
                case 1:
                default: {
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(getTopSpacerHeight())));
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ShareTopicCell cell = (ShareTopicCell) holder.itemView;
                if (position == 1 && isBotForum) {
                    cell.setAsNewBotForumTopic(isBotForumWithManageTopics);
                } else if (topics != null) {
                    TLRPC.TL_forumTopic topic = getItemTopic(position);
                    cell.setTopic(selectedTopicDialog, topic, topic != null && selectedDialogs.indexOfKey(topic.id) >= 0, null);
                }
            } else if (holder.getItemViewType() == BOTTOM_SPACER_VIEW_TYPE) {
                updateBottomSpacerHeight(holder.itemView, topicsBottomSpacerHeight);
            } else {
                updateTopSpacerHeight(holder.itemView);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 1 : position == getItemCount() - 1 ? BOTTOM_SPACER_VIEW_TYPE : 0;
        }
    }

    public static class DialogSearchResult {
        public TLRPC.Dialog dialog = new TLRPC.TL_dialog();
        public TLObject object;
        public int date;
        public CharSequence name;
    }

    public class ShareSearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private ArrayList<Object> searchResult = new ArrayList<>();
        private SearchAdapterHelper searchAdapterHelper;
        private Runnable searchRunnable;
        private Runnable searchRunnable2;
        private String lastSearchText;
        private int reqId;
        private int lastReqId;
        private int lastSearchId;

        private int lastGlobalSearchId;
        private int lastLocalSearchId;

        int hintsCell = -1;
        int resentTitleCell = -1;
        int firstEmptyViewCell = -1;
        int recentDialogsStartRow = -1;
        int searchResultsStartRow = -1;
        int lastFilledItem = -1;

        int itemsCount;

        DialogsSearchAdapter.CategoryAdapterRecycler categoryAdapter;
        RecyclerView categoryListView;

        public ShareSearchAdapter(Context context) {
            this.context = context;
            searchAdapterHelper = new SearchAdapterHelper(false) {
                @Override
                protected boolean filter(TLObject obj) {
                    return !(obj instanceof TLRPC.Chat) || ChatObject.canWriteToChat((TLRPC.Chat) obj);
                }
            };
            searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
                @Override
                public void onDataSetChanged(int searchId) {
                    lastGlobalSearchId = searchId;
                    if (lastLocalSearchId != searchId) {
                        searchResult.clear();
                    }
                    int oldItemsCount = lastItemCont;
                    if (getItemCount() == 0 && !searchAdapterHelper.isSearchInProgress() && !internalDialogsIsSearching) {
                        searchEmptyView.showProgress(false, true);
                    } else {
                        recyclerItemsEnterAnimator.showItemsAnimated(oldItemsCount);
                    }
                    notifyDataSetChanged();
                    checkCurrentList(true);
                }

                @Override
                public boolean canApplySearchResults(int searchId) {
                    return searchId == lastSearchId;
                }
            });
        }

        boolean internalDialogsIsSearching = false;
        private void searchDialogsInternal(final String query, final int searchId) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                try {
                    String search1 = query.trim().toLowerCase();
                    if (search1.length() == 0) {
                        lastSearchId = -1;
                        updateSearchResults(new ArrayList<>(), lastSearchId);
                        return;
                    }
                    String search2 = LocaleController.getInstance().getTranslitString(search1);
                    if (search1.equals(search2) || search2.length() == 0) {
                        search2 = null;
                    }
                    String[] search = new String[1 + (search2 != null ? 1 : 0)];
                    search[0] = search1;
                    if (search2 != null) {
                        search[1] = search2;
                    }

                    ArrayList<Long> usersToLoad = new ArrayList<>();
                    ArrayList<Long> chatsToLoad = new ArrayList<>();
                    int resultCount = 0;

                    LongSparseArray<DialogSearchResult> dialogsResult = new LongSparseArray<>();
                    SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 400");
                    while (cursor.next()) {
                        long id = cursor.longValue(0);
                        DialogSearchResult dialogSearchResult = new DialogSearchResult();
                        dialogSearchResult.date = cursor.intValue(1);
                        dialogsResult.put(id, dialogSearchResult);

                        if (DialogObject.isUserDialog(id)) {
                            if (!usersToLoad.contains(id)) {
                                usersToLoad.add(id);
                            }
                        } else if (DialogObject.isChatDialog(id)) {
                            if (!chatsToLoad.contains(-id)) {
                                chatsToLoad.add(-id);
                            }
                        }
                    }
                    cursor.dispose();

                    if (!usersToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, status, name FROM users WHERE uid IN(%s)", TextUtils.join(",", usersToLoad)));
                        while (cursor.next()) {
                            String name = cursor.stringValue(2);
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }
                            String username = null;
                            int usernamePos = name.lastIndexOf(";;;");
                            if (usernamePos != -1) {
                                username = name.substring(usernamePos + 3);
                            }
                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (username != null && username.startsWith(q)) {
                                    found = 2;
                                }
                                if (found != 0) {
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
                                        DialogSearchResult dialogSearchResult = dialogsResult.get(user.id);
                                        if (user.status != null) {
                                            user.status.expires = cursor.intValue(1);
                                        }
                                        if (found == 1) {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName(user.first_name, user.last_name, q);
                                        } else {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + UserObject.getPublicUsername(user), null, "@" + q);
                                        }
                                        dialogSearchResult.object = user;
                                        dialogSearchResult.dialog.id = user.id;
                                        resultCount++;
                                    }
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    if (!chatsToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, name FROM chats WHERE uid IN(%s)", TextUtils.join(",", chatsToLoad)));
                        while (cursor.next()) {
                            String name = cursor.stringValue(1);
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }
                            for (int a = 0; a < search.length; a++) {
                                String q = search[a];
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
                                        if (!(chat == null || ChatObject.isNotInChat(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                                            DialogSearchResult dialogSearchResult = dialogsResult.get(-chat.id);
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName(chat.title, null, q);
                                            dialogSearchResult.object = chat;
                                            dialogSearchResult.dialog.id = -chat.id;
                                            resultCount++;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    ArrayList<Object> searchResults = new ArrayList<>(resultCount);
                    for (int a = 0; a < dialogsResult.size(); a++) {
                        DialogSearchResult dialogSearchResult = dialogsResult.valueAt(a);
                        if (dialogSearchResult.object != null && dialogSearchResult.name != null) {
                            searchResults.add(dialogSearchResult);
                        }
                    }

                    cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT u.data, u.status, u.name, u.uid FROM users as u INNER JOIN contacts as c ON u.uid = c.uid");
                    while (cursor.next()) {
                        long uid = cursor.longValue(3);
                        if (dialogsResult.indexOfKey(uid) >= 0) {
                            continue;
                        }
                        String name = cursor.stringValue(2);
                        String tName = LocaleController.getInstance().getTranslitString(name);
                        if (name.equals(tName)) {
                            tName = null;
                        }
                        String username = null;
                        int usernamePos = name.lastIndexOf(";;;");
                        if (usernamePos != -1) {
                            username = name.substring(usernamePos + 3);
                        }
                        int found = 0;
                        for (String q : search) {
                            if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                found = 1;
                            } else if (username != null && username.startsWith(q)) {
                                found = 2;
                            }
                            if (found != 0) {
                                NativeByteBuffer data = cursor.byteBufferValue(0);
                                if (data != null) {
                                    TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                    data.reuse();
                                    DialogSearchResult dialogSearchResult = new DialogSearchResult();
                                    if (user.status != null) {
                                        user.status.expires = cursor.intValue(1);
                                    }
                                    dialogSearchResult.dialog.id = user.id;
                                    dialogSearchResult.object = user;
                                    if (found == 1) {
                                        dialogSearchResult.name = AndroidUtilities.generateSearchName(user.first_name, user.last_name, q);
                                    } else {
                                        dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + UserObject.getPublicUsername(user), null, "@" + q);
                                    }
                                    searchResults.add(dialogSearchResult);
                                }
                                break;
                            }
                        }
                    }
                    cursor.dispose();

                    Collections.sort(searchResults, (lhs, rhs) -> {
                        DialogSearchResult res1 = (DialogSearchResult) lhs;
                        DialogSearchResult res2 = (DialogSearchResult) rhs;
                        if (res1.date < res2.date) {
                            return 1;
                        } else if (res1.date > res2.date) {
                            return -1;
                        }
                        return 0;
                    });

                    updateSearchResults(searchResults, searchId);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }

        private void updateSearchResults(final ArrayList<Object> result, final int searchId) {
            AndroidUtilities.runOnUIThread(() -> {
                if (searchId != lastSearchId) {
                    return;
                }
                int oldItemCount = getItemCount();
                internalDialogsIsSearching = false;
                lastLocalSearchId = searchId;
                if (lastGlobalSearchId != searchId) {
                    searchAdapterHelper.clear();
                }
                if (gridView.getAdapter() != searchAdapter) {
                    topBeforeSwitch = getCurrentTop();
                    searchAdapter.notifyDataSetChanged();
                }
                for (int a = 0; a < result.size(); a++) {
                    DialogSearchResult obj = (DialogSearchResult) result.get(a);
                    if (obj.object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) obj.object;
                        MessagesController.getInstance(currentAccount).putUser(user, true);
                    } else if (obj.object instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) obj.object;
                        MessagesController.getInstance(currentAccount).putChat(chat, true);
                    }
                }
                boolean becomeEmpty = !searchResult.isEmpty() && result.isEmpty();
                boolean isEmpty = searchResult.isEmpty() && result.isEmpty();
                if (becomeEmpty) {
                    topBeforeSwitch = getCurrentTop();
                }
                searchResult = result;
                searchAdapterHelper.mergeResults(searchResult, null);
                int oldItemsCount = lastItemCont;
                if (getItemCount() == 0 && !searchAdapterHelper.isSearchInProgress() && !internalDialogsIsSearching) {
                    searchEmptyView.showProgress(false, true);
                } else {
                    recyclerItemsEnterAnimator.showItemsAnimated(oldItemsCount);
                }
                notifyDataSetChanged();
                checkCurrentList(true);

            });
        }

        public void searchDialogs(final String query) {
            if (query != null && query.equals(lastSearchText)) {
                return;
            }
            lastSearchText = query;
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (searchRunnable2 != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable2);
                searchRunnable2 = null;
            }
            searchResult.clear();
            searchAdapterHelper.mergeResults(null);
            searchAdapterHelper.queryServerSearch(null, true, true, true, true, false, 0, false, 0, 0);
            notifyDataSetChanged();
            checkCurrentList(true);

            if (TextUtils.isEmpty(query)) {
                topBeforeSwitch = getCurrentTop();
                lastSearchId = -1;
                internalDialogsIsSearching = false;
            } else {
                internalDialogsIsSearching = true;
                final int searchId = ++lastSearchId;
                searchEmptyView.showProgress(true, true);
                Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                    searchRunnable = null;
                    searchDialogsInternal(query, searchId);
                    AndroidUtilities.runOnUIThread(searchRunnable2 = () -> {
                        searchRunnable2 = null;
                        if (searchId != lastSearchId) {
                            return;
                        }
                        searchAdapterHelper.queryServerSearch(query, true, true, true, true, false, 0, false, 0, searchId);
                    });
                }, 300);
            }
            checkCurrentList(false);
        }

        int lastItemCont;

        @Override
        public int getItemCount() {
            itemsCount = 0;
            hintsCell = -1;
            resentTitleCell = -1;
            recentDialogsStartRow = -1;
            searchResultsStartRow = -1;
            lastFilledItem = -1;

            if (TextUtils.isEmpty(lastSearchText)) {
                firstEmptyViewCell = itemsCount++;
                hintsCell = itemsCount++;

                if (recentSearchObjects.size() > 0) {
                    resentTitleCell = itemsCount++;
                    recentDialogsStartRow = itemsCount;
                    itemsCount += recentSearchObjects.size();
                }
                lastFilledItem = itemsCount++;
                return lastItemCont = itemsCount;
            } else {
                firstEmptyViewCell = itemsCount++;
                searchResultsStartRow = itemsCount;
                itemsCount += (searchResult.size() + searchAdapterHelper.getLocalServerSearch().size());
                if (itemsCount == 1) {
                    firstEmptyViewCell = -1;
                    return lastItemCont = itemsCount = 0;
                }
                lastFilledItem = itemsCount++;
            }
            return lastItemCont = itemsCount;
        }

        public TLRPC.Dialog getItem(int position) {
            if (position >= recentDialogsStartRow && recentDialogsStartRow >= 0) {
                int index = position - recentDialogsStartRow;
                if (index < 0 || index >= recentSearchObjects.size()) {
                    return null;
                }
                DialogsSearchAdapter.RecentSearchObject recentSearchObject = recentSearchObjects.get(index);
                TLObject object = recentSearchObject.object;
                TLRPC.Dialog dialog = new TLRPC.TL_dialog();
                if (object instanceof TLRPC.User) {
                    dialog.id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Chat) {
                    dialog.id = -((TLRPC.Chat) object).id;
                } else {
                    return null;
                }
                return dialog;
            }
            position--;
            if (position < 0) {
                return null;
            }
            if (position < searchResult.size()) {
                return ((DialogSearchResult) searchResult.get(position)).dialog;
            } else {
                position -= searchResult.size();
            }
            ArrayList<TLObject> arrayList = searchAdapterHelper.getLocalServerSearch();
            if (position < arrayList.size()) {
                TLObject object = arrayList.get(position);
                TLRPC.Dialog dialog = new TLRPC.TL_dialog();
                if (object instanceof TLRPC.User) {
                    dialog.id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Chat) {
                    dialog.id = -((TLRPC.Chat) object).id;
                } else {
                    return null;
                }
                return dialog;
            }
            return null;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 1 || holder.getItemViewType() == 4) {
                return false;
            }
            return true;
        }


        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 5: {
                    view = new ShareDialogCell(context, ShareDialogCell.TYPE_SHARE, resourcesProvider);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(100)));
                    break;
                }
                case 0: {
                    view = new ProfileSearchCell(context, resourcesProvider).useCustomPaints().showPremiumBlock(true);
                    break;
                }
                default:
                case 1: {
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(getTopSpacerHeight())));
                    break;
                }
                case 2: {
                    RecyclerListView horizontalListView = new RecyclerListView(context, resourcesProvider) {
                        @Override
                        public boolean onInterceptTouchEvent(MotionEvent e) {
                            if (getParent() != null && getParent().getParent() != null) {
                                getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1) || canScrollHorizontally(1));
                            }
                            return super.onInterceptTouchEvent(e);
                        }
                    };
                    categoryListView = horizontalListView;
                    horizontalListView.setItemAnimator(null);
                    horizontalListView.setLayoutAnimation(null);
                    LinearLayoutManager layoutManager = new LinearLayoutManager(context) {
                        @Override
                        public boolean supportsPredictiveItemAnimations() {
                            return false;
                        }
                    };
                    layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                    horizontalListView.setLayoutManager(layoutManager);
                    horizontalListView.setAdapter(categoryAdapter = new DialogsSearchAdapter.CategoryAdapterRecycler(context, currentAccount, true, true, resourcesProvider) {
                        @Override
                        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                            HintDialogCell cell = (HintDialogCell) holder.itemView;
                            if (darkTheme || forceDarkThemeForHint) {
                                cell.setColors(Theme.key_voipgroup_nameText, Theme.key_voipgroup_inviteMembersBackground);
                            }

                            TLRPC.TL_topPeer peer = MediaDataController.getInstance(currentAccount).hints.get(position);
                            TLRPC.Chat chat = null;
                            TLRPC.User user = null;
                            long did = 0;
                            if (peer.peer.user_id != 0) {
                                did = peer.peer.user_id;
                                user = MessagesController.getInstance(currentAccount).getUser(peer.peer.user_id);
                            } else if (peer.peer.channel_id != 0) {
                                did = -peer.peer.channel_id;
                                chat = MessagesController.getInstance(currentAccount).getChat(peer.peer.channel_id);
                            } else if (peer.peer.chat_id != 0) {
                                did = -peer.peer.chat_id;
                                chat = MessagesController.getInstance(currentAccount).getChat(peer.peer.chat_id);
                            }
                            boolean animated = did == cell.getDialogId();
                            cell.setTag(did);
                            String name = "";
                            if (user != null) {
                                name = UserObject.getFirstName(user);
                            } else if (chat != null) {
                                name = chat.title;
                            }
                            cell.setDialog(did, true, name);
                            cell.setChecked(selectedDialogs.indexOfKey(did) >= 0, animated);
                        }
                    });
                    horizontalListView.setOnItemClickListener((view1, position) -> {
                        HintDialogCell cell = (HintDialogCell) view1;
                        TLRPC.TL_topPeer peer = MediaDataController.getInstance(currentAccount).hints.get(position);
                        TLRPC.Dialog dialog = new TLRPC.TL_dialog();
                        TLRPC.Chat chat = null;
                        TLRPC.User user = null;
                        long did = 0;
                        if (peer.peer.user_id != 0) {
                            did = peer.peer.user_id;
                        } else if (peer.peer.channel_id != 0) {
                            did = -peer.peer.channel_id;
                        } else if (peer.peer.chat_id != 0) {
                            did = -peer.peer.chat_id;
                        }
                        if (cell.isBlocked()) {
                            showPremiumBlockedToast(cell, did);
                            return;
                        }
                        dialog.id = did;
                        selectDialog(null, dialog);
                        cell.setChecked(selectedDialogs.indexOfKey(did) >= 0, true);
                    });
                    view = horizontalListView;
                    break;
                }
                case 3: {
                    GraySectionCell graySectionCell = new GraySectionCell(context, resourcesProvider);
                    graySectionCell.setTextColor(Theme.key_graySectionText);
                    ThemeBind.backgroundColor(graySectionCell, Theme.key_graySection, ShareAlert.this.resourcesProvider);
                    graySectionCell.setText(LocaleController.getString(R.string.Recent));
                    view = graySectionCell;
                    break;
                }
                case 4: {
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(searchLayoutManager.lastItemHeight, MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0 || holder.getItemViewType() == 5) {
//                ShareDialogCell cell = (ShareDialogCell) holder.itemView;
//                ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;
                CharSequence name = null;
                TLObject object = null;
                TLRPC.EncryptedChat ec = null;
                long id = 0;

                if (TextUtils.isEmpty(lastSearchText)) {
                    if (recentDialogsStartRow >= 0 && position >= recentDialogsStartRow) {
                        int p = position - recentDialogsStartRow;
                        DialogsSearchAdapter.RecentSearchObject recentSearchObject = recentSearchObjects.get(p);
                        object = recentSearchObject.object;
                        if (object instanceof TLRPC.User) {
                            TLRPC.User user = (TLRPC.User) object;
                            id = user.id;
                            name = ContactsController.formatName(user.first_name, user.last_name);
                        } else if (object instanceof TLRPC.Chat) {
                            TLRPC.Chat chat = (TLRPC.Chat) object;
                            id = -chat.id;
                            name = chat.title;
                        } else if (object instanceof TLRPC.TL_encryptedChat) {
                            TLRPC.TL_encryptedChat chat = (TLRPC.TL_encryptedChat) object;
                            ec = chat;
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(chat.user_id);
                            if (user != null) {
                                id = user.id;
                                name = ContactsController.formatName(user.first_name, user.last_name);
                            }
                        }
                        String foundUserName = searchAdapterHelper.getLastFoundUsername();
                        if (!TextUtils.isEmpty(foundUserName)) {
                            int index;
                            if (name != null && (index = AndroidUtilities.indexOfIgnoreCase(name.toString(), foundUserName)) != -1) {
                                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(name);
                                spannableStringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), index, index + foundUserName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                name = spannableStringBuilder;
                            }
                        }
                    }
                    if (holder.itemView instanceof ProfileSearchCell) {
                        ((ProfileSearchCell) holder.itemView).setData(object, ec, name, null, false, false);
                        ((ProfileSearchCell) holder.itemView).useSeparator = position < getItemCount() - 2;
                    } else if (holder.itemView instanceof ShareDialogCell) {
                        ((ShareDialogCell) holder.itemView).setDialog(id, selectedDialogs.indexOfKey(id) >= 0, name);
                    }
                    return;
                }
                position--;
                if (position < searchResult.size()) {
                    DialogSearchResult result = (DialogSearchResult) searchResult.get(position);
                    id = result.dialog.id;
                    name = result.name;
                } else {
                    position -= searchResult.size();
                    ArrayList<TLObject> arrayList = searchAdapterHelper.getLocalServerSearch();
                    object = arrayList.get(position);
                    if (object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) object;
                        id = user.id;
                        name = ContactsController.formatName(user.first_name, user.last_name);
                    } else {
                        TLRPC.Chat chat = (TLRPC.Chat) object;
                        id = -chat.id;
                        name = chat.title;
                    }
                    String foundUserName = searchAdapterHelper.getLastFoundUsername();
                    if (!TextUtils.isEmpty(foundUserName)) {
                        int index;
                        if (name != null && (index = AndroidUtilities.indexOfIgnoreCase(name.toString(), foundUserName)) != -1) {
                            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(name);
                            spannableStringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), index, index + foundUserName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            name = spannableStringBuilder;
                        }
                    }
                }
                if (holder.itemView instanceof ProfileSearchCell) {
                    ((ProfileSearchCell) holder.itemView).setData(object, ec, name, null, false, false);
                    ((ProfileSearchCell) holder.itemView).useSeparator = position < getItemCount() - 2;
                } else if (holder.itemView instanceof ShareDialogCell) {
                    ((ShareDialogCell) holder.itemView).setDialog(id, selectedDialogs.indexOfKey(id) >= 0, name);
                }
            } else if (holder.getItemViewType() == 2) {
                ((RecyclerListView) holder.itemView).getAdapter().notifyDataSetChanged();
            } else if (holder.getItemViewType() == 1) {
                updateTopSpacerHeight(holder.itemView);
            }
        }


        @Override
        public int getItemViewType(int position) {
            if (position == lastFilledItem) {
                return 4;
            } else if (position == firstEmptyViewCell) {
                return 1;
            } else if (position == hintsCell) {
                return 2;
            } else if (position == resentTitleCell) {
                return 3;
            }
            return TextUtils.isEmpty(lastSearchText) ? 0 : 5;
        }

        public boolean isSearching() {
            return !TextUtils.isEmpty(lastSearchText);
        }

        public int getSpanSize(int spanCount, int position) {
            if (position == hintsCell || position == resentTitleCell || position == firstEmptyViewCell || position == lastFilledItem) {
                return spanCount;
            }
            final int viewType = getItemViewType(position);
            if (viewType == 0) {
                return spanCount;
            }
            return 1;
        }
    }

    private boolean searchIsVisible;
    private boolean searchWasVisibleBeforeTopics;

    private void checkCurrentList(boolean force) {
        boolean searchVisibleLocal = false;
        if ((selectedTopicDialog != null || !TextUtils.isEmpty(searchView.editText.getText())
                || keyboardVisible && searchView.editText.hasFocus() || searchWasVisibleBeforeTopics)
                && (maybeFolderSwipe || trackingFolderSwipe || folderTargetTabId >= 0)) {
            cancelFolderSwipe();
        }
        if (!TextUtils.isEmpty(searchView.editText.getText()) || (keyboardVisible && searchView.editText.hasFocus()) || searchWasVisibleBeforeTopics) {
            searchVisibleLocal = true;
            updateSearchAdapter = true;
            if (selectedTopicDialog == null) {
                AndroidUtilities.updateViewVisibilityAnimated(gridView, false);
                AndroidUtilities.updateViewVisibilityAnimated(searchGridView, true);
            }
        } else {
            if (selectedTopicDialog == null) {
                AndroidUtilities.updateViewVisibilityAnimated(gridView, true);
                AndroidUtilities.updateViewVisibilityAnimated(searchGridView, false);
            }
        }

        if (searchIsVisible != searchVisibleLocal || force) {
            if (searchIsVisible != searchVisibleLocal) {
                searchIsVisible = searchVisibleLocal;
                updateFilterTabsVisibility(selectedTopicDialog == null && topicsAnimation == null);
            }
            searchAdapter.notifyDataSetChanged();
            listAdapter.notifyDataSetChanged();
            if (searchIsVisible) {
                if (lastOffset == Integer.MAX_VALUE) {
                    ((LinearLayoutManager) searchGridView.getLayoutManager()).scrollToPositionWithOffset(0, -searchGridView.getPaddingTop());
                } else {
                    ((LinearLayoutManager) searchGridView.getLayoutManager()).scrollToPositionWithOffset(0, lastOffset - searchGridView.getPaddingTop());
                }
                searchAdapter.searchDialogs(searchView.editText.getText().toString());
            } else {
                if (lastOffset == Integer.MAX_VALUE) {
                    layoutManager.scrollToPositionWithOffset(0, 0);
                } else {
                    layoutManager.scrollToPositionWithOffset(0, 0);
                }
            }
        }
    }

    private String getLink() {
        String link = null;
        if (switchView != null) {
            link = linkToCopy[switchView.currentTab];
        } else {
            if (exportedMessageLink != null) {
                link = exportedMessageLink.link;
            }
            if (link == null) {
                link = linkToCopy[0];
            }
        }
        if (timestampCheckbox != null && timestampCheckbox.isChecked()) {
            try {
                link = Uri.parse(link).buildUpon().appendQueryParameter("t", AndroidUtilities.formatTimestamp(timestamp)).build().toString();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (link == null) {
            return "";
        }
        return link;
    }

    private void updateLinkTextView() {
        if (linkTextView != null) {
            String link = getLink();
            if (link != null) {
                if (link.startsWith("https://")) {
                    link = link.substring(8);
                } else if (link.startsWith("http://")) {
                    link = link.substring(7);
                }
            }
            linkTextView.setText(link);
        }
    }

    private float keyboardT;
    private boolean hidePickerBottomDuringKeyboardTransition;
    private int getGridBottomSpacerHeight(int availableHeight, int dialogsCount, int bottomPadding) {
        final int contentHeight = dp(getTopSpacerHeight()) + (int) Math.ceil(dialogsCount / 4.0f) * dp(103);
        // Leave enough space to consume the collapsed offset, ending exactly at the expanded sheet.
        return Math.max(0, availableHeight - contentHeight - bottomPadding);
    }

    private void updateGridBottomSpacers(int availableHeight) {
        updateDialogGridBottomSpacer(gridView, listAdapter, availableHeight);
        if (folderGridView != null) updateDialogGridBottomSpacer(folderGridView, folderListAdapter, availableHeight);
        final int topicsHeight = getGridBottomSpacerHeight(availableHeight, shareTopicsAdapter.getItemCount() - 2, topicsGridView.getPaddingBottom());
        if (topicsBottomSpacerHeight != topicsHeight) {
            topicsBottomSpacerHeight = topicsHeight;
            shareTopicsAdapter.notifyItemChanged(shareTopicsAdapter.getItemCount() - 1);
        }
    }

    private void updateDialogGridBottomSpacer(RecyclerListView list, ShareDialogsAdapter adapter, int availableHeight) {
        final int height = getGridBottomSpacerHeight(availableHeight, adapter.dialogs.size(), list.getPaddingBottom());
        if (adapter.bottomSpacerHeight != height) {
            adapter.bottomSpacerHeight = height;
            adapter.notifyItemChanged(adapter.getItemCount() - 1);
        }
    }

    private void updateBottomSpacerHeight(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            view.setLayoutParams(params);
        }
    }

    private float getDialogListTop(View list) {
        return frameLayout == null ? 0 : Math.max(0, frameLayout.getY() + frameLayout.getHeight() - list.getY());
    }

    private float getDialogListDrawTop(View list) {
        // Like the chat list, rows keep drawing beneath the fade and the independent glass capsules.
        return sheetBackgroundTop - list.getY();
    }

    private void clipDialogListForDraw(Canvas canvas, View list) {
        // Bottom controls overlay the rows with their fade and glass, as they did before the header changes.
        canvas.clipRect(Math.max(0, -list.getTranslationX()), capturingDialogList ? -list.getY() : getDialogListDrawTop(list),
                Math.min(list.getWidth(), list.getWidth() - list.getTranslationX()), list.getHeight());
        if (!capturingDialogList && sheetGlassBackground != null) {
            canvas.translate(-list.getX(), -list.getY());
            canvas.clipPath(sheetGlassBackground.getPath());
            canvas.translate(list.getX(), list.getY());
        }
    }

    private float getDialogListBottom(View list) {
        float bottom = list.getHeight();
        if (pickerBottom != null && pickerBottom.getVisibility() == View.VISIBLE) {
            bottom = Math.min(bottom, pickerBottom.getY() - list.getY());
        }
        if (frameLayout2 != null && frameLayout2.getVisibility() == View.VISIBLE && frameLayout2.getAlpha() > 0) {
            bottom = Math.min(bottom, frameLayout2.getY() - list.getY());
        }
        if (timestampFrameLayout != null && timestampFrameLayout.getVisibility() == View.VISIBLE && timestampFrameLayout.getAlpha() > 0) {
            bottom = Math.min(bottom, timestampFrameLayout.getY() - list.getY());
        }
        return Math.max(0, bottom);
    }

    private int keyboardSize2;
    public void updateBottomOverlay() {
        if (frameLayout2 == null) return;
        final boolean emojiVisible = commentTextView != null && commentTextView.isPopupVisible();
        if (emojiVisible) {
            keyboardT = commentTextView.getEmojiPaddingShown();
        } else if (sizeNotifierFrameLayout != null && sizeNotifierFrameLayout.adjustPanLayoutHelper != null && !sizeNotifierFrameLayout.adjustPanLayoutHelper.animationInProgress()) {
            keyboardT = keyboardSize2 > dp(20) ? 1.0f : 0.0f;
        } else {
        }
        float bottomMargin = 0;
        if (timestampFrameLayout != null) {
            timestampFrameLayout.setTranslationY(-bottomMargin);
            bottomMargin += dp(48);
        }
        if (pickerBottom != null) {
            pickerBottom.setTranslationY(-bottomMargin); // sharesCountLayout is inside it
        }
        if (pickerBottomLayout != null) {
            float alpha = hidePickerBottomDuringKeyboardTransition ? 0.0f : Math.max(0.0f, Math.min(1.0f, 1.0f - keyboardT));
            pickerBottomLayout.setAlpha(alpha);
            pickerBottomLayout.setVisibility(alpha > 0.0f ? View.VISIBLE : View.INVISIBLE);
        }
        frameLayout2.setTranslationY(-bottomMargin);
        writeButtonContainer.setTranslationY(-bottomMargin);
        hideSendersNameButtonContainer.setTranslationY(-bottomMargin);
        hideCaptionButtonContainer.setTranslationY(-bottomMargin);
    }

    private void onTopicCellClick(TLRPC.TL_forumTopic topic) {
        if (topic == null || selectedTopicDialog == null) {
            return;
        }

        long dialogId = selectedTopicDialog.id;
        final boolean mono = MessagesController.getInstance(currentAccount).isMonoForum(dialogId);
        TLRPC.Dialog dialog = selectedTopicDialog;

        selectedDialogs.put(dialogId, dialog);
        selectedDialogTopics.put(dialog, topic);
        updateSelectedCount(2);

        if (searchIsVisible || searchWasVisibleBeforeTopics) {
            updateDialogAfterSearch(dialog, false);
            updateSearchAdapter = false;
            searchView.editText.setText("");
            checkCurrentList(false);
        }
        for (int i = 0; i < getMainGridView().getChildCount(); i++) {
            View child = getMainGridView().getChildAt(i);

            if (child instanceof ShareDialogCell && ((ShareDialogCell) child).getCurrentDialog() == selectedTopicDialog.id) {
                ShareDialogCell cell = (ShareDialogCell) child;

                if (cell != null) {
                    cell.setTopic(topic, mono, true);
                    cell.setChecked(true, true);
                }
            }
        }
        collapseTopics();
    }

    private void onTopicCreateCellClick() {
        if (selectedTopicDialog == null) {
            return;
        }

        long dialogId = selectedTopicDialog.id;
        TLRPC.Dialog dialog = selectedTopicDialog;

        selectedDialogs.put(dialogId, dialog);
        selectedDialogTopics.remove(dialog);
        updateSelectedCount(2);

        if (searchIsVisible || searchWasVisibleBeforeTopics) {
            updateDialogAfterSearch(dialog, false);
            updateSearchAdapter = false;
            searchView.editText.setText("");
            checkCurrentList(false);
        }
        for (int i = 0; i < getMainGridView().getChildCount(); i++) {
            View child = getMainGridView().getChildAt(i);
            if (child instanceof ShareDialogCell && ((ShareDialogCell) child).getCurrentDialog() == selectedTopicDialog.id) {
                ShareDialogCell cell = (ShareDialogCell) child;
                cell.setTopic(null, false, true);
                cell.setChecked(true, true);
            }
        }
        collapseTopics();
    }



    /* Blur */

    private GridLayoutManager createDialogLayoutManager(Context context) {
        return new GridLayoutManager(context, 4) {
            @Override
            protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extraLayoutSpace) {
                super.calculateExtraLayoutSpace(state, extraLayoutSpace);
                extendDialogListLayoutSpace(extraLayoutSpace);
            }
        };
    }

    private void extendDialogListLayoutSpace(int[] extraLayoutSpace) {
        if (iBlur3SourceGlass != null && GlassEngine.getCurrentGlassSettings().isBlurEnabled) {
            // A row above the viewport can still appear through the status-bar blur, including its outset.
            extraLayoutSpace[0] = Math.max(extraLayoutSpace[0], systemInsets.top + dp(48));
        }
    }

    private BlurredBackgroundDrawable createShareGlass(View view) {
        return iBlur3FactoryLiquidGlass.create(view, BlurredBackgroundProviderImpl.inputFieldShareAlert(resourcesProvider))
                .setRadius(dp(SHARE_GLASS_RADIUS_DP));
    }

    private float getHeaderFadeBottom() {
        float glassBottom = frameLayout.getY() + searchView.getY() + searchView.getHeight();
        if (filterTabsProgress > 0) {
            float tabsBottom = frameLayout.getY() + filterTabsView.getY() + filterTabsView.getHeight() - dp(5);
            glassBottom = AndroidUtilities.lerp(glassBottom, tabsBottom, filterTabsProgress);
        }
        if (topicsBackActionBar.getVisibility() == View.VISIBLE && topicsBackActionBar.getAlpha() > 0) {
            glassBottom = frameLayout.getY() + topicsBackActionBar.getY() + topicsBackActionBar.getHeight();
        }
        float tail = Math.max(0, dp(SHARE_FADE_HEIGHT_DP - 49) - systemInsets.bottom);
        if (pickerBottom != null && pickerBottomLayout != null && pickerBottom.getVisibility() == View.VISIBLE
                && bottomFadeView.getHeight() > 0) {
            final float bottomFadeTop = bottomFadeView.getY() + bottomFadeView.getHeight() - systemInsets.bottom - dp(SHARE_FADE_HEIGHT_DP);
            final float copyGlassTop = pickerBottom.getY() + pickerBottomLayout.getY() + dp(9);
            tail = Math.max(0, copyGlassTop - bottomFadeTop);
        }
        // Match the clear gap outside COPY LINK's glass body, measured from the actual bottom views.
        final int clearGap = dp(Math.max(0, getTopSpacerHeight() - getTopPanelHeight() + 8));
        return glassBottom + Math.min(tail, clearGap);
    }

    private void drawPinnedHeaderFade(Canvas canvas) {
        if (sheetPinnedToTopProgress <= 0) return;
        final float top = Math.max(0, sheetBackgroundTop - headerFadeView.getY());
        final float searchTop = frameLayout.getY() + searchView.getY() - headerFadeView.getY();
        final float end = getHeaderFadeBottom() - headerFadeView.getY();
        final float fadeHeight = dp(SHARE_FADE_HEIGHT_DP);
        float bottomGap = systemInsets.bottom + dp(9);
        if (pickerBottom != null && pickerBottomLayout != null && pickerBottom.getVisibility() == View.VISIBLE
                && pickerBottomLayout.getHeight() > 0 && bottomFadeView.getHeight() > 0) {
            final float bottomFadeEnd = bottomFadeView.getY() + bottomFadeView.getHeight() - systemInsets.bottom;
            final float copyGlassBottom = pickerBottom.getY() + pickerBottomLayout.getY()
                    + pickerBottomLayout.getHeight() - dp(9);
            bottomGap = bottomFadeEnd - copyGlassBottom;
        }
        bottomGap = Math.max(1, Math.min(fadeHeight - 1, bottomGap));
        headerPinnedFadeDrawable.setAlpha(Math.round(255 * sheetPinnedToTopProgress));
        headerPinnedFadeDrawable.setBounds(0, 0, headerFadeView.getWidth(), Math.round(fadeHeight));
        // Reflect the exact bottom fade segment between COPY LINK's lower edge and the screen edge
        // into the gap above search. Continue the same gradient to the list without a color jump.
        if (searchTop > top) {
            canvas.save();
            canvas.clipRect(0, top, headerFadeView.getWidth(), searchTop);
            canvas.translate(0, top);
            canvas.scale(1, (searchTop - top) / bottomGap);
            headerPinnedFadeDrawable.draw(canvas);
            canvas.restore();
        }
        if (end > searchTop) {
            final float scale = (end - searchTop) / (fadeHeight - bottomGap);
            canvas.save();
            canvas.clipRect(0, searchTop, headerFadeView.getWidth(), end);
            canvas.translate(0, searchTop - bottomGap * scale);
            canvas.scale(1, scale);
            headerPinnedFadeDrawable.draw(canvas);
            canvas.restore();
        }
    }

    private void captureHeaderBackdrop(Canvas canvas, RectF position) {
        if (sheetBackdropBitmap == null || sheetBackdropBitmap.getBitmap() == null
                || sheetSourceOffsetX == Integer.MIN_VALUE || sheetSourceOffsetY == Integer.MIN_VALUE) {
            return;
        }
        // Share the Alert's backdrop coordinates, then add avatars. The white fallback alone made the
        // collapsed header solid; the bottom controls keep their existing avatar-only capture.
        canvas.save();
        canvas.clipRect(0, Math.max(0, sheetBackgroundTop - dp(48)), containerView.getWidth(), getHeaderFadeBottom() + dp(48));
        canvas.translate(-sheetSourceOffsetX, -sheetSourceOffsetY);
        sheetBackdropBitmap.draw(canvas, position.left + sheetSourceOffsetX, position.top + sheetSourceOffsetY,
                position.right + sheetSourceOffsetX, position.bottom + sheetSourceOffsetY);
        canvas.restore();
    }

    private void updateSheetGeometry() {
        if (containerView == null || containerView.getMeasuredWidth() <= 0 || containerView.getMeasuredHeight() <= 0) return;
        final float extent = Math.max(1, dp(13));
        final float progress = isFullscreen ? 1f : Math.max(0f, Math.min(1f, 1f - scrollOffsetY / extent));
        // Extend only after the sheet reaches the status-bar area, continuously with the list offset.
        final float naturalTop = scrollOffsetY + systemInsets.top - extent;
        final float surfaceTop = isFullscreen ? 0 : naturalTop - (systemInsets.top - extent) * progress;
        final float newTop = surfaceTop + currentPanTranslationY;
        final boolean changed = sheetBackgroundTop != newTop || sheetPinnedToTopProgress != progress;
        sheetPinnedToTopProgress = progress;
        sheetBackgroundTop = newTop;
        if (sheetGlassBackground != null) {
            final float radius = dp(25) * (1f - progress);
            if (sheetGlassRadius != radius) {
                sheetGlassRadius = radius;
                sheetGlassBackground.setRadius(radius, radius, 0, 0);
            }
            sheetGlassBackground.setBounds(backgroundPaddingLeft, Math.round(newTop),
                    containerView.getMeasuredWidth() - backgroundPaddingLeft,
                    Math.round(containerView.getMeasuredHeight() + dp(60) + backgroundPaddingTop + currentPanTranslationY));
        }
        if (changed) {
            headerGlassNeedsCapture = true;
            if (frameLayout != null) frameLayout.invalidate();
            if (headerFadeView != null) headerFadeView.invalidate();
        }
        updateHeaderFade();
    }

    private void updateHeaderFade() {
        if (headerFadeView != null && frameLayout != null) {
            headerFadeView.setTranslationY(-containerView.getPaddingTop() * sheetPinnedToTopProgress);
            final float fadeBottom = getHeaderFadeBottom();
            headerFadeDrawable.setBounds(0, Math.round(Math.max(0, sheetBackgroundTop - headerFadeView.getY())),
                    headerFadeView.getMeasuredWidth(), Math.max(0, Math.round(fadeBottom - headerFadeView.getY())));
            final float radius = dp(25) * (1f - sheetPinnedToTopProgress);
            if (headerSurfaceGlassRadius != radius) {
                headerSurfaceGlassRadius = radius;
                headerSurfaceGlass.setRadius(radius, radius, 0, 0);
            }
        }
    }

    private void invalidateSheetGlass() {
        if (sheetBackdropSource != null) sheetBackdropSource.invalidateDisplayListForDrawables();
        if (sheetBackdropFrostedSource != null) sheetBackdropFrostedSource.invalidateDisplayListForDrawables();
        if (headerFadeView != null) headerFadeView.invalidate();
        containerView.postInvalidateOnAnimation();
    }

    private void primeSheetGlass() {
        if (shareClosed || !GlassEngine.getCurrentGlassSettings().isBlurEnabled || sheetBackdropCapture == null
                || sheetBackdropView.getWidth() <= 0 || sheetBackdropView.getHeight() <= 0
                || containerView.getWidth() <= 0 || containerView.getHeight() <= 0) {
            return;
        }
        final int width = sheetBackdropView.getWidth(), height = sheetBackdropView.getHeight();
        updateSheetGeometry();
        if (!sheetBackdropInvalidated && sheetBackdropWidth == width && sheetBackdropHeight == height) {
            return;
        }
        // Keep the capture in the chat's coordinates. Moving the sheet only changes its source offset.
        GlassPositionsArray positions = new GlassPositionsArray();
        positions.add(0, 0, width, height);
        sheetBackdropColor.setColor(getThemedColor(Theme.key_dialogBackground));
        sheetBackdropCapture.setPositions(positions);
        sheetBackdropCapture.invalidate();
        sheetBackdropWidth = width;
        sheetBackdropHeight = height;
        sheetBackdropInvalidated = false;
        invalidateSheetGlass();
        headerGlassNeedsCapture = true;
    }

    private void setBackdropMaterial(Bitmap material, int width, int height) {
        final Bitmap immutableMaterial = material.copy(Bitmap.Config.ARGB_8888, false);
        if (immutableMaterial != null) {
            material.recycle();
            material = immutableMaterial;
        }
        sheetBackdropBitmap.setBitmap(material);
        sheetBackdropBitmap.getMatrix().setScale(width / (float) material.getWidth(), height / (float) material.getHeight());
    }

    private void prepareInitialBackdrop() {
        if (shareClosed || !GlassEngine.getCurrentGlassSettings().isBlurEnabled) return;
        final int width = sheetBackdropView.getWidth(), height = sheetBackdropView.getHeight();
        if (width <= 0 || height <= 0) return;
        final int[] location = new int[2];
        sheetBackdropView.getLocationInWindow(location);
        Rect source = new Rect(location[0], location[1], location[0] + width, location[1] + height);
        final Bitmap material = Bitmap.createBitmap(Math.max(1, (width + 3) / 4),
                Math.max(1, (height + 3) / 4), Bitmap.Config.ARGB_8888);
        final int themeGeneration = backdropThemeGeneration;
        try {
            // Queue the copy before the sheet is attached, while the window still shows only the chat.
            PixelCopy.request(parentActivity.getWindow(), source, material, result -> {
                if (shareClosed) {
                    material.recycle();
                    return;
                }
                if (result == PixelCopy.SUCCESS && themeGeneration == backdropThemeGeneration
                        && width == sheetBackdropView.getWidth() && height == sheetBackdropView.getHeight()) {
                    setBackdropMaterial(material, width, height);
                } else {
                    material.recycle();
                }
                sheetBackdropInvalidated = true;
                primeSheetGlass();
                if (sheetBackdropInvalidated) {
                    invalidateSheetGlass();
                    headerGlassNeedsCapture = true;
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (IllegalArgumentException e) {
            material.recycle();
        }
    }

    private void primeHeaderGlass() {
        if (!headerGlassNeedsCapture || scrollableViewNoiseSuppressor == null || iBlur3Capture == null
                || !GlassEngine.getCurrentGlassSettings().isBlurEnabled) {
            return;
        }
        captureHeaderGlass();
    }

    private void captureDialogGrid(Canvas canvas, RectF position, RecyclerListView list) {
        if (folderTargetTabId < 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || list != gridView && list != folderGridView) {
            Blur3Utils.captureRelativeParent(list, canvas, position, list, containerView);
            return;
        }
        RenderNode node = folderCaptureNodes.get(list);
        final int outset = systemInsets.top + dp(48);
        if (node == null || !node.hasDisplayList() || node.getWidth() != list.getWidth()
                || node.getHeight() != list.getHeight() + outset) {
            node = new RenderNode("share-folder-capture");
            node.setPosition(0, -outset, list.getWidth(), list.getHeight());
            Canvas recording = node.beginRecording(list.getWidth(), list.getHeight() + outset);
            recording.translate(0, outset);
            folderCaptureBounds.set(0, -outset, list.getWidth(), list.getHeight());
            list.capture(recording, folderCaptureBounds);
            node.endRecording();
            folderCaptureNodes.put(list, node);
        }
        // Reuse references to the live child RenderNodes instead of traversing both grids
        // again for the header and footer on every horizontal animation frame.
        final int save = canvas.save();
        canvas.translate(list.getX(), list.getY());
        canvas.drawRenderNode(node);
        canvas.restoreToCount(save);
    }

    private void captureHeaderGlass() {
        updateSheetGeometry();
        final long frame = headerCaptureFrame;
        if (frame == lastHeaderCaptureFrame && !headerGlassNeedsCapture) return;
        headerGlassNeedsCapture = false;
        lastHeaderCaptureFrame = frame;
        scrollableViewNoiseSuppressor.setupRenderNodes(getHeaderCapturePositions());
        scrollableViewNoiseSuppressor.invalidateResultRenderNodes(iBlur3Capture, containerView.getWidth(), containerView.getHeight());
    }

    private GlassPositionsArray getHeaderCapturePositions() {
        headerCapturePositions.set(glassEngine.getAllVisibleDrawablesWithDisplayListPositionsMerged());
        final float outset = dp(48);
        final float top = Math.max(0, Math.min(sheetBackgroundTop, frameLayout.getY()) - outset);
        final float bottom = Math.min(containerView.getHeight(), getHeaderFadeBottom() + outset);
        // The fade is not a capsule registered with the glass engine; include its full width on every capture.
        headerCapturePositions.add(0, top, containerView.getWidth(), bottom);
        float bottomTop = containerView.getHeight();
        if (pickerBottom != null && pickerBottom.getVisibility() == View.VISIBLE && pickerBottom.getAlpha() > 0) {
            bottomTop = Math.min(bottomTop, pickerBottom.getY() - outset);
        }
        if (frameLayout2 != null && frameLayout2.getVisibility() == View.VISIBLE && frameLayout2.getAlpha() > 0) {
            bottomTop = Math.min(bottomTop, frameLayout2.getY() - outset);
        }
        if (timestampFrameLayout != null && timestampFrameLayout.getVisibility() == View.VISIBLE && timestampFrameLayout.getAlpha() > 0) {
            bottomTop = Math.min(bottomTop, timestampFrameLayout.getY() - outset);
        }
        for (int i = 0; i < containerView.getChildCount(); i++) {
            final View child = containerView.getChildAt(i);
            if (child instanceof EmojiView && child.getVisibility() == View.VISIBLE && child.getAlpha() > 0) {
                bottomTop = Math.min(bottomTop, child.getY() - outset);
            }
        }
        bottomTop = Math.max(0, bottomTop);
        if (bottomTop < containerView.getHeight()) {
            headerCapturePositions.add(0, bottomTop, containerView.getWidth(), containerView.getHeight());
        }
        headerCaptureMerger.merge(headerCapturePositions, mergedHeaderCapturePositions);
        return mergedHeaderCapturePositions;
    }

    private void updateSheetGlassPosition() {
        if (sheetGlassBackground == null || !GlassEngine.getCurrentGlassSettings().isBlurEnabled
                || containerView.getWidth() <= 0 || containerView.getHeight() <= 0) {
            return;
        }
        primeSheetGlass();
        sheetBackdropView.getLocationOnScreen(backdropPosition);
        containerView.getLocationOnScreen(sheetPosition);
        final int dx = sheetPosition[0] - backdropPosition[0];
        final int dy = sheetPosition[1] - backdropPosition[1];
        if (dx != sheetSourceOffsetX || dy != sheetSourceOffsetY) {
            // The opening animation moves the root itself, outside the glass engine's relative positions.
            sheetSourceOffsetX = dx;
            sheetSourceOffsetY = dy;
            sheetGlassBackground.setSourceOffset(dx, dy);
            containerView.invalidate();
            glassEngine.invalidate();
            if (frameLayout != null) frameLayout.invalidate();
            if (headerFadeView != null) headerFadeView.invalidate();
        }
    }

    private void setupSheetGlass() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || parentActivity == null) return;
        sheetBackdropView = parentFragment != null && parentFragment.getFragmentView() != null
                ? parentFragment.getFragmentView() : parentActivity.getWindow().getDecorView();
        sheetBackdropColor = new GlassSourceColor();
        sheetBackdropColor.setColor(getThemedColor(Theme.key_dialogBackground));
        sheetBackdropBitmap = new GlassSourceBitmap();
        sheetBackdropCapture = new GlassCaptureSourceCanvas();
        final IBlur3Capture capture = (canvas, position) -> {
            if (sheetBackdropBitmap.getBitmap() != null) {
                sheetBackdropBitmap.draw(canvas, position.left, position.top, position.right, position.bottom);
            }
        };
        sheetBackdropCapture.setCapture(capture);
        sheetBackdropCapture.setRootView(sheetBackdropView);
        sheetBackdropCapture.setAllowDownsample(true);
        sheetBackdropSource = new GlassSourceRenderNode(sheetBackdropColor);
        sheetBackdropSource.setUnderSource(sheetBackdropColor);
        sheetBackdropSource.setGlassCaptureSourceCanvas(sheetBackdropCapture, GlassCaptureSourceCanvas.SourceIndex.GLASS);
        sheetBackdropFrostedSource = new GlassSourceRenderNode(sheetBackdropColor);
        sheetBackdropFrostedSource.setUnderSource(sheetBackdropColor);
        sheetBackdropFrostedSource.setGlassCaptureSourceCanvas(sheetBackdropCapture, GlassCaptureSourceCanvas.SourceIndex.FROSTED_GLASS);
        BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(
                sheetBackdropSource, sheetBackdropFrostedSource, sheetBackdropColor);
        factory.setLiquidGlassEffectAllowed(true);
        factory.setOutset(dp(24));
        final BlurredBackgroundProvider sheetColors = BlurredBackgroundProviderImpl.inputFieldShareAlert(resourcesProvider);
        // Its source is fixed to the backdrop, so the root-relative glass engine must not reset its offset.
        sheetGlassBackground = factory.create(containerView,
                new BlurredBackgroundProviderBuilder(resourcesProvider)
                        .setBackgroundColor((r, isDark) -> {
                            return GlassEngine.getCurrentGlassSettings().isBlurEnabled
                                    ? sheetColors.getBackgroundColor() : Theme.getColor(Theme.key_dialogBackground, r);
                        })
                        .setStrokeColorTop(0x60FFFFFF, 0x40FFFFFF)
                        .setStrokeColorBottom(0x1A000000, 0x10FFFFFF)
                        .setShadowColor(0x30000000, 0x50000000)
                        .setShadowLayer(dp(12), 0, dp(3))
                        .setStrokeWidth(dpf2(0.6f), dpf2(0.4f))
                        .build());
        sheetGlassBackground.setThickness(dp(24));
        sheetGlassBackground.setIntensity(0.5f);
        sheetGlassBackground.setHasPadding(false);
        prepareInitialBackdrop();
    }

    private final @Nullable DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
    private final @Nullable GlassSourceRenderNode iBlur3SourceGlassFrosted;
    private final @Nullable GlassSourceRenderNode iBlur3SourceGlass;
    private final @NonNull GlassSourceColor iBlur3SourceColor;
    private final @NonNull BlurredBackgroundDrawableViewFactory iBlur3FactoryLiquidGlass;
    private final @NonNull BlurredBackgroundDrawableViewFactory iBlur3FactoryFrostedLiquidGlass;
    private final @NonNull BlurredBackgroundDrawableViewFactory iBlur3FactoryFade;

    private IBlur3Capture iBlur3Capture;
    private BlurredBackgroundDrawable emojiViewChildBg;
    private BlurredBackgroundWithFadeDrawable fadeDrawable;
    private BlurredBackgroundDrawable captionContainerBg;

    private void invalidateBlurCaptures(int flags) {
        updateSearchFieldBackground();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null
                || !GlassEngine.getCurrentGlassSettings().isBlurEnabled
                || frameLayout == null || containerView.getWidth() <= 0 || containerView.getHeight() <= 0) {
            return;
        }

        captureHeaderGlass();
    }

    @Override
    public void onThemeColorsChanged() {
        try {
            super.onThemeColorsChanged();
            folderCaptureNodes.clear();
            if (sheetGlassBackground != null) sheetGlassBackground.updateColors();
            if (headerFadeDrawable != null) headerFadeDrawable.updateColors();
            if (headerPinnedFadeDrawable != null) headerPinnedFadeDrawable.updateColors();
            sheetBackdropInvalidated = true;
            backdropThemeGeneration++;
            headerGlassNeedsCapture = true;
            updateSearchFieldBackground();
            if (iBlur3SourceColor != null) {
                iBlur3SourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
            }
            if (container != null) {
                final boolean lightNavigationBar = AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_dialogBackground)) > .721f;
                if (lightNavigationBar != themedLightNavigationBar) {
                    themedLightNavigationBar = lightNavigationBar;
                    AndroidUtilities.setLightNavigationBar(container, lightNavigationBar);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private boolean themedLightNavigationBar;

    /** {@code layout.setupRadialSelectors(color(key))}, following theme changes */
    private static void setupThemedRadialSelectors(ActionBarPopupWindow.ActionBarPopupWindowLayout layout, int key, Theme.ResourcesProvider resourcesProvider) {
        final int color = Theme.getColor(key, resourcesProvider);
        final int count = layout.getItemsCount();
        for (int a = 0; a < count; a++) {
            final View child = layout.getItemAt(a);
            ThemeBind.selectorBackground(child, Theme.createRadSelectorDrawable(color, a == 0 ? 6 : 0, a == count - 1 ? 6 : 0), null, ThemeBind.of(key), resourcesProvider);
        }
    }
}
