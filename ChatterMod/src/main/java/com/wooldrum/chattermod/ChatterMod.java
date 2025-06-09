/**
 * Safely resolve the active liveChatId for the configured channelId.
 * Returns empty string if no live stream is found or on any error.
 */
private String resolveLiveChatId() {
    if (cfg.channelId.isBlank()) return "";

    try {
        // 1) Search for an active live video on the channel
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
            // no live video found
            return "";
        }

        String videoId = searchItems.get(0)
                                    .getAsJsonObject()
                                    .getAsJsonObject("id")
                                    .get("videoId").getAsString();

        // 2) Fetch liveStreamingDetails for that video
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
            // no details found
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
