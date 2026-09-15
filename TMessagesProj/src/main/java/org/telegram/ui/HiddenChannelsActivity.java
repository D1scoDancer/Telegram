package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.HiddenSearchChannels;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Список каналов, скрытых из результатов поиска.
 * Канал остаётся в списке чатов — прячется только выдача поиска.
 */
public class HiddenChannelsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;

    private final ArrayList<ItemInner> oldItems = new ArrayList<>(), items = new ArrayList<>();

    private final static int VIEW_TYPE_HEADER = 0;
    private final static int VIEW_TYPE_CHANNEL = 1;
    private final static int VIEW_TYPE_ADD = 2;
    private final static int VIEW_TYPE_SHADOW = 3;
    private final static int VIEW_TYPE_ADD_FROM_MINE = 4;

    private final static int ID_ADD = -1;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.FocusHiddenChannels));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutAnimation(null);
        listView.setAdapter(adapter = new ListAdapter());
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            final ItemInner item = items.get(position);
            if (item.viewType == VIEW_TYPE_ADD) {
                openAddByUsername();
            } else if (item.viewType == VIEW_TYPE_ADD_FROM_MINE) {
                openChannelPicker();
            } else if (item.viewType == VIEW_TYPE_CHANNEL) {
                confirmRemove(item.chatId, item.text);
            }
        });

        updateItems(false);
        return fragmentView;
    }

    private void openAddByUsername() {
        if (getParentActivity() == null) {
            return;
        }
        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintText(LocaleController.getString(R.string.FocusHiddenChannelsHint));
        editText.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setSingleLine(true);
        editText.setBackgroundDrawable(null);
        editText.setLineColors(
                Theme.getColor(Theme.key_dialogInputField),
                Theme.getColor(Theme.key_dialogInputFieldActivated),
                Theme.getColor(Theme.key_text_RedBold));

        final LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT));

        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.FocusHiddenChannelsAdd));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.Add), (dialog, which) -> resolveAndAdd(editText.getText().toString()));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
        editText.requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editText), 100);
    }

    /** Принимает @name, name, t.me/name, https://t.me/name */
    private static String extractUsername(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        s = s.replaceFirst("(?i)^https?://", "");
        s = s.replaceFirst("(?i)^(www\\.)?t(elegram)?\\.me/", "");
        if (s.startsWith("@")) {
            s = s.substring(1);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? null : s;
    }

    private void resolveAndAdd(String input) {
        final String username = extractUsername(input);
        if (username == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.showDelayed(200);
        getMessagesController().getUserNameResolver().resolve(username, peerId -> {
            progress.dismiss();
            if (peerId == null) {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.FocusHiddenChannelsNotFound)).show();
                return;
            }
            if (peerId >= 0) {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.FocusHiddenChannelsNotChannel)).show();
                return;
            }
            final TLRPC.Chat chat = getMessagesController().getChat(-peerId);
            if (chat == null || !ChatObject.isChannelAndNotMegaGroup(chat)) {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.FocusHiddenChannelsNotChannel)).show();
                return;
            }
            if (HiddenSearchChannels.isHidden(chat)) {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.FocusHiddenChannelsAlready)).show();
                return;
            }
            HiddenSearchChannels.add(chat);
            updateItems(true);
        });
    }

    private void openChannelPicker() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids != null && !dids.isEmpty()) {
                final long did = dids.get(0).dialogId;
                final TLRPC.Chat chat = getMessagesController().getChat(-did);
                if (chat != null) {
                    HiddenSearchChannels.add(chat);
                }
            }
            fragment1.finishFragment();
            updateItems(true);
            return true;
        });
        presentFragment(fragment);
    }

    private void confirmRemove(long chatId, CharSequence title) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.FocusHiddenChannelsRemove));
        builder.setMessage(title);
        builder.setPositiveButton(LocaleController.getString(R.string.Remove), (dialog, which) -> {
            HiddenSearchChannels.remove(chatId);
            updateItems(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button instanceof android.widget.TextView) {
            ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private void updateItems(boolean animated) {
        oldItems.clear();
        oldItems.addAll(items);
        items.clear();

        final ArrayList<Long> ids = HiddenSearchChannels.getIds();
        if (!ids.isEmpty()) {
            items.add(ItemInner.asHeader(LocaleController.getString(R.string.FocusHiddenChannels)));
            for (int i = 0; i < ids.size(); ++i) {
                final long id = ids.get(i);
                final TLRPC.Chat chat = getMessagesController().getChat(id);
                final CharSequence title = chat != null ? chat.title : String.valueOf(id);
                final String username = chat != null ? ChatObject.getPublicUsername(chat) : null;
                items.add(ItemInner.asChannel(id, title, TextUtils.isEmpty(username) ? null : "@" + username, chat));
            }
        }
        items.add(ItemInner.asAdd(LocaleController.getString(R.string.FocusHiddenChannelsAdd)));
        items.add(ItemInner.asAddFromMine(LocaleController.getString(R.string.FocusHiddenChannelsAddFromMine)));
        items.add(ItemInner.asShadow(LocaleController.getString(R.string.FocusHiddenChannelsInfo)));

        if (adapter == null) {
            return;
        }
        if (animated) {
            adapter.setItems(oldItems, items);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    private static class ItemInner extends AdapterWithDiffUtils.Item {
        public CharSequence text;
        public CharSequence subtitle;
        public long chatId;
        public TLRPC.Chat chat;

        private ItemInner(int viewType) {
            super(viewType, viewType == VIEW_TYPE_CHANNEL || viewType == VIEW_TYPE_ADD || viewType == VIEW_TYPE_ADD_FROM_MINE);
        }

        static ItemInner asHeader(CharSequence text) {
            ItemInner i = new ItemInner(VIEW_TYPE_HEADER);
            i.text = text;
            return i;
        }

        static ItemInner asChannel(long chatId, CharSequence title, CharSequence username, TLRPC.Chat chat) {
            ItemInner i = new ItemInner(VIEW_TYPE_CHANNEL);
            i.chatId = chatId;
            i.text = title;
            i.subtitle = username;
            i.chat = chat;
            return i;
        }

        static ItemInner asAdd(CharSequence text) {
            ItemInner i = new ItemInner(VIEW_TYPE_ADD);
            i.text = text;
            return i;
        }

        static ItemInner asAddFromMine(CharSequence text) {
            ItemInner i = new ItemInner(VIEW_TYPE_ADD_FROM_MINE);
            i.text = text;
            return i;
        }

        static ItemInner asShadow(CharSequence text) {
            ItemInner i = new ItemInner(VIEW_TYPE_SHADOW);
            i.text = text;
            return i;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemInner item = (ItemInner) o;
            return viewType == item.viewType
                    && chatId == item.chatId
                    && Objects.equals(text, item.text)
                    && Objects.equals(subtitle, item.subtitle);
        }
    }

    private class ListAdapter extends AdapterWithDiffUtils {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_HEADER) {
                view = new HeaderCell(getContext());
            } else if (viewType == VIEW_TYPE_CHANNEL) {
                view = new UserCell(getContext(), 6, 0, false);
            } else if (viewType == VIEW_TYPE_ADD || viewType == VIEW_TYPE_ADD_FROM_MINE) {
                view = new TextCell(getContext());
            } else {
                view = new TextInfoPrivacyCell(getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= items.size()) {
                return;
            }
            final ItemInner item = items.get(position);
            final boolean divider = position + 1 < items.size() && items.get(position + 1).viewType == item.viewType;
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(item.text);
                    break;
                case VIEW_TYPE_CHANNEL: {
                    UserCell cell = (UserCell) holder.itemView;
                    cell.setData(item.chat, item.text, item.subtitle, 0, divider);
                    break;
                }
                case VIEW_TYPE_ADD: {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.setTextAndIcon(item.text, R.drawable.msg_link, true);
                    cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    break;
                }
                case VIEW_TYPE_ADD_FROM_MINE: {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.setTextAndIcon(item.text, R.drawable.msg_channel, false);
                    cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    break;
                }
                default: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (TextUtils.isEmpty(item.text)) {
                        cell.setFixedSize(12);
                        cell.setText(null);
                    } else {
                        cell.setFixedSize(0);
                        cell.setText(item.text);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int type = holder.getItemViewType();
            return type == VIEW_TYPE_CHANNEL || type == VIEW_TYPE_ADD || type == VIEW_TYPE_ADD_FROM_MINE;
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= items.size()) {
                return 0;
            }
            return items.get(position).viewType;
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
    }
}
