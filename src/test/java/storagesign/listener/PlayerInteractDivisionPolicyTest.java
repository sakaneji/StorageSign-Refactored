package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.MockBukkit;
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
    void zeroSizedRegisteredSignStackReturnsBeforeMergeing() throws Exception {
        setManualExport(true);
        Fixture fixture = fixture(Material.OAK_SIGN, Material.OAK_SIGN);
        ItemStack contents = mock(ItemStack.class);
        when(fixture.handSign().isUnregistered()).thenReturn(false);
        when(fixture.handSign().getAmount()).thenReturn(0);
        when(fixture.handSign().getContents(1)).thenReturn(contents);
        when(fixture.blockSign().isSimilar(contents)).thenReturn(true);

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
    void partialRegisteredSignMergeReturnsReducedStackAfterEmptyStack() throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, true);
        MockBukkit.mock();
        try {
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            Block block = mock(Block.class);
            StorageSign blockSign = mock(StorageSign.class);
            StorageSign handSign = StorageSign.fromSignLines(new String[] {
                StorageSign.HEADER_LINE, "STONE", "10"
            });
            ItemStack contents = handSign.getContents(1);
            ItemStack hand = StorageSign.createStorageSignItem(Material.OAK_SIGN, handSign, 3);
            when(player.getInventory()).thenReturn(inventory);
            when(blockSign.getAmount()).thenReturn(Integer.MAX_VALUE - 15);
            when(blockSign.isSimilar(contents)).thenReturn(true);
            when(inventory.addItem(org.mockito.ArgumentMatchers.any(ItemStack.class)))
                .thenAnswer(invocation -> new HashMap<>());

            invokeInteraction(player, block, blockSign, hand, handSign);

            verify(blockSign).setAmount(Integer.MAX_VALUE);
            ArgumentCaptor<ItemStack> mainHandCaptor = ArgumentCaptor.forClass(ItemStack.class);
            verify(inventory).setItemInMainHand(mainHandCaptor.capture());
            assertEquals(List.of("STONE 10"), mainHandCaptor.getValue().getItemMeta().getLore());
            assertEquals(1, mainHandCaptor.getValue().getAmount());
            ArgumentCaptor<ItemStack> addCaptor = ArgumentCaptor.forClass(ItemStack.class);
            verify(inventory, times(2)).addItem(addCaptor.capture());
            assertEquals("Empty", addCaptor.getAllValues().get(0).getItemMeta().getLore().get(0));
            assertEquals("STONE 5", addCaptor.getAllValues().get(1).getItemMeta().getLore().get(0));
            verify(player, never()).getWorld();
        } finally {
            MockBukkit.unmock();
        }
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
    void mergeWithLessThanOneStorageSignCapacityConsumesPartialStack() throws Exception {
        MockBukkit.mock();
        try {
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            Block block = mock(Block.class);
            StorageSign blockSign = mock(StorageSign.class);
            StorageSign handSign = StorageSign.fromSignLines(new String[] {
                StorageSign.HEADER_LINE, "STONE", "10"
            });
            ItemStack contents = handSign.getContents(1);
            ItemStack hand = StorageSign.createStorageSignItem(Material.OAK_SIGN, handSign, 1);
            when(player.getInventory()).thenReturn(inventory);
            when(blockSign.getAmount()).thenReturn(Integer.MAX_VALUE - 5);
            when(blockSign.isSimilar(contents)).thenReturn(true);
            invokeInteraction(player, block, blockSign, hand, handSign);

            verify(blockSign).setAmount(Integer.MAX_VALUE);
            ArgumentCaptor<ItemStack> mainHandCaptor = ArgumentCaptor.forClass(ItemStack.class);
            verify(inventory).setItemInMainHand(mainHandCaptor.capture());
            assertEquals(List.of("STONE 5"), mainHandCaptor.getValue().getItemMeta().getLore());
            assertEquals(1, mainHandCaptor.getValue().getAmount());
            verify(inventory, never()).addItem(org.mockito.ArgumentMatchers.any(ItemStack.class));
        } finally {
            MockBukkit.unmock();
        }
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
    void fullInventoryDropsEmptyBeforeReducedStackAfterPartialMerge() throws Exception {
        MockBukkit.mock();
        try {
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            World world = mock(World.class);
            Location location = mock(Location.class);
            Block block = mock(Block.class);
            StorageSign blockSign = mock(StorageSign.class);
            StorageSign handSign = StorageSign.fromSignLines(new String[] {
                StorageSign.HEADER_LINE, "STONE", "10"
            });
            ItemStack contents = handSign.getContents(1);
            ItemStack hand = StorageSign.createStorageSignItem(Material.OAK_SIGN, handSign, 3);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(location);
            when(blockSign.getAmount()).thenReturn(Integer.MAX_VALUE - 15);
            when(blockSign.isSimilar(contents)).thenReturn(true);
            when(inventory.addItem(org.mockito.ArgumentMatchers.any(ItemStack.class)))
                .thenAnswer(invocation -> {
                    ItemStack stack = invocation.getArgument(0);
                    HashMap<Integer, ItemStack> leftovers = new HashMap<>();
                    leftovers.put(0, stack);
                    return leftovers;
                });

            invokeInteraction(player, block, blockSign, hand, handSign);

            ArgumentCaptor<ItemStack> mainHandCaptor = ArgumentCaptor.forClass(ItemStack.class);
            verify(inventory).setItemInMainHand(mainHandCaptor.capture());
            assertEquals(List.of("STONE 10"), mainHandCaptor.getValue().getItemMeta().getLore());
            assertEquals(1, mainHandCaptor.getValue().getAmount());
            verify(blockSign).setAmount(Integer.MAX_VALUE);
            ArgumentCaptor<ItemStack> addCaptor = ArgumentCaptor.forClass(ItemStack.class);
            verify(inventory, times(2)).addItem(addCaptor.capture());
            assertEquals("Empty", addCaptor.getAllValues().get(0).getItemMeta().getLore().get(0));
            assertEquals("STONE 5", addCaptor.getAllValues().get(1).getItemMeta().getLore().get(0));
            ArgumentCaptor<ItemStack> dropCaptor = ArgumentCaptor.forClass(ItemStack.class);
            verify(world, times(2)).dropItemNaturally(org.mockito.ArgumentMatchers.eq(location), dropCaptor.capture());
            assertEquals("Empty", dropCaptor.getAllValues().get(0).getItemMeta().getLore().get(0));
            assertEquals("STONE 5", dropCaptor.getAllValues().get(1).getItemMeta().getLore().get(0));
        } finally {
            MockBukkit.unmock();
        }
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
        when(block.getType()).thenReturn(Material.OAK_SIGN);
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
    void signInSignDivisionStopsWhenStoredTemplateCannotBeRecovered() throws Exception {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack hand = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(false);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        when(block.getType()).thenReturn(Material.OAK_WALL_SIGN);
        when(blockSign.getAmount()).thenReturn(10);
        when(blockSign.isSignAsItem()).thenReturn(true);
        when(blockSign.getMaterial()).thenReturn(Material.OAK_WALL_SIGN);
        when(handSign.isUnregistered()).thenReturn(true);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(1);
        when(blockSign.getContents(1)).thenReturn(null);

        invokeInteraction(player, block, blockSign, hand, handSign);

        verify(blockSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(inventory, never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void signInSignDivisionStopsWhenTemplateCannotBeRestored() throws Exception {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack template = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(false);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        when(block.getType()).thenReturn(Material.OAK_WALL_SIGN);
        when(blockSign.getAmount()).thenReturn(10);
        when(blockSign.isSignAsItem()).thenReturn(true);
        when(blockSign.getMaterial()).thenReturn(Material.OAK_WALL_SIGN);
        when(blockSign.getContents(1)).thenReturn(template);
        when(handSign.isUnregistered()).thenReturn(true);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(1);

        invokeInteraction(player, block, blockSign, hand, handSign);

        verify(blockSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(inventory, never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void signInSignDivisionStopsWhenTemplateIsMissing() throws Exception {
        setManualExport(true);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack hand = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(false);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        when(block.getType()).thenReturn(Material.OAK_WALL_SIGN);
        when(blockSign.getAmount()).thenReturn(10);
        when(blockSign.isSignAsItem()).thenReturn(true);
        when(blockSign.getMaterial()).thenReturn(Material.OAK_WALL_SIGN);
        when(blockSign.getContents(1)).thenReturn(null);
        when(handSign.isUnregistered()).thenReturn(true);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(hand.getAmount()).thenReturn(1);

        invokeInteraction(player, block, blockSign, hand, handSign);

        verify(blockSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(inventory, never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void signInSignDivisionCreatesDividedStackAndUpdatesBlock() throws Exception {
        setManualExport(true);
        MockBukkit.mock();
        try {
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            Block block = mock(Block.class);
            ItemStack hand = mock(ItemStack.class);
            StorageSign blockSign = StorageSign.fromSignLines(
                new String[] {StorageSign.HEADER_LINE, "OakStorageSign", "10"});
            StorageSign handSign = StorageSign.fromSignLines(
                new String[] {StorageSign.HEADER_LINE, StorageSign.EMPTY_MARKER, "1"});
            ItemStack created = mock(ItemStack.class);
            when(player.getInventory()).thenReturn(inventory);
            when(player.isSneaking()).thenReturn(false);
            when(inventory.getContents()).thenReturn(new ItemStack[0]);
            when(block.getType()).thenReturn(Material.OAK_SIGN);
            when(hand.getType()).thenReturn(Material.OAK_SIGN);
            when(hand.getAmount()).thenReturn(1);
            try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
                signs.when(() -> StorageSign.createStorageSignItem(
                    org.mockito.ArgumentMatchers.eq(Material.OAK_SIGN),
                    org.mockito.ArgumentMatchers.any(StorageSign.class),
                    org.mockito.ArgumentMatchers.eq(1)))
                    .thenReturn(created);

                invokeInteraction(player, block, blockSign, hand, handSign);
            }

        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void signInSignDivisionStopsWhenComputedShareWouldBeZero() throws Exception {
        setManualExport(true);
        int original = getStaticInt("divideLimit");
        try {
            setStaticInt("divideLimit", 0);
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            Block block = mock(Block.class);
            StorageSign blockSign = mock(StorageSign.class);
            StorageSign handSign = mock(StorageSign.class);
            StorageSign divided = mock(StorageSign.class);
            ItemStack hand = mock(ItemStack.class);
            ItemStack template = mock(ItemStack.class);
            when(player.getInventory()).thenReturn(inventory);
            when(player.isSneaking()).thenReturn(false);
            when(inventory.getContents()).thenReturn(new ItemStack[0]);
            when(block.getType()).thenReturn(Material.OAK_SIGN);
            when(blockSign.getAmount()).thenReturn(10);
            when(blockSign.isSignAsItem()).thenReturn(true);
            when(blockSign.getMaterial()).thenReturn(Material.OAK_SIGN);
            when(blockSign.getContents(1)).thenReturn(template);
            when(handSign.isUnregistered()).thenReturn(true);
            when(hand.getType()).thenReturn(Material.OAK_SIGN);
            when(hand.getAmount()).thenReturn(1);

            try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
                signs.when(() -> StorageSign.fromStoredItem(template)).thenReturn(divided);

                invokeInteraction(player, block, blockSign, hand, handSign);
            }

            verify(divided, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
            verify(blockSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
            verify(inventory, never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
        } finally {
            setStaticInt("divideLimit", original);
        }
    }

    @Test
    void signInSignDivisionUsesConfiguredDivideLimitWhenNotSneaking() throws Exception {
        setManualExport(true);
        int original = getStaticInt("divideLimit");
        Field manualImport = ConfigLoader.class.getDeclaredField("manualImport");
        manualImport.setAccessible(true);
        MockBukkit.mock();
        try {
            setStaticInt("divideLimit", 0);
            manualImport.setBoolean(null, false);
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            Block block = mock(Block.class);
            StorageSign blockSign = StorageSign.fromStoredItem(new ItemStack(Material.OAK_SIGN));
            blockSign.setAmount(10);
            StorageSign handSign = mock(StorageSign.class);
            ItemStack hand = mock(ItemStack.class);
            ItemStack template = blockSign.getContents(1);
            StorageSign divided = StorageSign.fromStoredItem(template);
            when(player.getInventory()).thenReturn(inventory);
            when(player.isSneaking()).thenReturn(false);
            when(inventory.getContents()).thenReturn(new ItemStack[0]);
            when(block.getType()).thenReturn(Material.OAK_SIGN);
            when(hand.getType()).thenReturn(Material.OAK_SIGN);
            when(hand.getAmount()).thenReturn(1);

            try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
                signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(handSign);
                signs.when(() -> StorageSign.fromStoredItem(template)).thenReturn(divided);

                when(handSign.isUnregistered()).thenReturn(true);
                invokeInteraction(player, block, blockSign, hand, handSign);
            }

            verify(inventory).setItemInMainHand(org.mockito.ArgumentMatchers.any());
            assertEquals(5, blockSign.getAmount());
        } finally {
            manualImport.setBoolean(null, true);
            setStaticInt("divideLimit", original);
            MockBukkit.unmock();
        }
    }

    @Test
    void signInSignDivisionUsesConfiguredSneakDivideLimitWhenSneaking() throws Exception {
        setManualExport(true);
        int originalDivide = getStaticInt("divideLimit");
        int originalSneak = getStaticInt("sneakDivideLimit");
        Field manualImport = ConfigLoader.class.getDeclaredField("manualImport");
        manualImport.setAccessible(true);
        MockBukkit.mock();
        try {
            setStaticInt("divideLimit", 0);
            setStaticInt("sneakDivideLimit", 2);
            manualImport.setBoolean(null, false);
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            Block block = mock(Block.class);
            StorageSign blockSign = StorageSign.fromStoredItem(new ItemStack(Material.OAK_SIGN));
            blockSign.setAmount(10);
            StorageSign handSign = mock(StorageSign.class);
            ItemStack hand = mock(ItemStack.class);
            ItemStack template = blockSign.getContents(1);
            StorageSign divided = StorageSign.fromStoredItem(template);
            when(player.getInventory()).thenReturn(inventory);
            when(player.isSneaking()).thenReturn(true);
            when(inventory.getContents()).thenReturn(new ItemStack[0]);
            when(block.getType()).thenReturn(Material.OAK_SIGN);
            when(hand.getType()).thenReturn(Material.OAK_SIGN);
            when(hand.getAmount()).thenReturn(1);

            try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
                signs.when(() -> StorageSign.fromItemStack(hand)).thenReturn(handSign);
                signs.when(() -> StorageSign.fromStoredItem(template)).thenReturn(divided);

                when(handSign.isUnregistered()).thenReturn(true);
                invokeInteraction(player, block, blockSign, hand, handSign);
            }

            verify(inventory).setItemInMainHand(org.mockito.ArgumentMatchers.any());
            assertEquals(8, blockSign.getAmount());
        } finally {
            manualImport.setBoolean(null, true);
            setStaticInt("divideLimit", originalDivide);
            setStaticInt("sneakDivideLimit", originalSneak);
            MockBukkit.unmock();
        }
    }

    @Test
    void signInSignImportLoopHandlesNullMismatchedAndZeroSizedEntries() throws Exception {
        setManualExport(true);
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, true);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack wrongType = mock(ItemStack.class);
        ItemStack zeroSized = mock(ItemStack.class);
        StorageSign itemSign = mock(StorageSign.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(false);
        when(inventory.getContents()).thenReturn(new ItemStack[] {null, wrongType, zeroSized});
        when(blockSign.getAmount()).thenReturn(3);
        when(blockSign.isSignAsItem()).thenReturn(true);
        when(blockSign.getMaterial()).thenReturn(Material.OAK_SIGN);
        when(handSign.isUnregistered()).thenReturn(true);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(wrongType.getType()).thenReturn(Material.STONE);
        when(zeroSized.getType()).thenReturn(Material.OAK_SIGN);
        when(zeroSized.getAmount()).thenReturn(0);
        when(itemSign.isUnregistered()).thenReturn(true);
        when(blockSign.isSimilar(zeroSized)).thenReturn(true);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(zeroSized)).thenReturn(itemSign);
            invokeInteraction(player, block, blockSign, hand, handSign);
        }

        verify(blockSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(inventory, never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void signInSignImportLoopSkipsAlreadyRegisteredInventoryItems() throws Exception {
        setManualExport(true);
        Field field = ConfigLoader.class.getDeclaredField("manualImport");
        field.setAccessible(true);
        field.setBoolean(null, true);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Block block = mock(Block.class);
        StorageSign blockSign = mock(StorageSign.class);
        StorageSign handSign = mock(StorageSign.class);
        ItemStack hand = mock(ItemStack.class);
        ItemStack item = mock(ItemStack.class);
        StorageSign registered = mock(StorageSign.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(false);
        when(inventory.getContents()).thenReturn(new ItemStack[] { item });
        when(blockSign.getAmount()).thenReturn(3);
        when(blockSign.isSignAsItem()).thenReturn(true);
        when(blockSign.getMaterial()).thenReturn(Material.OAK_SIGN);
        when(handSign.isUnregistered()).thenReturn(true);
        when(hand.getType()).thenReturn(Material.OAK_SIGN);
        when(item.getType()).thenReturn(Material.OAK_SIGN);
        when(item.getAmount()).thenReturn(3);
        when(registered.isUnregistered()).thenReturn(false);

        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromItemStack(item)).thenReturn(registered);
            invokeInteraction(player, block, blockSign, hand, handSign);
        }

        verify(blockSign, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        verify(inventory, never()).setItemInMainHand(org.mockito.ArgumentMatchers.any());
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

    private static void setStaticInt(String fieldName, int value) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(null, value);
    }

    private static int getStaticInt(String fieldName) throws Exception {
        Field field = ConfigLoader.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private record Fixture(Player player, PlayerInventory inventory, Block block,
                           StorageSign blockSign, ItemStack hand, StorageSign handSign) {}
}
