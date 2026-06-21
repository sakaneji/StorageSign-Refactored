package storagesign.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.ConfigLoader;
import storagesign.StorageSign;

class PlayerInteractDivisionPolicyTest {

    @AfterEach
    void restoreManualExport() throws Exception {
        setManualExport(true);
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, true);
    }

    @Test
    void divisionIsDisabledByManualExportFlag() throws Exception {
        setManualExport(false);
        Fixture fixture = fixture(Material.OAK_SIGN, Material.OAK_SIGN);

        invoke(fixture);

        verify(fixture.inventory(), never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
        verify(fixture.blockSign(), never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void divisionRequiresMatchingSignMaterial() throws Exception {
        setManualExport(true);
        Fixture fixture = fixture(Material.OAK_SIGN, Material.SPRUCE_SIGN);

        invoke(fixture);

        verify(fixture.inventory(), never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
        verify(fixture.blockSign(), never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void registeredStorageSignIsNotUsedAsDivisionTarget() throws Exception {
        setManualExport(true);
        Fixture fixture = fixture(Material.OAK_SIGN, Material.OAK_SIGN);
        when(fixture.handSign().isUnregistered()).thenReturn(false);

        invoke(fixture);

        verify(fixture.inventory(), never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
        verify(fixture.blockSign(), never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void manualImportFlagStopsInventoryConsumption() throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, false);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        StorageSign sign = mock(StorageSign.class);
        ItemStack hand = mock(ItemStack.class);
        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "importItems", Player.class, Block.class, StorageSign.class, ItemStack.class
        );
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, sign, hand);
        verify(player, never()).getInventory();
        field.setBoolean(null, true);
    }

    @Test
    void partialRegisteredSignMergePreservesRemainingAndEmptiedSigns() throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, true);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack contents = mock(ItemStack.class);
        ItemStack remainingRegistered = mock(ItemStack.class);
        ItemStack emptied = mock(ItemStack.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack remaining = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(blockSign.getAmount()).thenReturn(Integer.MAX_VALUE - 10);
        when(blockSign.isSimilar(contents)).thenReturn(true);
        when(handSign.isUnregistered()).thenReturn(false);
        when(handSign.getAmount()).thenReturn(10);
        when(handSign.getContents(1)).thenReturn(contents);
        when(handSign.getLoreText()).thenReturn("STONE 10");
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(2);
        when(hand.clone()).thenReturn(remaining);
        when(remaining.getType()).thenReturn(Material.OAK_SIGN);
        when(inventory.addItem(emptied)).thenReturn(new HashMap<>());

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.createStorageSignItem(
                org.mockito.ArgumentMatchers.eq(Material.OAK_SIGN),
                org.mockito.ArgumentMatchers.eq(handSign), org.mockito.ArgumentMatchers.eq(1)))
                .thenReturn(remainingRegistered);
            signs.when(() -> StorageSign.createStorageSignItem(
                Material.OAK_SIGN, StorageSign.EMPTY_MARKER, 1)).thenReturn(emptied);
            Method method = PlayerInteractListener.class.getDeclaredMethod(
                "processStorageSignItemInteraction", Player.class, Block.class,
                StorageSign.class, ItemStack.class, StorageSign.class
            );
            method.setAccessible(true);
            method.invoke(new PlayerInteractListener(null), player, block, blockSign, hand, handSign);
        }

        verify(blockSign).setAmount(Integer.MAX_VALUE);
        verify(inventory).setItemInMainHand(remainingRegistered);
        verify(inventory).addItem(emptied);
    }

    @Test
    void completeRegisteredSignMergeReturnsSameNumberOfEmptySigns() throws Exception {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack contents = mock(ItemStack.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack emptied = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(blockSign.getAmount()).thenReturn(100);
        when(blockSign.isSimilar(contents)).thenReturn(true);
        when(handSign.isUnregistered()).thenReturn(false);
        when(handSign.getAmount()).thenReturn(10);
        when(handSign.getContents(1)).thenReturn(contents);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(2);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.createStorageSignItem(
                Material.OAK_SIGN, StorageSign.EMPTY_MARKER, 2)).thenReturn(emptied);
            invokeInteraction(player, block, blockSign, hand, handSign);
        }

        verify(blockSign).setAmount(120);
        verify(inventory).setItemInMainHand(emptied);
        verify(inventory, never()).addItem(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mergeWithLessThanOneStorageSignCapacityLeavesSourceUntouched() throws Exception {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack contents = mock(ItemStack.class);
        ItemStack hand = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(blockSign.getAmount()).thenReturn(Integer.MAX_VALUE - 5);
        when(blockSign.isSimilar(contents)).thenReturn(true);
        when(handSign.isUnregistered()).thenReturn(false);
        when(handSign.getAmount()).thenReturn(10);
        when(handSign.getContents(1)).thenReturn(contents);
        when(hand.getAmount()).thenReturn(1);

        invokeInteraction(player, block, blockSign, hand, handSign);

        verify(blockSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(inventory, never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registeredSignMergeIsDisabledByManualImportFlag() throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, false);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack contents = mock(ItemStack.class);
        ItemStack hand = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(blockSign.isSimilar(contents)).thenReturn(true);
        when(handSign.isUnregistered()).thenReturn(false);
        when(handSign.getAmount()).thenReturn(10);
        when(handSign.getContents(1)).thenReturn(contents);
        when(hand.getAmount()).thenReturn(1);

        invokeInteraction(player, block, blockSign, hand, handSign);

        verify(blockSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(inventory, never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registeredSignMergeRejectsDifferentStoredItem() throws Exception {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack contents = mock(ItemStack.class);
        ItemStack hand = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(blockSign.isSimilar(contents)).thenReturn(false);
        when(handSign.isUnregistered()).thenReturn(false);
        when(handSign.getContents(1)).thenReturn(contents);

        invokeInteraction(player, block, blockSign, hand, handSign);

        verify(blockSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(inventory, never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fullInventoryDropsOnlyEmptiedStorageSignAfterPartialMerge() throws Exception {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack contents = mock(ItemStack.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        ItemStack remainingRegistered = mock(ItemStack.class);
        ItemStack emptied = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        when(blockSign.getAmount()).thenReturn(Integer.MAX_VALUE - 10);
        when(blockSign.isSimilar(contents)).thenReturn(true);
        when(handSign.isUnregistered()).thenReturn(false);
        when(handSign.getAmount()).thenReturn(10);
        when(handSign.getContents(1)).thenReturn(contents);
        when(handSign.getLoreText()).thenReturn("STONE 10");
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(2);
        when(hand.clone()).thenReturn(clone);
        when(clone.getType()).thenReturn(Material.OAK_SIGN);
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        leftovers.put(0, emptied);
        when(inventory.addItem(emptied)).thenReturn(leftovers);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.createStorageSignItem(
                Material.OAK_SIGN, handSign, 1)).thenReturn(remainingRegistered);
            signs.when(() -> StorageSign.createStorageSignItem(
                Material.OAK_SIGN, StorageSign.EMPTY_MARKER, 1)).thenReturn(emptied);
            invokeInteraction(player, block, blockSign, hand, handSign);
        }

        verify(inventory).setItemInMainHand(remainingRegistered);
        verify(world).dropItemNaturally(location, emptied);
        verify(world, never()).dropItemNaturally(location, contents);
    }

    @Test
    void signInSignOverflowConsumesOnlyAvailableCapacity() throws Exception {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack remaining = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(true);
        when(inventory.getHeldItemSlot()).thenReturn(2);
        when(blockSign.getAmount()).thenReturn(Integer.MAX_VALUE - 2);
        when(blockSign.isSignAsItem()).thenReturn(true);
        when(blockSign.getMaterial()).thenReturn(Material.OAK_SIGN);
        when(handSign.isUnregistered()).thenReturn(true);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(5);
        when(hand.clone()).thenReturn(remaining);

        invokeInteraction(player, block, blockSign, hand, handSign);

        verify(blockSign).setAmount(Integer.MAX_VALUE);
        verify(remaining).setAmount(3);
        verify(inventory).setItem(2, remaining);
    }

    @Test
    void fullSignInSignStorageLeavesHeldStackUnchanged() throws Exception {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack hand = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(true);
        when(blockSign.getAmount()).thenReturn(Integer.MAX_VALUE);
        when(blockSign.isSignAsItem()).thenReturn(true);
        when(blockSign.getMaterial()).thenReturn(Material.OAK_SIGN);
        when(handSign.isUnregistered()).thenReturn(true);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(5);

        invokeInteraction(player, block, blockSign, hand, handSign);

        verify(blockSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(inventory, never()).setItem(org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.any());
    }

    private static Fixture fixture(Material blockType, Material handType) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack hand = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(block.getType()).thenReturn(blockType);
        when(blockSign.getAmount()).thenReturn(100);
        when(handSign.isUnregistered()).thenReturn(true);
        when(hand.getType()).thenReturn(handType);
        when(hand.getAmount()).thenReturn(2);
        return new Fixture(player, inventory, block, blockSign, hand, handSign);
    }

    private static void invoke(Fixture fixture) throws Exception {
        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "processStorageSignItemInteraction", Player.class, Block.class,
            StorageSign.class, ItemStack.class, StorageSign.class
        );
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), fixture.player(), fixture.block(),
            fixture.blockSign(), fixture.hand(), fixture.handSign());
    }

    private static void invokeInteraction(Player player, Block block, StorageSign blockSign,
                                          ItemStack hand, StorageSign handSign) throws Exception {
        Method method = PlayerInteractListener.class.getDeclaredMethod(
            "processStorageSignItemInteraction", Player.class, Block.class,
            StorageSign.class, ItemStack.class, StorageSign.class
        );
        method.setAccessible(true);
        method.invoke(new PlayerInteractListener(null), player, block, blockSign, hand, handSign);
    }

    private static void setManualExport(boolean value) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("manualExport");
        field.setAccessible(true);
        field.setBoolean(null, value);
    }

    private record Fixture(Player player, PlayerInventory inventory, Block block,
                           StorageSign blockSign, ItemStack hand, StorageSign handSign) {}
}
