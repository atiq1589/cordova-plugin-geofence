package com.appelit.geofence;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.RemoteInput;
import com.appelit.geofence.data.TransitionEvent;

public class GeofenceHandlerActivity extends Activity {
    @TargetApi(Build.VERSION_CODES.ECLAIR)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            TransitionEvent event = extras.getParcelable(GeofenceConstants.GEOFENCE_BUNDLE);

            if (event != null) {
                int notificationId = event.notificationId;

                if (!event.background) {
                    NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.cancel(GeofenceTransitionsIntentService.getAppName(this), notificationId);
                    GeofenceTransitionsIntentService.setNotification(notificationId, "");
                }

                event.foreground = false;
                event.coldStart = !GeofencePlugin.isActive();

                Bundle remoteInput = RemoteInput.getResultsFromIntent(getIntent());
                if (remoteInput != null) {
                    event.inlineReply = (String)remoteInput.getCharSequence(GeofenceConstants.INLINE_REPLY);
                }

                GeofencePlugin.sendEvent(event);

                finish();

                if (event.coldStart && !event.background) {
                    startMainActivity(event, false);
                } else if (event.background) {
                    startMainActivity(event, true);
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private void startMainActivity(TransitionEvent event, boolean startInBackground) {
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launchIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);

        launchIntent.putExtra(GeofenceConstants.GEOFENCE_BUNDLE, event);
        launchIntent.putExtra("cdvStartInBackground", startInBackground);

        startActivity(launchIntent);
    }
}
