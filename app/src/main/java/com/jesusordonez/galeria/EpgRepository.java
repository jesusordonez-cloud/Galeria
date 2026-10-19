package com.jesusordonez.galeria;

import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EpgRepository {
    public interface Callback {
        void onResult(ProgramInfo info);
    }

    public static final class ProgramInfo {
        public final String nowTitle;
        public final String nowTime;
        public final String nextTitle;
        public final String nextTime;
        public final String message;

        private ProgramInfo(String nowTitle, String nowTime, String nextTitle,
                            String nextTime, String message) {
            this.nowTitle = nowTitle;
            this.nowTime = nowTime;
            this.nextTitle = nextTitle;
            this.nextTime = nextTime;
            this.message = message;
        }

        public static ProgramInfo unavailable(String message) {
            return new ProgramInfo("", "", "", "", message);
        }
    }

    private static final String GUIDES_API = "https://iptv-org.github.io/api/guides.json";
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, String> guideByChannel = new ConcurrentHashMap<>();
    private final Map<String, CacheItem> cache = new ConcurrentHashMap<>();
    private volatile boolean indexLoaded = false;

    public void request(String channelId, Callback callback) {
        if (channelId == null || channelId.isBlank()) {
            callback.onResult(ProgramInfo.unavailable("Guía no disponible para este canal."));
            return;
        }

        CacheItem cached = cache.get(channelId);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 10 * 60 * 1000L) {
            callback.onResult(cached.info);
            return;
        }

        executor.execute(() -> {
            try {
                ensureIndex();
                String guideUrl = guideByChannel.get(channelId);
                if (guideUrl == null && channelId.contains("@")) {
                    guideUrl = guideByChannel.get(channelId.substring(0, channelId.indexOf('@')));
                }
                if (guideUrl == null) {
                    callback.onResult(ProgramInfo.unavailable("No hay guía publicada para este canal."));
                    return;
                }

                ProgramInfo info = parseGuide(guideUrl, channelId);
                cache.put(channelId, new CacheItem(info, System.currentTimeMillis()));
                callback.onResult(info);
            } catch (Exception e) {
                callback.onResult(ProgramInfo.unavailable("No fue posible cargar la guía."));
            }
        });
    }

    private synchronized void ensureIndex() throws Exception {
        if (indexLoaded) return;

        String json = readText(GUIDES_API);
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String channel = item.optString("channel");
            String feed = item.optString("feed");
            if (channel.isBlank()) continue;

            JSONArray sources = item.optJSONArray("sources");
            if (sources == null) continue;

            String selected = null;
            for (int j = 0; j < sources.length(); j++) {
                JSONObject source = sources.optJSONObject(j);
                if (source == null) continue;
                String format = source.optString("format");
                String url = source.optString("url");
                if ("XML".equalsIgnoreCase(format) && url.startsWith("https://")) {
                    selected = url;
                    break;
                }
            }

            if (selected != null) {
                guideByChannel.putIfAbsent(channel, selected);
                if (!feed.isBlank()) guideByChannel.putIfAbsent(channel + "@" + feed, selected);
            }
        }
        indexLoaded = true;
    }

    private ProgramInfo parseGuide(String guideUrl, String channelId) throws Exception {
        HttpURLConnection connection = open(guideUrl);
        long now = System.currentTimeMillis();
        ProgramEntry current = null;
        ProgramEntry next = null;

        try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, null);

            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG || !"programme".equals(parser.getName())) continue;

                String xmlChannel = parser.getAttributeValue(null, "channel");
                String startRaw = parser.getAttributeValue(null, "start");
                String stopRaw = parser.getAttributeValue(null, "stop");

                long start = parseXmlTvTime(startRaw);
                long stop = parseXmlTvTime(stopRaw);
                boolean sameChannel = channelId.equals(xmlChannel)
                        || (channelId.contains("@") && channelId.substring(0, channelId.indexOf('@')).equals(xmlChannel));

                String title = "";
                int depth = 1;
                while (depth > 0) {
                    int inner = parser.next();
                    if (inner == XmlPullParser.START_TAG) {
                        depth++;
                        if (sameChannel && "title".equals(parser.getName())) {
                            title = parser.nextText();
                            depth--;
                        }
                    } else if (inner == XmlPullParser.END_TAG) {
                        depth--;
                    } else if (inner == XmlPullParser.END_DOCUMENT) {
                        break;
                    }
                }

                if (!sameChannel || title.isBlank()) continue;
                ProgramEntry entry = new ProgramEntry(title.trim(), start, stop);

                if (start <= now && now < stop) {
                    current = entry;
                } else if (start > now && (next == null || start < next.start)) {
                    next = entry;
                }

                if (current != null && next != null && start > next.start) break;
            }
        } finally {
            connection.disconnect();
        }

        if (current == null && next == null) {
            return ProgramInfo.unavailable("La guía existe, pero no hay programación disponible ahora.");
        }

        return new ProgramInfo(
                current == null ? "" : current.title,
                current == null ? "" : formatRange(current.start, current.stop),
                next == null ? "" : next.title,
                next == null ? "" : formatRange(next.start, next.stop),
                ""
        );
    }

    private long parseXmlTvTime(String value) {
        if (value == null || value.length() < 14) return 0L;
        List<String> patterns = new ArrayList<>();
        patterns.add("yyyyMMddHHmmss Z");
        patterns.add("yyyyMMddHHmmssZ");
        patterns.add("yyyyMMddHHmmss");

        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                if ("yyyyMMddHHmmss".equals(pattern)) format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsed = format.parse(value.trim());
                if (parsed != null) return parsed.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private String formatRange(long start, long stop) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return format.format(new Date(start)) + " – " + format.format(new Date(stop));
    }

    private String readText(String url) throws Exception {
        HttpURLConnection connection = open(url);
        try (InputStream input = connection.getInputStream();
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(java.nio.charset.StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(45000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "TV-en-Vivo/2.0 Android");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        return connection;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static final class ProgramEntry {
        final String title;
        final long start;
        final long stop;

        ProgramEntry(String title, long start, long stop) {
            this.title = title;
            this.start = start;
            this.stop = stop;
        }
    }

    private static final class CacheItem {
        final ProgramInfo info;
        final long timestamp;

        CacheItem(ProgramInfo info, long timestamp) {
            this.info = info;
            this.timestamp = timestamp;
        }
    }
}
