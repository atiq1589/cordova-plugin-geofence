package com.appelit.geofence.data;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;

public class TransitionEvent implements Parcelable {
    public static final Creator<TransitionEvent> CREATOR = new Creator<TransitionEvent>() {
        @Override
        public TransitionEvent createFromParcel(Parcel in) {
            return new TransitionEvent(in);
        }

        @Override
        public TransitionEvent[] newArray(int size) {
            return new TransitionEvent[size];
        }
    };

    @Expose
    public List<String> ids;
    @Expose
    public TransitionLocation location;
    @Expose
    public int transitionType;
    @Expose
    public String id;
    @Expose
    public int notificationId;
    @Expose
    public boolean coldStart;
    @Expose
    public boolean foreground;
    @Expose
    public boolean background;
    @Expose
    public String callback;
    @Expose
    public String inlineReply;
    @Expose
    public boolean notificationClick;

    public TransitionEvent() {
    }

    public TransitionEvent(List<String> ids, TransitionLocation location, int transitionType) {
        this.ids = ids;
        this.location = location;
        this.transitionType = transitionType;
    }

    public TransitionEvent(List<String> ids, Location location, int transitionType) {
        this(ids, new TransitionLocation(location), transitionType);
    }

    public TransitionEvent(TransitionEvent event) {
        this.ids = event.ids;
        this.location = event.location;
        this.transitionType = event.transitionType;
        this.id = event.id;
        this.notificationId = event.notificationId;
        this.coldStart = event.coldStart;
        this.foreground = event.foreground;
        this.background = event.background;
        this.notificationClick = event.notificationClick;
        this.callback = event.callback;
        this.inlineReply = event.inlineReply;
    }

    public TransitionEvent(Parcel in) {
        ids = new ArrayList<String>();
        in.readStringList(ids);
        location = in.readParcelable(TransitionLocation.class.getClassLoader());
        transitionType = in.readInt();
        id = in.readString();
        notificationId = in.readInt();

        int bools = in.readInt();
        coldStart = (bools & 1) == 1;
        foreground = (bools & 2) == 2;
        background = (bools & 4) == 4;
        notificationClick = (bools & 8 ) == 8;

        callback = in.readString();
        inlineReply = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringList(ids);
        dest.writeParcelable(location, flags);
        dest.writeInt(transitionType);
        dest.writeString(id);
        dest.writeInt(notificationId);

        int bools = 0;
        if (coldStart) {
            bools = 1;
        }

        if (foreground) {
            bools = bools | 2;
        }

        if (background) {
            bools = bools | 4;
        }

        if (notificationClick) {
            bools = bools | 8;
        }

        dest.writeInt(bools);

        dest.writeString(callback);
        dest.writeString(inlineReply);
    }
}
