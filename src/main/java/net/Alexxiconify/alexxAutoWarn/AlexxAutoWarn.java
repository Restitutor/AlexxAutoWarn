package net.Alexxiconify.alexxAutoWarn; // Corrected package statement

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
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("ALL") // Suppress warnings for Java 21+ preview features like String Templates
public class AlexxAutoWarn extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

 // --- Plugin Constants ---
 private static final String COMMAND_NAME = "autoinform"; // Changed command name
 private static final String PERMISSION_ADMIN_SET = "autoinform.admin.set";
 private static final String PERMISSION_ALERT_RECEIVE = "autoinform.alert.receive";
 private static final String WAND_KEY_STRING = "ainform_wand";
 private static final String WAND_DISPLAY_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "AutoInform Zone Selector Wand";
 private static final List<String> WAND_LORE = Arrays.asList(
         ChatColor.GRAY + "Left-click: Set Position 1",
         ChatColor.GRAY + "Right-click: Set Position 2"
 );
 private static final String PLUGIN_PREFIX = ChatColor.RED + "[AutoInform] " + ChatColor.YELLOW;


 // --- Instance Variables ---
 private CoreProtectAPI coreProtectAPI;
 private final Map<String, AutoInformZone> definedZones = new HashMap<>();
 private final Set<Material> bannedMaterials = new HashSet<>();
 private final Map<UUID, Map<String, Map<String, Location>>> playerZoneSelections = new HashMap<>();
 private final Map<UUID, Location> playerWandPos1 = new HashMap<>();
 private final Map<UUID, Location> playerWandPos2 = new HashMap<>();
 private NamespacedKey wandKey;

 @Override
 public void onEnable() {
  this.wandKey = new NamespacedKey(this, WAND_KEY_STRING);
  this.coreProtectAPI = getCoreProtectAPI();

  if (this.coreProtectAPI == null) {
   getLogger().warning("CoreProtect API not found! Related functionality might be affected.");
  } else {
   getLogger().info("CoreProtect API hooked successfully.");
  }

  saveDefaultConfig();
  loadZonesFromConfig();
  loadBannedMaterialsFromConfig();

  Bukkit.getPluginManager().registerEvents(this, this);
  Objects.requireNonNull(getCommand(COMMAND_NAME)).setExecutor(this);
  Objects.requireNonNull(getCommand(COMMAND_NAME)).setTabCompleter(this);

  getLogger().info("AutoInform enabled!");
  if (definedZones.isEmpty()) {
   getLogger().warning(STR."No AutoInform zones are currently defined. Use /\{COMMAND_NAME} commands to define them.");
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

 /** Loads all AutoInform zones from config.yml. */
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
    Location corner1 = new Location(world, zoneConfig.getDouble("corner1.x"), zoneConfig.getDouble("corner1.y"), zoneConfig.getDouble("corner1.z"));
    Location corner2 = new Location(world, zoneConfig.getDouble("corner2.x"), zoneConfig.getDouble("corner2.y"), zoneConfig.getDouble("corner2.z"));
    boolean denyPlacement = zoneConfig.getBoolean("deny-placement", false);

    definedZones.put(zoneName, new AutoInformZone(zoneName, world, corner1, corner2, denyPlacement));
    getLogger().info(STR."Loaded zone '\{zoneName}' in world '\{worldName}' (Deny: \{denyPlacement}).");
   } catch (Exception e) {
    getLogger().severe(STR."Error loading coordinates for zone '\{zoneName}': \{e.getMessage()}. Skipping.");
   }
  }
 }

 /** Saves all currently defined AutoInform zones to config.yml. */
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
    config.set(path + "deny-placement", zone.shouldDenyPlacement());
   }
  }
  saveConfig();
 }

 /** Loads banned materials from config.yml. */
 private void loadBannedMaterialsFromConfig() {
  bannedMaterials.clear();
  FileConfiguration config = getConfig();
  List<String> materialNames = config.getStringList("banned-materials");

  for (String name : materialNames) {
   try {
    bannedMaterials.add(Material.valueOf(name.toUpperCase()));
   } catch (IllegalArgumentException e) {
    getLogger().warning(STR."Invalid material name '\{name}' found in config.yml banned-materials list. Skipping.");
   }
  }
 }

 /** Saves currently banned materials to config.yml. */
 private void saveBannedMaterialsToConfig() {
  FileConfiguration config = getConfig();
  config.set("banned-materials", bannedMaterials.stream().map(Enum::name).collect(Collectors.toList()));
  saveConfig();
 }

 /** Attempts to get the CoreProtect API instance. */
 private CoreProtectAPI getCoreProtectAPI() {
  Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
  if (plugin instanceof CoreProtect coreProtect) {
   CoreProtectAPI api = coreProtect.getAPI();
   if (api != null && api.isEnabled()) {
    if (api.APIVersion() < 10) {
     getLogger().warning(STR."CoreProtect API version is outdated (found: \{api.APIVersion()}). Please update CoreProtect.");
    }
    return api;
   } else {
    getLogger().warning("CoreProtect API is not enabled.");
   }
  } else {
   getLogger().warning("CoreProtect plugin not found or is not an instance of CoreProtect.");
  }
  return null;
 }

 /** Creates the AutoInform Zone Selector Wand item. */
 private ItemStack createWand() {
  ItemStack wand = new ItemStack(Material.WOODEN_AXE);
  ItemMeta meta = wand.getItemMeta();
  if (meta != null) {
   meta.setDisplayName(WAND_DISPLAY_NAME);
   meta.setLore(WAND_LORE);
   meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, "true");
   wand.setItemMeta(meta);
  }
  return wand;
 }

 /** Checks if an ItemStack is the AutoInform Zone Selector Wand. */
 private boolean isWand(ItemStack item) {
  return item != null && item.hasItemMeta() && Objects.requireNonNull(item.getItemMeta()).getPersistentDataContainer().has(wandKey, PersistentDataType.STRING);
 }

 /** Processes banned material placement: logs, alerts staff, and determines if denied. */
 private boolean processBannedMaterialPlacement(Player player, Location location, Material material) {
  if (!bannedMaterials.contains(material)) return false;

  boolean shouldDeny = false;
  AutoInformZone applicableZone = null;

  for (AutoInformZone zone : definedZones.values()) {
   if (zone.contains(location)) {
    applicableZone = zone;
    if (zone.shouldDenyPlacement()) {
     shouldDeny = true;
    }
    break;
   }
  }

  if (applicableZone != null) {
   String actionStatus = shouldDeny ? "DENIED" : "ALERTED";
   String logMessage = STR."Player \{player.getName()} attempted to place banned material \{material.name()} at \{formatLocation(location)} in protected zone '\{applicableZone.getName()}'. Action: \{actionStatus}.";
   getLogger().info(logMessage);

   String staffActionColor = shouldDeny ? ChatColor.RED.toString() : ChatColor.YELLOW.toString();
   String staffMessage = PLUGIN_PREFIX + STR."\{player.getName()} placed \{material.name()} in zone '\{applicableZone.getName()}' at \{location.getBlockX()},\{location.getBlockY()},\{location.getBlockZ()}. Action: \{staffActionColor}\{actionStatus}.";
   Bukkit.getOnlinePlayers().stream()
           .filter(staff -> staff.hasPermission(PERMISSION_ALERT_RECEIVE))
           .forEach(staff -> staff.sendMessage(staffMessage));
  }
  return shouldDeny;
 }

 @EventHandler
 public void onPlayerEmptyBucket(PlayerBucketEmptyEvent event) {
  Location placedLocation = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
  if (processBannedMaterialPlacement(event.getPlayer(), placedLocation, event.getBucket())) {
   event.setCancelled(true);
   event.getPlayer().sendMessage(ChatColor.RED + "You are not allowed to place " + event.getBucket().name() + " here!");
  }
 }

 @EventHandler
 public void onBlockPlace(BlockPlaceEvent event) {
  if (processBannedMaterialPlacement(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType())) {
   event.setCancelled(true);
   event.getPlayer().sendMessage(ChatColor.RED + "You are not allowed to place " + event.getBlock().getType().name() + " here!");
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

  if (handItem != null && bannedMaterials.contains(handItem.getType()) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
   if (handItem.getType() == Material.TNT_MINECART && event.getClickedBlock() != null && event.getClickedBlock().getType().name().contains("RAIL")) {
    Location placementLocation = event.getClickedBlock().getLocation().add(0, 1, 0);
    if (processBannedMaterialPlacement(player, placementLocation, Material.TNT_MINECART)) {
     event.setCancelled(true);
     player.sendMessage(ChatColor.RED + "You are not allowed to place " + Material.TNT_MINECART.name() + " here!");
    }
   }
  }
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  if (!command.getName().equalsIgnoreCase(COMMAND_NAME) && !command.getName().equalsIgnoreCase("ainform")) return false; // Check for alias too

  if (!(sender instanceof Player player)) {
   if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
    handleReloadCommand(sender);
    return true;
   }
   sender.sendMessage(ChatColor.RED + STR."This command can only be run by a player (except for /\{COMMAND_NAME} reload).");
   return true;
  }

  if (!player.hasPermission(PERMISSION_ADMIN_SET)) {
   player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
   return true;
  }

  if (args.length < 1) {
   sendHelpMessage(player);
   return true;
  }

  String subCommand = args[0].toLowerCase();
  switch (subCommand) {
   case "wand": handleWandCommand(player); break;
   case "pos1":
   case "pos2": handlePosCommand(player, subCommand, args); break;
   case "define": handleDefineCommand(player, args); break;
   case "remove": handleRemoveCommand(player, args); break;
   case "info": handleInfoCommand(player, args); break;
   case "list": handleListCommand(player); break;
   case "reload": handleReloadCommand(player); break;
   case "clearwand": handleClearWandCommand(player); break;
   case "banned": handleBannedCommand(player, args); break;
   case "deny": handleDenyCommand(player, args); break;
   default: sendHelpMessage(player); break;
  }
  return true;
 }

 // --- Command Handlers ---

 private void handleWandCommand(Player player) {
  player.getInventory().addItem(createWand());
  player.sendMessage(ChatColor.GREEN + "You have received the AutoInform Zone Selector Wand!");
 }

 private void handlePosCommand(Player player, String posType, String[] args) {
  if (args.length < 2) {
   player.sendMessage(ChatColor.RED + STR."Usage: /\{COMMAND_NAME} \{posType} <zone_name>");
   return;
  }
  String zoneNameForPos = args[1];
  playerZoneSelections.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).computeIfAbsent(zoneNameForPos, k -> new HashMap<>()).put(posType, player.getLocation());
  player.sendMessage(ChatColor.GREEN + STR."Position '\{posType}' set for zone '\{zoneNameForPos}' to your current location: \{formatLocation(player.getLocation())}");
  playerWandPos1.remove(player.getUniqueId());
  playerWandPos2.remove(player.getUniqueId());
 }

 private void handleDefineCommand(Player player, String[] args) {
  if (args.length < 2) {
   player.sendMessage(ChatColor.RED + STR."Usage: /\{COMMAND_NAME} define <zone_name>");
   return;
  }
  String zoneToDefine = args[1];
  Location p1 = playerWandPos1.get(player.getUniqueId());
  Location p2 = playerWandPos2.get(player.getUniqueId());

  if (p1 == null || p2 == null) {
   Map<String, Location> playerSelections = playerZoneSelections.getOrDefault(player.getUniqueId(), Collections.emptyMap()).get(zoneToDefine);
   if (playerSelections == null || !playerSelections.containsKey("pos1") || !playerSelections.containsKey("pos2")) {
    player.sendMessage(ChatColor.RED + STR."You must set both positions for zone '\{zoneToDefine}' first using the wand or /\{COMMAND_NAME} \{zoneToDefine} pos1 and /\{COMMAND_NAME} \{zoneToDefine} pos2.");
    return;
   }
   p1 = playerSelections.get("pos1");
   p2 = playerSelections.get("pos2");
   player.sendMessage(ChatColor.AQUA + "Using manual selections for zone '" + zoneToDefine + "'.");
  } else {
   player.sendMessage(ChatColor.AQUA + "Using wand selections for zone '" + zoneToDefine + "'.");
  }

  if (!p1.getWorld().equals(p2.getWorld())) {
   player.sendMessage(ChatColor.RED + "Both positions must be in the same world.");
   playerWandPos1.remove(player.getUniqueId());
   playerWandPos2.remove(player.getUniqueId());
   return;
  }

  boolean currentDenySetting = definedZones.containsKey(zoneToDefine) && definedZones.get(zoneToDefine).shouldDenyPlacement();

  definedZones.put(zoneToDefine, new AutoInformZone(zoneToDefine, p1.getWorld(), p1, p2, currentDenySetting));
  saveZonesToConfig();
  player.sendMessage(ChatColor.GREEN + STR."AutoInform zone '\{zoneToDefine}' defined and saved for world '\{p1.getWorld().getName()}'.");
  player.sendMessage(ChatColor.GRAY + "Corner 1: " + formatLocation(p1));
  player.sendMessage(ChatColor.GRAY + "Corner 2: " + formatLocation(p2));
  player.sendMessage(ChatColor.GRAY + "Deny Placement: " + currentDenySetting);

  playerWandPos1.remove(player.getUniqueId());
  playerWandPos2.remove(player.getUniqueId());
  if (playerZoneSelections.get(player.getUniqueId()) != null) {
   playerZoneSelections.get(player.getUniqueId()).remove(zoneToDefine);
   if (playerZoneSelections.get(player.getUniqueId()).isEmpty()) {
    playerZoneSelections.remove(player.getUniqueId());
   }
  }
 }

 private void handleRemoveCommand(Player player, String[] args) {
  if (args.length < 2) {
   player.sendMessage(ChatColor.RED + STR."Usage: /\{COMMAND_NAME} remove <zone_name>");
   return;
  }
  String zoneToRemove = args[1];
  if (definedZones.remove(zoneToRemove) != null) {
   saveZonesToConfig();
   player.sendMessage(ChatColor.GREEN + STR."Zone '\{zoneToRemove}' has been removed.");
  } else {
   player.sendMessage(ChatColor.YELLOW + STR."Zone '\{zoneToRemove}' not found.");
  }
 }

 private void handleInfoCommand(Player player, String[] args) {
  if (args.length == 2) {
   String zoneInfoName = args[1];
   AutoInformZone zoneInfo = definedZones.get(zoneInfoName);
   if (zoneInfo != null) {
    player.sendMessage(ChatColor.AQUA + STR."--- AutoInform Zone: \{zoneInfo.getName()} ---");
    player.sendMessage(ChatColor.GOLD + "World: " + ChatColor.WHITE + zoneInfo.getWorld().getName());
    player.sendMessage(ChatColor.GOLD + "Corner 1: " + ChatColor.WHITE + formatLocation(zoneInfo.getCorner1()));
    player.sendMessage(ChatColor.GOLD + "Corner 2: " + ChatColor.WHITE + formatLocation(zoneInfo.getCorner2()));
    player.sendMessage(ChatColor.GOLD + "Deny Placement: " + ChatColor.WHITE + zoneInfo.shouldDenyPlacement());
   } else {
    player.sendMessage(ChatColor.YELLOW + STR."Zone '\{zoneInfoName}' not found.");
   }
  } else {
   if (definedZones.isEmpty()) {
    player.sendMessage(ChatColor.YELLOW + "No AutoInform zones are currently defined.");
   } else {
    player.sendMessage(ChatColor.AQUA + "--- All AutoInform Zones ---");
    definedZones.values().forEach(zone -> player.sendMessage(ChatColor.GOLD + STR."- \{zone.getName()}: " + ChatColor.WHITE + STR."World: \{zone.getWorld().getName()}, Deny: \{zone.shouldDenyPlacement()}"));
   }
  }
 }

 private void handleListCommand(Player player) {
  if (definedZones.isEmpty()) {
   player.sendMessage(ChatColor.YELLOW + "No AutoInform zones are currently defined.");
  } else {
   player.sendMessage(ChatColor.AQUA + "--- Defined AutoInform Zones ---");
   definedZones.keySet().forEach(zoneName -> player.sendMessage(ChatColor.GOLD + "- " + zoneName));
  }
 }

 private void handleReloadCommand(CommandSender sender) {
  loadZonesFromConfig();
  loadBannedMaterialsFromConfig();
  sender.sendMessage(ChatColor.GREEN + "AutoInform configuration reloaded.");
  if (definedZones.isEmpty()) {
   sender.sendMessage(ChatColor.YELLOW + "Warning: No zones defined in config or some worlds are invalid.");
  } else {
   sender.sendMessage(ChatColor.AQUA + STR."Successfully loaded \{definedZones.size()} zone(s).");
  }
  sender.sendMessage(ChatColor.AQUA + STR."Currently banned materials: \{formatMaterialList(bannedMaterials)}");
 }

 private void handleClearWandCommand(Player player) {
  playerWandPos1.remove(player.getUniqueId());
  playerWandPos2.remove(player.getUniqueId());
  player.sendMessage(ChatColor.GREEN + "Your wand selections have been cleared.");
 }

 private void handleBannedCommand(Player player, String[] args) {
  if (args.length < 2) {
   player.sendMessage(ChatColor.RED + STR."Usage: /\{COMMAND_NAME} banned <add|remove|list> [material_name]");
   return;
  }
  String bannedAction = args[1].toLowerCase();
  switch (bannedAction) {
   case "add":
    if (args.length < 3) {
     player.sendMessage(ChatColor.RED + STR."Usage: /\{COMMAND_NAME} banned add <material_name>");
     return;
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
     player.sendMessage(ChatColor.RED + STR."Usage: /\{COMMAND_NAME} banned remove <material_name>");
     return;
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
    player.sendMessage(ChatColor.RED + STR."Unknown 'banned' subcommand. Usage: /\{COMMAND_NAME} banned <add|remove|list>");
    break;
  }
 }

 private void handleDenyCommand(Player player, String[] args) {
  if (args.length < 3) {
   player.sendMessage(ChatColor.RED + STR."Usage: /\{COMMAND_NAME} deny <zone_name> <true|false>");
   return;
  }
  String zoneName = args[1];
  String valueString = args[2].toLowerCase();
  boolean denyValue;

  if ("true".equals(valueString)) {
   denyValue = true;
  } else if ("false".equals(valueString)) {
   denyValue = false;
  } else {
   player.sendMessage(ChatColor.RED + "Invalid value. Please use 'true' or 'false'.");
   return;
  }

  AutoInformZone existingZone = definedZones.get(zoneName);
  if (existingZone == null) {
   player.sendMessage(ChatColor.YELLOW + STR."Zone '\{zoneName}' not found.");
   return;
  }

  AutoInformZone updatedZone = new AutoInformZone(existingZone.getName(), existingZone.getWorld(), existingZone.getCorner1(), existingZone.getCorner2(), denyValue);
  definedZones.put(zoneName, updatedZone);
  saveZonesToConfig();
  player.sendMessage(ChatColor.GREEN + STR."Deny placement for zone '\{zoneName}' set to \{denyValue}.");
 }

 @Override
 public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
  // Use COMMAND_NAME for primary check, and alias for secondary if needed.
  // The command.getName() will be either "autoinform" or "ainform"
  if (!cmd.getName().equalsIgnoreCase(COMMAND_NAME) && !cmd.getName().equalsIgnoreCase("ainform")) {
   return Collections.emptyList();
  }

  if (!sender.hasPermission(PERMISSION_ADMIN_SET)) {
   return Collections.emptyList();
  }

  if (args.length == 1) {
   return Arrays.asList("wand", "pos1", "pos2", "define", "remove", "info", "list", "reload", "clearwand", "banned", "deny").stream()
           .filter(s -> s.startsWith(args[0].toLowerCase()))
           .collect(Collectors.toList());
  } else if (args.length == 2) {
   String subCommand = args[0].toLowerCase();
   if (Arrays.asList("pos1", "pos2", "define", "remove", "info", "deny").contains(subCommand)) {
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
    return Arrays.stream(Material.values()).map(Enum::name).filter(s -> s.startsWith(args[2].toUpperCase())).collect(Collectors.toList());
   } else if (bannedAction.equals("remove")) {
    return bannedMaterials.stream().map(Enum::name).filter(s -> s.startsWith(args[2].toUpperCase())).collect(Collectors.toList());
   }
  } else if (args.length == 3 && args[0].equalsIgnoreCase("deny")) {
   return Arrays.asList("true", "false").stream()
           .filter(s -> s.startsWith(args[2].toLowerCase()))
           .collect(Collectors.toList());
  }
  return Collections.emptyList();
 }

 /** Sends the help message to the player. */
 private void sendHelpMessage(Player player) {
  player.sendMessage(ChatColor.AQUA + "--- AutoInform Setter Help ---");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} wand" + ChatColor.WHITE + " - Get the selection wand.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} <zone_name> pos1" + ChatColor.WHITE + " - Set first zone corner (manual).");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} <zone_name> pos2" + ChatColor.WHITE + " - Set second zone corner (manual).");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} <zone_name> define" + ChatColor.WHITE + " - Define/update <zone_name> using wand or manual selections.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} <zone_name> deny <true|false>" + ChatColor.WHITE + " - Toggle placement denial for a zone.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} remove <zone_name>" + ChatColor.WHITE + " - Remove a defined zone.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} info [zone_name]" + ChatColor.WHITE + " - Show info for a specific zone or all zones.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} list" + ChatColor.WHITE + " - List all defined zones.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} clearwand" + ChatColor.WHITE + " - Clear your wand selections.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} reload" + ChatColor.WHITE + " - Reload all zones and banned materials from config.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} banned <add|remove|list>" + ChatColor.WHITE + " - Manage banned materials.");
  player.sendMessage(ChatColor.YELLOW + "Note: Placement will be denied in zones set to 'deny-placement: true'. Otherwise, staff will be alerted.");
 }

 /** Formats a Location object into a readable string. */
 private String formatLocation(Location loc) {
  if (loc == null) return "N/A";
  return String.format("X: %.1f, Y: %.1f, Z: %.1f (World: %s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
 }

 /** Formats a set of Materials into a comma-separated string. */
 private String formatMaterialList(Set<Material> materials) {
  if (materials.isEmpty()) return "None";
  return materials.stream().map(Enum::name).collect(Collectors.joining(", "));
 }
}

/**
 * Represents a defined AutoInform zone with two corners, a world, and a denial setting.
 * This class is immutable.
 */
class AutoInformZone {
 private final String name;
 private final World world;
 private final Location corner1;
 private final Location corner2;
 private final boolean denyPlacement;

 /**
  * Constructs a new AutoInformZone.
  * @param name The unique name of the zone.
  * @param world The world the zone is in.
  * @param corner1 The first corner of the zone.
  * @param corner2 The second corner of the zone.
  * @param denyPlacement True if placement should be denied in this zone, false for alerts only.
  */
 public AutoInformZone(@NotNull String name, @NotNull World world, @NotNull Location corner1, @NotNull Location corner2, boolean denyPlacement) {
  this.name = Objects.requireNonNull(name, "Zone name cannot be null");
  this.world = Objects.requireNonNull(world, "Zone world cannot be null");
  this.corner1 = Objects.requireNonNull(corner1, "Zone corner1 cannot be null");
  this.corner2 = Objects.requireNonNull(corner2, "Zone corner2 cannot be null");
  this.denyPlacement = denyPlacement;
 }

 public String getName() { return name; }
 public World getWorld() { return world; }
 public Location getCorner1() { return corner1; }
 public Location getCorner2() { return corner2; }
 public boolean shouldDenyPlacement() { return denyPlacement; }

 /** Checks if a given location is within this zone's bounding box. */
 public boolean contains(@NotNull Location loc) {
  if (!loc.getWorld().equals(this.world)) return false;

  double x = loc.getX();
  double y = loc.getY();
  double z = loc.getZ();

  double minX = Math.min(corner1.getX(), corner2.getX());
  double minY = Math.min(corner1.getY(), corner2.getY());
  double minZ = Math.min(corner1.getZ(), corner2.getZ());

  double maxX = Math.max(corner1.getX(), corner2.getX());
  double maxY = Math.max(corner1.getY(), corner2.getY());
  double maxZ = Math.max(corner1.getZ(), corner2.getZ());

  return (x >= minX && x <= maxX) && (y >= minY && y <= maxY) && (z >= minZ && z <= maxZ);
 }

 @Override
 public boolean equals(Object o) {
  if (this == o) return true;
  if (o == null || getClass() != o.getClass()) return false;
  AutoInformZone that = (AutoInformZone) o;
  return denyPlacement == that.denyPlacement &&
          name.equals(that.name) &&
          world.equals(that.world) &&
          corner1.equals(that.corner1) &&
          corner2.equals(that.corner2);
 }

 @Override
 public int hashCode() {
  return Objects.hash(name, world, corner1, corner2, denyPlacement);
 }
}