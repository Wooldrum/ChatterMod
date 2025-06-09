package com.wooldrum.chattermod;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.*;
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
    private HttpClient       http;
    private ScheduledExecutorService exec;
    private String liveChatId = "", nextPageToken = "";

    @Override
    public void onInitializeClient() {
        cfg  = ChatterModConfig.load();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        liveChatId = cfg.liveChatId.isBlank() ? resolveLiveChatId() : cfg.liveChatId;
        if (liveChatId.isBlank()) {
            System.err.println("[ChatterMod] No live stream found; mod idle.");
            return;
        }

        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChatterMod-Poller"); t.setDaemon(true); return t;
        });
        exec.scheduleAtFixedRate(this::poll, 0, cfg.pollIntervalSeconds, TimeUnit.SECONDS);

        registerCommands();
        System.out.printf("[ChatterMod] ✔ polling every %ds (liveChatId=%s)%n",
                          cfg.pollIntervalSeconds, liveChatId);
    }

    /*─────────────────────────  polling  ─────────────────────────*/

    private void poll() {
        try {
            String url = "https://www.googleapis.com/youtube/v3/liveChat/messages"
                    + "?part=snippet,authorDetails"
                    + "&liveChatId=" + URLEncoder.encode(liveChatId, StandardCharsets.UTF_8)
                    + (nextPageToken.isBlank() ? "" :
                       "&pageToken=" + URLEncoder.encode(nextPageToken, StandardCharsets.UTF_8))
                    + "&maxResults=200"
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder().uri(URI.create(url))
                            .timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                System.err.printf("[ChatterMod] HTTP %d – check API key/quota%n", res.statusCode());
                return;
            }

            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            nextPageToken = root.has("nextPageToken") ? root.get("nextPageToken").getAsString() : "";

            for (JsonElement el : root.getAsJsonArray("items")) {
                JsonObject item = el.getAsJsonObject();
                String name = item.getAsJsonObject("authorDetails").get("displayName").getAsString();
                String txt  = item.getAsJsonObject("snippet")
                                  .getAsJsonObject("textMessageDetails")
                                  .get("messageText").getAsString();
                sendToMcChat(name, txt);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendToMcChat(String author, String msg) {
        String line = "[YT] <" + author + "> " + msg;
        MinecraftClient.getInstance().execute(() ->
            MinecraftClient.getInstance().inGameHud.getChatHud()
                         .addMessage(Text.literal(line)));
    }

    /*─────────────────────  liveChatId lookup  ───────────────────*/

    private String resolveLiveChatId() {
        if (cfg.channelId.isBlank()) return "";
        try {
            String search = "https://www.googleapis.com/youtube/v3/search?part=snippet"
                    + "&channelId=" + URLEncoder.encode(cfg.channelId, StandardCharsets.UTF_8)
                    + "&eventType=live&type=video"
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpResponse<String> sr = http.send(
                    HttpRequest.newBuilder().uri(URI.create(search))
                            .timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            JsonArray items = JsonParser.parseString(sr.body())
                                        .getAsJsonObject().getAsJsonArray("items");
            if (items.isEmpty()) return "";

            String vid = items.get(0).getAsJsonObject()
                              .getAsJsonObject("id").get("videoId").getAsString();

            String detail = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails"
                    + "&id=" + vid
                    + "&key=" + URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8);

            HttpResponse<String> dr = http.send(
                    HttpRequest.newBuilder().uri(URI.create(detail))
                            .timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            return JsonParser.parseString(dr.body())
                    .getAsJsonObject()
                    .getAsJsonArray("items")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("liveStreamingDetails")
                    .get("activeLiveChatId").getAsString();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace(); return "";
        }
    }

    /*────────────────────────  /chattermod cmd  ──────────────────*/

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((disp, ctx) ->
                disp.register(ClientCommandManager.literal("chattermod")
                    .then(ClientCommandManager.literal("apikey")
                        .then(ClientCommandManager.argument("key", StringArgumentType.greedyString())
                            .executes(c -> { cfg.apiKey = StringArgumentType.getString(c,"key"); cfg.store();
                                             reply(c.getSource(),"API key saved."); return 1; })))
                    .then(ClientCommandManager.literal("channel")
                        .then(ClientCommandManager.argument("id", StringArgumentType.greedyString())
                            .executes(c -> { cfg.channelId = StringArgumentType.getString(c,"id");
                                             cfg.liveChatId = ""; cfg.store();
                                             liveChatId = resolveLiveChatId();
                                             reply(c.getSource(), liveChatId.isBlank()
                                                     ? "No active livestream."
                                                     : "Channel updated."); return 1; })))
                ));
    }
    private static void reply(ClientCommandSource src, String m) { src.sendFeedback(Text.literal(m)); }
}
