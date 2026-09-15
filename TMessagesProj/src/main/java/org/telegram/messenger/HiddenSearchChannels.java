package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;

/**
 * Каналы, скрытые из результатов поиска.
 *
 * Список общий для всех аккаунтов, хранится в "mainconfig" рядом с настройками SharedConfig.
 * Канал остаётся в списке чатов и открывается как обычно — прячется только выдача поиска.
 */
public class HiddenSearchChannels {

    private static final String PREFS = "mainconfig";
    private static final String KEY_IDS = "hiddenSearchChannelIds";
    private static final String KEY_USERNAMES = "hiddenSearchChannelUsernames";

    private static final HashSet<Long> ids = new HashSet<>();
    private static final HashSet<String> usernames = new HashSet<>();
    private static boolean loaded;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void load() {
        if (loaded || ApplicationLoader.applicationContext == null) {
            return;
        }
        loaded = true;
        ids.clear();
        usernames.clear();
        final SharedPreferences preferences = prefs();
        final String raw = preferences.getString(KEY_IDS, "");
        if (!TextUtils.isEmpty(raw)) {
            for (String part : raw.split(",")) {
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (Exception ignore) {
                }
            }
        }
        usernames.addAll(preferences.getStringSet(KEY_USERNAMES, Collections.emptySet()));
    }

    private static void save() {
        prefs().edit()
                .putString(KEY_IDS, TextUtils.join(",", ids))
                .putStringSet(KEY_USERNAMES, new HashSet<>(usernames))
                .apply();
    }

    public static synchronized boolean isHidden(long chatId) {
        load();
        if (ids.isEmpty()) {
            return false;
        }
        return ids.contains(chatId < 0 ? -chatId : chatId);
    }

    public static synchronized boolean isHidden(TLObject obj) {
        load();
        if (ids.isEmpty() && usernames.isEmpty()) {
            return false;
        }
        if (obj instanceof TLRPC.Chat) {
            final TLRPC.Chat chat = (TLRPC.Chat) obj;
            if (ids.contains(chat.id)) {
                return true;
            }
            return hasHiddenUsername(chat);
        }
        return false;
    }

    public static synchronized boolean isHidden(Object obj) {
        return obj instanceof TLObject && isHidden((TLObject) obj);
    }

    private static boolean hasHiddenUsername(TLRPC.Chat chat) {
        if (usernames.isEmpty()) {
            return false;
        }
        if (!TextUtils.isEmpty(chat.username) && usernames.contains(lower(chat.username))) {
            return true;
        }
        if (chat.usernames != null) {
            for (int i = 0; i < chat.usernames.size(); ++i) {
                final TLRPC.TL_username u = chat.usernames.get(i);
                if (u != null && u.active && !TextUtils.isEmpty(u.username) && usernames.contains(lower(u.username))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    public static synchronized void add(TLRPC.Chat chat) {
        if (chat == null) {
            return;
        }
        load();
        ids.add(chat.id);
        if (!TextUtils.isEmpty(chat.username)) {
            usernames.add(lower(chat.username));
        }
        if (chat.usernames != null) {
            for (int i = 0; i < chat.usernames.size(); ++i) {
                final TLRPC.TL_username u = chat.usernames.get(i);
                if (u != null && !TextUtils.isEmpty(u.username)) {
                    usernames.add(lower(u.username));
                }
            }
        }
        save();
    }

    public static synchronized void remove(long chatId) {
        load();
        final long id = chatId < 0 ? -chatId : chatId;
        if (!ids.remove(id)) {
            return;
        }
        // юзернеймы удаляемого канала больше никому не принадлежат — пересобираем набор
        final TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(id);
        if (chat != null) {
            if (!TextUtils.isEmpty(chat.username)) {
                usernames.remove(lower(chat.username));
            }
            if (chat.usernames != null) {
                for (int i = 0; i < chat.usernames.size(); ++i) {
                    final TLRPC.TL_username u = chat.usernames.get(i);
                    if (u != null && !TextUtils.isEmpty(u.username)) {
                        usernames.remove(lower(u.username));
                    }
                }
            }
        }
        save();
    }

    public static synchronized ArrayList<Long> getIds() {
        load();
        return new ArrayList<>(ids);
    }

    public static synchronized boolean isEmpty() {
        load();
        return ids.isEmpty();
    }
}
