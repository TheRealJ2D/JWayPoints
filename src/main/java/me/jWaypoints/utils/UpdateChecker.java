package me.jWaypoints.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.jWaypoints.JWaypoints;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public class UpdateChecker {
    private final JWaypoints plugin;
    private final String githubUser = "TheRealJ2D";
    private final String githubRepo = "JWayPoints";
    private final String currentVersion;
    private final boolean enabled;
    private final boolean notifyOpsOnly;
    private final int checkInterval;

    private String latestVersion = null;
    private String downloadUrl = null;
    private String releaseUrl = null;
    private boolean updateAvailable = false;
    private long lastCheckTime = 0;

    public UpdateChecker(JWaypoints plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();

        this.enabled = plugin.getConfig().getBoolean("update_checker.enabled", true);
        this.notifyOpsOnly = plugin.getConfig().getBoolean("update_checker.notify_ops_only", true);
        this.checkInterval = plugin.getConfig().getInt("update_checker.check_interval_hours", 6);

        if (enabled) {
            plugin.getLogger().info("Update checker enabled. Current version: " + currentVersion);

            checkForUpdateAsync(null);

            schedulePeriodicChecks();
        } else {
            plugin.getLogger().info("Update checker is disabled in config.");
        }
    }

    private void schedulePeriodicChecks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastCheckTime >= (checkInterval * 60 * 60 * 1000)) {
                    checkForUpdateAsync(null);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 20L * 60 * 60);
    }

    public void checkForUpdateAsync(Consumer<Boolean> callback) {
        if (!enabled) {
            if (callback != null) callback.accept(false);
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                return checkForUpdate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to check for updates: " + e.getMessage());
                return false;
            }
        }).thenAccept(hasUpdate -> {
            lastCheckTime = System.currentTimeMillis();

            if (hasUpdate) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        notifyAdmins();
                    }
                }.runTask(plugin);
            }

            if (callback != null) {
                callback.accept(hasUpdate);
            }
        });
    }

    private boolean checkForUpdate() throws IOException {
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/latest", githubUser, githubRepo);

        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("User-Agent", "JWaypoints-UpdateChecker");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("GitHub API returned response code: " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();

        String tagName = jsonResponse.get("tag_name").getAsString();
        String htmlUrl = jsonResponse.get("html_url").getAsString();

        String jarDownloadUrl = null;
        if (jsonResponse.has("assets") && jsonResponse.getAsJsonArray("assets").size() > 0) {
            JsonObject firstAsset = jsonResponse.getAsJsonArray("assets").get(0).getAsJsonObject();
            jarDownloadUrl = firstAsset.get("browser_download_url").getAsString();
        }

        String cleanCurrentVersion = currentVersion.replaceFirst("^v", "");
        String cleanLatestVersion = tagName.replaceFirst("^v", "");

        this.latestVersion = cleanLatestVersion;
        this.downloadUrl = jarDownloadUrl;
        this.releaseUrl = htmlUrl;

        this.updateAvailable = isNewerVersion(cleanCurrentVersion, cleanLatestVersion);

        if (updateAvailable) {
            plugin.getLogger().info("New version available: " + latestVersion + " (Current: " + currentVersion + ")");
        } else {
            plugin.getLogger().info("Plugin is up to date. Latest version: " + latestVersion);
        }

        return updateAvailable;
    }


    private boolean isNewerVersion(String current, String latest) {
        try {
            String[] currentParts = current.split("\\.");
            String[] latestParts = latest.split("\\.");

            int maxLength = Math.max(currentParts.length, latestParts.length);

            for (int i = 0; i < maxLength; i++) {
                int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;

                if (latestPart > currentPart) {
                    return true;
                } else if (latestPart < currentPart) {
                    return false;
                }
            }

            return false;
        } catch (NumberFormatException e) {
            return !current.equals(latest);
        }
    }


    private void notifyAdmins() {
        if (!updateAvailable) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldNotifyPlayer(player)) {
                sendUpdateNotification(player);
            }
        }

        plugin.getLogger().info("=".repeat(50));
        plugin.getLogger().info("UPDATE AVAILABLE!");
        plugin.getLogger().info("Current version: " + currentVersion);
        plugin.getLogger().info("Latest version: " + latestVersion);
        plugin.getLogger().info("Download: " + releaseUrl);
        plugin.getLogger().info("=".repeat(50));
    }


    private boolean shouldNotifyPlayer(Player player) {
        if (notifyOpsOnly) {
            return player.isOp() || player.hasPermission("jwaypoints.admin") || player.hasPermission("jwaypoints.update.notify");
        } else {
            return player.hasPermission("jwaypoints.update.notify") || player.isOp();
        }
    }


    private void sendUpdateNotification(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "=".repeat(50));
        player.sendMessage(ChatColor.GREEN + "🔄 JWaypoints Update Available!");
        player.sendMessage(ChatColor.GRAY + "Current version: " + ChatColor.WHITE + currentVersion);
        player.sendMessage(ChatColor.GRAY + "Latest version: " + ChatColor.YELLOW + latestVersion);
        player.sendMessage("");

        TextComponent releaseLink = new TextComponent(ChatColor.AQUA + "📋 View Release Notes");
        releaseLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, releaseUrl));
        releaseLink.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponent[]{new TextComponent(ChatColor.YELLOW + "Click to open GitHub release page")}));

        player.spigot().sendMessage(releaseLink);

        if (downloadUrl != null) {
            TextComponent downloadLink = new TextComponent(ChatColor.GREEN + "⬇️ Download Update");
            downloadLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, downloadUrl));
            downloadLink.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponent[]{new TextComponent(ChatColor.YELLOW + "Click to download the latest version")}));

            player.spigot().sendMessage(downloadLink);
        }

        player.sendMessage(ChatColor.YELLOW + "=".repeat(50));
        player.sendMessage("");
    }

    public void performManualCheck(Player player) {
        if (!enabled) {
            player.sendMessage(ChatColor.RED + "Update checker is disabled in the configuration.");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Checking for updates...");

        checkForUpdateAsync(hasUpdate -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (hasUpdate) {
                        sendUpdateNotification(player);
                    } else {
                        player.sendMessage(ChatColor.GREEN + "✅ JWaypoints is up to date! (Version " + currentVersion + ")");
                    }
                }
            }.runTask(plugin);
        });
    }

    public void notifyPlayerOnJoin(Player player) {
        if (!enabled || !updateAvailable) return;

        if (shouldNotifyPlayer(player)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    sendUpdateNotification(player);
                }
            }.runTaskLater(plugin, 40L);
        }
    }

    public boolean isEnabled() { return enabled; }
    public boolean isUpdateAvailable() { return updateAvailable; }
    public String getLatestVersion() { return latestVersion; }
    public String getCurrentVersion() { return currentVersion; }
    public String getReleaseUrl() { return releaseUrl; }
    public String getDownloadUrl() { return downloadUrl; }
}