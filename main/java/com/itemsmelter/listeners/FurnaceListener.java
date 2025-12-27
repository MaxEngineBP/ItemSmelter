package com.itemsmelter.listeners;

import com.itemsmelter.ItemSmelter;
import com.itemsmelter.managers.SmeltingManager;
import com.itemsmelter.models.SmeltableItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlastFurnace;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class FurnaceListener implements Listener {

    private final ItemSmelter plugin;
    private final SmeltingManager smeltingManager;
    private final Map<Location, Long> lastSmelt = new HashMap<>();
    private final Map<Location, Boolean> processingFailure = new HashMap<>();

    public FurnaceListener(ItemSmelter plugin) {
        this.plugin = plugin;
        this.smeltingManager = plugin.getSmeltingManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        boolean isBlastFurnace = event.getBlock().getState() instanceof BlastFurnace;
        boolean isFurnace = event.getBlock().getState() instanceof Furnace;

        if (!isBlastFurnace && !isFurnace) {
            return;
        }

        Furnace furnace = (Furnace) event.getBlock().getState();
        ItemStack smelting = furnace.getInventory().getSmelting();

        if (smelting == null || smelting.getType() == Material.AIR) {
            return;
        }

        SmeltableItem smeltableItem = smeltingManager.getSmeltableItem(smelting);
        if (smeltableItem == null) {
            return;
        }

        String requiredFurnace = smeltableItem.getSmeltIn();
        if ("BLAST_FURNACE".equals(requiredFurnace) && !isBlastFurnace) {
            event.setCancelled(true);
            return;
        }
        if ("FURNACE".equals(requiredFurnace) && !isFurnace) {
            event.setCancelled(true);
            return;
        }

        Location location = event.getBlock().getLocation();
        if (!smeltingManager.isActivelySmelting(location)) {
            smeltingManager.startSmelting(location, smelting, smeltableItem, null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (event.isCancelled()) {
            return;
        }

        boolean isBlastFurnace = event.getBlock().getState() instanceof BlastFurnace;
        boolean isFurnace = event.getBlock().getState() instanceof Furnace && !(event.getBlock().getState() instanceof BlastFurnace);

        Location location = event.getBlock().getLocation();

        // Prevent duplicate processing
        long now = System.currentTimeMillis();
        Long last = lastSmelt.get(location);
        if (last != null && (now - last) < 100) {
            event.setCancelled(true);
            return;
        }
        lastSmelt.put(location, now);

        ItemStack source = event.getSource();
        SmeltableItem smeltableItem = smeltingManager.getSmeltableItem(source);

        if (smeltableItem == null) {
            return;
        }

        // Check furnace type matches requirement
        String requiredFurnace = smeltableItem.getSmeltIn();
        if ("BLAST_FURNACE".equals(requiredFurnace) && !isBlastFurnace) {
            // Wrong furnace type - cancel
            event.setCancelled(true);
            return;
        }
        if ("FURNACE".equals(requiredFurnace) && !isFurnace) {
            // Wrong furnace type - cancel
            event.setCancelled(true);
            return;
        }

        // Calculate output
        int outputAmount = smeltingManager.calculateOutputAmount(source, smeltableItem);

        if (outputAmount <= 0) {
            // FAILURE
            event.setCancelled(true);
            processingFailure.put(location, true);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                handleFailedSmelting(location);
            }, 2L); // Increased delay to 2 ticks

            return;
        }

        // SUCCESS
        ItemStack customResult = new ItemStack(smeltableItem.getOutputMaterial(), outputAmount);
        event.setResult(customResult);

        Bukkit.getScheduler().runTask(plugin, () -> {
            playSound(location, "success");
        });

        smeltingManager.completeSmelting(location);
    }

    private void handleFailedSmelting(Location location) {
        if (!processingFailure.getOrDefault(location, false)) {
            return;
        }

        processingFailure.remove(location);

        if (!(location.getBlock().getState() instanceof Furnace)) {
            return;
        }

        Furnace furnace = (Furnace) location.getBlock().getState();
        FurnaceInventory inv = furnace.getInventory();

        ItemStack smelting = inv.getSmelting();

        if (smelting != null && smelting.getType() != Material.AIR) {
            // Remove item
            if (smelting.getAmount() > 1) {
                smelting.setAmount(smelting.getAmount() - 1);
                inv.setSmelting(smelting);
            } else {
                inv.setSmelting(null);
            }

            // Reset cook time and burn time
            furnace.setCookTime((short) 0);
            furnace.setBurnTime((short) 0);

            // Update furnace
            furnace.update(true, false);

            // Play sound
            playSound(location, "failure");
        }

        smeltingManager.completeSmelting(location);
    }

    private void playSound(Location location, String type) {
        String soundName = plugin.getConfig().getString("sounds." + type, "BLOCK_ANVIL_USE");
        float volume = (float) plugin.getConfig().getDouble("sounds." + type + "_volume", 0.5);
        float pitch = (float) plugin.getConfig().getDouble("sounds." + type + "_pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(location.getWorld()) &&
                        player.getLocation().distance(location) <= 16) {
                    if (plugin.getPlayerSettingsManager().isSoundEnabled(player)) {
                        player.playSound(location, sound, volume, pitch);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + soundName);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        boolean isBlastFurnace = event.getInventory().getHolder() instanceof BlastFurnace;
        boolean isFurnace = event.getInventory().getHolder() instanceof Furnace;

        if (!isBlastFurnace && !isFurnace) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (!player.hasPermission("itemsmelter.use") && !player.hasPermission("itemsmelter.bypass")) {
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();

            if (cursor != null && smeltingManager.canSmelt(cursor)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
                return;
            }

            if (current != null && smeltingManager.canSmelt(current)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        boolean isBlastFurnace = event.getInventory().getHolder() instanceof BlastFurnace;
        boolean isFurnace = event.getInventory().getHolder() instanceof Furnace;

        if (!isBlastFurnace && !isFurnace) {
            return;
        }

        FurnaceInventory furnaceInv = (FurnaceInventory) event.getInventory();
        Location location = furnaceInv.getLocation();

        if (location != null) {
            if (smeltingManager.isActivelySmelting(location)) {
                ItemStack smelting = furnaceInv.getSmelting();
                if (smelting == null || smelting.getType() == Material.AIR) {
                    smeltingManager.cancelSmelting(location);
                }
            }

            lastSmelt.remove(location);
            processingFailure.remove(location);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        boolean isBlastFurnace = event.getBlock().getState() instanceof BlastFurnace;
        boolean isFurnace = event.getBlock().getState() instanceof Furnace;

        if (!isBlastFurnace && !isFurnace) {
            return;
        }

        Location location = event.getBlock().getLocation();
        if (smeltingManager.isActivelySmelting(location)) {
            smeltingManager.completeSmelting(location);
        }
    }
}