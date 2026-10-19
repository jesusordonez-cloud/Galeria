package com.jesusordonez.galeria;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChannelAdapter extends BaseAdapter {
    public interface FavoriteListener {
        void onToggle(Channel channel);
    }

    private final Context context;
    private final FavoriteListener favoriteListener;
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(4);
    private final android.util.LruCache<String, Bitmap> imageCache = new android.util.LruCache<>(80);
    private final List<Channel> channels = new ArrayList<>();
    private Set<String> favorites;
    private String selectedKey = "";

    public ChannelAdapter(Context context, Set<String> favorites, FavoriteListener listener) {
        this.context = context;
        this.favorites = favorites;
        this.favoriteListener = listener;
    }

    public void setChannels(List<Channel> values) {
        channels.clear();
        channels.addAll(values);
        notifyDataSetChanged();
    }

    public void setFavorites(Set<String> favorites) {
        this.favorites = favorites;
        notifyDataSetChanged();
    }

    public void setSelected(Channel channel) {
        selectedKey = channel == null ? "" : channel.key();
        notifyDataSetChanged();
    }

    @Override public int getCount() { return channels.size(); }
    @Override public Channel getItem(int position) { return channels.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row;
        if (convertView == null) {
            row = createRow();
            convertView = row.root;
            convertView.setTag(row);
        } else {
            row = (Row) convertView.getTag();
        }

        Channel channel = getItem(position);
        boolean selected = selectedKey.equals(channel.key());

        row.root.setBackground(makeBackground(
                selected ? Color.rgb(13, 53, 84) : Color.rgb(10, 22, 36), 18));

        row.name.setText(channel.name);
        row.group.setText(channel.group);
        row.live.setText(selected ? "REPRODUCIENDO" : "EN VIVO");
        row.live.setTextColor(selected ? Color.rgb(125, 211, 252) : Color.rgb(74, 222, 128));
        row.star.setText(favorites.contains(channel.key()) ? "★" : "☆");
        row.star.setTextColor(favorites.contains(channel.key())
                ? Color.rgb(250, 204, 21) : Color.rgb(203, 213, 225));
        row.star.setOnClickListener(v -> favoriteListener.onToggle(channel));

        row.logo.setTag(channel.logo);
        row.logo.setImageDrawable(null);
        row.logo.setBackground(makeBackground(Color.rgb(18, 42, 66), 14));

        if (!channel.logo.trim().isEmpty()) {
            loadLogo(channel.logo, row.logo);
            row.fallback.setVisibility(View.GONE);
        } else {
            row.fallback.setVisibility(View.VISIBLE);
            row.fallback.setText(initials(channel.name));
        }

        return convertView;
    }

    private Row createRow() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(11), dp(10), dp(10), dp(10));

        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86));
        rootParams.setMargins(dp(2), dp(4), dp(2), dp(4));
        root.setLayoutParams(rootParams);

        LinearLayout logoWrap = new LinearLayout(context);
        logoWrap.setGravity(Gravity.CENTER);
        logoWrap.setBackground(makeBackground(Color.rgb(18, 42, 66), 14));

        ImageView logo = new ImageView(context);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logoWrap.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView fallback = text("", 16, Color.rgb(24, 169, 255), true);
        fallback.setGravity(Gravity.CENTER);
        logoWrap.addView(fallback, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(58), dp(58));
        logoParams.setMarginEnd(dp(12));
        root.addView(logoWrap, logoParams);

        LinearLayout textBox = new LinearLayout(context);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = text("", 15, Color.WHITE, true);
        name.setMaxLines(1);

        TextView group = text("", 11, Color.rgb(148, 163, 184), false);
        group.setMaxLines(1);

        TextView live = text("EN VIVO", 9, Color.rgb(74, 222, 128), true);
        live.setPadding(0, dp(3), 0, 0);

        textBox.addView(name);
        textBox.addView(group);
        textBox.addView(live);

        root.addView(textBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView star = text("☆", 26, Color.rgb(203, 213, 225), false);
        star.setGravity(Gravity.CENTER);
        star.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(star, new LinearLayout.LayoutParams(dp(50), dp(50)));

        return new Row(root, logo, fallback, name, group, live, star);
    }

    private void loadLogo(String url, ImageView target) {
        Bitmap cached = imageCache.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }

        imageExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "TV-en-Vivo/3.0 Android");
                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        imageCache.put(url, bitmap);
                        target.post(() -> {
                            Object tag = target.getTag();
                            if (url.equals(tag)) target.setImageBitmap(bitmap);
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "TV";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(android.graphics.Typeface.DEFAULT,
                bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return view;
    }

    private GradientDrawable makeBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public void shutdown() {
        imageExecutor.shutdownNow();
    }

    private static final class Row {
        final LinearLayout root;
        final ImageView logo;
        final TextView fallback;
        final TextView name;
        final TextView group;
        final TextView live;
        final TextView star;

        Row(LinearLayout root, ImageView logo, TextView fallback, TextView name,
            TextView group, TextView live, TextView star) {
            this.root = root;
            this.logo = logo;
            this.fallback = fallback;
            this.name = name;
            this.group = group;
            this.live = live;
            this.star = star;
        }
    }
}
