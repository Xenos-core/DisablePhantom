package net.pikzstudio.donututils.disablephantom;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class DisablePhantomPlugin extends JavaPlugin {

    private static DisablePhantomPlugin instance;
    private DatabaseManager databaseManager;

    public static DisablePhantomPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.databaseManager = new DatabaseManager(this);

        if (getCommand("phantom") != null) {
            getCommand("phantom").setExecutor(new CommandHandler(this, databaseManager));
        } else {
            getLogger().warning("Command 'phantom' not defined in plugin.yml");
        }

        if (getCommand("donutdisablephantom") != null) {
            // Handle admin command in this class
            getCommand("donutdisablephantom").setExecutor(this);
        }

        getServer().getPluginManager().registerEvents(new EventListener(this, databaseManager), this);

        getLogger().info("DonutDisablePhantom enabled");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("donutdisablephantom".equalsIgnoreCase(command.getName())) {
            if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
                reloadConfig();
                sender.sendMessage("Config reloaded.");
                return true;
            }
            sender.sendMessage("Usage: /donutdisablephantom reload");
            return true;
        }
        return false;
    }

    public static void log(String str) {
        Bukkit.getConsoleSender().sendMessage(str);
    }
}