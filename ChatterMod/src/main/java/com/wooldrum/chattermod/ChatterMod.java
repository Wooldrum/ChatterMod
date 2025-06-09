package com.wooldrum.chattermod;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

@Environment(EnvType.CLIENT)
public class ChatterMod implements ClientModInitializer {

    private ChatterModConfig cfg;
    private HttpClient http;
    private ScheduledExecutorService exec;
    private String liveChatId = "";
    private String nextPageToken = "";

    @Override
    public void onInitializeClient() {
        cfg  = ChatterModConfig.load();
        http = HttpClient.newBuilder()
                         .connectTimeout(Duration.ofSeconds(10))
                         .build();

        liveChatId = cfg.liveChatId.isBlank()
                ? resolveLiveChatId()
                : cfg.liveChatId;

        if (liveChatId.isBlank()) {
            System.err.println("[ChatterMod] No live stream found; mod idle.");
            return;
        }

        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChatterMod-Poller");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleAtFixedRate(this::poll,
                0, cfg.pollIntervalSeconds, TimeUnit.SECONDS);

        registerCommands();
        System.out.printf("[ChatterMod] ✔ polling every %ds (liveChatId=%s)%n",
                          cfg.pollIntervalSeconds, liveChatId);
    }

    // … (poll and sendToChat unchanged) …

    /**
     * Safely resolve the active liveChatId for the configured channelId.
     * Returns "" if no live stream is found or on any parse error.
     */
    private String resolveLiveChatId() {
        if (cfg.channelId.isBlank()) return "";
        try {
            // 1) Search for an active live stream on the channel
            String searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet"
                    + "&channelId=" + URLEncoder.encode(cfg.channelId, StandardCharsets.UTF_8)
                    + "&eventType=live&type=video"
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpResponse<String> searchRes = http.send(
                HttpRequest.newBuilder().uri(URI.create(searchUrl))
                           .timeout(Duration.ofSeconds(10))
                           .GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            JsonObject searchJson = JsonParser.parseString(searchRes.body())
                                              .getAsJsonObject();

            // null-safe fetch of "items"
            JsonArray items = null;
            if (searchJson.has("items") && searchJson.get("items").isJsonArray()) {
                items = searchJson.getAsJsonArray("items");
            }
            if (items == null || items.size() == 0) {
                return "";
            }

            String videoId = items.get(0).getAsJsonObject()
                                .getAsJsonObject("id")
                                .get("videoId").getAsString();

            // 2) Get liveStreamingDetails for that video
            String detailUrl = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails"
                    + "&id=" + URLEncoder.encode(videoId, StandardCharsets.UTF_8)
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpResponse<String> detailRes = http.send(
                HttpRequest.newBuilder().uri(URI.create(detailUrl))
                           .timeout(Duration.ofSeconds(10))
                           .GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            JsonObject detailJson = JsonParser.parseString(detailRes.body())
                                              .getAsJsonObject();

            JsonArray detailItems = null;
            if (detailJson.has("items") && detailJson.get("items").isJsonArray()) {
                detailItems = detailJson.getAsJsonArray("items");
            }
            if (detailItems == null || detailItems.size() == 0) {
                return "";
            }

            JsonObject liveDetails = detailItems.get(0)
                .getAsJsonObject()
                .getAsJsonObject("liveStreamingDetails");

            if (liveDetails.has("activeLiveChatId")) {
                return liveDetails.get("activeLiveChatId").getAsString();
            } else {
                return "";
            }

        } catch (IOException | InterruptedException | JsonParseException e) {
            e.printStackTrace();
            return "";
        }
    }

    // … (registerCommands and reply unchanged) …

    private static void reply(FabricClientCommandSource src, String message) {
        src.sendFeedback(Text.literal(message));
    }
}
