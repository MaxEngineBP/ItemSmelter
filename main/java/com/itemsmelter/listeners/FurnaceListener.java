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

    // Check if placing item in smelting slot
    if (event.getSlot() == 0 || event.getRawSlot() == 0) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        ItemStack toCheck = null;
        
        // Determine which item is being placed
        if (cursor != null && cursor.getType() != Material.AIR) {
            toCheck = cursor;
        } else if (event.isShiftClick() && current != null && current.getType() != Material.AIR) {
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
            if (f.getInventory().getSmelting() != null) {
                f.getInventory().setSmelting(null);
                f.setCookTime((short) 0);
                f.update(true, false);
            }
        }, 1L);
        
        // Play sound
        playSound(location, "failure");
    }
    
    smeltingManager.completeSmelting(location);
}
