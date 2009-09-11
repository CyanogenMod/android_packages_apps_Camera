package com.android.camera;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;

// Please reference to {@link android.widget.ZoomButtonsController} for detail
// information about adding window to WindowManager.
public class OnScreenSettings {
    private static final String TAG = "OnScreenSettings";
    private static final int MSG_POST_SET_VISIBLE = 1;

    public interface OnVisibilityChangedListener {
        public void onVisibilityChanged(boolean visibility);
    }

    private LayoutParams mContainerLayoutParams;
    private final Context mContext;
    private final Container mContainer;
    private final WindowManager mWindowManager;
    private final View mOwnerView;
    private ListView mMainMenu;
    private ListView mSubMenu;
    private boolean mIsVisible = false;
    private OnVisibilityChangedListener mVisibilityListener;

    /**
     * When showing the on-screen settings, we add the view as a new window.
     * However, there is logic that needs to know the size of the zoom which
     * is determined after it's laid out. Therefore, we must post this logic
     * onto the UI thread so it will be exceuted AFTER the layout. This is
     * the logic.
     */
    private Runnable mPostedVisibleInitializer;
    private final LayoutInflater mInflater;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_POST_SET_VISIBLE:
                    setVisible(true);
                    break;
            }
        }
    };

    public OnScreenSettings(View ownerView) {
        mContext = ownerView.getContext();
        mInflater = (LayoutInflater)
                mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mWindowManager = (WindowManager)
                mContext.getSystemService(Context.WINDOW_SERVICE);
        mOwnerView = ownerView;
        mContainer = createContainer();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public void setOnVisibilityChangedListener(
            OnVisibilityChangedListener listener) {
        mVisibilityListener = listener;
    }

    public void setVisible(boolean visible) {
        if (visible) {
            if (mOwnerView.getWindowToken() == null) {
                /*
                 * We need a window token to show ourselves, maybe the owner's
                 * window hasn't been created yet but it will have been by the
                 * time the looper is idle, so post the setVisible(true) call.
                 */
                if (!mHandler.hasMessages(MSG_POST_SET_VISIBLE)) {
                    mHandler.sendEmptyMessage(MSG_POST_SET_VISIBLE);
                }
                return;
            }
        }

        if (mIsVisible == visible) {
            return;
        }
        mIsVisible = visible;

        if (visible) {
            if (mContainerLayoutParams.token == null) {
                mContainerLayoutParams.token = mOwnerView.getWindowToken();
            }
            if (mPostedVisibleInitializer == null) {
                mPostedVisibleInitializer = new Runnable() {
                    public void run() {
                        refreshPositioningVariables();
                    }
                };
            }
            mWindowManager.addView(mContainer, mContainerLayoutParams);
            mHandler.post(mPostedVisibleInitializer);
        } else {
            // Reset the two menus
            mSubMenu.setAdapter(null);
            mSubMenu.setVisibility(View.INVISIBLE);
            mMainMenu.setVisibility(View.VISIBLE);

            mWindowManager.removeView(mContainer);
            mHandler.removeCallbacks(mPostedVisibleInitializer);
        }
        if (mVisibilityListener != null) {
            mVisibilityListener.onVisibilityChanged(mIsVisible);
        }
    }

    private void refreshPositioningVariables() {
        // if the mOwnerView is detached from window then skip.
        if (mOwnerView.getWindowToken() == null) return;

        // Position the zoom controls on the bottom of the owner view.
        int ownerHeight = mOwnerView.getHeight();
        int ownerWidth = mOwnerView.getWidth();
        // The gap between the top of the owner and the top of the container
        int containerOwnerYOffset = ownerHeight - mContainer.getHeight();

        // Calculate the owner view's bounds
        int[] mOwnerViewRawLocation = new int[2];
        mOwnerView.getLocationOnScreen(mOwnerViewRawLocation);

        // lp.x and lp.y should be relative to the owner's window top-left
        mContainerLayoutParams.x = mOwnerViewRawLocation[0];
        mContainerLayoutParams.y = mOwnerViewRawLocation[1];

        mContainerLayoutParams.width = ownerWidth * 4 / 5;
        mContainerLayoutParams.height = ownerHeight - 10;

        if (mIsVisible) {
            mWindowManager.updateViewLayout(mContainer, mContainerLayoutParams);
        }
    }

    private void showSubMenu() {
        Util.slideOut(mMainMenu, Util.DIRECTION_LEFT);
        Util.slideIn(mSubMenu, Util.DIRECTION_RIGHT);
        mSubMenu.requestFocus();
    }

    private void closeSubMenu() {
        Util.slideOut(mSubMenu, Util.DIRECTION_RIGHT);
        Util.slideIn(mMainMenu, Util.DIRECTION_LEFT);

        // The data could be changed in the sub menu, so we update the summary
        // in the main menu here
        ((MainMenuAdapter) mMainMenu.getAdapter()).notifyDataSetChanged();
    }

    private Container createContainer() {
        LayoutParams lp = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.flags = 0;
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.height = LayoutParams.WRAP_CONTENT;
        lp.width = LayoutParams.WRAP_CONTENT;
        lp.type = LayoutParams.TYPE_APPLICATION_PANEL;
        lp.format = PixelFormat.TRANSPARENT;

        mContainerLayoutParams = lp;

        Container container = new Container(mContext);
        container.setLayoutParams(lp);

        mInflater.inflate(R.layout.on_screen_menu, container);

        mMainMenu = (ListView) container.findViewById(R.id.menu_view);
        mSubMenu = (ListView) container.findViewById(R.id.sub_menu);
        return container;
    }

    private boolean onContainerKey(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    setVisible(false);
                    return true;
                }
        }
        return false;
    }

    // Add the preference and it's children recursively to the given list. So
    // that we can show the preference (and it's children) in the list view.
    private static void addPreference(
            Preference preference, ArrayList<Preference> list) {
        list.add(preference);
        if (preference instanceof PreferenceGroup) {
            PreferenceGroup group = (PreferenceGroup) preference;
            for (int i = 0, n = group.getPreferenceCount(); i < n; ++i) {
                Preference child = group.getPreference(i);
                addPreference(child, list);
            }
        }
    }

    public void setPreferenceScreen(PreferenceScreen screen) {
        ArrayList<Preference> list = new ArrayList<Preference>();

        // We don't want the screen add to the list, we add the first level
        // preference here.
        for (int  i = 0, n = screen.getPreferenceCount(); i < n; ++i) {
            addPreference(screen.getPreference(i), list);
        }
        MainMenuAdapter mainAdapter = new MainMenuAdapter(mContext, list);
        mMainMenu.setAdapter(mainAdapter);
        mMainMenu.setOnItemClickListener(mainAdapter);
    }

    private View inflateIfNeed(
            View view, int resource, ViewGroup root, boolean attachToRoot) {
        if (view != null) return view;
        return mInflater.inflate(resource, root, attachToRoot);
    }

    private class MainMenuAdapter extends BaseAdapter
            implements OnItemClickListener {
        private final ArrayList<Preference> mPreferences;

        public MainMenuAdapter(
                Context context, ArrayList<Preference> preferences) {
            mPreferences = preferences;
        }

        public void onItemClick(
                AdapterView<?> parent, View view, int position, long id) {
            Preference preference = mPreferences.get(position);
            if (preference instanceof CheckBoxPreference) {
                CheckBoxPreference ckPref = (CheckBoxPreference) preference;
                ((CheckBox) view.findViewById(
                        R.id.check_box)).setChecked(!ckPref.isChecked());
                ckPref.setChecked(!ckPref.isChecked());
            } else if (preference instanceof ListPreference) {
                SubMenuAdapter adapter = new SubMenuAdapter(
                        mContext, (ListPreference) preference);
                mSubMenu.setAdapter(adapter);
                mSubMenu.setOnItemClickListener(adapter);
                showSubMenu();
            }
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Preference preference = mPreferences.get(position);

            if (preference instanceof PreferenceGroup) {
                convertView = inflateIfNeed(convertView,
                        R.layout.on_screen_menu_header, parent, false);
                PreferenceGroup group = (PreferenceGroup) preference;
                ((TextView) convertView.findViewById(
                        R.id.title)).setText(group.getTitle());
            } else if (preference instanceof CheckBoxPreference) {
                convertView = inflateIfNeed(convertView,
                        R.layout.on_screen_menu_checkbox_item, parent, false);
                ((TextView) convertView.findViewById(
                        R.id.title)).setText(preference.getTitle());
                CheckBox checkBox = ((CheckBox)
                        convertView.findViewById(R.id.check_box));
                checkBox.setChecked(
                        ((CheckBoxPreference) preference).isChecked());
            } else if (preference instanceof ListPreference) {
                convertView = inflateIfNeed(convertView,
                        R.layout.on_screen_menu_list_item, parent, false);
                ((TextView) convertView.findViewById(
                        R.id.title)).setText(preference.getTitle());
                ((TextView) convertView.findViewById(R.id.summary))
                        .setText(((ListPreference) preference).getEntry());
            }
            return convertView;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            Preference preference = mPreferences.get(position);
            return !(preference instanceof PreferenceGroup);
        }

        public int getCount() {
            return mPreferences.size();
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            Preference pref = mPreferences.get(position);
            if (pref instanceof PreferenceGroup) return 0;
            if (pref instanceof ListPreference) return 1;
            if (pref instanceof CheckBoxPreference) return 2;
            throw new IllegalStateException();
        }

        @Override
        public int getViewTypeCount() {
            // we have three type, see getItemViewType()
            return 3;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isEmpty() {
            return mPreferences.isEmpty();
        }
    }

    private class SubMenuAdapter extends BaseAdapter
            implements OnItemClickListener {
        private final ListPreference mPreference;

        public SubMenuAdapter(Context context, ListPreference preference) {
            mPreference = preference;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            CharSequence entry[] = mPreference.getEntries();
            if (position == 0) {
                convertView = inflateIfNeed(convertView,
                        R.layout.on_screen_submenu_header, parent, false);
                ((TextView) convertView.findViewById(
                        R.id.title)).setText(mPreference.getDialogTitle());
            } else if (position == entry.length + 1) {
                convertView = inflateIfNeed(convertView,
                        R.layout.on_screen_submenu_cancel, parent, false);
            } else {
                convertView = inflateIfNeed(convertView,
                        R.layout.on_screen_submenu_item, parent, false);
                boolean checked = mPreference.getValue().equals(
                        mPreference.getEntryValues()[position - 1]);
                ((TextView) convertView.findViewById(
                        R.id.title)).setText(entry[position - 1]);
                RadioButton radio = ((RadioButton)
                        convertView.findViewById(R.id.radio_button));
                radio.setChecked(checked);
            }
            return convertView;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItemViewType(position) != 0;
        }

        public int getCount() {
            // add one header and one cancel
            return mPreference.getEntries().length + 2;
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) return 0;
            if (position == getCount() - 1) return 1;
            return 2;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        public void onItemClick(
                AdapterView<?> parent, View view, int position, long id) {
            CharSequence values[] = mPreference.getEntryValues();
            if (position <= values.length) {
                int idx = mPreference.findIndexOfValue(mPreference.getValue());
                if (idx != position - 1) {
                    mPreference.setValueIndex(position - 1);
                    notifyDataSetChanged();
                    return;
                }
            }
            // Close the sub menu when user presses on "back" or the original
            // option.
            closeSubMenu();
        }
    }

    private class Container extends FrameLayout {
        public Container(Context context) {
            super(context);
        }

        /*
         * Need to override this to intercept the key events. Otherwise, we
         * would attach a key listener to the container but its superclass
         * ViewGroup gives it to the focused View instead of calling the key
         * listener, and so we wouldn't get the events.
         */
        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return onContainerKey(event)
                    ? true
                    : super.dispatchKeyEvent(event);
        }
    }
}
