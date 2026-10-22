package com.graphlab.data.cloud;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.graphlab.BuildConfig;

/**
 * Starts Firebase from the values in {@link BuildConfig} instead of from google-services.json, so
 * the project builds and runs with no Firebase account at all. Without the three values nothing is started.
 */
public final class FirebaseInit {
    private FirebaseInit() {
    }

    /** @return true if Firebase is ready to use */
    public static boolean init(@NonNull Context context) {
        if (!CloudConfig.isConfigured()) {
            return false;
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID.isEmpty() ? null : BuildConfig.FIREBASE_SENDER_ID)
                    .build();
            FirebaseApp.initializeApp(context, options);
        }
        return true;
    }
}
