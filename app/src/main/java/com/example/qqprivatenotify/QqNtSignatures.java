package com.example.qqprivatenotify;

/**
 * Contains method signatures and type names for QQ NT notification system.
 * Used to identify and hook the correct methods at runtime.
 */
final class QqNtSignatures {

    // QQ NT class names
    static final String APP_RUNTIME = "mqq.app.AppRuntime";
    static final String RECENT_CONTACT_INFO =
        "com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo";
    static final String NOTIFICATION_COMMON_INFO =
        "com.tencent.qqnt.kernel.nativeinterface.NotificationCommonInfo";
    static final String MSG_NOTIFY_ITEM =
        "com.tencent.qqnt.kernel.nativeinterface.MsgNotifyItem";
    static final String FACADE = "com.tencent.qqnt.notification.NotificationFacade";
    static final String TRACKER = "com.tencent.qqnt.notification.trace.INotifyTracker";
    static final String SETTINGS = "com.tencent.qqnt.global.settings.notification.a";

    private QqNtSignatures() {
        throw new AssertionError("No instances");
    }

    /**
     * Identifies the parameter index containing recent contact or message info.
     * @param returnType Method return type
     * @param params Method parameter types
     * @return Index of the info parameter, or -1 if not a match
     */
    static int recentArgumentIndex(String returnType, String[] params) {
        // Must be void return and have at least 3 parameters starting with AppRuntime
        if (!"void".equals(returnType) || params == null || params.length < 3
                || !APP_RUNTIME.equals(params[0])) {
            return -1;
        }

        // Pattern 1: (AppRuntime, MsgNotifyItem, boolean)
        if (params.length == 3
                && MSG_NOTIFY_ITEM.equals(params[1])
                && "boolean".equals(params[2])) {
            return 1;
        }

        // Pattern 2: (AppRuntime, RecentContactInfo, NotificationCommonInfo, boolean)
        if (params.length == 4
                && RECENT_CONTACT_INFO.equals(params[1])
                && NOTIFICATION_COMMON_INFO.equals(params[2])
                && "boolean".equals(params[3])) {
            return 1;
        }

        // Pattern 3: (AppRuntime, Object, NotificationCommonInfo, RecentContactInfo, [boolean])
        if ((params.length == 4 || params.length == 5)
                && isObjectType(params[1])
                && NOTIFICATION_COMMON_INFO.equals(params[2])
                && RECENT_CONTACT_INFO.equals(params[3])
                && (params.length == 4 || "boolean".equals(params[4]))) {
            return 3;
        }

        return -1;
    }

    /**
     * Identifies synthetic builder methods that construct notifications.
     * @param returnType Method return type
     * @param facadeName The declaring class name
     * @param params Method parameter types
     * @return Index of the builder parameter, or -1 if not a match
     */
    static int syntheticBuilderArgumentIndex(String returnType, String facadeName, String[] params) {
        // Must return a builder type and be declared in NotificationFacade
        if (!FACADE.equals(facadeName)
                || !(FACADE + "$a$a").equals(returnType)
                || params == null
                || (params.length != 6 && params.length != 7)
                || !facadeName.equals(params[0])
                || !APP_RUNTIME.equals(params[1])) {
            return -1;
        }

        // Pattern 1: Builder(Facade, AppRuntime, MsgNotifyItem, boolean, Tracker, Settings)
        if (params.length == 6
                && MSG_NOTIFY_ITEM.equals(params[2])
                && "boolean".equals(params[3])
                && TRACKER.equals(params[4])
                && SETTINGS.equals(params[5])) {
            return 2;
        }

        // Pattern 2: Builder(Facade, AppRuntime, RecentContactInfo, NotificationCommonInfo,
        //                    boolean, Tracker, Settings)
        if (params.length == 7
                && RECENT_CONTACT_INFO.equals(params[2])
                && NOTIFICATION_COMMON_INFO.equals(params[3])
                && "boolean".equals(params[4])
                && TRACKER.equals(params[5])
                && SETTINGS.equals(params[6])) {
            return 2;
        }

        return -1;
    }

    /**
     * Identifies methods that post notifications directly.
     * @param returnType Method return type
     * @param params Method parameter types
     * @return Index of the Notification parameter, or -1 if not a match
     */
    static int postNotificationArgumentIndex(String returnType, String[] params) {
        if (!"void".equals(returnType) || params == null) {
            return -1;
        }

        // Pattern: void method(String, Notification, int)
        if (params.length == 3
                && "java.lang.String".equals(params[0])
                && "android.app.Notification".equals(params[1])
                && "int".equals(params[2])) {
            return 1;
        }

        return -1;
    }

    /**
     * Checks if a type name represents an object (not primitive or array).
     * @param type The type name to check
     * @return true if it's an object type
     */
    private static boolean isObjectType(String type) {
        if (type == null || type.isEmpty() || type.startsWith("[") || type.endsWith("[]")) {
            return false;
        }

        // Check if it's a primitive type
        return switch (type) {
            case "void", "boolean", "byte", "char", "short", "int", "long", "float", "double" -> false;
            default -> true;
        };
    }
}
