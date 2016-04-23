/*
 *  Copyright (C) 2016 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnigears.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.omnirom.omnigears.R;
import org.omnirom.omnigears.ui.dslv.DragSortController;
import org.omnirom.omnigears.ui.dslv.DragSortListView;

import java.net.URISyntaxException;
import java.text.Collator;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class ShortcutDialog extends AlertDialog implements
        DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private LayoutInflater mInflater;
    private List<String> mShortcutList;
    private ShortcutListAdapter mShortcutAdapter;
    private DragSortListView mShortcutConfigList;
    private AlertDialog mAddShortcutDialog;
    private final List<MyApplicationInfo> mPackageInfoList = new ArrayList<MyApplicationInfo>();

    ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);

    class MyApplicationInfo {
        ApplicationInfo info;
        CharSequence label;
        ResolveInfo resolveInfo;
    }

    public class ShortcutListAdapter extends ArrayAdapter<String> {
        public ShortcutListAdapter(Context context) {
            super(context, R.layout.shortcut_app_item, mShortcutList);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppViewHolder holder = AppViewHolder.createOrRecycle(mInflater, convertView);
            convertView = holder.rootView;
            String intentString = getItem(position);
            Intent intent = null;
            try {
                intent = Intent.parseUri(intentString, 0);
            } catch (URISyntaxException e) {
                // TODO should never happen - better build a intent list
                return convertView;
            }
            final List<ResolveInfo> pkgAppsList = getContext().getPackageManager().queryIntentActivities(intent, 0);
            if (pkgAppsList.size() > 0) {
                Drawable icon = pkgAppsList.get(0).activityInfo.loadIcon(getContext().getPackageManager());
                CharSequence label = pkgAppsList.get(0).activityInfo.loadLabel(getContext().getPackageManager());
                holder.appName.setText(label);
                holder.appIcon.setImageDrawable(icon);
            }
            return convertView;
        }
    }

    private class ShortcutDragSortController extends DragSortController {

        public ShortcutDragSortController() {
            super(mShortcutConfigList, R.id.drag_handle,
                    DragSortController.ON_DOWN,
                    DragSortController.FLING_RIGHT_REMOVE);
            setRemoveEnabled(true);
            setSortEnabled(true);
            setBackgroundColor(0x363636);
        }

        @Override
        public void onDragFloatView(View floatView, Point floatPoint,
                Point touchPoint) {
            floatView.setLayoutParams(params);
            mShortcutConfigList.setFloatAlpha(0.8f);
        }

        @Override
        public View onCreateFloatView(int position) {
            View v = mShortcutAdapter.getView(position, null,
                    mShortcutConfigList);
            v.setLayoutParams(params);
            return v;
        }

        @Override
        public void onDestroyFloatView(View floatView) {
        }
    }

    public ShortcutDialog(Context context) {
        super(context);
        mInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installedAppsInfo = getContext().getPackageManager().queryIntentActivities(
                mainIntent, 0);

        for (ResolveInfo info : installedAppsInfo) {
            MyApplicationInfo myInfo = new MyApplicationInfo();
            myInfo.resolveInfo = info;
            myInfo.label = getResolveInfoTitle(info);
            mPackageInfoList.add(myInfo);
        }
        Collections.sort(mPackageInfoList, sDisplayNameComparator);

        mShortcutList = new ArrayList<String>();
        String shortcutStrings = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.LOCK_SHORTCUTS);
        if (shortcutStrings != null && shortcutStrings.length() != 0) {
            String[] values = TextUtils.split(shortcutStrings, "##");

            for (String intentString : values) {
                Intent intent = null;
                try {
                    intent = Intent.parseUri(intentString, 0);
                } catch (URISyntaxException e) {
                    continue;
                }

                final List<ResolveInfo> pkgAppsList = getContext().getPackageManager().queryIntentActivities(intent, 0);
                if (pkgAppsList.size() == 0) {
                    continue;
                }
                mShortcutList.add(intentString);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final Context context = getContext();
        final View view = getLayoutInflater().inflate(R.layout.shortcut_dialog,
                null);
        setView(view);
        setTitle(R.string.lockscreen_shortcut_apps_title);
        setCancelable(true);

        setButton(DialogInterface.BUTTON_POSITIVE,
                context.getString(android.R.string.ok), this);
        setButton(DialogInterface.BUTTON_NEUTRAL,
                context.getString(R.string.lockscreen_shortcut_add), this);
        setButton(DialogInterface.BUTTON_NEGATIVE,
                context.getString(android.R.string.cancel), this);

        super.onCreate(savedInstanceState);

        mShortcutConfigList = (DragSortListView) view
                .findViewById(R.id.shortcut_apps);
        mShortcutAdapter = new ShortcutListAdapter(context);
        mShortcutConfigList.setAdapter(mShortcutAdapter);

        final DragSortController dragSortController = new ShortcutDragSortController();
        mShortcutConfigList.setFloatViewManager(dragSortController);
        mShortcutConfigList
                .setDropListener(new DragSortListView.DropListener() {
                    @Override
                    public void drop(int from, int to) {
                        String intent = mShortcutList.remove(from);
                        mShortcutList.add(to, intent);
                        mShortcutAdapter.notifyDataSetChanged();
                    }
                });
        mShortcutConfigList
                .setRemoveListener(new DragSortListView.RemoveListener() {
                    @Override
                    public void remove(int which) {
                        mShortcutList.remove(which);
                        mShortcutAdapter.notifyDataSetChanged();
                    }
                });
        mShortcutConfigList.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return dragSortController.onTouch(view, motionEvent);
            }
        });
        mShortcutConfigList.setItemsCanFocus(false);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Button neutralButton = getButton(DialogInterface.BUTTON_NEUTRAL);
        neutralButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddShortcutDialog();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAddShortcutDialog != null) {
            mAddShortcutDialog.dismiss();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (mAddShortcutDialog != null) {
            mAddShortcutDialog = null;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            Settings.Secure.putString(getContext().getContentResolver(), Settings.Secure.LOCK_SHORTCUTS,
                    TextUtils.join("##", mShortcutList.toArray()));
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            cancel();
        }
    }

    private String getResolveInfoTitle(ResolveInfo info) {
        CharSequence label = info.loadLabel(getContext().getPackageManager());
        if (label == null) label = info.activityInfo.name;
        return label != null ? label.toString() : null;
    }

    private Intent getIntentForResolveInfo(ResolveInfo info, String action) {
        Intent intent = new Intent(action);
        ActivityInfo ai = info.activityInfo;
        intent.setClassName(ai.packageName, ai.name);
        return intent;
    }

    private void showAddShortcutDialog() {
        if (mAddShortcutDialog != null && mAddShortcutDialog.isShowing()) {
            return;
        }

        mAddShortcutDialog = new AddShortcutDialog(getContext());
        mAddShortcutDialog.setOnDismissListener(this);
        mAddShortcutDialog.show();
    }

    public void applyChanges(List<String> shortcutListEdit) {
        mShortcutList.clear();
        mShortcutList.addAll(shortcutListEdit);
        mShortcutAdapter.notifyDataSetChanged();
    }

    private class AddShortcutDialog extends AlertDialog implements
            DialogInterface.OnClickListener {
        private AppListAdapter mAppAdapter;
        private List<String> mShortcutListEdit;
        private ListView mListView;

        public class AppListAdapter extends ArrayAdapter<MyApplicationInfo> {
            public AppListAdapter(Context context) {
                super(context, R.layout.app_select_item, mPackageInfoList);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                AppSelectViewHolder holder = AppSelectViewHolder.createOrRecycle(mInflater, convertView);
                convertView = holder.rootView;
                MyApplicationInfo info = getItem(position);
                holder.appName.setText(info.label);
                Drawable icon = info.resolveInfo.loadIcon(getContext().getPackageManager());
                if (icon != null) {
                    holder.appIcon.setImageDrawable(icon);
                } else {
                    holder.appIcon.setImageDrawable(null);
                }

                Intent intent = getIntentForResolveInfo(info.resolveInfo, Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                String value = intent.toUri(0).toString();
                holder.checkBox.setChecked(mShortcutListEdit.contains(value));
                return convertView;
            }
        }

        protected AddShortcutDialog(Context context) {
            super(context);
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                applyChanges(mShortcutListEdit);
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                cancel();
            }
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            final Context context = getContext();
            final View view = getLayoutInflater().inflate(
                    R.layout.preference_app_list, null);
            setView(view);
            setTitle(R.string.lockscreen_shortcut_add_dialog_title);
            setCancelable(true);

            setButton(DialogInterface.BUTTON_POSITIVE,
                    context.getString(android.R.string.ok), this);
            setButton(DialogInterface.BUTTON_NEGATIVE,
                    context.getString(android.R.string.cancel), this);

            super.onCreate(savedInstanceState);
            mShortcutListEdit = new ArrayList<String>();
            mShortcutListEdit.addAll(mShortcutList);
            mListView = (ListView) view.findViewById(R.id.app_list);
            mAppAdapter = new AppListAdapter(getContext());
            mListView.setAdapter(mAppAdapter);
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    final AppSelectViewHolder holder = (AppSelectViewHolder) view.getTag();
                    final boolean isChecked = !holder.checkBox.isChecked();
                    holder.checkBox.setChecked(isChecked);

                    MyApplicationInfo myInfo = mAppAdapter.getItem(position);
                    ResolveInfo info = myInfo.resolveInfo;
                    Intent intent = getIntentForResolveInfo(info, Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    String value = intent.toUri(0).toString();

                    if (isChecked) {
                        mShortcutListEdit.add(value);
                    } else {
                        mShortcutListEdit.remove(value);
                    }
                }
            });
        }
    }

    public static class AppSelectViewHolder {
        public View rootView;
        public TextView appName;
        public ImageView appIcon;
        public CheckBox checkBox;

        public static AppSelectViewHolder createOrRecycle(LayoutInflater inflater, View convertView) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.app_select_item, null);

                // Creates a ViewHolder and store references to the two children views
                // we want to bind data to.
                AppSelectViewHolder holder = new AppSelectViewHolder();
                holder.rootView = convertView;
                holder.appName = (TextView) convertView.findViewById(R.id.app_name);
                holder.appIcon = (ImageView) convertView.findViewById(R.id.app_icon);
                holder.checkBox = (CheckBox) convertView.findViewById(android.R.id.checkbox);
                convertView.setTag(holder);
                return holder;
            } else {
                // Get the ViewHolder back to get fast access to the TextView
                // and the ImageView.
                return (AppSelectViewHolder)convertView.getTag();
            }
        }
    }

    public static class AppViewHolder {
        public View rootView;
        public TextView appName;
        public ImageView appIcon;

        public static AppViewHolder createOrRecycle(LayoutInflater inflater, View convertView) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.shortcut_app_item, null);

                // Creates a ViewHolder and store references to the two children views
                // we want to bind data to.
                AppViewHolder holder = new AppViewHolder();
                holder.rootView = convertView;
                holder.appName = (TextView) convertView.findViewById(R.id.app_name);
                holder.appIcon = (ImageView) convertView.findViewById(R.id.app_icon);
                convertView.setTag(holder);
                return holder;
            } else {
                // Get the ViewHolder back to get fast access to the TextView
                // and the ImageView.
                return (AppViewHolder)convertView.getTag();
            }
        }
    }

    private final static Comparator<MyApplicationInfo> sDisplayNameComparator
            = new Comparator<MyApplicationInfo>() {

        private final Collator collator = Collator.getInstance();

        public final int compare(MyApplicationInfo a, MyApplicationInfo b) {
            return collator.compare(a.label, b.label);
        }
    };
}
