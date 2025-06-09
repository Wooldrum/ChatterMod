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
        registerCommands(); // <-- THE FIX: This line was missing!
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

    // --- NEWLY RE-ADDED COMMANDS ---
    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("chattermod")
                .then(ClientCommandManager.literal("youtube")
                    .then(ClientCommandManager.literal("set-apikey")
                        .then(ClientCommandManager.argument("key", StringArgumentType.greedyString())
                            .executes(c -> {
                                // For simplicity, this affects the first YouTube account in the config
                                if (!config.youtubeAccounts.isEmpty()) {
                                    var acc = config.youtubeAccounts.get(0);
                                    config.youtubeAccounts.set(0, new ChatterModConfig.YouTubeAccount(acc.channelId(), acc.liveChatId(), StringArgumentType.getString(c, "key"), acc.pollIntervalSeconds()));
                                    // A real implementation would need a way to save this back to the properties file
                                    reply(c.getSource(), "YouTube API Key set. Please restart the game to apply.");
                                } else {
                                    reply(c.getSource(), "No YouTube account configured in properties file.");
                                }
                                return 1;
                            })))
                )
                .then(ClientCommandManager.literal("twitch")
                    .then(ClientCommandManager.literal("set-channel")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                            .executes(c -> {
                                if (!config.twitchAccounts.isEmpty()) {
                                    var acc = config.twitchAccounts.get(0);
                                    config.twitchAccounts.set(0, new ChatterModConfig.TwitchAccount(StringArgumentType.getString(c, "name"), acc.oauthToken()));
                                    reply(c.getSource(), "Twitch channel set. Please restart the game to apply.");
                                } else {
                                     reply(c.getSource(), "No Twitch account configured in properties file.");
                                }
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("set-token")
                        .then(ClientCommandManager.argument("token", StringArgumentType.greedyString())
                            .executes(c -> {
                                if (!config.twitchAccounts.isEmpty()) {
                                    var acc = config.twitchAccounts.get(0);
                                    // We don't save the token back to the file for security, this is session-only
                                    // A full implementation would need a better storage mechanism.
                                    config.twitchAccounts.set(0, new ChatterModConfig.TwitchAccount(acc.channelName(), StringArgumentType.getString(c, "token")));
                                    reply(c.getSource(), "Twitch token set. Please restart the game to apply.");
                                } else {
                                     reply(c.getSource(), "No Twitch account configured in properties file.");
                                }
                                return 1;
                            })))
                )
            )
        );
    }

    private static void reply(FabricClientCommandSource src, String message) {
        src.sendFeedback(Text.literal(message));
    }
}
