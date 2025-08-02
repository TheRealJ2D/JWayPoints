package me.jWaypoints;

import me.jWaypoints.commands.WaypointCommand;
import me.jWaypoints.utils.Metrics;
import me.jWaypoints.utils.UpdateChecker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class JWaypoints extends JavaPlugin implements Listener {
    private WaypointCommand waypointCommand;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDefaultUpdateConfig();

        Metrics metrics = new Metrics(this, 26413);
        waypointCommand = new WaypointCommand(this);
        updateChecker = new UpdateChecker(this);

        getCommand("waypoint").setExecutor(waypointCommand);
        getCommand("waypoint").setTabCompleter(waypointCommand);
        getCommand("wp").setExecutor(waypointCommand);
        getCommand("wp").setTabCompleter(waypointCommand);

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("JWaypoints has been enabled!");
    }

    @Override
    public void onDisable() {
        if (waypointCommand != null) {
            waypointCommand.cleanup();
        }
        getLogger().info("JWaypoints has been disabled!");
    }

    private void setupDefaultUpdateConfig() {
        if (!getConfig().isSet("update_checker.enabled")) {
            getConfig().set("update_checker.enabled", true);
        }
        if (!getConfig().isSet("update_checker.github_user")) {
            getConfig().set("update_checker.github_user", "YourGitHubUsername");
        }
        if (!getConfig().isSet("update_checker.github_repo")) {
            getConfig().set("update_checker.github_repo", "JWaypoints");
        }
        if (!getConfig().isSet("update_checker.notify_ops_only")) {
            getConfig().set("update_checker.notify_ops_only", true);
        }
        if (!getConfig().isSet("update_checker.check_interval_hours")) {
            getConfig().set("update_checker.check_interval_hours", 6);
        }
        saveConfig();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (updateChecker != null) {
            updateChecker.notifyPlayerOnJoin(event.getPlayer());
        }
    }

    public WaypointCommand getWaypointCommand() {
        return waypointCommand;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}