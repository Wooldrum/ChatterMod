package com.wooldrum.chattermod.platform;

import com.jroy.kick.KickAPI;
import com.jroy.kick.events.ChatMessageEvent;
import com.wooldrum.chattermod.ChatterMod;
import com.wooldrum.chattermod.ChatterModConfig;

import java.util.function.Consumer;

public class KickPlatform implements ChatPlatform {

    private final ChatterModConfig.KickAccount account;
    private KickAPI kickAPI;
    private Consumer<ChatMessage> messageConsumer;

    public KickPlatform(ChatterModConfig.KickAccount account) {
        this.account = account;
    }

    @Override
    public void onMessage(Consumer<ChatMessage> consumer) {
        this.messageConsumer = consumer;
    }

    @Override
    public void connect() {
        try {
            kickAPI = KickAPI.builder().channel(account.channelName()).build();
            kickAPI.connectBlocking();

            kickAPI.getEventManager().onEvent(ChatMessageEvent.class, event -> {
                String author = event.getSender().getUsername();
                String message = event.getContent();
                messageConsumer.accept(new ChatMessage(author, message, ChatMessage.Platform.KICK));
            });
            ChatterMod.LOGGER.info("[Kick] Connected to channel: {}", account.channelName());
        } catch (Exception e) {
            ChatterMod.LOGGER.error("[Kick] Failed to connect to channel '{}'. Is the name correct?", account.channelName(), e);
        }
    }

    @Override
    public void disconnect() {
        if (kickAPI != null) {
            kickAPI.close();
            ChatterMod.LOGGER.info("[Kick] Disconnected from channel: {}", account.channelName());
        }
    }
}
