package com.wooldrum.chattermod.platform;

import com.wooldrum.chattermod.ChatterMod;
import com.wooldrum.chattermod.ChatterModConfig;
import io.github.oxylca.kick.KickApi;
import io.github.oxylca.kick.KickListener;
import io.github.oxylca.kick.models.ChatMessage;

import java.util.function.Consumer;

public class KickPlatform implements ChatPlatform {

    private final ChatterModConfig.KickAccount account;
    private KickApi kickApi;
    private Consumer<com.wooldrum.chattermod.platform.ChatMessage> messageConsumer;

    public KickPlatform(ChatterModConfig.KickAccount account) {
        this.account = account;
    }

    @Override
    public void onMessage(Consumer<com.wooldrum.chattermod.platform.ChatMessage> consumer) {
        this.messageConsumer = consumer;
    }

    @Override
    public void connect() {
        try {
            // The new library uses a builder pattern and a listener
            kickApi = KickApi.builder().channel(account.channelName()).build();

            kickApi.registerListener(new KickListener() {
                @Override
                public void onChatMessage(io.github.oxylca.kick.models.ChatMessage event) {
                    // Convert the library's message object to our universal ChatMessage
                    String author = event.getSender().getUsername();
                    String message = event.getMessage();
                    messageConsumer.accept(new com.wooldrum.chattermod.platform.ChatMessage(author, message, com.wooldrum.chattermod.platform.ChatMessage.Platform.KICK));
                }

                @Override
                public void onConnected() {
                    ChatterMod.LOGGER.info("[Kick] Connected to channel: {}", account.channelName());
                }

                @Override
                public void onClose(int code, String reason) {
                    ChatterMod.LOGGER.warn("[Kick] Connection closed: {} - {}", code, reason);
                }

                @Override
                public void onFailure(Throwable t) {
                    ChatterMod.LOGGER.error("[Kick] Connection failure for channel '{}'", account.channelName(), t);
                }
            });

            kickApi.connect(); // This is non-blocking
        } catch (Exception e) {
            ChatterMod.LOGGER.error("[Kick] Failed to build API for channel '{}'", account.channelName(), e);
        }
    }

    @Override
    public void disconnect() {
        if (kickApi != null) {
            kickApi.close();
            ChatterMod.LOGGER.info("[Kick] Disconnected from channel: {}", account.channelName());
        }
    }
}
