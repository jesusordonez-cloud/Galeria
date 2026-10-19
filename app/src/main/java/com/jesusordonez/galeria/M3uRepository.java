package com.jesusordonez.galeria;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class M3uRepository {
    public interface Callback {
        void onLoaded(List<Channel> channels);
        void onError(Exception error);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Pattern ATTR = Pattern.compile("([\\w-]+)=\\\"([^\\\"]*)\\\"");

    public void load(String playlistUrl, Callback callback) {
        executor.execute(() -> {
            try {
                callback.onLoaded(downloadAndParse(playlistUrl));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private List<Channel> downloadAndParse(String playlistUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(playlistUrl).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(45000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "TV-en-Vivo/2.0 Android");

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + " al descargar canales");
        }

        List<Channel> result = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        String pendingId = "";
        String pendingName = "";
        String pendingLogo = "";
        String pendingGroup = "General";
        String pendingReferrer = "";
        String pendingUserAgent = "";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isBlank()) continue;

                if (line.startsWith("#EXTINF:")) {
                    pendingId = "";
                    pendingName = "";
                    pendingLogo = "";
                    pendingGroup = "General";
                    pendingReferrer = "";
                    pendingUserAgent = "";

                    int comma = line.indexOf(',');
                    String meta = comma >= 0 ? line.substring(0, comma) : line;
                    String displayName = comma >= 0 ? line.substring(comma + 1).trim() : "";

                    Matcher matcher = ATTR.matcher(meta);
                    while (matcher.find()) {
                        String key = matcher.group(1);
                        String value = matcher.group(2);
                        if ("tvg-id".equalsIgnoreCase(key)) pendingId = value;
                        else if ("tvg-name".equalsIgnoreCase(key)) pendingName = value;
                        else if ("tvg-logo".equalsIgnoreCase(key)) pendingLogo = value;
                        else if ("group-title".equalsIgnoreCase(key)) pendingGroup = value;
                    }

                    if (!displayName.isBlank()) pendingName = displayName;
                } else if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
                    pendingReferrer = line.substring("#EXTVLCOPT:http-referrer=".length()).trim();
                } else if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                    pendingUserAgent = line.substring("#EXTVLCOPT:http-user-agent=".length()).trim();
                } else if (line.startsWith("#EXTHTTP:")) {
                    try {
                        JSONObject headers = new JSONObject(line.substring("#EXTHTTP:".length()));
                        pendingReferrer = headers.optString("Referer",
                                headers.optString("referrer", pendingReferrer));
                        pendingUserAgent = headers.optString("User-Agent",
                                headers.optString("user-agent", pendingUserAgent));
                    } catch (Exception ignored) {
                    }
                } else if (!line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                    if (!isAdult(pendingName, pendingGroup) && seenUrls.add(line)) {
                        result.add(new Channel(
                                pendingId, pendingName, pendingLogo, pendingGroup, line,
                                pendingReferrer, pendingUserAgent
                        ));
                    }
                    pendingId = "";
                    pendingName = "";
                    pendingLogo = "";
                    pendingGroup = "General";
                    pendingReferrer = "";
                    pendingUserAgent = "";
                }
            }
        } finally {
            connection.disconnect();
        }

        if (result.isEmpty()) {
            throw new IllegalStateException("La lista no contiene canales reproducibles");
        }
        return result;
    }

    private boolean isAdult(String name, String group) {
        String value = ((name == null ? "" : name) + " " + (group == null ? "" : group))
                .toLowerCase(Locale.ROOT);
        return value.contains("xxx") || value.contains("adult") || value.contains("18+")
                || value.contains("porn") || value.contains("erotic");
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
