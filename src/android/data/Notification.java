package com.appelit.geofence.data;

import com.google.gson.annotations.Expose;

import java.util.List;

public class Notification {
    @Expose
    public int id;
    @Expose
    public String title;
    @Expose
    public String message;
    @Expose
    public String enterMessage;
    @Expose
    public String exitMessage;
    @Expose
    public String dwellMessage;
    @Expose
    public String summaryText;
    @Expose
    public String icon;
    @Expose
    public String iconColor;
    @Expose
    public String largeIcon;
    @Expose
    public String sound;
    @Expose
    public String style;
    @Expose
    public int priority;
    @Expose
    public int visibility;
    @Expose
    public String picture;
    @Expose
    public int[] ledColor;
    @Expose
    public long[] vibration;
    @Expose
    public List<NotificationAction> actions;

    public Notification() {
    }
}
