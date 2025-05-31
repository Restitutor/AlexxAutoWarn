Diffchecker logo
        Diffchecker

        Text

        Diffchecker Desktop icon
        Diffchecker Desktop
        The most secure way to run Diffchecker. Get the Diffchecker Desktop app: your diffs never leave your computer!
        Get Desktop
        Untitled diff

        Clear

        Save

        Share

        Explain
        338 removals
        454 lines

        Copy

        532 additions
        577 lines

        Copy

        package net.Alexxiconify.alexxAutoWarn.AlexxsAutoWarn;



import net.coreprotect.CoreProtect;

import net.coreprotect.CoreProtectAPI;

import org.bukkit.Bukkit;

import org.bukkit.ChatColor;

import org.bukkit.Location;

import org.bukkit.Material;

import org.bukkit.NamespacedKey;

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

import org.bukkit.event.block.Action;

import org.bukkit.event.player.PlayerBucketEmptyEvent;

import org.bukkit.event.player.PlayerInteractEvent;

import org.bukkit.inventory.ItemStack;

import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.persistence.PersistentDataType;

import org.bukkit.plugin.Plugin;

import org.bukkit.plugin.java.JavaPlugin;

import org.jetbrains.annotations.NotNull;



import java.util.ArrayList;

import java.util.Arrays;

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

public class AlexxAutoWarn extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {



 private CoreProtectAPI coreProtectAPI; // Instance variable for CoreProtect API



 // Stores all defined lava alert zones by their unique name

 private final Map<String, LavaZone> definedZones = new HashMap<>();



 // Temporary storage for player position selections for specific zone names (old method)

 // Player UUID -> Zone Name -> Position Type (e.g., "pos1", "pos2") -> Location

 private final Map<UUID, Map<String, Map<String, Location>>> playerZoneSelections = new HashMap<>();



 // Temporary storage for player wand selections

 private final Map<UUID, Location> playerWandPos1 = new HashMap<>();

 private final Map<UUID, Location> playerWandPos2 = new HashMap<>();



 // NamespacedKey for identifying the wand item

 private NamespacedKey wandKey;



 @Override

 public void onEnable() {

  // Initialize NamespacedKey for the wand

  this.wandKey = new NamespacedKey(this, "lzd_wand");



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

  playerWandPos1.clear();

  playerWandPos2.clear();

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
















































































































  Original text
  No file chosen
  Open file
  1
  2
  3
  4
  5
  6
  7
  8
  9
  10
  11
  12
  13
  14
  15
  16
  17
  18
  19
  20
  21
  22
  23
  24
  25
  26
  27
  28
  29
  30
  31
  32
  33
  34
  35
  36
package net.Alexxiconify.alexxAutoWarn.AlexxsAutoWarn;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.*;
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

import java.util.*;
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
    Changed text
    No file chosen
    Open file
    557
    558
    559
    560
    561
    562
    563
    564
    565
    566
    567
    568
    569
    570
    571
    572
    573
    574
    575
    576
    577
    player.sendMessage(ChatColor.GOLD + "/lzdset <zone_name> pos1" + ChatColor.WHITE + " - Set first zone corner (manual).");
    player.sendMessage(ChatColor.GOLD + "/lzdset <zone_name> pos2" + ChatColor.WHITE + " - Set second zone corner (manual).");
    player.sendMessage(ChatColor.GOLD + "/lzdset <zone_name> define" + ChatColor.WHITE + " - Define/update <zone_name> using wand or manual selections.");
    player.sendMessage(ChatColor.GOLD + "/lzdset remove <zone_name>" + ChatColor.WHITE + " - Remove a defined zone.");
    player.sendMessage(ChatColor.GOLD + "/lzdset info [zone_name]" + ChatColor.WHITE + " - Show info for a specific zone or all zones.");
    player.sendMessage(ChatColor.GOLD + "/lzdset list" + ChatColor.WHITE + " - List all defined zones.");
    player.sendMessage(ChatColor.GOLD + "/lzdset clearwand" + ChatColor.WHITE + " - Clear your wand selections.");
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