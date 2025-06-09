package com.wooldrum.chattermod;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.text.Text;

public class ChatterMod implements ClientModInitializer {
    private static final HttpClient http = HttpClient.newHttpClient();
    private final ChatterModConfig cfg = new ChatterModConfig();

    @Override
    public void onInitializeClient() {
        // your init code here...
        // e.g. register your /chat command with resolveLiveChatId() and reply(...)
    }

    /**
     * Safely resolve the active liveChatId for the configured channelId.
     * Returns empty string if no live stream is found or on any error.
     */
    private String resolveLiveChatId() {
        if (cfg.channelId.isBlank()) return "";

        try {
            // 1) Find live video ID
            String searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet"
                    + "&channelId=" + URLEncoder.encode(cfg.channelId, StandardCharsets.UTF_8)
                    + "&eventType=live&type=video"
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);
            HttpResponse<String> searchRes = http.send(
                HttpRequest.newBuilder(URI.create(searchUrl))
                           .timeout(Duration.ofSeconds(10))
                           .GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            JsonObject searchJson = JsonParser.parseString(searchRes.body())
                                              .getAsJsonObject();
            JsonArray searchItems = null;
            if (searchJson.has("items") && searchJson.get("items").isJsonArray()) {
                searchItems = searchJson.getAsJsonArray("items");
            }
            if (searchItems == null || searchItems.size() == 0) {
                return "";
            }
            String videoId = searchItems
                                .get(0)
                                .getAsJsonObject()
                                .getAsJsonObject("id")
                                .get("videoId").getAsString();

            // 2) Fetch its liveStreamingDetails
            String detailUrl = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails"
                    + "&id=" + URLEncoder.encode(videoId, StandardCharsets.UTF_8)
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);
            HttpResponse<String> detailRes = http.send(
                HttpRequest.newBuilder(URI.create(detailUrl))
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
            JsonObject liveDetails = detailItems
                                        .get(0)
                                        .getAsJsonObject()
                                        .getAsJsonObject("liveStreamingDetails");
            return liveDetails.has("activeLiveChatId")
                    ? liveDetails.get("activeLiveChatId").getAsString()
                    : "";

        } catch (IOException | InterruptedException | JsonParseException e) {
            e.printStackTrace();
            return "";
        }
    }

    private static void reply(ClientCommandSource src, String message) {
        src.sendFeedback(Text.literal(message));
    }
}
