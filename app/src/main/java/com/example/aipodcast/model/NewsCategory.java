package com.example.aipodcast.model;

public enum NewsCategory {
    TECHNOLOGY("technology"),
    ENTERTAINMENT("entertainment"),
    SPORTS("sports"),
    HEALTH("health"),
    POLITICS("politics");

    private final String value;

    NewsCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static NewsCategory fromName(String name) {
        switch (name.toLowerCase()) {
            case "technology": return TECHNOLOGY;
            case "sports": return SPORTS;
            case "entertainment": return ENTERTAINMENT;
            case "health": return HEALTH;
            case "politics": return POLITICS;
            default: return null;
        }
    }

}