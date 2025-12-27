package com.itemsmelter.managers;

import com.itemsmelter.ItemSmelter;
import com.itemsmelter.models.SmeltableItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.List;

public class RecipeManager {

    private final ItemSmelter plugin;
    private final List<NamespacedKey> registeredRecipes = new ArrayList<>();
    private final List<SmeltableItem> registeredItems = new ArrayList<>();

    public RecipeManager(ItemSmelter plugin) {
        this.plugin = plugin;
    }

    public void registerRecipes() {
        removeRecipes();
        registeredItems.clear();

        int registered = 0;
        for (SmeltableItem item : plugin.getConfigManager().getAllSmeltableItems()) {
            if (!item.isEnabled()) {
                continue;
            }

            ItemStack result = new ItemStack(item.getOutputMaterial(), 1);
            ItemStack source = new ItemStack(item.getMaterial(), 1);
            NamespacedKey key = new NamespacedKey(plugin, "smelt_" + item.getId().toLowerCase());

            try {
                Recipe recipe;
                
                if ("BLAST_FURNACE".equals(item.getSmeltIn())) {
                    BlastingRecipe blastRecipe = new BlastingRecipe(
                        key,
                        result,
                        source.getType(),
                        0.1f,
                        (int) (100 * item.getSmeltTimeMultiplier())
                    );
                    recipe = blastRecipe;
                } else {
                    FurnaceRecipe furnaceRecipe = new FurnaceRecipe(
                        key,
                        result,
                        source.getType(),
                        0.1f,
                        (int) (200 * item.getSmeltTimeMultiplier())
                    );
                    recipe = furnaceRecipe;
                }

                Bukkit.addRecipe(recipe);
                registeredRecipes.add(key);
                registeredItems.add(item);
                registered++;
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to register recipe for " + item.getId() + ": " + e.getMessage());
            }
        }
        
        plugin.getLogger().info("Registered " + registered + " recipe(s)");
    }

    public void removeRecipes() {
        for (NamespacedKey key : registeredRecipes) {
            Bukkit.removeRecipe(key);
        }
        registeredRecipes.clear();
    }

    public List<SmeltableItem> getRegisteredItems() {
        return new ArrayList<>(registeredItems);
    }

    public int getRegisteredCount() {
        return registeredItems.size();
    }

    public void cleanup() {
        removeRecipes();
    }
}
