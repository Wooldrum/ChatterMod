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

    // General Settings
    public boolean showPlatformLogo;

    // Platform-specific color settings
    public String youtubeColor;
    public String twitchColor;
    public String kickColor;

    // Account lists
    public List<YouTubeAccount> youtubeAccounts = new ArrayList<>();
    public List<TwitchAccount> twitchAccounts = new ArrayList<>();
    public List<KickAccount> kickAccounts = new ArrayList<>();

    // Data records for accounts
    public record YouTubeAccount(String channelId, String liveChatId, String apiKey, int pollIntervalSeconds) {}
    public record TwitchAccount(String channelName, String oauthToken) {}
    public record KickAccount(String channelName) {}

    public static ChatterModConfig load() {
        ChatterModConfig config = new ChatterModConfig();
        Properties props = new Properties();

        if (CONFIG_FILE.exists()) {
            try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
                props.load(in);
            } catch (IOException e) {
                ChatterMod.LOGGER.error("Failed to load chattermod.properties", e);
            }
        } else {
            // Write defaults if file doesn't exist
            writeDefaultConfig(props);
            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                props.store(out, "ChatterMod v2 Configuration");
            } catch (IOException e) {
                ChatterMod.LOGGER.error("Failed to write default chattermod.properties", e);
            }
        }

        // Load general settings
        config.showPlatformLogo = Boolean.parseBoolean(props.getProperty("general.showPlatformLogo", "true"));
        config.youtubeColor = props.getProperty("colors.youtube", "RED");
        config.twitchColor = props.getProperty("colors.twitch", "DARK_PURPLE");
        config.kickColor = props.getProperty("colors.kick", "GREEN");

        // Load accounts by iterating through numbered prefixes
        for (int i = 1; i <= 5; i++) { // Allow up to 5 accounts per platform
            // YouTube
            String ytChannelId = props.getProperty("youtube." + i + ".channelId");
            if (ytChannelId != null && !ytChannelId.isBlank()) {
                config.youtubeAccounts.add(new YouTubeAccount(
                        ytChannelId,
                        props.getProperty("youtube." + i + ".liveChatId", ""),
                        props.getProperty("youtube." + i + ".apiKey", ""),
                        Integer.parseInt(props.getProperty("youtube." + i + ".pollIntervalSeconds", "10"))
                ));
            }

            // Twitch
            String twChannelName = props.getProperty("twitch." + i + ".channelName");
            if (twChannelName != null && !twChannelName.isBlank()) {
                config.twitchAccounts.add(new TwitchAccount(
                        twChannelName,
                        props.getProperty("twitch." + i + ".oauthToken", "")
                ));
            }

            // Kick
            String kChannelName = props.getProperty("kick." + i + ".channelName");
            if (kChannelName != null && !kChannelName.isBlank()) {
                config.kickAccounts.add(new KickAccount(kChannelName));
            }
        }
        return config;
    }

    private static void writeDefaultConfig(Properties props) {
        props.setProperty("general.showPlatformLogo", "true");
        props.setProperty("colors.youtube", "RED");
        props.setProperty("colors.twitch", "DARK_PURPLE");
        props.setProperty("colors.kick", "GREEN");

        props.setProperty("youtube.1.channelId", "UCYOURCHANNELID_HERE");
        props.setProperty("youtube.1.liveChatId", "");
        props.setProperty("youtube.1.apiKey", "YOUR_API_KEY_HERE");
        props.setProperty("youtube.1.pollIntervalSeconds", "10");

        props.setProperty("twitch.1.channelName", "your_twitch_channel_name");
        props.setProperty("twitch.1.oauthToken", "YOUR_OAUTH_TOKEN_HERE");

        props.setProperty("kick.1.channelName", "your_kick_channel_name");
    }
}
