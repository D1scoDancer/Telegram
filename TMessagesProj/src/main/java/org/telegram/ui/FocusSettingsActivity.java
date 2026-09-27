package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.FocusSettings;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Fork settings screen: stories bar, muted counters and channels hidden from search.
 */
public class FocusSettingsActivity extends UniversalFragment {

    private static final int BUTTON_HIDE_STORIES = 1;
    private static final int BUTTON_HIDDEN_CHANNELS = 2;
    private static final int BUTTON_HIDE_MUTED_COUNTERS = 3;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.FocusSettings);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(BUTTON_HIDE_STORIES, getString(R.string.FocusHideStories)).setChecked(FocusSettings.isStoriesHidden()));
        items.add(UItem.asShadow(getString(R.string.FocusHideStoriesInfo)));
        items.add(UItem.asCheck(BUTTON_HIDE_MUTED_COUNTERS, getString(R.string.FocusHideMutedCounters)).setChecked(FocusSettings.isMutedCountersHidden()));
        items.add(UItem.asShadow(getString(R.string.FocusHideMutedCountersInfo)));
        items.add(UItem.asButton(BUTTON_HIDDEN_CHANNELS, R.drawable.msg2_block2, getString(R.string.FocusHiddenChannels)));
        items.add(UItem.asShadow(getString(R.string.FocusHiddenChannelsInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_HIDE_STORIES) {
            FocusSettings.setStoriesHidden(!FocusSettings.isStoriesHidden());
            listView.adapter.update(true);
        } else if (item.id == BUTTON_HIDE_MUTED_COUNTERS) {
            FocusSettings.setMutedCountersHidden(!FocusSettings.isMutedCountersHidden());
            listView.adapter.update(true);
        } else if (item.id == BUTTON_HIDDEN_CHANNELS) {
            presentFragment(new HiddenChannelsActivity());
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
