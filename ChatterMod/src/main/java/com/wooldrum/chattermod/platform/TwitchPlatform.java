package com.wooldrum.chattermod.platform;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.TwitchChatBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.wooldrum.chattermod.ChatterMod;
import com.wooldrum.chattermod.ChatterModConfig;

import java.util.function.Consumer;

public class TwitchPlatform implements ChatPlatform {

    private final ChatterModConfig.TwitchAccount account;
    private TwitchChat twitchChat;
    private Consumer<ChatMessage> messageConsumer;

    public TwitchPlatform(ChatterModConfig.TwitchAccount account) {
        this.account = account;
    }

    @Override
    public void onMessage(Consumer<ChatMessage> consumer) {
        this.messageConsumer = consumer;
    }

    @Override
    public void connect() {
        if (account.oauthToken().isBlank() || account.oauthToken().equals("YOUR_OAUTH_TOKEN_HERE")) {
            ChatterMod.LOGGER.error("[Twitch] Cannot connect to channel '{}': OAuth token is missing.", account.channelName());
            return;
        }
        
        OAuth2Credential credential = new OAuth2Credential("twitch", account.oauthToken());

        twitchChat = TwitchChatBuilder.builder()
                .withChatAccount(credential)
                .withEnableMembershipEvents(false) // No need for join/part messages
                .build();

        twitchChat.joinChannel(account.channelName());

        twitchChat.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
            String author = event.getUser().getName();
            String message = event.getMessage();
            messageConsumer.accept(new ChatMessage(author, message, ChatMessage.Platform.TWITCH));
        });

        ChatterMod.LOGGER.info("[Twitch] Connected to channel: {}", account.channelName());
    }

    @Override
    public void disconnect() {
        if (twitchChat != null) {
            twitchChat.disconnect();
            ChatterMod.LOGGER.info("[Twitch] Disconnected from channel: {}", account.channelName());
        }
    }
}
