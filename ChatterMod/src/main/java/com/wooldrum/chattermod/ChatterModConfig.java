package com.wooldrum.chattermod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Loads & stores config from config/chattermod.properties.
 * If missing, it writes a template with placeholders on the first run.
 */
public class ChatterModConfig {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "chattermod.properties";

    public String apiKey;
    public String liveChatId;
    public String channelId;
    public int pollIntervalSeconds;

    private ChatterModConfig() {
    }

    public static ChatterModConfig load() {
        ChatterModConfig config = new ChatterModConfig();
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, CONFIG_FILE);

            Properties props = new Properties();
            if (file.exists()) {
                try (InputStream in = new FileInputStream(file)) {
                    props.load(in);
                }
            } else {
                // First run: write a default template to disk
                props.setProperty("apiKey", "YOUR_API_KEY_HERE");
                props.setProperty("liveChatId", "");
                props.setProperty("channelId", "UCYOURCHANNELID_HERE");
                props.setProperty("pollIntervalSeconds", "5");
                try (OutputStream out = new FileOutputStream(file)) {
                    props.store(out, "ChatterMod Configuration");
                }
            }

            config.apiKey = props.getProperty("apiKey", "").trim();
            config.liveChatId = props.getProperty("liveChatId", "").trim();
            config.channelId = props.getProperty("channelId", "").trim();
            try {
                config.pollIntervalSeconds = Integer.parseInt(props.getProperty("pollIntervalSeconds", "5").trim());
            } catch (NumberFormatException e) {
                config.pollIntervalSeconds = 5; // Fallback on error
            }
            return config;
        } catch (Exception e) {
            e.printStackTrace();
            // On any failure, return dummy/default values
            config.apiKey = "";
            config.liveChatId = "";
            config.channelId = "";
            config.pollIntervalSeconds = 5;
            return config;
        }
    }

    /**
     * Writes the current configuration values back to disk.
     */
    public void store() {
        try {
            Properties props = new Properties();
            props.setProperty("apiKey", apiKey);
            props.setProperty("liveChatId", liveChatId);
            props.setProperty("channelId", channelId);
            props.setProperty("pollIntervalSeconds", String.valueOf(pollIntervalSeconds));
            try (OutputStream out = new FileOutputStream(new File(CONFIG_DIR, CONFIG_FILE))) {
                props.store(out, "ChatterMod Configuration");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
