package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Beehive;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

@Tag("integration")
class BlockPropertyCodecTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void sortsExplicitPropertiesAndPersistsAcrossSignPdcAndItemLore() {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(Material.OAK_STAIRS);
        when(data.getAsString(true)).thenReturn("minecraft:oak_stairs[facing=east,half=top]");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.createBlockData(eq(Material.OAK_STAIRS), anyString()))
                .thenReturn(data);
            StorageSign stored = StorageSign.fromSignLines(new String[] {
                "StorageSign", "OAK_STAIRS[half=top,facing=east]", "4"
            });
            assertNotNull(stored);
            assertEquals("OAK_STAIRS[facing=east,half=top]", stored.getIdentifier());

            var block = server.addSimpleWorld("block-properties").getBlockAt(0, 64, 0);
            block.setType(Material.OAK_SIGN);
            stored.applyToSign((Sign) block.getState());
            Sign sign = (Sign) block.getState();
            assertEquals(stored.getIdentifier(), sign.getPersistentDataContainer().get(
                new NamespacedKey("storagesign", "storage_identifier"), PersistentDataType.STRING));
            StorageSign fromSign = StorageSign.fromSign(sign);
            assertNotNull(fromSign);
            assertEquals(stored.getIdentifier(), fromSign.getIdentifier());

            ItemStack transport = StorageSign.createStorageSignItem(Material.OAK_SIGN, fromSign, 1);
            assertEquals(List.of("OAK_STAIRS[facing=east,half=top] 4"),
                transport.getItemMeta().getLore());
            StorageSign fromItem = StorageSign.fromItemStack(transport);
            assertNotNull(fromItem);
            assertEquals(stored.getIdentifier(), fromItem.getIdentifier());
            assertEquals(4, fromItem.getAmount());
        }
    }

    @Test
    void rejectsMalformedStateAndNeverUsesTheLegacyStoneSlabAlias() {
        for (String bad : List.of(
            "STONE_SLAB[]", "STONE_SLAB[type=top,]", "STONE_SLAB[type=top,type=bottom]",
            "STONE_SLAB[type=]", "STONE_SLAB[type=top]tail", "STONE_SLAB[[type=top]]",
            "STONE_SLAB[type=top,unknown=value]".repeat(30), "DIAMOND_SWORD[facing=east]"
        )) {
            assertNull(StorageSign.parseIdentifier(bad, 1), bad);
        }
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(Material.STONE_SLAB);
        when(data.getAsString(true)).thenReturn("minecraft:stone_slab[type=top]");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.createBlockData(eq(Material.STONE_SLAB), anyString()))
                .thenReturn(data);
            StorageSign stateful = StorageSign.parseIdentifier("STONE_SLAB[type=top]", 1);
            assertNotNull(stateful);
            assertEquals(Material.STONE_SLAB, stateful.getMaterial());
            assertEquals("STONE_SLAB[type=top]", stateful.getIdentifier());
            assertEquals(Material.SMOOTH_STONE_SLAB,
                StorageSign.parseIdentifier("STONE_SLAB", 1).getMaterial());
        }
    }

    @Test
    void rejectsUnknownPropertyValuesAndUnknownSerializedRepresentations() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.createBlockData(eq(Material.OAK_STAIRS), anyString()))
                .thenThrow(new IllegalArgumentException("invalid block property"));
            assertNull(StorageSign.parseIdentifier("OAK_STAIRS[facing=not_a_direction]", 1));

            BlockDataMeta meta = mock(BlockDataMeta.class);
            when(meta.hasBlockData()).thenReturn(true);
            when(meta.serialize()).thenReturn(Map.of("BlockStateTag", "unexpected"));
            assertNull(BlockPropertyCodec.fromItemMeta(Material.OAK_STAIRS, meta));
            when(meta.serialize()).thenReturn(Map.of("BlockStateTag", Map.of("facing", 12)));
            assertNull(BlockPropertyCodec.fromItemMeta(Material.OAK_STAIRS, meta));
            when(meta.serialize()).thenReturn(Map.of("BlockStateTag", Map.of("facing", "east", "bad key", "x")));
            assertNull(BlockPropertyCodec.fromItemMeta(Material.OAK_STAIRS, meta));
        }
    }

    @Test
    void registrationRejectsNamesLoreAndPdcOnStatefulItems() {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(Material.OAK_STAIRS);
        when(data.getAsString(true)).thenReturn("minecraft:oak_stairs[facing=east]");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.createBlockData(eq(Material.OAK_STAIRS), anyString()))
                .thenReturn(data);
            ItemStack item = mock(ItemStack.class);
            when(item.getType()).thenReturn(Material.OAK_STAIRS);
            BlockDataMeta meta = mock(BlockDataMeta.class);
            when(meta.hasBlockData()).thenReturn(true);
            when(meta.serialize()).thenReturn(Map.of("BlockStateTag", Map.of("facing", "east")));
            when(item.getItemMeta()).thenReturn(meta);

            when(meta.hasDisplayName()).thenReturn(true);
            assertNull(StorageSign.fromStoredItem(item));
            when(meta.hasDisplayName()).thenReturn(false);
            when(meta.hasLore()).thenReturn(true);
            assertNull(StorageSign.fromStoredItem(item));
            when(meta.hasLore()).thenReturn(false);
            when(meta.getItemFlags()).thenReturn(Set.of());
            PersistentDataContainer pdc = mock(PersistentDataContainer.class);
            when(meta.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.isEmpty()).thenReturn(false);
            assertNull(StorageSign.fromStoredItem(item));
        }
    }

    @Test
    void blockStateMetaMayCarryPropertiesButStillNeedsExactRestoration() {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(Material.OAK_STAIRS);
        when(data.getAsString(true)).thenReturn("minecraft:oak_stairs[facing=east]");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.createBlockData(eq(Material.OAK_STAIRS), anyString()))
                .thenReturn(data);
            BlockDataMeta meta = mock(BlockDataMeta.class,
                withSettings().extraInterfaces(BlockStateMeta.class));
            when(meta.hasBlockData()).thenReturn(true);
            when(meta.serialize()).thenReturn(Map.of("BlockStateTag", Map.of("facing", "east")));
            assertEquals("OAK_STAIRS[facing=east]",
                BlockPropertyCodec.fromItemMeta(Material.OAK_STAIRS, meta));

            ItemStack decorated = mock(ItemStack.class);
            when(decorated.getType()).thenReturn(Material.OAK_STAIRS);
            when(decorated.getItemMeta()).thenReturn(meta);
            when(meta.hasDisplayName()).thenReturn(true);
            assertNull(StorageSign.fromStoredItem(decorated));
        }
    }

    @Test
    void matchingPropertiesNeverHideShulkerOrBeeContents() {
        ShulkerBox shulker = mock(ShulkerBox.class);
        Inventory inventory = mock(Inventory.class);
        when(shulker.getInventory()).thenReturn(inventory);
        when(inventory.isEmpty()).thenReturn(false);
        assertOccupiedTileRejected(Material.WHITE_SHULKER_BOX,
            "WHITE_SHULKER_BOX[facing=north]", "facing", "north", shulker);

        Beehive beehive = mock(Beehive.class);
        when(beehive.getEntityCount()).thenReturn(1);
        assertOccupiedTileRejected(Material.BEEHIVE,
            "BEEHIVE[honey_level=0]", "honey_level", "0", beehive);
    }

    @Test
    void emptyTileStateStillUsesFullItemSimilarity() {
        ShulkerBox shulker = mock(ShulkerBox.class);
        Inventory inventory = mock(Inventory.class);
        when(shulker.getInventory()).thenReturn(inventory);
        when(inventory.isEmpty()).thenReturn(true);
        assertEmptyTileCanMatch(Material.WHITE_SHULKER_BOX,
            "WHITE_SHULKER_BOX[facing=north]", "facing", "north", shulker);

        Beehive beehive = mock(Beehive.class);
        when(beehive.getEntityCount()).thenReturn(0);
        assertEmptyTileCanMatch(Material.BEEHIVE,
            "BEEHIVE[honey_level=0]", "honey_level", "0", beehive);
    }

    private void assertOccupiedTileRejected(Material material, String identifier,
                                            String property, String value,
                                            org.bukkit.block.BlockState state) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        BlockDataMeta meta = mock(BlockDataMeta.class,
            withSettings().extraInterfaces(BlockStateMeta.class));
        when(meta.hasBlockData()).thenReturn(true);
        when(meta.serialize()).thenReturn(Map.of("BlockStateTag", Map.of(property, value)));
        when(((BlockStateMeta) meta).getBlockState()).thenReturn(state);
        when(item.getItemMeta()).thenReturn(meta);
        ItemStack weaklyMatchingReference = mock(ItemStack.class);
        when(weaklyMatchingReference.isSimilar(item)).thenReturn(true);
        StorageSign stored = new StorageSign(material, (short) 0, 1,
            null, null, false, identifier);

        assertNull(StorageSign.fromStoredItem(item));
        assertFalse(StorageSignItemCodec.isSimilar(stored, item, weaklyMatchingReference));
    }

    private void assertEmptyTileCanMatch(Material material, String identifier,
                                         String property, String value,
                                         org.bukkit.block.BlockState state) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        BlockDataMeta meta = mock(BlockDataMeta.class,
            withSettings().extraInterfaces(BlockStateMeta.class));
        when(meta.hasBlockData()).thenReturn(true);
        when(meta.serialize()).thenReturn(Map.of("BlockStateTag", Map.of(property, value)));
        when(((BlockStateMeta) meta).getBlockState()).thenReturn(state);
        when(meta.getItemFlags()).thenReturn(Set.of());
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.isEmpty()).thenReturn(true);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(item.getItemMeta()).thenReturn(meta);
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(material);
        when(data.getAsString(true)).thenReturn(
            "minecraft:" + material.name().toLowerCase() + "[" + property + "=" + value + "]");
        StorageSign stored = new StorageSign(material, (short) 0, 1,
            null, null, false, identifier);
        ItemStack reference = mock(ItemStack.class);
        when(reference.isSimilar(item)).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.createBlockData(eq(material), anyString())).thenReturn(data);
            assertTrue(StorageSignItemCodec.isSimilar(stored, item, reference));
            when(reference.isSimilar(item)).thenReturn(false);
            assertFalse(StorageSignItemCodec.isSimilar(stored, item, reference));
        }
    }
}
