package com.wooldrum.chattermod;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;

public class ChatterModConfig {
    public static Config INSTANCE;

    public static void init() {
        AutoConfig.register(Config.class, JanksonConfigSerializer::new);
        INSTANCE = AutoConfig.getConfigHolder(Config.class).getConfig();
    }
}
