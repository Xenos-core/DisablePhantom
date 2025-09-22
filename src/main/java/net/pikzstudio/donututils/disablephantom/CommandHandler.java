package net.pikzstudio.donututils.disablephantom;

import java.util.List;
import java.util.UUID;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class CommandHandler implements CommandExecutor {

    private final DisablePhantomPlugin plugin;
    private final DatabaseManager databaseManager;

    public CommandHandler(DisablePhantomPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        FileConfiguration config = plugin.getConfig();

        List<String> blacklistedWorlds = config.getStringList("blacklist-world");
        if (blacklistedWorlds != null && blacklistedWorlds.contains(player.getWorld().getName())) {
            boolean chat = config.getBoolean("messages.chat", true);
            String notAllowedMessage = colorize(config.getString("messages.not_allowed_world", "&cYou cannot use this command in this world."));
            if (chat) {
                player.sendMessage(notAllowedMessage);
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(notAllowedMessage));
            return true;
        }

        boolean nowDisabled = databaseManager.togglePhantom(uuid);

        boolean chat = config.getBoolean("messages.chat", true);
        String enableMessage = colorize(config.getString("messages.enable", "&aPhantoms disabled for you."));
        String disableMessage = colorize(config.getString("messages.disable", "&cPhantoms enabled for you."));
        Sound enableSound = soundOrDefault(config.getString("messages.enable-sound", "UI_TOAST_CHALLENGE_COMPLETE"), Sound.UI_TOAST_CHALLENGE_COMPLETE);
        Sound disableSound = soundOrDefault(config.getString("messages.disable-sound", "BLOCK_NOTE_BLOCK_PLING"), Sound.BLOCK_NOTE_BLOCK_PLING);

        if (nowDisabled) {
            if (chat) player.sendMessage(enableMessage);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(enableMessage));
            player.playSound(player.getLocation(), enableSound, 1.0f, 1.0f);
        } else {
            if (chat) player.sendMessage(disableMessage);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(disableMessage));
            player.playSound(player.getLocation(), disableSound, 1.0f, 1.0f);
        }

        return true;
    }

    private static String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private static Sound soundOrDefault(String name, Sound def) {
        try {
            return Sound.valueOf(name);
        } catch (Exception ignored) {
            return def;
        }
    }
}