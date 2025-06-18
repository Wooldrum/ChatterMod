package com.wooldrum.chattermod.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wooldrum.chattermod.ChatterMod;
import com.wooldrum.chattermod.ChatterModConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class YouTubePlatform implements ChatPlatform {

    private final ChatterModConfig.YouTubeAccount account;
    private final HttpClient http;
    private ScheduledExecutorService poller;
    private String liveChatId;
    private String nextPageToken = "";
    private Consumer<ChatMessage> messageConsumer;

    public YouTubePlatform(ChatterModConfig.YouTubeAccount account, HttpClient http) {
        this.account = account;
        this.http = http;
    }

    @Override
    public void onMessage(Consumer<ChatMessage> consumer) {
        this.messageConsumer = consumer;
    }

    @Override
    public void connect() {
        if (account.apiKey().isBlank() || account.apiKey().equals("YOUR_API_KEY_HERE")) {
            ChatterMod.LOGGER.error("[YouTube] Cannot connect: API Key is missing.");
            return;
        }
        this.liveChatId = resolveLiveChatIdFromChannel();

        if (this.liveChatId == null || this.liveChatId.isBlank()) {
            ChatterMod.LOGGER.error("[YouTube] Could not find a live chat ID for channel '{}'.", account.channelId());
            return;
        }

        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(this::poll, 0, 10, TimeUnit.SECONDS);
        ChatterMod.LOGGER.info("[YouTube] Connected and polling chat for liveChatId: {}", this.liveChatId);
    }

    private void poll() {
        try {
            String url = "https://www.googleapis.com/youtube/v3/liveChat/messages"
                    + "?part=snippet,authorDetails"
                    + "&liveChatId=" + URLEncoder.encode(liveChatId, StandardCharsets.UTF_8)
                    + (nextPageToken.isBlank() ? "" : "&pageToken=" + URLEncoder.encode(nextPageToken, StandardCharsets.UTF_8))
                    + "&maxResults=200"
                    + "&key=" + URLEncoder.encode(account.apiKey(), StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                ChatterMod.LOGGER.error("[YouTube] API Error: HTTP {}. Disconnecting.", res.statusCode());
                disconnect();
                return;
            }

            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            nextPageToken = root.has("nextPageToken") ? root.get("nextPageToken").getAsString() : "";

            for (JsonElement el : root.getAsJsonArray("items")) {
                JsonObject item = el.getAsJsonObject();
                if (item.getAsJsonObject("snippet").get("type").getAsString().equals("textMessageEvent")) {
                    String author = item.getAsJsonObject("authorDetails").get("displayName").getAsString();
                    String msg = item.getAsJsonObject("snippet").getAsJsonObject("textMessageDetails").get("messageText").getAsString();
                    messageConsumer.accept(new ChatMessage(author, msg, ChatMessage.Platform.YOUTUBE));
                }
            }
        } catch (Exception e) {
            ChatterMod.LOGGER.error("[YouTube] Polling failed.", e);
            disconnect();
        }
    }

    @Override
    public void disconnect() {
        if (poller != null && !poller.isShutdown()) {
            poller.shutdownNow();
            ChatterMod.LOGGER.info("[YouTube] Disconnected.");
        }
    }

    private String resolveLiveChatIdFromChannel() {
        try {
            String searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet"
                    + "&channelId=" + URLEncoder.encode(account.channelId(), StandardCharsets.UTF_8)
                    + "&eventType=live&type=video"
                    + "&key=" + URLEncoder.encode(account.apiKey(), StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(searchUrl)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) return null;

            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            if (!root.has("items") || root.getAsJsonArray("items").isEmpty()) return null;

            String videoId = root.getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("id").get("videoId").getAsString();

            String detailUrl = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails&id=" + videoId + "&key=" + account.apiKey();
            req = HttpRequest.newBuilder().uri(URI.create(detailUrl)).build();
            res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) return null;

            JsonObject detailRoot = JsonParser.parseString(res.body()).getAsJsonObject();
            if (!detailRoot.has("items") || detailRoot.getAsJsonArray("items").isEmpty()) return null;
            
            return detailRoot.getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("liveStreamingDetails").get("activeLiveChatId").getAsString();
        } catch (Exception e) {
            ChatterMod.LOGGER.error("[YouTube] Failed to auto-resolve live chat ID.", e);
            return null;
        }
    }
}
