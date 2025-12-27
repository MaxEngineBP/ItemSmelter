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

        if (!isBlastFurnace && !isFurnace) {
            return;
        }

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
            event.setCancelled(true);
            return;
        }
        if ("FURNACE".equals(requiredFurnace) && !isFurnace) {
            event.setCancelled(true);
            return;
        }

        // Calculate output
        int outputAmount = smeltingManager.calculateOutputAmount(source, smeltableItem);

        if (outputAmount <= 0) {
            // FAILURE - Cancel event and handle manually
            event.setCancelled(true);
            processingFailure.put(location, true);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                handleFailedSmelting(location);
            }, 2L);

            return;
        }

        // SUCCESS - Set custom result
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
            // Remove item FIRST
            if (smelting.getAmount() > 1) {
                smelting.setAmount(smelting.getAmount() - 1);
                inv.setSmelting(smelting);
            } else {
                inv.setSmelting(null);
            }

            // Reset timers to stop smelting
            furnace.setCookTime((short) -1);
            furnace.setBurnTime((short) 0);

            // Force update
            furnace.update(true, false);

            // Schedule another check to ensure item is gone
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Furnace f = (Furnace) location.getBlock().getState();
                FurnaceInventory fInv = f.getInventory();
                ItemStack remainingSmelting = fInv.getSmelting();

                if (remainingSmelting != null && remainingSmelting.getType() != Material.AIR) {
                    fInv.setSmelting(null);
                    f.setCookTime((short) 0);
                    f.update(true, false);
                }
            }, 1L);

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        boolean isBlastFurnace = event.getInventory().getHolder() instanceof BlastFurnace;
        boolean isFurnace = event.getInventory().getHolder() instanceof Furnace && !(event.getInventory().getHolder() instanceof BlastFurnace);

        if (!isBlastFurnace && !isFurnace) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check if placing item in smelting slot (slot 0)
        if (event.getSlot() == 0 || event.getRawSlot() == 0) {
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();
            ItemStack toCheck = null;

            // Determine which item is being placed
            if (cursor != null && cursor.getType() != Material.AIR) {
                toCheck = cursor;
            } else if (event.isShiftClick() && current != null && current.getType() != Material.AIR) {
                // Player is shift-clicking from their inventory
                toCheck = current;
            }

            if (toCheck != null) {
                SmeltableItem smeltableItem = smeltingManager.getSmeltableItem(toCheck);

                if (smeltableItem != null) {
                    // Check permission
                    if (!player.hasPermission("itemsmelter.use") && !player.hasPermission("itemsmelter.bypass")) {
                        event.setCancelled(true);
                        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "no_permission"));
                        return;
                    }

                    // Check furnace type matches
                    String requiredFurnace = smeltableItem.getSmeltIn();
                    if ("BLAST_FURNACE".equals(requiredFurnace) && !isBlastFurnace) {
                        event.setCancelled(true);
                        return;
                    }
                    if ("FURNACE".equals(requiredFurnace) && !isFurnace) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // Check permission for taking items
        if (!player.hasPermission("itemsmelter.use") && !player.hasPermission("itemsmelter.bypass")) {
            ItemStack current = event.getCurrentItem();
            if (current != null && smeltingManager.canSmelt(current)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "no_permission"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        boolean isBlastFurnace = event.getInventory().getHolder() instanceof BlastFurnace;
        boolean isFurnace = event.getInventory().getHolder() instanceof Furnace && !(event.getInventory().getHolder() instanceof BlastFurnace);

        if (!isBlastFurnace && !isFurnace) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check if dragging to smelting slot
        for (int slot : event.getRawSlots()) {
            if (slot == 0) {
                ItemStack item = event.getNewItems().get(slot);
                if (item != null && item.getType() != Material.AIR) {
                    SmeltableItem smeltableItem = smeltingManager.getSmeltableItem(item);

                    if (smeltableItem != null) {
                        // Check permission
                        if (!player.hasPermission("itemsmelter.use") && !player.hasPermission("itemsmelter.bypass")) {
                            event.setCancelled(true);
                            player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "no_permission"));
                            return;
                        }

                        // Check furnace type matches
                        String requiredFurnace = smeltableItem.getSmeltIn();
                        if ("BLAST_FURNACE".equals(requiredFurnace) && !isBlastFurnace) {
                            event.setCancelled(true);
                            return;
                        }
                        if ("FURNACE".equals(requiredFurnace) && !isFurnace) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
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
