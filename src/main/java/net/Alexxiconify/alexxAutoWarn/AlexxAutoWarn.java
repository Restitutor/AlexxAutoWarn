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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID; // <--- ADD THIS LINE
import java.util.stream.Collectors;

// Note: The STR."..." syntax is a Java 21+ String Template feature.
// If your server uses an older Java version, you'll need to change these to traditional string concatenation.
// e.g., getLogger().severe("World '" + worldName + "' not found for zone definition!");

/**
 * Represents a defined AutoInform zone with two corners and a world.
 */
class AutoInformZone {
 private final String name;
 private final World world;
 private final Location corner1;
 private final Location corner2;

 /**
  * Constructs a new AutoInformZone.
  * @param name The unique name of the zone.
  * @param world The world the zone is in.
  * @param corner1 The first corner of the zone.
  * @param corner2 The second corner of the zone.
  */
 public AutoInformZone(String name, @NotNull World world, @NotNull Location corner1, @NotNull Location corner2) {
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
  AutoInformZone autoInformZone = (AutoInformZone) o;
  return name.equals(autoInformZone.name) &&
          world.equals(autoInformZone.world) &&
          corner1.equals(autoInformZone.corner1) &&
          corner2.equals(autoInformZone.corner2);
 }

 @Override
 public int hashCode() {
  return Objects.hash(name, world, corner1, corner2);
 }
}

@SuppressWarnings("preview") // For String Templates if using Java < 21 with preview enabled
public class AlexxAutoWarn extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

 private CoreProtectAPI coreProtectAPI; // Instance variable for CoreProtect API

 // Stores all defined AutoInform zones by their unique name
 private final Map<String, AutoInformZone> definedZones = new HashMap<>();

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
  this.wandKey = new NamespacedKey(this, "ainform_wand");

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
  Objects.requireNonNull(getCommand("ainformset")).setExecutor(this);
  Objects.requireNonNull(getCommand("ainformset")).setTabCompleter(this);

  getLogger().info("AutoInform enabled!");
  if (definedZones.isEmpty()) {
   getLogger().warning("No AutoInform zones are currently defined. Use /ainformset commands to define them.");
  } else {
   getLogger().info(STR."\{definedZones.size()} AutoInform zone(s) loaded.");
  }
  getLogger().info(STR."Currently banned materials: \{formatMaterialList(bannedMaterials)}");
 }

 @Override
 public void onDisable() {
  getLogger().info("AutoInform disabled!");
  playerZoneSelections.clear();
  playerWandPos1.clear();
  playerWandPos2.clear();
  definedZones.clear();
  bannedMaterials.clear();
 }

 /**
  * Loads all AutoInform zones from the plugin's config.yml.
  */
 private void loadZonesFromConfig() {
  definedZones.clear();
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

    definedZones.put(zoneName, new AutoInformZone(zoneName, world, corner1, corner2));
    getLogger().info(STR."Loaded zone '\{zoneName}' in world '\{worldName}'.");
   } catch (Exception e) {
    getLogger().severe(STR."Error loading coordinates for zone '\{zoneName}': \{e.getMessage()}. Skipping.");
   }
  }
 }

 /**
  * Saves all currently defined AutoInform zones to the plugin's config.yml.
  */
 private void saveZonesToConfig() {
  FileConfiguration config = getConfig();
  config.set("zones", null);

  if (!definedZones.isEmpty()) {
   for (AutoInformZone zone : definedZones.values()) {
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
  bannedMaterials.clear();
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
          .map(Enum::name)
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
  if (api.APIVersion() < 10) {
   getLogger().warning(STR."CoreProtect API version is outdated (found: \{api.APIVersion()}). Please update CoreProtect.");
  }
  return api;
 }

 /**
  * Creates and returns the AutoInform Zone Selector Wand item.
  * @return The ItemStack representing the wand.
  */
 private ItemStack createWand() {
  ItemStack wand = new ItemStack(Material.WOODEN_AXE);
  ItemMeta meta = wand.getItemMeta();

  if (meta != null) {
   meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "AutoInform Zone Selector Wand");
   meta.setLore(Arrays.asList(
           ChatColor.GRAY + "Left-click: Set Position 1",
           ChatColor.GRAY + "Right-click: Set Position 2"
   ));
   meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, "true");
   wand.setItemMeta(meta);
  }
  return wand;
 }

 /**
  * Checks if an ItemStack is the AutoInform Zone Selector Wand.
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
  * @return true if the action should be canceled, false otherwise.
  */
 private boolean checkAndWarn(Player player, Location location, Material material) {
  if (!bannedMaterials.contains(material)) {
   return false;
  }

  for (AutoInformZone zone : definedZones.values()) {
   if (zone.contains(location)) {
    getLogger().info(STR."Player \{player.getName()} attempted to place \{material.name()} at \{formatLocation(location)} in protected zone '\{zone.getName()}'.");
    player.sendMessage(ChatColor.RED + STR."You are not allowed to place \{material.name()} in zone '\{zone.getName()}'.");
    return true;
   }
  }
  return false;
 }

 @EventHandler
 public void onPlayerEmptyBucket(PlayerBucketEmptyEvent event) {
  if (bannedMaterials.contains(event.getBucket())) {
   Location placedLocation = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
   if (checkAndWarn(event.getPlayer(), placedLocation, event.getBucket())) {
    event.setCancelled(true);
   }
  }
 }

 @EventHandler
 public void onBlockPlace(BlockPlaceEvent event) {
  if (bannedMaterials.contains(event.getBlock().getType())) {
   if (checkAndWarn(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType())) {
    event.setCancelled(true);
   }
  }
 }

 @EventHandler
 public void onPlayerInteract(PlayerInteractEvent event) {
  Player player = event.getPlayer();
  ItemStack handItem = event.getItem();

  if (isWand(handItem)) {
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
   return;
  }

  if (handItem != null && bannedMaterials.contains(handItem.getType())) {
   if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
    if (handItem.getType() == Material.TNT_MINECART && event.getClickedBlock() != null && event.getClickedBlock().getType().name().contains("RAIL")) {
     Location placementLocation = event.getClickedBlock().getLocation().add(0, 1, 0);
     if (checkAndWarn(player, placementLocation, Material.TNT_MINECART)) {
      event.setCancelled(true);
     }
    }
   }
  }
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  if (!(sender instanceof Player player)) {
   if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
    loadZonesFromConfig();
    loadBannedMaterialsFromConfig();
    sender.sendMessage(ChatColor.GREEN + "AutoInform configuration reloaded.");
    if (definedZones.isEmpty()) {
     sender.sendMessage(ChatColor.YELLOW + "Warning: No zones defined in config or some worlds are invalid.");
    } else {
     sender.sendMessage(ChatColor.AQUA + STR."Successfully loaded \{definedZones.size()} zone(s).");
    }
    sender.sendMessage(ChatColor.AQUA + STR."Currently banned materials: \{formatMaterialList(bannedMaterials)}");
    return true;
   }
   sender.sendMessage("This command can only be run by a player (except for /ainformset reload).");
   return true;
  }

  if (!player.hasPermission("autoinform.admin.set")) {
   player.sendMessage(ChatColor.RED + "You do抔have permission to use this command.");
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
    player.sendMessage(ChatColor.GREEN + "You have received the AutoInform Zone Selector Wand!");
    break;

   case "pos1":
   case "pos2":
    if (args.length < 2) {
     player.sendMessage(ChatColor.RED + STR."Usage: /ainformset \{subCommand} <zone_name>");
     return true;
    }
    String zoneNameForPos = args[1];
    playerZoneSelections
            .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
            .computeIfAbsent(zoneNameForPos, k -> new HashMap<>())
            .put(subCommand, player.getLocation());
    player.sendMessage(ChatColor.GREEN + STR."Position '\{subCommand}' set for zone '\{zoneNameForPos}' to your current location: \{formatLocation(player.getLocation())}");
    playerWandPos1.remove(player.getUniqueId());
    playerWandPos2.remove(player.getUniqueId());
    break;

   case "define":
    if (args.length < 2) {
     player.sendMessage(ChatColor.RED + "Usage: /ainformset define <zone_name>");
     return true;
    }
    String zoneToDefine = args[1];
    Location p1 = playerWandPos1.get(player.getUniqueId());
    Location p2 = playerWandPos2.get(player.getUniqueId());

    if (p1 != null && p2 != null) {
     player.sendMessage(ChatColor.AQUA + "Using wand selections for zone '" + zoneToDefine + "'.");
    } else {
     Map<String, Location> playerSelections = playerZoneSelections.getOrDefault(player.getUniqueId(), Collections.emptyMap()).get(zoneToDefine);
     if (playerSelections == null || !playerSelections.containsKey("pos1") || !playerSelections.containsKey("pos2")) {
      player.sendMessage(ChatColor.RED + STR."You must set both positions for zone '\{zoneToDefine}' first using the wand or /ainformset \{zoneToDefine} pos1 and /ainformset \{zoneToDefine} pos2.");
      return true;
     }
     p1 = playerSelections.get("pos1");
     p2 = playerSelections.get("pos2");
    }

    if (!p1.getWorld().equals(p2.getWorld())) {
     player.sendMessage(ChatColor.RED + "Both positions must be in the same world.");
     playerWandPos1.remove(player.getUniqueId());
     playerWandPos2.remove(player.getUniqueId());
     return true;
    }

    definedZones.put(zoneToDefine, new AutoInformZone(zoneToDefine, p1.getWorld(), p1, p2));
    saveZonesToConfig();
    player.sendMessage(ChatColor.GREEN + STR."AutoInform zone '\{zoneToDefine}' defined and saved for world '\{p1.getWorld().getName()}'.");
    player.sendMessage(ChatColor.GRAY + "Corner 1: " + formatLocation(p1));
    player.sendMessage(ChatColor.GRAY + "Corner 2: " + formatLocation(p2));

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
     player.sendMessage(ChatColor.RED + "Usage: /ainformset remove <zone_name>");
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
     AutoInformZone zoneInfo = definedZones.get(zoneInfoName);
     if (zoneInfo != null) {
      player.sendMessage(ChatColor.AQUA + STR."--- AutoInform Zone: \{zoneInfo.getName()} ---");
      player.sendMessage(ChatColor.GOLD + "World: " + ChatColor.WHITE + zoneInfo.getWorld().getName());
      player.sendMessage(ChatColor.GOLD + "Corner 1: " + ChatColor.WHITE + formatLocation(zoneInfo.getCorner1()));
      player.sendMessage(ChatColor.GOLD + "Corner 2: " + ChatColor.WHITE + formatLocation(zoneInfo.getCorner2()));
     } else {
      player.sendMessage(ChatColor.YELLOW + STR."Zone '\{zoneInfoName}' not found.");
     }
    } else if (args.length == 1) {
     if (definedZones.isEmpty()) {
      player.sendMessage(ChatColor.YELLOW + "No AutoInform zones are currently defined.");
     } else {
      player.sendMessage(ChatColor.AQUA + "--- All AutoInform Zones ---");
      definedZones.values().forEach(zone ->
              player.sendMessage(ChatColor.GOLD + STR."- \{zone.getName()}: " + ChatColor.WHITE + STR."World: \{zone.getWorld().getName()}, C1: \{formatLocation(zone.getCorner1())}, C2: \{formatLocation(zone.getCorner2())}")
      );
     }
    } else {
     player.sendMessage(ChatColor.RED + "Usage: /ainformset info [zone_name]");
    }
    break;

   case "list":
    if (definedZones.isEmpty()) {
     player.sendMessage(ChatColor.YELLOW + "No AutoInform zones are currently defined.");
    } else {
     player.sendMessage(ChatColor.AQUA + "--- Defined AutoInform Zones ---");
     definedZones.keySet().forEach(zoneName -> player.sendMessage(ChatColor.GOLD + "- " + zoneName));
    }
    break;

   case "reload":
    loadZonesFromConfig();
    loadBannedMaterialsFromConfig();
    player.sendMessage(ChatColor.GREEN + "AutoInform configuration reloaded.");
    if (definedZones.isEmpty()) {
     player.sendMessage(ChatColor.YELLOW + "Warning: No zones defined in config or some worlds are invalid.");
    } else {
     player.sendMessage(ChatColor.AQUA + STR."Successfully loaded \{definedZones.size()} zone(s).");
    }
    player.sendMessage(ChatColor.AQUA + STR."Currently banned materials: \{formatMaterialList(bannedMaterials)}");
    break;

   case "clearwand":
    playerWandPos1.remove(player.getUniqueId());
    playerWandPos2.remove(player.getUniqueId());
    player.sendMessage(ChatColor.GREEN + "Your wand selections have been cleared.");
    break;

   case "banned":
    if (args.length < 2) {
     player.sendMessage(ChatColor.RED + "Usage: /ainformset banned <add|remove|list> [material_name]");
     return true;
    }
    String bannedAction = args[1].toLowerCase();
    switch (bannedAction) {
     case "add":
      if (args.length < 3) {
       player.sendMessage(ChatColor.RED + "Usage: /ainformset banned add <material_name>");
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
       player.sendMessage(ChatColor.RED + "Usage: /ainformset banned remove <material_name>");
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
      player.sendMessage(ChatColor.RED + "Unknown 'banned' subcommand. Usage: /ainformset banned <add|remove|list>");
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
  if (!cmd.getName().equalsIgnoreCase("ainformset")) {
   return Collections.emptyList();
  }

  if (!sender.hasPermission("autoinform.admin.set")) {
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
    return Arrays.stream(Material.values())
            .map(Enum::name)
            .filter(s -> s.startsWith(args[2].toUpperCase()))
            .collect(Collectors.toList());
   } else if (bannedAction.equals("remove")) {
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
  player.sendMessage(ChatColor.AQUA + "--- AutoInform Setter Help ---");
  player.sendMessage(ChatColor.GOLD + "/ainformset wand" + ChatColor.WHITE + " - Get the selection wand.");
  player.sendMessage(ChatColor.GOLD + "/ainformset <zone_name> pos1" + ChatColor.WHITE + " - Set first zone corner (manual).");
  player.sendMessage(ChatColor.GOLD + "/ainformset <zone_name> pos2" + ChatColor.WHITE + " - Set second zone corner (manual).");
  player.sendMessage(ChatColor.GOLD + "/ainformset <zone_name> define" + ChatColor.WHITE + " - Define/update <zone_name> using wand or manual selections.");
  player.sendMessage(ChatColor.GOLD + "/ainformset remove <zone_name>" + ChatColor.WHITE + " - Remove a defined zone.");
  player.sendMessage(ChatColor.GOLD + "/ainformset info [zone_name]" + ChatColor.WHITE + " - Show info for a specific zone or all zones.");
  player.sendMessage(ChatColor.GOLD + "/ainformset list" + ChatColor.WHITE + " - List all defined zones.");
  player.sendMessage(ChatColor.GOLD + "/ainformset clearwand" + ChatColor.WHITE + " - Clear your wand selections.");
  player.sendMessage(ChatColor.GOLD + "/ainformset reload" + ChatColor.WHITE + " - Reload all zones and banned materials from config.");
  player.sendMessage(ChatColor.GOLD + "/ainformset banned <add|remove|list>" + ChatColor.WHITE + " - Manage banned materials.");
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
