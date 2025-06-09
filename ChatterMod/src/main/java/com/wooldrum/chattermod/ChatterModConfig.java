package com.wooldrum.chattermod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ChatterModConfig {

    private static final File CONFIG_FILE = new File("config", "chattermod.properties");

    public boolean showPlatformLogo;
    public boolean usePlatformColors;
    public String youtubeColor;
    public String twitchColor;
    public List<YouTubeAccount> youtubeAccounts = new ArrayList<>();
    public List<TwitchAccount> twitchAccounts = new ArrayList<>();

    public record YouTubeAccount(String channelId, String apiKey) {}
    public record TwitchAccount(String channelName, String oauthToken) {}

    private ChatterModConfig() {}

    public static ChatterModConfig load() {
        ChatterModConfig config = new ChatterModConfig();
        Properties props = new Properties();

        if (CONFIG_FILE.exists()) {
            try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
                props.load(in);
            } catch (IOException e) {
                ChatterMod.LOGGER.error("Failed to load chattermod.properties", e);
            }
        }

        config.showPlatformLogo = Boolean.parseBoolean(props.getProperty("general.showPlatformLogo", "true"));
        config.usePlatformColors = Boolean.parseBoolean(props.getProperty("general.usePlatformColors", "true"));
        config.youtubeColor = props.getProperty("colors.youtube", "RED");
        config.twitchColor = props.getProperty("colors.twitch", "DARK_PURPLE");

        loadAccounts(props, config);

        if (!CONFIG_FILE.exists()) {
            config.save(); // Creates the file with defaults on first run
        }
        return config;
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("general.showPlatformLogo", String.valueOf(this.showPlatformLogo));
        props.setProperty("general.usePlatformColors", String.valueOf(this.usePlatformColors));
        props.setProperty("colors.youtube", this.youtubeColor);
        props.setProperty("colors.twitch", this.twitchColor);

        if (!youtubeAccounts.isEmpty()) {
            props.setProperty("youtube.1.channelId", youtubeAccounts.get(0).channelId());
            props.setProperty("youtube.1.apiKey", youtubeAccounts.get(0).apiKey());
        }
        if (!twitchAccounts.isEmpty()) {
            props.setProperty("twitch.1.channelName", twitchAccounts.get(0).channelName());
            props.setProperty("twitch.1.oauthToken", twitchAccounts.get(0).oauthToken());
        }

        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "ChatterMod BETA Configuration");
        } catch (IOException e) {
            ChatterMod.LOGGER.error("Failed to save chattermod.properties", e);
        }
    }

    private static void loadAccounts(Properties props, ChatterModConfig config) {
        config.youtubeAccounts.clear();
        config.twitchAccounts.clear();

        String ytApiKey = props.getProperty("youtube.1.apiKey");
        if (ytApiKey != null && !ytApiKey.isBlank()) {
            config.youtubeAccounts.add(new YouTubeAccount(
                props.getProperty("youtube.1.channelId", ""),
                ytApiKey
            ));
        }

        String twChannelName = props.getProperty("twitch.1.channelName");
        if (twChannelName != null && !twChannelName.isBlank()) {
            config.twitchAccounts.add(new TwitchAccount(
                twChannelName,
                props.getProperty("twitch.1.oauthToken", "")
            ));
        }
    }
}
