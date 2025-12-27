package com.itemsmelter.config;

import com.itemsmelter.ItemSmelter;
import com.itemsmelter.models.DurabilityRange;
import com.itemsmelter.models.SmeltableItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class ConfigManager {

    private final ItemSmelter plugin;
    private final Map<Material, SmeltableItem> smeltableItems;
    private String language;
    private final Map<String, String> messages;

    public ConfigManager(ItemSmelter plugin) {
        this.plugin = plugin;
        this.smeltableItems = new HashMap<>();
        this.messages = new HashMap<>();
    }

    public void loadConfiguration() {
        smeltableItems.clear();
        messages.clear();

        // Load language
        language = plugin.getConfig().getString("language", "en");

        // Load messages
        loadMessages();

        // Load default items
        loadDefaultItems();

        // Load custom items (override defaults if exists)
        loadCustomItems();

        plugin.getLogger().info("Configuration loaded: " + smeltableItems.size() + " smeltable items");
    }

    private void loadMessages() {
        ConfigurationSection msgSection = plugin.getConfig().getConfigurationSection("messages." + language);
        if (msgSection == null) {
            plugin.getLogger().warning("Messages for language '" + language + "' not found! Using defaults.");
            return;
        }

        for (String key : msgSection.getKeys(false)) {
            messages.put(key, msgSection.getString(key));
        }
    }

    private void loadDefaultItems() {
        ConfigurationSection defaultSection = plugin.getConfig().getConfigurationSection("default_items");
        if (defaultSection == null) return;

        for (String key : defaultSection.getKeys(false)) {
            ConfigurationSection itemSection = defaultSection.getConfigurationSection(key);
            if (itemSection == null) continue;

            SmeltableItem item = parseSmeltableItem(key, itemSection);
            if (item != null && item.isEnabled()) {
                smeltableItems.put(item.getMaterial(), item);
            }
        }
    }

    private void loadCustomItems() {
        ConfigurationSection customSection = plugin.getConfig().getConfigurationSection("custom_items");
        if (customSection == null) return;

        for (String key : customSection.getKeys(false)) {
            ConfigurationSection itemSection = customSection.getConfigurationSection(key);
            if (itemSection == null) continue;

            SmeltableItem item = parseSmeltableItem(key, itemSection);
            if (item != null && item.isEnabled()) {
                // Custom items override default items
                smeltableItems.put(item.getMaterial(), item);
                plugin.getLogger().info("Loaded custom item: " + key);
            }
        }
    }

    private SmeltableItem parseSmeltableItem(String id, ConfigurationSection section) {
        try {
            String materialStr = section.getString("material");
            if (materialStr == null) {
                plugin.getLogger().warning("Item '" + id + "' has no material defined!");
                return null;
            }

            Material material = Material.matchMaterial(materialStr);
            if (material == null) {
                plugin.getLogger().warning("Invalid material '" + materialStr + "' for item '" + id + "'");
                return null;
            }

            boolean enabled = section.getBoolean("enabled", true);
            String smeltIn = section.getString("smelt_in", "BLAST_FURNACE");
            boolean ignoreSticks = section.getBoolean("ignore_sticks", true);
            boolean durabilityBased = section.getBoolean("durability_based", true);

            // Output material
            String outputMaterialStr = section.getString("output.material");
            Material outputMaterial = Material.matchMaterial(outputMaterialStr);
            if (outputMaterial == null) {
                plugin.getLogger().warning("Invalid output material for item '" + id + "'");
                return null;
            }

            int maxDurability = section.getInt("max_durability", material.getMaxDurability());
            double smeltTimeMultiplier = section.getDouble("smelt_time_multiplier", 2.0);

            // Parse durability ranges
            Map<Integer, DurabilityRange> durabilityRanges = new HashMap<>();
            ConfigurationSection rangesSection = section.getConfigurationSection("durability_ranges");

            if (rangesSection != null) {
                for (String threshold : rangesSection.getKeys(false)) {
                    ConfigurationSection rangeSection = rangesSection.getConfigurationSection(threshold);
                    if (rangeSection != null) {
                        int thresholdValue = Integer.parseInt(threshold);
                        int min = rangeSection.getInt("min", 0);
                        int max = rangeSection.getInt("max", 1);
                        durabilityRanges.put(thresholdValue, new DurabilityRange(min, max));
                    }
                }
            }

            return new SmeltableItem(
                    id,
                    material,
                    enabled,
                    smeltIn,
                    ignoreSticks,
                    durabilityBased,
                    outputMaterial,
                    maxDurability,
                    smeltTimeMultiplier,
                    durabilityRanges
            );

        } catch (Exception e) {
            plugin.getLogger().warning("Error parsing item '" + id + "': " + e.getMessage());
            return null;
        }
    }

    public SmeltableItem getSmeltableItem(Material material) {
        return smeltableItems.get(material);
    }

    public boolean isSmeltable(Material material) {
        return smeltableItems.containsKey(material);
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "Message not found: " + key);
    }

    public String getMessage(String key, Map<String, String> replacements) {
        String message = getMessage(key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    public int getLoadedItemsCount() {
        return smeltableItems.size();
    }

    public Collection<SmeltableItem> getAllSmeltableItems() {
        return smeltableItems.values();
    }
}