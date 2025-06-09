package com.wooldrum.chattermod;

import com.wooldrum.chattermod.platform.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Environment(EnvType.CLIENT)
public class ChatterMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ChatterMod");

    private ChatterModConfig config;
    private final List<ChatPlatform> activePlatforms = new ArrayList<>();
    private final BlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>();
    private Thread messageProcessorThread;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing ChatterMod v2.5...");
        this.config = ChatterModConfig.load();
        
        HttpClient httpClient = HttpClient.newHttpClient();

        config.youtubeAccounts.forEach(acc -> activePlatforms.add(new YouTubePlatform(acc, httpClient)));
        config.twitchAccounts.forEach(acc -> activePlatforms.add(new TwitchPlatform(acc)));

        for (ChatPlatform platform : activePlatforms) {
            platform.onMessage(messageQueue::add);
            platform.connect();
        }

        startMessageProcessor();
    }

    private void startMessageProcessor() {
        messageProcessorThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    ChatMessage chatMessage = messageQueue.take();
                    displayInMinecraftChat(chatMessage);
                }
            } catch (InterruptedException e) {
                LOGGER.info("Chat message processor thread interrupted.");
            }
        });
        messageProcessorThread.setDaemon(true);
        messageProcessorThread.setName("ChatterMod-Message-Processor");
        messageProcessorThread.start();
    }

    private void displayInMinecraftChat(ChatMessage msg) {
        Formatting platformColor = Formatting.WHITE;
        String platformTag = "";

        switch (msg.platform()) {
            case YOUTUBE -> {
                platformColor = Formatting.byName(config.youtubeColor.toUpperCase());
                platformTag = "[YT]";
            }
            case TWITCH -> {
                platformColor = Formatting.byName(config.twitchColor.toUpperCase());
                platformTag = "[TW]";
            }
            // KICK case removed
        }
        if (platformColor == null) platformColor = Formatting.WHITE;

        MutableText fullMessage = Text.literal("");
        
        if (config.showPlatformLogo) {
            fullMessage.append(Text.literal(platformTag + " ").formatted(platformColor));
        }
        
        fullMessage.append(Text.literal("<").formatted(Formatting.GRAY));
        fullMessage.append(Text.literal(msg.author()).formatted(platformColor));
        fullMessage.append(Text.literal("> ").formatted(Formatting.GRAY));
        fullMessage.append(Text.literal(msg.message()).formatted(Formatting.WHITE));

        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().inGameHud != null) {
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(fullMessage);
            }
        });
    }
}
