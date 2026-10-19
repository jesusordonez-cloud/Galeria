package com.jesusordonez.galeria;

public final class Channel {
    public final String id;
    public final String name;
    public final String logo;
    public final String group;
    public final String url;
    public final String referrer;
    public final String userAgent;

    public Channel(String id, String name, String logo, String group, String url,
                   String referrer, String userAgent) {
        this.id = id == null ? "" : id.trim();
        this.name = name == null || name.isBlank() ? "Canal sin nombre" : name.trim();
        this.logo = logo == null ? "" : logo.trim();
        this.group = group == null || group.isBlank() ? "General" : group.trim();
        this.url = url == null ? "" : url.trim();
        this.referrer = referrer == null ? "" : referrer.trim();
        this.userAgent = userAgent == null ? "" : userAgent.trim();
    }

    public String key() {
        return !id.isBlank() ? id + "|" + url : url;
    }
}
