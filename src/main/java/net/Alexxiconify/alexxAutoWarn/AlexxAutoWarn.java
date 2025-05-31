package net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn; // Make sure to change this to your actual package name

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
import java.util.UUID;
import java.util.stream.Collectors;

// Note: The STR."..." syntax is a Java 21+ String Template feature.
// If your server uses an older Java version, you'll need to change these to traditional string concatenation.
// e.g., getLogger().severe("World '" + worldName + "' not found for zone definition!");

@SuppressWarnings("preview") // For String Templates if using Java < 21 with preview enabled
public class AlexxAutoWarn extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

 private CoreProtectAPI coreProtectAPI; // Instance variable for CoreProtect API

 // For defining the single alert zone
 private Location zoneCorner1;
 private Location zoneCorner2;

 // Temporary storage for player position selections
 private final Map<UUID, Location> playerPos1Selections = new HashMap<>();
 private final Map<UUID, Location> playerPos2Selections = new HashMap<>();

 @Override
 public void onEnable() {
  // Initialize CoreProtect API
  this.coreProtectAPI = getCoreProtectAPI(); // Assign to the instance variable
  if (this.coreProtectAPI == null) {
   getLogger().severe("CoreProtect API not found! Related functionality might be affected.");
  } else {
   getLogger().info("CoreProtect API hooked successfully.");
  }

  // Load configuration
  saveDefaultConfig(); // Creates config.yml if it doesn't exist
  loadZoneFromConfig();

  // Register event listener
  Bukkit.getPluginManager().registerEvents(this, this);

  // Register command
  getCommand("lzdset").setExecutor(this);
  getCommand("lzdset").setTabCompleter(this);

  getLogger().info("LavaZoneDetector enabled!");
  if (zoneCorner1 == null || zoneCorner2 == null) {
   getLogger().warning("Lava alert zone is not yet defined. Use /lzdset commands to define it.");
  } else {
   getLogger().info(String.format("Lava alert zone loaded for world '%s'.", zoneCorner1.getWorld().getName()));
  }
 }

 @Override
 public void onDisable() {
  getLogger().info("LavaZoneDetector disabled!");
  playerPos1Selections.clear();
  playerPos2Selections.clear();
 }

 private void loadZoneFromConfig() {
  FileConfiguration config = getConfig();
  if (config.contains("zone.world") && config.contains("zone.corner1.x")) { // Check if zone is defined
   String worldName = config.getString("zone.world");
   World world = Bukkit.getWorld(worldName);
   if (world == null) {
    getLogger().severe(STR."World '\{worldName}' defined in config.yml for the zone not found! The zone will not be active.");
    zoneCorner1 = null;
    zoneCorner2 = null;
    return;
   }
   zoneCorner1 = new Location(world,
           config.getDouble("zone.corner1.x"),
           config.getDouble("zone.corner1.y"),
           config.getDouble("zone.corner1.z"));
   zoneCorner2 = new Location(world,
           config.getDouble("zone.corner2.x"),
           config.getDouble("zone.corner2.y"),
           config.getDouble("zone.corner2.z"));
  } else {
   zoneCorner1 = null;
   zoneCorner2 = null;
  }
 }

 private void saveZoneToConfig() {
  FileConfiguration config = getConfig();
  if (zoneCorner1 != null && zoneCorner2 != null) {
   config.set("zone.world", zoneCorner1.getWorld().getName());
   config.set("zone.corner1.x", zoneCorner1.getX());
   config.set("zone.corner1.y", zoneCorner1.getY());
   config.set("zone.corner1.z", zoneCorner1.getZ());
   config.set("zone.corner2.x", zoneCorner2.getX());
   config.set("zone.corner2.y", zoneCorner2.getY());
   config.set("zone.corner2.z", zoneCorner2.getZ());
  } else { // Clear it if null
   config.set("zone", null);
  }
  saveConfig();
 }


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
   if (zoneCorner1 != null && zoneCorner2 != null && isInZone(placedLocation)) {
    getLogger().info(STR."Player \{player.getName()} used a LAVA bucket at \{placedLocation.getBlockX()}, \{placedLocation.getBlockY()}, \{placedLocation.getBlockZ()} in the protected zone!");
    player.sendMessage("§cYou are not allowed to place lava in this zone.");
    event.setCancelled(true);

    // Optional: Log with CoreProtect if you have custom flags or reasons
    // if (this.coreProtectAPI != null) {
    // this.coreProtectAPI.logPlacement(player.getName(), placedLocation, Material.LAVA, null);
    // }
   }
  }
 }

 private boolean isInZone(Location loc) {
  if (loc == null || zoneCorner1 == null || zoneCorner2 == null) {
   return false;
  }
  if (!loc.getWorld().equals(zoneCorner1.getWorld())) {
   return false; // Must be in the same world
  }

  double x = loc.getX();
  double y = loc.getY();
  double z = loc.getZ();

  double minX = Math.min(zoneCorner1.getX(), zoneCorner2.getX());
  double minY = Math.min(zoneCorner1.getY(), zoneCorner2.getY());
  double minZ = Math.min(zoneCorner1.getZ(), zoneCorner2.getZ());

  double maxX = Math.max(zoneCorner1.getX(), zoneCorner2.getX());
  double maxY = Math.max(zoneCorner1.getY(), zoneCorner2.getY());
  double maxZ = Math.max(zoneCorner1.getZ(), zoneCorner2.getZ());

  return (x >= minX && x <= maxX) &&
          (y >= minY && y <= maxY) &&
          (z >= minZ && z <= maxZ);
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  if (!(sender instanceof Player player)) {
   if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
    loadZoneFromConfig();
    sender.sendMessage(ChatColor.GREEN + "LavaZoneDetector zone configuration reloaded.");
    if (zoneCorner1 == null || zoneCorner2 == null) {
     sender.sendMessage(ChatColor.YELLOW + "Warning: Zone is not defined in config or world is invalid.");
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

  if (args.length == 0) {
   sendHelpMessage(player);
   return true;
  }

  String subCommand = args[0].toLowerCase();
  switch (subCommand) {
   case "pos1":
    playerPos1Selections.put(player.getUniqueId(), player.getLocation());
    player.sendMessage(ChatColor.GREEN + "Position 1 set to your current location: " + formatLocation(player.getLocation()));
    break;
   case "pos2":
    playerPos2Selections.put(player.getUniqueId(), player.getLocation());
    player.sendMessage(ChatColor.GREEN + "Position 2 set to your current location: " + formatLocation(player.getLocation()));
    break;
   case "define":
    Location p1 = playerPos1Selections.get(player.getUniqueId());
    Location p2 = playerPos2Selections.get(player.getUniqueId());

    if (p1 == null || p2 == null) {
     player.sendMessage(ChatColor.RED + "You must set both positions first using /lzdset pos1 and /lzdset pos2.");
     return true;
    }
    if (!p1.getWorld().equals(p2.getWorld())) {
     player.sendMessage(ChatColor.RED + "Both positions must be in the same world.");
     return true;
    }

    this.zoneCorner1 = p1;
    this.zoneCorner2 = p2;
    saveZoneToConfig();
    player.sendMessage(ChatColor.GREEN + "Lava alert zone defined and saved for world '" + p1.getWorld().getName() + "'.");
    player.sendMessage(ChatColor.GRAY + "Corner 1: " + formatLocation(p1));
    player.sendMessage(ChatColor.GRAY + "Corner 2: " + formatLocation(p2));

    playerPos1Selections.remove(player.getUniqueId()); // Clear selections
    playerPos2Selections.remove(player.getUniqueId());
    break;
   case "reload":
    loadZoneFromConfig();
    player.sendMessage(ChatColor.GREEN + "LavaZoneDetector zone configuration reloaded.");
    if (zoneCorner1 == null || zoneCorner2 == null) {
     player.sendMessage(ChatColor.YELLOW + "Warning: Zone is not defined in config or world is invalid.");
    } else {
     player.sendMessage(ChatColor.AQUA + "Current zone loaded for world '" + zoneCorner1.getWorld().getName() + "'.");
    }
    break;
   case "info":
    if (zoneCorner1 != null && zoneCorner2 != null) {
     player.sendMessage(ChatColor.AQUA + "--- Current Lava Alert Zone ---");
     player.sendMessage(ChatColor.GOLD + "World: " + ChatColor.WHITE + zoneCorner1.getWorld().getName());
     player.sendMessage(ChatColor.GOLD + "Corner 1: " + ChatColor.WHITE + formatLocation(zoneCorner1));
     player.sendMessage(ChatColor.GOLD + "Corner 2: " + ChatColor.WHITE + formatLocation(zoneCorner2));
    } else {
     player.sendMessage(ChatColor.YELLOW + "No lava alert zone is currently defined.");
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
   List<String> subCommands = new ArrayList<>(List.of("pos1", "pos2", "define", "reload", "info"));
   return subCommands.stream()
           .filter(s -> s.startsWith(args[0].toLowerCase()))
           .collect(Collectors.toList());
  }
  return Collections.emptyList();
 }

 private void sendHelpMessage(Player player) {
  player.sendMessage(ChatColor.AQUA + "--- LavaZoneDetector Setter Help ---");
  player.sendMessage(ChatColor.GOLD + "/lzdset pos1" + ChatColor.WHITE + " - Set first zone corner.");
  player.sendMessage(ChatColor.GOLD + "/lzdset pos2" + ChatColor.WHITE + " - Set second zone corner.");
  player.sendMessage(ChatColor.GOLD + "/lzdset define" + ChatColor.WHITE + " - Define and save the zone using selected positions.");
  player.sendMessage(ChatColor.GOLD + "/lzdset info" + ChatColor.WHITE + " - Show current zone info.");
  player.sendMessage(ChatColor.GOLD + "/lzdset reload" + ChatColor.WHITE + " - Reload zone from config.");
 }

 private String formatLocation(Location loc) {
  if (loc == null) return "N/A";
  return String.format("X: %.1f, Y: %.1f, Z: %.1f (World: %s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
 }
}