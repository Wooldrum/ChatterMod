package com.wooldrum.chattermod;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.wooldrum.chattermod.platform.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
        LOGGER.info("Initializing ChatterMod BETA 1.0...");
        loadAndConnect();
        startMessageProcessor();
        registerCommands();
    }
    
    private void loadAndConnect() {
        // Disconnect any existing platforms before reloading
        activePlatforms.forEach(ChatPlatform::disconnect);
        activePlatforms.clear();
        
        this.config = ChatterModConfig.load();
        HttpClient httpClient = HttpClient.newHttpClient();

        if (!config.youtubeAccounts.isEmpty()) {
            activePlatforms.add(new YouTubePlatform(config.youtubeAccounts.get(0), httpClient));
        }
        if (!config.twitchAccounts.isEmpty()) {
            activePlatforms.add(new TwitchPlatform(config.twitchAccounts.get(0)));
        }

        for (ChatPlatform platform : activePlatforms) {
            platform.onMessage(messageQueue::add);
            platform.connect();
        }
    }

    private void startMessageProcessor() {
        messageProcessorThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    displayInMinecraftChat(messageQueue.take());
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
        }
        if (platformColor == null) platformColor = Formatting.WHITE;

        MutableText fullMessage = Text.literal("");
        
        if (config.showPlatformLogo) {
            fullMessage.append(Text.literal(platformTag + " ").formatted(platformColor));
        }
        
        MutableText authorText = Text.literal(msg.author());
        if (config.usePlatformColors) {
            authorText.formatted(platformColor);
        }
        
        fullMessage.append(Text.literal("<").formatted(Formatting.GRAY))
                   .append(authorText)
                   .append(Text.literal("> ").formatted(Formatting.GRAY))
                   .append(Text.literal(msg.message()).formatted(Formatting.WHITE));

        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().inGameHud != null) {
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(fullMessage);
            }
        });
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("chattermod")
                // Toggle Commands
                .then(ClientCommandManager.literal("toggle")
                    .then(ClientCommandManager.literal("logos")
                        .executes(c -> {
                            config.showPlatformLogo = !config.showPlatformLogo;
                            config.save();
                            reply(c.getSource(), "Platform logos " + (config.showPlatformLogo ? "enabled." : "disabled."));
                            return 1;
                        }))
                    .then(ClientCommandManager.literal("colors")
                        .executes(c -> {
                            config.usePlatformColors = !config.usePlatformColors;
                            config.save();
                            reply(c.getSource(), "Platform colors " + (config.usePlatformColors ? "enabled." : "disabled."));
                            return 1;
                        }))
                )
                // YouTube Commands
                .then(ClientCommandManager.literal("youtube")
                    .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.literal("apikey")
                            .then(ClientCommandManager.argument("key", StringArgumentType.greedyString())
                                .executes(c -> {
                                    String key = StringArgumentType.getString(c, "key");
                                    String channelId = config.youtubeAccounts.isEmpty() ? "" : config.youtubeAccounts.get(0).channelId();
                                    config.youtubeAccounts.clear();
                                    config.youtubeAccounts.add(new ChatterModConfig.YouTubeAccount(channelId, key));
                                    config.save();
                                    reply(c.getSource(), "YouTube API Key set. Use /chattermod reload to apply.");
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("channel")
                            .then(ClientCommandManager.argument("id", StringArgumentType.string())
                                .executes(c -> {
                                    String id = StringArgumentType.getString(c, "id");
                                    String apiKey = config.youtubeAccounts.isEmpty() ? "" : config.youtubeAccounts.get(0).apiKey();
                                    config.youtubeAccounts.clear();
                                    config.youtubeAccounts.add(new ChatterModConfig.YouTubeAccount(id, apiKey));
                                    config.save();
                                    reply(c.getSource(), "YouTube Channel ID set. Use /chattermod reload to apply.");
                                    return 1;
                                })))
                )
                // Twitch Commands
                .then(ClientCommandManager.literal("twitch")
                    .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.literal("channel")
                            .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                .executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    String token = config.twitchAccounts.isEmpty() ? "" : config.twitchAccounts.get(0).oauthToken();
                                    config.twitchAccounts.clear();
                                    config.twitchAccounts.add(new ChatterModConfig.TwitchAccount(name, token));
                                    config.save();
                                    reply(c.getSource(), "Twitch channel name set. Use /chattermod reload to apply.");
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("token")
                            .then(ClientCommandManager.argument("token", StringArgumentType.greedyString())
                                .executes(c -> {
                                    String token = StringArgumentType.getString(c, "token");
                                    String name = config.twitchAccounts.isEmpty() ? "" : config.twitchAccounts.get(0).channelName();
                                    config.twitchAccounts.clear();
                                    config.twitchAccounts.add(new ChatterModConfig.TwitchAccount(name, token));
                                    config.save();
                                    reply(c.getSource(), "Twitch OAuth token set. Use /chattermod reload to apply.");
                                    return 1;
                                })))
                )
                // Reload Command
                .then(ClientCommandManager.literal("reload")
                    .executes(c -> {
                        reply(c.getSource(), "Reloading ChatterMod configuration and reconnecting...");
                        loadAndConnect();
                        return 1;
                    }))
            )
        );
    }

    private static void reply(FabricClientCommandSource src, String message) {
        src.sendFeedback(Text.literal(message));
    }
}
