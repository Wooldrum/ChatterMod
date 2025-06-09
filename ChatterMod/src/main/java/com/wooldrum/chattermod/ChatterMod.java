package com.wooldrum.chattermod;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource; // Corrected import
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
        cfg = ChatterModConfig.load();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        liveChatId = cfg.liveChatId.isBlank() ? resolveLiveChatId() : cfg.liveChatId;

        if (liveChatId.isBlank()) {
            System.err.println("[ChatterMod] No live stream found; mod is idle.");
            registerCommands(); // Still register commands so user can set credentials
            return;
        }

        startPolling();
        registerCommands();
    }

    private void startPolling() {
        if (exec != null && !exec.isShutdown()) {
            exec.shutdownNow(); // Stop previous poller if it exists
        }
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChatterMod-Poller");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleAtFixedRate(this::poll, 0, cfg.pollIntervalSeconds, TimeUnit.SECONDS);

        System.out.printf("[ChatterMod] ✔ Polling started every %d seconds (liveChatId=%s)%n",
                cfg.pollIntervalSeconds, liveChatId);
    }

    private void poll() {
        if (liveChatId.isBlank()) return; // Don't poll if we don't have an ID
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
                System.err.printf("[ChatterMod] HTTP %d – check API key/quota. Polling stopped.%n", res.statusCode());
                exec.shutdown(); // Stop polling on error
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
            e.printStackTrace();
            if (exec != null) exec.shutdown(); // Stop on other exceptions
        }
    }

    private void sendToMcChat(String author, String msg) {
        String line = String.format("[YT] <%s> %s", author, msg);
        MinecraftClient.getInstance().execute(() ->
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal(line))
        );
    }

    private String resolveLiveChatId() {
        if (cfg.channelId.isBlank() || cfg.apiKey.isBlank()) return "";
        try {
            String searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet"
                    + "&channelId=" + URLEncoder.encode(cfg.channelId, StandardCharsets.UTF_8)
                    + "&eventType=live&type=video"
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpRequest searchReq = HttpRequest.newBuilder().uri(URI.create(searchUrl))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> searchRes = http.send(searchReq, HttpResponse.BodyHandlers.ofString());

            JsonArray items = JsonParser.parseString(searchRes.body()).getAsJsonObject().getAsJsonArray("items");
            if (items.isEmpty()) return "";

            String videoId = items.get(0).getAsJsonObject().getAsJsonObject("id").get("videoId").getAsString();

            String detailUrl = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails"
                    + "&id=" + videoId + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpRequest detailReq = HttpRequest.newBuilder().uri(URI.create(detailUrl))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> detailRes = http.send(detailReq, HttpResponse.BodyHandlers.ofString());

            JsonObject details = JsonParser.parseString(detailRes.body()).getAsJsonObject()
                    .getAsJsonArray("items").get(0).getAsJsonObject()
                    .getAsJsonObject("liveStreamingDetails");

            return details.has("activeLiveChatId") ? details.get("activeLiveChatId").getAsString() : "";
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
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
                                                if(exec != null) exec.shutdown();
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
