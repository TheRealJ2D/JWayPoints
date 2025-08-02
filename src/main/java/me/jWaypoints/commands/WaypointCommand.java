package me.jWaypoints.commands;

import me.jWaypoints.JWaypoints;
import me.jWaypoints.models.ArrowDesign;
import me.jWaypoints.utils.UpdateChecker;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StringUtil;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WaypointCommand implements CommandExecutor, TabCompleter, Listener {
    private final JWaypoints plugin;

    private final Map<UUID, Location> activeWaypoints = new ConcurrentHashMap<>();
    private final Map<UUID, List<ArmorStand>> playerArrowParts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> taskIds = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Location>> playerWaypoints = new ConcurrentHashMap<>();

    private final Map<String, ArrowDesign> arrowDesigns = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerArrowDesigns = new ConcurrentHashMap<>();
    private final Map<String, String> arrivalSounds = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerArrivalSounds = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerUsingActionBar = new ConcurrentHashMap<>();

    private final Map<UUID, Boolean> playerDistanceEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerOriginalExp = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerOriginalLevel = new ConcurrentHashMap<>();
    private final Map<UUID, Float> playerOriginalProgress = new ConcurrentHashMap<>();

    private final Map<UUID, Boolean> playerGuidanceMessagesEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> playerEditingSessions = new ConcurrentHashMap<>();

    private final Map<UUID, PendingDeletion> pendingDeletions = new ConcurrentHashMap<>();

    private final Map<UUID, String> playerArrivalParticles = new ConcurrentHashMap<>();
    private final Map<String, String> arrivalParticles = new HashMap<>();

    private final Map<UUID, Boolean> playerSilentMode = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerSoundDisabled = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerParticlesDisabled = new ConcurrentHashMap<>();


    private File waypointsFile;
    private FileConfiguration waypointsConfig;
    private File userPrefsFile;
    private FileConfiguration userPrefsConfig;

    private double arrowHeight;
    private double arrowSpacing;
    private double forwardOffset;
    private Material defaultArrowMaterial;
    private Material defaultArrowHeadMaterial;
    private Material defaultTailMaterial;
    private boolean allowSharing;
    private int waypointLimit;
    private String guiTitle;
    private String defaultArrowDesign;
    private String defaultArrivalSound;
    private boolean distanceDisplayDefault;

    private boolean silentModeDefault;
    private boolean soundDisabledDefault;
    private boolean particlesDisabledDefault;
    private boolean guidanceMessagesDefault;
    private String defaultArrivalParticle;

    private final Map<UUID, Integer> playerGUIPage = new ConcurrentHashMap<>();
    private int maxWaypointsPerPage;
    private int absoluteMaxWaypoints;

    private final Map<String, Material> soundIcons = new HashMap<>();

    private static class PendingDeletion {
        final String waypointName;
        final long expiryTime;
        final DeletionSource source;

        PendingDeletion(String waypointName, DeletionSource source) {
            this.waypointName = waypointName;
            this.source = source;
            this.expiryTime = System.currentTimeMillis() + 30000;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private enum DeletionSource {
        MAIN_GUI, EDIT_GUI, COMMAND
    }

    public WaypointCommand(JWaypoints plugin) {
        this.plugin = plugin;
        setupSoundIcons();
        setupArrivalParticles();
        setupConfig();
        loadUserPreferences();
        loadWaypoints();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void setupSoundIcons() {
        soundIcons.put("levelup", Material.EXPERIENCE_BOTTLE);
        soundIcons.put("ender_dragon", Material.DRAGON_HEAD);
        soundIcons.put("bell", Material.BELL);
        soundIcons.put("portal", Material.ENDER_PEARL);
        soundIcons.put("firework", Material.FIREWORK_ROCKET);
        soundIcons.put("anvil", Material.ANVIL);
        soundIcons.put("note_pling", Material.NOTE_BLOCK);
        soundIcons.put("experience", Material.EXPERIENCE_BOTTLE);
        soundIcons.put("chicken", Material.EGG);
        soundIcons.put("cat", Material.STRING);
    }

    private void setupConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        setDefaultConfigValues(config);
        plugin.saveConfig();

        loadConfigValues(config);
        loadArrowDesigns(config);
        loadArrivalSounds(config);

        setupWaypointsFile();
    }

    private void setDefaultConfigValues(FileConfiguration config) {
        if (!config.isSet("arrow.height")) config.set("arrow.height", 3.0);
        if (!config.isSet("arrow.spacing")) config.set("arrow.spacing", 0.4);
        if (!config.isSet("arrow.forward_offset")) config.set("arrow.forward_offset", 2.0);
        if (!config.isSet("arrow.default_material")) config.set("arrow.default_material", "LIME_CONCRETE");
        if (!config.isSet("arrow.head_material")) config.set("arrow.head_material", "YELLOW_CONCRETE");
        if (!config.isSet("arrow.tail_material")) config.set("arrow.tail_material", "RED_CONCRETE");

        if (!config.isSet("settings.allow_sharing")) config.set("settings.allow_sharing", true);
        if (!config.isSet("settings.waypoint_limit")) config.set("settings.waypoint_limit", 10);
        if (!config.isSet("settings.distance_display_default")) config.set("settings.distance_display_default", true);

        if (!config.isSet("settings.max_waypoints_per_page")) config.set("settings.max_waypoints_per_page", 45);
        if (!config.isSet("settings.absolute_max_waypoints")) config.set("settings.absolute_max_waypoints", 100);

        if (!config.isSet("settings.silent_mode_default")) config.set("settings.silent_mode_default", false);
        if (!config.isSet("settings.sound_disabled_default")) config.set("settings.sound_disabled_default", false);
        if (!config.isSet("settings.particles_disabled_default")) config.set("settings.particles_disabled_default", false);
        if (!config.isSet("settings.guidance_messages_default")) config.set("settings.guidance_messages_default", true);

        if (!config.isSet("gui.title")) config.set("gui.title", "&6Waypoint Manager");
        if (!config.isSet("designs.default")) config.set("designs.default", "standard");
        if (!config.isSet("sounds.default")) config.set("sounds.default", "levelup");
        if (!config.isSet("particles.default")) config.set("particles.default", "FIREWORK");

        setupDefaultDesigns(config);
        setupDefaultSounds(config);
        setupDefaultParticles(config);
    }

    private void setupDefaultParticles(FileConfiguration config) {
        if (!config.isSet("particles.available.FIREWORK")) {
            Map<String, String> particles = new HashMap<>();
            particles.put("FIREWORK", "Explosive celebration");
            particles.put("FLAME", "Fiery arrival");
            particles.put("SOUL", "Mystical soul flames");
            particles.put("HAPPY_VILLAGER", "Cheerful green sparkles");
            particles.put("HEART", "Lovely pink hearts");
            particles.put("NOTE", "Musical notes");
            particles.put("ENCHANT", "Magical enchantment");
            particles.put("EXPLOSION", "Powerful blast");
            particles.put("TOTEM_OF_UNDYING", "Golden celebration");
            particles.put("END_ROD", "Mystical end energy");
            particles.put("DRAGON_BREATH", "Dragon's mystical breath");
            particles.put("BUBBLE_POP", "Playful bubble bursts");
            particles.put("CAMPFIRE_COSY_SMOKE", "Cozy campfire smoke");
            particles.put("ELECTRIC_SPARK", "Electric energy");
            particles.put("GLOW", "Soft glowing light");
            particles.put("SCRAPE", "Metallic sparks");
            particles.put("SCULK_SOUL", "Deep sculk energy");
            particles.put("SONIC_BOOM", "Warden's sonic blast");
            particles.put("CHERRY_LEAVES", "Gentle cherry petals");

            for (Map.Entry<String, String> particle : particles.entrySet()) {
                config.set("particles.available." + particle.getKey(), particle.getValue());
            }
        }
    }

    private void setupDefaultDesigns(FileConfiguration config) {
        String[][] designs = {
                {"standard", "LIME_CONCRETE", "YELLOW_CONCRETE", "RED_CONCRETE", "false", "none", "0"},
                {"diamond", "DIAMOND_BLOCK", "LIGHT_BLUE_CONCRETE", "BLUE_CONCRETE", "true", "SPELL_WITCH", "0"},
                {"gold", "GOLD_BLOCK", "YELLOW_CONCRETE", "ORANGE_CONCRETE", "true", "FLAME", "1"},
                {"emerald", "EMERALD_BLOCK", "LIME_CONCRETE", "GREEN_CONCRETE", "true", "VILLAGER_HAPPY", "2"},
                {"redstone", "REDSTONE_BLOCK", "RED_CONCRETE", "BLACK_CONCRETE", "true", "REDSTONE", "1"}
        };

        for (String[] design : designs) {
            String path = "designs." + design[0];
            if (!config.isSet(path + ".main_material")) {
                config.set(path + ".main_material", design[1]);
                config.set(path + ".head_material", design[2]);
                config.set(path + ".tail_material", design[3]);
                config.set(path + ".glowing", Boolean.parseBoolean(design[4]));
                config.set(path + ".particle_effect", design[5]);
                config.set(path + ".pattern_type", Integer.parseInt(design[6]));
            }
        }
    }

    private void setupDefaultSounds(FileConfiguration config) {
        if (!config.isSet("sounds.available.levelup")) {
            Map<String, String> sounds = new HashMap<>();
            sounds.put("levelup", "entity.player.levelup");
            sounds.put("ender_dragon", "entity.ender_dragon.growl");
            sounds.put("bell", "block.bell.use");
            sounds.put("portal", "block.portal.travel");
            sounds.put("firework", "entity.firework_rocket.large_blast");
            sounds.put("anvil", "block.anvil.land");
            sounds.put("note_pling", "block.note_block.pling");
            sounds.put("experience", "entity.experience_orb.pickup");
            sounds.put("chicken", "entity.chicken.egg");
            sounds.put("cat", "entity.cat.purreow");

            for (Map.Entry<String, String> sound : sounds.entrySet()) {
                config.set("sounds.available." + sound.getKey(), sound.getValue());
            }
        }
    }



    private void loadConfigValues(FileConfiguration config) {
        arrowHeight = config.getDouble("arrow.height");
        arrowSpacing = config.getDouble("arrow.spacing");
        forwardOffset = config.getDouble("arrow.forward_offset");
        defaultArrowMaterial = Material.getMaterial(config.getString("arrow.default_material"));
        defaultArrowHeadMaterial = Material.getMaterial(config.getString("arrow.head_material"));
        defaultTailMaterial = Material.getMaterial(config.getString("arrow.tail_material"));
        allowSharing = config.getBoolean("settings.allow_sharing");
        waypointLimit = config.getInt("settings.waypoint_limit");
        distanceDisplayDefault = config.getBoolean("settings.distance_display_default");

        maxWaypointsPerPage = config.getInt("settings.max_waypoints_per_page", 45);
        absoluteMaxWaypoints = config.getInt("settings.absolute_max_waypoints", 100);

        if (waypointLimit > absoluteMaxWaypoints) {
            waypointLimit = absoluteMaxWaypoints;
            plugin.getLogger().warning("waypoint_limit was higher than absolute_max_waypoints, capping at " + absoluteMaxWaypoints);
        }

        silentModeDefault = config.getBoolean("settings.silent_mode_default");
        soundDisabledDefault = config.getBoolean("settings.sound_disabled_default");
        particlesDisabledDefault = config.getBoolean("settings.particles_disabled_default");
        guidanceMessagesDefault = config.getBoolean("settings.guidance_messages_default");

        guiTitle = ChatColor.translateAlternateColorCodes('&', config.getString("gui.title"));
        defaultArrowDesign = config.getString("designs.default");
        defaultArrivalSound = config.getString("sounds.default");
        defaultArrivalParticle = config.getString("particles.default");
    }

    private void setupWaypointsFile() {
        waypointsFile = new File(plugin.getDataFolder(), "waypoints.yml");
        if (!waypointsFile.exists()) {
            try {
                waypointsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create waypoints.yml file!");
                e.printStackTrace();
            }
        }
        waypointsConfig = YamlConfiguration.loadConfiguration(waypointsFile);
    }

    private void loadArrowDesigns(FileConfiguration config) {
        ConfigurationSection designsSection = config.getConfigurationSection("designs");
        if (designsSection == null) return;

        for (String designName : designsSection.getKeys(false)) {
            if (designName.equals("default")) continue;

            ConfigurationSection designSection = designsSection.getConfigurationSection(designName);
            if (designSection == null) continue;

            Material mainMaterial = Material.getMaterial(designSection.getString("main_material", "LIME_CONCRETE"));
            Material headMaterial = Material.getMaterial(designSection.getString("head_material", "YELLOW_CONCRETE"));
            Material tailMaterial = Material.getMaterial(designSection.getString("tail_material", "RED_CONCRETE"));
            boolean glowing = designSection.getBoolean("glowing", false);
            String particleEffect = designSection.getString("particle_effect", "none");
            int patternType = designSection.getInt("pattern_type", 0);

            ArrowDesign design = new ArrowDesign(designName, mainMaterial, headMaterial,
                    tailMaterial, glowing, particleEffect, patternType);
            arrowDesigns.put(designName, design);
        }
    }

    private void loadArrivalSounds(FileConfiguration config) {
        ConfigurationSection soundsSection = config.getConfigurationSection("sounds.available");
        if (soundsSection == null) return;

        for (String soundName : soundsSection.getKeys(false)) {
            String soundKey = soundsSection.getString(soundName);
            arrivalSounds.put(soundName, soundKey);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("jwaypoints.use")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set":
                handleSetCommand(player, args);
                break;
            case "remove":
                handleRemoveCommand(player, args);
                break;
            case "list":
            case "gui":
                openWaypointGUI(player);
                break;
            case "activate":
                handleActivateCommand(player, args);
                break;
            case "deactivate":
                handleDeactivateCommand(player);
                break;
            case "share":
                handleShareCommand(player, args);
                break;
            case "current":
                handleCurrentCommand(player);
                break;
            case "design":
                handleDesignCommand(player, args);
                break;
            case "designs":
                listDesigns(player);
                break;
            case "sound":
                handleSoundCommand(player, args);
                break;
            case "sounds":
                listSounds(player);
                break;
            case "distance":
                handleDistanceCommand(player);
                break;
            case "confirm":
                handleConfirmCommand(player);
                break;
            case "cancel":
                handleCancelCommand(player);
                break;
            case "guidancemessages":
            case "guidance":
                handleGuidanceMessagesCommand(player);
                break;
            case "particle":
                handleArrivalParticleCommand(player, args);
                break;
            case "particles":
                listArrivalParticles(player);
                break;
            case "updatecheck":
            case "checkupdate":
                handleUpdateCheckCommand(player);
                break;
            case "silent":
                handleSilentModeCommand(player);
                break;
            case "nosound":
            case "soundoff":
                handleSoundDisableCommand(player);
                break;
            case "noparticles":
            case "particlesoff":
                handleParticlesDisableCommand(player);
                break;
            default:
                sendMessage(player, ChatColor.RED + "Unknown command. Type /waypoint help for help.");
        }

        return true;
    }

    private void handleParticlesDisableCommand(Player player) {
        if (!player.hasPermission("jwaypoints.particles")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change particle settings!");
            return;
        }

        boolean currentlyDisabled = isParticlesDisabled(player);
        playerParticlesDisabled.put(player.getUniqueId(), !currentlyDisabled);
        saveUserPreferences();

        if (!currentlyDisabled) {
            sendMessage(player, ChatColor.YELLOW + "Arrival particles disabled!");
            sendMessage(player, ChatColor.GRAY + "No particles will show when reaching waypoints.");
        } else {
            sendMessage(player, ChatColor.GREEN + "Arrival particles enabled!");
            sendMessage(player, ChatColor.GRAY + "Particles will now show when reaching waypoints.");
        }
    }

    private void handleSoundDisableCommand(Player player) {
        if (!player.hasPermission("jwaypoints.sound")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change sound settings!");
            return;
        }

        boolean currentlyDisabled = isSoundDisabled(player);
        playerSoundDisabled.put(player.getUniqueId(), !currentlyDisabled);
        saveUserPreferences();

        if (!currentlyDisabled) {
            sendMessage(player, ChatColor.YELLOW + "Arrival sounds disabled!");
            sendMessage(player, ChatColor.GRAY + "No sounds will play when reaching waypoints.");
        } else {
            sendMessage(player, ChatColor.GREEN + "Arrival sounds enabled!");
            sendMessage(player, ChatColor.GRAY + "Sounds will now play when reaching waypoints.");
        }
    }

    private void handleSilentModeCommand(Player player) {
        if (!player.hasPermission("jwaypoints.settings")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change silent mode settings!");
            return;
        }

        boolean currentlyEnabled = isSilentModeEnabled(player);
        playerSilentMode.put(player.getUniqueId(), !currentlyEnabled);
        saveUserPreferences();

        if (!currentlyEnabled) {
            sendMessage(player, ChatColor.YELLOW + "Silent Mode enabled!");
            sendMessage(player, ChatColor.GRAY + "No sounds or particles will play when reaching waypoints.");
        } else {
            sendMessage(player, ChatColor.GREEN + "Silent Mode disabled!");
            sendMessage(player, ChatColor.GRAY + "Arrival sounds and particles are now enabled.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return new ArrayList<>();

        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "set", "remove", "list", "activate", "deactivate",
                    "share", "current", "design", "designs", "sound", "sounds",
                    "distance", "guidance", "particle", "particles", "gui", "help",
                    "updatecheck", "silent", "nosound", "noparticles"
            );
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "remove":
                case "activate":
                case "share":
                    Map<String, Location> waypoints = playerWaypoints.get(player.getUniqueId());
                    if (waypoints != null) {
                        StringUtil.copyPartialMatches(args[1], waypoints.keySet(), completions);
                    }
                    break;

                case "design":
                    StringUtil.copyPartialMatches(args[1], arrowDesigns.keySet(), completions);
                    break;

                case "sound":
                    StringUtil.copyPartialMatches(args[1], arrivalSounds.keySet(), completions);
                    break;

                case "particle":
                    StringUtil.copyPartialMatches(args[1], arrivalParticles.keySet(), completions);
                    break;
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("share")) {
                List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[2], playerNames, completions);
            } else if (subCommand.equals("set")) {
                completions.add(String.valueOf((int) player.getLocation().getX()));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            completions.add(String.valueOf((int) player.getLocation().getY()));
        } else if (args.length == 5 && args[0].equalsIgnoreCase("set")) {
            completions.add(String.valueOf((int) player.getLocation().getZ()));
        }

        Collections.sort(completions);
        return completions;
    }

    private void handleUpdateCheckCommand(Player player) {
        if (!player.hasPermission("jwaypoints.admin") && !player.hasPermission("jwaypoints.updatecheck") && !player.isOp()) {
            sendMessage(player, ChatColor.RED + "You don't have permission to check for updates!");
            return;
        }

        UpdateChecker updateChecker = plugin.getUpdateChecker();
        if (updateChecker != null) {
            updateChecker.performManualCheck(player);
        } else {
            sendMessage(player, ChatColor.RED + "Update checker is not available!");
        }
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(ChatColor.YELLOW + "[JWP]: " + message);
    }

    private void handleSetCommand(Player player, String[] args) {
        if (!player.hasPermission("jwaypoints.set")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to set waypoints!");
            return;
        }

        Map<String, Location> playerWaypointMap = playerWaypoints.computeIfAbsent(
                player.getUniqueId(), k -> new HashMap<>());

        if (playerWaypointMap.size() >= waypointLimit && !player.hasPermission("jwaypoints.bypass.limit")) {
            sendMessage(player, ChatColor.RED + "You have reached your waypoint limit of " + waypointLimit + "!");
            sendMessage(player, ChatColor.YELLOW + "Remove some waypoints or ask an admin for more slots.");
            return;
        }

        if (playerWaypointMap.size() >= absoluteMaxWaypoints) {
            sendMessage(player, ChatColor.RED + "You have reached the absolute maximum of " + absoluteMaxWaypoints + " waypoints!");
            sendMessage(player, ChatColor.RED + "This is a server safety limit to prevent performance issues.");
            return;
        }

        if (playerWaypointMap.size() >= waypointLimit - 2 && !player.hasPermission("jwaypoints.bypass.limit")) {
            int remaining = waypointLimit - playerWaypointMap.size() - 1;
            sendMessage(player, ChatColor.YELLOW + "⚠ Warning: Only " + remaining + " waypoint slots remaining!");
        }

        if (args.length == 2) {
            String name = args[1];

            if (name.length() > 32) {
                sendMessage(player, ChatColor.RED + "Waypoint name is too long! Maximum 32 characters.");
                return;
            }

            if (name.matches(".*[<>&\"'].*")) {
                sendMessage(player, ChatColor.RED + "Waypoint name contains invalid characters!");
                return;
            }

            if (playerWaypointMap.containsKey(name)) {
                sendMessage(player, ChatColor.RED + "You already have a waypoint with that name!");
                return;
            }

            Location loc = player.getLocation();
            playerWaypointMap.put(name, loc);
            saveWaypoints();
            sendMessage(player, ChatColor.GREEN + "Waypoint '" + name + "' set at your current location!");

        } else if (args.length == 5) {
            String name = args[1];

            if (name.length() > 32) {
                sendMessage(player, ChatColor.RED + "Waypoint name is too long! Maximum 32 characters.");
                return;
            }

            if (playerWaypointMap.containsKey(name)) {
                sendMessage(player, ChatColor.RED + "You already have a waypoint with that name!");
                return;
            }

            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);

                if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                    sendMessage(player, ChatColor.RED + "Coordinates are too far from spawn! Maximum distance: 30,000,000");
                    return;
                }

                if (y < -2048 || y > 2048) {
                    sendMessage(player, ChatColor.RED + "Y coordinate must be between -2048 and 2048!");
                    return;
                }

                Location loc = new Location(player.getWorld(), x, y, z);
                playerWaypointMap.put(name, loc);
                saveWaypoints();
                sendMessage(player, ChatColor.GREEN + "Waypoint '" + name + "' set at coordinates: " +
                        String.format("%.2f, %.2f, %.2f", x, y, z));
            } catch (NumberFormatException e) {
                sendMessage(player, ChatColor.RED + "Invalid coordinates! Please use numbers.");
            }
        } else {
            sendMessage(player, ChatColor.RED + "Usage: /waypoint set <name> [x y z]");
        }
    }

    private void handleRemoveCommand(Player player, String[] args) {
        if (!player.hasPermission("jwaypoints.remove")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to remove waypoints!");
            return;
        }

        if (args.length != 2) {
            sendMessage(player, ChatColor.RED + "Usage: /waypoint remove <name>");
            return;
        }

        String name = args[1];
        Map<String, Location> playerWaypointMap = playerWaypoints.get(player.getUniqueId());

        if (playerWaypointMap == null || !playerWaypointMap.containsKey(name)) {
            sendMessage(player, ChatColor.RED + "You don't have a waypoint with that name!");
            return;
        }

        initiateWaypointDeletion(player, name, DeletionSource.COMMAND);
    }

    private void initiateWaypointDeletion(Player player, String waypointName, DeletionSource source) {
        pendingDeletions.put(player.getUniqueId(), new PendingDeletion(waypointName, source));

        sendMessage(player, ChatColor.RED + "⚠ Are you sure you want to delete waypoint '" + waypointName + "'?");
        sendMessage(player, ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/waypoint confirm" + ChatColor.YELLOW + " to delete it");
        sendMessage(player, ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/waypoint cancel" + ChatColor.YELLOW + " to cancel");
        sendMessage(player, ChatColor.GRAY + "This confirmation will expire in 30 seconds.");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingDeletion pending = pendingDeletions.get(player.getUniqueId());
            if (pending != null && pending.waypointName.equals(waypointName) && pending.isExpired()) {
                pendingDeletions.remove(player.getUniqueId());
                sendMessage(player, ChatColor.GRAY + "Waypoint deletion confirmation expired.");
            }
        }, 600L);
    }

    private void handleConfirmCommand(Player player) {
        PendingDeletion pending = pendingDeletions.remove(player.getUniqueId());

        if (pending == null) {
            sendMessage(player, ChatColor.RED + "No pending waypoint deletion to confirm!");
            return;
        }

        if (pending.isExpired()) {
            sendMessage(player, ChatColor.RED + "Waypoint deletion confirmation has expired!");
            return;
        }

        Map<String, Location> playerWaypointMap = playerWaypoints.get(player.getUniqueId());
        if (playerWaypointMap == null || !playerWaypointMap.containsKey(pending.waypointName)) {
            sendMessage(player, ChatColor.RED + "Waypoint '" + pending.waypointName + "' no longer exists!");
            return;
        }

        Location waypointLocation = playerWaypointMap.get(pending.waypointName);

        if (Objects.equals(activeWaypoints.get(player.getUniqueId()), waypointLocation)) {
            removeActiveWaypoint(player);
        }

        playerWaypointMap.remove(pending.waypointName);
        saveWaypoints();

        sendMessage(player, ChatColor.GREEN + "Waypoint '" + pending.waypointName + "' has been deleted!");

        if (pending.source == DeletionSource.MAIN_GUI) {
            Bukkit.getScheduler().runTask(plugin, () -> openWaypointGUI(player));
        } else if (pending.source == DeletionSource.EDIT_GUI) {
            playerEditingSessions.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> openWaypointGUI(player));
        }
    }

    private void handleCancelCommand(Player player) {
        PendingDeletion pending = pendingDeletions.remove(player.getUniqueId());

        if (pending == null) {
            sendMessage(player, ChatColor.RED + "No pending waypoint deletion to cancel!");
            return;
        }

        sendMessage(player, ChatColor.GREEN + "Waypoint deletion cancelled.");

        if (pending.source == DeletionSource.MAIN_GUI) {
            Bukkit.getScheduler().runTask(plugin, () -> openWaypointGUI(player));
        } else if (pending.source == DeletionSource.EDIT_GUI) {
            Bukkit.getScheduler().runTask(plugin, () -> openWaypointEditGUI(player, pending.waypointName));
        }
    }

    private void openConfirmationGUI(Player player, String waypointName, DeletionSource source) {
        Inventory gui = Bukkit.createInventory(player, 27, ChatColor.RED + "Delete " + waypointName + "?");

        ItemStack confirmItem = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "✓ CONFIRM DELETE");
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(ChatColor.GRAY + "Permanently delete waypoint:");
        confirmLore.add(ChatColor.WHITE + waypointName);
        confirmLore.add("");
        confirmLore.add(ChatColor.RED + "⚠ This action cannot be undone!");
        confirmLore.add("");
        confirmLore.add(ChatColor.YELLOW + "Click to confirm deletion");
        confirmMeta.setLore(confirmLore);
        confirmItem.setItemMeta(confirmMeta);
        gui.setItem(11, confirmItem);

        ItemStack cancelItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "✗ CANCEL");
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add(ChatColor.GRAY + "Keep the waypoint and go back");
        cancelLore.add("");
        cancelLore.add(ChatColor.YELLOW + "Click to cancel deletion");
        cancelMeta.setLore(cancelLore);
        cancelItem.setItemMeta(cancelMeta);
        gui.setItem(15, cancelItem);

        ItemStack infoItem = new ItemStack(Material.COMPASS);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + waypointName);

        Map<String, Location> playerWaypointMap = playerWaypoints.get(player.getUniqueId());
        if (playerWaypointMap != null && playerWaypointMap.containsKey(waypointName)) {
            Location loc = playerWaypointMap.get(waypointName);
            List<String> infoLore = new ArrayList<>();
            infoLore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + loc.getWorld().getName());
            infoLore.add(ChatColor.GRAY + "X: " + ChatColor.WHITE + String.format("%.2f", loc.getX()));
            infoLore.add(ChatColor.GRAY + "Y: " + ChatColor.WHITE + String.format("%.2f", loc.getY()));
            infoLore.add(ChatColor.GRAY + "Z: " + ChatColor.WHITE + String.format("%.2f", loc.getZ()));
            infoMeta.setLore(infoLore);
        }

        infoItem.setItemMeta(infoMeta);
        gui.setItem(13, infoItem);

        pendingDeletions.put(player.getUniqueId(), new PendingDeletion(waypointName, source));
        player.openInventory(gui);
    }

    private void handleActivateCommand(Player player, String[] args) {
        if (!player.hasPermission("jwaypoints.activate")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to activate waypoints!");
            return;
        }

        if (args.length != 2) {
            sendMessage(player, ChatColor.RED + "Usage: /waypoint activate <name>");
            return;
        }

        String name = args[1];
        Map<String, Location> playerWaypointMap = playerWaypoints.get(player.getUniqueId());

        if (playerWaypointMap != null && playerWaypointMap.containsKey(name)) {
            removeActiveWaypoint(player);

            Location waypoint = playerWaypointMap.get(name);
            activeWaypoints.put(player.getUniqueId(), waypoint);

            List<ArmorStand> arrowParts = createArrowArt(player);
            playerArrowParts.put(player.getUniqueId(), arrowParts);

            if (isDistanceEnabled(player)) {
                setupDistanceDisplay(player);
            }

            startWaypointTask(player, name);
            sendMessage(player, ChatColor.GREEN + "Following waypoint '" + name + "'. Follow the floating arrow!");
        } else {
            sendMessage(player, ChatColor.RED + "You don't have a waypoint with that name!");
        }
    }

    private void handleDeactivateCommand(Player player) {
        if (!player.hasPermission("jwaypoints.deactivate")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to deactivate waypoints!");
            return;
        }

        if (!activeWaypoints.containsKey(player.getUniqueId())) {
            sendMessage(player, ChatColor.RED + "You don't have an active waypoint!");
            return;
        }

        removeActiveWaypoint(player);
        sendMessage(player, ChatColor.GREEN + "Waypoint deactivated!");
    }

    private void handleShareCommand(Player player, String[] args) {
        if (!allowSharing) {
            sendMessage(player, ChatColor.RED + "Waypoint sharing is disabled on this server!");
            return;
        }

        if (!player.hasPermission("jwaypoints.share")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to share waypoints!");
            return;
        }

        if (args.length != 3) {
            sendMessage(player, ChatColor.RED + "Usage: /waypoint share <name> <player>");
            return;
        }

        String name = args[1];
        String targetPlayerName = args[2];

        Map<String, Location> playerWaypointMap = playerWaypoints.get(player.getUniqueId());
        if (playerWaypointMap == null || !playerWaypointMap.containsKey(name)) {
            sendMessage(player, ChatColor.RED + "You don't have a waypoint with that name!");
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sendMessage(player, ChatColor.RED + "Player not found or not online!");
            return;
        }

        if (!targetPlayer.hasPermission("jwaypoints.receive")) {
            sendMessage(player, ChatColor.RED + "That player cannot receive waypoints!");
            return;
        }

        Map<String, Location> targetWaypointMap = playerWaypoints.computeIfAbsent(
                targetPlayer.getUniqueId(), k -> new HashMap<>());

        if (targetWaypointMap.size() >= waypointLimit && !targetPlayer.hasPermission("jwaypoints.bypass.limit")) {
            sendMessage(player, ChatColor.RED + "That player has reached their waypoint limit!");
            return;
        }

        String sharedName = name + "_from_" + player.getName();
        int counter = 1;
        while (targetWaypointMap.containsKey(sharedName)) {
            sharedName = name + "_from_" + player.getName() + "_" + counter++;
        }

        targetWaypointMap.put(sharedName, playerWaypointMap.get(name));
        saveWaypoints();

        sendMessage(player, ChatColor.GREEN + "Waypoint '" + name + "' shared with " + targetPlayer.getName() + "!");
        sendMessage(targetPlayer, ChatColor.GREEN + player.getName() + " shared waypoint '" + name +
                "' with you! It's saved as '" + sharedName + "'.");
    }

    private void handleCurrentCommand(Player player) {
        if (!activeWaypoints.containsKey(player.getUniqueId())) {
            sendMessage(player, ChatColor.RED + "You don't have an active waypoint!");
            return;
        }

        Location waypoint = activeWaypoints.get(player.getUniqueId());
        String waypointName = findWaypointName(player, waypoint);
        double distance = player.getLocation().distance(waypoint);

        player.sendMessage(ChatColor.YELLOW + "===== Active Waypoint =====");
        player.sendMessage(ChatColor.GREEN + "Name: " + ChatColor.WHITE + (waypointName != null ? waypointName : "Unknown"));
        player.sendMessage(ChatColor.GREEN + "Location: " + ChatColor.WHITE +
                String.format("%.2f, %.2f, %.2f", waypoint.getX(), waypoint.getY(), waypoint.getZ()));
        player.sendMessage(ChatColor.GREEN + "Distance: " + ChatColor.WHITE + String.format("%.2f blocks", distance));

        boolean distanceEnabled = isDistanceEnabled(player);
        player.sendMessage(ChatColor.GREEN + "Distance Display: " +
                (distanceEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));

        boolean silentMode = isSilentModeEnabled(player);
        boolean soundDisabled = isSoundDisabled(player);
        boolean particlesDisabled = isParticlesDisabled(player);

        player.sendMessage(ChatColor.GREEN + "Silent Mode: " +
                (silentMode ? ChatColor.RED + "Enabled" : ChatColor.GREEN + "Disabled"));

        if (!silentMode) {
            player.sendMessage(ChatColor.GREEN + "Arrival Sound: " +
                    (soundDisabled ? ChatColor.RED + "Disabled" : ChatColor.GREEN + "Enabled"));
            player.sendMessage(ChatColor.GREEN + "Arrival Particles: " +
                    (particlesDisabled ? ChatColor.RED + "Disabled" : ChatColor.GREEN + "Enabled"));
        } else {
            player.sendMessage(ChatColor.GRAY + "Individual sound/particle settings overridden by Silent Mode");
        }
    }

    private void handleDesignCommand(Player player, String[] args) {
        if (!player.hasPermission("jwaypoints.design")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change arrow designs!");
            return;
        }

        if (args.length != 2) {
            sendMessage(player, ChatColor.RED + "Usage: /waypoint design <name>");
            return;
        }

        String designName = args[1].toLowerCase();
        if (!arrowDesigns.containsKey(designName)) {
            sendMessage(player, ChatColor.RED + "Unknown design! Use /waypoint designs to see available designs.");
            return;
        }

        playerArrowDesigns.put(player.getUniqueId(), designName);
        saveUserPreferences();

        if (activeWaypoints.containsKey(player.getUniqueId())) {
            Location waypoint = activeWaypoints.get(player.getUniqueId());
            removeActiveWaypoint(player);
            if (waypoint != null) {
                activeWaypoints.put(player.getUniqueId(), waypoint);
                List<ArmorStand> arrowParts = createArrowArt(player);
                playerArrowParts.put(player.getUniqueId(), arrowParts);
                String waypointName = findWaypointName(player, waypoint);

                if (isDistanceEnabled(player)) {
                    setupDistanceDisplay(player);
                }

                startWaypointTask(player, waypointName != null ? waypointName : "Unknown");
            }
        }

        sendMessage(player, ChatColor.GREEN + "Arrow design changed to '" + designName + "'!");
    }

    private void listDesigns(Player player) {
        if (!player.hasPermission("jwaypoints.design")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change arrow designs!");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "===== Available Arrow Designs =====");

        for (String designName : arrowDesigns.keySet()) {
            ArrowDesign design = arrowDesigns.get(designName);
            String glowText = design.isGlowing() ? ChatColor.GREEN + "Glowing" : ChatColor.GRAY + "Not Glowing";
            String particleText = design.getParticleEffect().equals("none") ?
                    ChatColor.GRAY + "No Particles" : ChatColor.AQUA + "Has Particles";

            player.sendMessage(ChatColor.GREEN + designName + ChatColor.WHITE + " - " +
                    glowText + ChatColor.WHITE + ", " + particleText);
        }

        String currentDesign = playerArrowDesigns.getOrDefault(player.getUniqueId(), defaultArrowDesign);
        player.sendMessage(ChatColor.YELLOW + "Your current design: " + ChatColor.GREEN + currentDesign);
        player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/waypoint design <name>" +
                ChatColor.YELLOW + " to change your design.");
    }

    private void handleSoundCommand(Player player, String[] args) {
        if (!player.hasPermission("jwaypoints.sound")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change arrival sounds!");
            return;
        }

        if (args.length != 2) {
            sendMessage(player, ChatColor.RED + "Usage: /waypoint sound <name>");
            return;
        }

        String soundName = args[1].toLowerCase();
        if (!arrivalSounds.containsKey(soundName)) {
            sendMessage(player, ChatColor.RED + "Unknown sound! Use /waypoint sounds to see available sounds.");
            return;
        }

        playerArrivalSounds.put(player.getUniqueId(), soundName);
        saveUserPreferences();

        String soundKey = arrivalSounds.get(soundName);
        player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);

        sendMessage(player, ChatColor.GREEN + "Arrival sound changed to '" + soundName + "'!");
    }

    private void listSounds(Player player) {
        if (!player.hasPermission("jwaypoints.sound")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change arrival sounds!");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "===== Available Arrival Sounds =====");

        for (String soundName : arrivalSounds.keySet()) {
            player.sendMessage(ChatColor.GREEN + soundName);
        }

        String currentSound = playerArrivalSounds.getOrDefault(player.getUniqueId(), defaultArrivalSound);
        player.sendMessage(ChatColor.YELLOW + "Your current sound: " + ChatColor.GREEN + currentSound);
        player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/waypoint sound <n>" +
                ChatColor.YELLOW + " to change your sound.");
        player.sendMessage(ChatColor.YELLOW + "Tip: The sound will play when you reach your waypoint destination!");
    }

    private void handleDistanceCommand(Player player) {
        if (!player.hasPermission("jwaypoints.distance")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to toggle distance display!");
            return;
        }

        boolean currentlyEnabled = isDistanceEnabled(player);
        playerDistanceEnabled.put(player.getUniqueId(), !currentlyEnabled);
        saveUserPreferences();

        if (!currentlyEnabled) {
            if (activeWaypoints.containsKey(player.getUniqueId())) {
                setupDistanceDisplay(player);
            }
            sendMessage(player, ChatColor.GREEN + "Distance display enabled! Your XP bar will show distance to waypoints.");
        } else {
            restoreOriginalExp(player);
            sendMessage(player, ChatColor.RED + "Distance display disabled! Your XP has been restored.");
        }
    }

    private boolean isDistanceEnabled(Player player) {
        return playerDistanceEnabled.getOrDefault(player.getUniqueId(), distanceDisplayDefault);
    }

    private void setupDistanceDisplay(Player player) {
        UUID playerId = player.getUniqueId();

        playerOriginalExp.put(playerId, player.getTotalExperience());
        playerOriginalLevel.put(playerId, player.getLevel());
        playerOriginalProgress.put(playerId, player.getExp());
    }

    private void updateDistanceDisplay(Player player, double distance, double maxDistance) {
        if (!isDistanceEnabled(player)) return;

        double progress = Math.max(0.0, Math.min(1.0, 1.0 - (distance / maxDistance)));

        player.setLevel((int) distance);
        player.setExp((float) progress);
    }

    private void restoreOriginalExp(Player player) {
        UUID playerId = player.getUniqueId();

        Integer originalExp = playerOriginalExp.remove(playerId);
        Integer originalLevel = playerOriginalLevel.remove(playerId);
        Float originalProgress = playerOriginalProgress.remove(playerId);

        if (originalExp != null && originalLevel != null && originalProgress != null) {
            player.setTotalExperience(originalExp);
            player.setLevel(originalLevel);
            player.setExp(originalProgress);
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "===== JWaypoints Help =====");
        player.sendMessage(ChatColor.GREEN + "/waypoint set <n> [x y z]" + ChatColor.WHITE + " - Set a waypoint");
        player.sendMessage(ChatColor.GREEN + "/waypoint remove <n>" + ChatColor.WHITE + " - Remove a waypoint");
        player.sendMessage(ChatColor.GREEN + "/waypoint list" + ChatColor.WHITE + " - List all your waypoints");
        player.sendMessage(ChatColor.GREEN + "/waypoint activate <n>" + ChatColor.WHITE + " - Follow a waypoint");
        player.sendMessage(ChatColor.GREEN + "/waypoint deactivate" + ChatColor.WHITE + " - Stop following a waypoint");
        player.sendMessage(ChatColor.GREEN + "/waypoint share <n> <player>" + ChatColor.WHITE + " - Share a waypoint");
        player.sendMessage(ChatColor.GREEN + "/waypoint current" + ChatColor.WHITE + " - Show active waypoint info");
        player.sendMessage(ChatColor.GREEN + "/waypoint design <n>" + ChatColor.WHITE + " - Change arrow design");
        player.sendMessage(ChatColor.GREEN + "/waypoint sound <n>" + ChatColor.WHITE + " - Change arrival sound");
        player.sendMessage(ChatColor.GREEN + "/waypoint particle <n>" + ChatColor.WHITE + " - Change arrival particle");
        player.sendMessage(ChatColor.GREEN + "/waypoint distance" + ChatColor.WHITE + " - Toggle distance display");
        player.sendMessage(ChatColor.GREEN + "/waypoint guidance" + ChatColor.WHITE + " - Toggle guidance switch messages");
        player.sendMessage(ChatColor.GREEN + "/waypoint silent" + ChatColor.WHITE + " - Toggle silent mode");
        player.sendMessage(ChatColor.GREEN + "/waypoint nosound" + ChatColor.WHITE + " - Toggle arrival sounds");
        player.sendMessage(ChatColor.GREEN + "/waypoint noparticles" + ChatColor.WHITE + " - Toggle arrival particles");
        player.sendMessage(ChatColor.GREEN + "/waypoint confirm" + ChatColor.WHITE + " - Confirm waypoint deletion");
        player.sendMessage(ChatColor.GREEN + "/waypoint cancel" + ChatColor.WHITE + " - Cancel waypoint deletion");
        if (player.hasPermission("jwaypoints.admin") || player.hasPermission("jwaypoints.updatecheck") || player.isOp()) {
            player.sendMessage(ChatColor.GOLD + "/waypoint updatecheck" + ChatColor.WHITE + " - Check for plugin updates");
        }
    }

    private void loadWaypoints() {
        ConfigurationSection playersSection = waypointsConfig.getConfigurationSection("");
        if (playersSection == null) return;

        for (String uuidStr : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidStr);
                if (playerSection == null) continue;

                Map<String, Location> waypoints = new HashMap<>();
                for (String waypointName : playerSection.getKeys(false)) {
                    ConfigurationSection waypointSection = playerSection.getConfigurationSection(waypointName);
                    if (waypointSection == null) continue;

                    String worldName = waypointSection.getString("world");
                    if (worldName == null || Bukkit.getWorld(worldName) == null) continue;

                    double x = waypointSection.getDouble("x");
                    double y = waypointSection.getDouble("y");
                    double z = waypointSection.getDouble("z");

                    Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                    waypoints.put(waypointName, loc);
                }

                playerWaypoints.put(uuid, waypoints);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in waypoints.yml: " + uuidStr);
            }
        }
    }

    private void saveWaypoints() {
        for (String key : waypointsConfig.getKeys(false)) {
            waypointsConfig.set(key, null);
        }

        for (Map.Entry<UUID, Map<String, Location>> entry : playerWaypoints.entrySet()) {
            UUID playerId = entry.getKey();
            Map<String, Location> waypoints = entry.getValue();

            for (Map.Entry<String, Location> waypointEntry : waypoints.entrySet()) {
                String waypointName = waypointEntry.getKey();
                Location loc = waypointEntry.getValue();
                String path = playerId.toString() + "." + waypointName;

                waypointsConfig.set(path + ".world", loc.getWorld().getName());
                waypointsConfig.set(path + ".x", loc.getX());
                waypointsConfig.set(path + ".y", loc.getY());
                waypointsConfig.set(path + ".z", loc.getZ());
            }
        }

        try {
            waypointsConfig.save(waypointsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save waypoints.yml file!");
            e.printStackTrace();
        }
    }

    private String findWaypointName(Player player, Location waypoint) {
        Map<String, Location> playerWaypointMap = playerWaypoints.get(player.getUniqueId());
        if (playerWaypointMap == null) return null;

        for (Map.Entry<String, Location> entry : playerWaypointMap.entrySet()) {
            Location loc = entry.getValue();
            if (loc.getWorld().equals(waypoint.getWorld()) &&
                    loc.getX() == waypoint.getX() &&
                    loc.getY() == waypoint.getY() &&
                    loc.getZ() == waypoint.getZ()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void openWaypointGUI(Player player) {
        if (!player.hasPermission("jwaypoints.gui")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to use the waypoint GUI!");
            return;
        }

        Map<String, Location> playerWaypointMap = playerWaypoints.get(player.getUniqueId());
        if (playerWaypointMap == null || playerWaypointMap.isEmpty()) {
            sendMessage(player, ChatColor.RED + "You don't have any waypoints!");
            sendMessage(player, ChatColor.YELLOW + "Use /waypoint set <name> to create your first waypoint!");
            return;
        }

        try {
            int currentPage = playerGUIPage.getOrDefault(player.getUniqueId(), 0);
            List<Map.Entry<String, Location>> waypointList = new ArrayList<>(playerWaypointMap.entrySet());

            int totalWaypoints = waypointList.size();
            int totalPages = (int) Math.ceil((double) totalWaypoints / maxWaypointsPerPage);

            if (currentPage >= totalPages) {
                currentPage = Math.max(0, totalPages - 1);
                playerGUIPage.put(player.getUniqueId(), currentPage);
            }

            int startIndex = currentPage * maxWaypointsPerPage;
            int endIndex = Math.min(startIndex + maxWaypointsPerPage, totalWaypoints);
            List<Map.Entry<String, Location>> pageWaypoints = waypointList.subList(startIndex, endIndex);

            String title = totalPages > 1 ?
                    guiTitle + " (Page " + (currentPage + 1) + "/" + totalPages + ")" :
                    guiTitle;

            Inventory gui = Bukkit.createInventory(player, 54, title);

            int slot = 0;
            for (Map.Entry<String, Location> entry : pageWaypoints) {
                if (slot >= 45) break;

                String name = entry.getKey();
                Location loc = entry.getValue();

                ItemStack item = new ItemStack(Material.COMPASS);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.GREEN + name);

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + loc.getWorld().getName());
                lore.add(ChatColor.GRAY + "X: " + ChatColor.WHITE + String.format("%.1f", loc.getX()));
                lore.add(ChatColor.GRAY + "Y: " + ChatColor.WHITE + String.format("%.1f", loc.getY()));
                lore.add(ChatColor.GRAY + "Z: " + ChatColor.WHITE + String.format("%.1f", loc.getZ()));

                double distance = player.getLocation().distance(loc);
                lore.add(ChatColor.GRAY + "Distance: " + ChatColor.WHITE + String.format("%.1f blocks", distance));

                lore.add("");
                lore.add(ChatColor.YELLOW + "Left-click to activate");
                lore.add(ChatColor.RED + "Right-click to remove");
                lore.add(ChatColor.AQUA + "Shift-click to edit");

                meta.setLore(lore);
                item.setItemMeta(meta);
                gui.setItem(slot++, item);
            }

            if (totalPages > 1) {
                if (currentPage > 0) {
                    ItemStack prevItem = new ItemStack(Material.ARROW);
                    ItemMeta prevMeta = prevItem.getItemMeta();
                    prevMeta.setDisplayName(ChatColor.YELLOW + "← Previous Page");
                    List<String> prevLore = new ArrayList<>();
                    prevLore.add(ChatColor.GRAY + "Go to page " + currentPage);
                    prevLore.add("");
                    prevLore.add(ChatColor.AQUA + "▶ Click to go back");
                    prevMeta.setLore(prevLore);
                    prevItem.setItemMeta(prevMeta);
                    gui.setItem(45, prevItem);
                }

                ItemStack pageItem = new ItemStack(Material.BOOK);
                ItemMeta pageMeta = pageItem.getItemMeta();
                pageMeta.setDisplayName(ChatColor.GOLD + "Page " + (currentPage + 1) + " of " + totalPages);
                List<String> pageLore = new ArrayList<>();
                pageLore.add(ChatColor.GRAY + "Showing waypoints " + (startIndex + 1) + "-" + endIndex + " of " + totalWaypoints);
                pageLore.add("");
                if (totalWaypoints >= waypointLimit * 0.8) {
                    int remaining = waypointLimit - totalWaypoints;
                    if (remaining > 0) {
                        pageLore.add(ChatColor.YELLOW + "⚠ " + remaining + " slots remaining");
                    } else {
                        pageLore.add(ChatColor.RED + "⚠ Waypoint limit reached!");
                    }
                }
                pageMeta.setLore(pageLore);
                pageItem.setItemMeta(pageMeta);
                gui.setItem(49, pageItem);

                if (currentPage < totalPages - 1) {
                    ItemStack nextItem = new ItemStack(Material.ARROW);
                    ItemMeta nextMeta = nextItem.getItemMeta();
                    nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page →");
                    List<String> nextLore = new ArrayList<>();
                    nextLore.add(ChatColor.GRAY + "Go to page " + (currentPage + 2));
                    nextLore.add("");
                    nextLore.add(ChatColor.AQUA + "▶ Click to continue");
                    nextMeta.setLore(nextLore);
                    nextItem.setItemMeta(nextMeta);
                    gui.setItem(53, nextItem);
                }
            }

            ItemStack settingsItem = new ItemStack(Material.COMPARATOR);
            ItemMeta settingsMeta = settingsItem.getItemMeta();
            settingsMeta.setDisplayName(ChatColor.GOLD + "⚙ Settings");
            List<String> settingsLore = new ArrayList<>();
            settingsLore.add(ChatColor.YELLOW + "Click to open settings menu");
            settingsLore.add(ChatColor.GRAY + "Configure sounds, designs, and features");
            settingsMeta.setLore(settingsLore);
            settingsItem.setItemMeta(settingsMeta);
            gui.setItem(47, settingsItem);

            ItemStack closeItem = new ItemStack(Material.BARRIER);
            ItemMeta closeMeta = closeItem.getItemMeta();
            closeMeta.setDisplayName(ChatColor.RED + "✗ Close");
            closeItem.setItemMeta(closeMeta);
            gui.setItem(51, closeItem);

            player.openInventory(gui);

        } catch (Exception e) {
            plugin.getLogger().severe("Error opening waypoint GUI for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            sendMessage(player, ChatColor.RED + "An error occurred opening the waypoint GUI. Please try again.");
        }
    }

    private void openWaypointEditGUI(Player player, String waypointName) {
        if (!player.hasPermission("jwaypoints.edit")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to edit waypoints!");
            return;
        }

        Map<String, Location> playerWaypointMap = playerWaypoints.get(player.getUniqueId());
        if (playerWaypointMap == null || !playerWaypointMap.containsKey(waypointName)) {
            sendMessage(player, ChatColor.RED + "Waypoint not found!");
            return;
        }

        Location waypointLoc = playerWaypointMap.get(waypointName);
        Inventory gui = Bukkit.createInventory(player, 27, ChatColor.GOLD + "Edit: " + waypointName);

        ItemStack editNameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta editNameMeta = editNameItem.getItemMeta();
        editNameMeta.setDisplayName(ChatColor.GREEN + "Change Name");
        List<String> editNameLore = new ArrayList<>();
        editNameLore.add(ChatColor.GRAY + "Current name: " + ChatColor.WHITE + waypointName);
        editNameLore.add("");
        editNameLore.add(ChatColor.YELLOW + "Click to rename this waypoint");
        editNameLore.add(ChatColor.GRAY + "You'll be prompted to type the new name in chat");
        editNameMeta.setLore(editNameLore);
        editNameItem.setItemMeta(editNameMeta);
        gui.setItem(10, editNameItem);

        ItemStack editCoordsItem = new ItemStack(Material.COMPASS);
        ItemMeta editCoordsMeta = editCoordsItem.getItemMeta();
        editCoordsMeta.setDisplayName(ChatColor.AQUA + "Change Coordinates");
        List<String> editCoordsLore = new ArrayList<>();
        editCoordsLore.add(ChatColor.GRAY + "Current location:");
        editCoordsLore.add(ChatColor.WHITE + "X: " + String.format("%.1f", waypointLoc.getX()));
        editCoordsLore.add(ChatColor.WHITE + "Y: " + String.format("%.1f", waypointLoc.getY()));
        editCoordsLore.add(ChatColor.WHITE + "Z: " + String.format("%.1f", waypointLoc.getZ()));
        editCoordsLore.add(ChatColor.WHITE + "World: " + waypointLoc.getWorld().getName());
        editCoordsLore.add("");
        editCoordsLore.add(ChatColor.YELLOW + "Click to change coordinates");
        editCoordsLore.add(ChatColor.GRAY + "You'll be prompted to enter new coordinates");
        editCoordsMeta.setLore(editCoordsLore);
        editCoordsItem.setItemMeta(editCoordsMeta);
        gui.setItem(12, editCoordsItem);

        ItemStack moveHereItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta moveHereMeta = moveHereItem.getItemMeta();
        moveHereMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Move to Current Location");
        List<String> moveHereLore = new ArrayList<>();
        moveHereLore.add(ChatColor.GRAY + "Your current location:");
        Location playerLoc = player.getLocation();
        moveHereLore.add(ChatColor.WHITE + "X: " + String.format("%.1f", playerLoc.getX()));
        moveHereLore.add(ChatColor.WHITE + "Y: " + String.format("%.1f", playerLoc.getY()));
        moveHereLore.add(ChatColor.WHITE + "Z: " + String.format("%.1f", playerLoc.getZ()));
        moveHereLore.add("");
        moveHereLore.add(ChatColor.YELLOW + "Click to move waypoint to your current location");
        moveHereMeta.setLore(moveHereLore);
        moveHereItem.setItemMeta(moveHereMeta);
        gui.setItem(14, moveHereItem);

        ItemStack deleteItem = new ItemStack(Material.TNT);
        ItemMeta deleteMeta = deleteItem.getItemMeta();
        deleteMeta.setDisplayName(ChatColor.RED + "Delete Waypoint");
        List<String> deleteLore = new ArrayList<>();
        deleteLore.add(ChatColor.GRAY + "Permanently delete this waypoint");
        deleteLore.add("");
        deleteLore.add(ChatColor.RED + "⚠ This action cannot be undone!");
        deleteLore.add(ChatColor.YELLOW + "Click to delete");
        deleteMeta.setLore(deleteLore);
        deleteItem.setItemMeta(deleteMeta);
        gui.setItem(16, deleteItem);

        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Back to Waypoints");
        backItem.setItemMeta(backMeta);
        gui.setItem(22, backItem);

        Map<String, String> session = playerEditingSessions.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        session.put("editing_waypoint", waypointName);

        player.openInventory(gui);
    }

    private void openSettingsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(player, 45, ChatColor.GOLD + "⚙ Waypoint Settings ⚙");

        ItemStack designItem = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta designMeta = designItem.getItemMeta();
        designMeta.setDisplayName(ChatColor.GREEN + "🏹 Arrow Designs");
        List<String> designLore = new ArrayList<>();
        designLore.add(ChatColor.YELLOW + "Click to customize your arrow appearance");
        String currentDesign = playerArrowDesigns.getOrDefault(player.getUniqueId(), defaultArrowDesign);
        designLore.add(ChatColor.GRAY + "Current: " + ChatColor.WHITE + currentDesign);
        designLore.add("");
        designLore.add(ChatColor.AQUA + "▶ Click to browse designs");
        designMeta.setLore(designLore);
        designItem.setItemMeta(designMeta);
        gui.setItem(10, designItem);

        ItemStack soundItem = new ItemStack(Material.NOTE_BLOCK);
        ItemMeta soundMeta = soundItem.getItemMeta();
        soundMeta.setDisplayName(ChatColor.AQUA + "🔊 Arrival Sounds");
        List<String> soundLore = new ArrayList<>();
        soundLore.add(ChatColor.YELLOW + "Click to change your arrival sound");
        String currentSound = playerArrivalSounds.getOrDefault(player.getUniqueId(), defaultArrivalSound);
        soundLore.add(ChatColor.GRAY + "Current: " + ChatColor.WHITE + currentSound);
        boolean soundDisabled = isSoundDisabled(player);
        soundLore.add(ChatColor.GRAY + "Status: " +
                (soundDisabled ? ChatColor.RED + "🔇 Disabled" : ChatColor.GREEN + "🔊 Enabled"));
        soundLore.add("");
        soundLore.add(ChatColor.AQUA + "▶ Click to browse sounds");
        soundMeta.setLore(soundLore);
        soundItem.setItemMeta(soundMeta);
        gui.setItem(12, soundItem);

        ItemStack particleItem = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta particleMeta = particleItem.getItemMeta();
        particleMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "✨ Arrival Particles");
        List<String> particleLore = new ArrayList<>();
        particleLore.add(ChatColor.YELLOW + "Click to change your particle effects");
        String currentParticle = playerArrivalParticles.getOrDefault(player.getUniqueId(), "FIREWORK");
        particleLore.add(ChatColor.GRAY + "Current: " + ChatColor.WHITE + currentParticle);
        boolean particlesDisabled = isParticlesDisabled(player);
        particleLore.add(ChatColor.GRAY + "Status: " +
                (particlesDisabled ? ChatColor.RED + "❌ Disabled" : ChatColor.GREEN + "✨ Enabled"));
        particleLore.add("");
        particleLore.add(ChatColor.AQUA + "▶ Click to browse particles");
        particleMeta.setLore(particleLore);
        particleItem.setItemMeta(particleMeta);
        gui.setItem(14, particleItem);

        ItemStack distanceItem = new ItemStack(Material.CLOCK);
        ItemMeta distanceMeta = distanceItem.getItemMeta();
        distanceMeta.setDisplayName(ChatColor.YELLOW + "📏 Distance Display");
        List<String> distanceLore = new ArrayList<>();
        distanceLore.add(ChatColor.YELLOW + "Click to toggle distance in XP bar");
        distanceLore.add(ChatColor.GRAY + "Shows remaining distance to waypoint");
        boolean distanceEnabled = isDistanceEnabled(player);
        distanceLore.add(ChatColor.GRAY + "Status: " +
                (distanceEnabled ? ChatColor.GREEN + "📊 Enabled" : ChatColor.RED + "📊 Disabled"));
        distanceLore.add("");
        distanceLore.add(ChatColor.AQUA + "▶ Click to toggle");
        distanceMeta.setLore(distanceLore);
        distanceItem.setItemMeta(distanceMeta);
        gui.setItem(16, distanceItem);


        ItemStack silentItem = new ItemStack(isSilentModeEnabled(player) ? Material.REDSTONE_TORCH : Material.TORCH);
        ItemMeta silentMeta = silentItem.getItemMeta();
        silentMeta.setDisplayName(ChatColor.DARK_RED + "🔇 Silent Mode");
        List<String> silentLore = new ArrayList<>();
        silentLore.add(ChatColor.YELLOW + "Click to toggle complete silence");
        silentLore.add(ChatColor.GRAY + "Disables both sounds and particles");
        silentLore.add(ChatColor.GRAY + "when reaching waypoints");
        boolean silentEnabled = isSilentModeEnabled(player);
        silentLore.add("");
        silentLore.add(ChatColor.GRAY + "Status: " +
                (silentEnabled ? ChatColor.RED + "🔇 SILENT" : ChatColor.GREEN + "🔊 NORMAL"));
        if (silentEnabled) {
            silentLore.add("");
            silentLore.add(ChatColor.RED + "⚠ Overrides individual sound/particle settings");
        }
        silentLore.add("");
        silentLore.add(ChatColor.AQUA + "▶ Click to toggle");
        silentMeta.setLore(silentLore);
        silentItem.setItemMeta(silentMeta);
        gui.setItem(20, silentItem);

        ItemStack guidanceItem = new ItemStack(Material.PAPER);
        ItemMeta guidanceMeta = guidanceItem.getItemMeta();
        guidanceMeta.setDisplayName(ChatColor.GOLD + "💬 Guidance Messages");
        List<String> guidanceLore = new ArrayList<>();
        guidanceLore.add(ChatColor.YELLOW + "Click to toggle guidance notifications");
        guidanceLore.add(ChatColor.GRAY + "Controls messages when switching between");
        guidanceLore.add(ChatColor.GRAY + "arrow and compass guidance modes");
        boolean guidanceEnabled = isGuidanceMessagesEnabled(player);
        guidanceLore.add("");
        guidanceLore.add(ChatColor.GRAY + "Status: " +
                (guidanceEnabled ? ChatColor.GREEN + "💬 Enabled" : ChatColor.RED + "💬 Disabled"));
        guidanceLore.add("");
        guidanceLore.add(ChatColor.AQUA + "▶ Click to toggle");
        guidanceMeta.setLore(guidanceLore);
        guidanceItem.setItemMeta(guidanceMeta);
        gui.setItem(22, guidanceItem);

        boolean soundOff = isSoundDisabled(player);
        ItemStack soundToggleItem = new ItemStack(soundOff ? Material.REDSTONE : Material.GREEN_DYE);
        ItemMeta soundToggleMeta = soundToggleItem.getItemMeta();
        soundToggleMeta.setDisplayName(ChatColor.AQUA + (soundOff ? "🔇 Enable Sounds" : "🔊 Disable Sounds"));
        List<String> soundToggleLore = new ArrayList<>();
        soundToggleLore.add(ChatColor.YELLOW + "Click to toggle arrival sounds only");
        soundToggleLore.add(ChatColor.GRAY + "Independent of silent mode setting");
        soundToggleLore.add("");
        soundToggleLore.add(ChatColor.GRAY + "Arrival Sounds: " +
                (soundOff ? ChatColor.RED + "🔇 OFF" : ChatColor.GREEN + "🔊 ON"));

        if (isSilentModeEnabled(player)) {
            soundToggleLore.add("");
            soundToggleLore.add(ChatColor.RED + "⚠ Currently overridden by Silent Mode");
        }
        soundToggleLore.add("");
        soundToggleLore.add(ChatColor.AQUA + "▶ Click to toggle");
        soundToggleMeta.setLore(soundToggleLore);
        soundToggleItem.setItemMeta(soundToggleMeta);
        gui.setItem(30, soundToggleItem);

        boolean particlesOff = isParticlesDisabled(player);
        ItemStack particleToggleItem = new ItemStack(particlesOff ? Material.REDSTONE : Material.GREEN_DYE);
        ItemMeta particleToggleMeta = particleToggleItem.getItemMeta();
        particleToggleMeta.setDisplayName(ChatColor.LIGHT_PURPLE + (particlesOff ? "❌ Enable Particles" : "✨ Disable Particles"));
        List<String> particleToggleLore = new ArrayList<>();
        particleToggleLore.add(ChatColor.YELLOW + "Click to toggle arrival particles only");
        particleToggleLore.add(ChatColor.GRAY + "Independent of silent mode setting");
        particleToggleLore.add("");
        particleToggleLore.add(ChatColor.GRAY + "Arrival Particles: " +
                (particlesOff ? ChatColor.RED + "❌ OFF" : ChatColor.GREEN + "✨ ON"));

        if (isSilentModeEnabled(player)) {
            particleToggleLore.add("");
            particleToggleLore.add(ChatColor.RED + "⚠ Currently overridden by Silent Mode");
        }
        particleToggleLore.add("");
        particleToggleLore.add(ChatColor.AQUA + "▶ Click to toggle");
        particleToggleMeta.setLore(particleToggleLore);
        particleToggleItem.setItemMeta(particleToggleMeta);
        gui.setItem(32, particleToggleItem);

        ItemStack cornerItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta cornerMeta = cornerItem.getItemMeta();
        cornerMeta.setDisplayName(" ");
        cornerItem.setItemMeta(cornerMeta);

        gui.setItem(0, cornerItem);
        gui.setItem(8, cornerItem);
        gui.setItem(36, cornerItem);
        gui.setItem(44, cornerItem);

        ItemStack sideItem = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta sideMeta = sideItem.getItemMeta();
        sideMeta.setDisplayName(" ");
        sideItem.setItemMeta(sideMeta);

        gui.setItem(9, sideItem);
        gui.setItem(17, sideItem);
        gui.setItem(18, sideItem);
        gui.setItem(26, sideItem);
        gui.setItem(27, sideItem);
        gui.setItem(35, sideItem);

        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Back to Waypoints");
        List<String> backLore = new ArrayList<>();
        backLore.add(ChatColor.GRAY + "Return to your waypoint list");
        backLore.add("");
        backLore.add(ChatColor.AQUA + "▶ Click to go back");
        backMeta.setLore(backLore);
        backItem.setItemMeta(backMeta);
        gui.setItem(40, backItem);

        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "ℹ Settings Overview");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Configure your waypoint experience:");
        infoLore.add("");
        infoLore.add(ChatColor.GREEN + "🏹 Customize" + ChatColor.GRAY + " - Designs, sounds, particles");
        infoLore.add(ChatColor.YELLOW + "⚙ Features" + ChatColor.GRAY + " - Distance, guidance, silent mode");
        infoLore.add(ChatColor.AQUA + "🔧 Controls" + ChatColor.GRAY + " - Individual sound/particle toggles");
        infoLore.add("");
        if (isSilentModeEnabled(player)) {
            infoLore.add(ChatColor.RED + "🔇 Silent Mode is currently ACTIVE");
        } else {
            infoLore.add(ChatColor.GREEN + "🔊 All audio/visual effects enabled");
        }
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        gui.setItem(4, infoItem);

        player.openInventory(gui);
    }

    private void openDesignsGUI(Player player) {
        if (!player.hasPermission("jwaypoints.design")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change arrow designs!");
            return;
        }

        Inventory gui = Bukkit.createInventory(player, 27, ChatColor.GREEN + "Arrow Designs");
        int slot = 0;

        for (Map.Entry<String, ArrowDesign> entry : arrowDesigns.entrySet()) {
            String designName = entry.getKey();
            ArrowDesign design = entry.getValue();

            ItemStack item = new ItemStack(design.getMainMaterial());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + designName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Main: " + ChatColor.WHITE + design.getMainMaterial().name());
            lore.add(ChatColor.GRAY + "Head: " + ChatColor.WHITE + design.getHeadMaterial().name());
            lore.add(ChatColor.GRAY + "Tail: " + ChatColor.WHITE + design.getTailMaterial().name());

            if (design.isGlowing()) {
                lore.add(ChatColor.AQUA + "✨ Glowing");
            }

            if (!design.getParticleEffect().equals("none")) {
                lore.add(ChatColor.LIGHT_PURPLE + "🌟 Particle Effects");
            }

            String patternName = switch (design.getPatternType()) {
                case 1 -> "Spiral";
                case 2 -> "Zigzag";
                case 3 -> "Static Arrow";
                default -> "Standard";
            };

            lore.add(ChatColor.GRAY + "Pattern: " + ChatColor.WHITE + patternName);
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to select this design");

            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.setItem(slot++, item);

            if (slot >= 18) break;
        }

        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Back to Settings");
        backItem.setItemMeta(backMeta);
        gui.setItem(26, backItem);

        player.openInventory(gui);
    }

    private void openSoundsGUI(Player player) {
        if (!player.hasPermission("jwaypoints.sound")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change arrival sounds!");
            return;
        }

        Inventory gui = Bukkit.createInventory(player, 27, ChatColor.AQUA + "Arrival Sounds");
        int slot = 0;

        for (Map.Entry<String, String> entry : arrivalSounds.entrySet()) {
            String soundName = entry.getKey();

            Material soundMaterial = soundIcons.getOrDefault(soundName, Material.NOTE_BLOCK);

            ItemStack item = new ItemStack(soundMaterial);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + soundName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Sound effect played when");
            lore.add(ChatColor.GRAY + "you reach your destination");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to select and preview");

            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.setItem(slot++, item);

            if (slot >= 18) break;
        }

        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Back to Settings");
        backItem.setItemMeta(backMeta);
        gui.setItem(26, backItem);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        ItemStack clickedItem = event.getCurrentItem();

        if (isWaypointGUI(title)) {
            event.setCancelled(true);

            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            if (title.contains("Waypoint Manager") || title.equals(guiTitle)) {
                handleMainGUIClick(player, clickedItem, event);
            } else if (title.contains("Waypoint Settings") || title.contains("⚙")) {
                handleSettingsGUIClick(player, clickedItem);
            } else if (title.equals(ChatColor.GREEN + "Arrow Designs")) {
                handleDesignsGUIClick(player, clickedItem);
            } else if (title.equals(ChatColor.AQUA + "Arrival Sounds")) {
                handleSoundsGUIClick(player, clickedItem);
            } else if (title.equals(ChatColor.LIGHT_PURPLE + "Arrival Particles")) {
                handleArrivalParticlesGUIClick(player, clickedItem);
            } else if (title.startsWith(ChatColor.GOLD + "Edit: ")) {
                handleEditGUIClick(player, clickedItem, title);
            } else if (title.startsWith(ChatColor.RED + "Delete ")) {
                handleConfirmationGUIClick(player, clickedItem);
            }
        }
    }

    private boolean isWaypointGUI(String title) {
        String cleanTitle = ChatColor.stripColor(title).toLowerCase();

        return cleanTitle.contains("waypoint") ||
                cleanTitle.contains("arrow designs") ||
                cleanTitle.contains("arrival sounds") ||
                cleanTitle.contains("arrival particles") ||
                cleanTitle.contains("delete") ||
                cleanTitle.contains("edit:") ||
                cleanTitle.contains("page") ||
                title.contains("⚙");
    }

    private void handleWaypointClick(Player player, String waypointName, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                openWaypointEditGUI(player, waypointName);
            }, 2L);
        } else if (event.isLeftClick()) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.performCommand("waypoint activate " + waypointName);
            }, 2L);
        } else if (event.isRightClick()) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                openConfirmationGUI(player, waypointName, DeletionSource.MAIN_GUI);
            }, 2L);
        }
    }

    private void addPaginationButtons(Inventory gui, int currentPage, int totalPages) {
        if (currentPage > 0) {
            ItemStack prevItem = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevItem.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "← Previous Page");
            List<String> prevLore = new ArrayList<>();
            prevLore.add(ChatColor.GRAY + "Go to page " + currentPage);
            prevLore.add(ChatColor.GRAY + "Navigation: " + ChatColor.WHITE + "PREVIOUS");
            prevLore.add("");
            prevLore.add(ChatColor.AQUA + "▶ Click to go back");
            prevMeta.setLore(prevLore);
            prevItem.setItemMeta(prevMeta);
            gui.setItem(45, prevItem);
        }

        ItemStack pageItem = new ItemStack(Material.BOOK);
        ItemMeta pageMeta = pageItem.getItemMeta();
        pageMeta.setDisplayName(ChatColor.GOLD + "Page " + (currentPage + 1) + " of " + totalPages);
        List<String> pageLore = new ArrayList<>();
        pageLore.add(ChatColor.GRAY + "Current page information");
        pageLore.add(ChatColor.GRAY + "Use arrow buttons to navigate");
        pageMeta.setLore(pageLore);
        pageItem.setItemMeta(pageMeta);
        gui.setItem(49, pageItem);

        if (currentPage < totalPages - 1) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page →");
            List<String> nextLore = new ArrayList<>();
            nextLore.add(ChatColor.GRAY + "Go to page " + (currentPage + 2));
            nextLore.add(ChatColor.GRAY + "Navigation: " + ChatColor.WHITE + "NEXT");
            nextLore.add("");
            nextLore.add(ChatColor.AQUA + "▶ Click to continue");
            nextMeta.setLore(nextLore);
            nextItem.setItemMeta(nextMeta);
            gui.setItem(53, nextItem);
        }
    }

    private void handleMainGUIClick(Player player, ItemStack clickedItem, InventoryClickEvent event) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        String displayName = "";
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName() != null) {
            displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        }

        switch (clickedItem.getType()) {
            case BARRIER:
                player.closeInventory();
                break;

            case COMPARATOR:
                player.closeInventory();
                openSettingsGUI(player);
                break;

            case ARROW:
                if (displayName.contains("Previous") || displayName.contains("←")) {
                    int currentPage = playerGUIPage.getOrDefault(player.getUniqueId(), 0);
                    if (currentPage > 0) {
                        playerGUIPage.put(player.getUniqueId(), currentPage - 1);
                        openWaypointGUI(player);
                    }
                } else if (displayName.contains("Next") || displayName.contains("→")) {
                    Map<String, Location> playerWaypointMap = playerWaypoints.get(player.getUniqueId());
                    if (playerWaypointMap != null) {
                        int totalPages = (int) Math.ceil((double) playerWaypointMap.size() / maxWaypointsPerPage);
                        int currentPage = playerGUIPage.getOrDefault(player.getUniqueId(), 0);
                        if (currentPage < totalPages - 1) {
                            playerGUIPage.put(player.getUniqueId(), currentPage + 1);
                            openWaypointGUI(player);
                        }
                    }
                }
                break;

            case BOOK:
                break;

            case COMPASS:
                if (clickedItem.hasItemMeta()) {
                    String waypointName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

                    if (event.isShiftClick()) {
                        player.closeInventory();
                        openWaypointEditGUI(player, waypointName);
                    } else if (event.isLeftClick()) {
                        player.closeInventory();
                        player.performCommand("waypoint activate " + waypointName);
                    } else if (event.isRightClick()) {
                        player.closeInventory();
                        openConfirmationGUI(player, waypointName, DeletionSource.MAIN_GUI);
                    }
                }
                break;

            default:
                break;
        }
    }

    private void handlePaginationClick(Player player, String displayName, List<String> lore) {
        boolean isPrevious = displayName.contains("previous") || displayName.contains("←");
        boolean isNext = displayName.contains("next") || displayName.contains("→");

        if (!isPrevious && !isNext && lore != null) {
            String loreText = String.join(" ", lore).toLowerCase();
            isPrevious = loreText.contains("previous") || loreText.contains("back");
            isNext = loreText.contains("next") || loreText.contains("continue");
        }

        if (isPrevious) {
            int currentPage = playerGUIPage.getOrDefault(player.getUniqueId(), 0);
            if (currentPage > 0) {
                playerGUIPage.put(player.getUniqueId(), currentPage - 1);
                sendMessage(player, ChatColor.YELLOW + "Going to page " + currentPage + "...");

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    openWaypointGUI(player);
                }, 2L);
            } else {
                sendMessage(player, ChatColor.RED + "You're already on the first page!");
            }
        } else if (isNext) {
            Map<String, Location> playerWaypointMap = playerWaypoints.get(player.getUniqueId());
            if (playerWaypointMap != null) {
                int totalPages = (int) Math.ceil((double) playerWaypointMap.size() / maxWaypointsPerPage);
                int currentPage = playerGUIPage.getOrDefault(player.getUniqueId(), 0);

                if (currentPage < totalPages - 1) {
                    playerGUIPage.put(player.getUniqueId(), currentPage + 1);
                    sendMessage(player, ChatColor.YELLOW + "Going to page " + (currentPage + 2) + "...");

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        openWaypointGUI(player);
                    }, 2L);
                } else {
                    sendMessage(player, ChatColor.RED + "You're already on the last page!");
                }
            }
        }
    }

    private void handleArrivalParticlesGUIClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getType() == Material.ARROW) {
            player.closeInventory();
            openSettingsGUI(player);
        } else if (clickedItem.hasItemMeta()) {
            String particleName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            if (arrivalParticles.containsKey(particleName)) {
                playerArrivalParticles.put(player.getUniqueId(), particleName);
                saveUserPreferences();

                spawnArrivalParticle(player, player.getLocation(), particleName);

                player.closeInventory();
                sendMessage(player, ChatColor.GREEN + "Arrival particle changed to '" + particleName + "'!");
            }
        }
    }

    private void handleSettingsGUIClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE ||
                clickedItem.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE ||
                clickedItem.getType() == Material.BOOK) {
            return;
        }

        String displayName = "";
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName() != null) {
            displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).toLowerCase();
        }

        switch (clickedItem.getType()) {
            case ARROW:
                player.closeInventory();
                openWaypointGUI(player);
                break;

            case LIME_CONCRETE:
                if (displayName.contains("arrow designs")) {
                    player.closeInventory();
                    openDesignsGUI(player);
                }
                break;

            case NOTE_BLOCK:
                if (displayName.contains("arrival sounds")) {
                    player.closeInventory();
                    openSoundsGUI(player);
                }
                break;

            case CLOCK:
                if (displayName.contains("distance display")) {
                    player.closeInventory();
                    handleDistanceCommand(player);
                }
                break;

            case PAPER:
                if (displayName.contains("guidance messages")) {
                    player.closeInventory();
                    handleGuidanceMessagesCommand(player);
                }
                break;

            case FIREWORK_ROCKET:
                if (displayName.contains("arrival particles")) {
                    player.closeInventory();
                    openArrivalParticlesGUI(player);
                }
                break;

            case REDSTONE_TORCH:
            case TORCH:
                if (displayName.contains("silent mode")) {
                    player.closeInventory();
                    handleSilentModeCommand(player);
                }
                break;

            case REDSTONE:
            case GREEN_DYE:
                if (displayName.contains("sounds")) {
                    player.closeInventory();
                    handleSoundDisableCommand(player);
                } else if (displayName.contains("particles")) {
                    player.closeInventory();
                    handleParticlesDisableCommand(player);
                }
                break;

            default:
                break;
        }
    }


    private void handleDesignsGUIClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getType() == Material.ARROW) {
            player.closeInventory();
            openSettingsGUI(player);
        } else if (clickedItem.hasItemMeta()) {
            String designName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            if (arrowDesigns.containsKey(designName)) {
                playerArrowDesigns.put(player.getUniqueId(), designName);
                saveUserPreferences();
                player.closeInventory();
                sendMessage(player, ChatColor.GREEN + "Arrow design changed to '" + designName + "'!");

                if (activeWaypoints.containsKey(player.getUniqueId())) {
                    Location waypoint = activeWaypoints.get(player.getUniqueId());
                    removeActiveWaypoint(player);
                    if (waypoint != null) {
                        activeWaypoints.put(player.getUniqueId(), waypoint);
                        List<ArmorStand> arrowParts = createArrowArt(player);
                        playerArrowParts.put(player.getUniqueId(), arrowParts);
                        String waypointName = findWaypointName(player, waypoint);

                        if (isDistanceEnabled(player)) {
                            setupDistanceDisplay(player);
                        }

                        startWaypointTask(player, waypointName != null ? waypointName : "Unknown");
                    }
                }
            }
        }
    }

    private void handleSoundsGUIClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getType() == Material.ARROW) {
            player.closeInventory();
            openSettingsGUI(player);
        } else if (clickedItem.hasItemMeta()) {
            String soundName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            if (arrivalSounds.containsKey(soundName)) {
                playerArrivalSounds.put(player.getUniqueId(), soundName);
                saveUserPreferences();

                String soundKey = arrivalSounds.get(soundName);
                player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);

                player.closeInventory();
                sendMessage(player, ChatColor.GREEN + "Arrival sound changed to '" + soundName + "'!");
            }
        }
    }

    private void handleEditGUIClick(Player player, ItemStack clickedItem, String title) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        Map<String, String> session = playerEditingSessions.get(player.getUniqueId());
        if (session == null) {
            sendMessage(player, ChatColor.RED + "Edit session not found!");
            player.closeInventory();
            return;
        }

        String waypointName = session.get("editing_waypoint");
        if (waypointName == null) {
            sendMessage(player, ChatColor.RED + "Waypoint name not found in session!");
            player.closeInventory();
            return;
        }

        Map<String, Location> playerWaypointMap = playerWaypoints.get(player.getUniqueId());
        if (playerWaypointMap == null || !playerWaypointMap.containsKey(waypointName)) {
            sendMessage(player, ChatColor.RED + "Waypoint '" + waypointName + "' not found!");
            playerEditingSessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        switch (clickedItem.getType()) {
            case ARROW:
                playerEditingSessions.remove(player.getUniqueId());
                player.closeInventory();
                openWaypointGUI(player);
                break;

            case NAME_TAG:
                player.closeInventory();
                sendMessage(player, ChatColor.GREEN + "Type the new name for waypoint '" + waypointName + "' in chat:");
                sendMessage(player, ChatColor.GRAY + "Type 'cancel' to cancel the operation");
                session.put("editing_mode", "rename");
                break;

            case COMPASS:
                player.closeInventory();
                sendMessage(player, ChatColor.GREEN + "Type new coordinates for '" + waypointName + "' in chat (format: x y z):");
                sendMessage(player, ChatColor.YELLOW + "Example: 100 64 -200");
                sendMessage(player, ChatColor.GRAY + "Type 'cancel' to cancel the operation");
                session.put("editing_mode", "coordinates");
                break;

            case ENDER_PEARL:
                Location oldLocation = playerWaypointMap.get(waypointName);
                if (Objects.equals(activeWaypoints.get(player.getUniqueId()), oldLocation)) {
                    removeActiveWaypoint(player);
                }
                Location newLocation = player.getLocation().clone();
                playerWaypointMap.put(waypointName, newLocation);
                saveWaypoints();
                playerEditingSessions.remove(player.getUniqueId());
                player.closeInventory();
                sendMessage(player, ChatColor.GREEN + "Waypoint '" + waypointName + "' moved to your current location!");
                break;

            case TNT:
                player.closeInventory();
                openConfirmationGUI(player, waypointName, DeletionSource.EDIT_GUI);
                break;

            default:
                break;
        }
    }

    private void handleConfirmationGUIClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        PendingDeletion pending = pendingDeletions.get(player.getUniqueId());
        if (pending == null) {
            sendMessage(player, ChatColor.RED + "No pending deletion found!");
            player.closeInventory();
            return;
        }

        if (clickedItem.getType() == Material.GREEN_CONCRETE) {
            player.closeInventory();
            handleConfirmCommand(player);
        } else if (clickedItem.getType() == Material.RED_CONCRETE) {
            player.closeInventory();
            handleCancelCommand(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeActiveWaypoint(event.getPlayer());
        playerEditingSessions.remove(event.getPlayer().getUniqueId());
        pendingDeletions.remove(event.getPlayer().getUniqueId());
        playerUsingActionBar.remove(event.getPlayer().getUniqueId());
        playerGUIPage.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        Map<String, String> session = playerEditingSessions.get(playerId);
        if (session == null) return;

        String editingMode = session.get("editing_mode");
        String waypointName = session.get("editing_waypoint");

        if (editingMode == null || waypointName == null) return;

        event.setCancelled(true);

        String message = event.getMessage().trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (message.equalsIgnoreCase("cancel")) {
                    playerEditingSessions.remove(playerId);
                    sendMessage(player, ChatColor.YELLOW + "Editing cancelled for waypoint '" + waypointName + "'.");
                    return;
                }

                Map<String, Location> playerWaypointMap = playerWaypoints.get(playerId);
                if (playerWaypointMap == null) {
                    playerEditingSessions.remove(playerId);
                    sendMessage(player, ChatColor.RED + "No waypoints found for your account!");
                    return;
                }

                if (!playerWaypointMap.containsKey(waypointName)) {
                    playerEditingSessions.remove(playerId);
                    sendMessage(player, ChatColor.RED + "Waypoint '" + waypointName + "' not found!");
                    plugin.getLogger().warning("Waypoint '" + waypointName + "' not found for player " + player.getName() +
                            ". Available waypoints: " + playerWaypointMap.keySet());
                    return;
                }

                if (editingMode.equals("rename")) {
                    handleWaypointRename(player, waypointName, message, playerWaypointMap);
                } else if (editingMode.equals("coordinates")) {
                    handleCoordinateEdit(player, waypointName, message, playerWaypointMap);
                }

                playerEditingSessions.remove(playerId);

            } catch (Exception e) {
                plugin.getLogger().severe("Error processing waypoint edit for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                playerEditingSessions.remove(playerId);
                sendMessage(player, ChatColor.RED + "An error occurred while processing your edit.");
            }
        });
    }

    private void handleWaypointRename(Player player, String oldName, String newName, Map<String, Location> playerWaypointMap) {
        if (newName.isEmpty()) {
            sendMessage(player, ChatColor.RED + "Waypoint name cannot be empty!");
            return;
        }

        if (newName.length() > 32) {
            sendMessage(player, ChatColor.RED + "Waypoint name is too long! Maximum 32 characters.");
            return;
        }

        newName = newName.replaceAll("\\s+", "_");

        if (playerWaypointMap.containsKey(newName)) {
            sendMessage(player, ChatColor.RED + "You already have a waypoint with that name!");
            return;
        }

        Location location = playerWaypointMap.get(oldName);
        if (location == null) {
            sendMessage(player, ChatColor.RED + "Original waypoint location not found!");
            return;
        }

        playerWaypointMap.remove(oldName);
        playerWaypointMap.put(newName, location);

        saveWaypoints();
        sendMessage(player, ChatColor.GREEN + "Waypoint renamed from '" + oldName + "' to '" + newName + "'!");

    }

    private void handleCoordinateEdit(Player player, String waypointName, String coordinatesStr, Map<String, Location> playerWaypointMap) {
        try {
            String[] parts = coordinatesStr.split("\\s+");
            if (parts.length != 3) {
                sendMessage(player, ChatColor.RED + "Invalid format! Use: x y z (example: 100 64 -200)");
                return;
            }

            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);

            if (y < -2048 || y > 2048) {
                sendMessage(player, ChatColor.RED + "Y coordinate must be between -2048 and 2048!");
                return;
            }

            Location oldLocation = playerWaypointMap.get(waypointName);
            World world = (oldLocation != null && oldLocation.getWorld() != null) ?
                    oldLocation.getWorld() : player.getWorld();
            Location newLocation = new Location(world, x, y, z);

            if (Objects.equals(activeWaypoints.get(player.getUniqueId()), oldLocation)) {
                removeActiveWaypoint(player);
            }

            playerWaypointMap.put(waypointName, newLocation);
            saveWaypoints();

            sendMessage(player, ChatColor.GREEN + "Waypoint '" + waypointName + "' coordinates updated!");
            sendMessage(player, ChatColor.GRAY + "New location: " +
                    String.format("%.1f, %.1f, %.1f", x, y, z) + " in " + world.getName());

        } catch (NumberFormatException e) {
            sendMessage(player, ChatColor.RED + "Invalid coordinates! Please use numbers only.");
            sendMessage(player, ChatColor.YELLOW + "Example: 100 64 -200");
        }
    }

    private void startWaypointTask(Player player, String waypointName) {
        int taskId = new BukkitRunnable() {
            private double initialDistance = -1;
            private Location lastPlayerLocation = null;
            private int distanceUpdateCounter = 0;
            private int visibilityCheckCounter = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    removeActiveWaypoint(player);
                    return;
                }

                Location playerLoc = player.getLocation();
                Location targetLoc = activeWaypoints.get(player.getUniqueId());

                if (targetLoc == null) {
                    cancel();
                    return;
                }

                if (!playerLoc.getWorld().equals(targetLoc.getWorld())) {
                    sendMessage(player, ChatColor.RED + "Your waypoint is in a different world!");
                    removeActiveWaypoint(player);
                    return;
                }

                List<ArmorStand> arrowParts = playerArrowParts.get(player.getUniqueId());
                if (arrowParts == null) {
                    cancel();
                    return;
                }

                double currentDistance = playerLoc.distance(targetLoc);

                if (initialDistance < 0) {
                    initialDistance = currentDistance;
                }

                distanceUpdateCounter++;
                if (distanceUpdateCounter >= 10 && isDistanceEnabled(player)) {
                    updateDistanceDisplay(player, currentDistance, initialDistance);
                    distanceUpdateCounter = 0;
                }

                if (currentDistance <= 3.0) {
                    sendMessage(player, ChatColor.GREEN + "You have reached your waypoint!");

                    if (shouldPlaySound(player)) {
                        String soundName = playerArrivalSounds.getOrDefault(player.getUniqueId(), defaultArrivalSound);
                        String soundKey = arrivalSounds.getOrDefault(soundName, "entity.player.levelup");
                        player.playSound(playerLoc, soundKey, 1.0f, 1.0f);
                    }

                    if (shouldShowParticles(player)) {
                        String particleType = playerArrivalParticles.getOrDefault(player.getUniqueId(), defaultArrivalParticle);
                        spawnArrivalParticle(player, targetLoc, particleType);
                    }

                    removeActiveWaypoint(player);
                } else {
                    visibilityCheckCounter++;
                    if (visibilityCheckCounter >= 20) {
                        visibilityCheckCounter = 0;
                        Vector direction = targetLoc.toVector().subtract(playerLoc.toVector()).normalize();
                        Location arrowLoc = playerLoc.clone()
                                .add(direction.clone().multiply(forwardOffset))
                                .add(0, arrowHeight, 0);

                        boolean arrowSuffocating = isArrowSuffocating(player, arrowLoc);
                        boolean canSeeArrow = canPlayerSeeArrow(player, arrowLoc);
                        boolean shouldUseActionBar = arrowSuffocating || !canSeeArrow;

                        Boolean currentlyUsingActionBar = playerUsingActionBar.get(player.getUniqueId());

                        if (shouldUseActionBar && (currentlyUsingActionBar == null || !currentlyUsingActionBar)) {
                            playerUsingActionBar.put(player.getUniqueId(), true);
                            hideArrowParts(arrowParts);
                            if (isGuidanceMessagesEnabled(player)) {
                                sendMessage(player, ChatColor.YELLOW + "Arrow blocked - switching to compass guidance");
                            }
                        } else if (!shouldUseActionBar && currentlyUsingActionBar != null && currentlyUsingActionBar) {
                            playerUsingActionBar.put(player.getUniqueId(), false);
                            showArrowParts(player, arrowParts, targetLoc);
                            if (isGuidanceMessagesEnabled(player)) {
                                sendMessage(player, ChatColor.GREEN + "Arrow visible again - switching to arrow guidance");
                            }
                        }
                    }

                    Boolean usingActionBar = playerUsingActionBar.get(player.getUniqueId());
                    if (usingActionBar != null && usingActionBar) {
                        if (distanceUpdateCounter % 10 == 0) {
                            sendActionBarGuidance(player, targetLoc);
                        }
                    } else {
                        if (lastPlayerLocation == null ||
                                lastPlayerLocation.distance(playerLoc) > 0.1 ||
                                Math.abs(lastPlayerLocation.getYaw() - playerLoc.getYaw()) > 5) {

                            updateArrowPosition(player, arrowParts, targetLoc);
                            lastPlayerLocation = playerLoc.clone();
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();

        taskIds.put(player.getUniqueId(), taskId);
    }

    private List<ArmorStand> createArrowArt(Player player) {
        List<ArmorStand> arrowParts = new ArrayList<>();
        Location baseLoc = player.getLocation().add(0, arrowHeight, 0);

        String designName = playerArrowDesigns.getOrDefault(player.getUniqueId(), defaultArrowDesign);
        ArrowDesign design = arrowDesigns.getOrDefault(designName,
                new ArrowDesign("default", defaultArrowMaterial, defaultArrowHeadMaterial,
                        defaultTailMaterial, false, "none", 0));

        Material mainMaterial = design.getMainMaterial();
        Material headMaterial = design.getHeadMaterial();
        Material tailMaterial = design.getTailMaterial();
        boolean glowing = design.isGlowing();

        for (int i = 0; i < 5; i++) {
            ArmorStand stand = createArmorStandWithBlock(baseLoc, mainMaterial);
            if (glowing) stand.setGlowing(true);
            arrowParts.add(stand);
        }

        for (int i = 0; i < 7; i++) {
            ArmorStand stand = createArmorStandWithBlock(baseLoc, headMaterial);
            if (glowing) stand.setGlowing(true);
            arrowParts.add(stand);
        }

        for (int i = 0; i < 4; i++) {
            ArmorStand stand = createArmorStandWithBlock(baseLoc, mainMaterial);
            if (glowing) stand.setGlowing(true);
            arrowParts.add(stand);
        }

        for (int i = 0; i < 6; i++) {
            ArmorStand stand = createArmorStandWithBlock(baseLoc, tailMaterial);
            if (glowing) stand.setGlowing(true);
            arrowParts.add(stand);
        }

        return arrowParts;
    }

    private ArmorStand createArmorStandWithBlock(Location loc, Material material) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setBasePlate(false);
        stand.setHelmet(new ItemStack(material));
        return stand;
    }

    private void updateArrowPosition(Player player, List<ArmorStand> arrowParts, Location target) {
        Location playerLoc = player.getLocation();
        Vector direction = target.toVector().subtract(playerLoc.toVector()).normalize();

        Location baseLoc = playerLoc.clone()
                .add(direction.clone().multiply(forwardOffset))
                .add(0, arrowHeight, 0);

        Vector perp = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        String designName = playerArrowDesigns.getOrDefault(player.getUniqueId(), defaultArrowDesign);
        ArrowDesign design = arrowDesigns.getOrDefault(designName,
                new ArrowDesign("default", defaultArrowMaterial, defaultArrowHeadMaterial,
                        defaultTailMaterial, false, "none", 0));

        int patternType = design.getPatternType();
        String particleEffect = design.getParticleEffect();

        if (!particleEffect.equals("none") && player.getTicksLived() % 5 == 0) {
            try {
                player.getWorld().spawnParticle(Particle.valueOf(particleEffect),
                        baseLoc, 3, 0.2, 0.2, 0.2, 0.05);
            } catch (IllegalArgumentException ignored) {
            }
        }

        switch (patternType) {
            case 1 -> updateSpiralPattern(arrowParts, baseLoc, direction, perp, player.getTicksLived());
            case 2 -> updateZigzagPattern(arrowParts, baseLoc, direction, perp, player.getTicksLived());
            case 3 -> updateEmeraldPattern(arrowParts, baseLoc, direction, perp, player.getTicksLived());
            default -> updateDefaultPattern(arrowParts, baseLoc, direction, perp);
        }
    }

    private void updateDefaultPattern(List<ArmorStand> arrowParts, Location baseLoc, Vector direction, Vector perp) {
        int index = 0;

        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(arrowSpacing))
                .add(perp.clone().multiply(arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(arrowSpacing))
                .add(perp.clone().multiply(-arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(arrowSpacing * 2)));
        arrowParts.get(index++).teleport(baseLoc.clone());

        double headStart = 1.0;
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart + arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart))
                .add(perp.clone().multiply(arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart))
                .add(perp.clone().multiply(-arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing))
                .add(perp.clone().multiply(arrowSpacing * 1.5)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing))
                .add(perp.clone().multiply(-arrowSpacing * 1.5)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing * 2))
                .add(perp.clone().multiply(arrowSpacing * 2)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing * 2))
                .add(perp.clone().multiply(-arrowSpacing * 2)));

        double decoratorBase = arrowSpacing;
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(decoratorBase))
                .add(perp.clone().multiply(arrowSpacing * 1.5)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(decoratorBase))
                .add(perp.clone().multiply(-arrowSpacing * 1.5)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(decoratorBase * 2))
                .add(perp.clone().multiply(arrowSpacing * 1.5)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(decoratorBase * 2))
                .add(perp.clone().multiply(-arrowSpacing * 1.5)));

        double tailStart = -arrowSpacing;
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(tailStart)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(tailStart * 1.5)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(tailStart))
                .add(perp.clone().multiply(arrowSpacing * 1.5)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(tailStart * 2))
                .add(perp.clone().multiply(arrowSpacing * 2)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(tailStart))
                .add(perp.clone().multiply(-arrowSpacing * 1.5)));
        arrowParts.get(index).teleport(baseLoc.clone().add(direction.clone().multiply(tailStart * 2))
                .add(perp.clone().multiply(-arrowSpacing * 2)));
    }

    private void updateSpiralPattern(List<ArmorStand> arrowParts, Location baseLoc, Vector direction, Vector perp, int ticks) {
        int index = 0;
        double spiralRadius = 0.8;
        double spiralStep = Math.PI / 4;
        double rotationFactor = (ticks % 80) / 40.0 * Math.PI;

        for (int i = 0; i < 5; i++) {
            double angle = i * spiralStep + rotationFactor;
            Vector offset = perp.clone().multiply(Math.sin(angle) * spiralRadius);
            offset.setY(Math.cos(angle) * spiralRadius * 0.5);

            Location targetLoc = baseLoc.clone()
                    .add(direction.clone().multiply(i * arrowSpacing * 0.5))
                    .add(offset);

            ArmorStand stand = arrowParts.get(index++);
            Location currentLoc = stand.getLocation();
            Location smoothLoc = currentLoc.clone().add(
                    (targetLoc.getX() - currentLoc.getX()) * 0.3,
                    (targetLoc.getY() - currentLoc.getY()) * 0.3,
                    (targetLoc.getZ() - currentLoc.getZ()) * 0.3
            );
            stand.teleport(smoothLoc);
        }

        double headStart = 2.5;
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart + arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart))
                .add(perp.clone().multiply(arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart))
                .add(perp.clone().multiply(-arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing))
                .add(perp.clone().multiply(arrowSpacing * 1.5)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing))
                .add(perp.clone().multiply(-arrowSpacing * 1.5)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing * 2))
                .add(perp.clone().multiply(arrowSpacing * 2)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing * 2))
                .add(perp.clone().multiply(-arrowSpacing * 2)));

        for (int i = 0; i < 4; i++) {
            double decorAngle = (ticks % 60) / 30.0 * Math.PI + i * Math.PI / 2;
            Vector offset = perp.clone().multiply(Math.sin(decorAngle) * arrowSpacing * 0.8);
            offset.setY(Math.cos(decorAngle) * arrowSpacing * 0.3);
            arrowParts.get(index++).teleport(baseLoc.clone()
                    .add(direction.clone().multiply(arrowSpacing * 2))
                    .add(offset));
        }

        double tailStart = -arrowSpacing;
        for (int i = 0; i < 6; i++) {
            double angle = i * spiralStep + rotationFactor * 2;
            double radius = arrowSpacing * (0.8 + i * 0.1);
            Vector offset = perp.clone().multiply(Math.sin(angle) * radius);
            offset.setY(Math.cos(angle) * radius * 0.2);

            Location targetLoc = baseLoc.clone()
                    .add(direction.clone().multiply(tailStart - i * arrowSpacing * 0.3))
                    .add(offset);

            ArmorStand stand = arrowParts.get(index++);
            Location currentLoc = stand.getLocation();
            Location smoothLoc = currentLoc.clone().add(
                    (targetLoc.getX() - currentLoc.getX()) * 0.4,
                    (targetLoc.getY() - currentLoc.getY()) * 0.4,
                    (targetLoc.getZ() - currentLoc.getZ()) * 0.4
            );
            stand.teleport(smoothLoc);
        }
    }

    private void updateZigzagPattern(List<ArmorStand> arrowParts, Location baseLoc, Vector direction, Vector perp, int ticks) {
        int index = 0;
        double zigzagWidth = 1.0;
        double zigzagFactor = Math.sin((ticks % 60) / 30.0 * Math.PI) * zigzagWidth;

        for (int i = 0; i < 5; i++) {
            double sideOffset = (i % 2 == 0 ? 1 : -1) * zigzagFactor;
            Location targetLoc = baseLoc.clone()
                    .add(direction.clone().multiply(i * arrowSpacing * 0.5))
                    .add(perp.clone().multiply(sideOffset));

            ArmorStand stand = arrowParts.get(index++);
            Location currentLoc = stand.getLocation();
            Location smoothLoc = currentLoc.clone().add(
                    (targetLoc.getX() - currentLoc.getX()) * 0.3,
                    (targetLoc.getY() - currentLoc.getY()) * 0.3,
                    (targetLoc.getZ() - currentLoc.getZ()) * 0.3
            );
            stand.teleport(smoothLoc);
        }

        double headStart = 2.5;
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart + arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart))
                .add(perp.clone().multiply(arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart))
                .add(perp.clone().multiply(-arrowSpacing)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing))
                .add(perp.clone().multiply(arrowSpacing * 1.5)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing))
                .add(perp.clone().multiply(-arrowSpacing * 1.5)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing * 2))
                .add(perp.clone().multiply(arrowSpacing * 2)));
        arrowParts.get(index++).teleport(baseLoc.clone().add(direction.clone().multiply(headStart - arrowSpacing * 2))
                .add(perp.clone().multiply(-arrowSpacing * 2)));

        for (int i = 0; i < 4; i++) {
            double sideOffset = (i % 2 == 0 ? 0.4 : -0.4) * Math.sin((ticks % 40) / 20.0 * Math.PI);
            Location offset = baseLoc.clone().add(direction.clone().multiply(arrowSpacing * (i * 0.5 + 1)));
            offset.add(perp.clone().multiply(sideOffset));
            offset.add(0, sideOffset * 0.3, 0);
            arrowParts.get(index++).teleport(offset);
        }

        double tailStart = -arrowSpacing;
        for (int i = 0; i < 6; i++) {
            double sideOffset = (i % 2 == 0 ? 1 : -1) * zigzagFactor * (0.8 + i * 0.15);
            double heightOffset = (i % 2 == 0 ? 0.2 : -0.2) * Math.sin((ticks % 30) / 15.0 * Math.PI);

            Location targetLoc = baseLoc.clone().add(direction.clone().multiply(tailStart - i * arrowSpacing * 0.3));
            targetLoc.add(perp.clone().multiply(sideOffset));
            if (i > 0) {
                targetLoc.add(0, heightOffset, 0);
            }

            ArmorStand stand = arrowParts.get(index++);
            Location currentLoc = stand.getLocation();
            Location smoothLoc = currentLoc.clone().add(
                    (targetLoc.getX() - currentLoc.getX()) * 0.4,
                    (targetLoc.getY() - currentLoc.getY()) * 0.4,
                    (targetLoc.getZ() - currentLoc.getZ()) * 0.4
            );
            stand.teleport(smoothLoc);
        }
    }

    private void updateEmeraldPattern(List<ArmorStand> arrowParts, Location baseLoc, Vector direction, Vector perp, int ticks) {
        int index = 0;

        for (int i = 0; i < 5; i++) {
            double bodyDistance = i * arrowSpacing * 0.5;

            Location targetLoc = baseLoc.clone()
                    .add(direction.clone().multiply(bodyDistance));

            arrowParts.get(index++).teleport(targetLoc);
        }

        double headStart = 2.5;

        arrowParts.get(index++).teleport(baseLoc.clone()
                .add(direction.clone().multiply(headStart + arrowSpacing)));

        arrowParts.get(index++).teleport(baseLoc.clone()
                .add(direction.clone().multiply(headStart))
                .add(perp.clone().multiply(arrowSpacing * 0.8)));

        arrowParts.get(index++).teleport(baseLoc.clone()
                .add(direction.clone().multiply(headStart))
                .add(perp.clone().multiply(-arrowSpacing * 0.8)));

        arrowParts.get(index++).teleport(baseLoc.clone()
                .add(direction.clone().multiply(headStart - arrowSpacing * 0.6))
                .add(perp.clone().multiply(arrowSpacing * 1.2)));

        arrowParts.get(index++).teleport(baseLoc.clone()
                .add(direction.clone().multiply(headStart - arrowSpacing * 0.6))
                .add(perp.clone().multiply(-arrowSpacing * 1.2)));

        arrowParts.get(index++).teleport(baseLoc.clone()
                .add(direction.clone().multiply(headStart - arrowSpacing * 1.2))
                .add(perp.clone().multiply(arrowSpacing * 1.6)));

        arrowParts.get(index++).teleport(baseLoc.clone()
                .add(direction.clone().multiply(headStart - arrowSpacing * 1.2))
                .add(perp.clone().multiply(-arrowSpacing * 1.6)));

        for (int i = 0; i < 4; i++) {
            double fletchDistance = arrowSpacing * (1.0 + i * 0.3);
            double fletchWidth = arrowSpacing * (1.2 - i * 0.1);

            Vector fletchOffset = (i % 2 == 0) ?
                    perp.clone().multiply(fletchWidth) :
                    perp.clone().multiply(-fletchWidth);

            arrowParts.get(index++).teleport(baseLoc.clone()
                    .add(direction.clone().multiply(fletchDistance))
                    .add(fletchOffset));
        }

        double nockStart = -arrowSpacing * 0.4;
        for (int i = 0; i < 6; i++) {
            double nockDistance = nockStart - i * arrowSpacing * 0.4;
            double nockWidth = arrowSpacing * (0.6 + i * 0.1);

            Vector nockOffset = (i % 2 == 0) ?
                    perp.clone().multiply(nockWidth) :
                    perp.clone().multiply(-nockWidth);

            arrowParts.get(index++).teleport(baseLoc.clone()
                    .add(direction.clone().multiply(nockDistance))
                    .add(nockOffset));
        }
    }

    void removeActiveWaypoint(Player player) {
        UUID playerId = player.getUniqueId();

        Integer taskId = taskIds.remove(playerId);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }

        List<ArmorStand> arrowParts = playerArrowParts.remove(playerId);
        if (arrowParts != null) {
            arrowParts.forEach(ArmorStand::remove);
        }

        if (isDistanceEnabled(player)) {
            restoreOriginalExp(player);
        }

        playerUsingActionBar.remove(playerId);

        activeWaypoints.remove(playerId);
    }

    private void loadUserPreferences() {
        userPrefsFile = new File(plugin.getDataFolder(), "user_preferences.yml");
        if (!userPrefsFile.exists()) {
            try {
                userPrefsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create user_preferences.yml file!");
                e.printStackTrace();
            }
        }

        userPrefsConfig = YamlConfiguration.loadConfiguration(userPrefsFile);

        ConfigurationSection designsSection = userPrefsConfig.getConfigurationSection("arrow_designs");
        if (designsSection != null) {
            for (String uuidStr : designsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String designName = designsSection.getString(uuidStr);
                    if (designName != null && arrowDesigns.containsKey(designName)) {
                        playerArrowDesigns.put(uuid, designName);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in user_preferences.yml: " + uuidStr);
                }
            }
        }

        ConfigurationSection soundsSection = userPrefsConfig.getConfigurationSection("arrival_sounds");
        if (soundsSection != null) {
            for (String uuidStr : soundsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String soundName = soundsSection.getString(uuidStr);
                    if (soundName != null && arrivalSounds.containsKey(soundName)) {
                        playerArrivalSounds.put(uuid, soundName);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in user_preferences.yml: " + uuidStr);
                }
            }
        }

        ConfigurationSection distanceSection = userPrefsConfig.getConfigurationSection("distance_display");
        if (distanceSection != null) {
            for (String uuidStr : distanceSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    boolean enabled = distanceSection.getBoolean(uuidStr);
                    playerDistanceEnabled.put(uuid, enabled);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in user_preferences.yml: " + uuidStr);
                }
            }
        }

        ConfigurationSection guidanceSection = userPrefsConfig.getConfigurationSection("guidance_messages");
        if (guidanceSection != null) {
            for (String uuidStr : guidanceSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    boolean enabled = guidanceSection.getBoolean(uuidStr);
                    playerGuidanceMessagesEnabled.put(uuid, enabled);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in user_preferences.yml: " + uuidStr);
                }
            }
        }

        ConfigurationSection silentSection = userPrefsConfig.getConfigurationSection("silent_mode");
        if (silentSection != null) {
            for (String uuidStr : silentSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    boolean enabled = silentSection.getBoolean(uuidStr);
                    playerSilentMode.put(uuid, enabled);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in user_preferences.yml: " + uuidStr);
                }
            }
        }

        ConfigurationSection soundDisabledSection = userPrefsConfig.getConfigurationSection("sound_disabled");
        if (soundDisabledSection != null) {
            for (String uuidStr : soundDisabledSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    boolean disabled = soundDisabledSection.getBoolean(uuidStr);
                    playerSoundDisabled.put(uuid, disabled);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in user_preferences.yml: " + uuidStr);
                }
            }
        }

        ConfigurationSection particlesDisabledSection = userPrefsConfig.getConfigurationSection("particles_disabled");
        if (particlesDisabledSection != null) {
            for (String uuidStr : particlesDisabledSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    boolean disabled = particlesDisabledSection.getBoolean(uuidStr);
                    playerParticlesDisabled.put(uuid, disabled);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in user_preferences.yml: " + uuidStr);
                }
            }
        }


        ConfigurationSection particlesSection = userPrefsConfig.getConfigurationSection("arrival_particles");
        if (particlesSection != null) {
            for (String uuidStr : particlesSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String particleName = particlesSection.getString(uuidStr);
                    if (particleName != null && arrivalParticles.containsKey(particleName)) {
                        playerArrivalParticles.put(uuid, particleName);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in user_preferences.yml: " + uuidStr);
                }
            }
        }
    }

    private void setupArrivalParticles() {
        arrivalParticles.put("FIREWORK", "FIREWORK");
        arrivalParticles.put("FLAME", "FLAME");
        arrivalParticles.put("SOUL", "SOUL");
        arrivalParticles.put("HAPPY_VILLAGER", "HAPPY_VILLAGER");
        arrivalParticles.put("HEART", "HEART");
        arrivalParticles.put("NOTE", "NOTE");
        arrivalParticles.put("ENCHANT", "ENCHANT");
        arrivalParticles.put("EXPLOSION", "EXPLOSION");
        arrivalParticles.put("TOTEM_OF_UNDYING", "TOTEM_OF_UNDYING");
        arrivalParticles.put("END_ROD", "END_ROD");
        arrivalParticles.put("DRAGON_BREATH", "DRAGON_BREATH");
        arrivalParticles.put("BUBBLE_POP", "BUBBLE_POP");
        arrivalParticles.put("CAMPFIRE_COSY_SMOKE", "CAMPFIRE_COSY_SMOKE");
        arrivalParticles.put("ELECTRIC_SPARK", "ELECTRIC_SPARK");
        arrivalParticles.put("GLOW", "GLOW");
        arrivalParticles.put("SCRAPE", "SCRAPE");
        arrivalParticles.put("SCULK_SOUL", "SCULK_SOUL");
        arrivalParticles.put("SONIC_BOOM", "SONIC_BOOM");
        arrivalParticles.put("CHERRY_LEAVES", "CHERRY_LEAVES");
    }

    private void handleArrivalParticleCommand(Player player, String[] args) {
        if (!player.hasPermission("jwaypoints.particles")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change arrival particles!");
            return;
        }

        if (args.length != 2) {
            sendMessage(player, ChatColor.RED + "Usage: /waypoint particle <name>");
            return;
        }

        String particleName = args[1].toUpperCase();
        if (!arrivalParticles.containsKey(particleName)) {
            sendMessage(player, ChatColor.RED + "Unknown particle! Use /waypoint particles to see available particles.");
            return;
        }

        playerArrivalParticles.put(player.getUniqueId(), particleName);
        saveUserPreferences();

        spawnArrivalParticle(player, player.getLocation(), particleName);

        sendMessage(player, ChatColor.GREEN + "Arrival particle changed to '" + particleName + "'!");
    }

    private void listArrivalParticles(Player player) {
        if (!player.hasPermission("jwaypoints.particles")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change arrival particles!");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "===== Available Arrival Particles =====");

        for (String particleName : arrivalParticles.keySet()) {
            player.sendMessage(ChatColor.GREEN + particleName);
        }

        String currentParticle = playerArrivalParticles.getOrDefault(player.getUniqueId(), defaultArrivalParticle);
        player.sendMessage(ChatColor.YELLOW + "Your current particle: " + ChatColor.GREEN + currentParticle);
        player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/waypoint particle <name>" +
                ChatColor.YELLOW + " to change your particle.");
        player.sendMessage(ChatColor.YELLOW + "Tip: The particle will appear when you reach your waypoint destination!");
    }

    private void spawnArrivalParticle(Player player, Location location, String particleType) {
        try {
            Particle particle = Particle.valueOf(particleType);
            Location spawnLoc = location.clone().add(0, 1, 0);

            switch (particleType) {
                case "FIREWORK":
                    player.getWorld().spawnParticle(particle, spawnLoc, 50, 1.0, 1.0, 1.0, 0.1);
                    break;
                case "FLAME":
                case "SOUL":
                    player.getWorld().spawnParticle(particle, spawnLoc, 30, 0.5, 0.5, 0.5, 0.02);
                    break;
                case "HAPPY_VILLAGER":
                case "HEART":
                    player.getWorld().spawnParticle(particle, spawnLoc, 20, 1.0, 1.0, 1.0, 0.1);
                    break;
                case "NOTE":
                    player.getWorld().spawnParticle(particle, spawnLoc, 15, 1.0, 1.0, 1.0, 0.1);
                    break;
                case "ENCHANT":
                    player.getWorld().spawnParticle(particle, spawnLoc, 40, 1.5, 1.5, 1.5, 0.1);
                    break;
                case "EXPLOSION":
                    player.getWorld().spawnParticle(particle, spawnLoc, 5, 0.5, 0.5, 0.5, 0.1);
                    break;
                case "TOTEM_OF_UNDYING":
                    player.getWorld().spawnParticle(particle, spawnLoc, 25, 1.0, 1.0, 1.0, 0.1);
                    break;
                case "END_ROD":
                    player.getWorld().spawnParticle(particle, spawnLoc, 20, 0.8, 0.8, 0.8, 0.05);
                    break;
                case "DRAGON_BREATH":
                    player.getWorld().spawnParticle(particle, spawnLoc, 30, 1.0, 1.0, 1.0, 0.02);
                    break;
                case "SONIC_BOOM":
                    player.getWorld().spawnParticle(particle, spawnLoc, 1, 0, 0, 0, 0);
                    break;
                default:
                    player.getWorld().spawnParticle(particle, spawnLoc, 25, 0.8, 0.8, 0.8, 0.1);
                    break;
            }
        } catch (IllegalArgumentException e) {
            player.getWorld().spawnParticle(Particle.FIREWORK, location.clone().add(0, 1, 0), 50, 1.0, 1.0, 1.0, 0.1);
        }
    }

    private void openArrivalParticlesGUI(Player player) {
        if (!player.hasPermission("jwaypoints.particles")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change arrival particles!");
            return;
        }

        Inventory gui = Bukkit.createInventory(player, 36, ChatColor.LIGHT_PURPLE + "Arrival Particles");
        int slot = 0;

        for (Map.Entry<String, String> entry : arrivalParticles.entrySet()) {
            String particleName = entry.getKey();

            Material particleMaterial = getParticleMaterial(particleName);

            ItemStack item = new ItemStack(particleMaterial);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + particleName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Particle effect shown when");
            lore.add(ChatColor.GRAY + "you reach your destination");
            lore.add("");

            switch (particleName) {
                case "FIREWORK":
                    lore.add(ChatColor.YELLOW + "✨ Explosive celebration!");
                    break;
                case "FLAME":
                    lore.add(ChatColor.RED + "🔥 Fiery arrival");
                    break;
                case "SOUL":
                    lore.add(ChatColor.AQUA + "👻 Mystical soul flames");
                    break;
                case "HAPPY_VILLAGER":
                    lore.add(ChatColor.GREEN + "😊 Cheerful green sparkles");
                    break;
                case "HEART":
                    lore.add(ChatColor.LIGHT_PURPLE + "💖 Lovely pink hearts");
                    break;
                case "NOTE":
                    lore.add(ChatColor.BLUE + "🎵 Musical notes");
                    break;
                case "ENCHANT":
                    lore.add(ChatColor.AQUA + "✨ Magical enchantment");
                    break;
                case "EXPLOSION":
                    lore.add(ChatColor.RED + "💥 Powerful blast");
                    break;
                case "TOTEM_OF_UNDYING":
                    lore.add(ChatColor.GOLD + "🏆 Golden celebration");
                    break;
                case "SONIC_BOOM":
                    lore.add(ChatColor.DARK_BLUE + "💨 Warden's sonic blast");
                    break;
            }

            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to select and preview");

            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.setItem(slot++, item);

            if (slot >= 27) break;
        }

        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Back to Settings");
        backItem.setItemMeta(backMeta);
        gui.setItem(35, backItem);

        player.openInventory(gui);
    }

    private Material getParticleMaterial(String particleName) {
        return switch (particleName) {
            case "FIREWORK" -> Material.FIREWORK_ROCKET;
            case "FLAME" -> Material.FIRE_CHARGE;
            case "SOUL" -> Material.SOUL_TORCH;
            case "HAPPY_VILLAGER" -> Material.EMERALD;
            case "HEART" -> Material.PINK_DYE;
            case "NOTE" -> Material.NOTE_BLOCK;
            case "ENCHANT" -> Material.ENCHANTING_TABLE;
            case "EXPLOSION" -> Material.TNT;
            case "TOTEM_OF_UNDYING" -> Material.TOTEM_OF_UNDYING;
            case "END_ROD" -> Material.END_ROD;
            case "DRAGON_BREATH" -> Material.DRAGON_BREATH;
            case "BUBBLE_POP" -> Material.BUBBLE_CORAL;
            case "CAMPFIRE_COSY_SMOKE" -> Material.CAMPFIRE;
            case "ELECTRIC_SPARK" -> Material.LIGHTNING_ROD;
            case "GLOW" -> Material.GLOW_INK_SAC;
            case "SCRAPE" -> Material.COPPER_INGOT;
            case "SCULK_SOUL" -> Material.SCULK;
            case "SONIC_BOOM" -> Material.ECHO_SHARD;
            case "CHERRY_LEAVES" -> Material.CHERRY_LEAVES;
            default -> Material.GLOWSTONE_DUST;
        };
    }

    private boolean isArrowSuffocating(Player player, Location arrowLocation) {
        World world = arrowLocation.getWorld();
        if (world == null) return false;

        Location checkLoc = arrowLocation.clone();

        for (double x = -0.5; x <= 0.5; x += 0.5) {
            for (double y = -0.5; y <= 0.5; y += 0.5) {
                for (double z = -0.5; z <= 0.5; z += 0.5) {
                    Location testLoc = checkLoc.clone().add(x, y, z);
                    Material blockType = world.getBlockAt(testLoc).getType();

                    if (blockType == Material.AIR ||
                            blockType == Material.WATER ||
                            blockType == Material.LAVA ||
                            !blockType.isSolid()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean canPlayerSeeArrow(Player player, Location arrowLocation) {
        Location playerEye = player.getEyeLocation();

        Vector direction = arrowLocation.toVector().subtract(playerEye.toVector()).normalize();
        double distance = playerEye.distance(arrowLocation);

        for (double d = 0.5; d < distance; d += 0.5) {
            Location checkLoc = playerEye.clone().add(direction.clone().multiply(d));
            Material blockType = checkLoc.getWorld().getBlockAt(checkLoc).getType();

            if (blockType.isSolid() &&
                    blockType != Material.GLASS &&
                    blockType != Material.GLASS_PANE &&
                    !blockType.name().contains("GLASS")) {
                return false;
            }
        }

        return true;
    }

    private void sendActionBarGuidance(Player player, Location target) {
        Location playerLoc = player.getLocation();
        Vector direction = target.toVector().subtract(playerLoc.toVector());

        Vector horizontal = new Vector(direction.getX(), 0, direction.getZ()).normalize();

        Vector playerFacing = playerLoc.getDirection();
        playerFacing.setY(0);
        playerFacing.normalize();

        double dot = playerFacing.dot(horizontal);
        double cross = playerFacing.getX() * horizontal.getZ() - playerFacing.getZ() * horizontal.getX();

        String arrow;
        String directionText;

        if (dot > 0.8) {
            arrow = "⬆";
            directionText = "Forward";
        } else if (dot < -0.8) {
            arrow = "⬇";
            directionText = "Behind";
        } else if (cross > 0) {
            arrow = "➡";
            directionText = "Right";
        } else {
            arrow = "⬅";
            directionText = "Left";
        }

        double verticalDistance = target.getY() - playerLoc.getY();
        String verticalArrow = "";
        if (verticalDistance > 2) {
            verticalArrow = " ⬆ Up";
        } else if (verticalDistance < -2) {
            verticalArrow = " ⬇ Down";
        }

        double distance = playerLoc.distance(target);

        String actionBarMessage = ChatColor.YELLOW + arrow + " " +
                ChatColor.WHITE + directionText +
                verticalArrow +
                ChatColor.GRAY + " | " +
                ChatColor.GREEN + String.format("%.1fm", distance);

        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionBarMessage));
    }



    private void hideArrowParts(List<ArmorStand> arrowParts) {
        if (arrowParts == null) return;

        for (ArmorStand stand : arrowParts) {
            if (stand != null && !stand.isDead()) {
                Location hiddenLocation = stand.getLocation().clone();
                hiddenLocation.setY(-100);
                stand.teleport(hiddenLocation);
            }
        }
    }

    private void showArrowParts(Player player, List<ArmorStand> arrowParts, Location target) {
        if (arrowParts == null) return;

        updateArrowPosition(player, arrowParts, target);
    }

    private void handleGuidanceMessagesCommand(Player player) {
        if (!player.hasPermission("jwaypoints.settings")) {
            sendMessage(player, ChatColor.RED + "You don't have permission to change guidance message settings!");
            return;
        }

        boolean currentlyEnabled = isGuidanceMessagesEnabled(player);
        playerGuidanceMessagesEnabled.put(player.getUniqueId(), !currentlyEnabled);
        saveUserPreferences();

        if (!currentlyEnabled) {
            sendMessage(player, ChatColor.GREEN + "Guidance switch messages enabled!");
            sendMessage(player, ChatColor.GRAY + "You will now see messages when switching between arrow and compass guidance.");
        } else {
            sendMessage(player, ChatColor.RED + "Guidance switch messages disabled!");
            sendMessage(player, ChatColor.GRAY + "Guidance mode switches will now be silent.");
        }
    }

    private boolean isGuidanceMessagesEnabled(Player player) {
        return playerGuidanceMessagesEnabled.getOrDefault(player.getUniqueId(), guidanceMessagesDefault);
    }

    private boolean isSilentModeEnabled(Player player) {
        return playerSilentMode.getOrDefault(player.getUniqueId(), silentModeDefault);
    }

    private boolean isSoundDisabled(Player player) {
        return playerSoundDisabled.getOrDefault(player.getUniqueId(), soundDisabledDefault);
    }

    private boolean isParticlesDisabled(Player player) {
        return playerParticlesDisabled.getOrDefault(player.getUniqueId(), particlesDisabledDefault);
    }

    private boolean shouldPlaySound(Player player) {
        return !isSilentModeEnabled(player) && !isSoundDisabled(player);
    }

    private boolean shouldShowParticles(Player player) {
        return !isSilentModeEnabled(player) && !isParticlesDisabled(player);
    }

    public void saveUserPreferences() {
        userPrefsConfig.set("arrow_designs", null);
        userPrefsConfig.set("arrival_sounds", null);
        userPrefsConfig.set("distance_display", null);
        userPrefsConfig.set("guidance_messages", null);
        userPrefsConfig.set("arrival_particles", null);
        userPrefsConfig.set("silent_mode", null);
        userPrefsConfig.set("sound_disabled", null);
        userPrefsConfig.set("particles_disabled", null);

        for (Map.Entry<UUID, String> entry : playerArrowDesigns.entrySet()) {
            userPrefsConfig.set("arrow_designs." + entry.getKey().toString(), entry.getValue());
        }

        for (Map.Entry<UUID, String> entry : playerArrivalSounds.entrySet()) {
            userPrefsConfig.set("arrival_sounds." + entry.getKey().toString(), entry.getValue());
        }

        for (Map.Entry<UUID, Boolean> entry : playerDistanceEnabled.entrySet()) {
            userPrefsConfig.set("distance_display." + entry.getKey().toString(), entry.getValue());
        }

        for (Map.Entry<UUID, Boolean> entry : playerGuidanceMessagesEnabled.entrySet()) {
            userPrefsConfig.set("guidance_messages." + entry.getKey().toString(), entry.getValue());
        }

        for (Map.Entry<UUID, String> entry : playerArrivalParticles.entrySet()) {
            userPrefsConfig.set("arrival_particles." + entry.getKey().toString(), entry.getValue());
        }

        for (Map.Entry<UUID, Boolean> entry : playerSilentMode.entrySet()) {
            userPrefsConfig.set("silent_mode." + entry.getKey().toString(), entry.getValue());
        }

        for (Map.Entry<UUID, Boolean> entry : playerSoundDisabled.entrySet()) {
            userPrefsConfig.set("sound_disabled." + entry.getKey().toString(), entry.getValue());
        }

        for (Map.Entry<UUID, Boolean> entry : playerParticlesDisabled.entrySet()) {
            userPrefsConfig.set("particles_disabled." + entry.getKey().toString(), entry.getValue());
        }

        try {
            userPrefsConfig.save(userPrefsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save user_preferences.yml file!");
            e.printStackTrace();
        }
    }

    public void cleanup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeActiveWaypoint(player);
        }
        saveUserPreferences();
    }
}