package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Explicit receiver: notification controls never depend on a live viewer/activity. */
public class FileDownloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int account = intent.getIntExtra("account", -1);
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) return;
        FileDownloadTasks.getInstance(account).onNotificationAction(
                intent.getStringExtra("file"), intent.getIntExtra("notification", -1),
                intent.getStringExtra("action"), intent.getStringExtra("notification_tag"));
    }
}
