package com.example.qqprivatenotify;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

/**
 * Classifies QQ conversations as private chats or group chats.
 * Supports both legacy UIN types and modern NT chat types.
 */
final class ConversationClassifier {
    static final int PRIVATE = 1;
    static final int GROUP = 2;
    static final int UNKNOWN = 0;

    // Maximum depth for nested bundle traversal to prevent infinite loops
    private static final int MAX_BUNDLE_DEPTH = 3;

    // Keys used by QQ to identify conversation types
    static final String[] CHAT_TYPE_KEYS = {
        "uintype", "uin_type",           // Legacy UIN type
        "chatType", "chat_type"          // Modern NT chat type
    };

    static final String[] GROUP_INDICATOR_KEYS = {
        "android.isGroupConversation",
        "is_group", "isGroup", "is_group_chat"
    };

    private ConversationClassifier() {
        throw new AssertionError("No instances");
    }

    /**
     * Classifies a conversation based on metadata fields.
     * @param fields Map of metadata key-value pairs
     * @return Classification bitmask (PRIVATE, GROUP, or combination)
     */
    static int classify(Map<String, ?> fields) {
        if (fields == null || fields.isEmpty()) {
            return UNKNOWN;
        }

        int result = UNKNOWN;

        // Check legacy UIN type (0=private, 1=group, 3000=group)
        result |= classifyLegacyUinType(fields);

        // Check modern NT chat type (1/100=private, 2=group)
        result |= classifyModernChatType(fields);

        // Check group indicator flags
        result |= classifyGroupIndicators(fields);

        return result;
    }

    /**
     * Classifies based on legacy UIN type.
     */
    private static int classifyLegacyUinType(Map<String, ?> fields) {
        for (String key : new String[]{"uintype", "uin_type"}) {
            String type = scalarToString(fields.get(key));
            if ("0".equals(type)) {
                return PRIVATE;
            } else if ("1".equals(type) || "3000".equals(type)) {
                return GROUP;
            }
        }
        return UNKNOWN;
    }

    /**
     * Classifies based on modern NT chat type.
     */
    private static int classifyModernChatType(Map<String, ?> fields) {
        for (String key : new String[]{"chatType", "chat_type"}) {
            String type = scalarToString(fields.get(key));
            if ("1".equals(type) || "100".equals(type)) {
                return PRIVATE;
            } else if ("2".equals(type)) {
                return GROUP;
            }
        }
        return UNKNOWN;
    }

    /**
     * Classifies based on group indicator flags.
     */
    private static int classifyGroupIndicators(Map<String, ?> fields) {
        for (String key : GROUP_INDICATOR_KEYS) {
            String value = scalarToString(fields.get(key));
            if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
                return GROUP;
            } else if (!"android.isGroupConversation".equals(key)
                    && ("false".equalsIgnoreCase(value) || "0".equals(value))) {
                return PRIVATE;
            }
        }
        return UNKNOWN;
    }

    /**
     * Determines if a notification should be blocked based on its classification.
     * @param classification The conversation classification
     * @return true if the notification should be blocked
     */
    static boolean shouldBlock(int classification) {
        // Only block pure group notifications
        // Mixed/contradictory metadata and unknown conversations are allowed through
        return classification == GROUP;
    }

    /**
     * Classifies a notification object.
     * @param notification The notification to classify
     * @return Classification bitmask
     */
    static int classifyNotification(Notification notification) {
        if (notification == null) {
            return UNKNOWN;
        }

        int result = classifyBundle(notification.extras, 0);

        // Fallback: check for conversation title (indicates group chat)
        if (result == UNKNOWN && notification.extras != null) {
            CharSequence title = notification.extras.getCharSequence(
                Notification.EXTRA_CONVERSATION_TITLE);
            if (title != null && title.length() > 0) {
                result = GROUP;
            }
        }

        return result;
    }

    /**
     * Recursively classifies a bundle by examining its contents.
     * @param bundle The bundle to examine
     * @param depth Current recursion depth
     * @return Classification bitmask
     */
    @SuppressWarnings("deprecation")
    static int classifyBundle(Bundle bundle, int depth) {
        if (bundle == null || depth > MAX_BUNDLE_DEPTH) {
            return UNKNOWN;
        }

        // Extract relevant fields into a map
        Map<String, Object> fields = new HashMap<>();
        for (String key : CHAT_TYPE_KEYS) {
            if (bundle.containsKey(key)) {
                fields.put(key, bundle.get(key));
            }
        }
        for (String key : GROUP_INDICATOR_KEYS) {
            if (bundle.containsKey(key)) {
                fields.put(key, bundle.get(key));
            }
        }

        int result = classify(fields);

        // Recursively check nested bundles and intents
        result |= classifyNestedObjects(bundle, depth);

        return result;
    }

    /**
     * Searches for nested bundles and intents that may contain classification info.
     */
    private static int classifyNestedObjects(Bundle bundle, int depth) {
        int result = UNKNOWN;

        // Common keys that might contain nested data
        String[] nestedKeys = {
            "intent", "extras", "extra", "data",
            "param", "params", "contact", "session", "bundle"
        };

        for (String key : nestedKeys) {
            Object nested = bundle.get(key);
            if (nested instanceof Bundle) {
                result |= classifyBundle((Bundle) nested, depth + 1);
            } else if (nested instanceof Intent) {
                result |= classifyBundle(((Intent) nested).getExtras(), depth + 1);
            }
        }

        return result;
    }

    /**
     * Converts various scalar types to strings for comparison.
     * @param value The value to convert
     * @return String representation, or empty string if not a scalar
     */
    private static String scalarToString(Object value) {
        if (value instanceof Integer || value instanceof Long
                || value instanceof Boolean || value instanceof String) {
            return value.toString();
        }
        return "";
    }
}
