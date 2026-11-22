package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Components.VideoPlayer;

import java.util.ArrayList;

public class ChooseDownloadQualityLayout {

    public final ActionBarPopupWindow.ActionBarPopupWindowLayout layout;
    public final LinearLayout buttonsLayout;
    private final Callback callback;
    private final PhotoCallback photoCallback;
    private final Runnable saveAllPhotos;

    public ChooseDownloadQualityLayout(Context context, PopupSwipeBackLayout swipeBackLayout, Callback callback) {
        this(context, swipeBackLayout, callback, null, null);
    }

    public ChooseDownloadQualityLayout(Context context, PopupSwipeBackLayout swipeBackLayout, PhotoCallback callback, Runnable saveAllPhotos) {
        this(context, swipeBackLayout, null, callback, saveAllPhotos);
    }

    public ChooseDownloadQualityLayout(Context context, PopupSwipeBackLayout swipeBackLayout, Callback callback, PhotoCallback photoCallback, Runnable saveAllPhotos) {
        this.callback = callback;
        this.photoCallback = photoCallback;
        this.saveAllPhotos = saveAllPhotos;
        layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, null);
        layout.setFitItems(true);

        ActionBarMenuSubItem backItem = ActionBarMenuItem.addItem(layout, R.drawable.msg_arrow_back, getString(R.string.Back), false, null);
        backItem.setOnClickListener(view -> {
            swipeBackLayout.closeForeground();
        });
        backItem.setColors(0xfffafafa, 0xfffafafa);
        backItem.setSelectorColor(0x0fffffff);

        FrameLayout gap = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        gap.setMinimumWidth(dp(196));
        gap.setBackgroundColor(0xff181818);
        layout.addView(gap);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) gap.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = dp(8);
        gap.setLayoutParams(layoutParams);

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(buttonsLayout);
    }

    public boolean update(MessageObject messageObject) {
        return update(messageObject, null);
    }

    public boolean update(MessageObject messageObject, TLRPC.Document current) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.messageOwner.media == null) return false;
        if (!messageObject.isVideo() && !messageObject.isGif()) return false;

        TLRPC.MessageMedia media = MessageObject.getMedia(messageObject.messageOwner);
        if (media == null) return false;
        ArrayList<VideoPlayer.Quality> qualities = messageObject.isGif() ? getGifQualities(messageObject)
                : VideoPlayer.getQualities(messageObject.currentAccount, messageObject.getDownloadDocument(), media.alt_documents, 0, false);

        buttonsLayout.removeAllViews();
        for (int i = 0; i < qualities.size(); ++i) {
            final VideoPlayer.Quality q = qualities.get(i);
            VideoPlayer.VideoUri selected = q.getDownloadUri();
            for (VideoPlayer.VideoUri candidate : q.uris) {
                if (sameDocument(candidate.document, current)) { selected = candidate; break; }
            }
            final VideoPlayer.VideoUri uri = selected;
            if (uri == null) continue;
            String title = q.original && messageObject.isGif() ? getString(R.string.QualityOriginal) : q.toString();
            if (sameDocument(uri.document, current)) title += " · " + getString(R.string.Current);
            SpannableStringBuilder subtitle = new SpannableStringBuilder();
            if (uri.isCached()) {
                subtitle.append(AndroidUtilities.formatFileSize(uri.document.size));
                subtitle.append(LocaleController.getString(R.string.QualityCached));
            } else {
                final SpannableString s = new SpannableString("s ");
                final ColoredImageSpan span = new ColoredImageSpan(R.drawable.msg_mini_arrow_mediabold);
                span.rotate(90.0f);
                span.translate(0, dp(1));
                span.spaceScaleX = .85f;
                s.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                subtitle.append(s);
                subtitle.append(AndroidUtilities.formatFileSize(uri.document.size));
            }
            ActionBarMenuSubItem item = ActionBarMenuItem.addItem(buttonsLayout, 0, title, false, null);
            item.setSubtext(subtitle);
            item.setColors(0xfffafafa, 0xfffafafa);
            item.subtextView.setPadding(0, 0, 0, 0);
            item.setOnClickListener((view) -> {
                // Freeze the concrete codec/file even if another version completes later.
                callback.onQualitySelected(messageObject, new VideoPlayer.Quality(uri));
            });
            item.setSelectorColor(0x0fffffff);
        }
        return buttonsLayout.getChildCount() > 0;
    }

    public static ArrayList<VideoPlayer.Quality> getGifQualities(MessageObject messageObject) {
        TLRPC.Document original = messageObject.getDocumentFast();
        TLRPC.MessageMedia media = MessageObject.getMedia(messageObject.messageOwner);
        int reference = FileLoader.getInstance(messageObject.currentAccount).getFileReference(messageObject);
        ArrayList<VideoPlayer.Quality> qualities = VideoPlayer.getQualities(messageObject.currentAccount, original,
                media == null ? null : media.alt_documents, reference, false);
        if (original != null) {
            boolean hasOriginal = false;
            for (VideoPlayer.Quality quality : qualities) {
                hasOriginal |= quality.original;
            }
            if (!hasOriginal) {
                try {
                    VideoPlayer.VideoUri uri = VideoPlayer.VideoUri.of(messageObject.currentAccount, original, null, reference, false);
                    if (uri.width <= 0 || uri.height <= 0) {
                        uri.original = true;
                        for (TLRPC.DocumentAttribute attribute : original.attributes) {
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                                uri.width = attribute.w;
                                uri.height = attribute.h;
                                break;
                            }
                        }
                        qualities.add(0, new VideoPlayer.Quality(uri));
                    }
                } catch (Exception exception) {
                    FileLog.e(exception);
                }
            }
        }
        return qualities;
    }

    public boolean updatePhoto(MessageObject messageObject, TLRPC.Photo photo, ArrayList<TLRPC.PhotoSize> sizes, TLRPC.PhotoSize current, String allItemsPluralKey, int photoCount) {
        if (messageObject == null || photo == null || sizes == null || sizes.isEmpty() || photoCallback == null) return false;
        buttonsLayout.removeAllViews();
        for (int i = sizes.size() - 1; i >= 0; i--) {
            TLRPC.PhotoSize size = sizes.get(i);
            SpannableStringBuilder title = new SpannableStringBuilder();
            title.append(Integer.toString(size.w)).append(" x ").append(Integer.toString(size.h));
            SpannableStringBuilder subtitle = new SpannableStringBuilder(AndroidUtilities.formatFileSize(size.size));
            if (size == current) {
                subtitle.append(" · ").append(getString(R.string.Current));
            }
            ActionBarMenuSubItem item = ActionBarMenuItem.addItem(buttonsLayout, 0, title, false, null);
            item.setSubtext(subtitle);
            item.setColors(0xfffafafa, 0xfffafafa);
            item.subtextView.setPadding(0, 0, 0, 0);
            item.setOnClickListener(view -> photoCallback.onQualitySelected(messageObject, photo, size));
            item.setSelectorColor(0x0fffffff);
        }
        if (!TextUtils.isEmpty(allItemsPluralKey) && saveAllPhotos != null) {
            FrameLayout gap = new FrameLayout(buttonsLayout.getContext());
            gap.setBackgroundColor(0xff181818);
            buttonsLayout.addView(gap, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, dp(8)));
            ActionBarMenuSubItem item = ActionBarMenuItem.addItem(buttonsLayout, R.drawable.msg_gallery,
                    LocaleController.formatPluralString(allItemsPluralKey, photoCount), false, null);
            item.setColors(0xfffafafa, 0xfffafafa);
            item.setSelectorColor(0x0fffffff);
            item.setOnClickListener(view -> saveAllPhotos.run());
        }
        return buttonsLayout.getChildCount() > 0;
    }

    private static boolean sameDocument(TLRPC.Document first, TLRPC.Document second) {
        return first != null && second != null && first.id == second.id && first.dc_id == second.dc_id;
    }

    public interface Callback {
        void onQualitySelected(MessageObject messageObject, VideoPlayer.Quality quality);
    }

    public interface PhotoCallback {
        void onQualitySelected(MessageObject messageObject, TLRPC.Photo photo, TLRPC.PhotoSize size);
    }

}
