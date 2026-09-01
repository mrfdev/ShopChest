package de.epiceric.shopchest.command;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.Placeholder;
import de.epiceric.shopchest.language.Message;
import de.epiceric.shopchest.language.MessageRegistry;
import de.epiceric.shopchest.language.Replacement;
import de.epiceric.shopchest.utils.ClickType.SelectClickType;
import de.epiceric.shopchest.utils.ClickType.EditClickType;
import de.epiceric.shopchest.utils.Permissions;
import de.epiceric.shopchest.shop.Shop;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class ShopCommand {

    private static boolean commandCreated = false;

    private final ShopChest plugin;
    private final String name;
    private final Command commandContext;
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
        this.executor = new ShopCommandExecutor(plugin);
        this.commandContext = new CommandContext(name);

        ShopTabCompleter tabCompleter = new ShopTabCompleter(plugin);

        final Replacement cmdReplacement = new Replacement(Placeholder.COMMAND, name);

        addSubCommand(new ShopSubCommand("create", true, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

                if (sender.hasPermission(Permissions.CREATE_ADMIN)) {
                    return messageRegistry.getMessage(Message.HELP_COMMAND_CREATE_ADMIN, cmdReplacement);
                } else if (hasCreationPermission(sender)) {
                    return messageRegistry.getMessage(Message.HELP_COMMAND_CREATE, cmdReplacement);
                }

                return "";
            }
        });

        addSubCommand(new ShopSubCommand("edit", true, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                if (!hasCreationPermission(sender)) {
                    return "";
                }
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                return messageRegistry.getMessage(Message.HELP_COMMAND_EDIT, cmdReplacement);
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

        addSubCommand(new ShopSubCommand("profile", false, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                return sender.hasPermission(Permissions.PROFILE)
                        ? "§6/" + name + " profile [player] §7- View or edit a storefront profile"
                        : "";
            }
        });

        addSubCommand(new ShopSubCommand("search", false, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                return sender.hasPermission(Permissions.SEARCH)
                        ? "§6/" + name + " search <item> [page] §7- Find in-stock player shops"
                        : "";
            }
        });

        addSubCommand(new ShopSubCommand("advertise", true, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                return sender.hasPermission(Permissions.ADVERTISE)
                        ? "§6/" + name + " advertise §7- Preview, purchase, or queue a storefront ad"
                        : "";
            }
        });

        addSubCommand(new ShopSubCommand("recent", true, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                if (!sender.hasPermission(Permissions.RECENT)) {
                    return "";
                }
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                return messageRegistry.getMessage(Message.HELP_COMMAND_RECENT, cmdReplacement);
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
                if (sender.hasPermission(Permissions.ADMIN_LIST)
                        || sender.hasPermission(Permissions.ADMIN_AUDIT)
                        || sender.hasPermission(Permissions.ADMIN_DEBUG)
                        || sender.hasPermission(Permissions.ADMIN_STOREFRONT)
                        || sender.hasPermission(Permissions.ADMIN_ADVERTISE)
                        || sender.hasPermission(Permissions.ADMIN_EXPORT)) {
                    final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                    return messageRegistry.getMessage(Message.HELP_COMMAND_ADMIN, cmdReplacement);
                }
                return "";
            }
        });

        addSubCommand(new ShopSubCommand("debug", false, ShopSubCommand.HelpSection.STAFF, executor, tabCompleter) {
            @Override
            public String getHelpMessage(CommandSender sender) {
                if (!sender.hasPermission(Permissions.ADMIN_DEBUG)) {
                    return "";
                }
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
                return messageRegistry.getMessage(Message.HELP_COMMAND_DEBUG, cmdReplacement);
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

    /**
     * Call the second part of the create method after the player
     * has selected an item from the creative inventory.
     */
    public void createShopAfterSelected(Player player, SelectClickType clickType) {
        executor.create2(player, clickType);
    }

    /**
     * Apply an edit after the player has selected one of their shops.
     */
    public void editShopAfterSelected(Player player, Shop shop, EditClickType clickType) {
        executor.edit2(player, shop, clickType);
    }

    /**
     * Show the authoritative details for a selected or targeted shop.
     */
    public void inspectShop(Player player, Shop shop) {
        executor.inspect(player, shop);
    }

    public void cacheAdminTeleportTargets(Player player, java.util.Map<Integer, Location> targets) {
        executor.cacheAdminTeleportTargets(player, targets);
    }

    public void invalidateEphemeralState() {
        executor.invalidateEphemeralState();
    }

    private boolean hasCreationPermission(CommandSender sender) {
        if (sender.hasPermission(Permissions.CREATE)) {
            return true;
        }
        for (PermissionAttachmentInfo permInfo : sender.getEffectivePermissions()) {
            final String permission = permInfo.getPermission();
            if (permission.startsWith(Permissions.CREATE) && sender.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    public void addSubCommand(ShopSubCommand subCommand) {
        plugin.debug("Adding sub command \"" + subCommand.getName() + "\"");
        this.subCommands.add(subCommand);
    }

    public List<ShopSubCommand> getSubCommands() {
        return new ArrayList<>(subCommands);
    }

    private void register() {
        plugin.debug("Registering command " + name);
        plugin.registerCommand(
                name,
                "Manage players' shops or this plugin.",
                new PaperShopCommand());
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

            for (ShopSubCommand subCommand : subCommands) {
                if (subCommand.isVisibleTo(sender)) {
                    subCommandNames.add(subCommand.getName());
                }
            }

            if (args.length <= 1) {
                return rootTabCompletions(subCommandNames, args);
            } else {
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

    static List<String> rootTabCompletions(List<String> subCommandNames, String[] args) {
        if (args.length > 1) {
            return List.of();
        }
        if (args.length == 0 || args[0].isEmpty()) {
            return new ArrayList<>(subCommandNames);
        }

        final List<String> matches = new ArrayList<>();
        final String prefix = args[0].toLowerCase(Locale.ROOT);
        for (String subCommandName : subCommandNames) {
            if (subCommandName.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(subCommandName);
            }
        }
        return matches;
    }

    private final class PaperShopCommand implements BasicCommand {

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            commandContext.execute(source.getSender(), name, args);
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            return commandContext.tabComplete(source.getSender(), name, args);
        }
    }

    private final class CommandContext extends Command {

        private final ShopBaseCommandExecutor commandExecutor = new ShopBaseCommandExecutor();
        private final ShopBaseTabCompleter tabCompleter = new ShopBaseTabCompleter();

        private CommandContext(String commandName) {
            super(commandName);
            setDescription("Manage players' shops or this plugin.");
            setUsage("/" + commandName);
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return commandExecutor.onCommand(sender, this, commandLabel, args);
        }

        @Override
        public List<String> tabComplete(
                CommandSender sender,
                String alias,
                String[] args
        ) {
            return tabCompleter.onTabComplete(sender, this, alias, args);
        }
    }

}
