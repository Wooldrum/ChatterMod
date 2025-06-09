package com.wooldrum.chattermod;

import java.io.*;
import java.util.Properties;

/** Simple POJO + disk I/O for config/chattermod.properties */
public class ChatterModConfig {
    private static final File FILE =
            new File("config", "chattermod.properties");

    public String apiKey        = "";
    public String liveChatId    = "";
    public String channelId     = "";
    public int    pollIntervalSeconds = 5;

    /* load or create template */
    public static ChatterModConfig load() {
        ChatterModConfig c = new ChatterModConfig();
        try {
            FILE.getParentFile().mkdirs();
            Properties p = new Properties();

            if (FILE.exists()) try (InputStream in = new FileInputStream(FILE)) {
                p.load(in);
            } else {
                p.setProperty("apiKey", "YOUR_API_KEY_HERE");
                p.setProperty("liveChatId", "");
                p.setProperty("channelId", "UCYOURCHANNELID_HERE");
                p.setProperty("pollIntervalSeconds", "5");
                try (OutputStream out = new FileOutputStream(FILE)) {
                    p.store(out, "ChatterMod configuration");
                }
            }

            c.apiKey              = p.getProperty("apiKey", "").trim();
            c.liveChatId          = p.getProperty("liveChatId", "").trim();
            c.channelId           = p.getProperty("channelId", "").trim();
            c.pollIntervalSeconds =
                    Integer.parseInt(p.getProperty("pollIntervalSeconds", "5").trim());

        } catch (Exception e) { e.printStackTrace(); }
        return c;
    }

    /* persist current fields */
    public void store() {
        try {
            Properties p = new Properties();
            p.setProperty("apiKey", apiKey);
            p.setProperty("liveChatId", liveChatId);
            p.setProperty("channelId", channelId);
            p.setProperty("pollIntervalSeconds", String.valueOf(pollIntervalSeconds));
            try (OutputStream out = new FileOutputStream(FILE)) {
                p.store(out, "ChatterMod configuration");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}
