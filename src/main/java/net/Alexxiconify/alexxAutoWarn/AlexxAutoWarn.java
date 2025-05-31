package net.Alexxiconify.alexxAutoWarn.AlexxsAutoWarn;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// Note: The STR."..." syntax is a Java 21+ String Template feature.
// If your server uses an older Java version, you'll need to change these to traditional string concatenation.
// e.g., getLogger().severe("World '" + worldName + "' not found for zone definition!");

/**
 * Represents a defined lava alert zone with two corners and a world.
 */
class LavaZone {
 private final String name;
 private final World world;
 private final Location corner1;
 private final Location corner2;

 /**
  * Constructs a new LavaZone.
  * @param name The unique name of the zone.
  * @param world The world the zone is in.
  * @param corner1 The first corner of the zone.
  * @param corner2 The second corner of the zone.
  */
 public LavaZone(String name, @NotNull World world, @NotNull Location corner1, @NotNull Location corner2) {
  this.name = name;
  this.world = world;
  this.corner1 = corner1;
  this.corner2 = corner2;
 }

 public String getName() { return name; }
 public World getWorld() { return world; }
 public Location getCorner1() { return corner1; }
 public Location getCorner2() { return corner2; }

 /**
  * Checks if a given location is within this zone.
  * @param loc The location to check.
  * @return true if the location is within the zone, false otherwise.
  */
 public boolean contains(@NotNull Location loc) {
  // Must be in the same world
  if (!loc.getWorld().equals(this.world)) {
   return false;
  }

  double x = loc.getX();
  double y = loc.getY();
  double z = loc.getZ();

  double minX = Math.min(corner1.getX(), corner2.getX());
  double minY = Math.min(corner1.getY(), corner2.getY());
  double minZ = Math.min(corner1.getZ(), corner2.getZ());

  double maxX = Math.max(corner1.getX(), corner2.getX());
  double maxY = Math.max(corner1.getY(), corner2.getY());
  double maxZ = Math.max(corner1.getZ(), corner2.getZ());

  // Check if the location is within the bounding box defined by the two corners
  return (x >= minX && x <= maxX) &&
          (y >= minY && y <= maxY) &&
          (z >= minZ && z <= maxZ);
 }

 @Override
 public boolean equals(Object o) {
  if (this == o) return true;
  if (o == null || getClass() != o.getClass()) return false;
  LavaZone lavaZone = (LavaZone) o;
  return name.equals(lavaZone.name) &&
          world.equals(lavaZone.world) &&
          corner1.equals(lavaZone.corner1) &&
          corner2.equals(lavaZone.corner2);
 }

 @Override
 public int hashCode() {
  return Objects.hash(name, world, corner1, corner2);
 }
}

@SuppressWarnings("preview") // For String Templates if using Java < 21 with preview enabled
public class AutoWarn extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

 private CoreProtectAPI coreProtectAPI; // Instance variable for CoreProtect API

 // Stores all defined lava alert zones by their unique name
 private final Map<String, LavaZone> definedZones = new HashMap<>();

 // Temporary storage for player position selections for specific zone names
 // Player UUID -> Zone Name -> Position Type (e.g., "pos1", "pos2") -> Location
 private final Map<UUID, Map<String, Map<String, Location>>> playerZoneSelections = new HashMap<>();

 @Override
 public void onEnable() {
  // Initialize CoreProtect API
  this.coreProtectAPI = getCoreProtectAPI();
  if (this.coreProtectAPI == null) {
   getLogger().warning("CoreProtect API not found! Related functionality might be affected.");
  } else {
   getLogger().info("CoreProtect API hooked successfully.");
  }

  // Load configuration
  saveDefaultConfig(); // Creates config.yml if it doesn't exist
  loadZonesFromConfig();

  // Register event listener
  Bukkit.getPluginManager().registerEvents(this, this);

  // Register command
  Objects.requireNonNull(getCommand("lzdset")).setExecutor(this);
  Objects.requireNonNull(getCommand("lzdset")).setTabCompleter(this);

  getLogger().info("LavaZoneDetector enabled!");
  if (definedZones.isEmpty()) {
   getLogger().warning("No lava alert zones are currently defined. Use /lzdset commands to define them.");
  } else {
   getLogger().info(STR."\{definedZones.size()} lava alert zone(s) loaded.");
  }
 }

 @Override
 public void onDisable() {
  getLogger().info("LavaZoneDetector disabled!");
  playerZoneSelections.clear();
  definedZones.clear(); // Clear zones on disable
 }

 /**
  * Loads all lava zones from the plugin's config.yml.
  */
 private void loadZonesFromConfig() {
  definedZones.clear(); // Clear existing zones before loading
  FileConfiguration config = getConfig();
  ConfigurationSection zonesSection = config.getConfigurationSection("zones");

  if (zonesSection == null) {
   getLogger().info("No 'zones' section found in config.yml. No zones loaded.");
   return;
  }

  for (String zoneName : zonesSection.getKeys(false)) {
   ConfigurationSection zoneConfig = zonesSection.getConfigurationSection(zoneName);
   if (zoneConfig == null) {
    getLogger().warning(STR."Invalid configuration for zone '\{zoneName}'. Skipping.");
    continue;
   }

   String worldName = zoneConfig.getString("world");
   World world = Bukkit.getWorld(worldName);
   if (world == null) {
    getLogger().severe(STR."World '\{worldName}' for zone '\{zoneName}' not found! This zone will not be active.");
    continue;
   }

   try {
    Location corner1 = new Location(world,
            zoneConfig.getDouble("corner1.x"),
            zoneConfig.getDouble("corner1.y"),
            zoneConfig.getDouble("corner1.z"));
    Location corner2 = new Location(world,
            zoneConfig.getDouble("corner2.x"),
            zoneConfig.getDouble("corner2.y"),
            zoneConfig.getDouble("corner2.z"));

    definedZones.put(zoneName, new LavaZone(zoneName, world, corner1, corner2));
    getLogger().info(STR."Loaded zone '\{zoneName}' in world '\{worldName}'.");
   } catch (Exception e) {
    getLogger().severe(STR."Error loading coordinates for zone '\{zoneName}': \{e.getMessage()}. Skipping.");
   }
  }
 }

 /**
  * Saves all currently defined lava zones to the plugin's config.yml.
  */
 private void saveZonesToConfig() {
  FileConfiguration config = getConfig();
  config.set("zones", null); // Clear existing zones section to rewrite it

  if (!definedZones.isEmpty()) {
   for (LavaZone zone : definedZones.values()) {
    String path = STR."zones.\{zone.getName()}.";
    config.set(path + "world", zone.getWorld().getName());
    config.set(path + "corner1.x", zone.getCorner1().getX());
    config.set(path + "corner1.y", zone.getCorner1().getY());
    config.set(path + "corner1.z", zone.getCorner1().getZ());
    config.set(path + "corner2.x", zone.getCorner2().getX());
    config.set(path + "corner2.y", zone.getCorner2().getY());
    config.set(path + "corner2.z", zone.getCorner2().getZ());
   }
  }
  saveConfig();
 }

 /**
  * Attempts to get the CoreProtect API instance.
  * @return The CoreProtectAPI instance, or null if not found or not enabled.
  */
 private CoreProtectAPI getCoreProtectAPI() {
  Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
  if (plugin == null || !(plugin instanceof CoreProtect)) {
   getLogger().warning("CoreProtect plugin not found or is not an instance of CoreProtect.");
   return null;
  }
  CoreProtectAPI api = ((CoreProtect) plugin).getAPI();
  if (!api.isEnabled()) {
   getLogger().warning("CoreProtect API is not enabled.");
   return null;
  }
  if (api.APIVersion() < 10) { // Adjust API version check as needed
   getLogger().warning(STR."CoreProtect API version is outdated (found: \{api.APIVersion()}). Please update CoreProtect.");
   // return null; // Decide if an old API is a deal-breaker
  }
  return api;
 }

 @EventHandler
 public void onPlayerEmptyBucket(PlayerBucketEmptyEvent event) {
  Player player = event.getPlayer();
  Material bucketContent = event.getBucket();
  Location placedLocation = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();

  if (bucketContent == Material.LAVA_BUCKET) {
   // Check if the placed location is within any defined lava zone
   for (LavaZone zone : definedZones.values()) {
    if (zone.contains(placedLocation)) {
     getLogger().info(STR."Player \{player.getName()} used a LAVA bucket at \{formatLocation(placedLocation)} in protected zone '\{zone.getName()}'.");
     player.sendMessage(ChatColor.RED + "You are not allowed to place lava in zone '" + zone.getName() + "'.");
     event.setCancelled(true);

     // Optional: Log with CoreProtect if you have custom flags or reasons
     // if (this.coreProtectAPI != null) {
     //    this.coreProtectAPI.logPlacement(player.getName(), placedLocation, Material.LAVA, null);
     // }
     return; // Only need to cancel once if found in any zone
    }
   }
  }
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  // Allow console to reload
  if (!(sender instanceof Player player)) {
   if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
    loadZonesFromConfig();
    sender.sendMessage(ChatColor.GREEN + "LavaZoneDetector zone configuration reloaded.");
    if (definedZones.isEmpty()) {
     sender.sendMessage(ChatColor.YELLOW + "Warning: No zones defined in config or some worlds are invalid.");
    } else {
     sender.sendMessage(ChatColor.AQUA + STR."Successfully loaded \{definedZones.size()} zone(s).");
    }
    return true;
   }
   sender.sendMessage("This command can only be run by a player (except for /lzdset reload).");
   return true;
  }

  if (!player.hasPermission("lavazonedetector.admin.set")) {
   player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
   return true;
  }

  if (args.length < 1) {
   sendHelpMessage(player);
   return true;
  }

  String subCommand = args[0].toLowerCase();

  switch (subCommand) {
   case "pos1":
   case "pos2":
    if (args.length < 2) {
     player.sendMessage(ChatColor.RED + STR."Usage: /lzdset \{subCommand} <zone_name>");
     return true;
    }
    String zoneNameForPos = args[1];
    playerZoneSelections
            .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
            .computeIfAbsent(zoneNameForPos, k -> new HashMap<>())
            .put(subCommand, player.getLocation());
    player.sendMessage(ChatColor.GREEN + STR."Position '\{subCommand}' set for zone '\{zoneNameForPos}' to your current location: \{formatLocation(player.getLocation())}");
    break;

   case "define":
    if (args.length < 2) {
     player.sendMessage(ChatColor.RED + "Usage: /lzdset define <zone_name>");
     return true;
    }
    String zoneToDefine = args[1];
    Map<String, Location> playerSelections = playerZoneSelections.getOrDefault(player.getUniqueId(), Collections.emptyMap()).get(zoneToDefine);

    if (playerSelections == null || !playerSelections.containsKey("pos1") || !playerSelections.containsKey("pos2")) {
     player.sendMessage(ChatColor.RED + STR."You must set both positions for zone '\{zoneToDefine}' first using /lzdset \{zoneToDefine} pos1 and /lzdset \{zoneToDefine} pos2.");
     return true;
    }

    Location p1 = playerSelections.get("pos1");
    Location p2 = playerSelections.get("pos2");

    if (!p1.getWorld().equals(p2.getWorld())) {
     player.sendMessage(ChatColor.RED + "Both positions must be in the same world.");
     return true;
    }

    definedZones.put(zoneToDefine, new LavaZone(zoneToDefine, p1.getWorld(), p1, p2));
    saveZonesToConfig();
    player.sendMessage(ChatColor.GREEN + STR."Lava alert zone '\{zoneToDefine}' defined and saved for world '\{p1.getWorld().getName()}'.");
    player.sendMessage(ChatColor.GRAY + "Corner 1: " + formatLocation(p1));
    player.sendMessage(ChatColor.GRAY + "Corner 2: " + formatLocation(p2));

    // Clear selections for this specific zone for the player
    playerZoneSelections.get(player.getUniqueId()).remove(zoneToDefine);
    if (playerZoneSelections.get(player.getUniqueId()).isEmpty()) {
     playerZoneSelections.remove(player.getUniqueId());
    }
    break;

   case "remove":
    if (args.length < 2) {
     player.sendMessage(ChatColor.RED + "Usage: /lzdset remove <zone_name>");
     return true;
    }
    String zoneToRemove = args[1];
    if (definedZones.remove(zoneToRemove) != null) {
     saveZonesToConfig();
     player.sendMessage(ChatColor.GREEN + STR."Zone '\{zoneToRemove}' has been removed.");
    } else {
     player.sendMessage(ChatColor.YELLOW + STR."Zone '\{zoneToRemove}' not found.");
    }
    break;

   case "info":
    if (args.length == 2) {
     String zoneInfoName = args[1];
     LavaZone zoneInfo = definedZones.get(zoneInfoName);
     if (zoneInfo != null) {
      player.sendMessage(ChatColor.AQUA + STR."--- Lava Alert Zone: \{zoneInfo.getName()} ---");
      player.sendMessage(ChatColor.GOLD + "World: " + ChatColor.WHITE + zoneInfo.getWorld().getName());
      player.sendMessage(ChatColor.GOLD + "Corner 1: " + ChatColor.WHITE + formatLocation(zoneInfo.getCorner1()));
      player.sendMessage(ChatColor.GOLD + "Corner 2: " + ChatColor.WHITE + formatLocation(zoneInfo.getCorner2()));
     } else {
      player.sendMessage(ChatColor.YELLOW + STR."Zone '\{zoneInfoName}' not found.");
     }
    } else if (args.length == 1) {
     if (definedZones.isEmpty()) {
      player.sendMessage(ChatColor.YELLOW + "No lava alert zones are currently defined.");
     } else {
      player.sendMessage(ChatColor.AQUA + "--- All Lava Alert Zones ---");
      definedZones.values().forEach(zone ->
              player.sendMessage(ChatColor.GOLD + STR."- \{zone.getName()}: " + ChatColor.WHITE + STR."World: \{zone.getWorld().getName()}, C1: \{formatLocation(zone.getCorner1())}, C2: \{formatLocation(zone.getCorner2())}")
      );
     }
    } else {
     player.sendMessage(ChatColor.RED + "Usage: /lzdset info [zone_name]");
    }
    break;

   case "list":
    if (definedZones.isEmpty()) {
     player.sendMessage(ChatColor.YELLOW + "No lava alert zones are currently defined.");
    } else {
     player.sendMessage(ChatColor.AQUA + "--- Defined Lava Alert Zones ---");
     definedZones.keySet().forEach(zoneName -> player.sendMessage(ChatColor.GOLD + "- " + zoneName));
    }
    break;

   case "reload":
    loadZonesFromConfig();
    player.sendMessage(ChatColor.GREEN + "LavaZoneDetector zone configuration reloaded.");
    if (definedZones.isEmpty()) {
     player.sendMessage(ChatColor.YELLOW + "Warning: No zones defined in config or some worlds are invalid.");
    } else {
     player.sendMessage(ChatColor.AQUA + STR."Successfully loaded \{definedZones.size()} zone(s).");
    }
    break;

   default:
    sendHelpMessage(player);
    break;
  }
  return true;
 }

 @Override
 public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
  if (!sender.hasPermission("lavazonedetector.admin.set")) {
   return Collections.emptyList();
  }

  if (args.length == 1) {
   List<String> subCommands = new ArrayList<>(List.of("pos1", "pos2", "define", "remove", "info", "list", "reload"));
   return subCommands.stream()
           .filter(s -> s.startsWith(args[0].toLowerCase()))
           .collect(Collectors.toList());
  } else if (args.length == 2) {
   String subCommand = args[0].toLowerCase();
   if (subCommand.equals("pos1") || subCommand.equals("pos2") || subCommand.equals("define") || subCommand.equals("remove") || subCommand.equals("info")) {
    // Suggest existing zone names for these commands
    return definedZones.keySet().stream()
            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
            .collect(Collectors.toList());
   }
  }
  return Collections.emptyList();
 }

 /**
  * Sends the help message to the player.
  * @param player The player to send the message to.
  */
 private void sendHelpMessage(Player player) {
  player.sendMessage(ChatColor.AQUA + "--- LavaZoneDetector Setter Help ---");
  player.sendMessage(ChatColor.GOLD + "/lzdset <zone_name> pos1" + ChatColor.WHITE + " - Set first zone corner for <zone_name>.");
  player.sendMessage(ChatColor.GOLD + "/lzdset <zone_name> pos2" + ChatColor.WHITE + " - Set second zone corner for <zone_name>.");
  player.sendMessage(ChatColor.GOLD + "/lzdset <zone_name> define" + ChatColor.WHITE + " - Define/update <zone_name> using selected positions.");
  player.sendMessage(ChatColor.GOLD + "/lzdset remove <zone_name>" + ChatColor.WHITE + " - Remove a defined zone.");
  player.sendMessage(ChatColor.GOLD + "/lzdset info [zone_name]" + ChatColor.WHITE + " - Show info for a specific zone or all zones.");
  player.sendMessage(ChatColor.GOLD + "/lzdset list" + ChatColor.WHITE + " - List all defined zones.");
  player.sendMessage(ChatColor.GOLD + "/lzdset reload" + ChatColor.WHITE + " - Reload all zones from config.");
 }

 /**
  * Formats a Location object into a readable string.
  * @param loc The location to format.
  * @return A formatted string representation of the location.
  */
 private String formatLocation(Location loc) {
  if (loc == null) return "N/A";
  return String.format("X: %.1f, Y: %.1f, Z: %.1f (World: %s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
 }
}
