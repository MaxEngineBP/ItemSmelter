package com.itemsmelter.commands;

import com.itemsmelter.ItemSmelter;
import com.itemsmelter.models.SmeltableItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemSmelterCommand implements CommandExecutor, TabCompleter {

    private final ItemSmelter plugin;

    public ItemSmelterCommand(ItemSmelter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                sendHelp((Player) sender);
            } else {
                sender.sendMessage("§6=== ItemSmelter Commands ===");
                sender.sendMessage("§e/itemsmelter reload §7- Reload configuration");
                sender.sendMessage("§e/itemsmelter recipes §7- Show loaded recipes");
                sender.sendMessage("§e/itemsmelter info §7- Show plugin information");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("itemsmelter.admin")) {
                    if (sender instanceof Player) {
                        sender.sendMessage(plugin.getLocaleManager().getMessage(((Player) sender).getUniqueId(), "no_permission"));
                    } else {
                        sender.sendMessage("§cYou don't have permission!");
                    }
                    return true;
                }

                try {
                    plugin.reload();
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        sender.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "reload_success"));
                        
                        Map<String, String> replacements = new HashMap<>();
                        replacements.put("items", String.valueOf(plugin.getConfigManager().getLoadedItemsCount()));
                        sender.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "info_items", replacements));
                    } else {
                        sender.sendMessage("§aConfiguration reloaded successfully!");
                    }
                } catch (Exception e) {
                    if (sender instanceof Player) {
                        sender.sendMessage(plugin.getLocaleManager().getMessage(((Player) sender).getUniqueId(), "reload_error"));
                    } else {
                        sender.sendMessage("§cError reloading configuration!");
                    }
                    e.printStackTrace();
                }
                return true;

            case "sound":
            case "sounds":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players!");
                    return true;
                }

                Player player = (Player) sender;
                
                if (args.length == 1) {
                    boolean enabled = plugin.getPlayerSettingsManager().isSoundEnabled(player);
                    if (enabled) {
                        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "sound_status_on"));
                    } else {
                        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "sound_status_off"));
                    }
                } else if (args.length == 2) {
                    String value = args[1].toLowerCase();
                    if (value.equals("on") || value.equals("enable") || value.equals("true")) {
                        plugin.getPlayerSettingsManager().setSoundEnabled(player, true);
                        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "sound_enabled"));
                    } else if (value.equals("off") || value.equals("disable") || value.equals("false")) {
                        plugin.getPlayerSettingsManager().setSoundEnabled(player, false);
                        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "sound_disabled"));
                    } else {
                        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "sound_usage"));
                    }
                } else {
                    player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "sound_usage"));
                }
                return true;

            case "lang":
            case "language":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players!");
                    return true;
                }

                Player langPlayer = (Player) sender;
                
                if (args.length == 1) {
                    // Show available languages
                    List<String> codes = plugin.getLocaleManager().getAvailableLocaleCodes();
                    String codesList = String.join(", ", codes);
                    
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("languages", codesList);
                    langPlayer.sendMessage(plugin.getLocaleManager().getMessage(langPlayer.getUniqueId(), "lang_list", replacements));
                } else if (args.length == 2) {
                    String code = args[1].toLowerCase();
                    
                    if (plugin.getLocaleManager().isValidLocaleCode(code)) {
                        plugin.getLocaleManager().setPlayerLocale(langPlayer.getUniqueId(), code);
                        
                        String languageName = plugin.getLocaleManager().getLocaleNameByCode(code);
                        Map<String, String> replacements = new HashMap<>();
                        replacements.put("language", languageName);
                        langPlayer.sendMessage(plugin.getLocaleManager().getMessage(langPlayer.getUniqueId(), "lang_changed", replacements));
                    } else {
                        List<String> codes = plugin.getLocaleManager().getAvailableLocaleCodes();
                        String codesList = String.join(", ", codes);
                        
                        Map<String, String> replacements = new HashMap<>();
                        replacements.put("languages", codesList);
                        langPlayer.sendMessage(plugin.getLocaleManager().getMessage(langPlayer.getUniqueId(), "lang_invalid", replacements));
                    }
                } else {
                    langPlayer.sendMessage(plugin.getLocaleManager().getMessage(langPlayer.getUniqueId(), "lang_usage"));
                }
                return true;

            case "recipes":
                if (!sender.hasPermission("itemsmelter.admin")) {
                    if (sender instanceof Player) {
                        sender.sendMessage(plugin.getLocaleManager().getMessage(((Player) sender).getUniqueId(), "no_permission"));
                    } else {
                        sender.sendMessage("§cYou don't have permission!");
                    }
                    return true;
                }

                List<SmeltableItem> items = plugin.getRecipeManager().getRegisteredItems();
                
                if (sender instanceof Player) {
                    Player recipePlayer = (Player) sender;
                    Map<String, String> headerReplacements = new HashMap<>();
                    headerReplacements.put("count", String.valueOf(items.size()));
                    sender.sendMessage(plugin.getLocaleManager().getMessage(recipePlayer.getUniqueId(), "recipes_header", headerReplacements));
                    
                    for (SmeltableItem item : items) {
                        Map<String, String> itemReplacements = new HashMap<>();
                        itemReplacements.put("id", item.getId());
                        itemReplacements.put("material", item.getMaterial().name());
                        itemReplacements.put("output", item.getOutputMaterial().name());
                        itemReplacements.put("furnace", item.getSmeltIn());
                        sender.sendMessage(plugin.getLocaleManager().getMessage(recipePlayer.getUniqueId(), "recipes_item", itemReplacements));
                    }
                } else {
                    sender.sendMessage("§6=== Loaded Recipes (" + items.size() + ") ===");
                    for (SmeltableItem item : items) {
                        sender.sendMessage("§e" + item.getId() + " §7- §f" + item.getMaterial().name() + 
                                         " §7-> §f" + item.getOutputMaterial().name() + " §7(" + item.getSmeltIn() + ")");
                    }
                }
                return true;

            case "help":
                if (sender instanceof Player) {
                    sendHelp((Player) sender);
                } else {
                    sender.sendMessage("§6=== ItemSmelter Commands ===");
                    sender.sendMessage("§e/itemsmelter reload §7- Reload configuration");
                    sender.sendMessage("§e/itemsmelter recipes §7- Show loaded recipes");
                    sender.sendMessage("§e/itemsmelter info §7- Show plugin information");
                }
                return true;

            case "info":
                Map<String, String> infoReplacements = new HashMap<>();
                infoReplacements.put("version", plugin.getDescription().getVersion());
                infoReplacements.put("items", String.valueOf(plugin.getConfigManager().getLoadedItemsCount()));
                infoReplacements.put("recipes", String.valueOf(plugin.getRecipeManager().getRegisteredCount()));
                infoReplacements.put("author", String.join(", ", plugin.getDescription().getAuthors()));
                
                if (sender instanceof Player) {
                    Player infoPlayer = (Player) sender;
                    sender.sendMessage(plugin.getLocaleManager().getMessage(infoPlayer.getUniqueId(), "info_header"));
                    sender.sendMessage(plugin.getLocaleManager().getMessage(infoPlayer.getUniqueId(), "info_version", infoReplacements));
                    sender.sendMessage(plugin.getLocaleManager().getMessage(infoPlayer.getUniqueId(), "info_items", infoReplacements));
                    sender.sendMessage(plugin.getLocaleManager().getMessage(infoPlayer.getUniqueId(), "info_recipes", infoReplacements));
                    sender.sendMessage(plugin.getLocaleManager().getMessage(infoPlayer.getUniqueId(), "info_author", infoReplacements));
                } else {
                    sender.sendMessage("§6=== ItemSmelter Info ===");
                    sender.sendMessage("§eVersion: §f" + infoReplacements.get("version"));
                    sender.sendMessage("§eLoaded items: §f" + infoReplacements.get("items"));
                    sender.sendMessage("§eRegistered recipes: §f" + infoReplacements.get("recipes"));
                    sender.sendMessage("§eAuthor: §f" + infoReplacements.get("author"));
                }
                return true;

            default:
                sender.sendMessage("§cUnknown subcommand. Use /itemsmelter help for help.");
                return true;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "help_header"));
        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "help_reload"));
        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "help_sound"));
        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "help_lang"));
        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "help_recipes"));
        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "help_info"));
        player.sendMessage(plugin.getLocaleManager().getMessage(player.getUniqueId(), "help_help"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
            completions.add("sound");
            completions.add("lang");
            completions.add("recipes");
            completions.add("help");
            completions.add("info");
            return filterCompletions(completions, args[0]);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("sound")) {
                completions.add("on");
                completions.add("off");
                return filterCompletions(completions, args[1]);
            } else if (args[0].equalsIgnoreCase("lang") || args[0].equalsIgnoreCase("language")) {
                return plugin.getLocaleManager().getAvailableLocaleCodes();
            }
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> completions, String input) {
        List<String> filtered = new ArrayList<>();
        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(input.toLowerCase())) {
                filtered.add(completion);
            }
        }
        return filtered;
    }
}
