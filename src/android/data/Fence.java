package com.appelit.geofence.data;

import com.google.android.gms.location.Geofence;
import com.google.gson.*;
import com.google.gson.annotations.Expose;

import java.lang.reflect.Type;

public class Fence {
    @Expose
    public String id;
    @Expose
    public double latitude;
    @Expose
    public double longitude;
    @Expose
    public float radius;
    @Expose
    public int transitionType;
    @Expose
    public int loiteringDelay;
    @Expose
    public int responseTimeout;
    @Expose
    public boolean forceStart;
    @Expose
    public Notification notification;

    public Fence() {
    }

    public Geofence toGeofence() {
        return new Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(latitude, longitude, radius)
                .setTransitionTypes(transitionType)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setLoiteringDelay(loiteringDelay)
                .setNotificationResponsiveness(responseTimeout)
                .build();
    }
}
