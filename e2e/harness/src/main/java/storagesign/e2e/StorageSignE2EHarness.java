package storagesign.e2e;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.block.Dropper;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.entity.ChestBoat;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class StorageSignE2EHarness extends JavaPlugin {

    private static final int BASE_Y = 65;
    private PermissionAttachment deniedUse;
    private UUID deniedPlayer;
    private boolean lastBreakCancelled;
    private boolean lastBreakDrops;
    private List<String> lastEditLines = List.of();

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
                case "admin" -> {
                    player.addAttachment(this, "storagesign.index.admin", true);
                    player.addAttachment(this, "storagesign.search.admin", true);
                    player.updateCommands();
                    player.sendMessage("SSTEST ADMIN " + scenario);
                }
                case "move" -> {
                    player.teleport(player.getLocation().add(2, 0, 0));
                    player.sendMessage("SSTEST MOVED " + scenario);
                }
                case "stash" -> {
                    stashOminousBanner(player);
                    player.sendMessage("SSTEST STASHED " + scenario);
                }
                case "unstash" -> {
                    unstashOminousBanner(player);
                    player.sendMessage("SSTEST UNSTASHED " + scenario);
                }
                case "interact" -> {
                    interactWithStorageSign(player);
                    player.sendMessage("SSTEST INTERACTED " + scenario);
                }
                case "break" -> {
                    breakStorageSign(player);
                    player.sendMessage("SSTEST BROKEN " + scenario);
                }
                case "edit" -> {
                    editStorageSign(player);
                    player.sendMessage("SSTEST EDITED " + scenario);
                }
                case "dispense" -> {
                    dispenseToWorld(player);
                    player.sendMessage("SSTEST DISPENSED " + scenario);
                }
                case "double-interact" -> {
                    interactWithStorageSign(player);
                    interactWithStorageSign(player);
                    player.sendMessage("SSTEST DOUBLE " + scenario);
                }
                case "break-support" -> {
                    breakSupportBlock(player);
                    player.sendMessage("SSTEST SUPPORT " + scenario);
                }
                case "boat-transfer" -> {
                    transferIntoChestBoat(player);
                    player.sendMessage("SSTEST BOAT " + scenario);
                }
                case "storage-minecart-transfer" -> {
                    transferIntoStorageMinecart(player);
                    player.sendMessage("SSTEST STORAGE-MINECART " + scenario);
                }
                case "double-transfer" -> {
                    transferIntoDoubleChest(player);
                    player.sendMessage("SSTEST DOUBLECHEST " + scenario);
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
        lastBreakCancelled = false;
        lastBreakDrops = true;
        lastEditLines = List.of();

        switch (scenario) {
            case "client", "special-potion", "special-banner" ->
                world.getBlockAt(0, BASE_Y - 1, 0).setType(Material.STONE);
            case "banner-upgrade-seed" -> {
                world.getBlockAt(0, BASE_Y - 1, 0).setType(Material.STONE);
                preparePotionUpgradeSign(player);
            }
            case "manual-export" -> createStorageSign(world, 0, BASE_Y, 0, "STONE", 128);
            case "mismatch-export" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", 64);
                player.getInventory().setItem(0, new ItemStack(Material.DIRT));
                player.getInventory().setHeldItemSlot(0);
            }
            case "register-empty" -> {
                createStorageSign(world, 0, BASE_Y, 0, "", 0);
                player.getInventory().setItem(0, new ItemStack(Material.STONE, 16));
                player.getInventory().setHeldItemSlot(0);
            }
            case "full-inventory-export" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", 64);
                for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
                }
                player.getInventory().setHeldItemSlot(0);
            }
            case "double-interact" -> createStorageSign(world, 0, BASE_Y, 0, "STONE", 128);
            case "attached-sign" -> createStorageSign(world, 0, BASE_Y, 0, "STONE", 64);
            case "manual-import" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", 0);
                player.getInventory().setItem(0, new ItemStack(Material.STONE, 64));
                player.getInventory().setItem(1, new ItemStack(Material.STONE, 16));
                player.getInventory().setHeldItemSlot(0);
            }
            case "overflow-import" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", Integer.MAX_VALUE - 2);
                player.getInventory().setItem(0, new ItemStack(Material.STONE, 8));
                player.getInventory().setHeldItemSlot(0);
            }
            case "merge-partial" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", Integer.MAX_VALUE - 10);
                giveRegisteredStorageSigns(player, "STONE", 10, 2);
            }
            case "merge-partial-full" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", Integer.MAX_VALUE - 10);
                giveRegisteredStorageSigns(player, "STONE", 10, 2);
                for (int slot = 1; slot < player.getInventory().getStorageContents().length; slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
                }
            }
            case "full-command" -> {
                for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
                }
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
            case "break-denied" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", 64);
                deniedUse = player.addAttachment(this, "storagesign.break", false);
                deniedPlayer = player.getUniqueId();
            }
            case "break-allowed", "edit-protected" ->
                createStorageSign(world, 0, BASE_Y, 0, "STONE", 64);
            case "storage-sign-items" ->
                createStorageSign(world, 0, BASE_Y, 0, "OakStorageSign", 2);
            case "divide" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", 100);
                giveEmptyStorageSigns(player, 2);
            }
            case "divide-sneak" -> {
                createStorageSign(world, 0, BASE_Y, 0, "STONE", 200000);
                giveEmptyStorageSigns(player, 2);
            }
            case "auto-import" -> prepareAutoImport(world);
            case "auto-export" -> prepareAutoExport(world);
            case "minecart-import" -> prepareMinecartImport(world);
            case "minecart-export" -> prepareMinecartExport(world);
            case "world-dispense" -> prepareWorldDispense(world);
            case "world-dispenser" -> prepareWorldDispense(world, Material.DISPENSER);
            case "world-crafter" -> prepareWorldDispense(world, Material.CRAFTER);
            case "chest-boat-import" -> prepareChestBoatImport(world);
            case "chest-minecart-import" -> prepareStorageMinecartImport(world);
            case "double-chest-import" -> prepareDoubleChestImport(world);
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

    @SuppressWarnings("deprecation")
    private static void giveEmptyStorageSigns(Player player, int amount) {
        ItemStack item = new ItemStack(Material.OAK_SIGN, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("StorageSign");
        meta.setLore(List.of("Empty"));
        item.setItemMeta(meta);
        player.getInventory().setItem(0, item);
        player.getInventory().setHeldItemSlot(0);
    }

    @SuppressWarnings("deprecation")
    private static void giveRegisteredStorageSigns(Player player, String identifier,
                                                   int storedPerSign, int amount) {
        ItemStack item = new ItemStack(Material.OAK_SIGN, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("StorageSign");
        meta.setLore(List.of(identifier + " " + storedPerSign));
        item.setItemMeta(meta);
        player.getInventory().setItem(0, item);
        player.getInventory().setHeldItemSlot(0);
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
        Hopper hopper = (Hopper) hopperBlock.getState();
        hopper.getInventory().setItem(0, new ItemStack(Material.STONE, 64));
        hopper.getInventory().setItem(1, new ItemStack(Material.STONE, 64));
        hopper.getInventory().setItem(2, new ItemStack(Material.STONE, 64));
    }

    private static void prepareAutoExport(World world) {
        Block chestBlock = world.getBlockAt(0, BASE_Y, 0);
        chestBlock.setType(Material.CHEST);
        ((Chest) chestBlock.getState()).getInventory().setItem(0, new ItemStack(Material.STONE, 64));
        createWallStorageSign(world, -1, BASE_Y, 0,
            org.bukkit.block.BlockFace.WEST, "STONE", 192);
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

    private static void prepareMinecartExport(World world) {
        createWallStorageSign(world, -1, BASE_Y, 0,
            org.bukkit.block.BlockFace.WEST, "STONE", 192);
        world.getBlockAt(0, BASE_Y, 0).setType(Material.RAIL, false);
        HopperMinecart minecart = world.spawn(
            new Location(world, 0.5, BASE_Y, 0.5), HopperMinecart.class
        );
        minecart.getInventory().setItem(0, new ItemStack(Material.STONE, 64));
        world.getBlockAt(0, BASE_Y - 1, 0).setType(Material.HOPPER);
    }

    private static void prepareWorldDispense(World world) {
        prepareWorldDispense(world, Material.DROPPER);
    }

    private static void prepareWorldDispense(World world, Material type) {
        Block dropperBlock = world.getBlockAt(0, BASE_Y, 0);
        dropperBlock.setType(type);
        ((Container) dropperBlock.getState()).getInventory().setItem(0, new ItemStack(Material.STONE));
        createWallStorageSign(world, -1, BASE_Y, 0,
            org.bukkit.block.BlockFace.WEST, "STONE", 64);
    }

    private static void prepareChestBoatImport(World world) {
        createWallStorageSign(world, -1, BASE_Y, 0,
            org.bukkit.block.BlockFace.WEST, "STONE", 0);
        ChestBoat boat = (ChestBoat) world.spawnEntity(
            new Location(world, 0.5, BASE_Y, 0.5), EntityType.OAK_CHEST_BOAT
        );
        boat.setGravity(false);
        boat.getInventory().setItem(0, new ItemStack(Material.STONE, 64));
    }

    private static void prepareStorageMinecartImport(World world) {
        createWallStorageSign(world, -1, BASE_Y, 0, BlockFace.WEST, "STONE", 0);
        world.getBlockAt(0, BASE_Y, 0).setType(Material.RAIL, false);
        StorageMinecart minecart = (StorageMinecart) world.spawnEntity(
            new Location(world, 0.5, BASE_Y, 0.5), EntityType.CHEST_MINECART
        );
        minecart.getInventory().setItem(0, new ItemStack(Material.STONE, 64));
    }

    private static void prepareDoubleChestImport(World world) {
        Block left = world.getBlockAt(0, BASE_Y, 0);
        Block right = world.getBlockAt(1, BASE_Y, 0);
        left.setType(Material.CHEST, false);
        right.setType(Material.CHEST, false);
        org.bukkit.block.data.type.Chest leftData =
            (org.bukkit.block.data.type.Chest) left.getBlockData();
        org.bukkit.block.data.type.Chest rightData =
            (org.bukkit.block.data.type.Chest) right.getBlockData();
        leftData.setFacing(BlockFace.NORTH);
        rightData.setFacing(BlockFace.NORTH);
        leftData.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
        rightData.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
        left.setBlockData(leftData, false);
        right.setBlockData(rightData, false);
        ((Chest) left.getState()).getInventory().setItem(0, new ItemStack(Material.STONE, 64));
        createWallStorageSign(world, -1, BASE_Y, 0, BlockFace.WEST, "STONE", 0);
    }

    private static void transferIntoDoubleChest(Player player) {
        Inventory destination = ((Chest) player.getWorld().getBlockAt(0, BASE_Y, 0).getState())
            .getInventory();
        Block sourceBlock = player.getWorld().getBlockAt(3, BASE_Y, 0);
        sourceBlock.setType(Material.CHEST);
        Inventory source = ((Chest) sourceBlock.getState()).getInventory();
        Bukkit.getPluginManager().callEvent(new InventoryMoveItemEvent(
            source, new ItemStack(Material.STONE), destination, true
        ));
    }

    private static void transferIntoChestBoat(Player player) {
        ChestBoat boat = player.getWorld().getEntitiesByClass(ChestBoat.class).stream()
            .findFirst().orElseThrow(() -> new IllegalStateException("Chest boat is missing"));
        boat.teleport(new Location(player.getWorld(), 0.5, BASE_Y, 0.5));
        Block sourceBlock = player.getWorld().getBlockAt(3, BASE_Y, 0);
        sourceBlock.setType(Material.CHEST);
        Chest source = (Chest) sourceBlock.getState();
        ItemStack moved = new ItemStack(Material.STONE);
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(
            source.getInventory(), moved, boat.getInventory(), true
        );
        Bukkit.getPluginManager().callEvent(event);
    }

    private static void transferIntoStorageMinecart(Player player) {
        StorageMinecart minecart = player.getWorld().getEntitiesByClass(StorageMinecart.class).stream()
            .filter(entity -> !(entity instanceof HopperMinecart))
            .findFirst().orElseThrow(() -> new IllegalStateException("Chest minecart is missing"));
        Block sourceBlock = player.getWorld().getBlockAt(3, BASE_Y, 0);
        sourceBlock.setType(Material.CHEST);
        Chest source = (Chest) sourceBlock.getState();
        Bukkit.getPluginManager().callEvent(new InventoryMoveItemEvent(
            source.getInventory(), new ItemStack(Material.STONE), minecart.getInventory(), true
        ));
    }

    private static void dispenseToWorld(Player player) {
        Block block = player.getWorld().getBlockAt(0, BASE_Y, 0);
        if (!(block.getState() instanceof Container source)) {
            throw new IllegalStateException("Dispensing container is missing");
        }
        ItemStack emitted = source.getInventory().getItem(0).clone();
        emitted.setAmount(1);
        BlockDispenseEvent event = new BlockDispenseEvent(block, emitted, new Vector(0, 0, 1));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) throw new IllegalStateException("BlockDispenseEvent was cancelled");
        source.getInventory().setItem(0, null);
        player.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 1.5), emitted);
    }

    private String snapshot(Player player, String scenario) {
        World world = player.getWorld();
        List<String> lines = signLines(world);
        int playerStone = count(player.getInventory().getContents(), Material.STONE);
        int playerSigns = count(player.getInventory().getContents(), Material.OAK_SIGN);
        int playerEmptyStorageSigns = countStorageSignsWithLore(
            player.getInventory().getContents(), "Empty");
        int playerRegisteredStorageSigns = countStorageSignsWithLore(
            player.getInventory().getContents(), "STONE 10");
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
        int storageMinecartStone = world.getEntitiesByClass(StorageMinecart.class).stream()
            .filter(entity -> !(entity instanceof HopperMinecart))
            .mapToInt(cart -> count(cart.getInventory().getContents(), Material.STONE))
            .sum();
        int chestBoatStone = world.getEntitiesByClass(ChestBoat.class).stream()
            .mapToInt(boat -> count(boat.getInventory().getContents(), Material.STONE))
            .sum();
        int textDisplayCount = world.getEntitiesByClass(TextDisplay.class).size();
        List<String> textDisplayTexts = world.getEntitiesByClass(TextDisplay.class).stream()
            .map(TextDisplay::getText)
            .sorted()
            .toList();
        ItemStack playerBanner = findOminousBanner(player.getInventory().getContents());
        ItemStack chestBanner = bannerChestItem(world);
        ItemStack inspectedBanner = playerBanner != null ? playerBanner : chestBanner;
        String heldLore = heldLore(player);
        PotionSignSnapshot potionSign = potionSignSnapshot(world, scenario);
        ItemStack ominousBottle = find(player.getInventory().getContents(), Material.OMINOUS_BOTTLE);
        return "{\"scenario\":\"" + escape(scenario) + "\","
            + "\"lines\":" + jsonArray(lines) + ","
            + "\"playerStone\":" + playerStone + ","
            + "\"playerSigns\":" + playerSigns + ","
            + "\"playerEmptyStorageSigns\":" + playerEmptyStorageSigns + ","
            + "\"playerRegisteredStorageSigns\":" + playerRegisteredStorageSigns + ","
            + "\"droppedStone\":" + droppedStone + ","
            + "\"droppedStorageSigns\":" + droppedStorageSigns(world) + ","
            + "\"droppedEmptyStorageSigns\":"
            + droppedStorageSignsWithLore(world, "Empty") + ","
            + "\"chestStone\":" + chestStone + ","
            + "\"hopperStone\":" + hopperStone + ","
            + "\"minecartStone\":" + minecartStone + ","
            + "\"storageMinecartStone\":" + storageMinecartStone + ","
            + "\"chestBoatStone\":" + chestBoatStone + ","
            + "\"playerOminousBanners\":"
            + countOminousBanners(player.getInventory().getContents()) + ","
            + "\"playerOminousBottles\":"
            + count(player.getInventory().getContents(), Material.OMINOUS_BOTTLE) + ","
            + "\"ominousBottleAmplifier\":" + ominousBottleAmplifier(ominousBottle) + ","
            + "\"chestOminousBanners\":" + ominousBannerAmount(chestBanner) + ","
            + "\"bannerPatterns\":\"" + escape(bannerPatternSignature(inspectedBanner)) + "\","
            + "\"bannerNamePresent\":" + bannerNamePresent(inspectedBanner) + ","
            + "\"bannerTooltipHidden\":" + bannerTooltipHidden(inspectedBanner) + ","
            + "\"loggerPluginEnabled\":" + loggerPluginEnabled() + ","
            + "\"externalLoggerRegistered\":" + externalLoggerRegistered() + ","
            + "\"textDisplayCount\":" + textDisplayCount + ","
            + "\"textDisplayTexts\":" + jsonArray(textDisplayTexts) + ","
            + "\"heldType\":\"" + player.getInventory().getItemInMainHand().getType().name() + "\","
            + "\"storageSignAcceptsHeld\":" + storageSignAcceptsHeld(player) + ","
            + "\"canPlace\":" + player.hasPermission("storagesign.place") + ","
            + "\"breakCancelled\":" + lastBreakCancelled + ","
            + "\"breakDrops\":" + lastBreakDrops + ","
            + "\"editLines\":" + jsonArray(lastEditLines) + ","
            + "\"heldLore\":\"" + escape(heldLore) + "\","
            + "\"potionSignLines\":" + jsonArray(potionSign.lines()) + ","
            + "\"potionSignKey\":\"" + escape(potionSign.key()) + "\","
            + "\"potionSignDisplayWidth\":" + potionSign.displayWidth() + "}";
    }

    private static void preparePotionUpgradeSign(Player player) {
        World world = player.getWorld();
        createStorageSign(world, 4, BASE_Y, 0, "LPOTION:SPEED:2", 3);
        Block signBlock = world.getBlockAt(4, BASE_Y, 0);
        interactWithStorageSign(player, signBlock);
        Item dropped = world.getEntitiesByClass(Item.class).stream()
            .filter(entity -> entity.getItemStack().getType() == Material.LINGERING_POTION)
            .findFirst().orElseThrow(() -> new IllegalStateException("Potion upgrade export failed"));
        player.getInventory().setItem(0, dropped.getItemStack().clone());
        player.getInventory().setHeldItemSlot(0);
        dropped.remove();
        player.setSneaking(true);
        interactWithStorageSign(player, signBlock);
        player.setSneaking(false);
        StorageSignLine line = storageSignLine(signBlock);
        if (!"3".equals(line.amount())) throw new IllegalStateException("Potion upgrade import failed");
    }

    private static void interactWithStorageSign(Player player, Block sign) {
        PlayerInteractEvent event = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(),
            sign, BlockFace.SOUTH, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
    }

    private static PotionSignSnapshot potionSignSnapshot(World world, String scenario) {
        int x = "special-potion".equals(scenario) ? 0 : 4;
        Block block = world.getBlockAt(x, BASE_Y, 0);
        if (!(block.getState() instanceof Sign sign)) return new PotionSignSnapshot(List.of(), "", 0);
        List<String> lines = List.of(sign.getSide(Side.FRONT).getLines());
        String key = sign.getPersistentDataContainer().get(
            new NamespacedKey("storagesign", "potion_identifier"), PersistentDataType.STRING);
        String display = lines.size() > 1 ? lines.get(1) : "";
        return new PotionSignSnapshot(lines, key == null ? "" : key, vanillaAsciiWidth(display));
    }

    private static int vanillaAsciiWidth(String value) {
        int width = 0;
        for (char c : value.toCharArray()) {
            width += switch (c) {
                case ':', '!', '.', ',' -> 2;
                case 'I', '1', 'i', 'l' -> 4;
                default -> 6;
            };
        }
        return width;
    }

    private record PotionSignSnapshot(List<String> lines, String key, int displayWidth) {}

    private record StorageSignLine(String amount) {}

    private static StorageSignLine storageSignLine(Block block) {
        if (!(block.getState() instanceof Sign sign)) return new StorageSignLine("");
        return new StorageSignLine(sign.getSide(Side.FRONT).getLine(2));
    }

    private static void stashOminousBanner(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isOminousBanner(item)) continue;

            ItemStack single = item.clone();
            single.setAmount(1);
            if (item.getAmount() == 1) {
                player.getInventory().setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(slot, item);
            }

            Block chestBlock = player.getWorld().getBlockAt(2, BASE_Y, 0);
            chestBlock.setType(Material.CHEST);
            ((Chest) chestBlock.getState()).getInventory().setItem(0, single);
            return;
        }
        throw new IllegalStateException("Player has no compatible ominous banner");
    }

    private static void unstashOminousBanner(Player player) {
        Block chestBlock = player.getWorld().getBlockAt(2, BASE_Y, 0);
        if (!(chestBlock.getState() instanceof Chest chest)) {
            throw new IllegalStateException("Upgrade banner chest is missing");
        }
        ItemStack banner = findOminousBanner(chest.getInventory().getContents());
        if (banner == null) throw new IllegalStateException("Upgrade chest has no ominous banner");

        ItemStack single = banner.clone();
        single.setAmount(1);
        chest.getInventory().removeItem(single);
        int hotbarSlot = 0;
        while (hotbarSlot < 9 && player.getInventory().getItem(hotbarSlot) != null) hotbarSlot++;
        if (hotbarSlot == 9) throw new IllegalStateException("Player hotbar is full");
        player.getInventory().setItem(hotbarSlot, single);
        player.getInventory().setHeldItemSlot(hotbarSlot);
    }

    private static void interactWithStorageSign(Player player) {
        Block sign = player.getWorld().getBlockAt(0, BASE_Y, 0);
        PlayerInteractEvent event = new PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_BLOCK,
            player.getInventory().getItemInMainHand(),
            sign,
            BlockFace.SOUTH,
            EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(event);
    }

    private void breakStorageSign(Player player) {
        Block block = player.getWorld().getBlockAt(0, BASE_Y, 0);
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(event);
        lastBreakCancelled = event.isCancelled();
        lastBreakDrops = event.isDropItems();
    }

    private static void breakSupportBlock(Player player) {
        Block support = player.getWorld().getBlockAt(0, BASE_Y - 1, 0);
        BlockBreakEvent event = new BlockBreakEvent(support, player);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) support.setType(Material.AIR, false);
    }

    @SuppressWarnings("deprecation")
    private void editStorageSign(Player player) {
        Block block = player.getWorld().getBlockAt(0, BASE_Y, 0);
        SignChangeEvent event = new SignChangeEvent(
            block, player, new String[] {"destroyed", "DIAMOND", "999", "tampered"}
        );
        Bukkit.getPluginManager().callEvent(event);
        lastEditLines = List.of(event.getLines());
    }

    private static ItemStack bannerChestItem(World world) {
        Block block = world.getBlockAt(2, BASE_Y, 0);
        if (!(block.getState() instanceof Chest chest)) return null;
        return findOminousBanner(chest.getInventory().getContents());
    }

    private static int countStorageSignsWithLore(ItemStack[] items, String expectedLore) {
        int total = 0;
        for (ItemStack item : items) {
            if (hasStorageSignLore(item, expectedLore)) total += item.getAmount();
        }
        return total;
    }

    private static int droppedStorageSignsWithLore(World world, String expectedLore) {
        return world.getEntitiesByClass(Item.class).stream()
            .map(Item::getItemStack)
            .filter(item -> hasStorageSignLore(item, expectedLore))
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    private static boolean hasStorageSignLore(ItemStack item, String expectedLore) {
        if (item == null || item.getType() != Material.OAK_SIGN || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasLore() && meta.getLore() != null
            && !meta.getLore().isEmpty() && expectedLore.equals(meta.getLore().get(0));
    }

    private static ItemStack findOminousBanner(ItemStack[] items) {
        for (ItemStack item : items) {
            if (isOminousBanner(item)) return item;
        }
        return null;
    }

    private static int countOminousBanners(ItemStack[] items) {
        int total = 0;
        for (ItemStack item : items) total += ominousBannerAmount(item);
        return total;
    }

    private static int ominousBannerAmount(ItemStack item) {
        return isOminousBanner(item) ? item.getAmount() : 0;
    }

    private static boolean isOminousBanner(ItemStack item) {
        if (item == null || item.getType() != Material.WHITE_BANNER
            || !(item.getItemMeta() instanceof BannerMeta meta)
            || meta.numberOfPatterns() != 8) return false;
        List<Pattern> patterns = meta.getPatterns();
        List<DyeColor> colors = patterns.stream().map(Pattern::getColor).toList();
        List<String> types = patterns.stream().map(pattern -> pattern.getPattern().name()).toList();
        return colors.equals(List.of(
                DyeColor.CYAN, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.LIGHT_GRAY,
                DyeColor.BLACK, DyeColor.LIGHT_GRAY, DyeColor.LIGHT_GRAY, DyeColor.BLACK
            ))
            && matches(types.get(0), "RHOMBUS", "RHOMBUS_MIDDLE")
            && matches(types.get(1), "STRIPE_BOTTOM")
            && matches(types.get(2), "STRIPE_CENTER")
            && matches(types.get(3), "BORDER")
            && matches(types.get(4), "STRIPE_MIDDLE")
            && matches(types.get(5), "HALF_HORIZONTAL")
            && matches(types.get(6), "CIRCLE", "CIRCLE_MIDDLE")
            && matches(types.get(7), "BORDER");
    }

    private static boolean matches(String actual, String... expected) {
        return java.util.Arrays.asList(expected).contains(actual);
    }

    private static String bannerPatternSignature(ItemStack item) {
        if (!isOminousBanner(item) || !(item.getItemMeta() instanceof BannerMeta meta)) return "";
        return meta.getPatterns().stream()
            .map(pattern -> pattern.getColor().name() + ":" + pattern.getPattern().name())
            .reduce((left, right) -> left + "|" + right)
            .orElse("");
    }

    private static boolean bannerNamePresent(ItemStack item) {
        return isOminousBanner(item) && item.getItemMeta() != null
            && (item.getItemMeta().hasItemName() || item.getItemMeta().hasDisplayName());
    }

    private static boolean bannerTooltipHidden(ItemStack item) {
        return isOminousBanner(item) && item.getItemMeta() != null
            && item.getItemMeta().hasItemFlag(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }

    private static boolean loggerPluginEnabled() {
        Plugin logger = Bukkit.getPluginManager().getPlugin("Logger");
        return logger != null && logger.isEnabled();
    }

    private static boolean externalLoggerRegistered() {
        Plugin logger = Bukkit.getPluginManager().getPlugin("Logger");
        Plugin storageSign = Bukkit.getPluginManager().getPlugin("StorageSign-Refactored");
        if (logger == null || !logger.isEnabled() || storageSign == null) return false;
        try {
            Class<?> api = logger.getClass().getClassLoader()
                .loadClass("com.github.teruteru128.logger.Logger");
            return api.getMethod("getInstance", Plugin.class).invoke(null, storageSign) != null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    private static boolean storageSignAcceptsHeld(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) return false;
        Plugin storageSign = Bukkit.getPluginManager().getPlugin("StorageSign-Refactored");
        if (storageSign == null) return false;
        try {
            ClassLoader loader = storageSign.getClass().getClassLoader();
            Class<?> type = loader.loadClass("storagesign.StorageSign");
            Object instance = type.getMethod("fromBlock", Block.class)
                .invoke(null, player.getWorld().getBlockAt(0, BASE_Y, 0));
            return instance != null && (boolean) type.getMethod("isSimilar", ItemStack.class)
                .invoke(instance, held);
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
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

    private static ItemStack find(ItemStack[] items, Material material) {
        for (ItemStack item : items) {
            if (item != null && item.getType() == material) return item;
        }
        return null;
    }

    private static int ominousBottleAmplifier(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof OminousBottleMeta meta)) return -1;
        return meta.hasAmplifier() ? meta.getAmplifier() : 0;
    }

    @SuppressWarnings("deprecation")
    private static int droppedStorageSigns(World world) {
        return world.getEntitiesByClass(Item.class).stream()
            .map(Item::getItemStack)
            .filter(item -> item.getType() == Material.OAK_SIGN && item.hasItemMeta())
            .filter(item -> "StorageSign".equals(item.getItemMeta().getDisplayName()))
            .mapToInt(ItemStack::getAmount)
            .sum();
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
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
