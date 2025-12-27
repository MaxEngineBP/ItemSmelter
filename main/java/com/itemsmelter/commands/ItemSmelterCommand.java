package com.itemsmelter.commands;

import com.itemsmelter.ItemSmelter;
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
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("itemsmelter.admin")) {
                    sender.sendMessage(plugin.getLocaleManager().getMessage("no_permission"));
                    return true;
                }

                try {
                    plugin.reload();
                    sender.sendMessage(plugin.getLocaleManager().getMessage("reload_success"));

                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("items", String.valueOf(plugin.getConfigManager().getLoadedItemsCount()));
                    sender.sendMessage(plugin.getLocaleManager().getMessage("info_items", replacements));
                } catch (Exception e) {
                    sender.sendMessage(plugin.getLocaleManager().getMessage("reload_error"));
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
                    // Show current status
                    boolean enabled = plugin.getPlayerSettingsManager().isSoundEnabled(player);
                    if (enabled) {
                        player.sendMessage(plugin.getLocaleManager().getMessage("sound_status_on"));
                    } else {
                        player.sendMessage(plugin.getLocaleManager().getMessage("sound_status_off"));
                    }
                } else if (args.length == 2) {
                    // Set specific value
                    String value = args[1].toLowerCase();
                    if (value.equals("on") || value.equals("enable") || value.equals("true")) {
                        plugin.getPlayerSettingsManager().setSoundEnabled(player, true);
                        player.sendMessage(plugin.getLocaleManager().getMessage("sound_enabled"));
                    } else if (value.equals("off") || value.equals("disable") || value.equals("false")) {
                        plugin.getPlayerSettingsManager().setSoundEnabled(player, false);
                        player.sendMessage(plugin.getLocaleManager().getMessage("sound_disabled"));
                    } else {
                        player.sendMessage(plugin.getLocaleManager().getMessage("sound_usage"));
                    }
                } else {
                    player.sendMessage(plugin.getLocaleManager().getMessage("sound_usage"));
                }
                return true;

            case "help":
                sendHelp(sender);
                return true;

            case "info":
                Map<String, String> infoReplacements = new HashMap<>();
                infoReplacements.put("version", plugin.getDescription().getVersion());
                infoReplacements.put("items", String.valueOf(plugin.getConfigManager().getLoadedItemsCount()));
                infoReplacements.put("author", String.join(", ", plugin.getDescription().getAuthors()));

                sender.sendMessage(plugin.getLocaleManager().getMessage("info_header"));
                sender.sendMessage(plugin.getLocaleManager().getMessage("info_version", infoReplacements));
                sender.sendMessage(plugin.getLocaleManager().getMessage("info_items", infoReplacements));
                sender.sendMessage(plugin.getLocaleManager().getMessage("info_author", infoReplacements));
                return true;

            default:
                sender.sendMessage("§cUnknown subcommand. Use /itemsmelter help for help.");
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getLocaleManager().getMessage("help_header"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("help_reload"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("help_sound"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("help_info"));
        sender.sendMessage(plugin.getLocaleManager().getMessage("help_help"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
            completions.add("sound");
            completions.add("help");
            completions.add("info");
            return filterCompletions(completions, args[0]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sound")) {
            completions.add("on");
            completions.add("off");
            return filterCompletions(completions, args[1]);
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