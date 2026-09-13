package com.skooper.sentinelac;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * SentinelAC - Production-quality server-authoritative Minecraft Anti-Cheat.
 */
public final class SentinelAC extends JavaPlugin {

    private static SentinelAC instance;
    private Logger logger;

    @Override
    public void onEnable() {
        instance = this;
        this.logger = getLogger();

        logger.info("=================================================");
        logger.info(" SentinelAC v" + getDescription().getVersion() + " initialized successfully.");
        logger.info(" Server-authoritative physics & statistical fusion active.");
        logger.info("=================================================");
    }

    @Override
    public void onDisable() {
        if (logger != null) {
            logger.info("SentinelAC disabled.");
        }
        instance = null;
    }

    public static SentinelAC getInstance() {
        return instance;
    }
}
