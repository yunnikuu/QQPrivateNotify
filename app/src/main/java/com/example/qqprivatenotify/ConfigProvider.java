package com.example.qqprivatenotify;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Content Provider for exposing module configuration to the hooked QQ process.
 * This allows the QQ process to read settings without needing file access permissions.
 */
public final class ConfigProvider extends ContentProvider {
    private static final String PREFS_NAME = "settings";

    // Column names for the configuration cursor
    private static final String COL_PRIVATE_ENABLED = WhitelistConfig.PRIVATE_ENABLED;
    private static final String COL_GROUP_ENABLED = WhitelistConfig.GROUP_ENABLED;

    // Legacy column names for backward compatibility
    private static final String COL_WHITELIST_MODE = "whitelist_mode";
    private static final String COL_PRIVATE_IDS = "private_ids";
    private static final String COL_GROUP_IDS = "group_ids";
    private static final String COL_PRIVATE_MODE = "private_whitelist";
    private static final String COL_GROUP_MODE = "group_whitelist";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                       String[] selectionArgs, String sortOrder) {
        if (getContext() == null) {
            return null;
        }

        // Create cursor with all configuration columns
        MatrixCursor cursor = new MatrixCursor(new String[]{
            COL_WHITELIST_MODE,
            COL_PRIVATE_IDS,
            COL_GROUP_IDS,
            COL_PRIVATE_MODE,
            COL_GROUP_MODE,
            COL_PRIVATE_ENABLED,
            COL_GROUP_ENABLED
        });

        // Read preferences
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);

        // Get current settings
        boolean privateEnabled = prefs.getBoolean(COL_PRIVATE_ENABLED, true);
        boolean groupEnabled = prefs.getBoolean(COL_GROUP_ENABLED, false);

        // Legacy settings (for backward compatibility)
        boolean legacyEnabled = prefs.getBoolean(COL_WHITELIST_MODE, true);
        String privateIds = prefs.getString(COL_PRIVATE_IDS, "");
        String groupIds = prefs.getString(COL_GROUP_IDS, "");
        boolean privateLegacyMode = prefs.getBoolean(COL_PRIVATE_MODE, legacyEnabled);
        boolean groupLegacyMode = prefs.getBoolean(COL_GROUP_MODE, legacyEnabled);

        // Add row with all settings
        cursor.addRow(new Object[]{
            legacyEnabled ? 1 : 0,
            privateIds,
            groupIds,
            privateLegacyMode ? 1 : 0,
            groupLegacyMode ? 1 : 0,
            privateEnabled ? 1 : 0,
            groupEnabled ? 1 : 0
        });

        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/config";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert operation not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete operation not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Update operation not supported");
    }
}
