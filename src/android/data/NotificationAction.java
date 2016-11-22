package com.appelit.geofence.data;

import com.google.gson.*;
import com.google.gson.annotations.Expose;

import java.lang.reflect.Type;

public class NotificationAction {
    @Expose
    public String title;
    @Expose
    public String icon = "";
    @Expose
    public String callback;
    @Expose
    public boolean inline = false;
    @Expose
    public boolean foreground = true;
    @Expose
    public String replyLabel;

    public NotificationAction() {
    }
}
