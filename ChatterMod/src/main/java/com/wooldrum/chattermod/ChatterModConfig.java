// src/main/java/com/wooldrum/chattermod/ChatterModConfig.java

package com.wooldrum.chattermod;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;

/**
 * Handles registration and loading of our config file.
 * Call ChatterModConfig.init() once in your mod initializer
 * before accessing Config.INSTANCE.
 */
public class ChatterModConfig {
    /** The loaded config instance. */
    public static Config INSTANCE;

    /** Register the Config class and load the instance. */
    public static void init() {
        AutoConfig.register(Config.class, JanksonConfigSerializer::new);
        INSTANCE = AutoConfig.getConfigHolder(Config.class).getConfig();
    }
}
