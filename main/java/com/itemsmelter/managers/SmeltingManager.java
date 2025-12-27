package com.itemsmelter.managers;

import com.itemsmelter.ItemSmelter;
import com.itemsmelter.models.SmeltableItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlastFurnace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SmeltingManager {

    private final ItemSmelter plugin;
    private final Map<Location, SmeltingProcess> activeProcesses;

    public SmeltingManager(ItemSmelter plugin) {
        this.plugin = plugin;
        this.activeProcesses = new HashMap<>();
    }

    public boolean canSmelt(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        SmeltableItem smeltableItem = plugin.getConfigManager().getSmeltableItem(item.getType());
        return smeltableItem != null && smeltableItem.isEnabled();
    }

    public SmeltableItem getSmeltableItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        return plugin.getConfigManager().getSmeltableItem(item.getType());
    }

    public int calculateOutputAmount(ItemStack item, SmeltableItem smeltableItem) {
        if (!smeltableItem.isDurabilityBased()) {
            return smeltableItem.calculateOutput(smeltableItem.getMaxDurability());
        }

        if (item.getItemMeta() instanceof Damageable) {
            Damageable damageable = (Damageable) item.getItemMeta();
            int damage = damageable.getDamage();
            int currentDurability = smeltableItem.getMaxDurability() - damage;
            return smeltableItem.calculateOutput(currentDurability);
        }

        return smeltableItem.calculateOutput(smeltableItem.getMaxDurability());
    }

    public void startSmelting(Location furnaceLocation, ItemStack source, SmeltableItem smeltableItem, UUID playerId) {
        // Prevent duplicate processes
        if (activeProcesses.containsKey(furnaceLocation)) {
            return;
        }

        int outputAmount = calculateOutputAmount(source, smeltableItem);
        double cookTimeMultiplier = smeltableItem.getSmeltTimeMultiplier();

        // Base blast furnace cook time is 100 ticks (5 seconds)
        int cookTimeTicks = (int) (100 * cookTimeMultiplier);

        SmeltingProcess process = new SmeltingProcess(
                furnaceLocation,
                source.clone(),
                smeltableItem,
                outputAmount,
                cookTimeTicks,
                playerId
        );

        activeProcesses.put(furnaceLocation, process);
    }

    public void cancelSmelting(Location furnaceLocation) {
        SmeltingProcess process = activeProcesses.remove(furnaceLocation);
        if (process != null && process.task != null) {
            process.task.cancel();
        }
    }

    public boolean isActivelySmelting(Location furnaceLocation) {
        return activeProcesses.containsKey(furnaceLocation);
    }

    public SmeltingProcess getProcess(Location furnaceLocation) {
        return activeProcesses.get(furnaceLocation);
    }

    public void completeSmelting(Location furnaceLocation) {
        activeProcesses.remove(furnaceLocation);
    }

    public void reload() {
        // Cancel all active processes
        for (SmeltingProcess process : activeProcesses.values()) {
            if (process.task != null) {
                process.task.cancel();
            }
        }
        activeProcesses.clear();
    }

    public void cleanup() {
        reload();
    }

    public static class SmeltingProcess {
        private final Location furnaceLocation;
        private final ItemStack sourceItem;
        private final SmeltableItem smeltableItem;
        private final int outputAmount;
        private final int cookTimeTicks;
        private final UUID playerId;
        private BukkitTask task;
        private int currentCookTime;

        public SmeltingProcess(Location furnaceLocation, ItemStack sourceItem,
                               SmeltableItem smeltableItem, int outputAmount,
                               int cookTimeTicks, UUID playerId) {
            this.furnaceLocation = furnaceLocation;
            this.sourceItem = sourceItem;
            this.smeltableItem = smeltableItem;
            this.outputAmount = outputAmount;
            this.cookTimeTicks = cookTimeTicks;
            this.playerId = playerId;
            this.currentCookTime = 0;
        }

        // Getters
        public Location getFurnaceLocation() { return furnaceLocation; }
        public ItemStack getSourceItem() { return sourceItem; }
        public SmeltableItem getSmeltableItem() { return smeltableItem; }
        public int getOutputAmount() { return outputAmount; }
        public int getCookTimeTicks() { return cookTimeTicks; }
        public UUID getPlayerId() { return playerId; }
        public BukkitTask getTask() { return task; }
        public int getCurrentCookTime() { return currentCookTime; }

        public void setTask(BukkitTask task) { this.task = task; }
        public void incrementCookTime() { this.currentCookTime++; }
    }
}