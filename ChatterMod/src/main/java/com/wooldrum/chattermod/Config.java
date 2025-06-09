package com.wooldrum.chattermod;

import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Your mod’s runtime configuration.
 * Any public fields here will become options in-game.
 */
@Config(name = "chattermod")
public class Config {
    /**
     * If true, debug info (e.g. raw YouTube chat JSON) will be logged.
     */
    @ConfigEntry.Gui.Tooltip
    public boolean debug = false;

    // → add more options here
}
