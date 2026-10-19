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
        row.root.setBackground(makeBackground(selected ? Color.rgb(24, 48, 88) : Color.rgb(17, 24, 39), 16));

        row.name.setText(channel.name);
        row.group.setText(channel.group);
        row.star.setText(favorites.contains(channel.key()) ? "★" : "☆");
        row.star.setContentDescription("Favorito: " + channel.name);
        row.star.setOnClickListener(v -> favoriteListener.onToggle(channel));

        row.logo.setTag(channel.logo);
        row.logo.setImageDrawable(null);
        row.logo.setBackground(makeBackground(Color.rgb(30, 41, 59), 13));
        if (!channel.logo.isBlank()) loadLogo(channel.logo, row.logo);

        return convertView;
    }

    private Row createRow() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(12), dp(9), dp(10), dp(9));
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(82));
        rootParams.setMargins(dp(8), dp(4), dp(8), dp(4));
        root.setLayoutParams(rootParams);

        ImageView logo = new ImageView(context);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(58), dp(58));
        logoParams.setMarginEnd(dp(12));
        root.addView(logo, logoParams);

        LinearLayout textBox = new LinearLayout(context);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(context);
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        name.setMaxLines(2);

        TextView group = new TextView(context);
        group.setTextColor(Color.rgb(148, 163, 184));
        group.setTextSize(12);
        group.setMaxLines(1);

        textBox.addView(name);
        textBox.addView(group);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        root.addView(textBox, textParams);

        TextView star = new TextView(context);
        star.setTextColor(Color.rgb(250, 204, 21));
        star.setTextSize(27);
        star.setGravity(Gravity.CENTER);
        star.setPadding(dp(9), dp(9), dp(9), dp(9));
        root.addView(star, new LinearLayout.LayoutParams(dp(52), dp(52)));

        return new Row(root, logo, name, group, star);
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
                connection.setRequestProperty("User-Agent", "TV-en-Vivo/2.0 Android");
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
        final TextView name;
        final TextView group;
        final TextView star;

        Row(LinearLayout root, ImageView logo, TextView name, TextView group, TextView star) {
            this.root = root;
            this.logo = logo;
            this.name = name;
            this.group = group;
            this.star = star;
        }
    }
}
