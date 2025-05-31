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
import org.bukkit.event.block.BlockPlaceEvent;
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
import java.util.HashSet; // Use HashSet for efficient material lookups
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set; // Use Set for banned materials
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

 // Stores all banned materials
 private final Set<Material> bannedMaterials = new HashSet<>();

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
  loadBannedMaterialsFromConfig(); // Load banned materials

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
  getLogger().info(STR."Currently banned materials: \{formatMaterialList(bannedMaterials)}");
 }

 @Override
 public void onDisable() {
  getLogger().info("LavaZoneDetector disabled!");
  playerZoneSelections.clear();
  playerWandPos1.clear();
  playerWandPos2.clear();
  definedZones.clear(); // Clear zones on disable
  bannedMaterials.clear(); // Clear banned materials on disable
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
  * Loads banned materials from the plugin's config.yml.
  */
 private void loadBannedMaterialsFromConfig() {
  bannedMaterials.clear(); // Clear existing materials before loading
  FileConfiguration config = getConfig();
  List<String> materialNames = config.getStringList("banned-materials");

  for (String name : materialNames) {
   try {
    Material material = Material.valueOf(name.toUpperCase());
    bannedMaterials.add(material);
   } catch (IllegalArgumentException e) {
    getLogger().warning(STR."Invalid material name '\{name}' found in config.yml banned-materials list. Skipping.");
   }
  }
 }

 /**
  * Saves currently banned materials to the plugin's config.yml.
  */
 private void saveBannedMaterialsToConfig() {
  FileConfiguration config = getConfig();
  List<String> materialNames = bannedMaterials.stream()
          .map(Enum::name) // Convert Material enum to its string name
          .collect(Collectors.toList());
  config.set("banned-materials", materialNames);
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

 /**
  * Creates and returns the Lava Zone Selector Wand item.
  * @return The ItemStack representing the wand.
  */
 private ItemStack createWand() {
  ItemStack wand = new ItemStack(Material.WOODEN_AXE); // A common choice for selection tools
  ItemMeta meta = wand.getItemMeta();

  if (meta != null) {
   meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Lava Zone Selector Wand");
   meta.setLore(Arrays.asList(
           ChatColor.GRAY + "Left-click: Set Position 1",
           ChatColor.GRAY + "Right-click: Set Position 2"
   ));
   // Add a custom NBT tag to identify this specific wand
   meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, "true");
   wand.setItemMeta(meta);
  }
  return wand;
 }

 /**
  * Checks if an ItemStack is the Lava Zone Selector Wand.
  * @param item The ItemStack to check.
  * @return true if it's the wand, false otherwise.
  */
 private boolean isWand(ItemStack item) {
  if (item == null || !item.hasItemMeta()) {
   return false;
  }
  ItemMeta meta = item.getItemMeta();
  return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.STRING);
 }

 /**
  * Helper method to check if a location is within any defined zone and send a message.
  * @param player The player performing the action.
  * @param location The location of the action.
  * @param material The material being placed/used.
  * @return true if the action should be cancelled, false otherwise.
  */
 private boolean checkAndWarn(Player player, Location location, Material material) {
  // Only proceed if the material is in the banned list
  if (!bannedMaterials.contains(material)) {
   return false;
  }

  for (LavaZone zone : definedZones.values()) {
   if (zone.contains(location)) {
    getLogger().info(STR."Player \{player.getName()} attempted to place \{material.name()} at \{formatLocation(location)} in protected zone '\{zone.getName()}'.");
    player.sendMessage(ChatColor.RED + STR."You are not allowed to place \{material.name()} in zone '\{zone.getName()}'.");
    // Optional: Log with CoreProtect if you have custom flags or reasons
    // if (this.coreProtectAPI != null) {
    //    this.coreProtectAPI.logPlacement(player.getName(), location, material, null);
    // }
    return true; // Action should be cancelled
   }
  }
  return false; // Action should not be cancelled
 }

 @EventHandler
 public void onPlayerEmptyBucket(PlayerBucketEmptyEvent event) {
  // Check if the bucket content (LAVA_BUCKET) is in the banned list
  if (bannedMaterials.contains(event.getBucket())) {
   Location placedLocation = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
   if (checkAndWarn(event.getPlayer(), placedLocation, event.getBucket())) {
    event.setCancelled(true);
   }
  }
 }

 @EventHandler
 public void onBlockPlace(BlockPlaceEvent event) {
  // Check if the placed block's material is in the banned list
  if (bannedMaterials.contains(event.getBlock().getType())) {
   if (checkAndWarn(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType())) {
    event.setCancelled(true);
   }
  }
 }

 @EventHandler
 public void onPlayerInteract(PlayerInteractEvent event) {
  Player player = event.getPlayer();
  ItemStack handItem = event.getItem(); // Get item in hand

  // Handle wand interactions
  if (isWand(handItem)) {
   // Prevent block breaking/placement with the wand
   event.setCancelled(true);

   if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
    if (event.getClickedBlock() != null) {
     playerWandPos1.put(player.getUniqueId(), event.getClickedBlock().getLocation());
     player.sendMessage(ChatColor.GREEN + "Position 1 set: " + formatLocation(event.getClickedBlock().getLocation()));
    } else {
     player.sendMessage(ChatColor.YELLOW + "You must click on a block to set Position 1.");
    }
   } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
    if (event.getClickedBlock() != null) {
     playerWandPos2.put(player.getUniqueId(), event.getClickedBlock().getLocation());
     player.sendMessage(ChatColor.GREEN + "Position 2 set: " + formatLocation(event.getClickedBlock().getLocation()));
    } else {
     player.sendMessage(ChatColor.YELLOW + "You must click on a block to set Position 2.");
    }
   }
   return; // Don't process other interactions if it's the wand
  }

  // Handle placement of items that spawn entities (like TNT Minecart)
  if (handItem != null && bannedMaterials.contains(handItem.getType())) {
   if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
    // For TNT Minecart, it's placed on rails
    if (handItem.getType() == Material.TNT_MINECART && event.getClickedBlock() != null && event.getClickedBlock().getType().name().contains("RAIL")) {
     // The location where the minecart would be placed
     Location placementLocation = event.getClickedBlock().getLocation().add(0, 1, 0); // Minecart spawns above the rail
     if (checkAndWarn(player, placementLocation, Material.TNT_MINECART)) {
      event.setCancelled(true);
     }
    }
    // Add more specific checks for other entity-spawning items if needed
   }
  }
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  // Allow console to reload
  if (!(sender instanceof Player player)) {
   if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
    loadZonesFromConfig();
    loadBannedMaterialsFromConfig(); // Reload banned materials
    sender.sendMessage(ChatColor.GREEN + "LavaZoneDetector configuration reloaded.");
    if (definedZones.isEmpty()) {
     sender.sendMessage(ChatColor.YELLOW + "Warning: No zones defined in config or some worlds are invalid.");
    } else {
     sender.sendMessage(ChatColor.AQUA + STR."Successfully loaded \{definedZones.size()} zone(s).");
    }
    sender.sendMessage(ChatColor.AQUA + STR."Currently banned materials: \{formatMaterialList(bannedMaterials)}");
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
   case "wand":
    player.getInventory().addItem(createWand());
    player.sendMessage(ChatColor.GREEN + "You have received the Lava Zone Selector Wand!");
    break;

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
    // Clear any wand selections to avoid confusion
    playerWandPos1.remove(player.getUniqueId());
    playerWandPos2.remove(player.getUniqueId());
    break;

   case "define":
    if (args.length < 2) {
     player.sendMessage(ChatColor.RED + "Usage: /lzdset define <zone_name>");
     return true;
    }
    String zoneToDefine = args[1];
    Location p1 = playerWandPos1.get(player.getUniqueId());
    Location p2 = playerWandPos2.get(player.getUniqueId());

    // Prioritize wand selections
    if (p1 != null && p2 != null) {
     player.sendMessage(ChatColor.AQUA + "Using wand selections for zone '" + zoneToDefine + "'.");
    } else {
     // Fallback to manual /lzdset pos1/pos2 selections
     Map<String, Location> playerSelections = playerZoneSelections.getOrDefault(player.getUniqueId(), Collections.emptyMap()).get(zoneToDefine);
     if (playerSelections == null || !playerSelections.containsKey("pos1") || !playerSelections.containsKey("pos2")) {
      player.sendMessage(ChatColor.RED + STR."You must set both positions for zone '\{zoneToDefine}' first using the wand or /lzdset \{zoneToDefine} pos1 and /lzdset \{zoneToDefine} pos2.");
      return true;
     }
     p1 = playerSelections.get("pos1");
     p2 = playerSelections.get("pos2");
    }

    if (!p1.getWorld().equals(p2.getWorld())) {
     player.sendMessage(ChatColor.RED + "Both positions must be in the same world.");
     // Clear wand selections if they were used and caused this error
     playerWandPos1.remove(player.getUniqueId());
     playerWandPos2.remove(player.getUniqueId());
     return true;
    }

    definedZones.put(zoneToDefine, new LavaZone(zoneToDefine, p1.getWorld(), p1, p2));
    saveZonesToConfig();
    player.sendMessage(ChatColor.GREEN + STR."Lava alert zone '\{zoneToDefine}' defined and saved for world '\{p1.getWorld().getName()}'.");
    player.sendMessage(ChatColor.GRAY + "Corner 1: " + formatLocation(p1));
    player.sendMessage(ChatColor.GRAY + "Corner 2: " + formatLocation(p2));

    // Clear both wand and manual selections for this specific zone for the player
    playerWandPos1.remove(player.getUniqueId());
    playerWandPos2.remove(player.getUniqueId());
    if (playerZoneSelections.get(player.getUniqueId()) != null) {
     playerZoneSelections.get(player.getUniqueId()).remove(zoneToDefine);
     if (playerZoneSelections.get(player.getUniqueId()).isEmpty()) {
      playerZoneSelections.remove(player.getUniqueId());
     }
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
    loadBannedMaterialsFromConfig(); // Reload banned materials on reload command
    player.sendMessage(ChatColor.GREEN + "LavaZoneDetector configuration reloaded.");
    if (definedZones.isEmpty()) {
     player.sendMessage(ChatColor.YELLOW + "Warning: No zones defined in config or some worlds are invalid.");
    } else {
     player.sendMessage(ChatColor.AQUA + STR."Successfully loaded \{definedZones.size()} zone(s).");
    }
    player.sendMessage(ChatColor.AQUA + STR."Currently banned materials: \{formatMaterialList(bannedMaterials)}");
    break;

   case "clearwand": // New command to clear wand selections
    playerWandPos1.remove(player.getUniqueId());
    playerWandPos2.remove(player.getUniqueId());
    player.sendMessage(ChatColor.GREEN + "Your wand selections have been cleared.");
    break;

   case "banned": // New subcommand for banned materials management
    if (args.length < 2) {
     player.sendMessage(ChatColor.RED + "Usage: /lzdset banned <add|remove|list> [material_name]");
     return true;
    }
    String bannedAction = args[1].toLowerCase();
    switch (bannedAction) {
     case "add":
      if (args.length < 3) {
       player.sendMessage(ChatColor.RED + "Usage: /lzdset banned add <material_name>");
       return true;
      }
      String materialToAdd = args[2].toUpperCase();
      try {
       Material material = Material.valueOf(materialToAdd);
       if (bannedMaterials.add(material)) {
        saveBannedMaterialsToConfig();
        player.sendMessage(ChatColor.GREEN + STR."Material '\{material.name()}' added to banned list.");
       } else {
        player.sendMessage(ChatColor.YELLOW + STR."Material '\{material.name()}' is already in the banned list.");
       }
      } catch (IllegalArgumentException e) {
       player.sendMessage(ChatColor.RED + STR."Invalid material name: '\{materialToAdd}'. Please use a valid Minecraft material name (e.g., LAVA_BUCKET, TNT).");
      }
      break;
     case "remove":
      if (args.length < 3) {
       player.sendMessage(ChatColor.RED + "Usage: /lzdset banned remove <material_name>");
       return true;
      }
      String materialToRemove = args[2].toUpperCase();
      try {
       Material material = Material.valueOf(materialToRemove);
       if (bannedMaterials.remove(material)) {
        saveBannedMaterialsToConfig();
        player.sendMessage(ChatColor.GREEN + STR."Material '\{material.name()}' removed from banned list.");
       } else {
        player.sendMessage(ChatColor.YELLOW + STR."Material '\{material.name()}' is not in the banned list.");
       }
      } catch (IllegalArgumentException e) {
       player.sendMessage(ChatColor.RED + STR."Invalid material name: '\{materialToRemove}'. Please use a valid Minecraft material name.");
      }
      break;
     case "list":
      if (bannedMaterials.isEmpty()) {
       player.sendMessage(ChatColor.YELLOW + "No materials are currently banned.");
      } else {
       player.sendMessage(ChatColor.AQUA + "--- Banned Materials ---");
       player.sendMessage(ChatColor.WHITE + formatMaterialList(bannedMaterials));
      }
      break;
     default:
      player.sendMessage(ChatColor.RED + "Unknown 'banned' subcommand. Usage: /lzdset banned <add|remove|list>");
      break;
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
   List<String> subCommands = new ArrayList<>(List.of("wand", "pos1", "pos2", "define", "remove", "info", "list", "reload", "clearwand", "banned"));
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
   } else if (subCommand.equals("banned")) {
    return Arrays.asList("add", "remove", "list").stream()
            .filter(s -> s.startsWith(args[1].toLowerCase()))
            .collect(Collectors.toList());
   }
  } else if (args.length == 3 && args[0].equalsIgnoreCase("banned")) {
   String bannedAction = args[1].toLowerCase();
   if (bannedAction.equals("add")) {
    // Suggest all possible materials for 'add'
    return Arrays.stream(Material.values())
            .map(Enum::name)
            .filter(s -> s.startsWith(args[2].toUpperCase()))
            .collect(Collectors.toList());
   } else if (bannedAction.equals("remove")) {
    // Suggest only currently banned materials for 'remove'
    return bannedMaterials.stream()
            .map(Enum::name)
            .filter(s -> s.startsWith(args[2].toUpperCase()))
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
  player.sendMessage(ChatColor.GOLD + "/lzdset wand" + ChatColor.WHITE + " - Get the selection wand.");
  player.sendMessage(ChatColor.GOLD + "/lzdset <zone_name> pos1" + ChatColor.WHITE + " - Set first zone corner (manual).");
  player.sendMessage(ChatColor.GOLD + "/lzdset <zone_name> pos2" + ChatColor.WHITE + " - Set second zone corner (manual).");
  player.sendMessage(ChatColor.GOLD + "/lzdset <zone_name> define" + ChatColor.WHITE + " - Define/update <zone_name> using wand or manual selections.");
  player.sendMessage(ChatColor.GOLD + "/lzdset remove <zone_name>" + ChatColor.WHITE + " - Remove a defined zone.");
  player.sendMessage(ChatColor.GOLD + "/lzdset info [zone_name]" + ChatColor.WHITE + " - Show info for a specific zone or all zones.");
  player.sendMessage(ChatColor.GOLD + "/lzdset list" + ChatColor.WHITE + " - List all defined zones.");
  player.sendMessage(ChatColor.GOLD + "/lzdset clearwand" + ChatColor.WHITE + " - Clear your wand selections.");
  player.sendMessage(ChatColor.GOLD + "/lzdset reload" + ChatColor.WHITE + " - Reload all zones and banned materials from config.");
  player.sendMessage(ChatColor.GOLD + "/lzdset banned <add|remove|list>" + ChatColor.WHITE + " - Manage banned materials.");
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

 /**
  * Formats a set of Materials into a comma-separated string.
  * @param materials The set of materials to format.
  * @return A string representation of the materials.
  */
 private String formatMaterialList(Set<Material> materials) {
  if (materials.isEmpty()) {
   return "None";
  }
  return materials.stream()
          .map(Enum::name)
          .collect(Collectors.joining(", "));
 }
}
