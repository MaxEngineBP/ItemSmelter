package com.itemsmelter;

import com.itemsmelter.commands.ItemSmelterCommand;
import com.itemsmelter.config.ConfigManager;
import com.itemsmelter.listeners.FurnaceListener;
import com.itemsmelter.managers.LocaleManager;
import com.itemsmelter.managers.PlayerSettingsManager;
import com.itemsmelter.managers.RecipeManager;
import com.itemsmelter.managers.SmeltingManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemSmelter extends JavaPlugin {

    private static ItemSmelter instance;
    private ConfigManager configManager;
    private SmeltingManager smeltingManager;
    private PlayerSettingsManager playerSettingsManager;
    private RecipeManager recipeManager;
    private LocaleManager localeManager;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize configuration
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        configManager.loadConfiguration();

        // Initialize locale manager
        localeManager = new LocaleManager(this);
        localeManager.loadLocales();

        // Initialize managers
        smeltingManager = new SmeltingManager(this);
        playerSettingsManager = new PlayerSettingsManager(this);
        recipeManager = new RecipeManager(this);

        // Register recipes
        recipeManager.registerRecipes();

        // Register listeners
        getServer().getPluginManager().registerEvents(new FurnaceListener(this), this);

        // Register commands
        getCommand("itemsmelter").setExecutor(new ItemSmelterCommand(this));

        getLogger().info("ItemSmelter v" + getDescription().getVersion() + " enabled!");
        getLogger().info("Loaded " + configManager.getLoadedItemsCount() + " smeltable items");
    }

    @Override
    public void onDisable() {
        if (recipeManager != null) {
            recipeManager.cleanup();
        }
        if (smeltingManager != null) {
            smeltingManager.cleanup();
        }
        if (playerSettingsManager != null) {
            playerSettingsManager.saveSettings();
        }
        getLogger().info("ItemSmelter disabled!");
    }

    public void reload() {
        reloadConfig();
        configManager.loadConfiguration();
        localeManager.loadLocales();
        smeltingManager.reload();
        recipeManager.registerRecipes();
    }

    public static ItemSmelter getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SmeltingManager getSmeltingManager() {
        return smeltingManager;
    }

    public PlayerSettingsManager getPlayerSettingsManager() {
        return playerSettingsManager;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public LocaleManager getLocaleManager() {
        return localeManager;
    }
}