package com.appelit.geofence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import com.appelit.geofence.data.Fence;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.Status;

import java.util.List;

public class GeofenceReceiver extends BroadcastReceiver {
    private final static String TAG = "GeofenceReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        new GeofenceManager(context, new GeofenceManager.Listener() {
            @Override
            public void connected(GeofenceManager manager, Bundle bundle) {
                LocationManager locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    manager.restoreFences();
                } else {
                    Log.w(TAG, "High accuracy location is not enabled, not restoring fences");
                }
            }

            @Override
            public void connectionSuspended(GeofenceManager geofenceManager, int i) {

            }

            @Override
            public void connectionFailed(GeofenceManager geofenceManager, ConnectionResult connectionResult) {
                Log.e(TAG, "Connection to Google Play Services failed: " + connectionResult.getErrorMessage());
            }

            @Override
            public void result(GeofenceManager manager, Status status) {
                if (status.isSuccess()) {
                    Log.v(TAG, "Geofences re-initialized");
                } else {
                    Log.w(TAG, "Could not re-initialize geofences");
                }
            }

            @Override
            public void locationSettingsResult(GeofenceManager manager, Status status) {
            }
        });
    }
}