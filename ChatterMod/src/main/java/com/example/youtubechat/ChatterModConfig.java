// File: src/main/java/com/wooldrum/chattermod/ChatterModConfig.java
package com.wooldrum.chattermod;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads config from config/chattermod.properties.
 * If missing, writes a template with placeholders.
 */
public class ChatterModConfig {
    private static final String CONFIG_DIR  = "config";
    private static final String CONFIG_FILE = "chattermod.properties";

    public final String apiKey;
    public final String liveChatId; 
    public final String channelId;
    public final int    pollIntervalSeconds;

    private ChatterModConfig(String apiKey, String liveChatId, String channelId, int pollIntervalSeconds) {
        this.apiKey              = apiKey;
        this.liveChatId          = liveChatId;
        this.channelId           = channelId;
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    public static ChatterModConfig load() {
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, CONFIG_FILE);
            if (!file.exists()) {
                Properties defaults = new Properties();
                defaults.setProperty("apiKey", "YOUR_API_KEY_HERE");
                defaults.setProperty("liveChatId", "");
                defaults.setProperty("channelId", "UCYOURCHANNELID_HERE");
                defaults.setProperty("pollIntervalSeconds", "5");
                try (var out = new java.io.FileOutputStream(file)) {
                    defaults.store(out, "ChatterMod Configuration");
                }
                return new ChatterModConfig("YOUR_API_KEY_HERE", "", "UCYOURCHANNELID_HERE", 5);
            }

            Properties props = new Properties();
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            }

            String apiKey = props.getProperty("apiKey", "").trim();
            String liveChatId = props.getProperty("liveChatId", "").trim();
            String channelId = props.getProperty("channelId", "").trim();
            int interval;
            try {
                interval = Integer.parseInt(props.getProperty("pollIntervalSeconds", "5").trim());
            } catch (NumberFormatException e) {
                interval = 5;
            }

            return new ChatterModConfig(apiKey, liveChatId, channelId, interval);
        } catch (Exception e) {
            e.printStackTrace();
            return new ChatterModConfig("", "", "", 5);
        }
    }
}
