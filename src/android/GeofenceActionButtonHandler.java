package com.appelit.geofence;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.RemoteInput;
import com.appelit.geofence.data.TransitionEvent;

public class GeofenceActionButtonHandler extends BroadcastReceiver {
    private static final String TAG = "GeofenceActionHandler";

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();

        if (extras != null)	{
            TransitionEvent event = extras.getParcelable(GeofenceConstants.GEOFENCE_BUNDLE);

            if (event != null) {
                int notificationId = event.notificationId;

                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(GeofenceTransitionsIntentService.getAppName(context), notificationId);

                event.foreground = GeofencePlugin.isInForeground();
                event.coldStart = !GeofencePlugin.isActive();

                Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
                if (remoteInput != null) {
                    event.inlineReply = (String)remoteInput.getCharSequence(GeofenceConstants.INLINE_REPLY);
                }

                GeofencePlugin.sendEvent(event);
            }
        }
    }
}