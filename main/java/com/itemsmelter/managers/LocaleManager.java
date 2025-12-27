package com.itemsmelter.managers;

import com.itemsmelter.ItemSmelter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class LocaleManager {

    private final ItemSmelter plugin;
    private final Map<String, FileConfiguration> locales = new HashMap<>();
    private final Map<String, String> localeNames = new HashMap<>(); // code -> full name
    private final Map<UUID, String> playerLocales = new HashMap<>();
    private String defaultLocale;
    private File playerLocalesFile;
    private FileConfiguration playerLocalesConfig;

    public LocaleManager(ItemSmelter plugin) {
        this.plugin = plugin;
        this.playerLocalesFile = new File(plugin.getDataFolder(), "player_locales.yml");
    }

    public void loadLocales() {
        locales.clear();
        localeNames.clear();
        
        defaultLocale = plugin.getConfig().getString("language", "en_us");
        
        File localesFolder = new File(plugin.getDataFolder(), "locales");
        if (!localesFolder.exists()) {
            localesFolder.mkdirs();
        }

        saveDefaultLocale("en_us.yml");
        saveDefaultLocale("sru_sru.yml");
        saveDefaultLocale("ua_uk.yml");

        int loadedCount = 0;
        File[] localeFiles = localesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (localeFiles != null) {
            for (File localeFile : localeFiles) {
                String localeId = localeFile.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(localeFile);
                locales.put(localeId, config);
                
                // Get locale code and name from file
                String code = config.getString("locale_code", localeId);
                String name = config.getString("locale_name", localeId);
                localeNames.put(code, localeId);
                
                loadedCount++;
            }
        }

        plugin.getLogger().info("Loaded " + loadedCount + " locale(s)");

        if (!locales.containsKey(defaultLocale)) {
            plugin.getLogger().warning("Default locale '" + defaultLocale + "' not found! Using en_us");
            defaultLocale = "en_us";
        }
        
        loadPlayerLocales();
    }

    private void loadPlayerLocales() {
        if (!playerLocalesFile.exists()) {
            try {
                playerLocalesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create player_locales.yml");
            }
        }
        
        playerLocalesConfig = YamlConfiguration.loadConfiguration(playerLocalesFile);
        
        if (playerLocalesConfig.contains("players")) {
            for (String uuidString : playerLocalesConfig.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                String locale = playerLocalesConfig.getString("players." + uuidString);
                playerLocales.put(uuid, locale);
            }
        }
    }

    private void savePlayerLocales() {
        try {
            playerLocalesConfig.save(playerLocalesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save player_locales.yml");
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
                    localeFile.createNewFile();
                    FileConfiguration config = YamlConfiguration.loadConfiguration(localeFile);
                    
                    if (fileName.equals("en_us.yml")) {
                        config.set("locale_code", "eng");
                        config.set("locale_name", "English");
                        config.set("no_permission", "&cYou don't have permission to use item smelting!");
                        config.set("reload_success", "&aConfiguration reloaded successfully!");
                        config.set("reload_error", "&cError reloading configuration!");
                        config.set("sound_enabled", "&aItem smelting sounds &2ENABLED&a!");
                        config.set("sound_disabled", "&cItem smelting sounds &4DISABLED&c!");
                        config.set("sound_status_on", "&aYou have item smelting sounds &2ENABLED");
                        config.set("sound_status_off", "&cYou have item smelting sounds &4DISABLED");
                        config.set("sound_usage", "&eUsage: /itemsmelter sound [on|off]");
                        config.set("lang_changed", "&aLanguage changed to: &f{language}");
                        config.set("lang_invalid", "&cInvalid language code. Available: &f{languages}");
                        config.set("lang_usage", "&eUsage: /itemsmelter lang <code>");
                        config.set("lang_list", "&eAvailable languages: &f{languages}");
                        config.set("help_header", "&6=== ItemSmelter Commands ===");
                        config.set("help_reload", "&e/itemsmelter reload &7- Reload configuration");
                        config.set("help_sound", "&e/itemsmelter sound [on|off] &7- Toggle or set smelting sounds");
                        config.set("help_lang", "&e/itemsmelter lang <code> &7- Change language");
                        config.set("help_recipes", "&e/itemsmelter recipes &7- Show loaded recipes (admin)");
                        config.set("help_info", "&e/itemsmelter info &7- Show plugin information");
                        config.set("help_help", "&e/itemsmelter help &7- Show this help");
                        config.set("info_header", "&6=== ItemSmelter Info ===");
                        config.set("info_version", "&eVersion: &f{version}");
                        config.set("info_items", "&eLoaded items: &f{items}");
                        config.set("info_recipes", "&eRegistered recipes: &f{recipes}");
                        config.set("info_author", "&eAuthor: &f{author}");
                        config.set("recipes_header", "&6=== Loaded Recipes ({count}) ===");
                        config.set("recipes_item", "&e{id} &7- &f{material} &7-> &f{output} &7({furnace})");
                    } else if (fileName.equals("sru_sru.yml")) {
                        config.set("locale_code", "rus");
                        config.set("locale_name", "Русский");
                    } else if (fileName.equals("ua_uk.yml")) {
                        config.set("locale_code", "ua");
                        config.set("locale_name", "Українська");
                    }
                    
                    config.save(localeFile);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save default locale: " + fileName);
            }
        }
    }

    public String getPlayerLocale(UUID playerId) {
        return playerLocales.getOrDefault(playerId, defaultLocale);
    }

    public void setPlayerLocale(UUID playerId, String localeCode) {
        String localeId = localeNames.get(localeCode.toLowerCase());
        if (localeId == null) {
            localeId = localeCode; // Try as full ID
        }
        
        if (locales.containsKey(localeId)) {
            playerLocales.put(playerId, localeId);
            playerLocalesConfig.set("players." + playerId.toString(), localeId);
            savePlayerLocales();
        }
    }

    public String getMessage(UUID playerId, String key) {
        String localeId = getPlayerLocale(playerId);
        FileConfiguration locale = locales.get(localeId);
        if (locale == null) {
            locale = locales.get(defaultLocale);
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

    public String getMessage(UUID playerId, String key, Map<String, String> replacements) {
        String message = getMessage(playerId, key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    public List<String> getAvailableLocaleCodes() {
        return new ArrayList<>(localeNames.keySet());
    }

    public String getLocaleNameByCode(String code) {
        String localeId = localeNames.get(code.toLowerCase());
        if (localeId != null) {
            FileConfiguration locale = locales.get(localeId);
            if (locale != null) {
                return locale.getString("locale_name", code);
            }
        }
        return code;
    }

    public boolean isValidLocaleCode(String code) {
        return localeNames.containsKey(code.toLowerCase());
    }
}
