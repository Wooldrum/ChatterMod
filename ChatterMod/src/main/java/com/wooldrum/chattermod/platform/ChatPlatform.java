package com.wooldrum.chattermod.platform;

import java.util.function.Consumer;

public interface ChatPlatform {
    void connect();
    void disconnect();
    void onMessage(Consumer<ChatMessage> messageConsumer);
}
