package de.epiceric.shopchest.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.utils.Permissions;

class ShopTabCompleter implements TabCompleter {
    private ShopChest plugin;

    ShopTabCompleter(ShopChest plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase(Config.mainCommandName)) {

            List<String> createSubCommands = Arrays.asList("admin");
            List<String> editFields = Arrays.asList("amount", "buy", "sell", "holograms");
            List<String> editHologramOrientations =
                    Arrays.asList("reset", "faceme", "north", "south", "east", "west");
            List<String> debugSections =
                    Arrays.asList("status", "commands", "permissions", "placeholders");
            List<String> configSubCommands = Arrays.asList("add", "remove", "set");
            List<String> infoSubCommands = Arrays.asList("shop");
            List<String> areaShopRemoveEvents = Arrays.asList("DELETE", "RESELL", "SELL", "UNRENT");
            List<String> townyShopPlots = Arrays.asList("ARENA", "COMMERCIAL", "EMBASSY", "FARM", "INN", "JAIL", "RESIDENTIAL", "SPLEEF", "WILDS");

            Set<String> configValues = plugin.getConfig().getKeys(true);
            List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());

            ArrayList<String> returnCompletions = new ArrayList<>();

            if (args.length == 2) {
                if (args[0].equalsIgnoreCase("admin")) {
                    final List<String> adminCommands = new ArrayList<>();
                    if (sender.hasPermission(Permissions.ADMIN_LIST)) {
                        adminCommands.add("list");
                    }
                    if (sender.hasPermission(Permissions.ADMIN_AUDIT)) {
                        adminCommands.add("audit");
                    }
                    if (sender.hasPermission(Permissions.ADMIN_DEBUG)) {
                        adminCommands.add("debug");
                    }
                    if (sender.hasPermission(Permissions.ADMIN_STOREFRONT)) {
                        adminCommands.add("storefront");
                    }
                    if (sender.hasPermission(Permissions.ADMIN_ADVERTISE)) {
                        adminCommands.add("advertise");
                    }
                    if (sender.hasPermission(Permissions.ADMIN_EXPORT)) {
                        adminCommands.add("export");
                    }
                    return filterCompletions(adminCommands, args[1]);
                } else if (args[0].equalsIgnoreCase("profile")) {
                    final List<String> profileCommands = new ArrayList<>(
                            Arrays.asList("set", "clear", "featured", "shops"));
                    profileCommands.addAll(playerNames);
                    return filterCompletions(profileCommands, args[1]);
                } else if (args[0].equalsIgnoreCase("advertise")) {
                    return filterCompletions(
                            Arrays.asList("pass", "status", "cancel"), args[1]);
                } else if (args[0].equalsIgnoreCase("edit")) {
                    return filterCompletions(editFields, args[1]);
                } else if (args[0].equalsIgnoreCase("debug")
                        && sender.hasPermission(Permissions.ADMIN_DEBUG)) {
                    return filterCompletions(debugSections, args[1]);
                } else if (args[0].equals("config")) {
                    if (!args[1].equals("")) {
                        for (String s : configSubCommands) {
                            if (s.startsWith(args[1])) {
                                returnCompletions.add(s);
                            }
                        }

                        return returnCompletions;
                    } else {
                        return configSubCommands;
                    }
                } else if (args[0].equals("info")) {
                    if (!args[1].equals("")) {
                        for (String s : infoSubCommands) {
                            if (s.startsWith(args[1])) {
                                returnCompletions.add(s);
                            }
                        }

                        return returnCompletions;
                    } else {
                        return infoSubCommands;
                    }
                } else if (args[0].equals("removeall")) {
                    if (!args[1].equals("")) {
                        for (String name : playerNames) {
                            if (name.startsWith(args[1])) {
                                returnCompletions.add(name);
                            }
                        }

                        return returnCompletions;
                    } else {
                        return playerNames;
                    }
                }
            } else if (args.length == 3) {
                if (args[0].equalsIgnoreCase("edit")
                        && args[1].equalsIgnoreCase("holograms")) {
                    return filterCompletions(editHologramOrientations, args[2]);
                } else if (args[0].equalsIgnoreCase("admin")
                        && args[1].equalsIgnoreCase("list")
                        && sender.hasPermission(Permissions.ADMIN_LIST)) {
                    return filterCompletions(playerNames, args[2]);
                } else if (args[0].equalsIgnoreCase("admin")
                        && args[1].equalsIgnoreCase("audit")
                        && sender.hasPermission(Permissions.ADMIN_AUDIT)) {
                    final List<String> auditScopes = new ArrayList<>();
                    auditScopes.add("all");
                    auditScopes.addAll(playerNames);
                    return filterCompletions(auditScopes, args[2]);
                } else if (args[0].equalsIgnoreCase("admin")
                        && args[1].equalsIgnoreCase("advertise")
                        && sender.hasPermission(Permissions.ADMIN_ADVERTISE)) {
                    return filterCompletions(List.of("currency"), args[2]);
                } else if (args[0].equalsIgnoreCase("admin")
                        && args[1].equalsIgnoreCase("storefront")
                        && sender.hasPermission(Permissions.ADMIN_STOREFRONT)) {
                    return filterCompletions(playerNames, args[2]);
                } else if (args[0].equalsIgnoreCase("admin")
                        && args[1].equalsIgnoreCase("export")
                        && sender.hasPermission(Permissions.ADMIN_EXPORT)) {
                    return filterCompletions(List.of("marketplace"), args[2]);
                } else if (args[0].equalsIgnoreCase("profile")
                        && (args[1].equalsIgnoreCase("set")
                        || args[1].equalsIgnoreCase("clear"))) {
                    return filterCompletions(
                            Arrays.asList("name", "advertisement", "description", "location"),
                            args[2]);
                } else if (args[0].equalsIgnoreCase("profile")
                        && args[1].equalsIgnoreCase("featured")) {
                    return filterCompletions(Arrays.asList("add", "remove", "clear"), args[2]);
                } else if (args[0].equalsIgnoreCase("profile")
                        && !args[1].equalsIgnoreCase("shops")) {
                    return filterCompletions(List.of("shops"), args[2]);
                } else if (args[0].equals("config")) {
                    if (!args[2].equals("")) {
                        for (String s : configValues) {
                            if (s.startsWith(args[2])) {
                                returnCompletions.add(s);
                            }
                        }

                        return returnCompletions;
                    } else {
                        return new ArrayList<>(configValues);
                    }
                }
            } else if (args.length == 4) {
                if (args[0].equalsIgnoreCase("admin")
                        && args[1].equalsIgnoreCase("advertise")
                        && args[2].equalsIgnoreCase("currency")
                        && sender.hasPermission(Permissions.ADMIN_ADVERTISE)) {
                    return filterCompletions(Arrays.asList("status", "capture", "clear"), args[3]);
                } else if (args[0].equalsIgnoreCase("admin")
                        && args[1].equalsIgnoreCase("storefront")
                        && sender.hasPermission(Permissions.ADMIN_STOREFRONT)) {
                    return filterCompletions(
                            Arrays.asList("hide", "show", "suspend", "unsuspend", "clear"),
                            args[3]);
                } else if (args[0].equals("config")) {
                    if (args[1].equalsIgnoreCase("set")
                            && args[2].equalsIgnoreCase("hologram-text-alignment")) {
                        return filterCompletions(
                                Arrays.asList("LEFT", "CENTER", "RIGHT"), args[3]);
                    } else if (args[1].equalsIgnoreCase("set")
                            && plugin.getConfig().isBoolean(args[2])) {
                        return filterCompletions(Arrays.asList("true", "false"), args[3]);
                    } else if (args[2].equals("towny-shop-plots")) {
                        if (!args[3].equals("")) {
                            for (String s : townyShopPlots) {
                                if (s.startsWith(args[3])) {
                                    returnCompletions.add(s);
                                }
                            }

                            return returnCompletions;
                        } else {
                            return townyShopPlots;
                        }
                    } else if (args[2].equals("areashop-remove-shops")) {
                        if (!args[3].equals("")) {
                            for (String s : areaShopRemoveEvents) {
                                if (s.startsWith(args[3])) {
                                    returnCompletions.add(s);
                                }
                            }

                            return returnCompletions;
                        } else {
                            return areaShopRemoveEvents;
                        }
                    }
                }
            } else if (args.length == 5) {
                if (args[0].equals("create")) {
                    if (!args[4].equals("")) {
                        for (String s : createSubCommands) {
                            if (s.startsWith(args[4])) {
                                returnCompletions.add(s);
                            }
                        }

                        return returnCompletions;
                    } else {
                        return createSubCommands;
                    }
                }
            }
        }

        return new ArrayList<>();
    }

    private List<String> filterCompletions(List<String> candidates, String input) {
        final String prefix = input.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toList());
    }
}
