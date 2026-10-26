/*
 *  Copyright (C) 2018 The OmniROM Project
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
package org.omnirom.omnistyle;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.omnirom.omnistyle.HeaderUtils.DaylightHeaderInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrowseHeaderActivity extends AppCompatActivity {

    private static final String TAG = "BrowseHeaderActivity";

    private List<DaylightHeaderInfo> mHeadersList = new ArrayList<>();
    private Resources mRes;
    private String mPackageName;
    private String mHeaderName;
    private RecyclerView mHeaderListView;
    private Spinner mHeaderSelect;
    private Map<String, String> mHeaderMap = new HashMap<>();
    private List<String> mLabelList = new ArrayList<>();
    private HeaderListAdapter mHeaderListAdapter;
    private TextView mCreatorName;
    private String mCreatorLabel;
    private String mCreator;
    protected boolean mPickerMode;

    public class HeaderListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.header_image, parent, false);
            return new HeaderImageHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            HeaderImageHolder holder = (HeaderImageHolder) h;
            DaylightHeaderInfo info = mHeadersList.get(position);
            int resId = mRes != null ? mRes.getIdentifier(info.mImage, "drawable", mPackageName) : 0;
            if (resId != 0) {
                holder.mHeaderImage.setImageDrawable(mRes.getDrawable(resId, null));
            } else {
                holder.mHeaderImage.setImageDrawable(null);
            }
            holder.mHeaderName.setText(info.mName != null ? info.mName : info.mImage);
        }

        @Override
        public int getItemCount() {
            return mHeadersList.size();
        }
    }

    class HeaderImageHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public ImageView mHeaderImage;
        public TextView mHeaderName;

        HeaderImageHolder(View itemView) {
            super(itemView);
            mHeaderImage = (ImageView) itemView.findViewById(R.id.header_image);
            mHeaderName = (TextView) itemView.findViewById(R.id.header_name);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getAdapterPosition();
            if (!mPickerMode || position < 0 || position >= mHeadersList.size()) {
                return;
            }
            DaylightHeaderInfo info = mHeadersList.get(position);
            Settings.System.putString(getContentResolver(),
                    Settings.System.STATUS_BAR_CUSTOM_HEADER_IMAGE, mPackageName + "/" + info.mImage);
            Settings.System.putString(getContentResolver(),
                    Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER, "static");
            Toast.makeText(BrowseHeaderActivity.this, R.string.custom_header_image_notice,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_browse);

        mHeaderSelect = (Spinner) findViewById(R.id.package_select);
        mCreatorName = (TextView) findViewById(R.id.meta_creator);
        mCreatorLabel = getResources().getString(R.string.header_creator_label);
        getAvailableHeaderPacks();

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(this, R.layout.spinner_item);
        adapter.addAll(mLabelList);
        mHeaderSelect.setAdapter(adapter);
        mHeaderSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= mLabelList.size()) {
                    return;
                }
                loadHeaderPackage(mHeaderMap.get(mLabelList.get(position)));
                mCreatorName.setText(mCreatorLabel + mCreator);
                mCreatorName.setVisibility(TextUtils.isEmpty(mCreator) ? View.GONE : View.VISIBLE);
                mHeaderListAdapter.notifyDataSetChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mHeaderListView = (RecyclerView) findViewById(R.id.package_images);
        layoutHeaderList(getResources().getConfiguration());
        mHeaderListView.setLayoutManager(new GridLayoutManager(this, 1));
        mHeaderListView.setHasFixedSize(false);
        mHeaderListAdapter = new HeaderListAdapter();
        mHeaderListView.setAdapter(mHeaderListAdapter);

        if (mLabelList.isEmpty()) {
            Log.w(TAG, "No header packs installed");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    private void getAvailableHeaderPacks() {
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent("org.omnirom.DaylightHeaderPack");
        for (ResolveInfo info : packageManager.queryIntentActivities(intent, 0)) {
            String packageName = info.activityInfo.packageName;
            CharSequence loaded = info.activityInfo.loadLabel(packageManager);
            String label = loaded != null ? loaded.toString() : packageName;
            mHeaderMap.put(label, packageName);
        }
        intent.setAction("org.omnirom.DaylightHeaderPack1");
        for (ResolveInfo info : packageManager.queryIntentActivities(intent, 0)) {
            String packageName = info.activityInfo.packageName;
            CharSequence loaded = info.activityInfo.loadLabel(packageManager);
            String label = loaded != null ? loaded.toString() : packageName;
            mHeaderMap.put(label, packageName + "/" + info.activityInfo.name);
        }
        mLabelList.addAll(mHeaderMap.keySet());
        Collections.sort(mLabelList);
    }

    private void loadHeaderPackage(String label) {
        if (label == null) {
            mRes = null;
            mHeadersList.clear();
            return;
        }
        int separator = label.indexOf('/');
        if (separator != -1) {
            mPackageName = label.substring(0, separator);
            mHeaderName = label.substring(separator + 1);
        } else {
            mPackageName = label;
            mHeaderName = null;
        }
        try {
            mRes = getPackageManager().getResourcesForApplication(mPackageName);
            mCreator = HeaderUtils.loadHeaders(mRes, mHeaderName, mHeadersList);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load header pack " + label, e);
            mRes = null;
            mCreator = null;
            mHeadersList.clear();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        layoutHeaderList(newConfig);
        mHeaderListView.getLayoutManager().requestLayout();
    }

    private void layoutHeaderList(Configuration configuration) {
        LinearLayout.LayoutParams params;
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            params = new LinearLayout.LayoutParams(
                    getResources().getDimensionPixelOffset(R.dimen.header_notification_width_landscape),
                    ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        params.gravity = Gravity.CENTER_HORIZONTAL;
        mHeaderListView.setLayoutParams(params);
    }
}
