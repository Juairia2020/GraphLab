package com.graphlab.data.cloud;

import com.graphlab.BuildConfig;

/**
 * Whether this build knows which Firebase project to talk to. The three values come from
 * local.properties at build time (see the README, Cloud gallery); they are empty in a fresh checkout,
 * and then every cloud feature says "not set up" instead of failing.
 */
public final class CloudConfig {
    private CloudConfig() {
    }

    /** Push messages need the sender id as well as the three values above. */
    public static boolean isMessagingConfigured() {
        return isConfigured() && !BuildConfig.FIREBASE_SENDER_ID.isEmpty();
    }

    public static boolean isConfigured() {
        return !BuildConfig.FIREBASE_API_KEY.isEmpty()
                && !BuildConfig.FIREBASE_APP_ID.isEmpty()
                && !BuildConfig.FIREBASE_PROJECT_ID.isEmpty();
    }
}
