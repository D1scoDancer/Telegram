package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * "Telegram Focus" fork settings, shared across all accounts.
 * Stored in "mainconfig" next to {@link HiddenSearchChannels}.
 */
public class FocusSettings {

    private static final String PREFS = "mainconfig";
    private static final String KEY_HIDE_STORIES = "focus_hide_stories";

    private static Boolean storiesHidden;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Whether the stories bar above the chat list and the hidden-stories ring on the Archive row are hidden. */
    public static boolean isStoriesHidden() {
        if (storiesHidden == null) {
            storiesHidden = ApplicationLoader.applicationContext != null && prefs().getBoolean(KEY_HIDE_STORIES, false);
        }
        return storiesHidden;
    }

    public static void setStoriesHidden(boolean hidden) {
        storiesHidden = hidden;
        prefs().edit().putBoolean(KEY_HIDE_STORIES, hidden).apply();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.isValidAccount(a)) {
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.storiesUpdated);
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
            }
        }
    }
}
