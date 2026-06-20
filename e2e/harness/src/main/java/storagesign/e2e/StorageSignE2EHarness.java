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
import org.bukkit.World;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
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
            case "client", "special-potion", "special-banner", "banner-upgrade-seed" ->
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
        ItemStack playerBanner = findOminousBanner(player.getInventory().getContents());
        ItemStack chestBanner = bannerChestItem(world);
        ItemStack inspectedBanner = playerBanner != null ? playerBanner : chestBanner;
        String heldLore = heldLore(player);
        return "{\"scenario\":\"" + escape(scenario) + "\","
            + "\"lines\":" + jsonArray(lines) + ","
            + "\"playerStone\":" + playerStone + ","
            + "\"playerSigns\":" + playerSigns + ","
            + "\"droppedStone\":" + droppedStone + ","
            + "\"chestStone\":" + chestStone + ","
            + "\"hopperStone\":" + hopperStone + ","
            + "\"minecartStone\":" + minecartStone + ","
            + "\"playerOminousBanners\":"
            + countOminousBanners(player.getInventory().getContents()) + ","
            + "\"chestOminousBanners\":" + ominousBannerAmount(chestBanner) + ","
            + "\"bannerPatterns\":\"" + escape(bannerPatternSignature(inspectedBanner)) + "\","
            + "\"bannerNamePresent\":" + bannerNamePresent(inspectedBanner) + ","
            + "\"bannerTooltipHidden\":" + bannerTooltipHidden(inspectedBanner) + ","
            + "\"loggerPluginEnabled\":" + loggerPluginEnabled() + ","
            + "\"externalLoggerRegistered\":" + externalLoggerRegistered() + ","
            + "\"heldType\":\"" + player.getInventory().getItemInMainHand().getType().name() + "\","
            + "\"storageSignAcceptsHeld\":" + storageSignAcceptsHeld(player) + ","
            + "\"canPlace\":" + player.hasPermission("storagesign.place") + ","
            + "\"heldLore\":\"" + escape(heldLore) + "\"}";
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

    private static ItemStack bannerChestItem(World world) {
        Block block = world.getBlockAt(2, BASE_Y, 0);
        if (!(block.getState() instanceof Chest chest)) return null;
        return findOminousBanner(chest.getInventory().getContents());
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
