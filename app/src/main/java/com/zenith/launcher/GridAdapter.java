package com.zenith.launcher;

import android.content.Context;
import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

    private int getCustomAppIconRes(String pkg) {
        if (pkg == null) return 0;
        String p = pkg.toLowerCase();
        if (p.contains("netflix")) return R.drawable.icon_netflix;
        if (p.contains("youtube")) return R.drawable.icon_youtube;
        if (p.contains("disney")) return R.drawable.icon_disney;
        if (p.contains("firebat") || p.contains("amazonvideo") || p.contains("primevideo")) return R.drawable.icon_prime;
        if (p.contains("streamflix")) return R.drawable.icon_streamflix;
        if (p.contains("aurora")) return R.drawable.icon_aurora;
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_grid_app, parent, false);
            holder = new ViewHolder();
            holder.cardInner = convertView.findViewById(R.id.card_inner);
            holder.flIconWrapper = convertView.findViewById(R.id.fl_icon_wrapper);
            holder.ivIcon = convertView.findViewById(R.id.iv_app_icon);
            holder.tvName = convertView.findViewById(R.id.tv_app_name);
            holder.tvFavBadge = convertView.findViewById(R.id.tv_fav_badge);
            holder.tvLockBadge = convertView.findViewById(R.id.tv_lock_badge);

            final float density = context.getResources().getDisplayMetrics().density;
            final float cardRadiusPx = 16 * density;
            final float iconRadiusPx = 14 * density;

            holder.cardInner.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cardRadiusPx);
                }
            });
            holder.cardInner.setClipToOutline(true);

            if (holder.flIconWrapper != null) {
                holder.flIconWrapper.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), iconRadiusPx);
                    }
                });
                holder.flIconWrapper.setClipToOutline(true);
            }

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AppInfo app = apps.get(position);
        boolean isFav = (favoritePkgs != null && favoritePkgs.contains(app.getPackageName()));
        boolean isLocked = (lockedPkgs != null && lockedPkgs.contains(app.getPackageName()));

        holder.tvName.setText(app.getLabel());

        int customRes = getCustomAppIconRes(app.getPackageName());
        if (customRes != 0) {
            holder.ivIcon.setImageResource(customRes);
        } else if (app.getIcon() != null) {
            holder.ivIcon.setImageDrawable(app.getIcon());
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_apps);
        }

        holder.tvFavBadge.setVisibility(isFav ? View.VISIBLE : View.GONE);
        holder.tvLockBadge.setVisibility(isLocked ? View.VISIBLE : View.GONE);

        return convertView;
    }

    private static class ViewHolder {
        FrameLayout cardInner;
        FrameLayout flIconWrapper;
        ImageView ivIcon;
        TextView tvName;
        TextView tvFavBadge;
        TextView tvLockBadge;
    }
}
