package com.appelit.geofence;

import com.google.gson.GsonBuilder;

class Gson {
    private static final com.google.gson.Gson gson;

    static {
        gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .enableComplexMapKeySerialization()
                .create();
    }

    public static com.google.gson.Gson get() {
        return gson;
    }
}
