package com.example.qqprivatenotify;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks notification objects and their associated conversation types.
 * Uses ThreadLocal to propagate type information through nested calls,
 * and WeakHashMap to remember posted notifications.
 */
final class NotificationTrace {

    /**
     * Functional interface for work that may throw exceptions.
     */
    interface Work {
        Object run() throws Throwable;
    }

    // Thread-local storage for current conversation type
    private final ThreadLocal<Integer> currentType = new ThreadLocal<>();

    // Thread-local storage for current conversation IDs
    private final ThreadLocal<Set<String>> currentIds = new ThreadLocal<>();

    // Weak references to notification objects and their types
    private final Map<Object, Integer> postedTypes =
        Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Executes work within a conversation type context.
     * @param type The conversation type (PRIVATE, GROUP, etc.)
     * @param work The work to execute
     * @return The result of the work
     */
    Object during(int type, Work work) throws Throwable {
        return during(type, null, work);
    }

    /**
     * Executes work within a conversation type context with IDs.
     * @param type The conversation type
     * @param ids Set of conversation IDs
     * @param work The work to execute
     * @return The result of the work
     */
    Object during(int type, Set<String> ids, Work work) throws Throwable {
        Integer previousType = currentType.get();
        Set<String> previousIds = currentIds.get();

        // Set current context
        currentType.set(type);
        if (ids == null || ids.isEmpty()) {
            currentIds.remove();
        } else {
            currentIds.set(ids);
        }

        try {
            return work.run();
        } finally {
            // Restore previous context
            if (previousType == null) {
                currentType.remove();
            } else {
                currentType.set(previousType);
            }

            if (previousIds == null) {
                currentIds.remove();
            } else {
                currentIds.set(previousIds);
            }
        }
    }

    /**
     * Gets the conversation type for a notification.
     * First checks ThreadLocal context, then falls back to remembered types.
     * @param notification The notification object
     * @return The conversation type, or 0 if unknown
     */
    int typeFor(Object notification) {
        // Check current thread context first
        Integer type = currentType.get();
        if (type != null) {
            return type;
        }

        // Fall back to remembered type
        if (notification == null) {
            return 0;
        }

        Integer saved = postedTypes.get(notification);
        return saved == null ? 0 : saved;
    }

    /**
     * Gets the conversation IDs for a notification.
     * @param notification The notification object (unused currently)
     * @return Set of conversation IDs, or empty set
     */
    Set<String> idsFor(Object notification) {
        Set<String> ids = currentIds.get();
        return ids == null ? Collections.emptySet() : ids;
    }

    /**
     * Remembers a notification object and its current type.
     * @param notification The notification to remember
     */
    void remember(Object notification) {
        Integer type = currentType.get();
        if (notification != null && type != null) {
            postedTypes.put(notification, type);
        }
    }

    /**
     * Converts NT chat type to classification constant.
     * @param type The NT chat type value
     * @return ConversationClassifier constant (PRIVATE or GROUP)
     */
    static int classification(int type) {
        if (type == 2) {
            return ConversationClassifier.GROUP;
        }
        if (type == 1 || type == 100) {
            return ConversationClassifier.PRIVATE;
        }
        return 0;
    }

    /**
     * Clears all remembered notification types.
     * Useful for testing or memory management.
     */
    void clear() {
        synchronized (postedTypes) {
            postedTypes.clear();
        }
    }

    /**
     * Gets the number of remembered notifications.
     * @return Number of tracked notifications
     */
    int size() {
        synchronized (postedTypes) {
            return postedTypes.size();
        }
    }
}
