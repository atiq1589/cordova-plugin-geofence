package com.appelit.geofence;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import com.appelit.geofence.data.Fence;
import com.appelit.geofence.data.TransitionEvent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.Status;
import com.google.gson.JsonSyntaxException;
import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GeofencePlugin extends CordovaPlugin implements GeofenceManager.Listener {
    private static final int GOOGLE_API_CONNECTION_REQUEST = 0x43370;
    private static final int GOOGLE_API_RESULT_REQUEST = 0x43371;
    private static final int GOOGLE_API_LOCATION_REQUEST = 0x43372;
    private static final int PERMISSION_REQUEST = 0x43370;
    private static final String TAG = "GeofencePlugin";
    private static final ArrayList<TransitionEvent> cachedEvents = new ArrayList<TransitionEvent>();
    private static final ArrayList<Action> cachedActions = new ArrayList<Action>();
    private static CallbackContext geofenceContext;
    private static CordovaWebView gWebView;
    private static boolean foreground = false;
    private GeofenceManager geofenceManager;
    private CallbackContext activityCallbackContext;
    private boolean isWorking;
    private boolean isInitialized;

    private static void sendError(String code, String message) {
        if (geofenceContext != null) {
            try {
                JSONObject json = new JSONObject();
                json.put("code", code);
                json.put("message", message);
                PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, json);
                pluginResult.setKeepCallback(true);
                geofenceContext.sendPluginResult(pluginResult);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException: " + e.getMessage(), e);
            }
        }
    }

    static boolean isInForeground() {
        return foreground;
    }

    static boolean isActive() {
        return gWebView != null;
    }

    static void sendEvent(TransitionEvent event) {
        if (event != null) {
            if (geofenceContext != null) {
                try {
                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, new JSONObject(Gson.get().toJson(event, TransitionEvent.class)));
                    pluginResult.setKeepCallback(true);
                    geofenceContext.sendPluginResult(pluginResult);
                } catch (JSONException e) {
                    Log.e(TAG, "JsonException: " + e.getMessage());
                }
            } else {
                cachedEvents.add(event);
            }
        }
    }

    private void sendResultForAction(PluginResult.Status status, Action action, String code, String message) {
        CallbackContext context = null;
        if (action != null) {
            context = action.context;
        }

        boolean keep = false;
        if (context == null) {
            keep = true;

            if (activityCallbackContext != null) {
                context = activityCallbackContext;
            } else if (geofenceContext != null) {
                context = geofenceContext;
            } else {
                return;
            }
        }

        try {
            JSONObject json = new JSONObject();
            json.put("code", code);
            json.put("message", message);
            PluginResult pluginResult = new PluginResult(status, json);
            pluginResult.setKeepCallback(keep);
            context.sendPluginResult(pluginResult);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException", e);
        }
    }

    private void sendResultForAction(PluginResult.Status status, Action action, String code) {
        sendResultForAction(status, action, code, null);
    }

    /**
     * Gets the application context form cordova's main activity
     *
     * @return the application context
     */
    private Context getApplicationContext() {
        return this.cordova.getActivity().getApplicationContext();
    }

    private void clearAllNotifications() {
        final NotificationManager notificationManager = (NotificationManager) cordova.getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    private void runActions() {
        synchronized(cachedActions) {
            if (!isWorking && !cachedActions.isEmpty()) {
                final Action action = cachedActions.get(0);
                isWorking = true;

                Log.v(TAG, action.action);

                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String resultMessage;
                            if (action.action.equals("addFences")) {
                                Log.v(TAG, "addFences");
                                Fence[] fences = Gson.get().fromJson(action.args.getJSONArray(0).toString(), Fence[].class);
                                resultMessage = geofenceManager.addFences(Arrays.asList(fences));
                            } else if (action.action.equals("removeFences")) {
                                Log.v(TAG, "removeFences");
                                String[] ids = Gson.get().fromJson(action.args.getJSONArray(0).toString(), String[].class);

                                resultMessage = geofenceManager.removeFences(Arrays.asList(ids));
                            } else {
                                Log.v(TAG, "removeAllFences");
                                resultMessage = geofenceManager.removeAllFences();
                            }

                            if (resultMessage != null) {
                                sendResultForAction(PluginResult.Status.ERROR, action, resultMessage);
                                
                                synchronized(cachedActions) {
                                    cachedActions.remove(0);
                                }

                                isWorking = false;
                                runActions();
                            }
                        } catch (JSONException e) {
                            sendResultForAction(PluginResult.Status.ERROR, action, e.getMessage());
                            
                            synchronized (cachedActions) {
                                cachedActions.remove(0);
                            }

                            isWorking = false;
                            runActions();
                        } catch (JsonSyntaxException e) {
                            sendResultForAction(PluginResult.Status.ERROR, action, e.getMessage());
                            
                            synchronized (cachedActions) {
                                cachedActions.remove(0);
                            }
                            
                            isWorking = false;
                            runActions();
                        }
                    }
                });
            }
        }
    }

    @Override
    public boolean execute(String action, final JSONArray data, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("init")) {
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    geofenceContext = callbackContext;

                    try {
                        JSONObject json = data.getJSONObject(0).optJSONObject("android");

                        if (json != null) {
                            SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(GeofenceConstants.COM_APPELIT_GEOFENCE, Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPref.edit();

                            try {
                                editor.putString(GeofenceConstants.ICON, json.getString(GeofenceConstants.ICON));
                            } catch (JSONException ignored) {
                            }

                            try {
                                editor.putString(GeofenceConstants.ICON_COLOR, json.getString(GeofenceConstants.ICON_COLOR));
                            } catch (JSONException ignored) {
                            }

                            editor.putBoolean(GeofenceConstants.SOUND, json.optBoolean(GeofenceConstants.SOUND, true));
                            editor.putBoolean(GeofenceConstants.VIBRATE, json.optBoolean(GeofenceConstants.VIBRATE, true));
                            editor.putBoolean(GeofenceConstants.CLEAR_NOTIFICATIONS, json.optBoolean(GeofenceConstants.CLEAR_NOTIFICATIONS, true));
                            editor.putBoolean(GeofenceConstants.FORCE_SHOW, json.optBoolean(GeofenceConstants.FORCE_SHOW, false));
                            editor.commit();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "execute: Got JSON Exception " + e.getMessage());
                        callbackContext.error(e.getMessage());
                    }

                    if (!cachedEvents.isEmpty()) {
                        Log.v(TAG, "sending cached extras");
                        synchronized (cachedEvents) {
                            for (TransitionEvent event : cachedEvents) {
                                sendEvent(event);
                            }
                        }
                        cachedEvents.clear();
                    }

                    if (!cordova.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        cordova.requestPermission(GeofencePlugin.this, PERMISSION_REQUEST, Manifest.permission.ACCESS_FINE_LOCATION);
                    } else {
                        isInitialized = true;

                        synchronized(cachedActions) {
                            if (!cachedActions.isEmpty()) {
                                runActions();
                            }
                        }
                    }

                    LocationManager locationManager = (LocationManager)getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        geofenceManager.restoreFences();
                    } else {
                        isWorking = true;
                        geofenceManager.showLocationSettings();
                    }
                }
            });
        } else if (action.equals("finish")) {
            callbackContext.success();
        } else if (action.equals("hasPermission")) {
            callbackContext.success(cordova.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ? 1 : 0);
        } else if (action.equals("clearAllNotifications")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Log.v(TAG, "clearAllNotifications");
                    clearAllNotifications();
                    callbackContext.success();
                }
            });
        } else if (action.equals("getFence")) {
            Log.v(TAG, "getFence");
            Fence fence = geofenceManager.getFence(data.getString(0));
            if (fence != null) {
                callbackContext.success(new JSONObject(Gson.get().toJson(fence, Fence.class)));
            } else {
                callbackContext.success((String)null);
            }
        } else if (action.equals("getFences")) {
            Log.v(TAG, "getFences");
            List<Fence> fences = geofenceManager.getFences();
            callbackContext.success(new JSONArray(Gson.get().toJson(fences.toArray(new Fence[fences.size()]), Fence[].class)));
        } else if (action.equals("addFences") || action.equals("removeFences") || action.equals("removeAllFences")) {
            synchronized (cachedActions) {
                cachedActions.add(new Action(action, data, callbackContext));
            }

            if (isInitialized) {
                runActions();
            }
        } else {
            Log.e(TAG, "Invalid action : " + action);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }

        return true;
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList("events", cachedEvents);
        bundle.putParcelableArrayList("actions", cachedActions);
        return bundle;
    }

    @Override
    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        super.onRestoreStateForActivityResult(state, callbackContext);

        List<TransitionEvent> events = state.getParcelableArrayList("events");
        if (events != null) {
            cachedEvents.addAll(events);
        }

        List<Action> actions = state.getParcelableArrayList("actions");
        if (actions != null) {
            synchronized (cachedActions) {
                cachedActions.addAll(actions);
            }
        }

        activityCallbackContext = callbackContext;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case GOOGLE_API_CONNECTION_REQUEST:
                isWorking = false;
                if (resultCode == Activity.RESULT_OK) {
                    geofenceManager.connect();
                } else {
                    sendError("failed", null);
                }
                break;
            case GOOGLE_API_RESULT_REQUEST:
                if (resultCode != Activity.RESULT_OK) {
                    Action action = null;
                    synchronized (cachedActions) {
                        if (!cachedActions.isEmpty()) {
                            action = cachedActions.remove(0);
                        }
                    }

                    sendResultForAction(PluginResult.Status.ERROR, action, "failed", Integer.toString(resultCode));
                } else {
                    isWorking = false;
                    runActions();
                }
                break;
            case GOOGLE_API_LOCATION_REQUEST:
                Log.d(TAG, "LocationRequest result: " + resultCode);
                if (resultCode != Activity.RESULT_OK) {
                    sendError("location_unavailable", null);
                } else {
                    geofenceManager.restoreFences();
                }
            default:
                super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        switch (requestCode) {
            case PERMISSION_REQUEST:
                for (int grantResult : grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        isInitialized = false;
                        return;
                    }
                }
                isInitialized = true;

                synchronized (cachedActions) {
                    if (!cachedActions.isEmpty()) {
                        runActions();
                    }
                }
                break;
            default:
                super.onRequestPermissionResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        geofenceManager = new GeofenceManager(getApplicationContext(), this);
        gWebView = webView;
        foreground = true;
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        foreground = false;
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        foreground = true;

        SharedPreferences prefs = getApplicationContext().getSharedPreferences(GeofenceConstants.COM_APPELIT_GEOFENCE, Context.MODE_PRIVATE);
        if (prefs.getBoolean(GeofenceConstants.CLEAR_NOTIFICATIONS, true)) {
            clearAllNotifications();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        foreground = false;
        gWebView = null;
        geofenceContext = null;
    }

    @Override
    public void connected(GeofenceManager manager, Bundle bundle) {
    }

    @Override
    public void connectionSuspended(GeofenceManager geofenceManager, int i) {
    }

    @TargetApi(Build.VERSION_CODES.DONUT)
    @Override
    public void connectionFailed(GeofenceManager geofenceManager, final ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        connectionResult.startResolutionForResult(cordova.getActivity(), GOOGLE_API_CONNECTION_REQUEST);
                    } catch (IntentSender.SendIntentException ignored) {
                    }
                }
            });
        } else {
            sendError("unavailable", connectionResult.getErrorMessage());
        }
    }

    @TargetApi(Build.VERSION_CODES.DONUT)
    @Override
    public void result(GeofenceManager manager, final Status status) {
        Action action = null;
        
        synchronized(cachedActions) {
            if (!cachedActions.isEmpty()) {
                action = cachedActions.remove(0);
            }
        }

        if (status.isSuccess()) {
            sendResultForAction(PluginResult.Status.OK, action, null);
            isWorking = false;
            runActions();
        } else if (status.hasResolution()) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        status.startResolutionForResult(cordova.getActivity(), GOOGLE_API_RESULT_REQUEST);
                    } catch (IntentSender.SendIntentException ignored) {
                    }
                }
            });
        } else {
            sendResultForAction(PluginResult.Status.ERROR, action, "failed", status.getStatusMessage());
            isWorking = false;
            runActions();
        }
    }

    @Override
    public void locationSettingsResult(GeofenceManager manager, final Status status) {
        if (status.isSuccess()) {
            manager.restoreFences();
        } else if (status.hasResolution()) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        status.startResolutionForResult(cordova.getActivity(), GOOGLE_API_LOCATION_REQUEST);
                    } catch (IntentSender.SendIntentException ignored) {
                    }
                }
            });
        } else {
            sendError("location_unavailable", status.getStatusMessage());
        }
    }

    private static class Action implements Parcelable {
        public static final Creator<Action> CREATOR = new Creator<Action>() {
            @Override
            public Action createFromParcel(Parcel in) {
                return new Action(in);
            }

            @Override
            public Action[] newArray(int size) {
                return new Action[size];
            }
        };

        String action;
        JSONArray args;
        CallbackContext context;

        Action(String action, JSONArray args, CallbackContext context) {
            this.action = action;
            this.args = args;
            this.context = context;
        }

        protected Action(Parcel in) {
            this.action = in.readString();
            try {
                this.args = new JSONArray(in.readString());
            } catch (JSONException ignored) {
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(action);
            dest.writeString(args.toString());
        }
    }
}
