package com.wooldrum.chattermod;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;  // ← corrected import

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

    private void poll() {
        try {
            String url = "https://www.googleapis.com/youtube/v3/liveChat/messages"
                    + "?part=snippet,authorDetails"
                    + "&liveChatId=" + URLEncoder.encode(liveChatId, StandardCharsets.UTF_8)
                    + (nextPageToken.isBlank() ? "" 
                       : "&pageToken=" + URLEncoder.encode(nextPageToken, StandardCharsets.UTF_8))
                    + "&maxResults=200"
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpResponse<String> res = http.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                           .timeout(Duration.ofSeconds(10))
                           .GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            if (res.statusCode() != 200) {
                System.err.printf("[ChatterMod] HTTP %d – check API key/quota%n", res.statusCode());
                return;
            }

            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            nextPageToken = root.has("nextPageToken")
                ? root.get("nextPageToken").getAsString()
                : "";

            for (JsonElement el : root.getAsJsonArray("items")) {
                JsonObject item = el.getAsJsonObject();
                String name = item.getAsJsonObject("authorDetails")
                                  .get("displayName").getAsString();
                String msg  = item.getAsJsonObject("snippet")
                                  .getAsJsonObject("textMessageDetails")
                                  .get("messageText").getAsString();
                sendToChat(name, msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendToChat(String author, String message) {
        String line = String.format("[YT] <%s> %s", author, message);
        MinecraftClient.getInstance().execute(() ->
            MinecraftClient.getInstance()
                .inGameHud.getChatHud()
                .addMessage(Text.literal(line))
        );
    }

    private String resolveLiveChatId() {
        if (cfg.channelId.isBlank()) return "";
        try {
            String searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet"
                    + "&channelId=" + URLEncoder.encode(cfg.channelId, StandardCharsets.UTF_8)
                    + "&eventType=live&type=video"
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpResponse<String> sr = http.send(
                HttpRequest.newBuilder().uri(URI.create(searchUrl))
                           .timeout(Duration.ofSeconds(10))
                           .GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            JsonArray items = JsonParser.parseString(sr.body())
                                        .getAsJsonObject()
                                        .getAsJsonArray("items");
            if (items.isEmpty()) return "";

            String videoId = items.get(0).getAsJsonObject()
                                   .getAsJsonObject("id")
                                   .get("videoId").getAsString();

            String detailUrl = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails"
                    + "&id=" + videoId
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpResponse<String> dr = http.send(
                HttpRequest.newBuilder().uri(URI.create(detailUrl))
                           .timeout(Duration.ofSeconds(10))
                           .GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            JsonObject details = JsonParser.parseString(dr.body())
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

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) ->
            dispatcher.register(ClientCommandManager.literal("chattermod")
                .then(ClientCommandManager.literal("apikey")
                    .then(ClientCommandManager.argument("key", StringArgumentType.greedyString())
                        .executes(c -> {
                            cfg.apiKey = StringArgumentType.getString(c, "key");
                            cfg.store();
                            reply((FabricClientCommandSource)c.getSource(), "API key saved.");
                            return 1;
                        })))
                .then(ClientCommandManager.literal("channel")
                    .then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
                        .executes(c -> {
                            cfg.channelId = StringArgumentType.getString(c, "id");
                            cfg.liveChatId = "";
                            cfg.store();
                            liveChatId = resolveLiveChatId();
                            reply((FabricClientCommandSource)c.getSource(),
                                  liveChatId.isBlank() ? "No live stream." : "Channel updated.");
                            return 1;
                        })))
            )
        );
    }

    // ← signature updated to use FabricClientCommandSource
    private static void reply(FabricClientCommandSource src, String message) {
        src.sendFeedback(Text.literal(message));
    }
}
