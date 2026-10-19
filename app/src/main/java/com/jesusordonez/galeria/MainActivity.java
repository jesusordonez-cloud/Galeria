package com.jesusordonez.galeria;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 12, 22);
    private static final int SURFACE = Color.rgb(17, 24, 39);
    private static final int SURFACE_2 = Color.rgb(24, 34, 53);
    private static final int BLUE = Color.rgb(59, 130, 246);
    private static final int MUTED = Color.rgb(148, 163, 184);

    private static final String[] SCOPE_LABELS = {"México", "Español", "Mundo"};
    private static final String[] SCOPE_URLS = {
            "https://iptv-org.github.io/iptv/countries/mx.m3u",
            "https://iptv-org.github.io/iptv/languages/spa.m3u",
            "https://iptv-org.github.io/iptv/index.m3u"
    };

    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout headerView;
    private LinearLayout bodyView;
    private Spinner scopeSpinner;
    private Spinner categorySpinner;
    private EditText searchInput;
    private ToggleButton favoritesOnly;
    private TextView statusText;
    private TextView channelTitle;
    private TextView nowText;
    private TextView nextText;
    private ProgressBar progressBar;
    private ListView listView;

    private final M3uRepository m3uRepository = new M3uRepository();
    private final EpgRepository epgRepository = new EpgRepository();
    private final List<Channel> allChannels = new ArrayList<>();
    private ChannelAdapter channelAdapter;
    private SharedPreferences preferences;
    private Set<String> favorites = new HashSet<>();
    private Channel selectedChannel;
    private int playerNormalHeight;
    private boolean scopeListenerReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        preferences = getSharedPreferences("tv-live", MODE_PRIVATE);
        favorites = new HashSet<>(preferences.getStringSet("favorites", Collections.emptySet()));

        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                runOnUiThread(() -> {
                    nowText.setText("No fue posible reproducir esta señal.");
                    Toast.makeText(MainActivity.this,
                            "La señal puede estar caída, restringida por región o haber cambiado de URL.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });

        buildUi();
        channelAdapter = new ChannelAdapter(this, favorites, this::toggleFavorite);
        listView.setAdapter(channelAdapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                playChannel(channelAdapter.getItem(position)));

        setupInteractions();
        scopeListenerReady = true;
        loadScope(0);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        headerView = new LinearLayout(this);
        headerView.setOrientation(LinearLayout.HORIZONTAL);
        headerView.setGravity(Gravity.CENTER_VERTICAL);
        headerView.setPadding(dp(16), dp(10), dp(12), dp(10));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("TV EN VIVO", 23, Color.WHITE, true);
        TextView subtitle = text("Canales públicos por Internet", 12, MUTED, false);
        titleBox.addView(title);
        titleBox.addView(subtitle);
        headerView.addView(titleBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView liveBadge = text("●  EN VIVO", 11, Color.WHITE, true);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setPadding(dp(10), dp(7), dp(10), dp(7));
        liveBadge.setBackground(rounded(Color.rgb(185, 28, 28), 99));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.setMarginEnd(dp(8));
        headerView.addView(liveBadge, badgeParams);

        Button pipButton = smallButton("▣");
        pipButton.setContentDescription("Picture in Picture");
        pipButton.setOnClickListener(v -> enterPip());
        headerView.addView(pipButton);

        Button refreshButton = smallButton("↻");
        refreshButton.setContentDescription("Actualizar canales");
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        refreshParams.setMarginStart(dp(6));
        headerView.addView(refreshButton, refreshParams);
        refreshButton.setOnClickListener(v -> loadScope(scopeSpinner.getSelectedItemPosition()));

        root.addView(headerView);

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setKeepScreenOn(true);
        playerNormalHeight = dp(228);
        root.addView(playerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, playerNormalHeight));

        bodyView = new LinearLayout(this);
        bodyView.setOrientation(LinearLayout.VERTICAL);
        bodyView.setPadding(dp(12), dp(10), dp(12), 0);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), dp(12), dp(14), dp(12));
        info.setBackground(rounded(SURFACE, 16));

        channelTitle = text("Selecciona un canal", 18, Color.WHITE, true);
        nowText = text("Ahora: —", 13, Color.rgb(203, 213, 225), false);
        nextText = text("Siguiente: —", 12, MUTED, false);
        nowText.setPadding(0, dp(6), 0, 0);
        nextText.setPadding(0, dp(3), 0, 0);
        info.addView(channelTitle);
        info.addView(nowText);
        info.addView(nextText);
        bodyView.addView(info);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        filterRow.setPadding(0, dp(10), 0, 0);

        scopeSpinner = new Spinner(this);
        scopeSpinner.setAdapter(spinnerAdapter(SCOPE_LABELS));
        filterRow.addView(scopeSpinner, new LinearLayout.LayoutParams(0, dp(48), 1f));

        categorySpinner = new Spinner(this);
        categorySpinner.setAdapter(spinnerAdapter(new String[]{"Todos"}));
        LinearLayout.LayoutParams catParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        catParams.setMarginStart(dp(8));
        filterRow.addView(categorySpinner, catParams);

        bodyView.addView(filterRow);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(0, dp(8), 0, 0);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Buscar canal…");
        searchInput.setHintTextColor(Color.rgb(100, 116, 139));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(14);
        searchInput.setPadding(dp(13), 0, dp(13), 0);
        searchInput.setBackground(rounded(SURFACE, 13));
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(46), 1f));

        favoritesOnly = new ToggleButton(this);
        favoritesOnly.setTextOff("☆");
        favoritesOnly.setTextOn("★");
        favoritesOnly.setText("☆");
        favoritesOnly.setTextSize(22);
        favoritesOnly.setTextColor(Color.rgb(250, 204, 21));
        favoritesOnly.setBackground(rounded(SURFACE, 13));
        LinearLayout.LayoutParams favParams = new LinearLayout.LayoutParams(dp(54), dp(46));
        favParams.setMarginStart(dp(8));
        searchRow.addView(favoritesOnly, favParams);
        bodyView.addView(searchRow);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(dp(4), dp(8), dp(4), dp(4));

        statusText = text("Preparando canales…", 12, MUTED, false);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        statusRow.addView(progressBar, new LinearLayout.LayoutParams(dp(28), dp(28)));
        bodyView.addView(statusRow);

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setBackgroundColor(Color.TRANSPARENT);
        bodyView.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(bodyView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void setupInteractions() {
        scopeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (scopeListenerReady) loadScope(position);
            }
        });

        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilter();
            }
        });

        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        favoritesOnly.setOnCheckedChangeListener((buttonView, isChecked) -> applyFilter());
    }

    private void loadScope(int position) {
        if (position < 0 || position >= SCOPE_URLS.length) position = 0;

        if (!hasInternet()) {
            statusText.setText("Sin conexión a Internet.");
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Esta aplicación necesita Internet para cargar y reproducir TV.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        final int selectedPosition = position;
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Actualizando " + SCOPE_LABELS[position] + "…");
        searchInput.setText("");
        favoritesOnly.setChecked(false);

        m3uRepository.load(SCOPE_URLS[position], new M3uRepository.Callback() {
            @Override
            public void onLoaded(List<Channel> channels) {
                runOnUiThread(() -> {
                    if (scopeSpinner.getSelectedItemPosition() != selectedPosition) return;
                    allChannels.clear();
                    allChannels.addAll(channels);
                    rebuildCategories();
                    applyFilter();
                    progressBar.setVisibility(View.GONE);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("No fue posible cargar la lista de canales.");
                    Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void rebuildCategories() {
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Channel channel : allChannels) {
            if (!channel.group.isBlank()) categories.add(channel.group);
        }

        List<String> values = new ArrayList<>();
        values.add("Todos");
        values.addAll(categories);
        categorySpinner.setAdapter(spinnerAdapter(values.toArray(new String[0])));
        categorySpinner.setSelection(0);
    }

    private void applyFilter() {
        if (channelAdapter == null) return;

        String query = searchInput == null ? "" :
                searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        String category = categorySpinner == null || categorySpinner.getSelectedItem() == null
                ? "Todos" : categorySpinner.getSelectedItem().toString();
        boolean onlyFavorites = favoritesOnly != null && favoritesOnly.isChecked();

        List<Channel> filtered = new ArrayList<>();
        for (Channel channel : allChannels) {
            boolean searchMatch = query.isBlank()
                    || channel.name.toLowerCase(Locale.ROOT).contains(query)
                    || channel.group.toLowerCase(Locale.ROOT).contains(query);
            boolean categoryMatch = "Todos".equals(category)
                    || channel.group.equalsIgnoreCase(category);
            boolean favoriteMatch = !onlyFavorites || favorites.contains(channel.key());

            if (searchMatch && categoryMatch && favoriteMatch) filtered.add(channel);
        }

        channelAdapter.setChannels(filtered);
        channelAdapter.setFavorites(favorites);
        statusText.setText(filtered.size() + " canales"
                + (allChannels.size() == filtered.size() ? "" : " de " + allChannels.size()));
    }

    @OptIn(markerClass = UnstableApi.class)
    private void playChannel(Channel channel) {
        selectedChannel = channel;
        channelAdapter.setSelected(channel);

        channelTitle.setText(channel.name);
        nowText.setText("Ahora: consultando guía…");
        nextText.setText("Siguiente: —");

        String userAgent = channel.userAgent.isBlank()
                ? "Mozilla/5.0 (Linux; Android) TV-en-Vivo/2.0"
                : channel.userAgent;

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000);

        Map<String, String> headers = new HashMap<>();
        if (!channel.referrer.isBlank()) {
            headers.put("Referer", channel.referrer);
            try {
                java.net.URI uri = java.net.URI.create(channel.referrer);
                if (uri.getScheme() != null && uri.getHost() != null) {
                    headers.put("Origin", uri.getScheme() + "://" + uri.getHost());
                }
            } catch (Exception ignored) {}
        }
        if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(httpFactory);
        player.setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(channel.url)));
        player.prepare();
        player.setPlayWhenReady(true);

        final String key = channel.key();
        epgRepository.request(channel.id, info -> runOnUiThread(() -> {
            if (selectedChannel == null || !selectedChannel.key().equals(key)) return;

            if (!info.message.isBlank()) {
                nowText.setText("Guía: " + info.message);
                nextText.setText("");
                return;
            }

            if (!info.nowTitle.isBlank()) {
                nowText.setText("Ahora  " + info.nowTime + "  ·  " + info.nowTitle);
            } else {
                nowText.setText("Ahora: sin datos");
            }

            if (!info.nextTitle.isBlank()) {
                nextText.setText("Siguiente  " + info.nextTime + "  ·  " + info.nextTitle);
            } else {
                nextText.setText("Siguiente: sin datos");
            }
        }));
    }

    private void toggleFavorite(Channel channel) {
        if (favorites.contains(channel.key())) {
            favorites.remove(channel.key());
            Toast.makeText(this, "Quitado de favoritos", Toast.LENGTH_SHORT).show();
        } else {
            favorites.add(channel.key());
            Toast.makeText(this, "Añadido a favoritos", Toast.LENGTH_SHORT).show();
        }

        preferences.edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
        channelAdapter.setFavorites(favorites);
        applyFilter();
    }

    private boolean hasInternet() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Picture in Picture requiere Android 8 o superior.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedChannel == null) {
            Toast.makeText(this, "Primero reproduce un canal.", Toast.LENGTH_SHORT).show();
            return;
        }

        PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .build();
        enterPictureInPictureMode(params);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && selectedChannel != null
                && player != null
                && player.isPlaying()
                && !isInPictureInPictureMode()) {
            enterPip();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
                                              Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        headerView.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        bodyView.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams params = playerView.getLayoutParams();
        params.height = isInPictureInPictureMode
                ? ViewGroup.LayoutParams.MATCH_PARENT : playerNormalHeight;
        playerView.setLayoutParams(params);
        playerView.setUseController(!isInPictureInPictureMode);
    }

    private ArrayAdapter<String> spinnerAdapter(String[] values) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) {
                    TextView text = (TextView) view;
                    text.setTextColor(Color.WHITE);
                    text.setTextSize(13);
                    text.setPadding(dp(12), 0, dp(8), 0);
                    text.setBackground(rounded(SURFACE, 12));
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                if (view instanceof TextView) {
                    TextView text = (TextView) view;
                    text.setTextColor(Color.WHITE);
                    text.setTextSize(14);
                    text.setPadding(dp(14), dp(13), dp(14), dp(13));
                    text.setBackgroundColor(SURFACE_2);
                }
                return view;
            }
        };
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        return view;
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(20);
        button.setTextColor(Color.WHITE);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setBackground(rounded(SURFACE_2, 13));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return button;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isInPictureInPictureMode() && player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null && selectedChannel != null && !isInPictureInPictureMode()) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (channelAdapter != null) channelAdapter.shutdown();
        m3uRepository.shutdown();
        epgRepository.shutdown();
        if (player != null) player.release();
        super.onDestroy();
    }
}
