package com.example.qqprivatenotify;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Settings activity with improved Material Design-inspired UI.
 * Allows users to configure notification filtering preferences.
 */
public final class SettingsActivity extends Activity {

    private static final String PREFS_NAME = "settings";

    private SharedPreferences preferences;
    private Switch privateChatSwitch;
    private Switch groupChatSwitch;
    private Button saveButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("QQ 通知过滤设置");

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Create the UI
        ScrollView scrollView = createScrollView();
        setContentView(scrollView);

        // Request insets for edge-to-edge display
        scrollView.requestApplyInsets();

        // Load current settings
        loadSettings();
    }

    /**
     * Creates the main scrollable container.
     */
    private ScrollView createScrollView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // Handle window insets for edge-to-edge display
        scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;

            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }

            view.setPadding(left, top, right, bottom);
            return insets;
        });

        // Add content layout
        LinearLayout contentLayout = createContentLayout();
        scrollView.addView(contentLayout);

        return scrollView;
    }

    /**
     * Creates the main content layout.
     */
    private LinearLayout createContentLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(24), dp(16), dp(16));

        // Add header card
        layout.addView(createHeaderCard());

        // Add spacing
        layout.addView(createVerticalSpacer(dp(16)));

        // Add settings card
        layout.addView(createSettingsCard());

        // Add flexible spacer
        Space spacer = new Space(this);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        layout.addView(spacer, spacerParams);

        // Add save button
        layout.addView(createSaveButton());

        return layout;
    }

    /**
     * Creates the header information card.
     */
    private View createHeaderCard() {
        LinearLayout card = createCard();

        // Title
        TextView title = new TextView(this);
        title.setText("通知过滤设置");
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#212121"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        // Spacing
        card.addView(createVerticalSpacer(dp(12)));

        // Description
        TextView description = new TextView(this);
        description.setText("控制 QQ 消息通知的显示。开启后允许显示通知，关闭后屏蔽通知。\n\n" +
            "注意：设置实时生效，无需重启 QQ。");
        description.setTextSize(14);
        description.setTextColor(Color.parseColor("#757575"));
        description.setLineSpacing(dp(4), 1.0f);
        card.addView(description);

        return card;
    }

    /**
     * Creates the settings card with switches.
     */
    private View createSettingsCard() {
        LinearLayout card = createCard();

        // Private chat section
        card.addView(createSettingSection(
            "私聊消息通知",
            "允许显示私聊消息的通知",
            true
        ));

        // Divider
        card.addView(createDivider());

        // Group chat section
        card.addView(createSettingSection(
            "群聊消息通知",
            "允许显示群聊消息的通知",
            false
        ));

        return card;
    }

    /**
     * Creates a setting section with a switch.
     */
    private View createSettingSection(String title, String description, boolean isPrivate) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setGravity(Gravity.CENTER_VERTICAL);
        section.setPadding(0, dp(12), 0, dp(12));

        // Text container
        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);

        // Title
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(Color.parseColor("#212121"));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        textContainer.addView(titleView);

        // Spacing
        textContainer.addView(createVerticalSpacer(dp(4)));

        // Description
        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextSize(13);
        descView.setTextColor(Color.parseColor("#757575"));
        textContainer.addView(descView);

        // Add text container with weight to push switch to the right
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        section.addView(textContainer, textParams);

        // Switch
        Switch switchView = new Switch(this);
        switchView.setMinimumWidth(dp(60));

        if (isPrivate) {
            privateChatSwitch = switchView;
        } else {
            groupChatSwitch = switchView;
        }

        // Real-time save on toggle
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSettingImmediately(isPrivate, isChecked);
        });

        section.addView(switchView);

        return section;
    }

    /**
     * Creates a material-style card.
     */
    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Add subtle shadow effect (elevation)
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            card.setElevation(dp(2));
            card.setTranslationZ(dp(1));
        }

        // Rounded corners
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setColor(Color.WHITE);
            drawable.setCornerRadius(dp(8));
            card.setBackground(drawable);
            card.setClipToOutline(true);
        }

        return card;
    }

    /**
     * Creates the save button.
     */
    private Button createSaveButton() {
        saveButton = new Button(this);
        saveButton.setText("保存设置");
        saveButton.setTextSize(16);
        saveButton.setTextColor(Color.WHITE);
        saveButton.setTypeface(null, android.graphics.Typeface.BOLD);
        saveButton.setPadding(dp(24), dp(14), dp(24), dp(14));
        saveButton.setAllCaps(false);

        // Button background
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.parseColor("#2196F3"));
        background.setCornerRadius(dp(8));
        saveButton.setBackground(background);

        // Elevation
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            saveButton.setElevation(dp(4));
            saveButton.setStateListAnimator(null);
        }

        // Click handler
        saveButton.setOnClickListener(v -> saveAllSettings());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(16);
        saveButton.setLayoutParams(params);

        return saveButton;
    }

    /**
     * Creates a divider line.
     */
    private View createDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        );
        params.topMargin = dp(4);
        params.bottomMargin = dp(4);
        divider.setLayoutParams(params);
        return divider;
    }

    /**
     * Creates a vertical spacer.
     */
    private View createVerticalSpacer(int height) {
        Space spacer = new Space(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        );
        spacer.setLayoutParams(params);
        return spacer;
    }

    /**
     * Loads current settings from preferences.
     */
    private void loadSettings() {
        boolean privateEnabled = preferences.getBoolean(WhitelistConfig.PRIVATE_ENABLED, true);
        boolean groupEnabled = preferences.getBoolean(WhitelistConfig.GROUP_ENABLED, false);

        privateChatSwitch.setChecked(privateEnabled);
        groupChatSwitch.setChecked(groupEnabled);
    }

    /**
     * Saves a single setting immediately when toggled.
     */
    private void saveSettingImmediately(boolean isPrivate, boolean enabled) {
        String key = isPrivate ? WhitelistConfig.PRIVATE_ENABLED : WhitelistConfig.GROUP_ENABLED;
        preferences.edit().putBoolean(key, enabled).apply();

        // Show brief feedback
        String message = (isPrivate ? "私聊" : "群聊") + "通知已" + (enabled ? "开启" : "关闭");
        showToast(message);
    }

    /**
     * Saves all settings when the save button is clicked.
     */
    private void saveAllSettings() {
        boolean privateEnabled = privateChatSwitch.isChecked();
        boolean groupEnabled = groupChatSwitch.isChecked();

        preferences.edit()
            .putBoolean(WhitelistConfig.PRIVATE_ENABLED, privateEnabled)
            .putBoolean(WhitelistConfig.GROUP_ENABLED, groupEnabled)
            .apply();

        // Update button text temporarily
        saveButton.setText("✓ 已保存");
        saveButton.setEnabled(false);

        // Show success message
        showToast("设置已保存，立即生效");

        // Reset button after 1.5 seconds
        saveButton.postDelayed(() -> {
            saveButton.setText("保存设置");
            saveButton.setEnabled(true);
        }, 1500);
    }

    /**
     * Shows a toast message.
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Converts dp to pixels.
     */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
