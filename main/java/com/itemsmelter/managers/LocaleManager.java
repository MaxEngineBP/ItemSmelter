package com.itemsmelter.managers;

import com.itemsmelter.ItemSmelter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class LocaleManager {

    private final ItemSmelter plugin;
    private final Map<String, FileConfiguration> locales = new HashMap<>();
    private String currentLocale;

    public LocaleManager(ItemSmelter plugin) {
        this.plugin = plugin;
    }

    public void loadLocales() {
        locales.clear();

        // Get current locale from config
        currentLocale = plugin.getConfig().getString("language", "en_us");

        // Create locales folder
        File localesFolder = new File(plugin.getDataFolder(), "locales");
        if (!localesFolder.exists()) {
            localesFolder.mkdirs();
        }

        // Save default locale files
        saveDefaultLocale("en_us.yml");
        saveDefaultLocale("sru_sru.yml");
        saveDefaultLocale("ua_uk.yml");

        // Load all locale files
        File[] localeFiles = localesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (localeFiles != null) {
            for (File localeFile : localeFiles) {
                String localeName = localeFile.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(localeFile);
                locales.put(localeName, config);
                plugin.getLogger().info("Loaded locale: " + localeName);
            }
        }

        // Validate current locale exists
        if (!locales.containsKey(currentLocale)) {
            plugin.getLogger().warning("Locale '" + currentLocale + "' not found! Using en_us");
            currentLocale = "en_us";
        }
    }

    private void saveDefaultLocale(String fileName) {
        File localeFile = new File(plugin.getDataFolder(), "locales/" + fileName);
        if (!localeFile.exists()) {
            try {
                InputStream in = plugin.getResource("locales/" + fileName);
                if (in != null) {
                    Files.copy(in, localeFile.toPath());
                } else {
                    // Create default file
                    localeFile.createNewFile();
                    FileConfiguration config = YamlConfiguration.loadConfiguration(localeFile);

                    // Add default messages based on filename
                    if (fileName.equals("en_us.yml")) {
                        config.set("no_permission", "&cYou don't have permission to use item smelting!");
                        config.set("reload_success", "&aConfiguration reloaded successfully!");
                        config.set("reload_error", "&cError reloading configuration!");
                        config.set("sound_enabled", "&aItem smelting sounds enabled!");
                        config.set("sound_disabled", "&cItem smelting sounds disabled!");
                        config.set("sound_status_on", "&aYou have item smelting sounds &2ENABLED");
                        config.set("sound_status_off", "&cYou have item smelting sounds &4DISABLED");
                        config.set("help_header", "&6=== ItemSmelter Commands ===");
                        config.set("help_reload", "&e/itemsmelter reload &7- Reload configuration");
                        config.set("help_sound", "&e/itemsmelter sound [on|off] &7- Toggle or set smelting sounds");
                        config.set("help_info", "&e/itemsmelter info &7- Show plugin information");
                        config.set("help_help", "&e/itemsmelter help &7- Show this help");
                        config.set("info_header", "&6=== ItemSmelter Info ===");
                        config.set("info_version", "&eVersion: &f{version}");
                        config.set("info_items", "&eLoaded items: &f{items}");
                        config.set("info_author", "&eAuthor: &f{author}");
                    }

                    config.save(localeFile);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save default locale: " + fileName);
            }
        }
    }

    public String getMessage(String key) {
        FileConfiguration locale = locales.get(currentLocale);
        if (locale == null) {
            locale = locales.get("en_us");
        }

        if (locale == null) {
            return "Missing locale: " + key;
        }

        String message = locale.getString(key);
        if (message == null) {
            return "Missing translation: " + key;
        }

        return message.replace("&", "§");
    }

    public String getMessage(String key, Map<String, String> replacements) {
        String message = getMessage(key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }
}