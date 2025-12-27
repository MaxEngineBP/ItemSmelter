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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FurnaceListener implements Listener {

    private final ItemSmelter plugin;
    private final SmeltingManager smeltingManager;
    private final Map<Location, Long> lastSmelt = new HashMap<>();
    private final Set<Location> processingFailure = new HashSet<>();

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
        
        // Skip if already processing failure
        if (processingFailure.contains(location)) {
            event.setCancelled(true);
            return;
        }
        
        // Prevent duplicate processing
        long now = System.currentTimeMillis();
        Long last = lastSmelt.get(location);
        if (last != null && (now - last) < 150) {
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
        
        plugin.getLogger().info("Smelting " + source.getType() + " - Output: " + outputAmount + " (durability: " + 
            (source.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable ? 
            ((org.bukkit.inventory.meta.Damageable)source.getItemMeta()).getDamage() : "N/A") + ")");
        
        if (outputAmount <= 0) {
            // FAILURE - Set result to AIR and remove item
            event.setCancelled(true);
            processingFailure.add(location);
            
            // Schedule removal on next tick
            Bukkit.getScheduler().runTask(plugin, () -> {
                handleFailedSmelting(location);
            });
            
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
        if (!(location.getBlock().getState() instanceof Furnace)) {
            processingFailure.remove(location);
            return;
        }
        
        Furnace furnace = (Furnace) location.getBlock().getState();
        FurnaceInventory inv = furnace.getInventory();
        
        ItemStack smelting = inv.getSmelting();
        
        if (smelting != null && smelting.getType() != Material.AIR) {
            // Remove one item from stack
            if (smelting.getAmount() > 1) {
                smelting.setAmount(smelting.getAmount() - 1);
                inv.setSmelting(smelting);
            } else {
                inv.setSmelting(null);
            }
            
            // ВАЖНО: Сбрасываем только cookTime, НЕ трогаем burnTime (топливо)!
            furnace.setCookTime((short) 0);
            
            // Update furnace state
            furnace.update(true, false);
            
            plugin.getLogger().info("Removed failed smelting item at " + location);
            
            // Play failure sound
            playSound(location, "failure");
        }
        
        // Clean up and allow next smelting
        processingFailure.remove(location);
        smeltingManager.completeSmelting(location);
        
        // Force update after 1 tick to ensure item is gone
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (location.getBlock().getState() instanceof Furnace) {
                Furnace f = (Furnace) location.getBlock().getState();
                
                // If item is still there somehow, force remove it
                ItemStack stillThere = f.getInventory().getSmelting();
                if (stillThere != null && stillThere.getType() != Material.AIR) {
                    SmeltableItem check = smeltingManager.getSmeltableItem(stillThere);
                    if (check != null) {
                        // This is still a custom item that failed - remove it
                        f.getInventory().setSmelting(null);
                        f.setCookTime((short) 0);
                        f.update(true, false);
                        plugin.getLogger().warning("Force removed stuck item at " + location);
                    }
                }
            }
        }, 1L);
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        
        if (!(event.getView().getTopInventory().getHolder() instanceof Furnace)) {
            return;
        }

        boolean isBlastFurnace = event.getView().getTopInventory().getHolder() instanceof BlastFurnace;
        boolean isFurnace = event.getView().getTopInventory().getHolder() instanceof Furnace && !isBlastFurnace;

        // Handle shift-click from player inventory
        if (event.isShiftClick() && event.getClickedInventory() != null && 
            event.getClickedInventory().getHolder() == player) {
            
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                SmeltableItem smeltableItem = smeltingManager.getSmeltableItem(clicked);
                
                if (smeltableItem != null) {
                    if (!player.hasPermission("itemsmelter.use") && !player.hasPermission("itemsmelter.bypass")) {
                        event.setCancelled(true);
                        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "no_permission"));
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
                }
            }
        }
        
        // Handle direct click on smelting slot
        if (event.getClickedInventory() != null && 
            event.getClickedInventory().getHolder() instanceof Furnace && 
            event.getSlot() == 0) {
            
            ItemStack cursor = event.getCursor();
            
            if (cursor != null && cursor.getType() != Material.AIR) {
                SmeltableItem smeltableItem = smeltingManager.getSmeltableItem(cursor);
                
                if (smeltableItem != null) {
                    if (!player.hasPermission("itemsmelter.use") && !player.hasPermission("itemsmelter.bypass")) {
                        event.setCancelled(true);
                        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "no_permission"));
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
                }
            }
        }

        if (!player.hasPermission("itemsmelter.use") && !player.hasPermission("itemsmelter.bypass")) {
            ItemStack current = event.getCurrentItem();
            if (current != null && smeltingManager.canSmelt(current)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "no_permission"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        
        if (!(event.getView().getTopInventory().getHolder() instanceof Furnace)) {
            return;
        }

        boolean isBlastFurnace = event.getView().getTopInventory().getHolder() instanceof BlastFurnace;
        boolean isFurnace = event.getView().getTopInventory().getHolder() instanceof Furnace && !isBlastFurnace;

        if (event.getRawSlots().contains(0)) {
            ItemStack draggedItem = event.getOldCursor();
            
            if (draggedItem != null && draggedItem.getType() != Material.AIR) {
                SmeltableItem smeltableItem = smeltingManager.getSmeltableItem(draggedItem);
                
                if (smeltableItem != null) {
                    if (!player.hasPermission("itemsmelter.use") && !player.hasPermission("itemsmelter.bypass")) {
                        event.setCancelled(true);
                        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "no_permission"));
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
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Furnace)) {
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
        if (!(event.getBlock().getState() instanceof Furnace)) {
            return;
        }

        Location location = event.getBlock().getLocation();
        if (smeltingManager.isActivelySmelting(location)) {
            smeltingManager.completeSmelting(location);
        }
    }
}
