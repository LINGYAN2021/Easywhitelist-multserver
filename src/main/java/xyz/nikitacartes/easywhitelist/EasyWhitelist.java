package xyz.nikitacartes.easywhitelist;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Uuids;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nikitacartes.easywhitelist.commands.*;
import xyz.nikitacartes.easywhitelist.config.DatabaseConfig;
import xyz.nikitacartes.easywhitelist.database.DatabaseManager;

import java.util.Collection;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

public class EasyWhitelist implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger("EasyWhitelist");

    public static Collection<GameProfile> getProfileFromNickname(String name) {
        return Collections.singletonList(new GameProfile(Uuids.getOfflinePlayerUuid(name), name));
    }

    public static boolean permissionsLoaded = false;
    public static DatabaseConfig databaseConfig;
    public static DatabaseManager databaseManager;
    private static Timer syncTimer;

    @Override
    public void onInitialize() {
        LOGGER.info("[EasyWhitelist] Whitelist is now name-based.");

        databaseConfig = DatabaseConfig.load();

        if (databaseConfig.enabled) {
            databaseManager = new DatabaseManager(databaseConfig);
            if (databaseManager.connect()) {
                LOGGER.info("[EasyWhitelist] MySQL database integration enabled.");
            } else {
                LOGGER.error("[EasyWhitelist] Failed to connect to MySQL. Database features disabled.");
                databaseManager = null;
            }
        } else {
            LOGGER.info("[EasyWhitelist] Database integration is disabled. Edit config/easywhitelist.json to enable.");
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
            EasyWhitelistCommand.registerCommand(dispatcher);
            EasyBanCommand.registerCommand(dispatcher);
            EasyPardonCommand.registerCommand(dispatcher);
            EasyOpCommand.registerCommand(dispatcher);
            EasyDeOpCommand.registerCommand(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (databaseManager != null && databaseManager.isConnected()) {
                startSyncTask();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            stopSyncTask();
            if (databaseManager != null) {
                databaseManager.close();
            }
        });

        if (FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")) {
            permissionsLoaded = true;
        }
    }

    private void startSyncTask() {
        if (databaseConfig.syncIntervalSeconds > 0) {
            syncTimer = new Timer("EasyWhitelist-Sync", true);
            syncTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (databaseManager != null && databaseManager.isConnected()) {
                        databaseManager.refreshCache();
                    }
                }
            }, databaseConfig.syncIntervalSeconds * 1000L, databaseConfig.syncIntervalSeconds * 1000L);
            LOGGER.info("[EasyWhitelist] Whitelist sync task started (interval: {}s).", databaseConfig.syncIntervalSeconds);
        }
    }

    private static void stopSyncTask() {
        if (syncTimer != null) {
            syncTimer.cancel();
            syncTimer = null;
        }
    }

    public static boolean isDatabaseEnabled() {
        return databaseManager != null && databaseManager.isConnected();
    }
}
