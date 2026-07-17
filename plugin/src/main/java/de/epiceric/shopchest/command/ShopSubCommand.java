package de.epiceric.shopchest.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public abstract class ShopSubCommand {
    private final String name;
    private final boolean playerCommand;
    private final HelpSection helpSection;
    private final CommandExecutor executor;
    private final TabCompleter tabCompleter;

    public ShopSubCommand(String name, boolean playerCommand, CommandExecutor executor, TabCompleter tabCompleter) {
        this(name, playerCommand, HelpSection.PLAYER, executor, tabCompleter);
    }

    public ShopSubCommand(
            String name,
            boolean playerCommand,
            HelpSection helpSection,
            CommandExecutor executor,
            TabCompleter tabCompleter
    ) {
        this.name = name;
        this.playerCommand = playerCommand;
        this.helpSection = helpSection;
        this.executor = executor;
        this.tabCompleter = tabCompleter;
    }

    public String getName() {
        return name;
    }

    /**
     * @return Whether the command can only be used by players, not by the console
     */
    public boolean isPlayerCommand() {
        return playerCommand;
    }

    public HelpSection getHelpSection() {
        return helpSection;
    }

    public boolean isVisibleTo(CommandSender sender) {
        if (playerCommand && !(sender instanceof Player)) {
            return false;
        }
        final String helpMessage = getHelpMessage(sender);
        return helpMessage != null && !helpMessage.isBlank();
    }

    /**
     * Execute the sub command
     * @param sender Sender of the command
     * @param command Command which was executed
     * @param label Alias of the command which was used
     * @param args Arguments of the command ({@code args[0]} is the sub command's name)
     * @return Whether the sender should be sent the help message
     */
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        return executor.onCommand(sender, command, label, args);
    }

    /**
     * @param sender Sender of the command
     * @param command Command which was executed
     * @param label Alias of the command which was used
     * @param args Arguments of the command ({@code args[0]} is the sub command's name)
     * @return A list of tab completions for the sub command (may be an empty list)
     */
    public List<String> getTabCompletions(CommandSender sender, Command command, String label, String[] args) {
        if (tabCompleter == null) {
            return new ArrayList<>();
        }

        return tabCompleter.onTabComplete(sender, command, label, args);
    }

    /**
     * @param sender Sender to receive the help message
     * @return The help message for the command.
     */
    public abstract String getHelpMessage(CommandSender sender);

    public enum HelpSection {
        PLAYER,
        STAFF
    }
}
