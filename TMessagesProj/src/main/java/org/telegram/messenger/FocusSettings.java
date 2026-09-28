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
    private static final String KEY_HIDE_MUTED_COUNTERS = "focus_hide_muted_counters";
    private static final String KEY_HIDE_PREMIUM_PROMO = "focus_hide_premium_promo";

    private static Boolean storiesHidden;
    private static Boolean mutedCountersHidden;
    private static Boolean premiumPromoHidden;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean read(String key) {
        return ApplicationLoader.applicationContext != null && prefs().getBoolean(key, false);
    }

    private static void write(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    private static void postToAllAccounts(int id, Object... args) {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.isValidAccount(a)) {
                NotificationCenter.getInstance(a).postNotificationName(id, args);
            }
        }
    }

    /** Whether the stories bar above the chat list and the hidden-stories ring on the Archive row are hidden. */
    public static boolean isStoriesHidden() {
        if (storiesHidden == null) {
            storiesHidden = read(KEY_HIDE_STORIES);
        }
        return storiesHidden;
    }

    public static void setStoriesHidden(boolean hidden) {
        storiesHidden = hidden;
        write(KEY_HIDE_STORIES, hidden);
        postToAllAccounts(NotificationCenter.storiesUpdated);
        postToAllAccounts(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
    }

    /** Whether the Archive row count is hidden and folder tab counters skip muted chats. Chat rows keep their badges. */
    public static boolean isMutedCountersHidden() {
        if (mutedCountersHidden == null) {
            mutedCountersHidden = read(KEY_HIDE_MUTED_COUNTERS);
        }
        return mutedCountersHidden;
    }

    public static void setMutedCountersHidden(boolean hidden) {
        mutedCountersHidden = hidden;
        write(KEY_HIDE_MUTED_COUNTERS, hidden);
        postToAllAccounts(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.isValidAccount(a)) {
                final MessagesStorage storage = MessagesStorage.getInstance(a);
                storage.getStorageQueue().postRunnable(() -> storage.resetAllUnreadCounters(false));
            }
        }
    }

    /** Whether Premium upsell banners above the chat list and purchase entries in Settings are hidden. */
    public static boolean isPremiumPromoHidden() {
        if (premiumPromoHidden == null) {
            premiumPromoHidden = read(KEY_HIDE_PREMIUM_PROMO);
        }
        return premiumPromoHidden;
    }

    public static void setPremiumPromoHidden(boolean hidden) {
        premiumPromoHidden = hidden;
        write(KEY_HIDE_PREMIUM_PROMO, hidden);
        postToAllAccounts(NotificationCenter.newSuggestionsAvailable);
    }
}
