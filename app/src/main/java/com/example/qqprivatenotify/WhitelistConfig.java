package com.example.qqprivatenotify;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import java.util.Set;

/**
 * Configuration manager for notification filtering.
 * Provides independent switches for private and group notifications.
 */
final class WhitelistConfig {

    private static final String PREFS_NAME = "settings";
    static final String PRIVATE_ENABLED = "private_enabled";
    static final String GROUP_ENABLED = "group_enabled";

    // Content provider URI for reading config from QQ process
    private static final String AUTHORITY = "com.example.qqprivatenotify.config";
    private static final Uri CONFIG_URI = Uri.parse("content://" + AUTHORITY + "/settings");

    // Cached remote preferences (set by Xposed framework)
    private static volatile SharedPreferences remotePreferences;

    // Cache for the last successfully read config method
    private static volatile ConfigReadMethod lastSuccessfulMethod = null;

    private WhitelistConfig() {
        throw new AssertionError("No instances");
    }

    /**
     * Sets the remote preferences provider (called by Xposed module).
     * @param preferences The remote preferences to use
     */
    static void setRemotePreferences(SharedPreferences preferences) {
        remotePreferences = preferences;
    }

    /**
     * Checks if configuration is available.
     * @param context The context to use
     * @return true if config can be read
     */
    static boolean isConfigured(Context context) {
        return context != null;
    }

    /**
     * Determines if a notification should be shown based on its source object.
     * @param context Application context
     * @param source The notification source object
     * @param type The conversation type
     * @return true if notification should be shown
     */
    static boolean shouldShowSource(Context context, Object source, int type) {
        return enabled(context, type);
    }

    /**
     * Determines if a notification should be shown.
     * @param context Application context
     * @param notification The notification object
     * @param type The conversation type
     * @return true if notification should be shown
     */
    static boolean shouldShow(Context context, Notification notification, int type) {
        return enabled(context, type);
    }

    /**
     * Determines if a notification should be shown with conversation IDs.
     * @param context Application context
     * @param notification The notification object
     * @param type The conversation type
     * @param ids Set of conversation IDs (currently unused)
     * @return true if notification should be shown
     */
    static boolean shouldShow(Context context, Notification notification, int type, Set<String> ids) {
        return enabled(context, type);
    }

    /**
     * Determines if unknown conversation types should be shown.
     * @param context Application context
     * @return true if unknown types should be shown
     */
    static boolean shouldShowUnknown(Context context) {
        Config config = read(context);
        return config.groupEnabled;
    }

    /**
     * Collects conversation IDs from a source object (currently not implemented).
     * @param source The source object
     * @param out Set to add IDs to
     */
    static void collectObjectIds(Object source, Set<String> out) {
        // Future implementation for per-conversation filtering
    }

    /**
     * Checks if a conversation type should show notifications.
     * @param context Application context
     * @param type The conversation type
     * @return true if enabled
     */
    private static boolean enabled(Context context, int type) {
        Config config = read(context);

        if (type == ConversationClassifier.GROUP) {
            return config.groupEnabled;
        }

        if (type == ConversationClassifier.PRIVATE) {
            return config.privateEnabled;
        }

        // Unknown types are allowed by default
        return true;
    }

    /**
     * Reads configuration using the fastest available method.
     * @param context Application context
     * @return Configuration object
     */
    private static Config read(Context context) {
        // Try the last successful method first (if any)
        if (lastSuccessfulMethod != null) {
            Config config = tryReadMethod(context, lastSuccessfulMethod);
            if (config != null) {
                return config;
            }
            // Method failed, clear cache
            lastSuccessfulMethod = null;
        }

        // Try all methods in order of preference
        for (ConfigReadMethod method : ConfigReadMethod.values()) {
            Config config = tryReadMethod(context, method);
            if (config != null) {
                lastSuccessfulMethod = method;
                return config;
            }
        }

        // Fallback to defaults
        return new Config(true, false);
    }

    /**
     * Attempts to read config using a specific method.
     * @param context Application context
     * @param method The read method to try
     * @return Configuration or null if method failed
     */
    private static Config tryReadMethod(Context context, ConfigReadMethod method) {
        try {
            return switch (method) {
                case REMOTE_PREFERENCES -> readFromRemotePreferences();
                case CONTENT_PROVIDER -> readFromContentProvider(context);
                case PACKAGE_CONTEXT -> readFromPackageContext(context);
                case DIRECT_PREFERENCES -> readFromDirectPreferences(context);
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Reads config from remote preferences (Xposed API).
     */
    private static Config readFromRemotePreferences() {
        SharedPreferences remote = remotePreferences;
        if (remote == null) {
            return null;
        }

        if (remote.contains(PRIVATE_ENABLED) || remote.contains(GROUP_ENABLED)) {
            return new Config(
                remote.getBoolean(PRIVATE_ENABLED, true),
                remote.getBoolean(GROUP_ENABLED, false)
            );
        }

        return null;
    }

    /**
     * Reads config from content provider.
     */
    private static Config readFromContentProvider(Context context) {
        if (context == null) {
            return null;
        }

        try (Cursor cursor = context.getContentResolver().query(
                CONFIG_URI, null, null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int privateIndex = cursor.getColumnIndex(PRIVATE_ENABLED);
                int groupIndex = cursor.getColumnIndex(GROUP_ENABLED);

                if (privateIndex >= 0 || groupIndex >= 0) {
                    boolean privateEnabled = privateIndex < 0 || cursor.getInt(privateIndex) != 0;
                    boolean groupEnabled = groupIndex >= 0 && cursor.getInt(groupIndex) != 0;
                    return new Config(privateEnabled, groupEnabled);
                }
            }
        }

        return null;
    }

    /**
     * Reads config from module's package context.
     */
    private static Config readFromPackageContext(Context context) {
        if (context == null) {
            return null;
        }

        final Context moduleContext;
        try {
            moduleContext = context.createPackageContext(
                "com.example.qqprivatenotify",
                Context.CONTEXT_IGNORE_SECURITY
            );
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }

        SharedPreferences prefs = moduleContext.getSharedPreferences(PREFS_NAME, 0);
        return new Config(
            prefs.getBoolean(PRIVATE_ENABLED, true),
            prefs.getBoolean(GROUP_ENABLED, false)
        );
    }

    /**
     * Reads config from direct preferences access.
     */
    private static Config readFromDirectPreferences(Context context) {
        if (context == null) {
            return null;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return new Config(
            prefs.getBoolean(PRIVATE_ENABLED, true),
            prefs.getBoolean(GROUP_ENABLED, false)
        );
    }

    /**
     * Enumeration of config reading methods in order of preference.
     */
    private enum ConfigReadMethod {
        REMOTE_PREFERENCES,
        CONTENT_PROVIDER,
        PACKAGE_CONTEXT,
        DIRECT_PREFERENCES
    }

    /**
     * Immutable configuration data class.
     */
    private static final class Config {
        final boolean privateEnabled;
        final boolean groupEnabled;

        Config(boolean privateEnabled, boolean groupEnabled) {
            this.privateEnabled = privateEnabled;
            this.groupEnabled = groupEnabled;
        }

        @Override
        public String toString() {
            return "Config{private=" + privateEnabled + ", group=" + groupEnabled + "}";
        }
    }
}
