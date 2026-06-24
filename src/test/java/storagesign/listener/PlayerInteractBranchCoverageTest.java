package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.MockBukkit;
import storagesign.ConfigLoader;
import storagesign.StorageSign;
import storagesign.logging.PluginLogger;

class PlayerInteractBranchCoverageTest {

    @org.junit.jupiter.api.BeforeEach
    void enableTraceLogging() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        PluginManager manager = mock(PluginManager.class);
        Logger jul = Logger.getLogger("PlayerInteractBranchCoverageTest.trace");
        jul.setUseParentHandlers(false);
        jul.setLevel(java.util.logging.Level.FINEST);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(manager.getPlugin("Logger")).thenReturn(null);
        when(plugin.getLogger()).thenReturn(jul);
        PluginLogger.initialize(plugin, "TRACE");
    }

    @org.junit.jupiter.api.AfterEach
    void resetTraceLogging() {
        PluginLogger.shutdown();
    }

    @Test
    void onPlayerInteractSkipsPermissionForNonSignBlocks() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.STONE);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);

        new PlayerInteractListener(null).onPlayerInteract(event);

        verify(player, never()).hasPermission("storagesign.use");
    }

    @Test
    void rightClickAirWithoutDeniedBlockDoesNotTargetStorageSign() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getClickedBlock()).thenReturn(null);
        when(event.useInteractedBlock()).thenReturn(Result.ALLOW);

        new PlayerInteractListener(null).onPlayerInteract(event);

        verify(player, never()).getTargetBlockExact(3);
        verify(player, never()).hasPermission("storagesign.use");
    }

    @Test
    void rightClickAirWithDeniedInteractedBlockUsesTargetBlockLookup() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block target = mock(Block.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getClickedBlock()).thenReturn(null);
        when(event.useInteractedBlock()).thenReturn(Result.DENY);
        when(player.getTargetBlockExact(3)).thenReturn(target);
        when(target.getType()).thenReturn(Material.STONE);

        new PlayerInteractListener(null).onPlayerInteract(event);

        verify(player).getTargetBlockExact(3);
        verify(player, never()).hasPermission("storagesign.use");
    }

    @Test
    void offHandStorageSignIsDeniedWithoutProcessingMainInteraction() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        ItemStack offHand = mock(ItemStack.class);
        StorageSign ss = mock(StorageSign.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        when(event.getItem()).thenReturn(offHand);
        when(player.isSneaking()).thenReturn(true);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(ss);
            signs.when(() -> StorageSign.isStorageSign(offHand)).thenReturn(true);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(event).setUseItemInHand(Result.DENY);
        verify(event).setUseInteractedBlock(Result.DENY);
        verify(player, never()).hasPermission("storagesign.use");
    }

    @Test
    void offHandNonStorageSignReturnsWithoutDenyingInteraction() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        ItemStack offHand = mock(ItemStack.class);
        StorageSign ss = mock(StorageSign.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        when(event.getItem()).thenReturn(offHand);
        when(player.isSneaking()).thenReturn(false);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(ss);
            signs.when(() -> StorageSign.isStorageSign(offHand)).thenReturn(false);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(event, never()).setUseItemInHand(Result.DENY);
        verify(event, never()).setUseInteractedBlock(Result.DENY);
    }

    @Test
    void signBlockWithoutStorageSignDataReturnsBeforePermissionCheck() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        StorageSign ss = mock(StorageSign.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(null);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(player, never()).hasPermission("storagesign.use");
        verify(event, never()).setUseItemInHand(Result.DENY);
        verify(event, never()).setUseInteractedBlock(Result.DENY);
    }

    @Test
    void debugTraceLogsWhenEnabledForMainHandItem() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        ItemStack hand = mock(ItemStack.class);
        StorageSign blockSign = mock(StorageSign.class);
        Location blockLocation = mock(Location.class);

        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(block.getState()).thenReturn(sign);
        when(block.getLocation()).thenReturn(blockLocation);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getItem()).thenReturn(hand);
        when(player.hasPermission("storagesign.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hand.getType()).thenReturn(Material.DIAMOND);
        when(hand.hasItemMeta()).thenReturn(true);
        when(hand.getItemMeta()).thenReturn(mock(org.bukkit.inventory.meta.ItemMeta.class));
        when(blockSign.isUnregistered()).thenReturn(false);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class);
             MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(blockSign);
            config.when(ConfigLoader::getBannerDebug).thenReturn(true);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(hand).hasItemMeta();
    }

    @Test
    void debugTraceLogsForRightClickAirWhenEnabled() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        ItemStack hand = mock(ItemStack.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getClickedBlock()).thenReturn(null);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getItem()).thenReturn(hand);
        when(hand.getType()).thenReturn(Material.DIAMOND);
        when(hand.hasItemMeta()).thenReturn(true);
        when(hand.getItemMeta()).thenReturn(mock(org.bukkit.inventory.meta.ItemMeta.class));

        try (MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            config.when(ConfigLoader::getBannerDebug).thenReturn(true);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(hand).hasItemMeta();
    }

    @Test
    void dyeAndInkItemsDeferToVanillaInteractionWhenManualExportIsEnabled() {
        PlayerInteractEvent dyeEvent = mock(PlayerInteractEvent.class);
        Player dyePlayer = mock(Player.class);
        PlayerInventory dyeInventory = mock(PlayerInventory.class);
        Block dyeBlock = mock(Block.class);
        Sign dyeSign = mock(Sign.class);
        ItemStack dye = mock(ItemStack.class);
        StorageSign dyeSignState = mock(StorageSign.class);
        when(dyeEvent.getPlayer()).thenReturn(dyePlayer);
        when(dyePlayer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(dyeEvent.getClickedBlock()).thenReturn(dyeBlock);
        when(dyeBlock.getType()).thenReturn(Material.OAK_SIGN);
        when(dyeBlock.getState()).thenReturn(dyeSign);
        when(dyeEvent.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(dyeEvent.getHand()).thenReturn(EquipmentSlot.HAND);
        when(dyePlayer.hasPermission("storagesign.use")).thenReturn(true);
        when(dyePlayer.getInventory()).thenReturn(dyeInventory);
        when(dyeEvent.getItem()).thenReturn(dye);
        when(dyeInventory.getItemInMainHand()).thenReturn(dye);
        when(dye.getType()).thenReturn(Material.RED_DYE);
        when(dyeSignState.isUnregistered()).thenReturn(false);

        PlayerInteractEvent inkEvent = mock(PlayerInteractEvent.class);
        Player inkPlayer = mock(Player.class);
        PlayerInventory inkInventory = mock(PlayerInventory.class);
        Block inkBlock = mock(Block.class);
        Sign inkSign = mock(Sign.class);
        ItemStack ink = mock(ItemStack.class);
        StorageSign inkSignState = mock(StorageSign.class);
        when(inkEvent.getPlayer()).thenReturn(inkPlayer);
        when(inkPlayer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(inkEvent.getClickedBlock()).thenReturn(inkBlock);
        when(inkBlock.getType()).thenReturn(Material.OAK_SIGN);
        when(inkBlock.getState()).thenReturn(inkSign);
        when(inkEvent.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(inkEvent.getHand()).thenReturn(EquipmentSlot.HAND);
        when(inkPlayer.hasPermission("storagesign.use")).thenReturn(true);
        when(inkPlayer.getInventory()).thenReturn(inkInventory);
        when(inkEvent.getItem()).thenReturn(ink);
        when(inkInventory.getItemInMainHand()).thenReturn(ink);
        when(ink.getType()).thenReturn(Material.INK_SAC);
        when(inkSignState.isUnregistered()).thenReturn(false);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class);
             MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            signs.when(() -> StorageSign.fromBlock(dyeBlock)).thenReturn(dyeSignState);
            signs.when(() -> StorageSign.fromBlock(inkBlock)).thenReturn(inkSignState);
            signs.when(() -> StorageSign.fromItemStack(dye)).thenReturn(null);
            signs.when(() -> StorageSign.fromItemStack(ink)).thenReturn(null);
            config.when(ConfigLoader::getBannerDebug).thenReturn(false);
            config.when(ConfigLoader::getManualImport).thenReturn(false);
            config.when(ConfigLoader::getManualExport).thenReturn(true);

            new PlayerInteractListener(null).onPlayerInteract(dyeEvent);
            new PlayerInteractListener(null).onPlayerInteract(inkEvent);
        }

        verify(dyeEvent).setUseItemInHand(Result.ALLOW);
        verify(dyeEvent).setUseInteractedBlock(Result.ALLOW);
        verify(inkEvent).setUseItemInHand(Result.ALLOW);
        verify(inkEvent).setUseInteractedBlock(Result.ALLOW);
    }

    @Test
    void onPlayerInteractRegistersNewStorageSignItemFromEmptySign() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        ItemStack hand = mock(ItemStack.class);
        StorageSign empty = mock(StorageSign.class);
        StorageSign registered = mock(StorageSign.class);
        Location blockLocation = mock(Location.class);

        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(block.getState()).thenReturn(sign);
        when(block.getLocation()).thenReturn(blockLocation);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(player.hasPermission("storagesign.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hand.getType()).thenReturn(Material.DIAMOND);
        when(empty.isUnregistered()).thenReturn(true);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class);
             MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(empty);
            signs.when(() -> StorageSign.fromStoredItem(hand)).thenReturn(registered);
            config.when(ConfigLoader::getBannerDebug).thenReturn(false);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(registered).applyToSign(sign);
    }

    @Test
    void onPlayerInteractMergesStorageSignItemFromHandBranch() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack contents = mock(ItemStack.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack emptied = mock(ItemStack.class);
        Location blockLocation = mock(Location.class);

        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(block.getState()).thenReturn(sign);
        when(block.getLocation()).thenReturn(blockLocation);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(player.hasPermission("storagesign.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(1);
        when(blockSign.isUnregistered()).thenReturn(false);
        when(blockSign.getAmount()).thenReturn(100);
        when(blockSign.isSimilar(contents)).thenReturn(true);
        when(handSign.isUnregistered()).thenReturn(false);
        when(handSign.getAmount()).thenReturn(1);
        when(handSign.getContents(1)).thenReturn(contents);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class);
             MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(blockSign);
            signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(handSign);
            signs.when(() -> StorageSign.createStorageSignItem(
                Material.OAK_SIGN, StorageSign.EMPTY_MARKER, 1)).thenReturn(emptied);
            config.when(ConfigLoader::getBannerDebug).thenReturn(false);
            config.when(ConfigLoader::getManualImport).thenReturn(true);
            config.when(ConfigLoader::getManualExport).thenReturn(false);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(inventory).setItemInMainHand(emptied);
        verify(blockSign).setAmount(101);
    }

    @Test
    void onPlayerInteractStoresSignIntoSignAsItemWithoutSneaking() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack stored = mock(ItemStack.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        StorageSign itemSign = mock(StorageSign.class);
        Location blockLocation = mock(Location.class);

        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(block.getState()).thenReturn(sign);
        when(block.getLocation()).thenReturn(blockLocation);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(player.hasPermission("storagesign.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(2);
        when(player.isSneaking()).thenReturn(false);
        when(inventory.getContents()).thenReturn(new ItemStack[] { hand });
        when(blockSign.isUnregistered()).thenReturn(false);
        when(blockSign.isSignAsItem()).thenReturn(true);
        when(blockSign.getMaterial()).thenReturn(Material.OAK_SIGN);
        when(blockSign.getAmount()).thenReturn(3);
        when(handSign.isUnregistered()).thenReturn(true);
        when(handSign.getAmount()).thenReturn(2);
        when(itemSign.isUnregistered()).thenReturn(true);
        when(itemSign.getAmount()).thenReturn(2);
        when(handSign.getContents(1)).thenReturn(stored);
        when(blockSign.isSimilar(stored)).thenReturn(true);
        when(hand.clone()).thenReturn(hand);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class);
             MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(blockSign);
            signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(handSign);
            signs.when(() -> StorageSign.fromItemStack(stored)).thenReturn(itemSign);
            config.when(ConfigLoader::getBannerDebug).thenReturn(false);
            config.when(ConfigLoader::getManualImport).thenReturn(true);
            config.when(ConfigLoader::getManualExport).thenReturn(false);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(blockSign).setAmount(5);
        verify(inventory).setItem(0, null);
    }

    @Test
    void onPlayerInteractStoresSignIntoSignAsItemWhileSneaking() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        ItemStack hand = mock(ItemStack.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        Location blockLocation = mock(Location.class);

        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(block.getState()).thenReturn(sign);
        when(block.getLocation()).thenReturn(blockLocation);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(player.hasPermission("storagesign.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(2);
        when(player.isSneaking()).thenReturn(true);
        when(inventory.getHeldItemSlot()).thenReturn(0);
        when(blockSign.isUnregistered()).thenReturn(false);
        when(blockSign.isSignAsItem()).thenReturn(true);
        when(blockSign.getMaterial()).thenReturn(Material.OAK_SIGN);
        when(blockSign.getAmount()).thenReturn(3);
        when(handSign.isUnregistered()).thenReturn(true);
        when(handSign.getAmount()).thenReturn(2);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class);
             MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(blockSign);
            signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(handSign);
            config.when(ConfigLoader::getBannerDebug).thenReturn(false);
            config.when(ConfigLoader::getManualImport).thenReturn(true);
            config.when(ConfigLoader::getManualExport).thenReturn(false);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(inventory).setItem(0, null);
        verify(blockSign).setAmount(5);
    }

    @Test
    void offHandStorageSignWithoutSneakingDoesNotDenyInteraction() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        ItemStack offHand = mock(ItemStack.class);
        StorageSign ss = mock(StorageSign.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        when(event.getItem()).thenReturn(offHand);
        when(player.isSneaking()).thenReturn(false);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(ss);
            signs.when(() -> StorageSign.isStorageSign(offHand)).thenReturn(true);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(event, never()).setUseItemInHand(Result.DENY);
        verify(event, never()).setUseInteractedBlock(Result.DENY);
    }

    @Test
    void onPlayerInteractNullMainHandFallsBackToAirAndStopsWhenExportDisabled() {
        MockBukkit.mock();
        try {
            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            Block block = mock(Block.class);
            Sign sign = mock(Sign.class);
            StorageSign blockSign = mock(StorageSign.class);
            Location blockLocation = mock(Location.class);

            when(event.getPlayer()).thenReturn(player);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(event.getClickedBlock()).thenReturn(block);
            when(block.getType()).thenReturn(Material.OAK_SIGN);
            when(block.getState()).thenReturn(sign);
            when(block.getLocation()).thenReturn(blockLocation);
            when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
            when(event.getHand()).thenReturn(EquipmentSlot.HAND);
            when(player.hasPermission("storagesign.use")).thenReturn(true);
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getItemInMainHand()).thenReturn(null);
            when(blockSign.isUnregistered()).thenReturn(false);

            try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class);
                 MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
                signs.when(() -> StorageSign.fromBlock(block)).thenReturn(blockSign);
                signs.when(() -> StorageSign.fromItemStack(Mockito.any(ItemStack.class))).thenReturn(null);
                config.when(ConfigLoader::getBannerDebug).thenReturn(false);
                config.when(ConfigLoader::getManualImport).thenReturn(false);
                config.when(ConfigLoader::getManualExport).thenReturn(false);
                new PlayerInteractListener(null).onPlayerInteract(event);
            }

            verify(inventory).getItemInMainHand();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void onPlayerInteractDividesStorageSignItemWhenManualExportEnabled() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack template = mock(ItemStack.class);
        ItemStack created = mock(ItemStack.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        StorageSign divided = mock(StorageSign.class);
        Location blockLocation = mock(Location.class);

        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(block.getState()).thenReturn(sign);
        when(block.getLocation()).thenReturn(blockLocation);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(player.hasPermission("storagesign.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(1);
        when(player.isSneaking()).thenReturn(false);
        when(blockSign.isUnregistered()).thenReturn(false);
        when(blockSign.getAmount()).thenReturn(2);
        when(blockSign.getContents(1)).thenReturn(template);
        when(handSign.isUnregistered()).thenReturn(true);
        when(divided.isUnregistered()).thenReturn(true);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class);
             MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(blockSign);
            signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(handSign);
            signs.when(() -> StorageSign.fromStoredItem(template)).thenReturn(divided);
            signs.when(() -> StorageSign.createStorageSignItem(
                Material.OAK_SIGN, divided, 1)).thenReturn(created);
            config.when(ConfigLoader::getBannerDebug).thenReturn(false);
            config.when(ConfigLoader::getManualImport).thenReturn(false);
            config.when(ConfigLoader::getManualExport).thenReturn(true);
            config.when(ConfigLoader::getDivideLimit).thenReturn(64);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(inventory).setItemInMainHand(created);
        verify(divided).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(blockSign).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void onPlayerInteractImportsMatchingStorageSignItemIntoBlock() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        ItemStack hand = mock(ItemStack.class);
        StorageSign blockSign = mock(StorageSign.class);
        Location blockLocation = mock(Location.class);

        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(block.getState()).thenReturn(sign);
        when(block.getLocation()).thenReturn(blockLocation);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(player.hasPermission("storagesign.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hand.getType()).thenReturn(Material.DIAMOND);
        when(player.isSneaking()).thenReturn(false);
        when(hand.getAmount()).thenReturn(4);
        when(inventory.getContents()).thenReturn(new ItemStack[] { hand });
        when(blockSign.getAmount()).thenReturn(5);
        when(blockSign.isUnregistered()).thenReturn(false);
        when(blockSign.isSimilar(hand)).thenReturn(true);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class);
             MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(blockSign);
            signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(null);
            config.when(ConfigLoader::getBannerDebug).thenReturn(false);
            config.when(ConfigLoader::getManualImport).thenReturn(true);
            config.when(ConfigLoader::getManualExport).thenReturn(false);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(blockSign).setAmount(9);
        verify(blockSign).applyToSign(sign);
        verify(player).updateInventory();
    }

    @Test
    void onPlayerInteractExportsWhenManualImportFailsAndManualExportEnabled() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        Location blockLocation = mock(Location.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack out = mock(ItemStack.class);
        StorageSign blockSign = mock(StorageSign.class);

        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(block.getState()).thenReturn(sign);
        when(block.getLocation()).thenReturn(blockLocation);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(player.hasPermission("storagesign.use")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hand.getType()).thenReturn(Material.DIAMOND);
        when(blockSign.isUnregistered()).thenReturn(false);
        when(blockSign.getAmount()).thenReturn(10);
        when(blockSign.isSimilar(hand)).thenReturn(false);
        when(blockSign.getContents(1)).thenReturn(out);
        when(out.getMaxStackSize()).thenReturn(64);
        when(player.isSneaking()).thenReturn(false);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        when(location.clone()).thenReturn(location);
        when(location.add(0, 0.5, 0)).thenReturn(location);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class);
             MockedStatic<ConfigLoader> config = Mockito.mockStatic(ConfigLoader.class)) {
            signs.when(() -> StorageSign.fromBlock(block)).thenReturn(blockSign);
            signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(null);
            config.when(ConfigLoader::getBannerDebug).thenReturn(false);
            config.when(ConfigLoader::getManualImport).thenReturn(true);
            config.when(ConfigLoader::getManualExport).thenReturn(true);
            new PlayerInteractListener(null).onPlayerInteract(event);
        }

        verify(world).dropItem(location, out);
        verify(blockSign).applyToSign(sign);
    }

    @Test
    void importItemsSneakingColorsMatchingSignAndConsumesHandStack() throws Exception {
        resetMockBukkit();
        org.mockbukkit.mockbukkit.MockBukkit.mock();
        var mockServer = org.mockbukkit.mockbukkit.MockBukkit.getMock();
        var worldObj = mockServer.addSimpleWorld("dye-import");
        var block = worldObj.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack hand = new ItemStack(Material.RED_DYE, 4);
        StorageSign storageSign = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "RED_DYE", "5"});
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(true);
        when(inventory.getHeldItemSlot()).thenReturn(0);

        setManualImport(true);
        DyeColor color = null;
        try {
            invokeImport(player, block, storageSign, hand);
            color = ((Sign) block.getState()).getSide(Side.FRONT).getColor();
        } finally {
            resetManualImport();
            resetMockBukkit();
        }

        assertEquals(9, storageSign.getAmount());
        assertEquals(DyeColor.RED, color);
        verify(inventory).setItem(0, null);
        verify(player).updateInventory();
    }

    @Test
    void importItemsSneakingEnablesGlowingTextForGlowInkSac() throws Exception {
        resetMockBukkit();
        var mockServer = org.mockbukkit.mockbukkit.MockBukkit.mock();
        var worldObj = mockServer.addSimpleWorld("glow-import");
        var block = worldObj.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack hand = new ItemStack(Material.GLOW_INK_SAC, 2);
        StorageSign storageSign = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "GLOW_INK_SAC", "3"});
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(true);
        when(inventory.getHeldItemSlot()).thenReturn(1);

        setManualImport(true);
        boolean glowing = false;
        try {
            invokeImport(player, block, storageSign, hand);
            glowing = ((Sign) block.getState()).getSide(Side.FRONT).isGlowingText();
        } finally {
            resetManualImport();
            resetMockBukkit();
        }

        assertEquals(5, storageSign.getAmount());
        assertTrue(glowing);
        verify(inventory).setItem(1, null);
        verify(player).updateInventory();
    }

    @Test
    void exportItemsReturnsImmediatelyForEmptyOrUnregisteredStorageSigns() throws Exception {
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        StorageSign empty = mock(StorageSign.class);
        when(empty.isUnregistered()).thenReturn(false);
        when(empty.getAmount()).thenReturn(0);

        invokeExport(player, block, empty);

        verify(empty, never()).getContents(1);

        StorageSign unregistered = mock(StorageSign.class);
        when(unregistered.isUnregistered()).thenReturn(true);
        when(unregistered.getAmount()).thenReturn(10);

        invokeExport(player, block, unregistered);

        verify(unregistered, never()).getContents(1);
    }

    @Test
    void exportItemsReturnsWhenContentsCannotBeRestored() throws Exception {
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(storageSign.isUnregistered()).thenReturn(false);
        when(storageSign.getAmount()).thenReturn(3);
        when(storageSign.getContents(1)).thenReturn(null);

        invokeExport(player, block, storageSign);

        verify(storageSign).getContents(1);
        verify(storageSign, never()).setAmount(0);
    }

    @Test
    void importItemsLogsWhenNonSneakingImportSucceeds() throws Exception {
        resetMockBukkit();
        org.mockbukkit.mockbukkit.MockBukkit.mock();
        try {
            var world = org.mockbukkit.mockbukkit.MockBukkit.getMock().addSimpleWorld("import-log");
            var block = world.getBlockAt(0, 64, 0);
            block.setType(Material.OAK_SIGN);
            Sign sign = (Sign) block.getState();

            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            StorageSign storageSign = StorageSign.fromSignLines(
                new String[] {StorageSign.HEADER_LINE, "STONE", "5"});
            ItemStack matched = new ItemStack(Material.STONE, 4);
            when(player.getInventory()).thenReturn(inventory);
            when(player.isSneaking()).thenReturn(false);
            when(inventory.getContents()).thenReturn(new ItemStack[] { matched });

            setManualImport(true);
            try {
                invokeImport(player, block, storageSign, matched);
            } finally {
                resetManualImport();
            }

            assertEquals(9, storageSign.getAmount());
            verify(player).updateInventory();
            verify(inventory).setItem(0, null);
        } finally {
            resetMockBukkit();
        }
    }

    @Test
    void registerItemReturnsImmediatelyWhenHandIsNullOrAir() throws Exception {
        Player player = mock(Player.class);
        Block block = mock(Block.class);

        invokeRegister(player, block, null);

        ItemStack air = mock(ItemStack.class);
        when(air.getType()).thenReturn(Material.AIR);
        invokeRegister(player, block, air);
    }

    private static void setManualImport(boolean value) throws Exception {
        var field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, value);
    }

    private static void resetManualImport() throws Exception {
        setManualImport(true);
    }

    private static void resetMockBukkit() {
        try {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        } catch (IllegalStateException ignored) {
        }
    }

    private static void invokeImport(Player player, Block block, StorageSign sign, ItemStack hand)
            throws Exception {
        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "importItems", Player.class, Block.class, StorageSign.class, ItemStack.class);
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, sign, hand);
    }

    private static void invokeExport(Player player, Block block, StorageSign sign)
            throws Exception {
        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "exportItems", Player.class, Block.class, StorageSign.class);
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, sign);
    }

    private static void invokeRegister(Player player, Block block, ItemStack hand)
            throws Exception {
        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "registerItem", Player.class, Block.class, ItemStack.class);
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, hand);
    }
}
