package xyz.nikitacartes.easywhitelist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DatabaseConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("easywhitelist.json");

    public boolean enabled = false;
    public String host = "localhost";
    public int port = 3306;
    public String database = "minecraft";
    public String username = "root";
    public String password = "";
    public String tablePrefix = "ew_";
    public String serverId = "default";
    public boolean shareAcrossServers = true;
    public int connectionPoolSize = 5;
    public int syncIntervalSeconds = 30;

    public static DatabaseConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                DatabaseConfig config = GSON.fromJson(json, DatabaseConfig.class);
                config.save();
                return config;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        DatabaseConfig config = new DatabaseConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
