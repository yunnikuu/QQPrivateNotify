package com.example.qqprivatenotify;

import android.app.Application;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedModule;

/**
 * Main Xposed module for filtering QQ notifications.
 * Hooks into QQ's notification system to selectively block group/private chat notifications.
 */
public final class QQPrivateNotify extends XposedModule {

    private static final String QQ_PACKAGE = "com.tencent.mobileqq";
    private static final String TAG = "QQPrivateNotify";
    private static final String MODULE_VERSION = "1.2";

    // Maximum number of PendingIntents to track (LRU cache)
    private static final int MAX_CONVERSATION_CACHE = 512;

    // Tracks PendingIntent -> conversation type mappings
    private final Map<PendingIntent, Integer> conversationCache = new LinkedHashMap<PendingIntent, Integer>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<PendingIntent, Integer> entry) {
            return size() > MAX_CONVERSATION_CACHE;
        }
    };

    // State flags
    private boolean isQQProcess;
    private boolean systemHooksInstalled;
    private String processName;
    private volatile Context hostContext;

    // NT-specific hooks
    private final Set<Method> ntHookedMethods = new HashSet<>();

    // Trace utilities
    private final NotificationTrace notificationTrace = new NotificationTrace();

    // Statistics
    private final AtomicInteger totalDecisions = new AtomicInteger();
    private final AtomicInteger readFailures = new AtomicInteger();
    private final AtomicInteger unknownNotifications = new AtomicInteger();

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        isQQProcess = QQ_PACKAGE.equals(processName) || processName.startsWith(QQ_PACKAGE + ":");

        // Initialize remote preferences for config access
        try {
            WhitelistConfig.setRemotePreferences(getRemotePreferences("settings"));
        } catch (Throwable e) {
            log(Log.WARN, TAG, "Failed to init remote preferences: " + e.getMessage());
        }

        if (!isQQProcess) {
            return;
        }

        log(Log.INFO, TAG, "Module v" + MODULE_VERSION + " loaded in process=" + processName
            + " api=" + getApiVersion());

        // Install system-level hooks
        installSystemHooks();

        // Install lifecycle hooks to catch NT hooks at the right time
        installLifecycleHooks();
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!isQQProcess || !QQ_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        // Try to install NT hooks when package is ready
        installNtHooks(param.getClassLoader(), "package-ready");
    }

    /**
     * Installs hooks into application lifecycle to catch NT classes at load time.
     */
    private void installLifecycleHooks() {
        // Hook Application.attach()
        try {
            hook(Application.class.getDeclaredMethod("attach", Context.class))
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Context context = (Context) chain.getArg(0);

                    if (QQ_PACKAGE.equals(context.getPackageName())) {
                        hostContext = context;
                        installNtHooks(context.getClassLoader(), "attach");
                    }

                    return result;
                });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            log(Log.WARN, TAG, "Failed to hook Application.attach: " + e.getMessage());
        }

        // Hook Instrumentation.callApplicationOnCreate()
        try {
            hook(Instrumentation.class.getDeclaredMethod("callApplicationOnCreate", Application.class))
                .intercept(chain -> {
                    Application app = (Application) chain.getArg(0);

                    // Install hooks before onCreate
                    if (QQ_PACKAGE.equals(app.getPackageName())) {
                        hostContext = app;
                        installNtHooks(app.getClassLoader(), "application-create");
                    }

                    // Call original onCreate
                    Object result = chain.proceed();

                    // Install hooks after onCreate (in case classes are loaded lazily)
                    if (QQ_PACKAGE.equals(app.getPackageName())) {
                        installNtHooks(app.getClassLoader(), "application-created");
                    }

                    return result;
                });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            log(Log.WARN, TAG, "Failed to hook Instrumentation: " + e.getMessage());
        }
    }

    /**
     * Installs hooks into QQ NT notification system.
     * @param loader ClassLoader to use
     * @param stage Description of when this is being called
     */
    private synchronized void installNtHooks(ClassLoader loader, String stage) {
        try {
            Class<?> facadeClass = Class.forName(QqNtSignatures.FACADE, false, loader);

            // Scan all methods in NotificationFacade
            for (Method method : facadeClass.getDeclaredMethods()) {
                // Skip if already hooked
                if (ntHookedMethods.contains(method)) {
                    continue;
                }

                // Get parameter type names
                Class<?>[] paramTypes = method.getParameterTypes();
                String[] paramNames = new String[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    paramNames[i] = paramTypes[i].getName();
                }

                String returnTypeName = method.getReturnType().getName();

                // Check if this is a recent contact notification method
                int recentIndex = QqNtSignatures.recentArgumentIndex(returnTypeName, paramNames);

                // Check if this is a builder method
                int builderIndex = QqNtSignatures.syntheticBuilderArgumentIndex(
                    returnTypeName, facadeClass.getName(), paramNames);

                // Check if this is a post notification method
                int postIndex = QqNtSignatures.postNotificationArgumentIndex(returnTypeName, paramNames);

                // Install appropriate hook
                if (builderIndex >= 0 && Modifier.isStatic(method.getModifiers())) {
                    installBuilderHook(method, builderIndex, paramNames[builderIndex]);
                } else if (postIndex >= 0) {
                    installPostHook(method, postIndex);
                } else if (recentIndex >= 0) {
                    installRecentContactHook(method, recentIndex, paramNames[recentIndex]);
                }
            }

            log(Log.INFO, TAG, "NT hooks installed: count=" + ntHookedMethods.size()
                + " stage=" + stage + " process=" + processName);

        } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
            log(Log.WARN, TAG, "NT hooks unavailable at stage=" + stage
                + " error=" + e.getClass().getSimpleName());
        }
    }

    /**
     * Hooks a method that receives recent contact notifications.
     */
    private void installRecentContactHook(Method method, int argIndex, String typeName) {
        boolean isMsgNotifyItem = typeName.endsWith(".MsgNotifyItem");

        try {
            hook(method).intercept(chain -> {
                Object source = chain.getArg(argIndex);
                int chatType = readChatTypeSafely(source, isMsgNotifyItem);

                boolean shouldBlock = chatType == ConversationClassifier.GROUP
                    && !WhitelistConfig.shouldShowSource(hostContext, source, chatType);

                reportDecision("nt-" + (isMsgNotifyItem ? "item" : "recent"),
                    chatType, shouldBlock);

                // Return null to block, or proceed normally
                return shouldBlock ? null : chain.proceed();
            });

            ntHookedMethods.add(method);
            log(Log.DEBUG, TAG, "Hooked NT method: " + method.getName()
                + " type=" + (isMsgNotifyItem ? "item" : "recent"));

        } catch (RuntimeException | LinkageError e) {
            log(Log.WARN, TAG, "Failed to hook NT method: " + e.getMessage());
        }
    }

    /**
     * Hooks a builder method to capture context for nested calls.
     */
    private void installBuilderHook(Method method, int argIndex, String typeName) {
        boolean isMsgNotifyItem = QqNtSignatures.MSG_NOTIFY_ITEM.equals(typeName);

        try {
            hook(method).intercept(chain -> {
                Object source = chain.getArg(argIndex);
                int chatType = readChatTypeSafely(source, isMsgNotifyItem);

                // Collect conversation IDs (for future per-conversation filtering)
                Set<String> ids = new HashSet<>();
                WhitelistConfig.collectObjectIds(source, ids);

                // Execute the builder within this context
                return notificationTrace.during(chatType, ids, chain::proceed);
            });

            ntHookedMethods.add(method);
            log(Log.DEBUG, TAG, "Hooked NT builder: " + method.getName());

        } catch (RuntimeException | LinkageError e) {
            log(Log.WARN, TAG, "Failed to hook NT builder: " + e.getMessage());
        }
    }

    /**
     * Hooks a direct notification post method.
     */
    private void installPostHook(Method method, int notificationIndex) {
        try {
            hook(method).intercept(chain -> {
                Notification notification = (Notification) chain.getArg(notificationIndex);

                // Remember this notification's type
                notificationTrace.remember(notification);

                // Check if should block
                return interceptNotificationPost(notification, "nt-post", chain::proceed);
            });

            ntHookedMethods.add(method);
            log(Log.DEBUG, TAG, "Hooked NT post: " + method.getName());

        } catch (RuntimeException | LinkageError e) {
            log(Log.WARN, TAG, "Failed to hook NT post: " + e.getMessage());
        }
    }

    /**
     * Safely reads chat type from an object, handling exceptions.
     */
    private int readChatTypeSafely(Object source, boolean isMsgNotifyItem) {
        try {
            return NtChatTypeReader.read(source, isMsgNotifyItem);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            int count = readFailures.incrementAndGet();
            if (count <= 20) {
                log(Log.WARN, TAG, "Failed to read chat type: " + e.getMessage());
            }
            return 0;
        }
    }

    /**
     * Installs hooks into Android's NotificationManager.
     */
    private void installSystemHooks() {
        if (systemHooksInstalled) {
            return;
        }
        systemHooksInstalled = true;

        // Hook PendingIntent creation to capture conversation metadata
        hookPendingIntentCreation();

        // Hook NotificationManager.notify methods
        int hookCount = 0;
        for (Method method : NotificationManager.class.getDeclaredMethods()) {
            String name = method.getName();

            // Only hook notify methods
            if (!(name.equals("notify") || name.equals("notifyAsUser") || name.equals("notifyAsPackage"))
                    || method.getReturnType() != void.class) {
                continue;
            }

            // Find Notification parameter
            int notificationIndex = indexOfType(method.getParameterTypes(), Notification.class);
            if (notificationIndex < 0) {
                continue;
            }

            try {
                hook(method).intercept(chain -> {
                    Notification notification = (Notification) chain.getArg(notificationIndex);
                    return interceptNotificationPost(notification, "android-notify", chain::proceed);
                });

                hookCount++;
            } catch (RuntimeException | LinkageError e) {
                log(Log.WARN, TAG, "Failed to hook NotificationManager." + name);
            }
        }

        log(Log.INFO, TAG, "System hooks installed: NotificationManager hooks=" + hookCount);
    }

    /**
     * Hooks PendingIntent creation to capture Intent metadata.
     */
    private void hookPendingIntentCreation() {
        for (Method method : PendingIntent.class.getDeclaredMethods()) {
            String name = method.getName();

            // Only hook static factory methods
            if (!Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != PendingIntent.class
                    || !(name.equals("getActivity") || name.equals("getActivities")
                    || name.equals("getBroadcast") || name.equals("getService")
                    || name.equals("getForegroundService"))) {
                continue;
            }

            Class<?>[] params = method.getParameterTypes();
            int singleIntent = indexOfType(params, Intent.class);
            int multipleIntents = indexOfType(params, Intent[].class);
            int intentIndex = Math.max(singleIntent, multipleIntents);

            // Must have Intent followed by flags
            if (intentIndex < 0 || intentIndex + 1 >= params.length
                    || params[intentIndex + 1] != int.class) {
                continue;
            }

            try {
                hook(method).intercept(chain -> {
                    Object result = chain.proceed();

                    if (!(result instanceof PendingIntent)) {
                        return result;
                    }

                    try {
                        // Classify the intent(s)
                        Object intentValue = chain.getArg(intentIndex);
                        int classification = classifyIntentValue(intentValue);
                        int flags = (Integer) chain.getArg(intentIndex + 1);

                        // Cache the classification if this creates a new PendingIntent
                        if ((flags & PendingIntent.FLAG_NO_CREATE) == 0) {
                            synchronized (conversationCache) {
                                PendingIntent pi = (PendingIntent) result;
                                if ((flags & PendingIntent.FLAG_UPDATE_CURRENT) != 0
                                        || !conversationCache.containsKey(pi)) {
                                    conversationCache.put(pi, classification);
                                }
                            }
                        }
                    } catch (RuntimeException | LinkageError ignored) {
                        // Best effort classification
                    }

                    return result;
                });
            } catch (RuntimeException | LinkageError e) {
                log(Log.WARN, TAG, "Failed to hook PendingIntent." + name);
            }
        }
    }

    /**
     * Classifies an Intent or Intent[] value.
     */
    private int classifyIntentValue(Object value) {
        int classification = 0;

        if (value instanceof Intent) {
            classification = ConversationClassifier.classifyBundle(
                ((Intent) value).getExtras(), 0);
        } else if (value instanceof Intent[]) {
            for (Intent intent : (Intent[]) value) {
                if (intent != null) {
                    classification |= ConversationClassifier.classifyBundle(
                        intent.getExtras(), 0);
                }
            }
        }

        return classification;
    }

    /**
     * Intercepts a notification post and decides whether to block it.
     */
    private Object interceptNotificationPost(Notification notification, String source,
                                            NotificationTrace.Work original) throws Throwable {
        int classification = 0;

        // Exempt certain notifications from filtering
        boolean exempt = notification == null
            || Notification.CATEGORY_CALL.equals(notification.category)
            || (notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;

        // Classify the notification
        if (!exempt) {
            try {
                classification = classifyNotification(notification);
            } catch (RuntimeException | LinkageError e) {
                int count = readFailures.incrementAndGet();
                if (count <= 20) {
                    log(Log.WARN, TAG, "Failed to classify notification: " + e.getMessage());
                }
            }
        }

        // Check if we should block
        boolean configured = WhitelistConfig.isConfigured(hostContext);
        boolean shouldBlock = !exempt && ConversationClassifier.shouldBlock(classification);

        if (!exempt && configured) {
            shouldBlock = !WhitelistConfig.shouldShow(hostContext, notification,
                classification, notificationTrace.idsFor(notification));
        }

        reportDecision(source, classification, shouldBlock);

        // Log unclassified notifications for debugging
        if (!exempt && !shouldBlock && (classification == 0 || classification == 3)) {
            reportUnknownNotification(source, classification);
        }

        // Block or allow
        return shouldBlock ? null : original.run();
    }

    /**
     * Classifies a notification using all available information.
     */
    private int classifyNotification(Notification notification) {
        // Check trace context first
        int known = NotificationTrace.classification(notificationTrace.typeFor(notification));
        if (known != 0) {
            return known;
        }

        // Classify based on notification metadata
        int classification = ConversationClassifier.classifyNotification(notification);

        // Check conversation title as fallback
        if (classification == 0 && notification.extras != null) {
            CharSequence title = notification.extras.getCharSequence(
                Notification.EXTRA_CONVERSATION_TITLE);
            if (title != null && title.length() > 0) {
                classification = ConversationClassifier.GROUP;
            }
        }

        // Check cached PendingIntent classification
        synchronized (conversationCache) {
            Integer intentType = conversationCache.get(notification.contentIntent);
            if (intentType != null) {
                classification |= intentType;
            }
        }

        return classification;
    }

    /**
     * Reports a filtering decision for debugging.
     */
    private void reportDecision(String source, int type, boolean blocked) {
        int count = totalDecisions.incrementAndGet();

        // Log first 100 decisions, then every 100th
        if (count <= 100 || count % 100 == 0) {
            log(Log.INFO, TAG, "Decision: source=" + source + " type=" + type
                + " blocked=" + blocked + " count=" + count + " process=" + processName);
        }
    }

    /**
     * Reports an unknown notification for debugging.
     */
    private void reportUnknownNotification(String source, int classification) {
        int count = unknownNotifications.incrementAndGet();

        // Limit logging to avoid spam
        if (count > 30 && count % 100 != 0) {
            return;
        }

        // Build caller stack trace
        StringBuilder callers = new StringBuilder();
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (!frame.getClassName().startsWith("com.tencent.")) {
                continue;
            }

            if (callers.length() > 0) {
                callers.append(" <- ");
            }

            callers.append(frame.getClassName())
                .append('.')
                .append(frame.getMethodName());

            if (callers.length() >= 1400) {
                break;
            }
        }

        log(Log.INFO, TAG, "Unknown notification: source=" + source
            + " classification=" + classification + " count=" + count
            + " process=" + processName + " callers=" + callers);
    }

    /**
     * Finds the index of a type in an array of types.
     */
    private static int indexOfType(Class<?>[] types, Class<?> target) {
        for (int i = 0; i < types.length; i++) {
            if (types[i] == target) {
                return i;
            }
        }
        return -1;
    }
}
