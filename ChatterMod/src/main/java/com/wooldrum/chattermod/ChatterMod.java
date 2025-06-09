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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

@Environment(EnvType.CLIENT)
public class ChatterMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ChatterMod");

    private ChatterModConfig cfg;
    private HttpClient http;
    private ScheduledExecutorService exec;
    private String liveChatId = "";
    private String nextPageToken = "";

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing ChatterMod by Wooldrum...");

        cfg = ChatterModConfig.load();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        liveChatId = cfg.liveChatId.isBlank() ? resolveLiveChatId() : cfg.liveChatId;

        if (liveChatId.isBlank()) {
            LOGGER.warn("Could not find an active livestream. Mod is idle. Use '/chattermod' to set credentials.");
            registerCommands(); // Still register commands so user can set credentials
            return;
        }

        startPolling();
        registerCommands();
    }

    private void startPolling() {
        if (exec != null && !exec.isShutdown()) {
            exec.shutdownNow();
        }
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChatterMod-Poller");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleAtFixedRate(this::poll, 0, cfg.pollIntervalSeconds, TimeUnit.SECONDS);
        LOGGER.info("✔ Polling started every {} seconds (liveChatId={})", cfg.pollIntervalSeconds, liveChatId);
    }

    private void poll() {
        // Method implementation remains the same...
        if (liveChatId.isBlank()) return;
        try {
            String url = "https://www.googleapis.com/youtube/v3/liveChat/messages"
                    + "?part=snippet,authorDetails"
                    + "&liveChatId=" + URLEncoder.encode(liveChatId, StandardCharsets.UTF_8)
                    + (nextPageToken.isBlank() ? "" : "&pageToken=" + URLEncoder.encode(nextPageToken, StandardCharsets.UTF_8))
                    + "&maxResults=200"
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                LOGGER.error("HTTP {} – check API key/quota. Polling stopped.", res.statusCode());
                if (exec != null) exec.shutdown();
                return;
            }

            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            nextPageToken = root.has("nextPageToken") ? root.get("nextPageToken").getAsString() : "";

            for (JsonElement el : root.getAsJsonArray("items")) {
                JsonObject item = el.getAsJsonObject();
                if (item.getAsJsonObject("snippet").get("type").getAsString().equals("textMessageEvent")) {
                    String name = item.getAsJsonObject("authorDetails").get("displayName").getAsString();
                    String msg = item.getAsJsonObject("snippet")
                            .getAsJsonObject("textMessageDetails")
                            .get("messageText").getAsString();
                    sendToMcChat(name, msg);
                }
            }
        } catch (Exception e) {
            LOGGER.error("An error occurred during polling", e);
            if (exec != null) exec.shutdown();
        }
    }

    private void sendToMcChat(String author, String msg) {
        String line = String.format("[YT] <%s> %s", author, msg);
        MinecraftClient.getInstance().execute(() ->
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal(line))
        );
    }

    private String resolveLiveChatId() {
        if (cfg.channelId.isBlank() || cfg.apiKey.isBlank() || cfg.apiKey.equals("YOUR_API_KEY_HERE")) {
            LOGGER.warn("Cannot resolve live chat ID: API key or Channel ID is missing/default.");
            return "";
        }
        try {
            LOGGER.info("Attempting to resolve liveChatId for channel: {}", cfg.channelId);

            // --- First API Call: Search for the live video ---
            String searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet"
                    + "&channelId=" + URLEncoder.encode(cfg.channelId, StandardCharsets.UTF_8)
                    + "&eventType=live&type=video"
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpRequest searchReq = HttpRequest.newBuilder().uri(URI.create(searchUrl))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> searchRes = http.send(searchReq, HttpResponse.BodyHandlers.ofString());

            // FIX: Check for HTTP errors before parsing JSON
            if (searchRes.statusCode() != 200) {
                LOGGER.error("YouTube API error during search: HTTP {} - {}", searchRes.statusCode(), searchRes.body());
                return "";
            }

            JsonObject searchRoot = JsonParser.parseString(searchRes.body()).getAsJsonObject();

            // FIX: Check that the "items" array exists before trying to use it
            if (!searchRoot.has("items")) {
                LOGGER.error("YouTube API response did not contain 'items' array. Full response: {}", searchRes.body());
                return "";
            }

            JsonArray items = searchRoot.getAsJsonArray("items");
            if (items.isEmpty()) {
                LOGGER.warn("No active live broadcast found for the channel.");
                return "";
            }

            String videoId = items.get(0).getAsJsonObject().getAsJsonObject("id").get("videoId").getAsString();

            // --- Second API Call: Get video details ---
            String detailUrl = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails"
                    + "&id=" + videoId + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpRequest detailReq = HttpRequest.newBuilder().uri(URI.create(detailUrl))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> detailRes = http.send(detailReq, HttpResponse.BodyHandlers.ofString());

            // FIX: Add same error checking for the second call
            if (detailRes.statusCode() != 200) {
                LOGGER.error("YouTube API error during video details fetch: HTTP {} - {}", detailRes.statusCode(), detailRes.body());
                return "";
            }

            JsonObject detailRoot = JsonParser.parseString(detailRes.body()).getAsJsonObject();
            if (!detailRoot.has("items") || detailRoot.getAsJsonArray("items").isEmpty()) {
                LOGGER.error("Could not get video details for videoId '{}'. Response: {}", videoId, detailRes.body());
                return "";
            }

            JsonObject details = detailRoot.getAsJsonArray("items").get(0).getAsJsonObject()
                    .getAsJsonObject("liveStreamingDetails");

            return details.has("activeLiveChatId") ? details.get("activeLiveChatId").getAsString() : "";

        } catch (Exception e) {
            LOGGER.error("Failed to resolve Live Chat ID from channel", e);
            return "";
        }
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("chattermod")
                        .then(ClientCommandManager.literal("apikey")
                                .then(ClientCommandManager.argument("key", StringArgumentType.greedyString())
                                        .executes(c -> {
                                            cfg.apiKey = StringArgumentType.getString(c, "key");
                                            cfg.store();
                                            reply(c.getSource(), "API key saved.");
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("channel")
                                .then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
                                        .executes(c -> {
                                            cfg.channelId = StringArgumentType.getString(c, "id");
                                            cfg.liveChatId = ""; // Clear direct ID to force re-lookup
                                            cfg.store();
                                            liveChatId = resolveLiveChatId();
                                            if (!liveChatId.isBlank()) {
                                                reply(c.getSource(), "Channel updated. Restarting poll...");
                                                startPolling();
                                            } else {
                                                reply(c.getSource(), "Channel updated, but no active livestream found.");
                                                if(exec != null && !exec.isShutdown()) exec.shutdown();
                                            }
                                            return 1;
                                        })))
                )
        );
    }

    private static void reply(FabricClientCommandSource src, String message) {
        src.sendFeedback(Text.literal(message));
    }
}
