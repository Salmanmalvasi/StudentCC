package com.salmanmalvasi.studentcc.helpers;

import android.content.Context;

/**
 * Placeholder helper retained while the events feature is disabled.
 */
public class EventsManager {

    private static EventsManager instance;

    private EventsManager() {
    }

    public static synchronized EventsManager getInstance(Context context) {
        if (instance == null) {
            instance = new EventsManager();
        }
        return instance;
    }

}
