package com.appelit.geofence;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import com.appelit.geofence.data.Fence;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.*;
import com.google.android.gms.location.*;
import com.google.gson.JsonSyntaxException;

import java.util.*;

class GeofenceManager implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<Status> {
    private static final Map<String, Fence> fences = new HashMap<String, Fence>();
    private static final String TAG = "GeofenceManager";
    private static boolean loaded = false;
    private final Context context;
    private final GoogleApiClient googleApiClient;
    private final Listener listener;
    private PendingIntent mGeofencePendingIntent;
    private boolean isAvailable = true;
    private boolean isConnected = false;
    private boolean isRestored;

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    GeofenceManager(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;

        LocalStorage localStorage = new LocalStorage(context);

        synchronized (this) {
            if (!loaded) {
                String sFences = localStorage.getItem("fences");

                if (sFences != null && !sFences.isEmpty()) {
                    try {
                        Fence[] fences = Gson.get().fromJson(sFences, Fence[].class);
                        Log.v(TAG, "Restoring " + fences.length + " fences");
                        for (Fence fence : fences) {
                            GeofenceManager.fences.put(fence.id, fence);
                        }
                    } catch (JsonSyntaxException ignored) {
                    }
                }

                loaded = true;
            }
        }

        googleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        googleApiClient.connect();
    }

    Fence getFence(String id) {
        return GeofenceManager.fences.get(id);
    }

    List<Fence> getFences() {
        return new ArrayList<Fence>(GeofenceManager.fences.values());
    }

    String addFences(List<Fence> fences) {
        while (!isConnected && isAvailable) {
            Thread.yield();
        }

        if (isAvailable) {
            if (fences.size() > 0) {
                try {
                    LocationServices.GeofencingApi.addGeofences(googleApiClient, getGeofencingRequest(fences), getGeofencingPendingIntent()).setResultCallback(this);

                    for (Fence fence : fences) {
                        GeofenceManager.fences.put(fence.id, fence);
                    }

                    save();
                } catch (SecurityException securityException) {
                    Log.e(TAG, "Invalid location permission. You need to use ACCESS_FINE_LOCATION with geofences", securityException);
                    return "permission";
                }
            } else if (this.listener != null) {
                this.listener.result(this, new Status(CommonStatusCodes.SUCCESS));
            }

            return null;
        }

        Log.w(TAG, "Google Play Services unavailable");
        return "unavailable";
    }

    String removeFences(List<String> ids) {
        while (!isConnected && isAvailable) {
            Thread.yield();
        }

        if (isAvailable) {
            if (ids.size() > 0) {
                try {
                    LocationServices.GeofencingApi.removeGeofences(googleApiClient, ids).setResultCallback(this);

                    for (String id : ids) {
                        fences.remove(id);
                    }

                    save();
                } catch (SecurityException securityException) {
                    Log.e(TAG, "Invalid location permission. You need to use ACCESS_FINE_LOCATION with geofences", securityException);
                    return "permission";
                }
            } else if (this.listener != null) {
                this.listener.result(this, new Status(CommonStatusCodes.SUCCESS));
            }

            return null;
        }

        Log.w(TAG, "Google Play Services unavailable");
        return "unavailable";
    }

    String removeAllFences() {
        while (!isConnected && isAvailable) {
            Thread.yield();
        }

        if (isAvailable) {
            try {
                removeFences(new ArrayList<String>(fences.keySet()));
            } catch (SecurityException securityException) {
                Log.e(TAG, "Invalid location permission. You need to use ACCESS_FINE_LOCATION with geofences", securityException);
                return "permission";
            }

            return null;
        }

        Log.w(TAG, "Google Play Services unavailable");
        return "unavailable";
    }

    void showLocationSettings() {
        while (!isConnected && isAvailable) {
            Thread.yield();
        }

        if (isAvailable) {
            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(30000);
            locationRequest.setFastestInterval(5000);
            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
            builder.setAlwaysShow(true);

            LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build()).setResultCallback(new ResultCallback<LocationSettingsResult>() {
                @Override
                public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                    if (listener != null) {
                        listener.locationSettingsResult(GeofenceManager.this, locationSettingsResult.getStatus());
                    }
                }
            });
        }
    }

    void stop() {
        removeAllFences();
        googleApiClient.disconnect();
    }

    void connect() {
        googleApiClient.connect();
    }

    void disconnect() {
        googleApiClient.disconnect();
    }

    void restoreFences() {
        if (!isRestored) {
            List<Fence> fences = getFences();
            Log.v(TAG, "Going to restore " + fences.size() + " fences");
            addFences(fences);
            isRestored = true;
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Connected to GoogleApiClient");

        isConnected = true;
        isAvailable = true;
        if (this.listener != null) {
            listener.connected(this, bundle);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection connectionSuspended");

        isConnected = false;
        isAvailable = true;
        if (this.listener != null) {
            listener.connectionSuspended(this, i);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "Connection connectionFailed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());

        isConnected = false;
        isAvailable = false;
        if (this.listener != null) {
            listener.connectionFailed(this, connectionResult);
        }
    }

    @Override
    public void onResult(@NonNull Status status) {
        if (!status.isSuccess()) {
            Log.w(TAG, "Geofence action failed (" + status.getStatusCode() + "): " + status.getStatusMessage());
        }

        if (this.listener != null) {
            this.listener.result(this, status);
        }
    }

    private void save() {
        Collection<Fence> values = GeofenceManager.fences.values();
        Fence[] fences = values.toArray(new Fence[values.size()]);

        synchronized (this) {
            LocalStorage localStorage = new LocalStorage(context);
            localStorage.setItem("fences", Gson.get().toJson(fences, Fence[].class));
        }
    }

    private GeofencingRequest getGeofencingRequest(List<Fence> fences) {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();

        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);

        List<Geofence> geofenceList = new ArrayList<Geofence>();
        for (Fence fence : fences) {
            geofenceList.add(fence.toGeofence());
        }

        builder.addGeofences(geofenceList);

        return builder.build();
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private synchronized PendingIntent getGeofencingPendingIntent() {
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }

        Intent intent = new Intent(context, GeofenceTransitionsIntentService.class);
        mGeofencePendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        return mGeofencePendingIntent;
    }

    interface Listener {
        void connected(GeofenceManager manager, Bundle bundle);

        void connectionSuspended(GeofenceManager geofenceManager, int i);

        void connectionFailed(GeofenceManager geofenceManager, ConnectionResult connectionResult);

        void result(GeofenceManager manager, Status status);

        void locationSettingsResult(GeofenceManager manager, Status status);
    }
}
