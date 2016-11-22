package com.appelit.geofence;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.RemoteInput;
import android.support.v4.util.SparseArrayCompat;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import com.appelit.geofence.data.Fence;
import com.appelit.geofence.data.Notification;
import com.appelit.geofence.data.NotificationAction;
import com.appelit.geofence.data.TransitionEvent;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SuppressWarnings("WeakerAccess")
@TargetApi(Build.VERSION_CODES.CUPCAKE)
public class GeofenceTransitionsIntentService extends IntentService {
    private static final String TAG = "GeofenceTransitionsIS";
    private static final SparseArrayCompat<ArrayList<String>> messageMap = new SparseArrayCompat<ArrayList<String>>();
    private GeofenceManager geofenceManager;


    public GeofenceTransitionsIntentService() {
        super(TAG);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    static void setNotification(int notificationId, String message) {
        ArrayList<String> messageList = messageMap.get(notificationId);
        if (messageList == null) {
            messageList = new ArrayList<String>();
            messageMap.put(notificationId, messageList);
        }

        if (message != null) {
            if (message.isEmpty()) {
                messageList.clear();
            } else {
                messageList.add(message);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.DONUT)
    static String getAppName(Context context) {
        CharSequence appName = context.getPackageManager().getApplicationLabel(context.getApplicationInfo());
        return (String) appName;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        geofenceManager = new GeofenceManager(this, null);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: " + geofencingEvent.getErrorCode());
            return;
        }

        Context applicationContext = getApplicationContext();
        SharedPreferences prefs = applicationContext.getSharedPreferences(GeofenceConstants.COM_APPELIT_GEOFENCE, Context.MODE_PRIVATE);
        boolean forceShow = prefs.getBoolean(GeofenceConstants.FORCE_SHOW, false);

        TransitionEvent event = convertToTransitionEvent(geofencingEvent);

        if (!forceShow && GeofencePlugin.isInForeground()) {
            event.foreground = true;
            event.coldStart = false;
            GeofencePlugin.sendEvent(event);
        } else if (forceShow && GeofencePlugin.isInForeground()) {
            event.foreground = true;
            event.coldStart = false;
            showNotificationIfPossible(applicationContext, event);
        } else {
            event.foreground = false;
            event.coldStart = GeofencePlugin.isActive();
            showNotificationIfPossible(applicationContext, event);
        }
    }

    private TransitionEvent convertToTransitionEvent(GeofencingEvent event) {
        int geofenceTransition = event.getGeofenceTransition();

        List<String> ids = new ArrayList<String>();

        for (Geofence geofence : event.getTriggeringGeofences()) {
            String id = geofence.getRequestId();
            Fence fence = geofenceManager.getFence(id);

            if ((geofenceTransition & fence.transitionType) != 0) {
                ids.add(id);
            }
        }

        return new TransitionEvent(ids, event.getTriggeringLocation(), geofenceTransition);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private void showNotificationIfPossible(Context context, TransitionEvent event) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        for (String id : event.ids) {
            TransitionEvent fenceEvent = new TransitionEvent(event);
            Fence fence = geofenceManager.getFence(id);
            fenceEvent.id = fence.id;

            if (fence.notification != null) {
                fenceEvent.notificationId = fence.notification.id;
                createNotification(context, notificationManager, fence, new TransitionEvent(fenceEvent));
            } else {
                fenceEvent.notificationId = -1;
            }

            if (!GeofencePlugin.isActive() && fence.forceStart) {
                fenceEvent = new TransitionEvent(fenceEvent);
                fenceEvent.background = true;

                Intent intent = new Intent(this, GeofenceHandlerActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("cdvStartInBackground", true);
                intent.putExtra(GeofenceConstants.GEOFENCE_BUNDLE, fenceEvent);
                startActivity(intent);
            } else {
                GeofencePlugin.sendEvent(fenceEvent);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private void createNotification(Context context, NotificationManager notificationManager, Fence fence, TransitionEvent event) {
        AssetManager assetManager = context.getAssets();
        Resources resources = context.getResources();
        Notification notification = fence.notification;

        String packageName = context.getPackageName();

        event.notificationClick = true;

        int requestCode = new Random().nextInt();
        Intent notificationIntent = new Intent(context, GeofenceHandlerActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.putExtra(GeofenceConstants.GEOFENCE_BUNDLE, event);
        PendingIntent contentIntent = PendingIntent.getActivity(context, requestCode, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        SharedPreferences prefs = context.getSharedPreferences(GeofenceConstants.COM_APPELIT_GEOFENCE, Context.MODE_PRIVATE);
        String title = prefs.getString(GeofenceConstants.TITLE, null);
        String icon = prefs.getString(GeofenceConstants.ICON, null);
        String iconColor = prefs.getString(GeofenceConstants.ICON_COLOR, null);
        boolean soundOption = prefs.getBoolean(GeofenceConstants.SOUND, true);
        boolean vibrateOption = prefs.getBoolean(GeofenceConstants.VIBRATE, true);
        String summaryText = prefs.getString(GeofenceConstants.SUMMARY_TEXT, null);

        if (notification.title != null && !notification.title.isEmpty()) {
            title = notification.title;
        }

        String message = notification.message;
        if (event.transitionType == Geofence.GEOFENCE_TRANSITION_ENTER && notification.enterMessage != null && !notification.enterMessage.isEmpty()) {
             message = notification.enterMessage;
        } else if (event.transitionType == Geofence.GEOFENCE_TRANSITION_EXIT && notification.exitMessage != null && !notification.exitMessage.isEmpty()) {
             message = notification.exitMessage;
        } else if (event.transitionType == Geofence.GEOFENCE_TRANSITION_DWELL && notification.dwellMessage != null && !notification.dwellMessage.isEmpty()) {
             message = notification.dwellMessage;
        }

        if (title == null || title.isEmpty()) {
            title = getAppName(context);
        }

        int id = fence.notification.id;

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setWhen(System.currentTimeMillis())
                .setContentTitle(fromHtml(title))
                .setTicker(fromHtml(title))
                .setContentIntent(contentIntent)
                .setAutoCancel(true);

        if (notification.vibration != null) {
            mBuilder.setVibrate(notification.vibration);
        } else if (vibrateOption) {
            mBuilder.setDefaults(android.app.Notification.DEFAULT_VIBRATE);
        }

        int color = 0;
        if (notification.iconColor != null && !notification.iconColor.isEmpty()) {
            try {
                color = Color.parseColor(notification.iconColor);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "couldn't parse color from android options");
            }
        } else if (iconColor != null && !iconColor.isEmpty()) {
            try {
                color = Color.parseColor(iconColor);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "couldn't parse color from android options");
            }
        }
        if (color != 0) {
            mBuilder.setColor(color);
        }

        int iconId = 0;
        if (notification.icon != null && !notification.icon.isEmpty()) {
            iconId = resources.getIdentifier(notification.icon, "drawable", packageName);
        } else if (icon != null && !"".equals(icon)) {
            iconId = resources.getIdentifier(icon, "drawable", packageName);
        }
        if (iconId == 0) {
            iconId = context.getApplicationInfo().icon;
        }
        mBuilder.setSmallIcon(iconId);

        if (notification.largeIcon != null && !notification.largeIcon.isEmpty()) {
            if (notification.largeIcon.startsWith("http://") || notification.largeIcon.startsWith("https://")) {
                mBuilder.setLargeIcon(getBitmapFromURL(notification.largeIcon));
            } else {
                InputStream input;
                try {
                    input = assetManager.open(notification.largeIcon);
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    mBuilder.setLargeIcon(bitmap);
                } catch (IOException e) {
                    int largeIconId;
                    largeIconId = resources.getIdentifier(notification.largeIcon, "drawable", packageName);
                    if (largeIconId != 0) {
                        Bitmap largeIconBitmap = BitmapFactory.decodeResource(resources, largeIconId);
                        mBuilder.setLargeIcon(largeIconBitmap);
                    }
                }
            }
        }

        if (soundOption) {
            if (notification.sound != null && notification.sound.contentEquals("ringtone")) {
                mBuilder.setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI);
            } else if (notification.sound != null && !notification.sound.contentEquals("default")) {
                Uri sound = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/raw/" + notification.sound);
                mBuilder.setSound(sound);
            } else {
                mBuilder.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
            }
        }

        if (notification.ledColor != null) {
            if (notification.ledColor.length == 4) {
                mBuilder.setLights(Color.argb(notification.ledColor[0], notification.ledColor[1], notification.ledColor[2], notification.ledColor[3]), 500, 500);
            } else {
                Log.e(TAG, "ledColor parameter must be an array of length == 4 (ARGB)");
            }
        }

        if (notification.priority >= NotificationCompat.PRIORITY_MIN && notification.priority <= NotificationCompat.PRIORITY_MAX) {
            mBuilder.setPriority(notification.priority);
        } else {
            Log.e(TAG, "Priority parameter must be between " + NotificationCompat.PRIORITY_MIN + " and " + NotificationCompat.PRIORITY_MAX);
        }

        if (notification.visibility >= NotificationCompat.VISIBILITY_SECRET && notification.visibility <= NotificationCompat.VISIBILITY_PUBLIC) {
            mBuilder.setVisibility(notification.visibility);
        } else {
            Log.e(TAG, "Visibility parameter must be between " + NotificationCompat.VISIBILITY_SECRET + " and " + NotificationCompat.VISIBILITY_PUBLIC);
        }

        if (notification.style != null && notification.style.equals("picture") && notification.picture != null && !notification.picture.isEmpty()) {
            id = Integer.MAX_VALUE - id;

            NotificationCompat.BigPictureStyle bigPicture = new NotificationCompat.BigPictureStyle();
            bigPicture.bigPicture(getBitmapFromURL(notification.picture));
            bigPicture.setBigContentTitle(fromHtml(title));

            if (notification.summaryText != null && !notification.summaryText.isEmpty()) {
                bigPicture.setSummaryText(fromHtml(summaryText));
            }

            if (message != null && !message.isEmpty()) {
                mBuilder.setContentText(fromHtml(message));
            }

            mBuilder.setStyle(bigPicture);
        } else {
            setNotification(notification.id, message);
            mBuilder.setContentText(fromHtml(message));

            ArrayList<String> messageList = messageMap.get(notification.id);
            Integer sizeList = messageList.size();
            if (sizeList > 1) {
                String sizeListMessage = sizeList.toString();
                String stacking = sizeList + " more";
                if (notification.summaryText != null && !notification.summaryText.isEmpty()) {
                    stacking = notification.summaryText;
                    stacking = stacking.replace("%n%", sizeListMessage);
                } else if (summaryText != null) {
                    stacking = summaryText;
                    stacking = stacking.replace("%n%", sizeListMessage);
                }
                NotificationCompat.InboxStyle notificationInbox = new NotificationCompat.InboxStyle()
                        .setBigContentTitle(fromHtml(title))
                        .setSummaryText(fromHtml(stacking));

                for (int i = messageList.size() - 1; i >= 0; i--) {
                    notificationInbox.addLine(fromHtml(messageList.get(i)));
                }

                mBuilder.setStyle(notificationInbox);
            } else {
                NotificationCompat.BigTextStyle bigText = new NotificationCompat.BigTextStyle();
                if (message != null) {
                    bigText.bigText(fromHtml(message));
                    bigText.setBigContentTitle(fromHtml(title));
                    mBuilder.setStyle(bigText);
                }
            }
        }

        if (notification.actions != null && notification.actions.size() > 0) {
            ArrayList<NotificationCompat.Action> wActions = new ArrayList<NotificationCompat.Action>();

            for (NotificationAction action : notification.actions) {
                int min = 1;
                int max = 2000000000;
                Random random = new Random();
                int uniquePendingIntentRequestCode = random.nextInt((max - min) + 1) + min;

                Intent intent;
                PendingIntent pIntent;

                TransitionEvent actionEvent = new TransitionEvent(event);
                actionEvent.callback = action.callback;

                if (action.inline) {
                    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) {
                        intent = new Intent(this, GeofenceHandlerActivity.class);
                    } else {
                        intent = new Intent(this, GeofenceActionButtonHandler.class);
                    }

                    intent.putExtra(GeofenceConstants.GEOFENCE_BUNDLE, actionEvent);

                    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) {
                        pIntent = PendingIntent.getActivity(this, uniquePendingIntentRequestCode, intent, PendingIntent.FLAG_ONE_SHOT);
                    } else {
                        pIntent = PendingIntent.getBroadcast(this, uniquePendingIntentRequestCode, intent, PendingIntent.FLAG_ONE_SHOT);
                    }
                } else if (action.foreground) {
                    intent = new Intent(this, GeofenceHandlerActivity.class);
                    intent.putExtra(GeofenceConstants.GEOFENCE_BUNDLE, actionEvent);
                    pIntent = PendingIntent.getActivity(this, uniquePendingIntentRequestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    intent = new Intent(this, GeofenceActionButtonHandler.class);
                    intent.putExtra(GeofenceConstants.GEOFENCE_BUNDLE, actionEvent);
                    pIntent = PendingIntent.getBroadcast(this, uniquePendingIntentRequestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                }

                NotificationCompat.Action.Builder actionBuilder = new NotificationCompat.Action.Builder(resources.getIdentifier(action.icon, "drawable", packageName), action.title, pIntent);

                if (action.inline) {
                    String replyLabel = "Enter your reply here";
                    if (action.replyLabel != null && !action.replyLabel.isEmpty()) {
                        replyLabel = action.replyLabel;
                    }
                    RemoteInput remoteInput = new RemoteInput.Builder(GeofenceConstants.INLINE_REPLY).setLabel(replyLabel).build();
                    actionBuilder.addRemoteInput(remoteInput);
                }

                NotificationCompat.Action wAction = actionBuilder.build();
                wActions.add(wAction);

                if (action.inline) {
                    mBuilder.addAction(wAction);
                } else {
                    mBuilder.addAction(resources.getIdentifier(action.icon, "drawable", packageName), action.title, pIntent);
                }
            }
            mBuilder.extend(new NotificationCompat.WearableExtender().addActions(wActions));
            wActions.clear();
        }

        notificationManager.notify(getAppName(this), id, mBuilder.build());
    }

    private Bitmap getBitmapFromURL(String strURL) {
        try {
            URL url = new URL(strURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            Log.e(TAG, "could not download bitmap", e);
            return null;
        }
    }

    private Spanned fromHtml(String source) {
        if (source != null) {
            return Html.fromHtml(source);
        } else {
            return null;
        }
    }
}