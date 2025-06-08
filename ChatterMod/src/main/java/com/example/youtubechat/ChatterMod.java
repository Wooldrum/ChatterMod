// File: src/main/java/com/wooldrum/chattermod/ChatterMod.java
package com.wooldrum.chattermod;

import com.google.gson.*;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * ChatterMod: fetches YouTube live chat and injects into Minecraft chat.
 */
@Environment(EnvType.CLIENT)
public class ChatterMod implements ClientModInitializer {
    private ChatterModConfig config;
    private HttpClient httpClient;
    private ScheduledExecutorService scheduler;
    private String liveChatId;
    private String nextPageToken = "";

    @Override
    public void onInitializeClient() {
        config     = ChatterModConfig.load();
        httpClient = HttpClient.newBuilder()
                               .connectTimeout(Duration.ofSeconds(10))
                               .build();

        liveChatId = config.liveChatId;
        if (liveChatId.isEmpty() && !config.channelId.isEmpty()) {
            liveChatId = fetchLiveChatId(config.apiKey, config.channelId);
            if (liveChatId.isEmpty()) {
                System.err.println("[ChatterMod] ❌ Cannot resolve liveChatId; disabling chat fetch.");
                return;
            }
        } else if (liveChatId.isEmpty()) {
            System.err.println("[ChatterMod] ❌ Both liveChatId & channelId blank; disabling chat fetch.");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChatterMod-Poller");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
            this::pollChat,
            0,
            config.pollIntervalSeconds,
            TimeUnit.SECONDS
        );

        System.out.printf("[ChatterMod] ✅ Polling YouTube chat (liveChatId=%s) every %d seconds.%n",
                          liveChatId, config.pollIntervalSeconds);

        registerCommands();
    }

    private String fetchLiveChatId(String apiKey, String channelId) {
        try {
            // Search for active livestream
            String searchUri = String.format(
                "https://www.googleapis.com/youtube/v3/search?part=snippet" +
                "&channelId=%s&eventType=live&type=video&key=%s",
                URLEncoder.encode(channelId, StandardCharsets.UTF_8),
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
            );
            HttpRequest req1 = HttpRequest.newBuilder()
                                .uri(URI.create(searchUri))
                                .timeout(Duration.ofSeconds(10))
                                .GET().build();
            HttpResponse<String> res1 = httpClient.send(req1, HttpResponse.BodyHandlers.ofString());
            if (res1.statusCode() != 200) {
                System.err.printf("[ChatterMod] search error HTTP %d%n", res1.statusCode());
                return "";
            }
            JsonArray items = JsonParser.parseString(res1.body())
                                        .getAsJsonObject()
                                        .getAsJsonArray("items");
            if (items.isEmpty()) {
                System.err.println("[ChatterMod] No active livestream found for channelId=" + channelId);
                return "";
            }
            String videoId = items.get(0).getAsJsonObject()
                                     .getAsJsonObject("id")
                                     .get("videoId").getAsString();

            // Get liveChatId from videos.list
            String videosUri = String.format(
                "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails" +
                "&id=%s&key=%s",
                URLEncoder.encode(videoId, StandardCharsets.UTF_8),
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
            );
            HttpRequest req2 = HttpRequest.newBuilder()
                                .uri(URI.create(videosUri))
                                .timeout(Duration.ofSeconds(10))
                                .GET().build();
            HttpResponse<String> res2 = httpClient.send(req2, HttpResponse.BodyHandlers.ofString());
            if (res2.statusCode() != 200) {
                System.err.printf("[ChatterMod] videos error HTTP %d%n", res2.statusCode());
                return "";
            }
            JsonObject details = JsonParser.parseString(res2.body())
                                           .getAsJsonObject()
                                           .getAsJsonArray("items")
                                           .get(0).getAsJsonObject()
                                           .getAsJsonObject("liveStreamingDetails");
            return details.has("activeLiveChatId")
                ? details.get("activeLiveChatId").getAsString()
                : "";
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "";
        }
    }

    private void pollChat() {
        try {
            String uri = "https://www.googleapis.com/youtube/v3/liveChat/messages"
                + "?part=snippet,authorDetails"
                + "&liveChatId=" + URLEncoder.encode(liveChatId, StandardCharsets.UTF_8)
                + (nextPageToken.isEmpty() ? "" 
                   : "&pageToken=" + URLEncoder.encode(nextPageToken, StandardCharsets.UTF_8))
                + "&maxResults=200"
                + "&key=" + URLEncoder.encode(config.apiKey, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(uri))
                                .timeout(Duration.ofSeconds(10))
                                .GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                System.err.printf("[ChatterMod] chat error HTTP %d%n", res.statusCode());
                return;
            }

            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            if (root.has("nextPageToken")) {
                nextPageToken = root.get("nextPageToken").getAsString();
            }

            for (JsonElement e : root.getAsJsonArray("items")) {
                JsonObject item    = e.getAsJsonObject();
                JsonObject snip    = item.getAsJsonObject("snippet");
                String text        = snip.getAsJsonObject("textMessageDetails").get("messageText").getAsString();
                JsonObject authDet = item.getAsJsonObject("authorDetails");
                String authorName  = authDet.get("displayName").getAsString();
                displayInChat(authorName, text);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void displayInChat(String author, String message) {
        String line = String.format("[YT] <%s> %s", author, message);
        MinecraftClient.getInstance().execute(() ->
            MinecraftClient.getInstance()
                           .inGameHud
                           .getChatHud()
                           .addMessage(new LiteralText(line))
        );
    }

    /** Registers in-game client commands for configuring the mod. */
    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("chattermod")
                .then(ClientCommandManager.literal("apikey")
                    .then(ClientCommandManager.argument("key", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            config.apiKey = StringArgumentType.getString(ctx, "key");
                            config.store();
                            ((FabricClientCommandSource) ctx.getSource()).sendFeedback(new LiteralText("API key saved"));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("channel")
                    .then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            config.channelId = StringArgumentType.getString(ctx, "id");
                            liveChatId = fetchLiveChatId(config.apiKey, config.channelId);
                            config.liveChatId = liveChatId;
                            config.store();
                            ((FabricClientCommandSource) ctx.getSource()).sendFeedback(new LiteralText("Channel updated"));
                            return 1;
                        }))));
        });
    }
}
