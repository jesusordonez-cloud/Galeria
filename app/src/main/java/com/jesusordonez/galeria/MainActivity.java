package com.jesusordonez.galeria;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
    private static final int BG = Color.rgb(4, 10, 18);
    private static final int SURFACE = Color.rgb(10, 22, 36);
    private static final int SURFACE_2 = Color.rgb(14, 32, 50);
    private static final int SURFACE_3 = Color.rgb(18, 42, 66);
    private static final int BLUE = Color.rgb(24, 169, 255);
    private static final int BLUE_DARK = Color.rgb(7, 124, 242);
    private static final int MUTED = Color.rgb(168, 191, 212);
    private static final int GREEN = Color.rgb(34, 197, 94);
    private static final int RED = Color.rgb(225, 29, 72);

    private static final String[] SCOPE_LABELS = {"🇲🇽  México", "◎  Español", "◎  Mundo"};
    private static final String[] SCOPE_URLS = {
            "https://iptv-org.github.io/iptv/countries/mx.m3u",
            "https://iptv-org.github.io/iptv/languages/spa.m3u",
            "https://iptv-org.github.io/iptv/index.m3u"
    };

    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout headerView;
    private LinearLayout bodyView;
    private LinearLayout featuredRow;
    private LinearLayout scopeRow;
    private Button[] scopeButtons;
    private EditText searchInput;
    private Spinner categorySpinner;
    private TextView statusText;
    private TextView channelTitle;
    private TextView channelGroup;
    private TextView nowText;
    private TextView nextText;
    private TextView connectionText;
    private TextView favoriteAction;
    private ProgressBar progressBar;
    private ListView listView;

    private final M3uRepository m3uRepository = new M3uRepository();
    private final EpgRepository epgRepository = new EpgRepository();
    private final List<Channel> allChannels = new ArrayList<>();
    private ChannelAdapter channelAdapter;
    private SharedPreferences preferences;
    private Set<String> favorites = new HashSet<>();
    private Channel selectedChannel;
    private int currentScope = 0;
    private boolean favoritesMode = false;
    private int playerNormalHeight;

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
                    nowText.setText("La señal no está disponible en este momento.");
                    nextText.setText("Prueba otro canal o actualiza la lista.");
                    Toast.makeText(MainActivity.this,
                            "La señal puede estar caída o restringida por región.",
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
        headerView.setPadding(dp(16), dp(12), dp(16), dp(8));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);

        TextView brandTitle = text("TV EN VIVO", 25, Color.WHITE, true);
        brandTitle.setLetterSpacing(-0.03f);
        TextView brandSub = text("Tu televisión, en cualquier lugar", 11, MUTED, false);

        brand.addView(brandTitle);
        brand.addView(brandSub);
        headerView.addView(brand, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout connection = new LinearLayout(this);
        connection.setGravity(Gravity.CENTER_VERTICAL);
        connection.setPadding(dp(10), dp(7), dp(10), dp(7));
        connection.setBackground(rounded(SURFACE_2, 99));

        TextView dot = text("●", 12, GREEN, true);
        connectionText = text(" Online", 11, Color.WHITE, true);
        connection.addView(dot);
        connection.addView(connectionText);
        headerView.addView(connection);

        root.addView(headerView);

        HorizontalScrollView scopes = new HorizontalScrollView(this);
        scopes.setHorizontalScrollBarEnabled(false);
        scopes.setClipToPadding(false);
        scopes.setPadding(dp(12), dp(6), dp(12), dp(10));

        scopeRow = new LinearLayout(this);
        scopeRow.setOrientation(LinearLayout.HORIZONTAL);
        scopeButtons = new Button[4];

        for (int i = 0; i < 3; i++) {
            final int index = i;
            Button chip = scopeChip(SCOPE_LABELS[i]);
            chip.setOnClickListener(v -> {
                favoritesMode = false;
                loadScope(index);
            });
            scopeButtons[i] = chip;
            scopeRow.addView(chip);
        }

        Button favScope = scopeChip("★  Favoritos");
        favScope.setOnClickListener(v -> {
            favoritesMode = true;
            updateScopeStyles();
            applyFilter();
        });
        scopeButtons[3] = favScope;
        scopeRow.addView(favScope);

        scopes.addView(scopeRow);
        root.addView(scopes, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        LinearLayout heroWrap = new LinearLayout(this);
        heroWrap.setOrientation(LinearLayout.VERTICAL);
        heroWrap.setPadding(dp(12), 0, dp(12), 0);

        LinearLayout playerCard = new LinearLayout(this);
        playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setBackground(rounded(Color.BLACK, 20));
        playerCard.setClipToOutline(true);

        LinearLayout liveTop = new LinearLayout(this);
        liveTop.setGravity(Gravity.CENTER_VERTICAL);
        liveTop.setPadding(dp(12), dp(9), dp(12), dp(7));
        liveTop.setBackgroundColor(Color.rgb(7, 17, 28));

        TextView liveBadge = text("●  EN VIVO", 11, Color.WHITE, true);
        liveBadge.setPadding(dp(9), dp(5), dp(9), dp(5));
        liveBadge.setBackground(rounded(RED, 99));
        liveTop.addView(liveBadge);

        TextView liveHint = text("  Streaming", 11, MUTED, false);
        liveTop.addView(liveHint);

        playerCard.addView(liveTop);

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setKeepScreenOn(true);
        playerNormalHeight = dp(205);
        playerCard.addView(playerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, playerNormalHeight));

        heroWrap.addView(playerCard);
        root.addView(heroWrap);

        bodyView = new LinearLayout(this);
        bodyView.setOrientation(LinearLayout.VERTICAL);
        bodyView.setPadding(dp(12), dp(10), dp(12), 0);

        LinearLayout programCard = new LinearLayout(this);
        programCard.setOrientation(LinearLayout.VERTICAL);
        programCard.setPadding(dp(15), dp(13), dp(15), dp(12));
        programCard.setBackground(rounded(SURFACE, 18));

        LinearLayout channelHeader = new LinearLayout(this);
        channelHeader.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout channelText = new LinearLayout(this);
        channelText.setOrientation(LinearLayout.VERTICAL);

        channelTitle = text("Selecciona un canal", 20, Color.WHITE, true);
        channelGroup = text("TV en Vivo", 12, BLUE, true);
        channelText.addView(channelTitle);
        channelText.addView(channelGroup);
        channelHeader.addView(channelText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView hdBadge = text("HD", 11, Color.WHITE, true);
        hdBadge.setPadding(dp(8), dp(5), dp(8), dp(5));
        hdBadge.setBackground(rounded(SURFACE_3, 7));
        channelHeader.addView(hdBadge);

        programCard.addView(channelHeader);

        TextView nowLabel = text("AHORA", 10, Color.WHITE, true);
        nowLabel.setPadding(dp(7), dp(4), dp(7), dp(4));
        nowLabel.setBackground(rounded(RED, 6));
        LinearLayout.LayoutParams nowLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nowLabelParams.topMargin = dp(12);
        programCard.addView(nowLabel, nowLabelParams);

        nowText = text("Selecciona un canal para comenzar", 14, Color.WHITE, false);
        nowText.setPadding(0, dp(6), 0, 0);
        programCard.addView(nowText);

        View progressTrack = new View(this);
        progressTrack.setBackground(rounded(BLUE, 99));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                dp(150), dp(4));
        progressParams.topMargin = dp(8);
        progressParams.bottomMargin = dp(8);
        programCard.addView(progressTrack, progressParams);

        nextText = text("Siguiente: —", 12, MUTED, false);
        programCard.addView(nextText);

        bodyView.addView(programCard);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(9), 0, dp(5));

        favoriteAction = actionButton("☆", "Favorito");
        favoriteAction.setOnClickListener(v -> {
            if (selectedChannel != null) toggleFavorite(selectedChannel);
            else Toast.makeText(this, "Selecciona un canal primero.", Toast.LENGTH_SHORT).show();
        });

        TextView pip = actionButton("▣", "Mini TV");
        pip.setOnClickListener(v -> enterPip());

        TextView refresh = actionButton("↻", "Actualizar");
        refresh.setOnClickListener(v -> loadScope(currentScope));

        actions.addView(favoriteAction, actionLayout());
        actions.addView(pip, actionLayout());
        actions.addView(refresh, actionLayout());
        bodyView.addView(actions);

        TextView featuredTitle = sectionTitle("Canales destacados");
        bodyView.addView(featuredTitle);

        HorizontalScrollView featuredScroll = new HorizontalScrollView(this);
        featuredScroll.setHorizontalScrollBarEnabled(false);
        featuredScroll.setClipToPadding(false);

        featuredRow = new LinearLayout(this);
        featuredRow.setOrientation(LinearLayout.HORIZONTAL);
        featuredRow.setPadding(0, dp(3), dp(8), dp(6));
        featuredScroll.addView(featuredRow);
        bodyView.addView(featuredScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(0, dp(5), 0, dp(5));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("⌕  Buscar canal");
        searchInput.setHintTextColor(Color.rgb(105, 130, 154));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(14);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        searchInput.setBackground(rounded(SURFACE, 14));
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(46), 1f));

        categorySpinner = new Spinner(this);
        categorySpinner.setAdapter(spinnerAdapter(new String[]{"Todos"}));
        LinearLayout.LayoutParams catParams = new LinearLayout.LayoutParams(dp(118), dp(46));
        catParams.setMarginStart(dp(8));
        searchRow.addView(categorySpinner, catParams);
        bodyView.addView(searchRow);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(dp(3), dp(4), dp(3), dp(4));

        TextView allTitle = sectionTitle("Todos los canales");
        allTitle.setTextSize(17);
        statusRow.addView(allTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusText = text("Cargando…", 11, MUTED, false);
        statusRow.addView(statusText);

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(dp(24), dp(24));
        pb.setMarginStart(dp(5));
        statusRow.addView(progressBar, pb);
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

        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(8), dp(5), dp(8), dp(5));
        bottomNav.setBackgroundColor(Color.rgb(5, 15, 25));

        TextView home = bottomTab("⌂", "Inicio", true);
        TextView channels = bottomTab("▤", "Canales", false);
        TextView guide = bottomTab("▦", "Guía", false);
        TextView fav = bottomTab("★", "Favoritos", false);

        home.setOnClickListener(v -> listView.smoothScrollToPosition(0));
        channels.setOnClickListener(v -> searchInput.requestFocus());
        guide.setOnClickListener(v -> Toast.makeText(this,
                selectedChannel == null ? "Selecciona un canal para consultar su guía."
                        : "La guía del canal aparece debajo del reproductor.",
                Toast.LENGTH_SHORT).show());
        fav.setOnClickListener(v -> {
            favoritesMode = true;
            updateScopeStyles();
            applyFilter();
        });

        bottomNav.addView(home, bottomLayout());
        bottomNav.addView(channels, bottomLayout());
        bottomNav.addView(guide, bottomLayout());
        bottomNav.addView(fav, bottomLayout());

        root.addView(bottomNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        setContentView(root);
    }

    private void setupInteractions() {
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
    }

    private void loadScope(int position) {
        if (position < 0 || position >= SCOPE_URLS.length) position = 0;
        currentScope = position;
        favoritesMode = false;
        updateScopeStyles();

        if (!hasInternet()) {
            statusText.setText("Sin conexión");
            connectionText.setText(" Offline");
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Necesitas Internet para cargar y reproducir TV.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        connectionText.setText(" Online");
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Actualizando…");
        searchInput.setText("");

        final int selectedPosition = position;
        m3uRepository.load(SCOPE_URLS[position], new M3uRepository.Callback() {
            @Override
            public void onLoaded(List<Channel> channels) {
                runOnUiThread(() -> {
                    if (currentScope != selectedPosition) return;
                    allChannels.clear();
                    allChannels.addAll(channels);
                    rebuildCategories();
                    buildFeatured(channels);
                    applyFilter();
                    progressBar.setVisibility(View.GONE);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Error al cargar");
                    Toast.makeText(MainActivity.this,
                            "No fue posible actualizar los canales.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void rebuildCategories() {
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Channel channel : allChannels) {
            if (!channel.group.trim().isEmpty()) categories.add(channel.group);
        }
        List<String> values = new ArrayList<>();
        values.add("Todos");
        values.addAll(categories);
        categorySpinner.setAdapter(spinnerAdapter(values.toArray(new String[0])));
        categorySpinner.setSelection(0);
    }

    private void buildFeatured(List<Channel> channels) {
        featuredRow.removeAllViews();
        int count = Math.min(8, channels.size());
        for (int i = 0; i < count; i++) {
            Channel channel = channels.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(9), dp(8), dp(9), dp(7));
            card.setBackground(rounded(SURFACE, 15));
            card.setClickable(true);

            String initials = initials(channel.name);
            TextView logo = text(initials, 18, BLUE, true);
            logo.setGravity(Gravity.CENTER);
            logo.setBackground(rounded(SURFACE_3, 11));
            card.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(42)));

            TextView name = text(channel.name, 11, Color.WHITE, true);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    dp(94), ViewGroup.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = dp(5);
            card.addView(name, nameParams);

            card.setOnClickListener(v -> playChannel(channel));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    dp(105), dp(84));
            cardParams.setMarginEnd(dp(8));
            featuredRow.addView(card, cardParams);
        }
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "TV";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private void applyFilter() {
        if (channelAdapter == null) return;

        String query = searchInput == null ? "" :
                searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        String category = categorySpinner == null || categorySpinner.getSelectedItem() == null
                ? "Todos" : categorySpinner.getSelectedItem().toString();

        List<Channel> filtered = new ArrayList<>();
        for (Channel channel : allChannels) {
            boolean searchMatch = query.isEmpty()
                    || channel.name.toLowerCase(Locale.ROOT).contains(query)
                    || channel.group.toLowerCase(Locale.ROOT).contains(query);
            boolean categoryMatch = "Todos".equals(category)
                    || channel.group.equalsIgnoreCase(category);
            boolean favoriteMatch = !favoritesMode || favorites.contains(channel.key());

            if (searchMatch && categoryMatch && favoriteMatch) filtered.add(channel);
        }

        channelAdapter.setChannels(filtered);
        channelAdapter.setFavorites(favorites);
        statusText.setText(filtered.size() + " canales");
    }

    @OptIn(markerClass = UnstableApi.class)
    private void playChannel(Channel channel) {
        selectedChannel = channel;
        channelAdapter.setSelected(channel);

        channelTitle.setText(channel.name);
        channelGroup.setText(channel.group + "  ·  " + SCOPE_LABELS[currentScope].replace("🇲🇽  ", "").replace("◎  ", ""));
        nowText.setText("Consultando programación…");
        nextText.setText("Siguiente: —");
        updateFavoriteAction();

        String userAgent = channel.userAgent.trim().isEmpty()
                ? "Mozilla/5.0 (Linux; Android) TV-en-Vivo/3.0"
                : channel.userAgent;

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000);

        Map<String, String> headers = new HashMap<>();
        if (!channel.referrer.trim().isEmpty()) {
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

            if (!info.message.trim().isEmpty()) {
                nowText.setText(info.message);
                nextText.setText("");
                return;
            }

            nowText.setText(!info.nowTitle.trim().isEmpty()
                    ? info.nowTime + "  ·  " + info.nowTitle
                    : "Ahora: sin datos");

            nextText.setText(!info.nextTitle.trim().isEmpty()
                    ? "Siguiente  " + info.nextTime + "  ·  " + info.nextTitle
                    : "Siguiente: sin datos");
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
        updateFavoriteAction();
        applyFilter();
    }

    private void updateFavoriteAction() {
        if (favoriteAction == null) return;
        boolean active = selectedChannel != null && favorites.contains(selectedChannel.key());
        favoriteAction.setText((active ? "★" : "☆") + "\nFavorito");
        favoriteAction.setTextColor(active ? Color.rgb(250, 204, 21) : Color.WHITE);
    }

    private void updateScopeStyles() {
        if (scopeButtons == null) return;
        for (int i = 0; i < scopeButtons.length; i++) {
            boolean active = favoritesMode ? i == 3 : i == currentScope;
            scopeButtons[i].setBackground(rounded(active ? BLUE_DARK : SURFACE, 99));
            scopeButtons[i].setTextColor(active ? Color.WHITE : MUTED);
        }
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
            Toast.makeText(this, "Mini TV requiere Android 8 o superior.", Toast.LENGTH_SHORT).show();
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
                && selectedChannel != null && player != null && player.isPlaying()
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
        if (scopeRow != null && scopeRow.getParent() != null) {
            View parent = (View) scopeRow.getParent().getParent();
            if (parent != null) parent.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        }
        ViewGroup.LayoutParams params = playerView.getLayoutParams();
        params.height = isInPictureInPictureMode
                ? ViewGroup.LayoutParams.MATCH_PARENT : playerNormalHeight;
        playerView.setLayoutParams(params);
        playerView.setUseController(!isInPictureInPictureMode);
    }

    private Button scopeChip(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(MUTED);
        button.setPadding(dp(15), 0, dp(15), 0);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setBackground(rounded(SURFACE, 99));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        params.setMarginEnd(dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private TextView actionButton(String icon, String label) {
        TextView view = text(icon + "\n" + label, 12, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(7), dp(8), dp(7));
        view.setBackground(rounded(SURFACE, 14));
        return view;
    }

    private LinearLayout.LayoutParams actionLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(58), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private TextView bottomTab(String icon, String label, boolean active) {
        TextView view = text(icon + "\n" + label, 10, active ? BLUE : MUTED, active);
        view.setGravity(Gravity.CENTER);
        view.setLineSpacing(0, 0.9f);
        return view;
    }

    private LinearLayout.LayoutParams bottomLayout() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 18, Color.WHITE, true);
        view.setPadding(dp(2), dp(5), 0, dp(3));
        return view;
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
                    text.setTextSize(12);
                    text.setGravity(Gravity.CENTER);
                    text.setPadding(dp(9), 0, dp(7), 0);
                    text.setBackground(rounded(SURFACE, 14));
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
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
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
        if (!isInPictureInPictureMode() && player != null) player.setPlayWhenReady(false);
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
