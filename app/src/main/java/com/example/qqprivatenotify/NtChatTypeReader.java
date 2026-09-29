package com.example.qqprivatenotify;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads chat type from QQ NT notification objects using reflection.
 * Caches reflection results for better performance.
 */
final class NtChatTypeReader {

    // Cache for reflected fields to avoid repeated lookups
    private static final ConcurrentHashMap<Class<?>, Field> fieldCache = new ConcurrentHashMap<>();

    // Cache for reflected methods to avoid repeated lookups
    private static final ConcurrentHashMap<Class<?>, Method> methodCache = new ConcurrentHashMap<>();

    private NtChatTypeReader() {
        throw new AssertionError("No instances");
    }

    /**
     * Reads the chat type from a notification source object.
     * @param source The notification source (RecentContactInfo or MsgNotifyItem)
     * @param notifyItem True if source is MsgNotifyItem, false if RecentContactInfo
     * @return The chat type integer, or 0 if not found
     */
    static int read(Object source, boolean notifyItem) throws ReflectiveOperationException {
        if (source == null) {
            return 0;
        }

        // For MsgNotifyItem, first extract the msgInfo property
        if (notifyItem) {
            source = readProperty(source, "msgInfo", "getMsgInfo");
            if (source == null) {
                return 0;
            }
        }

        // Read the chatType property
        Object value = readProperty(source, "chatType", "getChatType");
        return value instanceof Integer ? (Integer) value : 0;
    }

    /**
     * Reads a property from an object, trying field access first, then getter method.
     * Results are cached for performance.
     * @param source The object to read from
     * @param fieldName The field name to try
     * @param getterName The getter method name to try
     * @return The property value
     */
    private static Object readProperty(Object source, String fieldName, String getterName)
            throws ReflectiveOperationException {

        Class<?> sourceClass = source.getClass();

        // Try to get field from cache or reflection
        Field field = fieldCache.computeIfAbsent(sourceClass, clazz -> {
            // Search through class hierarchy
            for (Class<?> type = clazz; type != null; type = type.getSuperclass()) {
                try {
                    Field f = type.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                    // Continue to next superclass
                }
            }
            return null;
        });

        // If field exists, use it
        if (field != null) {
            return field.get(source);
        }

        // Otherwise, try getter method (cache it too)
        Method getter = methodCache.computeIfAbsent(sourceClass, clazz -> {
            try {
                Method m = clazz.getMethod(getterName);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Neither field '" + fieldName
                    + "' nor method '" + getterName + "' found", e);
            }
        });

        return getter.invoke(source);
    }

    /**
     * Clears the reflection cache. Useful for testing or when class loaders change.
     */
    static void clearCache() {
        fieldCache.clear();
        methodCache.clear();
    }
}
