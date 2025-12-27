package com.itemsmelter.managers;

import com.itemsmelter.ItemSmelter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerSettingsManager {

    private final ItemSmelter plugin;
    private final File settingsFile;
    private FileConfiguration settingsConfig;
    private final Map<UUID, Boolean> soundsEnabled = new HashMap<>();

    public PlayerSettingsManager(ItemSmelter plugin) {
        this.plugin = plugin;
        this.settingsFile = new File(plugin.getDataFolder(), "player_settings.yml");
        loadSettings();
    }

    private void loadSettings() {
        if (!settingsFile.exists()) {
            try {
                settingsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create player_settings.yml");
            }
        }

        settingsConfig = YamlConfiguration.loadConfiguration(settingsFile);

        // Load all player settings into cache
        if (settingsConfig.contains("players")) {
            for (String uuidString : settingsConfig.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                boolean enabled = settingsConfig.getBoolean("players." + uuidString + ".sounds_enabled", true);
                soundsEnabled.put(uuid, enabled);
            }
        }
    }

    public void saveSettings() {
        try {
            settingsConfig.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save player_settings.yml");
        }
    }

    public boolean isSoundEnabled(Player player) {
        return soundsEnabled.getOrDefault(player.getUniqueId(), true);
    }

    public void setSoundEnabled(Player player, boolean enabled) {
        UUID uuid = player.getUniqueId();
        soundsEnabled.put(uuid, enabled);
        settingsConfig.set("players." + uuid.toString() + ".sounds_enabled", enabled);
        saveSettings();
    }

    public void toggleSound(Player player) {
        boolean current = isSoundEnabled(player);
        setSoundEnabled(player, !current);
    }
}