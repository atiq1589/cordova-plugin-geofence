package com.appelit.geofence.data;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import com.google.gson.*;
import com.google.gson.annotations.Expose;

import java.lang.reflect.Type;

public class TransitionLocation implements Parcelable {
    public static final Creator<TransitionLocation> CREATOR = new Creator<TransitionLocation>() {
        @Override
        public TransitionLocation createFromParcel(Parcel in) {
            return new TransitionLocation(in);
        }

        @Override
        public TransitionLocation[] newArray(int size) {
            return new TransitionLocation[size];
        }
    };

    @Expose
    public double latitude;
    @Expose
    public double longitude;
    @Expose
    public float accuracy;
    @Expose
    public boolean hasAccuracy;
    @Expose
    public double altitude;
    @Expose
    public boolean hasAltitude;
    @Expose
    public float bearing;
    @Expose
    public boolean hasBearing;
    @Expose
    public float speed;
    @Expose
    public boolean hasSpeed;
    @Expose
    public long time;

    public TransitionLocation() {
    }

    TransitionLocation(Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        accuracy = location.getAccuracy();
        hasAccuracy = location.hasAccuracy();
        altitude = location.getAltitude();
        hasAltitude = location.hasAltitude();
        bearing = location.getBearing();
        hasBearing = location.hasBearing();
        speed = location.getSpeed();
        hasSpeed = location.hasSpeed();
        time = location.getTime();
    }

    public TransitionLocation(Parcel in) {
        latitude = in.readDouble();
        longitude = in.readDouble();
        accuracy = in.readFloat();
        altitude = in.readDouble();
        bearing = in.readFloat();
        speed = in.readFloat();
        time = in.readLong();

        int mask = in.readInt();

        hasAccuracy = (mask & 1) == 1;
        hasAltitude = (mask & 2) == 2;
        hasBearing = (mask & 4) == 4;
        hasSpeed = (mask & 8) == 8;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeFloat(accuracy);
        dest.writeDouble(altitude);
        dest.writeFloat(bearing);
        dest.writeFloat(speed);
        dest.writeLong(time);

        int bools = 0;
        if (hasAccuracy) {
            bools = 1;
        }

        if (hasAltitude) {
            bools = bools | 2;
        }

        if (hasBearing) {
            bools = bools | 4;
        }

        if (hasSpeed) {
            bools = bools | 8;
        }

        dest.writeInt(bools);
    }
}
