package com.wooldrum.chattermod.platform;

public record ChatMessage(String author, String message, Platform platform) {
    public enum Platform {
        YOUTUBE,
        TWITCH,
        KICK
    }
}
