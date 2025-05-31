import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import static org.bukkit.Bukkit.getServer;

@SuppressWarnings("ConditionCoveredByFurtherCondition")
private CoreProtectAPI getCoreProtect() {
    Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");

    // Check that CoreProtect is loaded
    if (plugin == null || !(plugin instanceof CoreProtect)) {
        return null;
    }

    // Check that the API is enabled
    CoreProtectAPI CoreProtect = ((CoreProtect) plugin).getAPI();
    if (!CoreProtect.isEnabled()) {
        return null;
    }

    // Check that a compatible version of the API is loaded
    if (CoreProtect.APIVersion() < 10) {
        return null;
    }

    return CoreProtect;
}
public static final class AlexxAAutoWarn extends JavaPlugin {
    @Override
        public void onEnable() {
        // Plugin startup logic
        getLogger().info("Hello MineAcademy.org!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

void main() {
}
@SuppressWarnings("preview")
public static class LavaZoneDetector extends JavaPlugin implements Listener {

 // Define your zone's corners here
    // These are just examples, set them to your desired coordinates
    private Location zoneCorner1;
    private Location zoneCorner2;

 @SuppressWarnings("preview")
 @Override
    public void onEnable() {
        // Initialize CoreProtect API
     CoreProtectAPI coreProtectAPI = getCoreProtectAPI();
        if (coreProtectAPI == null) {
            getLogger().severe("CoreProtect API not found! Disabling plugin functionality related to CoreProtect.");
            // Potentially disable the plugin or parts of it
        } else {
            // You can log API version or other details if needed
            // Example: coreProtectAPI.testAPI(); will print to console if API is working
            getLogger().info("CoreProtect API hooked successfully.");
        }

        // Define your protected zone (example coordinates)
  // Or your specific world name
  String worldName = "world";
  World world = Bukkit.getWorld(worldName);
        if (world != null) {
            zoneCorner1 = new Location(world, 100, 64, 200); // Example corner 1
            zoneCorner2 = new Location(world, 150, 74, 250); // Example corner 2
        } else {
            getLogger().severe(STR."World '\{worldName}' not found for zone definition!");
        }


        // Register the event listener
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("LavaZoneDetector enabled!");
    }

    private CoreProtectAPI getCoreProtectAPI() {
        // Use Bukkit.getPluginManager() directly for wider compatibility and clarity
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");

        // Check that CoreProtect is loaded
        if (!(plugin instanceof CoreProtect)) {
            getLogger().warning("CoreProtect plugin not found or is not an instance of CoreProtect.");
            return null;
        }

        // Check that the API is enabled
        CoreProtectAPI api = ((CoreProtect) plugin).getAPI();
        if (!api.isEnabled()) {
            getLogger().warning("CoreProtect API is not enabled.");
            return null;
        }

        // Check API version (Example for API version 10, CoreProtect 22.4+)
        // Adjust if you are targeting a different API version.
        // You can check CoreProtect's documentation or pom.xml for API versioning details.
        if (api.APIVersion() < 10) { // This number might need to be adjusted based on the CoreProtect version you use
            getLogger().warning(STR."CoreProtect API version is outdated (found: \{api.APIVersion()}). Please update CoreProtect. Some features might not work.");
            // Depending on your needs, you might still return the api or null
            // return null;
        }

        return api;
    }

    @EventHandler
    public void onPlayerEmptyBucket(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Material bucketContent = event.getBucket(); // Material of the bucket item itself
        Location placedLocation = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation(); // Location where the liquid will flow

        // Check if it's a lava bucket
        if (bucketContent == Material.LAVA_BUCKET) {
            // Check if the placement is within the defined zone
            if (zoneCorner1 != null && zoneCorner2 != null && isInZone(placedLocation)) {
                // Player used a lava bucket within the specified zone
                getLogger().info(STR."Player \{player.getName()} used a LAVA bucket at \{placedLocation.getBlockX()}, \{placedLocation.getBlockY()}, \{placedLocation.getBlockZ()} in the protected zone!");

                // You can add your custom actions here:
                // - Cancel the event: event.setCancelled(true);
                // - Send a message to the player: player.sendMessage("You cannot place lava here!");
                // - Alert staff: Bukkit.broadcast("Admin Alert: " + player.getName() + " tried to place lava in a protected zone!", "AlexxAutoWarn.admin.alerts");
                // - Log with CoreProtect if you have custom flags or reasons (though placement is already logged by default)
                //   if (coreProtectAPI != null) {
                //       coreProtectAPI.logPlacement(player.getName(), placedLocation, Material.LAVA, null);
                //       // Note: CoreProtect already logs vanilla LAVA bucket placements.
                //       // This would be for custom logging or if you cancel the event and want to log the attempt.
                //   }

                // For this example, let's just send a message to the player and cancel it.
                player.sendMessage("§cYou are not allowed to place lava in this zone.");
                event.setCancelled(true);

            }
        }
    }

    /**
     * Checks if a location is within the defined cuboid zone.
     * Ensures that corner1 is min XYZ and corner2 is max XYZ.
     *
     * @param loc The location to check.
     * @return True if the location is within the zone, false otherwise.
     */
    private boolean isInZone(Location loc) {
        if (loc == null || zoneCorner1 == null || zoneCorner2 == null) {
            return false;
        }
        // Ensure the location is in the same world as the zone
        if (!loc.getWorld().equals(zoneCorner1.getWorld())) {
            return false;
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
    public void onDisable() {
        getLogger().info("LavaZoneDetector disabled!");
    }
}