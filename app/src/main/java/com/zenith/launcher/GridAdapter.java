package com.zenith.launcher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;
import java.util.Set;

public class GridAdapter extends BaseAdapter {

    private final Context context;
    private final List<AppInfo> apps;
    private Set<String> favoritePkgs;
    private Set<String> lockedPkgs;

    public GridAdapter(Context context, List<AppInfo> apps, Set<String> favoritePkgs, Set<String> lockedPkgs) {
        this.context = context;
        this.apps = apps;
        this.favoritePkgs = favoritePkgs;
        this.lockedPkgs = lockedPkgs;
    }

    public void updateState(Set<String> favorites, Set<String> locked) {
        this.favoritePkgs = favorites;
        this.lockedPkgs = locked;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public Object getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_grid_app, parent, false);
            holder = new ViewHolder();
            holder.tvName = convertView.findViewById(R.id.grid_app_name);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AppInfo app = apps.get(position);
        boolean isFav = (favoritePkgs != null && favoritePkgs.contains(app.getPackageName()));
        boolean isLocked = (lockedPkgs != null && lockedPkgs.contains(app.getPackageName()));

        StringBuilder sb = new StringBuilder();
        if (isFav) sb.append("★ ");
        if (isLocked) sb.append("🔒 ");
        sb.append(app.getLabel());

        holder.tvName.setText(sb.toString());

        return convertView;
    }

    private static class ViewHolder {
        TextView tvName;
    }
}
