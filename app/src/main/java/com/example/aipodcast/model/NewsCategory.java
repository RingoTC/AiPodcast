package com.example.aipodcast.model;

public enum NewsCategory {
    ARTS("arts"),
    HOME("home"),
    SCIENCE("science"),
    US("us"),
    WORLD("world");

    private final String value;

    NewsCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}