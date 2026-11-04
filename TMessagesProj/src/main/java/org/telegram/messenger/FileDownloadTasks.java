package org.telegram.messenger;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Account-local, file-local downloads. UI windows are observers, not task owners. */
public final class FileDownloadTasks implements NotificationCenter.NotificationCenterDelegate {
    private static final FileDownloadTasks[] instances = new FileDownloadTasks[UserConfig.MAX_ACCOUNT_COUNT];
    private static final AtomicInteger nextConsumer = new AtomicInteger();
    private static final AtomicInteger nextNotification = new AtomicInteger(20000);
    private static final String TAG = "file_downloads";
    private static final String NOTIFICATION_TAG = TAG + "_" + UUID.randomUUID();
    private final int account;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Set<String> blockedFiles = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> cancelledFiles = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean updatingSpeed;
    private final Runnable updateSpeed = this::updateDownloadSpeed;

    public static void clearStaleNotifications() {
        NotificationManager manager = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        try {
            for (StatusBarNotification notification : manager.getActiveNotifications()) {
                String tag = notification.getTag();
                // Saved-file notifications use negative IDs and remain useful after a restart.
                if (notification.getId() >= 19900 && isTaskTag(tag) && !NOTIFICATION_TAG.equals(tag)) {
                    manager.cancel(tag, notification.getId());
                }
            }
        } catch (RuntimeException e) {
            FileLog.e(e);
        }
    }

    private static boolean isTaskTag(String tag) {
        return tag != null && (TAG.equals(tag) || tag.startsWith(TAG + "_"));
    }

    private synchronized void updateDownloadSpeed() {
        updatingSpeed = false;
        Map<String, Long> speeds = new HashMap<>();
        for (Entry entry : new ArrayList<>(entries.values())) {
            if (!entry.notified || entry.state.complete || entry.state.blocked() || entry.failed) continue;
            long speed = downloadSpeed(speeds, entry.fileName);
            entry.speed = speed <= 0 ? 0 : entry.speed <= 0 ? speed : (entry.speed * 3 + speed) / 4;
            publish(entry, false);
        }
    }

    private long downloadSpeed(Map<String, Long> speeds, String fileName) {
        return speeds.computeIfAbsent(fileName,
                name -> Math.max(0, FileLoader.getInstance(account).getDownloadSpeed(name)));
    }

    private static final class State {
        enum Pause { NONE, BACKGROUND, USER }

        volatile int consumers;
        volatile boolean playback;
        volatile boolean save;
        volatile boolean share;
        volatile boolean external;
        volatile boolean keepInBackground;
        volatile boolean independentDownload;
        volatile boolean complete;
        volatile boolean cancelled;
        volatile Pause pause = Pause.NONE;

        synchronized void startPlayback() {
            playback = true;
            cancelled = false;
            if (pause == Pause.BACKGROUND) pause = Pause.NONE;
        }

        synchronized void addExport(boolean saving) {
            if (saving) save = true; else share = true;
            cancelled = false;
            // Starting an explicit action resumes a file the user previously paused.
            pause = Pause.NONE;
        }

        synchronized void startExternal() {
            external = true;
            cancelled = false;
            pause = Pause.NONE;
        }

        synchronized void pause(boolean background) {
            if (cancelled || complete || background && hasIndependentWork()) return;
            if (!background || pause != Pause.USER) {
                pause = background ? Pause.BACKGROUND : Pause.USER;
            }
        }

        synchronized boolean resume(boolean background) {
            if (cancelled || background && pause != Pause.BACKGROUND) return false;
            if (!background && playback) keepInBackground = true;
            pause = Pause.NONE;
            return true;
        }

        synchronized boolean releasePlayback() {
            playback = false;
            // Closing the viewer must release its automatic pause on independent work.
            if (pause == Pause.BACKGROUND) pause = Pause.NONE;
            return !hasIndependentWork();
        }

        synchronized boolean hasIndependentWork() {
            return consumers > 0 || save || share || external || keepInBackground || independentDownload;
        }

        synchronized boolean hasWork() {
            return playback || hasIndependentWork();
        }

        synchronized boolean keepsTransfer() {
            return playback || hasIndependentWork() && !blocked();
        }

        synchronized boolean blocked() {
            return cancelled || pause != Pause.NONE;
        }

        synchronized void cancel() {
            cancelled = true;
            consumers = 0;
            playback = save = share = external = keepInBackground = independentDownload = false;
            pause = Pause.NONE;
        }
    }

    private static final class Export {
        final AtomicBoolean cancelled = new AtomicBoolean();
        boolean started;
    }

    private static final class Entry {
        final FileDownloadSource source;
        final TLRPC.Document document;
        final MessageObject message;
        final ConcurrentHashMap<Integer, Consumer> consumers = new ConcurrentHashMap<>();
        final String fileName;
        final int notification = nextNotification.incrementAndGet();
        final long notificationTime = System.currentTimeMillis();
        final State state = new State();
        final EnumMap<FileExport.Target, Export> saves = new EnumMap<>(FileExport.Target.class);
        Export share;
        File file;
        float progress;
        long downloadedBytes;
        long totalBytes;
        long lastNotification;
        long speed;
        boolean failed;
        boolean viewerBackground;
        boolean viewerPlayback;
        final Set<Object> playbackOwners = Collections.newSetFromMap(new ConcurrentHashMap<>());
        boolean notified;
        final Set<Object> externalOwners = Collections.newSetFromMap(new ConcurrentHashMap<>());

        Entry(FileDownloadSource source) {
            this.source = source;
            document = source.document;
            message = source.message;
            fileName = source.fileName;
            totalBytes = document != null ? document.size : source.photoSize != null ? source.photoSize.size : 0;
        }
    }

    public interface ReadyCallback {
        void onReady(File file);
        void onCancelled();
    }

    /** Cancellation belongs to a handoff even if the user later restarts this file. */
    public static final class ExternalOwner {
        public volatile boolean cancelled;
        private final Runnable onCancel;

        public ExternalOwner(Runnable onCancel) {
            this.onCancel = onCancel;
        }
    }

    private static final class Consumer {
        final int id = nextConsumer.incrementAndGet();
        final String purpose;
        final ReadyCallback callback;
        boolean delivered;
        Consumer(String purpose, ReadyCallback callback) { this.purpose = purpose; this.callback = callback; }
    }

    public synchronized void export(FileDownloadSource source, FileExport.Target target) {
        if (target == null) throw new IllegalArgumentException("Missing export target");
        startExport(source, target);
    }

    /** Holds a file for a compound export until the caller releases its registration. */
    public synchronized int whenReady(FileDownloadSource source, String purpose, ReadyCallback callback) {
        Entry entry = entry(source);
        Consumer consumer = new Consumer(purpose, callback);
        entry.consumers.put(consumer.id, consumer);
        entry.state.consumers = entry.consumers.size();
        entry.state.pause = State.Pause.NONE;
        entry.state.cancelled = false;
        blockedFiles.remove(entry.fileName);
        cancelledFiles.remove(entry.fileName);
        if (entry.state.complete) notifyConsumers(entry); else load(entry);
        publish(entry, true);
        return consumer.id;
    }

    public synchronized void release(FileDownloadSource source, int registration) {
        Entry entry = entries.get(source.fileName);
        if (entry == null) return;
        entry.consumers.remove(registration);
        entry.state.consumers = entry.consumers.size();
        finishCancellation(entry);
    }

    private synchronized void notifyConsumers(Entry entry) {
        for (Consumer consumer : new ArrayList<>(entry.consumers.values())) {
            if (!consumer.delivered) {
                consumer.delivered = true;
                AndroidUtilities.runOnUIThread(() -> {
                    if (entries.get(entry.fileName) == entry && entry.consumers.get(consumer.id) == consumer) {
                        consumer.callback.onReady(entry.file);
                    }
                });
            }
        }
    }

    public static synchronized FileDownloadTasks getInstance(int account) {
        if (instances[account] == null) instances[account] = new FileDownloadTasks(account);
        return instances[account];
    }

    public static boolean hasTask(int account, String fileName) {
        FileDownloadTasks tasks = instances[account];
        return tasks != null && fileName != null && tasks.entries.containsKey(fileName);
    }

    public synchronized void cancel(String fileName) {
        Entry entry = entries.get(fileName);
        if (entry != null) cancel(entry);
    }

    public static boolean isBlocked(int account, TLRPC.Document document) {
        FileDownloadTasks tasks = instances[account];
        return tasks != null && document != null && tasks.blockedFiles.contains(FileLoader.getAttachFileName(document));
    }

    public static boolean isBlocked(int account, String name) {
        FileDownloadTasks tasks = instances[account];
        return tasks != null && name != null && tasks.blockedFiles.contains(name);
    }

    public synchronized void beginIndependent(MessageObject message, TLRPC.Document document) {
        Entry entry = entry(message, document);
        entry.state.independentDownload = true;
        entry.state.cancelled = false;
        entry.state.pause = State.Pause.NONE;
        blockedFiles.remove(entry.fileName);
        cancelledFiles.remove(entry.fileName);
        publish(entry, true);
    }

    public synchronized void clear() {
        for (Entry entry : new ArrayList<>(entries.values())) cancel(entry);
        blockedFiles.clear();
        cancelledFiles.clear();
    }

    public static boolean keepsFile(int account, TLRPC.Document document) {
        FileDownloadTasks tasks = instances[account];
        if (tasks == null || document == null) return false;
        Entry entry = tasks.entries.get(FileLoader.getAttachFileName(document));
        return entry != null && entry.state.keepsTransfer();
    }

    public static boolean isPlaybackOnly(int account, String fileName) {
        FileDownloadTasks tasks = instances[account];
        Entry entry = tasks == null || fileName == null ? null : tasks.entries.get(fileName);
        return entry != null && entry.state.playback && (entry.state.blocked() || !entry.state.hasIndependentWork());
    }

    static void onIndependentDownloadStarted(int account, String fileName) {
        FileDownloadTasks tasks = instances[account];
        if (tasks == null) return;
        synchronized (tasks) {
            Entry entry = tasks.entries.get(fileName);
            if (entry != null && !entry.state.blocked() && !entry.state.independentDownload) {
                entry.state.independentDownload = true;
                tasks.publish(entry, true);
            }
        }
    }

    public static boolean isExternalActive(int account, TLRPC.Document document, Object owner) {
        FileDownloadTasks tasks = instances[account];
        if (tasks == null || document == null) return false;
        Entry entry = tasks.find(document);
        return entry != null && entry.externalOwners.contains(owner);
    }

    private FileDownloadTasks(int account) {
        this.account = account;
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver(this, NotificationCenter.fileLoaded);
        center.addObserver(this, NotificationCenter.fileLoadFailed);
        center.addObserver(this, NotificationCenter.fileLoadProgressChanged);
        center.addObserver(this, NotificationCenter.appDidLogout);
    }

    private synchronized Entry entry(MessageObject message, TLRPC.Document document) {
        return entry(FileDownloadSource.document(message, document));
    }

    private synchronized Entry entry(FileDownloadSource source) {
        String name = source.fileName;
        Entry entry = entries.get(name);
        if (entry == null) {
            entry = new Entry(source);
            entry.state.independentDownload = FileLoader.getInstance(account).isLoadingFileIndependently(name);
            entry.file = source.localFile(account);
            entry.state.complete = entry.file != null;
            if (entry.state.complete) {
                if (entry.totalBytes <= 0) entry.totalBytes = entry.file.length();
                entry.downloadedBytes = entry.totalBytes;
                entry.progress = 1;
            }
            if (cancelledFiles.contains(name)) entry.state.pause = State.Pause.USER;
            entries.put(name, entry);
        }
        return entry;
    }

    public static boolean isCancelled(int account, TLRPC.Document document) {
        FileDownloadTasks tasks = instances[account];
        return tasks != null && document != null && tasks.cancelledFiles.contains(FileLoader.getAttachFileName(document));
    }

    /** A new user-selected playback may reuse previously stopped stream variants. */
    public synchronized void allowPlayback(TLRPC.Document document) {
        if (document == null) return;
        String name = FileLoader.getAttachFileName(document);
        cancelledFiles.remove(name);
        blockedFiles.remove(name);
        Entry entry = entries.get(name);
        if (entry != null) {
            entry.state.cancelled = false;
            // Opening a player resumes its streams, while a paused download stays paused.
            if (entry.state.pause == State.Pause.BACKGROUND || !entry.state.hasIndependentWork()) entry.state.pause = State.Pause.NONE;
        }
    }

    /** Each playback owner retains its file until that owner releases it. */
    public synchronized boolean acquirePlayback(MessageObject message, TLRPC.Document document, Object owner) {
        if (isCancelled(account, document)) return false;
        Entry previous = find(document);
        if (previous != null && (previous.state.cancelled || previous.state.pause == State.Pause.BACKGROUND)) return false;
        blockedFiles.remove(FileLoader.getAttachFileName(document));
        Entry entry = entry(message, document);
        entry.playbackOwners.add(owner);
        entry.state.playback = true;
        if (entry.viewerPlayback || entry.state.hasIndependentWork()) publish(entry, true);
        return true;
    }

    public synchronized void releasePlayback(TLRPC.Document document, Object owner) {
        Entry entry = find(document);
        if (entry == null || !entry.playbackOwners.remove(owner)) return;
        if (!entry.viewerPlayback && entry.playbackOwners.isEmpty()) releasePlayback(document);
    }

    public synchronized void beginPlayback(MessageObject message, TLRPC.Document document) {
        if (document == null || message == null) return;
        Entry entry = entry(message, document);
        entry.viewerPlayback = true;
        entry.viewerBackground = false;
        entry.state.startPlayback();
        blockedFiles.remove(entry.fileName);
        cancelledFiles.remove(entry.fileName);
        publish(entry, true);
    }

    public synchronized boolean isUserPaused(TLRPC.Document document) {
        Entry entry = find(document);
        return entry != null && entry.state.pause == State.Pause.USER;
    }

    public synchronized boolean isIndependentActive(TLRPC.Document document) {
        Entry entry = find(document);
        return entry != null && entry.state.independentDownload && !entry.state.complete && !entry.state.cancelled
                && entry.state.pause != State.Pause.USER;
    }

    public synchronized void releasePlayback(TLRPC.Document document) {
        Entry entry = find(document);
        if (entry == null) return;
        entry.viewerBackground = false;
        entry.viewerPlayback = false;
        if (!entry.playbackOwners.isEmpty() || !entry.playbackSessions.isEmpty()) {
            if (entry.state.hasIndependentWork()) publish(entry, true); else hide(entry, true);
            return;
        }
        boolean wasPaused = entry.state.pause == State.Pause.BACKGROUND;
        if (entry.state.releasePlayback()) {
            if (!entry.state.complete) stopTransfer(entry);
            remove(entry);
        } else {
            if (wasPaused && !entry.state.complete && !entry.state.blocked()) load(entry);
            else if (entry.state.blocked() && !entry.state.complete) stopTransfer(entry);
            publish(entry, true);
        }
    }

    public synchronized void setViewerBackground(TLRPC.Document document, boolean background) {
        Entry entry = find(document);
        if (entry == null) return;
        entry.viewerBackground = background;
        if (background) {
            entry.state.pause(true);
            if (entry.state.blocked()) stopTransfer(entry);
        } else if (entry.state.resume(true)) {
            load(entry);
        }
        publish(entry, true);
    }

    public synchronized void pause(TLRPC.Document document) { pause(find(document)); }

    public synchronized void pause(String fileName) { pause(entries.get(fileName)); }

    private synchronized void pause(Entry entry) {
        if (entry == null) return;
        entry.state.pause(false);
        if (entry.state.blocked() && !entry.state.playback) stopTransfer(entry);
        publish(entry, true);
    }

    public synchronized void resume(TLRPC.Document document) { resume(find(document)); }

    private synchronized void resume(Entry entry) {
        if (entry == null || !entry.state.resume(false)) return;
        entry.failed = false;
        if (entry.state.complete) { notifyConsumers(entry); finishExports(entry); } else load(entry);
        publish(entry, true);
    }

    public synchronized void cancel(TLRPC.Document document) {
        if (document != null) cancelledFiles.add(FileLoader.getAttachFileName(document));
        Entry entry = find(document);
        if (entry == null) {
            if (document != null) {
                blockedFiles.add(FileLoader.getAttachFileName(document));
                FileLoader.getInstance(account).cancelLoadFile(document);
                changed();
            }
            return;
        }
        cancel(entry);
    }

    public synchronized void cancelPersistent(TLRPC.Document document) {
        Entry entry = find(document);
        cancelPersistent(entry, document);
    }

    private synchronized void cancelPersistent(Entry entry, TLRPC.Document document) {
        if (entry == null) {
            if (document != null) FileLoader.getInstance(account).cancelLoadFile(document);
            changed();
            return;
        }
        ArrayList<Consumer> consumers = new ArrayList<>(entry.consumers.values());
        entry.consumers.clear();
        entry.state.consumers = 0;
        for (Export save : entry.saves.values()) save.cancelled.set(true);
        entry.saves.clear();
        entry.state.save = false;
        if (entry.share != null) entry.share.cancelled.set(true);
        entry.share = null;
        entry.state.share = false;
        cancelExternalOwners(entry);
        entry.state.external = false;
        entry.state.keepInBackground = false;
        entry.state.independentDownload = false;
        entry.state.pause = State.Pause.NONE;
        cancelledFiles.remove(entry.fileName);
        if (entry.state.playback) {
            blockedFiles.remove(entry.fileName);
            publish(entry, true);
        } else {
            if (!entry.state.complete) stopTransfer(entry);
            remove(entry);
        }
        for (Consumer consumer : consumers) AndroidUtilities.runOnUIThread(consumer.callback::onCancelled);
    }

    private synchronized void cancel(Entry entry) {
        cancelledFiles.add(entry.fileName);
        ArrayList<Consumer> consumers = new ArrayList<>(entry.consumers.values());
        entry.consumers.clear();
        entry.state.consumers = 0;
        for (Export save : entry.saves.values()) save.cancelled.set(true);
        entry.saves.clear();
        if (entry.share != null) entry.share.cancelled.set(true);
        entry.share = null;
        entry.viewerPlayback = false;
        entry.playbackOwners.clear();
        cancelExternalOwners(entry);
        entry.state.cancel();
        stopTransfer(entry);
        remove(entry);
        for (Consumer consumer : consumers) AndroidUtilities.runOnUIThread(consumer.callback::onCancelled);
    }

    private void cancelExternalOwners(Entry entry) {
        for (Object owner : entry.externalOwners) {
            if (owner instanceof ExternalOwner) {
                ExternalOwner external = (ExternalOwner) owner;
                external.cancelled = true;
                // Defer until the task lock is released; the session can hold its own lock.
                if (external.onCancel != null) ApplicationLoader.applicationHandler.post(external.onCancel);
            }
        }
        entry.externalOwners.clear();
    }

    private synchronized void cancelExternal(Entry entry) {
        if (entry == null || !entry.state.external) return;
        cancelExternalOwners(entry);
        entry.state.external = false;
        if (!entry.state.hasWork()) remove(entry); else publish(entry, true);
    }

    public synchronized boolean hasExport(TLRPC.Document document, FileExport.Target target) {
        Entry entry = find(document);
        return entry != null && entry.saves.containsKey(target);
    }

    public synchronized void cancelExport(TLRPC.Document document, FileExport.Target target) {
        cancelExport(find(document), target);
    }

    public synchronized void cancelExport(FileDownloadSource source, FileExport.Target target) {
        cancelExport(entries.get(source.fileName), target);
    }

    private synchronized void cancelExport(Entry entry, FileExport.Target target) {
        if (entry == null) return;
        Export export = entry.saves.remove(target);
        if (export != null) export.cancelled.set(true);
        entry.state.save = !entry.saves.isEmpty();
        finishCancellation(entry);
    }

    public synchronized void cancelShare(TLRPC.Document document) {
        Entry entry = find(document);
        if (entry == null) return;
        if (entry.share != null) entry.share.cancelled.set(true);
        entry.share = null;
        entry.state.share = false;
        finishCancellation(entry);
    }

    private synchronized void finishCancellation(Entry entry) {
        if (!entry.state.hasWork()) {
            if (!entry.state.complete) stopTransfer(entry);
            remove(entry);
        } else publish(entry, true);
    }

    public synchronized void export(MessageObject message, TLRPC.Document document, FileExport.Target target) {
        if (target == null) throw new IllegalArgumentException("Missing export target");
        startExport(FileDownloadSource.document(message, document), target);
    }

    public synchronized void share(MessageObject message, TLRPC.Document document) {
        startExport(FileDownloadSource.document(message, document), null);
    }

    private synchronized void startExport(FileDownloadSource source, FileExport.Target target) {
        Entry entry = entry(source);
        if (target == null ? entry.share != null : entry.saves.containsKey(target)) {
            publish(entry, true);
            return;
        }
        Export export = new Export();
        if (target == null) entry.share = export; else entry.saves.put(target, export);
        entry.state.addExport(target != null);
        blockedFiles.remove(entry.fileName);
        cancelledFiles.remove(entry.fileName);
        entry.failed = false;
        if (entry.state.complete) { notifyConsumers(entry); finishExports(entry); } else load(entry);
        publish(entry, true);
    }

    public synchronized void beginExternal(MessageObject message, TLRPC.Document document, Object owner) {
        Entry entry = entry(message, document);
        entry.externalOwners.add(owner);
        entry.state.startExternal();
        blockedFiles.remove(entry.fileName);
        cancelledFiles.remove(entry.fileName);
        if (!entry.state.complete) load(entry);
        publish(entry, true);
    }

    public synchronized void releaseExternal(TLRPC.Document document, Object owner) {
        Entry entry = find(document);
        if (entry == null) return;
        if (!entry.externalOwners.remove(owner) || !entry.externalOwners.isEmpty()) return;
        entry.state.external = false;
        if (entry.viewerBackground && entry.state.playback && !entry.state.keepInBackground) {
            entry.state.pause(true);
            if (entry.state.blocked()) stopTransfer(entry);
        }
        if (!entry.state.hasWork()) {
            if (!entry.state.complete) stopTransfer(entry);
            remove(entry);
        } else publish(entry, true);
    }

    public synchronized File consumeShare(TLRPC.Document document) {
        Entry entry = find(document);
        if (entry == null || !entry.state.complete || entry.share == null || entry.share.cancelled.get()) return null;
        File file = entry.file;
        cancelShare(document);
        return file;
    }

    public ArrayList<TLRPC.Document> exportDocuments(MessageObject message, FileExport.Target target) {
        return requestedDocuments(message, target, false);
    }

    public ArrayList<TLRPC.Document> shareDocuments(MessageObject message) {
        return requestedDocuments(message, null, true);
    }

    private ArrayList<TLRPC.Document> requestedDocuments(MessageObject message, FileExport.Target target, boolean sharing) {
        ArrayList<TLRPC.Document> result = new ArrayList<>();
        if (message == null) return result;
        for (Entry entry : entries.values()) {
            if (entry.message.getDialogId() == message.getDialogId() && entry.message.getId() == message.getId()
                    && entry.document != null && (sharing ? entry.share != null : entry.saves.containsKey(target))) result.add(entry.document);
        }
        return result;
    }

    private static boolean isOriginal(TLRPC.Document document, MessageObject message) {
        TLRPC.Document original = message == null || message.messageOwner == null ? null : message.getDocumentFast();
        return document != null && original != null && document.id == original.id && document.dc_id == original.dc_id;
    }

    public static File localFile(int account, TLRPC.Document document, MessageObject message) {
        if (document == null) return null;
        if (isOriginal(document, message) && message.messageOwner.attachPath != null && !message.messageOwner.attachPath.isEmpty()) {
            File file = new File(message.messageOwner.attachPath);
            if (file.isFile()) return file;
        }
        FileLoader loader = FileLoader.getInstance(account);
        File file = loader.getPathToAttach(document, null, false, true);
        if (file != null && file.isFile()) return file;
        file = loader.getPathToAttach(document, null, true, true);
        return file != null && file.isFile() ? file : null;
    }

    public static Intent shareIntent(File file, String mime) {
        Uri uri = FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.getApplicationId() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_SEND).setType(mime == null ? "application/octet-stream" : mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newRawUri("file", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, LocaleController.getString(R.string.ShareFile));
    }

    private synchronized Entry find(TLRPC.Document document) {
        return document == null ? null : entries.get(FileLoader.getAttachFileName(document));
    }

    private synchronized void load(Entry entry) {
        if (entry.state.complete || entry.state.blocked()) return;
        // Joining an active stream must not reset its seek/download offset to zero.
        FileLoader loader = FileLoader.getInstance(account);
        if (loader.isLoadingFile(entry.fileName) && !loader.isWaitingForStream(entry.fileName)) return;
        blockedFiles.remove(entry.fileName);
        entry.source.load(account);
    }

    private synchronized void stopTransfer(Entry entry) {
        blockedFiles.add(entry.fileName);
        entry.source.cancel(account);
    }

    private synchronized void remove(Entry entry) {
        if (!entries.remove(entry.fileName, entry)) return;
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(NOTIFICATION_TAG, entry.notification);
        if (entries.isEmpty()) {
            AndroidUtilities.cancelRunOnUIThread(updateSpeed);
            updatingSpeed = false;
        }
        updateSummary();
        changed();
    }

    private synchronized void updateSummary() {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (!entry.notified) continue;
            count++;
        }
        NotificationManagerCompat manager = NotificationManagerCompat.from(ApplicationLoader.applicationContext);
        int summary = 19900 + account;
        // Cancelling a group summary can also remove its surviving child notifications.
        if (count == 0) { manager.cancel(NOTIFICATION_TAG, summary); return; }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(LocaleController.getString(R.string.AppName))
                .setContentText(LocaleController.formatPluralString("Files", count)).setGroup(NOTIFICATION_TAG + "_" + account).setGroupSummary(true).setOnlyAlertOnce(true);
        try { manager.notify(NOTIFICATION_TAG, summary, builder.build()); } catch (SecurityException e) { FileLog.e(e); }
    }

    private synchronized void changed() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.onDownloadingFilesChanged));
    }

    private synchronized void finishExports(Entry entry) {
        for (FileExport.Target target : new ArrayList<>(entry.saves.keySet())) {
            Export save = entry.saves.get(target);
            if (save == null || save.started || save.cancelled.get()) continue;
            save.started = true;
            FileExport.save(entry.file, entry.source.displayName, entry.source.mime,
                    target, save.cancelled, success -> {
                synchronized (FileDownloadTasks.this) {
                if (entry.saves.get(target) != save) return;
                entry.failed = !success && !save.cancelled.get();
                if (entry.failed) {
                    save.started = false;
                    publish(entry, true);
                } else {
                    entry.saves.remove(target);
                    entry.state.save = !entry.saves.isEmpty();
                    if (!entry.state.hasWork()) remove(entry); else publish(entry, true);
                    if (success) savedNotification(entry, target);
                }
                }
            });
        }
    }

    private synchronized void savedNotification(Entry entry, FileExport.Target target) {
        NotificationsController.checkOtherNotificationsChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
                .setSmallIcon(R.drawable.msg_gallery).setContentTitle(LocaleController.getString(R.string.FileSaved))
                .setContentText(target.label()).setAutoCancel(true).setOnlyAlertOnce(true);
        // Use a separate ID so a completed save cannot replace a pending share of the same file.
        try { NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(NOTIFICATION_TAG, -entry.notification, builder.build()); }
        catch (SecurityException e) { FileLog.e(e); }
    }

    private synchronized PendingIntent control(Entry entry, String action) {
        Intent intent = new Intent(ApplicationLoader.applicationContext, FileDownloadReceiver.class)
                .setAction(ApplicationLoader.getApplicationId() + ".FILE_DOWNLOAD." + NOTIFICATION_TAG + "." + action)
                .putExtra("account", account).putExtra("file", entry.fileName)
                .putExtra("notification_tag", NOTIFICATION_TAG)
                .putExtra("notification", entry.notification).putExtra("action", action);
        return PendingIntent.getBroadcast(ApplicationLoader.applicationContext, entry.notification,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static String downloadSize(Entry entry) {
        long total = Math.max(0, entry.totalBytes);
        long downloaded = Math.max(0, total > 0 ? Math.min(entry.downloadedBytes, total) : entry.downloadedBytes);
        String text = AndroidUtilities.formatFileSize(downloaded);
        return total > 0 ? text + " / " + AndroidUtilities.formatFileSize(total) : text;
    }

    private static String downloadProgress(Entry entry, boolean paused) {
        long total = Math.max(0, entry.totalBytes);
        long downloaded = Math.max(0, total > 0 ? Math.min(entry.downloadedBytes, total) : entry.downloadedBytes);
        String text = downloadSize(entry);
        if (paused) return text + " · " + LocaleController.getString(R.string.DownloadPaused);
        if (entry.speed <= 0 || total <= 0) return text + " · N/A · -";
        long seconds = (total - downloaded + entry.speed - 1) / entry.speed;
        return text + " · " + AndroidUtilities.formatFileSize(entry.speed) + "/s · "
                + LocaleController.formatString("DownloadTimeLeft", R.string.DownloadTimeLeft,
                LocaleController.formatTTLString((int) Math.min(Integer.MAX_VALUE, seconds)));
    }

    private static String appendPurpose(String purposes, String purpose) {
        return purposes.isEmpty() ? purpose : purposes + " · " + purpose;
    }

    private NotificationCompat.Builder taskNotification(int notification, long when, String text) {
        return new NotificationCompat.Builder(ApplicationLoader.applicationContext, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(LocaleController.getString(R.string.AppName))
                .setContentText(text).setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setGroup(NOTIFICATION_TAG + "_" + account).setSortKey(Integer.toString(notification))
                .setWhen(when).setShowWhen(false).setOnlyAlertOnce(true);
    }

    private synchronized void hide(Entry entry, boolean immediate) {
        if (!entry.notified) return;
        entry.notified = false;
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(NOTIFICATION_TAG, entry.notification);
        updateSummary();
        if (immediate) changed();
    }

    private synchronized void publish(Entry entry, boolean immediate) {
        if (entries.get(entry.fileName) != entry) return;
        if (!entry.state.hasIndependentWork()) {
            hide(entry, immediate);
            return;
        }
        if (!immediate && android.os.SystemClock.elapsedRealtime() - entry.lastNotification < 500) return;
        entry.lastNotification = android.os.SystemClock.elapsedRealtime();
        boolean shareReady = entry.state.complete && entry.share != null;
        boolean externalReady = entry.state.complete && entry.state.external && entry.saves.isEmpty()
                && entry.consumers.isEmpty() && entry.share == null && !entry.failed;
        if (entry.state.complete && entry.saves.isEmpty() && entry.consumers.isEmpty() && !shareReady && !entry.failed) {
            if (!entry.state.playback && !entry.state.external) { remove(entry); return; }
            if (!externalReady) { hide(entry, immediate); return; }
        }
        NotificationsController.checkOtherNotificationsChannel();
        boolean downloading = !entry.state.complete && !entry.state.blocked() && !entry.failed;
        if (downloading && !updatingSpeed) {
            updatingSpeed = true;
            AndroidUtilities.runOnUIThread(updateSpeed, 1000);
        }
        if (!downloading) entry.speed = 0;
        String text = shareReady ? LocaleController.getString(R.string.ReadyToShare)
                : entry.failed ? LocaleController.getString(R.string.DownloadFailed)
                : externalReady ? downloadSize(entry) + " · " + LocaleController.getString(R.string.CacheComplete)
                : entry.state.blocked() ? downloadProgress(entry, true)
                : downloading ? downloadProgress(entry, false) : LocaleController.getString(R.string.SavingContentTitle);
        String purposes = "";
        if (entry.state.playback) purposes = appendPurpose(purposes, LocaleController.getString(R.string.PlaybackCache));
        for (FileExport.Target target : entry.saves.keySet()) purposes = appendPurpose(purposes, target.label());
        if (entry.share != null) purposes = appendPurpose(purposes, LocaleController.getString(R.string.ShareFile));
        if (entry.state.external) purposes = appendPurpose(purposes, LocaleController.getString(R.string.OpenInExternalApp));
        NotificationCompat.Builder builder = taskNotification(entry.notification, entry.notificationTime, text)
                .setOngoing(!shareReady).setAutoCancel(shareReady);
        if (!purposes.isEmpty() && !entry.failed) builder.setSubText(purposes);
        if (!entry.state.complete && !entry.failed) {
            builder.setProgress(100, (int) (entry.progress * 100), !entry.state.blocked() && entry.progress == 0);
        }
        builder.addAction(R.drawable.ic_close_white, LocaleController.getString(R.string.Cancel), control(entry, "cancel"));
        if (shareReady) {
            Intent send = shareIntent(entry.file, entry.source.mime).getParcelableExtra(Intent.EXTRA_INTENT);
            Intent chooser = Intent.createChooser(send, LocaleController.getString(R.string.ShareFile), control(entry, "shared").getIntentSender())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            builder.setDeleteIntent(control(entry, "cancel"));
            builder.setContentIntent(PendingIntent.getActivity(ApplicationLoader.applicationContext, entry.notification, chooser,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        try { NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(NOTIFICATION_TAG, entry.notification, builder.build()); }
        catch (SecurityException e) { FileLog.e(e); }
        boolean wasNotified = entry.notified;
        entry.notified = true;
        if (!wasNotified) updateSummary();
        if (immediate) changed();
    }

    public synchronized void onNotificationAction(String fileName, int notification, String action, String tag) {
        if (tag == null) tag = TAG; // Notifications posted before process-specific tags.
        if (notification <= 20000 || !isTaskTag(tag)) return;
        Entry entry = fileName == null ? null : entries.get(fileName);
        if (!NOTIFICATION_TAG.equals(tag) || entry == null || entry.notification != notification) {
            if ("cancel".equals(action)) NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(tag, notification);
            return;
        }
        if ("cancel".equals(action)) {
            boolean externalReady = entry.state.complete && entry.state.external && entry.saves.isEmpty()
                    && entry.consumers.isEmpty() && entry.share == null && !entry.failed;
            if (externalReady) cancelExternal(entry); else cancelPersistent(entry, entry.document);
        }
        else if ("shared".equals(action)) consumeShare(entry.document);
    }

    @Override
    public synchronized void didReceivedNotification(int id, int eventAccount, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
            for (Entry entry : new ArrayList<>(entries.values())) cancel(entry);
            blockedFiles.clear();
            cancelledFiles.clear();
            return;
        }
        String name = (String) args[0];
        Entry entry = entries.get(name);
        if (entry == null) return;
        if (id == NotificationCenter.fileLoaded) {
            if (entry.state.blocked() && !entry.state.playback) return;
            entry.file = args.length > 1 && args[1] instanceof File ? (File) args[1] : entry.source.localFile(account);
            if (entry.file == null || !entry.file.isFile()) return;
            entry.state.complete = true;
            entry.progress = 1;
            if (entry.totalBytes <= 0) entry.totalBytes = entry.file.length();
            entry.downloadedBytes = entry.totalBytes;
            if (!entry.state.blocked()) {
                notifyConsumers(entry);
                finishExports(entry);
            }
            publish(entry, true);
        } else if (id == NotificationCenter.fileLoadFailed) {
            if (entry.state.blocked() || args.length > 1 && Integer.valueOf(1).equals(args[1])) return;
            entry.failed = true;
            publish(entry, true);
        } else {
            entry.downloadedBytes = Math.max(0, (Long) args[1]);
            long total = (Long) args[2];
            if (total > 0) entry.totalBytes = total;
            entry.progress = total == 0 ? 0 : Math.min(1, entry.downloadedBytes / (float) total);
            publish(entry, false);
        }
    }
}
