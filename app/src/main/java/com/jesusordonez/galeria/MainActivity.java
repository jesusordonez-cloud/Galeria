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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
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
    private static final int BG = Color.rgb(3, 9, 16);
    private static final int PANEL = Color.rgb(8, 26, 43);
    private static final int PANEL_2 = Color.rgb(10, 43, 67);
    private static final int BLUE = Color.rgb(22, 174, 255);
    private static final int BLUE_DARK = Color.rgb(5, 126, 242);
    private static final int MUTED = Color.rgb(180, 201, 219);
    private static final int GREEN = Color.rgb(74, 222, 128);
    private static final int RED = Color.rgb(239, 43, 63);

    private static final String[] SCOPE_LABELS = {"🇲🇽  México", "Español", "Mundo"};
    private static final String[] SCOPE_URLS = {
            "https://iptv-org.github.io/iptv/countries/mx.m3u",
            "https://iptv-org.github.io/iptv/languages/spa.m3u",
            "https://iptv-org.github.io/iptv/index.m3u"
    };

    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout headerContent;
    private LinearLayout featuredRow;
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
    private LinearLayout bottomNav;
    private LinearLayout playerCard;
    private LinearLayout rootView;
    private PlayerView fullscreenPlayerView;
    private boolean isFullscreen = false;

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
        listView.setOnItemClickListener((parent, view, position, id) -> {
            int adapterPosition = position - listView.getHeaderViewsCount();
            if (adapterPosition >= 0 && adapterPosition < channelAdapter.getCount()) {
                playChannel(channelAdapter.getItem(adapterPosition));
            }
        });

        setupInteractions();
        loadScope(0);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        rootView = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(appBackground());

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

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setBackgroundColor(Color.TRANSPARENT);
        listView.setClipToPadding(false);
        listView.setPadding(dp(10), 0, dp(10), dp(16));

        headerContent = buildHeaderContent();
        listView.addHeaderView(headerContent, null, false);

        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomNav = buildBottomNav();
        root.addView(bottomNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        setContentView(root);
    }

    private LinearLayout buildHeaderContent() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(2), dp(10), dp(2), dp(8));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(dp(8), dp(3), dp(8), dp(8));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);

        TextView brandTitle = text("TV EN VIVO", 27, Color.WHITE, true);
        brandTitle.setLetterSpacing(-0.02f);
        TextView brandSub = text("Tu televisión, en cualquier lugar", 13, MUTED, false);
        brandSub.setPadding(0, dp(2), 0, 0);

        brand.addView(brandTitle);
        brand.addView(brandSub);
        brandRow.addView(brand, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout connection = new LinearLayout(this);
        connection.setGravity(Gravity.CENTER_VERTICAL);
        connection.setPadding(dp(11), dp(7), dp(11), dp(7));
        connection.setBackground(glassPill(false));

        TextView dot = text("●", 12, GREEN, true);
        connectionText = text("  Online", 12, Color.WHITE, true);
        connection.addView(dot);
        connection.addView(connectionText);
        brandRow.addView(connection);

        header.addView(brandRow);

        HorizontalScrollView scopes = new HorizontalScrollView(this);
        scopes.setHorizontalScrollBarEnabled(false);
        scopes.setClipToPadding(false);
        scopes.setPadding(dp(4), dp(4), dp(4), dp(9));

        LinearLayout scopeRow = new LinearLayout(this);
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
        header.addView(scopes, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        playerCard = new LinearLayout(this);
        playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setPadding(dp(1), dp(1), dp(1), dp(1));
        playerCard.setBackground(glassCard(22));
        playerCard.setClipToOutline(true);
        playerCard.setElevation(dp(8));

        LinearLayout liveTop = new LinearLayout(this);
        liveTop.setGravity(Gravity.CENTER_VERTICAL);
        liveTop.setPadding(dp(12), dp(10), dp(12), dp(9));
        liveTop.setBackground(glassStrip());

        TextView liveBadge = text("●  EN VIVO", 12, Color.WHITE, true);
        liveBadge.setPadding(dp(9), dp(5), dp(9), dp(5));
        liveBadge.setBackground(rounded(RED, 99));
        liveTop.addView(liveBadge);

        TextView stream = text("   Streaming en directo", 12, MUTED, false);
        liveTop.addView(stream);
        playerCard.addView(liveTop);

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setKeepScreenOn(true);
        playerNormalHeight = dp(220);
        playerCard.addView(playerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, playerNormalHeight));

        LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playerParams.setMargins(dp(4), 0, dp(4), dp(10));
        header.addView(playerCard, playerParams);

        LinearLayout programCard = new LinearLayout(this);
        programCard.setOrientation(LinearLayout.VERTICAL);
        programCard.setPadding(dp(16), dp(15), dp(16), dp(14));
        programCard.setBackground(glassCard(20));
        programCard.setElevation(dp(5));

        LinearLayout channelHeader = new LinearLayout(this);
        channelHeader.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout channelText = new LinearLayout(this);
        channelText.setOrientation(LinearLayout.VERTICAL);

        channelTitle = text("Selecciona un canal", 22, Color.WHITE, true);
        channelGroup = text("TV en Vivo", 14, BLUE, true);
        channelGroup.setPadding(0, dp(2), 0, 0);
        channelText.addView(channelTitle);
        channelText.addView(channelGroup);
        channelHeader.addView(channelText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView hdBadge = text("HD", 12, Color.WHITE, true);
        hdBadge.setPadding(dp(9), dp(6), dp(9), dp(6));
        hdBadge.setBackground(glassPill(false));
        channelHeader.addView(hdBadge);

        programCard.addView(channelHeader);

        TextView nowLabel = text("AHORA", 11, Color.WHITE, true);
        nowLabel.setPadding(dp(8), dp(4), dp(8), dp(4));
        nowLabel.setBackground(rounded(RED, 7));
        LinearLayout.LayoutParams nowLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nowLabelParams.topMargin = dp(13);
        programCard.addView(nowLabel, nowLabelParams);

        nowText = text("Selecciona un canal para comenzar", 16, Color.WHITE, false);
        nowText.setPadding(0, dp(7), 0, 0);
        programCard.addView(nowText);

        View progressTrack = new View(this);
        progressTrack.setBackground(rounded(BLUE, 99));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                dp(190), dp(5));
        progressParams.topMargin = dp(9);
        progressParams.bottomMargin = dp(9);
        programCard.addView(progressTrack, progressParams);

        nextText = text("Siguiente: —", 14, MUTED, false);
        programCard.addView(nextText);

        LinearLayout.LayoutParams programParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        programParams.setMargins(dp(4), 0, dp(4), dp(8));
        header.addView(programCard, programParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(dp(1), dp(2), dp(1), dp(8));

        favoriteAction = actionButton("☆", "Favorito");
        favoriteAction.setOnClickListener(v -> {
            if (selectedChannel != null) toggleFavorite(selectedChannel);
            else Toast.makeText(this, "Selecciona un canal primero.", Toast.LENGTH_SHORT).show();
        });

        TextView pip = actionButton("▣", "Mini TV");
        pip.setOnClickListener(v -> enterPip());

        TextView fullscreen = actionButton("⛶", "Pantalla");
        fullscreen.setOnClickListener(v -> enterFullscreen());

        TextView refresh = actionButton("↻", "Actualizar");
        refresh.setOnClickListener(v -> loadScope(currentScope));

        actions.addView(favoriteAction, actionLayout());
        actions.addView(fullscreen, actionLayout());
        actions.addView(pip, actionLayout());
        actions.addView(refresh, actionLayout());
        header.addView(actions);

        header.addView(sectionTitle("Canales destacados"));

        HorizontalScrollView featuredScroll = new HorizontalScrollView(this);
        featuredScroll.setHorizontalScrollBarEnabled(false);
        featuredScroll.setClipToPadding(false);

        featuredRow = new LinearLayout(this);
        featuredRow.setOrientation(LinearLayout.HORIZONTAL);
        featuredRow.setPadding(dp(2), dp(4), dp(8), dp(9));
        featuredScroll.addView(featuredRow);
        header.addView(featuredScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(112)));

        header.addView(sectionTitle("Explorar canales"));

        LinearLayout searchCard = new LinearLayout(this);
        searchCard.setOrientation(LinearLayout.VERTICAL);
        searchCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        searchCard.setBackground(glassCard(18));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("⌕  Buscar canal");
        searchInput.setHintTextColor(Color.rgb(126, 153, 176));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(16);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        searchInput.setBackground(glassInput());
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(50), 1f));

        categorySpinner = new Spinner(this);
        categorySpinner.setAdapter(spinnerAdapter(new String[]{"Todos"}));
        LinearLayout.LayoutParams catParams = new LinearLayout.LayoutParams(dp(124), dp(50));
        catParams.setMarginStart(dp(8));
        searchRow.addView(categorySpinner, catParams);
        searchCard.addView(searchRow);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(dp(4), dp(10), dp(4), 0);

        TextView allTitle = text("Todos los canales", 18, Color.WHITE, true);
        statusRow.addView(allTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusText = text("Cargando…", 13, MUTED, false);
        statusRow.addView(statusText);

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(dp(26), dp(26));
        pb.setMarginStart(dp(6));
        statusRow.addView(progressBar, pb);

        searchCard.addView(statusRow);

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(dp(4), 0, dp(4), dp(10));
        header.addView(searchCard, searchParams);

        return header;
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(5), dp(8), dp(5));
        nav.setBackground(glassNav());

        TextView home = bottomTab("⌂", "Inicio", true);
        TextView channels = bottomTab("▤", "Canales", false);
        TextView guide = bottomTab("▦", "Guía TV", false);
        TextView fav = bottomTab("★", "Favoritos", false);

        home.setOnClickListener(v -> listView.smoothScrollToPosition(0));
        channels.setOnClickListener(v -> {
            listView.smoothScrollToPosition(0);
            searchInput.requestFocus();
        });
        guide.setOnClickListener(v -> Toast.makeText(this,
                selectedChannel == null
                        ? "Selecciona un canal para consultar su guía."
                        : "La programación actual aparece en la tarjeta del canal.",
                Toast.LENGTH_SHORT).show());
        fav.setOnClickListener(v -> {
            favoritesMode = true;
            updateScopeStyles();
            applyFilter();
            listView.smoothScrollToPosition(0);
        });

        nav.addView(home, bottomLayout());
        nav.addView(channels, bottomLayout());
        nav.addView(guide, bottomLayout());
        nav.addView(fav, bottomLayout());
        return nav;
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
            connectionText.setText("  Offline");
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Necesitas Internet para cargar y reproducir TV.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        connectionText.setText("  Online");
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Actualizando…");
        searchInput.setText("");

        final int selectedPosition = position;
        m3uRepository.load(SCOPE_URLS[position], new M3uRepository.Callback() {
            @Override
            public void onLoaded(List<Channel> channels) {
                List<Channel> prepared = prioritizeChannels(channels, selectedPosition);
                runOnUiThread(() -> {
                    if (currentScope != selectedPosition) return;
                    allChannels.clear();
                    allChannels.addAll(prepared);
                    rebuildCategories();
                    buildFeatured(prepared);
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

    private List<Channel> prioritizeChannels(List<Channel> source, int scope) {
        List<Channel> result = new ArrayList<>(source);
        int starsIndex = -1;
        for (int i = 0; i < result.size(); i++) {
            Channel channel = result.get(i);
            String name = channel.name.toLowerCase(Locale.ROOT);
            String id = channel.id.toLowerCase(Locale.ROOT);
            if (name.contains("las estrellas") || id.contains("lasestrellas.mx")) {
                starsIndex = i;
                break;
            }
        }

        if (starsIndex >= 0) {
            Channel stars = result.remove(starsIndex);
            result.add(0, stars);
        } else if (scope == 0) {
            result.add(0, new Channel(
                    "LasEstrellas.mx@SD",
                    "Las Estrellas",
                    "",
                    "México",
                    "https://channel01-onlymex.akamaized.net/hls/live/2022749/event01/index.m3u8",
                    "",
                    ""
            ));
        }
        return result;
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
        int count = Math.min(10, channels.size());
        for (int i = 0; i < count; i++) {
            Channel channel = channels.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(10), dp(9), dp(10), dp(8));
            card.setBackground(glassCard(16));
            card.setClickable(true);
            card.setElevation(dp(3));

            TextView logo = text(initials(channel.name), 19, BLUE, true);
            logo.setGravity(Gravity.CENTER);
            logo.setBackground(glassPill(false));
            card.addView(logo, new LinearLayout.LayoutParams(dp(50), dp(46)));

            TextView name = text(channel.name, 12, Color.WHITE, true);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    dp(104), ViewGroup.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = dp(6);
            card.addView(name, nameParams);

            card.setOnClickListener(v -> playChannel(channel));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    dp(116), dp(96));
            cardParams.setMarginEnd(dp(9));
            featuredRow.addView(card, cardParams);
        }
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "TV";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length()))
                    .toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1))
                .toUpperCase(Locale.ROOT);
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
        channelGroup.setText(channel.group + "  ·  "
                + SCOPE_LABELS[currentScope].replace("🇲🇽  ", ""));
        nowText.setText("Consultando programación…");
        nextText.setText("Siguiente: —");
        updateFavoriteAction();

        String userAgent = channel.userAgent.trim().isEmpty()
                ? "Mozilla/5.0 (Linux; Android) TV-en-Vivo/4.0"
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

        listView.smoothScrollToPosition(0);

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
            scopeButtons[i].setBackground(active ? selectedChip() : glassPill(false));
            scopeButtons[i].setTextColor(active ? Color.WHITE : MUTED);
        }
    }

    private boolean hasInternet() {
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void enterFullscreen() {
        if (isFullscreen) return;
        if (selectedChannel == null) {
            Toast.makeText(this, "Primero reproduce un canal.", Toast.LENGTH_SHORT).show();
            return;
        }

        isFullscreen = true;

        playerView.setPlayer(null);

        FrameLayout fullscreenRoot = new FrameLayout(this);
        fullscreenRoot.setBackgroundColor(Color.BLACK);

        fullscreenPlayerView = new PlayerView(this);
        fullscreenPlayerView.setBackgroundColor(Color.BLACK);
        fullscreenPlayerView.setUseController(true);
        fullscreenPlayerView.setControllerAutoShow(true);
        fullscreenPlayerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        fullscreenPlayerView.setKeepScreenOn(true);
        fullscreenPlayerView.setPlayer(player);

        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        fullscreenRoot.addView(fullscreenPlayerView, videoParams);

        TextView close = text("×", 30, Color.WHITE, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(glassPill(false));
        close.setOnClickListener(v -> exitFullscreen());

        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.TOP | Gravity.END
        );
        closeParams.topMargin = dp(18);
        closeParams.rightMargin = dp(18);
        fullscreenRoot.addView(close, closeParams);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(fullscreenRoot);
    }

    private void exitFullscreen() {
        if (!isFullscreen) return;

        isFullscreen = false;

        if (fullscreenPlayerView != null) {
            fullscreenPlayerView.setPlayer(null);
            fullscreenPlayerView = null;
        }

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

        setContentView(rootView);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        rootView.requestApplyInsets();
    }

    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            exitFullscreen();
            return;
        }
        super.onBackPressed();
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Mini TV requiere Android 8 o superior.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedChannel == null) {
            Toast.makeText(this, "Primero reproduce un canal.", Toast.LENGTH_SHORT).show();
            return;
        }
        listView.smoothScrollToPosition(0);
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
                && !isInPictureInPictureMode() && !isFullscreen) {
            enterPip();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPip, newConfig);
        bottomNav.setVisibility(inPip ? View.GONE : View.VISIBLE);
        playerView.setUseController(!inPip);
        if (inPip) {
            listView.smoothScrollToPosition(0);
        }
    }

    private Button scopeChip(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(MUTED);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setBackground(glassPill(false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        params.setMarginEnd(dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private TextView actionButton(String icon, String label) {
        TextView view = text(icon + "\n" + label, 14, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setBackground(glassCard(15));
        return view;
    }

    private LinearLayout.LayoutParams actionLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(68), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private TextView bottomTab(String icon, String label, boolean active) {
        TextView view = text(icon + "\n" + label, 12, active ? BLUE : MUTED, active);
        view.setGravity(Gravity.CENTER);
        view.setLineSpacing(0, 1.0f);
        return view;
    }

    private LinearLayout.LayoutParams bottomLayout() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 20, Color.WHITE, true);
        view.setPadding(dp(7), dp(8), 0, dp(5));
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
                    text.setTextSize(14);
                    text.setGravity(Gravity.CENTER);
                    text.setPadding(dp(10), 0, dp(8), 0);
                    text.setBackground(glassInput());
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                if (view instanceof TextView) {
                    TextView text = (TextView) view;
                    text.setTextColor(Color.WHITE);
                    text.setTextSize(15);
                    text.setPadding(dp(14), dp(14), dp(14), dp(14));
                    text.setBackgroundColor(PANEL);
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

    private GradientDrawable appBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(2, 8, 14), Color.rgb(5, 24, 39), Color.rgb(2, 10, 17)});
    }

    private GradientDrawable glassCard(int radiusDp) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(210, 11, 36, 58),
                        Color.argb(150, 10, 24, 41),
                        Color.argb(130, 16, 67, 92)
                });
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), Color.argb(95, 65, 190, 255));
        return d;
    }

    private GradientDrawable glassPill(boolean strong) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(strong
                ? Color.argb(215, 8, 113, 215)
                : Color.argb(150, 20, 48, 70));
        d.setCornerRadius(dp(99));
        d.setStroke(dp(1), Color.argb(90, 88, 193, 255));
        return d;
    }

    private GradientDrawable selectedChip() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BLUE_DARK, BLUE});
        d.setCornerRadius(dp(99));
        d.setStroke(dp(1), Color.argb(180, 150, 225, 255));
        return d;
    }

    private GradientDrawable glassInput() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.argb(125, 9, 31, 50));
        d.setCornerRadius(dp(14));
        d.setStroke(dp(1), Color.argb(75, 82, 177, 230));
        return d;
    }

    private GradientDrawable glassStrip() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.argb(175, 4, 17, 29));
        d.setCornerRadii(new float[]{
                dp(21), dp(21), dp(21), dp(21), 0, 0, 0, 0
        });
        return d;
    }

    private GradientDrawable glassNav() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.argb(235, 4, 19, 31));
        d.setStroke(dp(1), Color.argb(75, 68, 172, 225));
        return d;
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
        if (fullscreenPlayerView != null) {
            fullscreenPlayerView.setPlayer(null);
            fullscreenPlayerView = null;
        }
        if (playerView != null) playerView.setPlayer(null);
        if (channelAdapter != null) channelAdapter.shutdown();
        m3uRepository.shutdown();
        epgRepository.shutdown();
        if (player != null) player.release();
        super.onDestroy();
    }
}
