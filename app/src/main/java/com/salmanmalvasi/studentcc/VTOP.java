package com.salmanmalvasi.studentcc;

import com.salmanmalvasi.studentcc.helpers.AppContextProvider;
import com.salmanmalvasi.studentcc.helpers.FirebaseHelper;
import com.salmanmalvasi.studentcc.helpers.FirebaseCrashlyticsHelper;
import com.salmanmalvasi.studentcc.helpers.FirebaseConfigHelper;

public class VTOP extends AppContextProvider {
    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase services
        FirebaseHelper.getInstance().initializeFirebase(this);

        // Initialize Firebase Crashlytics
        FirebaseCrashlyticsHelper.initialize(this);

        // Initialize Firebase Remote Config
        FirebaseConfigHelper.initialize(this);

        // Log that Firebase In-App Messaging is ready
        android.util.Log.d("VTOP", "Firebase In-App Messaging initialized and ready");
    }
}
