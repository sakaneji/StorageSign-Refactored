package storagesign.e2e;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class StorageSignE2EHarness extends JavaPlugin {

    private static final int BASE_Y = 65;
    private PermissionAttachment deniedUse;
    private UUID deniedPlayer;

    @Override
    public void onEnable() {
        getCommand("sstest").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 2) return false;
        String action = args[0].toLowerCase(Locale.ROOT);
        String scenario = args[1].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "reset" -> {
                    reset(player, scenario);
                    player.sendMessage("SSTEST READY " + scenario);
                }
                case "inspect" -> player.sendMessage("SSTEST " + snapshot(player, scenario));
                case "drop" -> {
                    player.getWorld().dropItem(
                        player.getLocation().clone().add(0, 0.25, 0),
                        new ItemStack(Material.STONE, 8)
                    ).setPickupDelay(0);
                    player.sendMessage("SSTEST DROPPED " + scenario);
                }
                case "place" -> {
                    placeStorageSignEvent(player);
                    player.sendMessage("SSTEST PLACED " + scenario);
                }
                case "sneak" -> {
                    player.setSneaking(Boolean.parseBoolean(scenario));
                    player.sendMessage("SSTEST SNEAK " + scenario);
                }
                default -> { return false; }
            }
        } catch (RuntimeException e) {
            getLogger().severe("Scenario " + scenario + " failed: " + e);
            player.sendMessage("SSTEST ERROR " + scenario + " " + e.getClass().getSimpleName());
        }
        return true;
    }

    private void reset(Player player, String scenario) {
        World world = player.getWorld();
        clearDeniedPermission(player);
        clearArea(world);
        player.getInventory().clear();
        player.setGameMode(GameMode.CREATIVE);
        player.setFlying(false);
        player.setVelocity(new Vector());
        player.teleport(new Location(world, 0.5, BASE_Y, 2.5, 180f, 0f));

        switch (scenario) {
            case "client", "special-potion", "special-banner" ->
                world.getBlockAt(0, BASE_Y - 1, 0).setType(Material.STONE);
            case "manual-export" -> createStorageSign(world, 0, BASE_Y, 0, "STONE", 128);
            case "manual-import" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", 0);
                player.getInventory().setItem(0, new ItemStack(Material.STONE, 64));
                player.getInventory().setItem(1, new ItemStack(Material.STONE, 16));
                player.getInventory().setHeldItemSlot(0);
            }
            case "sneak-import" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", 0);
                player.getInventory().setItem(0, new ItemStack(Material.STONE, 32));
                player.getInventory().setItem(1, new ItemStack(Material.STONE, 16));
                player.getInventory().setHeldItemSlot(0);
            }
            case "zero-export" -> createStorageSign(world, 0, BASE_Y, 0, "STONE", 1);
            case "permission-denied" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", 64);
                deniedUse = player.addAttachment(this, "storagesign.use", false);
                deniedPlayer = player.getUniqueId();
            }
            case "auto-import" -> prepareAutoImport(world);
            case "auto-export" -> prepareAutoExport(world);
            case "minecart-import" -> prepareMinecartImport(world);
            case "autocollect" -> {
                player.getInventory().setItem(1, new ItemStack(Material.STONE, 64));
                world.getBlockAt(0, BASE_Y - 1, 0).setType(Material.STONE);
            }
            case "restart" -> createStorageSign(world, 0, BASE_Y, 0, "STONE", 77);
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
    }

    private void clearDeniedPermission(Player player) {
        if (deniedUse != null && player.getUniqueId().equals(deniedPlayer)) {
            player.removeAttachment(deniedUse);
        }
        deniedUse = null;
        deniedPlayer = null;
    }

    private static void placeStorageSignEvent(Player player) {
        World world = player.getWorld();
        Block support = world.getBlockAt(0, BASE_Y - 1, 0);
        Block placed = world.getBlockAt(0, BASE_Y, 0);
        var replaced = placed.getState();
        ItemStack held = player.getInventory().getItemInMainHand().clone();
        placed.setType(Material.OAK_SIGN, false);
        BlockPlaceEvent event = new BlockPlaceEvent(
            placed, replaced, support, held, player, true, EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            replaced.update(true, false);
            throw new IllegalStateException("BlockPlaceEvent was cancelled");
        }
    }

    private static void clearArea(World world) {
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (!(entity instanceof Player)) entity.remove();
        }
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                for (int y = BASE_Y - 1; y <= BASE_Y + 5; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
                world.getBlockAt(x, BASE_Y - 2, z).setType(Material.STONE, false);
            }
        }
        // Removing containers can release their contents after the initial entity
        // cleanup. Clear those drops as part of the same atomic scenario reset.
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (!(entity instanceof Player)) entity.remove();
        }
    }

    private static void createStorageSign(World world, int x, int y, int z,
                                          String identifier, int amount) {
        Block support = world.getBlockAt(x, y - 1, z);
        support.setType(Material.STONE);
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        var front = sign.getSide(Side.FRONT);
        front.setLine(0, "StorageSign");
        front.setLine(1, identifier);
        front.setLine(2, Integer.toString(amount));
        front.setLine(3, summary(amount));
        sign.update(true, false);
    }

    private static void createWallStorageSign(World world, int x, int y, int z,
                                              org.bukkit.block.BlockFace facing,
                                              String identifier, int amount) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.OAK_WALL_SIGN, false);
        Directional data = (Directional) block.getBlockData();
        data.setFacing(facing);
        block.setBlockData(data, false);
        Sign sign = (Sign) block.getState();
        var front = sign.getSide(Side.FRONT);
        front.setLine(0, "StorageSign");
        front.setLine(1, identifier);
        front.setLine(2, Integer.toString(amount));
        front.setLine(3, summary(amount));
        sign.update(true, false);
    }

    private static String summary(int amount) {
        int rem = amount % 3456;
        return (amount / 3456) + "LC " + (rem / 64) + "s " + (rem % 64);
    }

    private static void prepareAutoImport(World world) {
        Block chestBlock = world.getBlockAt(0, BASE_Y, 0);
        chestBlock.setType(Material.CHEST);
        Chest chest = (Chest) chestBlock.getState();
        chest.getInventory().setItem(0, new ItemStack(Material.STONE, 64));
        createWallStorageSign(world, -1, BASE_Y, 0,
            org.bukkit.block.BlockFace.WEST, "STONE", 0);

        Block hopperBlock = world.getBlockAt(0, BASE_Y + 1, 0);
        hopperBlock.setType(Material.HOPPER);
        ((Hopper) hopperBlock.getState()).getInventory().setItem(0, new ItemStack(Material.STONE, 1));
    }

    private static void prepareAutoExport(World world) {
        Block chestBlock = world.getBlockAt(0, BASE_Y, 0);
        chestBlock.setType(Material.CHEST);
        ((Chest) chestBlock.getState()).getInventory().setItem(0, new ItemStack(Material.STONE, 1));
        createWallStorageSign(world, -1, BASE_Y, 0,
            org.bukkit.block.BlockFace.WEST, "STONE", 64);
        world.getBlockAt(0, BASE_Y - 1, 0).setType(Material.HOPPER);
    }

    private static void prepareMinecartImport(World world) {
        createWallStorageSign(world, -1, BASE_Y, 0,
            org.bukkit.block.BlockFace.WEST, "STONE", 0);
        world.getBlockAt(0, BASE_Y, 0).setType(Material.RAIL, false);
        HopperMinecart minecart = world.spawn(
            new Location(world, 0.5, BASE_Y, 0.5), HopperMinecart.class
        );
        minecart.getInventory().setItem(0, new ItemStack(Material.STONE, 64));
        Item dropped = world.dropItem(new Location(world, 0.5, BASE_Y + 0.25, 0.5),
            new ItemStack(Material.STONE, 1));
        dropped.setPickupDelay(0);
    }

    private static String snapshot(Player player, String scenario) {
        World world = player.getWorld();
        List<String> lines = signLines(world);
        int playerStone = count(player.getInventory().getContents(), Material.STONE);
        int playerSigns = count(player.getInventory().getContents(), Material.OAK_SIGN);
        int droppedStone = world.getEntitiesByClass(Item.class).stream()
            .map(Item::getItemStack)
            .filter(item -> item.getType() == Material.STONE)
            .mapToInt(ItemStack::getAmount)
            .sum();
        int chestStone = containerCount(world.getBlockAt(0, BASE_Y, 0), Material.STONE);
        int hopperStone = containerCount(world.getBlockAt(0, BASE_Y - 1, 0), Material.STONE)
            + containerCount(world.getBlockAt(0, BASE_Y + 1, 0), Material.STONE);
        int minecartStone = world.getEntitiesByClass(HopperMinecart.class).stream()
            .mapToInt(cart -> count(cart.getInventory().getContents(), Material.STONE))
            .sum();
        String heldLore = heldLore(player);
        return "{\"scenario\":\"" + escape(scenario) + "\","
            + "\"lines\":" + jsonArray(lines) + ","
            + "\"playerStone\":" + playerStone + ","
            + "\"playerSigns\":" + playerSigns + ","
            + "\"droppedStone\":" + droppedStone + ","
            + "\"chestStone\":" + chestStone + ","
            + "\"hopperStone\":" + hopperStone + ","
            + "\"minecartStone\":" + minecartStone + ","
            + "\"canPlace\":" + player.hasPermission("storagesign.place") + ","
            + "\"heldLore\":\"" + escape(heldLore) + "\"}";
    }

    private static List<String> signLines(World world) {
        for (int x = -1; x <= 1; x++) {
            for (int y = BASE_Y; y <= BASE_Y + 2; y++) {
                Block block = world.getBlockAt(x, y, 0);
                if (block.getState() instanceof Sign sign) {
                    return List.of(sign.getSide(Side.FRONT).getLines());
                }
            }
        }
        return List.of();
    }

    private static int containerCount(Block block, Material material) {
        if (!(block.getState() instanceof Container container)) return 0;
        return count(container.getInventory().getContents(), material);
    }

    private static int count(ItemStack[] items, Material material) {
        int total = 0;
        for (ItemStack item : items) {
            if (item != null && item.getType() == material) total += item.getAmount();
        }
        return total;
    }

    @SuppressWarnings("deprecation")
    private static String heldLore(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || !held.hasItemMeta() || held.getItemMeta().getLore() == null) return "";
        return String.join("|", held.getItemMeta().getLore());
    }

    private static String jsonArray(List<String> values) {
        return values.stream().map(value -> "\"" + escape(value) + "\"")
            .reduce("[", (left, value) -> left.equals("[") ? left + value : left + "," + value)
            + "]";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
