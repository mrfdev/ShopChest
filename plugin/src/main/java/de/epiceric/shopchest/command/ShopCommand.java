package de.epiceric.shopchest.command;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.Placeholder;
import de.epiceric.shopchest.language.Message;
import de.epiceric.shopchest.language.MessageRegistry;
import de.epiceric.shopchest.language.Replacement;
import de.epiceric.shopchest.utils.ClickType.SelectClickType;
import de.epiceric.shopchest.utils.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ShopCommand {

    private static boolean commandCreated = false;

    private final ShopChest plugin;
    private final String name;
    private final String fallbackPrefix;
    private final PluginCommand pluginCommand;
    private final ShopCommandExecutor executor;

    private final List<ShopSubCommand> subCommands = new ArrayList<>();

    public ShopCommand(final ShopChest plugin) {
        if (commandCreated) {
            IllegalStateException e = new IllegalStateException("Command has already been registered");
            plugin.debug(e);
            throw e;
        }

        this.plugin = plugin;
        this.name = Config.mainCommandName.toLowerCase(Locale.ENGLISH).trim();
        this.fallbackPrefix = plugin.getName().toLowerCase(Locale.ENGLISH).trim();
        this.pluginCommand = createPluginCommand();
        this.executor = new ShopCommandExecutor(plugin);

        ShopTabCompleter tabCompleter = new ShopTabCompleter(plugin);

        final Replacement cmdReplacement = new Replacement(Placeholder.COMMAND, name);

        addSubCommand(new ShopSubCommand("create", true, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                boolean receiveCreateMessage = sender.hasPermission(Permissions.CREATE);
                if (!receiveCreateMessage) {
                    for (PermissionAttachmentInfo permInfo : sender.getEffectivePermissions()) {
                        String perm = permInfo.getPermission();
                        if (perm.startsWith(Permissions.CREATE) && sender.hasPermission(perm)) {
                            receiveCreateMessage = true;
                            break;
                        }
                    }
                }

                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

                if (sender.hasPermission(Permissions.CREATE_ADMIN)) {
                    return messageRegistry.getMessage(Message.HELP_COMMAND_CREATE_ADMIN, cmdReplacement);
                } else if (receiveCreateMessage) {
                    return messageRegistry.getMessage(Message.HELP_COMMAND_CREATE, cmdReplacement);
                }

                return "";
            }
        });

        addSubCommand(new ShopSubCommand("remove", true, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                return messageRegistry.getMessage(Message.HELP_COMMAND_REMOVE, cmdReplacement);
            }
        });

        addSubCommand(new ShopSubCommand("info", false, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                return messageRegistry.getMessage(Message.HELP_COMMAND_INFO, cmdReplacement);
            }
        });

        addSubCommand(new ShopSubCommand("list", true, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                return messageRegistry.getMessage(Message.HELP_COMMAND_LIST, cmdReplacement);
            }
        });

        addSubCommand(new ShopSubCommand("help", false, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                return messageRegistry.getMessage(Message.HELP_COMMAND_HELP, cmdReplacement);
            }
        });

        addSubCommand(new ShopSubCommand("inspect", true, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                return messageRegistry.getMessage(Message.HELP_COMMAND_INSPECT, cmdReplacement);
            }
        });

        addSubCommand(new ShopSubCommand("limits", true, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                return messageRegistry.getMessage(Message.HELP_COMMAND_LIMITS, cmdReplacement);
            }
        });

        addSubCommand(new ShopSubCommand("open", true, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                return messageRegistry.getMessage(Message.HELP_COMMAND_OPEN, cmdReplacement);
            }
        });

        addSubCommand(new ShopSubCommand("admin", false, ShopSubCommand.HelpSection.STAFF, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                if (sender.hasPermission(Permissions.ADMIN_LIST)) {
                    final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                    return messageRegistry.getMessage(Message.HELP_COMMAND_ADMIN, cmdReplacement);
                }
                return "";
            }
        });

        addSubCommand(new ShopSubCommand("removeall", false, ShopSubCommand.HelpSection.STAFF, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                if (sender.hasPermission(Permissions.REMOVE_OTHER)) {
                    final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                    return messageRegistry.getMessage(Message.HELP_COMMAND_REMOVEALL, cmdReplacement);
                } else {
                    return "";
                }
            }
        });

        addSubCommand(new ShopSubCommand("reload", false, ShopSubCommand.HelpSection.STAFF, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                if (sender.hasPermission(Permissions.RELOAD)) {
                    final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                    return messageRegistry.getMessage(Message.HELP_COMMAND_RELOAD, cmdReplacement);
                } else {
                    return "";
                }
            }
        });

        addSubCommand(new ShopSubCommand("config", false, ShopSubCommand.HelpSection.STAFF, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                if (sender.hasPermission(Permissions.CONFIG)) {
                    final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                    return messageRegistry.getMessage(Message.HELP_COMMAND_CONFIG, cmdReplacement);
                } else {
                    return "";
                }
            }
        });

        register();
        commandCreated = true;
    }

    public PluginCommand getCommand() {
        return pluginCommand;
    }

    /**
     * Call the second part of the create method after the player
     * has selected an item from the creative inventory.
     */
    public void createShopAfterSelected(Player player, SelectClickType clickType) {
        executor.create2(player, clickType);
    }

    private PluginCommand createPluginCommand() {
        plugin.debug("Creating plugin command");
        try {
            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            c.setAccessible(true);

            PluginCommand cmd = c.newInstance(name, plugin);
            cmd.setDescription("Manage players' shops or this plugin.");
            cmd.setUsage("/" + name);
            cmd.setExecutor(new ShopBaseCommandExecutor());
            cmd.setTabCompleter(new ShopBaseTabCompleter());

            return cmd;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            plugin.getLogger().severe("Failed to create command");
            plugin.debug("Failed to create plugin command");
            plugin.debug(e);
        }

        return null;
    }

    public void addSubCommand(ShopSubCommand subCommand) {
        plugin.debug("Adding sub command \"" + subCommand.getName() + "\"");
        this.subCommands.add(subCommand);
    }

    public List<ShopSubCommand> getSubCommands() {
        return new ArrayList<>(subCommands);
    }

    private void register() {
        if (pluginCommand == null) return;

        plugin.debug("Registering command " + name);

        try {
            Field fCommandMap = Bukkit.getPluginManager().getClass().getDeclaredField("commandMap");
            fCommandMap.setAccessible(true);

            Object commandMapObject = fCommandMap.get(Bukkit.getPluginManager());
            if (commandMapObject instanceof CommandMap) {
                CommandMap commandMap = (CommandMap) commandMapObject;
                commandMap.register(fallbackPrefix, pluginCommand);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin.getLogger().severe("Failed to register command");
            plugin.debug("Failed to register plugin command");
            plugin.debug(e);
        }
    }

    public void unregister() {
        if (pluginCommand == null) return;

        plugin.debug("Unregistering command " + name);

        try {
            Field fCommandMap = Bukkit.getPluginManager().getClass().getDeclaredField("commandMap");
            fCommandMap.setAccessible(true);

            Object commandMapObject = fCommandMap.get(Bukkit.getPluginManager());
            if (commandMapObject instanceof CommandMap) {
                CommandMap commandMap = (CommandMap) commandMapObject;
                pluginCommand.unregister(commandMap);

                Field fKnownCommands = SimpleCommandMap.class.getDeclaredField("knownCommands");
                fKnownCommands.setAccessible(true);

                Object knownCommandsObject = fKnownCommands.get(commandMap);
                if (knownCommandsObject instanceof Map) {
                    Map<?, ?> knownCommands = (Map<?, ?>) knownCommandsObject;
                    knownCommands.remove(fallbackPrefix + ":" + name);
                    if (pluginCommand.equals(knownCommands.get(name))) {
                        knownCommands.remove(name);
                    }
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin.getLogger().severe("Failed to unregister command");
            plugin.debug("Failed to unregister plugin command");
            plugin.debug(e);
        }
    }

    /**
     * Sends the basic help message
     *
     * @param sender {@link CommandSender} who will receive the message
     */
    private void sendBasicHelpMessage(CommandSender sender) {
        plugin.debug("Sending basic help message to " + sender.getName());

        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

        sender.sendMessage(" ");
        String header = messageRegistry.getMessage(Message.HELP_HEADER,
                new Replacement(Placeholder.COMMAND, Config.mainCommandName));

        if (!header.trim().isEmpty()) sender.sendMessage(header);

        sendHelpSection(sender, ShopSubCommand.HelpSection.PLAYER, Message.HELP_PLAYER_HEADER);
        sendHelpSection(sender, ShopSubCommand.HelpSection.STAFF, Message.HELP_STAFF_HEADER);

        String footer = messageRegistry.getMessage(Message.HELP_FOOTER,
                new Replacement(Placeholder.COMMAND, Config.mainCommandName));

        if (!footer.trim().isEmpty()) sender.sendMessage(footer);
        sender.sendMessage(" ");
    }

    private void sendHelpSection(
            CommandSender sender,
            ShopSubCommand.HelpSection section,
            Message headerMessage
    ) {
        final List<String> entries = new ArrayList<>();
        for (ShopSubCommand subCommand : subCommands) {
            if (subCommand.getHelpSection() == section && subCommand.isVisibleTo(sender)) {
                entries.add(subCommand.getHelpMessage(sender));
            }
        }
        if (entries.isEmpty()) {
            return;
        }

        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        sender.sendMessage(messageRegistry.getMessage(headerMessage));
        entries.forEach(sender::sendMessage);
    }

    private class ShopBaseCommandExecutor implements CommandExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length > 0) {
                for (ShopSubCommand subCommand : subCommands) {
                    if (subCommand.getName().equalsIgnoreCase(args[0])) {
                        if (!(sender instanceof Player) && subCommand.isPlayerCommand()) {
                            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                            return true;
                        }

                        if (!subCommand.execute(sender, command, label, args)) {
                            sendBasicHelpMessage(sender);
                        }

                        return true;
                    }
                }

                sendBasicHelpMessage(sender);
            } else {
                sendBasicHelpMessage(sender);
            }

            return true;
        }
    }

    private class ShopBaseTabCompleter implements TabCompleter {

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
            List<String> subCommandNames = new ArrayList<>();

            List<String> tabCompletions = new ArrayList<>();

            for (ShopSubCommand subCommand : subCommands) {
                if (subCommand.isVisibleTo(sender)) {
                    subCommandNames.add(subCommand.getName());
                }
            }

            if (args.length == 1) {
                if (!args[0].isEmpty()) {
                    for (String s : subCommandNames) {
                        if (s.startsWith(args[0])) {
                            tabCompletions.add(s);
                        }
                    }
                    return tabCompletions;
                } else {
                    return subCommandNames;
                }
            } else if (args.length > 1) {
                for (ShopSubCommand subCmd : subCommands) {
                    if (subCmd.getName().equalsIgnoreCase(args[0])) {
                        return subCmd.isVisibleTo(sender)
                                ? subCmd.getTabCompletions(sender, command, label, args)
                                : new ArrayList<>();
                    }
                }
            }

            return new ArrayList<>();
        }

    }

}
