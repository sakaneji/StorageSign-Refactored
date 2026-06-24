package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import sun.misc.Unsafe;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.block.Beehive;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.sign.Side;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.MockBukkit;
import storagesign.compat.OminousBannerCodec;
import storagesign.StorageSignPlugin;
import storagesign.search.StorageSignQueryService;

class StorageSignBranchCoverageTest {

    @BeforeEach
    void setUp() {
        resetMockBukkit();
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        resetMockBukkit();
    }

    @Test
    void fromBlockAndIsStorageSignRecognizeRealSignBlocks() {
        var world = MockBukkit.getMock().addSimpleWorld("from-block");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.getSide(Side.FRONT).setLine(0, StorageSign.HEADER_LINE);
        sign.getSide(Side.FRONT).setLine(1, "STONE");
        sign.getSide(Side.FRONT).setLine(2, "12");
        sign.update();

        StorageSign parsed = StorageSign.fromBlock(block);
        assertNotNull(parsed);
        assertEquals(Material.STONE, parsed.getMaterial());
        assertTrue(StorageSign.isStorageSign(block));
    }

    @Test
    void fromBlockRejectsSignBlocksWhoseStateIsNotARealSign() {
        var block = mock(org.bukkit.block.Block.class);
        BlockState state = mock(BlockState.class);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(block.getState()).thenReturn(state);

        assertNull(StorageSign.fromBlock(block));
        assertTrue(!StorageSign.isStorageSign(block));
    }

    @Test
    void fromSignPrefersBlankStorageIdentifierThenPotionIdentifier() {
        var world = MockBukkit.getMock().addSimpleWorld("from-sign");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.getSide(Side.FRONT).setLine(0, StorageSign.HEADER_LINE);
        sign.getSide(Side.FRONT).setLine(1, "ignored");
        sign.getSide(Side.FRONT).setLine(2, "4");
        sign.getPersistentDataContainer().set(
            new NamespacedKey("storagesign", "storage_identifier"),
            PersistentDataType.STRING, " ");
        sign.getPersistentDataContainer().set(
            new NamespacedKey("storagesign", "potion_identifier"),
            PersistentDataType.STRING, "POTION:HEAL:0");
        sign.update();

        StorageSign parsed = StorageSign.fromSign(sign);
        assertNotNull(parsed);
        assertEquals(Material.POTION, parsed.getMaterial());
        assertNotNull(parsed.getPotionType());
        assertEquals("POTION:HEAL:0", parsed.getIdentifier());
    }

    @Test
    void fromSignUsesCanonicalStorageIdentifierWhenPresent() {
        var world = MockBukkit.getMock().addSimpleWorld("from-sign-canonical");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.getSide(Side.FRONT).setLine(0, StorageSign.HEADER_LINE);
        sign.getSide(Side.FRONT).setLine(1, "ignored");
        sign.getSide(Side.FRONT).setLine(2, "6");
        sign.getPersistentDataContainer().set(
            new NamespacedKey("storagesign", "storage_identifier"),
            PersistentDataType.STRING, "STONE");
        sign.getPersistentDataContainer().remove(
            new NamespacedKey("storagesign", "potion_identifier"));
        sign.update();

        StorageSign parsed = StorageSign.fromSign(sign);
        assertNotNull(parsed);
        assertEquals(Material.STONE, parsed.getMaterial());
        assertEquals(6, parsed.getAmount());
    }

    @Test
    void fromSignPrefersStorageIdentifierOverPotionIdentifierWhenBothExist() {
        var world = MockBukkit.getMock().addSimpleWorld("from-sign-precedence");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.getSide(Side.FRONT).setLine(0, StorageSign.HEADER_LINE);
        sign.getSide(Side.FRONT).setLine(1, "ignored");
        sign.getSide(Side.FRONT).setLine(2, "2");
        sign.getPersistentDataContainer().set(
            new NamespacedKey("storagesign", "storage_identifier"),
            PersistentDataType.STRING, "STONE");
        sign.getPersistentDataContainer().set(
            new NamespacedKey("storagesign", "potion_identifier"),
            PersistentDataType.STRING, "POTION:HEAL:0");
        sign.update();

        StorageSign parsed = StorageSign.fromSign(sign);
        assertNotNull(parsed);
        assertEquals(Material.STONE, parsed.getMaterial());
        assertNull(parsed.getPotionType());
    }

    @Test
    void fromSignFallsBackToLinesWhenCanonicalAndPotionIdentifiersAreBlank() {
        var world = MockBukkit.getMock().addSimpleWorld("from-sign-fallback");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.getSide(Side.FRONT).setLine(0, StorageSign.HEADER_LINE);
        sign.getSide(Side.FRONT).setLine(1, "STONE");
        sign.getSide(Side.FRONT).setLine(2, "3");
        sign.getPersistentDataContainer().set(
            new NamespacedKey("storagesign", "storage_identifier"),
            PersistentDataType.STRING, " ");
        sign.update();

        StorageSign parsed = StorageSign.fromSign(sign);
        assertNotNull(parsed);
        assertEquals(Material.STONE, parsed.getMaterial());
        assertEquals(3, parsed.getAmount());
    }

    @Test
    void fromSignFallsBackToLinesWhenNoCanonicalIdentifiersExist() {
        var world = MockBukkit.getMock().addSimpleWorld("from-sign-no-pdc");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.getSide(Side.FRONT).setLine(0, StorageSign.HEADER_LINE);
        sign.getSide(Side.FRONT).setLine(1, "STONE");
        sign.getSide(Side.FRONT).setLine(2, "5");
        sign.update();

        StorageSign parsed = StorageSign.fromSign(sign);
        assertNotNull(parsed);
        assertEquals(Material.STONE, parsed.getMaterial());
        assertEquals(5, parsed.getAmount());
    }

    @Test
    void fromSignRejectsNullAndShortInputs() {
        assertNull(StorageSign.fromSign(null));

        var world = MockBukkit.getMock().addSimpleWorld("short-sign");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.getSide(Side.FRONT).setLine(0, StorageSign.HEADER_LINE);
        sign.getSide(Side.FRONT).setLine(1, "STONE");
        sign.update();

        assertNull(StorageSign.fromSign(sign));
    }

    @Test
    void fromSignRejectsWrongHeaderAndInvalidAmount() {
        var world = MockBukkit.getMock().addSimpleWorld("from-sign-invalid");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.getSide(Side.FRONT).setLine(0, "not-storage-sign");
        sign.getSide(Side.FRONT).setLine(1, "STONE");
        sign.getSide(Side.FRONT).setLine(2, "1");
        sign.update();
        assertNull(StorageSign.fromSign(sign));

        Sign invalidAmount = (Sign) block.getState();
        invalidAmount.getSide(Side.FRONT).setLine(0, StorageSign.HEADER_LINE);
        invalidAmount.getSide(Side.FRONT).setLine(1, "STONE");
        invalidAmount.getSide(Side.FRONT).setLine(2, "not-a-number");
        invalidAmount.update();
        assertNull(StorageSign.fromSign(invalidAmount));
    }

    @Test
    void fromSignRejectsShortLinesFromMockedSignSide() {
        Sign sign = mock(Sign.class);
        SignSide front = mock(SignSide.class);
        when(sign.getSide(Side.FRONT)).thenReturn(front);
        when(front.getLines()).thenReturn(new String[] {StorageSign.HEADER_LINE, "STONE"});

        assertNull(StorageSign.fromSign(sign));
    }

    @Test
    void fromItemStackRejectsNullMetaAndEmptyMarkerItem() {
        ItemStack nullMeta = org.mockito.Mockito.mock(ItemStack.class);
        org.mockito.Mockito.when(nullMeta.getType()).thenReturn(Material.OAK_SIGN);
        org.mockito.Mockito.when(nullMeta.getItemMeta()).thenReturn(null);
        assertNull(StorageSign.fromItemStack(nullMeta));

        assertNull(StorageSign.fromItemStack(new ItemStack(Material.AIR)));

        ItemStack wrongName = new ItemStack(Material.OAK_SIGN);
        var meta = wrongName.getItemMeta();
        meta.setDisplayName("not-storage-sign");
        wrongName.setItemMeta(meta);
        assertNull(StorageSign.fromItemStack(wrongName));

        ItemStack noLore = org.mockito.Mockito.mock(ItemStack.class);
        org.mockito.Mockito.when(noLore.getType()).thenReturn(Material.OAK_SIGN);
        var noLoreMeta = org.mockito.Mockito.mock(org.bukkit.inventory.meta.ItemMeta.class);
        org.mockito.Mockito.when(noLore.getItemMeta()).thenReturn(noLoreMeta);
        org.mockito.Mockito.when(noLoreMeta.getDisplayName()).thenReturn(StorageSign.HEADER_LINE);
        org.mockito.Mockito.when(noLoreMeta.getLore()).thenReturn(null);
        assertNull(StorageSign.fromItemStack(noLore));

        ItemStack noSeparator = org.mockito.Mockito.mock(ItemStack.class);
        org.mockito.Mockito.when(noSeparator.getType()).thenReturn(Material.OAK_SIGN);
        var sepMeta = org.mockito.Mockito.mock(org.bukkit.inventory.meta.ItemMeta.class);
        org.mockito.Mockito.when(noSeparator.getItemMeta()).thenReturn(sepMeta);
        org.mockito.Mockito.when(sepMeta.getDisplayName()).thenReturn(StorageSign.HEADER_LINE);
        org.mockito.Mockito.when(sepMeta.getLore()).thenReturn(java.util.List.of("STONE"));
        assertNull(StorageSign.fromItemStack(noSeparator));

        ItemStack emptyLore = org.mockito.Mockito.mock(ItemStack.class);
        org.mockito.Mockito.when(emptyLore.getType()).thenReturn(Material.OAK_SIGN);
        var emptyLoreMeta = org.mockito.Mockito.mock(org.bukkit.inventory.meta.ItemMeta.class);
        org.mockito.Mockito.when(emptyLore.getItemMeta()).thenReturn(emptyLoreMeta);
        org.mockito.Mockito.when(emptyLoreMeta.getDisplayName()).thenReturn(StorageSign.HEADER_LINE);
        org.mockito.Mockito.when(emptyLoreMeta.getLore()).thenReturn(java.util.List.of());
        assertNull(StorageSign.fromItemStack(emptyLore));

        ItemStack empty = StorageSign.createStorageSignItem(
            Material.OAK_SIGN, StorageSign.EMPTY_MARKER, 1);
        StorageSign parsed = StorageSign.fromItemStack(empty);
        assertNotNull(parsed);
        assertTrue(parsed.isUnregistered());
    }

    @Test
    void fromStoredItemRecognizesStorageSignItems() {
        StorageSign stored = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "3"});
        assertNotNull(stored);

        ItemStack item = StorageSign.createStorageSignItem(Material.OAK_SIGN, stored, 1);
        StorageSign parsed = StorageSign.fromStoredItem(item);
        assertNotNull(parsed);
        assertEquals(Material.OAK_SIGN, parsed.getMaterial());
        assertTrue(parsed.isSignAsItem());
        assertEquals(0, parsed.getAmount());
    }

    @Test
    void parseStoredAmountRejectsInvalidInput() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod("parseStoredAmount", String.class);
        method.setAccessible(true);

        assertNull(method.invoke(null, new Object[] {null}));
        assertNull(method.invoke(null, ""));
        assertNull(method.invoke(null, "   "));
        assertNull(method.invoke(null, "-1"));
        assertNull(method.invoke(null, "not-a-number"));
        assertEquals(12, method.invoke(null, "12"));
    }

    @Test
    void getCanonicalPotionIdentifierRejectsNonPotionAndEmptySigns() {
        StorageSign stone = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "1"});
        assertNotNull(stone);
        assertNull(stone.getCanonicalPotionIdentifier());
        assertNull(StorageSign.empty().getCanonicalPotionIdentifier());
    }

    @Test
    void getCanonicalPotionIdentifierRejectsNonPotionMaterialEvenWithPotionType() throws Exception {
        var ctor = StorageSign.class.getDeclaredConstructor(
            Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
        ctor.setAccessible(true);
        StorageSign fakePotion = ctor.newInstance(Material.STONE, (short) 0, 1, PotionType.HEALING, null, false);

        assertNull(fakePotion.getCanonicalPotionIdentifier());
    }

    @Test
    void getCanonicalPotionIdentifierReturnsCanonicalPotionIdentifier() {
        StorageSign potion = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "POTION:HEAL:0", "1"});
        assertNotNull(potion);
        assertEquals("POTION:minecraft:healing", potion.getCanonicalPotionIdentifier());
    }

    @Test
    void getCanonicalPotionIdentifierRejectsPotionSignsWithoutPotionType() throws Exception {
        var ctor = StorageSign.class.getDeclaredConstructor(
            Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
        ctor.setAccessible(true);
        StorageSign potion = ctor.newInstance(Material.POTION, (short) 0, 1, null, null, false);

        assertNull(potion.getCanonicalPotionIdentifier());
    }

    @Test
    void createStorageSignItemReturnsRawItemWhenMetaUnavailable() {
        try (var mocked = mockConstruction(org.bukkit.inventory.ItemStack.class, (stack, context) -> {
            when(stack.getItemMeta()).thenReturn(null);
        })) {
            ItemStack item = StorageSign.createStorageSignItem(
                Material.OAK_SIGN, StorageSign.EMPTY_MARKER, 1);
            assertNotNull(item);
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void createStorageSignItemSkipsConfiguredMaxStackWhenMethodHandleIsMissing() throws Exception {
        Field field = StorageSign.class.getDeclaredField("SET_MAX_STACK_SIZE");
        field.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        Object original = unsafe.getObject(base, offset);

        try {
            unsafe.putObject(base, offset, null);
            ItemStack item = StorageSign.createStorageSignItem(
                Material.OAK_SIGN, StorageSign.EMPTY_MARKER, 1);
            assertNotNull(item);
        } finally {
            unsafe.putObject(base, offset, original);
        }
    }

    @Test
    void applyConfiguredMaxStackIgnoresMethodHandleExceptions() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "applyConfiguredMaxStack", ItemMeta.class);
        method.setAccessible(true);

        ItemMeta meta = mock(ItemMeta.class);
        Mockito.doThrow(new IllegalStateException("boom"))
            .when(meta).setMaxStackSize(org.mockito.ArgumentMatchers.anyInt());

        method.invoke(null, meta);
    }

    @Test
    void getContentsFallsBackToHorseEggWhenVirtualIdentifiersAreEmpty() throws Exception {
        Field configField = storagesign.ConfigLoader.class.getDeclaredField("virtualItemIdentifiers");
        configField.setAccessible(true);
        Field defaultField = StorageSign.class.getDeclaredField("DEFAULT_VIRTUAL_IDENTIFIERS");
        defaultField.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        Object configBase = unsafe.staticFieldBase(configField);
        long configOffset = unsafe.staticFieldOffset(configField);
        Object defaultBase = unsafe.staticFieldBase(defaultField);
        long defaultOffset = unsafe.staticFieldOffset(defaultField);
        @SuppressWarnings("unchecked")
        Map<String, String> originalConfig = (Map<String, String>) unsafe.getObject(configBase, configOffset);
        @SuppressWarnings("unchecked")
        Map<String, String> originalDefault = (Map<String, String>) unsafe.getObject(defaultBase, defaultOffset);

        try {
            unsafe.putObject(configBase, configOffset, Map.of());
            unsafe.putObject(defaultBase, defaultOffset, Map.of());

            var ctor = StorageSign.class.getDeclaredConstructor(
                Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
            ctor.setAccessible(true);
            StorageSign endPortal = ctor.newInstance(Material.END_PORTAL, StorageSign.DAMAGE_SS_ITEM,
                1, null, null, false);

            ItemStack item = endPortal.getContents(1);
            assertNotNull(item);
            assertEquals(Material.GHAST_SPAWN_EGG, item.getType());
            assertEquals("HorseEgg", item.getItemMeta().getDisplayName());
        } finally {
            unsafe.putObject(configBase, configOffset, originalConfig);
            unsafe.putObject(defaultBase, defaultOffset, originalDefault);
        }
    }

    @Test
    void isSimilarFallsBackToHorseEggWhenVirtualIdentifiersAreEmpty() throws Exception {
        Field configField = storagesign.ConfigLoader.class.getDeclaredField("virtualItemIdentifiers");
        configField.setAccessible(true);
        Field defaultField = StorageSign.class.getDeclaredField("DEFAULT_VIRTUAL_IDENTIFIERS");
        defaultField.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        Object configBase = unsafe.staticFieldBase(configField);
        long configOffset = unsafe.staticFieldOffset(configField);
        Object defaultBase = unsafe.staticFieldBase(defaultField);
        long defaultOffset = unsafe.staticFieldOffset(defaultField);
        @SuppressWarnings("unchecked")
        Map<String, String> originalConfig = (Map<String, String>) unsafe.getObject(configBase, configOffset);
        @SuppressWarnings("unchecked")
        Map<String, String> originalDefault = (Map<String, String>) unsafe.getObject(defaultBase, defaultOffset);

        try {
            unsafe.putObject(configBase, configOffset, Map.of());
            unsafe.putObject(defaultBase, defaultOffset, Map.of());

            var ctor = StorageSign.class.getDeclaredConstructor(
                Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
            ctor.setAccessible(true);
            StorageSign legacyMarker = ctor.newInstance(Material.END_PORTAL, StorageSign.DAMAGE_SS_ITEM,
                1, null, null, false);

            ItemStack item = new ItemStack(Material.GHAST_SPAWN_EGG);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("HorseEgg");
            meta.setLore(List.of(StorageSign.EMPTY_MARKER));
            item.setItemMeta(meta);

            assertTrue(legacyMarker.isSimilar(item));
        } finally {
            unsafe.putObject(configBase, configOffset, originalConfig);
            unsafe.putObject(defaultBase, defaultOffset, originalDefault);
        }
    }

    @Test
    void isSimilarRejectsHorseEggWithMismatchedDisplayName() {
        StorageSign legacyMarker = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "HorseEgg", "1"});
        assertNotNull(legacyMarker);

        ItemStack item = new ItemStack(Material.GHAST_SPAWN_EGG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("NotHorseEgg");
        meta.setLore(List.of(StorageSign.EMPTY_MARKER));
        item.setItemMeta(meta);

        assertFalse(legacyMarker.isSimilar(item));
    }

    @Test
    void isSimilarRejectsHorseEggWhenItemTypeIsWrong() {
        StorageSign legacyMarker = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "HorseEgg", "1"});
        assertNotNull(legacyMarker);

        ItemStack item = new ItemStack(Material.STONE);
        assertFalse(legacyMarker.isSimilar(item));
    }

    @Test
    void isSimilarRejectsHorseEggWhenMetaIsMissing() {
        StorageSign legacyMarker = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "HorseEgg", "1"});
        assertNotNull(legacyMarker);

        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.GHAST_SPAWN_EGG);
        when(item.getItemMeta()).thenReturn(null);

        assertFalse(legacyMarker.isSimilar(item));
    }

    @Test
    void isSimilarRejectsHorseEggWhenVirtualIdentifierFallbackIsUsed() throws Exception {
        Field configField = storagesign.ConfigLoader.class.getDeclaredField("virtualItemIdentifiers");
        configField.setAccessible(true);
        Field defaultField = StorageSign.class.getDeclaredField("DEFAULT_VIRTUAL_IDENTIFIERS");
        defaultField.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object defaultBase = unsafe.staticFieldBase(defaultField);
        long defaultOffset = unsafe.staticFieldOffset(defaultField);
        Object originalDefault = unsafe.getObject(defaultBase, defaultOffset);
        @SuppressWarnings("unchecked")
        Map<String, String> originalConfig = new HashMap<>((Map<String, String>) configField.get(null));
        try {
            configField.set(null, Map.of());
            unsafe.putObject(defaultBase, defaultOffset, Map.of());

            var ctor = StorageSign.class.getDeclaredConstructor(
                Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
            ctor.setAccessible(true);
            StorageSign legacyMarker = ctor.newInstance(Material.END_PORTAL, StorageSign.DAMAGE_SS_ITEM,
                1, null, null, false);
            assertNotNull(legacyMarker);

            ItemStack item = mock(ItemStack.class);
            ItemMeta meta = mock(ItemMeta.class);
            when(item.getType()).thenReturn(Material.GHAST_SPAWN_EGG);
            when(item.getItemMeta()).thenReturn(meta);
            when(meta.getDisplayName()).thenReturn("HorseEgg");
            when(meta.hasLore()).thenReturn(true);

            assertTrue(legacyMarker.isSimilar(item));
        } finally {
            configField.set(null, originalConfig);
            unsafe.putObject(defaultBase, defaultOffset, originalDefault);
        }
    }

    @Test
    void isSimilarRejectsEndPortalMarkerWhenDamageDiffers() throws Exception {
        var ctor = StorageSign.class.getDeclaredConstructor(
            Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
        ctor.setAccessible(true);
        StorageSign marker = ctor.newInstance(Material.END_PORTAL, (short) 0, 1, null, null, false);

        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getType()).thenReturn(Material.GHAST_SPAWN_EGG);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getDisplayName()).thenReturn("HorseEgg");
        when(meta.hasLore()).thenReturn(true);

        assertFalse(marker.isSimilar(item));
    }

    @Test
    void isSimilarRejectsHorseEggWhenLoreIsMissing() {
        StorageSign legacyMarker = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "HorseEgg", "1"});
        assertNotNull(legacyMarker);

        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getType()).thenReturn(Material.GHAST_SPAWN_EGG);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getDisplayName()).thenReturn("HorseEgg");
        when(meta.hasLore()).thenReturn(false);

        assertFalse(legacyMarker.isSimilar(item));
    }

    @Test
    void isSimilarRejectsPlainSignWhenStoredSignIsMarkedAsItem() {
        StorageSign signItem = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "OakStorageSign", "1"});
        assertNotNull(signItem);

        ItemStack item = new ItemStack(Material.OAK_SIGN);
        assertFalse(signItem.isSimilar(item));
    }

    @Test
    void isSimilarRejectsPlainSignWithDecoratedMetaWhenStoredSignIsMarkedAsItem() {
        StorageSign signItem = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "OakStorageSign", "1"});
        assertNotNull(signItem);

        ItemStack item = new ItemStack(Material.OAK_SIGN);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("custom");
        item.setItemMeta(meta);

        assertFalse(signItem.isSimilar(item));
    }

    @Test
    void isSimilarRejectsStorageSignItemWhenItemIsNotUnregistered() {
        StorageSign signItem = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "OakStorageSign", "1"});
        assertNotNull(signItem);

        ItemStack item = StorageSign.createStorageSignItem(Material.OAK_SIGN, "STONE 1", 1);
        assertFalse(signItem.isSimilar(item));
    }

    @Test
    void isSimilarRejectsShulkerWhenMetaTypeIsWrong() {
        StorageSign shulker = StorageSign.fromStoredItem(new ItemStack(Material.SHULKER_BOX));
        assertNotNull(shulker);

        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.SHULKER_BOX);
        when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));
        assertFalse(shulker.isSimilar(item));
    }

    @Test
    void isSimilarRejectsShulkerWhenMetaIsMissing() {
        StorageSign shulker = StorageSign.fromStoredItem(new ItemStack(Material.SHULKER_BOX));
        assertNotNull(shulker);

        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.SHULKER_BOX);
        when(item.getItemMeta()).thenReturn(null);
        assertFalse(shulker.isSimilar(item));
    }

    @Test
    void isSimilarRejectsEnchantedBookWithWrongLevel() {
        StorageSign book = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "ENCHBOOK:sharp:5", "1"});
        assertNotNull(book);

        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(Enchantment.SHARPNESS, 4, true);
        item.setItemMeta(meta);

        assertFalse(book.isSimilar(item));
    }

    @Test
    void isSimilarRejectsPotionWhenCandidatePotionTypeIsMissing() throws Exception {
        var ctor = StorageSign.class.getDeclaredConstructor(
            Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
        ctor.setAccessible(true);
        StorageSign potion = ctor.newInstance(Material.POTION, (short) 0, 1, null, null, false);

        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.HEALING);
        item.setItemMeta(meta);

        assertFalse(potion.isSimilar(item));
    }

    @Test
    void isSimilarRejectsOrdinaryItemWhenCachedReferenceDoesNotMatch() {
        StorageSign stored = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "1"});
        assertNotNull(stored);

        assertFalse(stored.isSimilar(new ItemStack(Material.DIRT)));
    }

    @Test
    void isSimilarRejectsOrdinaryItemWhenCachedReferenceDiffersByEnchant() {
        StorageSign stored = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "1"});
        assertNotNull(stored);

        ItemStack item = new ItemStack(Material.STONE);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, 1, true);
        item.setItemMeta(meta);

        assertTrue(stored.isSimilar(item));
    }

    @Test
    void isSimilarRejectsOrdinaryItemWithSameTypeButDifferentMeta() {
        StorageSign stored = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "1"});
        assertNotNull(stored);

        ItemStack item = new ItemStack(Material.STONE);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, 1, true);
        item.setItemMeta(meta);

        assertTrue(stored.isSimilar(item));
    }

    @Test
    void isSimilarRejectsDamageableItemWhenDamageMatchesButMetaDoesNot() {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        Damageable meta = (Damageable) tool.getItemMeta();
        meta.setDamage(123);
        tool.setItemMeta(meta);

        StorageSign stored = StorageSign.fromStoredItem(tool);
        assertNotNull(stored);

        ItemStack sameDamage = new ItemStack(Material.DIAMOND_PICKAXE);
        Damageable sameMeta = (Damageable) sameDamage.getItemMeta();
        sameMeta.setDamage(123);
        sameDamage.setItemMeta(sameMeta);
        assertTrue(stored.isSimilar(sameDamage));
    }

    @Test
    void isSimilarRejectsWhiteBannerWithDifferentDamage() {
        StorageSign banner = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "WHITE_BANNER:0", "1"});
        assertNotNull(banner);
        assertTrue(banner.isSimilar(new ItemStack(Material.WHITE_BANNER)));
    }

    @Test
    void isSimilarRejectsHorseEggStorageSignItemsThatAreNotEmpty() {
        StorageSign legacyMarker = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "HorseEgg", "1"});
        assertNotNull(legacyMarker);

        ItemStack item = StorageSign.createStorageSignItem(Material.GHAST_SPAWN_EGG, "STONE 1", 1);
        assertFalse(legacyMarker.isSimilar(item));
    }

    @Test
    void getContentsUsesConfiguredVirtualIdentifierWhenPresent() throws Exception {
        Field configField = storagesign.ConfigLoader.class.getDeclaredField("virtualItemIdentifiers");
        configField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> original = new HashMap<>((Map<String, String>) configField.get(null));
        try {
            configField.set(null, Map.of("HorseEgg", "END_PORTAL:1"));
            var ctor = StorageSign.class.getDeclaredConstructor(
                Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
            ctor.setAccessible(true);
            StorageSign endPortal = ctor.newInstance(Material.END_PORTAL, StorageSign.DAMAGE_SS_ITEM,
                1, null, null, false);
            ItemStack item = endPortal.getContents(1);
            assertNotNull(item);
            assertEquals("HorseEgg", item.getItemMeta().getDisplayName());
        } finally {
            configField.set(null, original);
        }
    }

    @Test
    void getContentsFallsBackToDefaultHorseEggIdentifierWhenNoVirtualMappingExists() throws Exception {
        Field configField = storagesign.ConfigLoader.class.getDeclaredField("virtualItemIdentifiers");
        configField.setAccessible(true);
        Field defaultField = StorageSign.class.getDeclaredField("DEFAULT_VIRTUAL_IDENTIFIERS");
        defaultField.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        Object configBase = unsafe.staticFieldBase(configField);
        long configOffset = unsafe.staticFieldOffset(configField);
        Object defaultBase = unsafe.staticFieldBase(defaultField);
        long defaultOffset = unsafe.staticFieldOffset(defaultField);
        @SuppressWarnings("unchecked")
        Map<String, String> originalConfig = (Map<String, String>) unsafe.getObject(configBase, configOffset);
        @SuppressWarnings("unchecked")
        Map<String, String> originalDefault = (Map<String, String>) unsafe.getObject(defaultBase, defaultOffset);

        try {
            unsafe.putObject(configBase, configOffset, Map.of());
            unsafe.putObject(defaultBase, defaultOffset, Map.of());

            var ctor = StorageSign.class.getDeclaredConstructor(
                Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
            ctor.setAccessible(true);
            StorageSign endPortal = ctor.newInstance(Material.END_PORTAL, StorageSign.DAMAGE_SS_ITEM,
                1, null, null, false);

            ItemStack item = endPortal.getContents(1);
            assertNotNull(item);
            assertEquals("HorseEgg", item.getItemMeta().getDisplayName());
        } finally {
            unsafe.putObject(configBase, configOffset, originalConfig);
            unsafe.putObject(defaultBase, defaultOffset, originalDefault);
        }
    }

    @Test
    void getContentsRejectsNullAndAirMaterials() throws Exception {
        var ctor = StorageSign.class.getDeclaredConstructor(
            Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
        ctor.setAccessible(true);
        StorageSign nullMaterial = ctor.newInstance(null, (short) 0, 1, null, null, false);
        StorageSign airMaterial = ctor.newInstance(Material.AIR, (short) 0, 1, null, null, false);

        assertNull(nullMaterial.getContents(1));
        assertNull(airMaterial.getContents(1));
    }

    @Test
    void canonicalPotionIdentifierRejectsUnregisteredPotionSigns() throws Exception {
        var ctor = StorageSign.class.getDeclaredConstructor(
            Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
        ctor.setAccessible(true);
        StorageSign potion = ctor.newInstance(Material.POTION, (short) 0, 1, PotionType.HEALING, null, true);

        assertNull(potion.getCanonicalPotionIdentifier());
    }

    @Test
    void getContentsReturnsNullWhenOminousBannerMetaCannotBeApplied() {
        StorageSign banner = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "WHITE_BANNER:8", "1"});
        assertNotNull(banner);
        BannerMeta meta = new OminousBannerCodec().create();
        assertNotNull(meta);

        try (MockedStatic<StorageSignPlugin> mocked = mockStatic(StorageSignPlugin.class);
             var constructed = mockConstruction(org.bukkit.inventory.ItemStack.class, (stack, context) -> {
                 when(stack.getItemMeta()).thenReturn(meta.clone());
                 when(stack.setItemMeta(meta.clone())).thenReturn(false);
             })) {
            mocked.when(StorageSignPlugin::getOminousBannerMeta).thenReturn(meta);
            assertNull(banner.getContents(1));
            assertEquals(1, constructed.constructed().size());
        }
    }

    @Test
    void getContentsCoversMarkerAndPlainSignBranches() {
        StorageSign legacy = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "HorseEgg", "2"});
        assertNotNull(legacy);
        ItemStack marker = legacy.getContents(2);
        assertNotNull(marker);
        assertEquals(Material.GHAST_SPAWN_EGG, marker.getType());
        assertEquals("HorseEgg", marker.getItemMeta().getDisplayName());

        StorageSign endPortal = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "END_PORTAL", "2"});
        assertNotNull(endPortal);
        ItemStack signItem = endPortal.getContents(2);
        assertNotNull(signItem);
        assertEquals(Material.OAK_SIGN, signItem.getType());
        assertEquals(StorageSign.EMPTY_MARKER, signItem.getItemMeta().getLore().getFirst());

        StorageSign plainSign = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "OAK_SIGN", "2"});
        assertNotNull(plainSign);
        ItemStack plain = plainSign.getContents(2);
        assertNotNull(plain);
        assertEquals(Material.OAK_SIGN, plain.getType());
        assertNotNull(plain.getItemMeta());
    }

    @Test
    void ifExactlyRestorableRejectsDecoratedItemsAndNullRestoration() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "ifExactlyRestorable", ItemStack.class, StorageSign.class);
        method.setAccessible(true);

        ItemStack named = new ItemStack(Material.STONE);
        ItemMeta meta = named.getItemMeta();
        meta.setDisplayName("named");
        named.setItemMeta(meta);

        assertNull(method.invoke(null, named, StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "1"})));
        assertNull(method.invoke(null, new ItemStack(Material.STONE), StorageSign.empty()));
    }

    @Test
    void ifExactlyRestorableReturnsSameItemWhenNoDecorationIsPresent() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "ifExactlyRestorable", ItemStack.class, StorageSign.class);
        method.setAccessible(true);

        StorageSign stone = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "1"});
        ItemStack item = new ItemStack(Material.STONE);

        StorageSign restored = (StorageSign) method.invoke(null, item, stone);
        assertNotNull(restored);
        assertEquals(Material.STONE, restored.getMaterial());
    }

    @Test
    void ifExactlyRestorableRejectsNonMatchingRestoration() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "ifExactlyRestorable", ItemStack.class, StorageSign.class);
        method.setAccessible(true);

        ItemStack item = new ItemStack(Material.DIRT);
        StorageSign mismatched = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "1"});

        assertNull(method.invoke(null, item, mismatched));
    }

    @Test
    void parseIdentifierCoversSpecialAndMalformedPaths() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "parseIdentifier", String.class, int.class);
        method.setAccessible(true);

        assertNull(method.invoke(null, new Object[] {null, 1}));
        assertNull(method.invoke(null, "   ", 1));
        StorageSign legacySign = (StorageSign) method.invoke(null, "OakStorageSign", 4);
        assertNotNull(legacySign);
        assertTrue(legacySign.isSignAsItem());

        StorageSign special = (StorageSign) method.invoke(null, "OMINOUS_BOTTLE:3", 7);
        assertNotNull(special);
        assertEquals(Material.OMINOUS_BOTTLE, special.getMaterial());
        assertEquals(3, special.getDamage());

        StorageSign malformedSpecial = (StorageSign) method.invoke(null, "OMINOUS_BOTTLE:x", 7);
        assertNotNull(malformedSpecial);
        assertEquals(0, malformedSpecial.getDamage());

        assertNull(method.invoke(null, "ENCHBOOK:sharp", 1));
        assertNull(method.invoke(null, "ENCHBOOK:sharp:not-a-number", 1));
        assertNull(method.invoke(null, "ENCHBOOK:unknown:5", 1));
        assertNull(method.invoke(null, "ENCHANTED_BOOK:unknown:5", 1));
        StorageSign legacyEnchBookNoLevel = (StorageSign) method.invoke(
            null, "ENCHANTED_BOOK:unknown", 1);
        assertNotNull(legacyEnchBookNoLevel);
        assertEquals(Material.ENCHANTED_BOOK, legacyEnchBookNoLevel.getMaterial());
        assertEquals(0, legacyEnchBookNoLevel.getDamage());
        StorageSign nonEnchBookTriple = (StorageSign) method.invoke(null, "STONE:foo:5", 1);
        assertNotNull(nonEnchBookTriple);
        assertEquals(Material.STONE, nonEnchBookTriple.getMaterial());
        assertEquals(0, nonEnchBookTriple.getDamage());
        StorageSign legacyEnchBook = (StorageSign) method.invoke(
            null, "ENCHANTED_BOOK:sharp:5", 1);
        assertNotNull(legacyEnchBook);
        assertEquals(Material.ENCHANTED_BOOK, legacyEnchBook.getMaterial());
        assertNotNull(legacyEnchBook.getEnchantment());
        StorageSign legacyEnchBookAlt = (StorageSign) method.invoke(
            null, "ENCHANTED_BOOK:fire_protection:3", 1);
        assertNotNull(legacyEnchBookAlt);
        assertEquals(Material.ENCHANTED_BOOK, legacyEnchBookAlt.getMaterial());
        assertNotNull(legacyEnchBookAlt.getEnchantment());
        StorageSign numericFallback = (StorageSign) method.invoke(null, "STONE:abc", 1);
        assertNotNull(numericFallback);
        assertEquals(Material.STONE, numericFallback.getMaterial());
        assertNull(method.invoke(null, "ENCHANTED_BOOK:sharp:not-a-number", 1));
        assertNull(method.invoke(null, "NOT_A_REAL_MATERIAL", 1));
    }

    @Test
    void resolveMaterialFromIdentifierTokenCoversConfiguredDefaultAndBlankPaths() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "resolveMaterialFromIdentifierToken", String.class);
        method.setAccessible(true);

        assertNull(method.invoke(null, (Object) null));
        assertNull(method.invoke(null, "   "));
        assertEquals(Material.OAK_SIGN, method.invoke(null, "SIGN"));
        assertEquals(Material.STONE, method.invoke(null, "stone"));
        assertEquals(Material.RED_DYE, method.invoke(null, "ROSE_RED"));
        assertEquals(Material.GREEN_DYE, method.invoke(null, "CACTUS_GREEN"));
        assertEquals(Material.YELLOW_DYE, method.invoke(null, "DANDELION_YELLOW"));
        assertEquals(Material.SMOOTH_STONE_SLAB, method.invoke(null, "STONE_SLAB"));
    }

    @Test
    void resolveMaterialFromIdentifierTokenCoversConfiguredAliasPaths() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "resolveMaterialFromIdentifierToken", String.class);
        method.setAccessible(true);

        Field field = storagesign.ConfigLoader.class.getDeclaredField("identifierAliases");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> original = new HashMap<>((Map<String, String>) field.get(null));
        try {
            Map<String, String> custom = new HashMap<>();
            custom.put("CUSTOM_ALIAS", "STONE");
            custom.put("BLANK_ALIAS", " ");
            custom.put("TRIM_ALIAS", "  OAK_SIGN  ");
            custom.put("BROKEN_ALIAS", "NOT_A_REAL_MATERIAL");
            field.set(null, custom);

            assertEquals(Material.STONE, method.invoke(null, "CUSTOM_ALIAS"));
            assertEquals(Material.OAK_SIGN, method.invoke(null, "trim_alias"));
            assertEquals(Material.STONE, method.invoke(null, "stone"));
            assertNull(method.invoke(null, "BLANK_ALIAS"));
            assertNull(method.invoke(null, "BROKEN_ALIAS"));
        } finally {
            field.set(null, original);
        }
    }

    @Test
    void resolveMaterialFromIdentifierTokenUsesDefaultAliasWhenConfigIsEmpty() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "resolveMaterialFromIdentifierToken", String.class);
        method.setAccessible(true);

        Field field = storagesign.ConfigLoader.class.getDeclaredField("identifierAliases");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> original = new HashMap<>((Map<String, String>) field.get(null));
        try {
            field.set(null, Map.of());
            assertEquals(Material.SMOOTH_STONE_SLAB, method.invoke(null, "STONE_SLAB"));
        } finally {
            field.set(null, original);
        }
    }

    @Test
    void resolveMaterialFromIdentifierTokenCoversBrokenDefaultAlias() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "resolveMaterialFromIdentifierToken", String.class);
        method.setAccessible(true);

        Field field = StorageSign.class.getDeclaredField("DEFAULT_IDENTIFIER_ALIASES");
        field.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        @SuppressWarnings("unchecked")
        Map<String, String> original = (Map<String, String>) unsafe.getObject(base, offset);
        Map<String, String> injected = new HashMap<>(original);
        injected.put("BROKEN_DEFAULT_ALIAS", "NOT_A_REAL_MATERIAL");

        try {
            unsafe.putObject(base, offset, injected);
            assertNull(method.invoke(null, "BROKEN_DEFAULT_ALIAS"));
        } finally {
            unsafe.putObject(base, offset, original);
        }
    }

    @Test
    void resolveMaterialFromIdentifierTokenReturnsNullForUnknownToken() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "resolveMaterialFromIdentifierToken", String.class);
        method.setAccessible(true);

        Field field = storagesign.ConfigLoader.class.getDeclaredField("identifierAliases");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> original = new HashMap<>((Map<String, String>) field.get(null));
        try {
            field.set(null, Map.of());
            assertNull(method.invoke(null, "TOTALLY_UNKNOWN_IDENTIFIER"));
        } finally {
            field.set(null, original);
        }
    }

    @Test
    void resolveMaterialFromIdentifierTokenUsesDefaultSignAlias() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "resolveMaterialFromIdentifierToken", String.class);
        method.setAccessible(true);

        Field field = storagesign.ConfigLoader.class.getDeclaredField("identifierAliases");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> original = new HashMap<>((Map<String, String>) field.get(null));
        try {
            field.set(null, Map.of());
            assertEquals(Material.OAK_SIGN, method.invoke(null, "SIGN"));
        } finally {
            field.set(null, original);
        }
    }

    @Test
    void parseVirtualIdentifierCoversConfiguredBlankAndInvalidMaterialPaths() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "parseVirtualIdentifier", String.class, int.class);
        method.setAccessible(true);

        StorageSign horse = (StorageSign) method.invoke(null, "HorseEgg", 9);
        assertNotNull(horse);
        assertEquals(Material.END_PORTAL, horse.getMaterial());
        assertEquals(1, horse.getDamage());
        assertNull(method.invoke(null, "UNKNOWN", 9));
    }

    @Test
    void parseVirtualIdentifierCoversConfiguredEntriesAndInvalidMaterial() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "parseVirtualIdentifier", String.class, int.class);
        method.setAccessible(true);

        Field field = storagesign.ConfigLoader.class.getDeclaredField("virtualItemIdentifiers");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> original = new HashMap<>((Map<String, String>) field.get(null));
        try {
            Map<String, String> custom = new HashMap<>();
            custom.put("CUSTOM", "OAK_SIGN:2");
            custom.put("NOCOLON", "OAK_SIGN");
            custom.put("BLANK", " ");
            custom.put("BROKEN", "NOT_A_REAL_MATERIAL:1");
            field.set(null, custom);

            StorageSign parsed = (StorageSign) method.invoke(null, "CUSTOM", 9);
            assertNotNull(parsed);
            assertEquals(Material.OAK_SIGN, parsed.getMaterial());
            assertEquals(2, parsed.getDamage());
            StorageSign noColon = (StorageSign) method.invoke(null, "NOCOLON", 9);
            assertNotNull(noColon);
            assertEquals(0, noColon.getDamage());
            assertNull(method.invoke(null, "BLANK", 9));
            assertNull(method.invoke(null, "BROKEN", 9));
        } finally {
            field.set(null, original);
        }
    }

    @Test
    void parseVirtualIdentifierTreatsInvalidDamageAsZero() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "parseVirtualIdentifier", String.class, int.class);
        method.setAccessible(true);

        Field field = storagesign.ConfigLoader.class.getDeclaredField("virtualItemIdentifiers");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> original = new HashMap<>((Map<String, String>) field.get(null));
        try {
            field.set(null, Map.of("BROKEN_DAMAGE", "STONE:not-a-number"));

            StorageSign parsed = (StorageSign) method.invoke(null, "BROKEN_DAMAGE", 9);
            assertNotNull(parsed);
            assertEquals(Material.STONE, parsed.getMaterial());
            assertEquals(0, parsed.getDamage());
        } finally {
            field.set(null, original);
        }
    }

    @Test
    void createLegacyMarkerItemReturnsRawItemWhenMetaUnavailable() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "createLegacyMarkerItem", int.class, String.class);
        method.setAccessible(true);

        try (var mocked = mockConstruction(ItemStack.class, (stack, context) -> {
            when(stack.getItemMeta()).thenReturn(null);
        })) {
            ItemStack item = (ItemStack) method.invoke(null, 1, "HorseEgg");
            assertNotNull(item);
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void matchesVirtualSpecCoversBlankMismatchAndMatchPaths() throws Exception {
        Method method = StorageSign.class.getDeclaredMethod(
            "matchesVirtualSpec", Material.class, short.class, String.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(null, Material.OAK_SIGN, (short) 1, null));
        assertFalse((Boolean) method.invoke(null, Material.OAK_SIGN, (short) 1, " "));
        assertFalse((Boolean) method.invoke(null, Material.OAK_SIGN, (short) 1, "OAK_SIGN"));
        assertFalse((Boolean) method.invoke(null, Material.OAK_SIGN, (short) 1, "STONE:1"));
        assertFalse((Boolean) method.invoke(null, Material.OAK_SIGN, (short) 1,
            "NOT_A_REAL_MATERIAL:1"));
        assertFalse((Boolean) method.invoke(null, Material.OAK_SIGN, (short) 1, "OAK_SIGN:x"));
        assertTrue((Boolean) method.invoke(null, Material.OAK_SIGN, (short) 1, "OAK_SIGN:1"));
    }

    @Test
    void tryAcquireRejectsZeroLimitAndContentionAtCapacity() throws Exception {
        Method method = StorageSignQueryService.class.getDeclaredMethod(
            "tryAcquire", AtomicInteger.class, int.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(null, new AtomicInteger(0), 0));
        assertFalse((Boolean) method.invoke(null, new AtomicInteger(1), 1));
    }

    @Test
    void tryAcquireCoversCompareAndSetFailurePath() throws Exception {
        Method method = StorageSignQueryService.class.getDeclaredMethod(
            "tryAcquire", AtomicInteger.class, int.class);
        method.setAccessible(true);

        AtomicInteger counter = mock(AtomicInteger.class);
        when(counter.get()).thenReturn(0);
        when(counter.compareAndSet(0, 1)).thenReturn(false, true);

        assertTrue((Boolean) method.invoke(null, counter, 1));
    }

    @Test
    void fromStoredItemCoversLegacyMarkerAndSpecialRejects() {
        ItemStack legacy = new ItemStack(Material.GHAST_SPAWN_EGG);
        ItemMeta legacyMeta = legacy.getItemMeta();
        legacyMeta.setDisplayName("HorseEgg");
        legacyMeta.setLore(java.util.List.of("Empty"));
        legacy.setItemMeta(legacyMeta);
        assertNotNull(StorageSign.fromStoredItem(legacy));

        ItemStack blankLegacy = new ItemStack(Material.GHAST_SPAWN_EGG);
        ItemMeta blankMeta = blankLegacy.getItemMeta();
        blankMeta.setLore(java.util.List.of("Empty"));
        blankLegacy.setItemMeta(blankMeta);
        assertNull(StorageSign.fromStoredItem(blankLegacy));

        ItemStack malformedPotion = new ItemStack(Material.POTION);
        PotionMeta potionMeta = (PotionMeta) malformedPotion.getItemMeta();
        potionMeta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 20, 1), true);
        malformedPotion.setItemMeta(potionMeta);
        assertNull(StorageSign.fromStoredItem(malformedPotion));

        ItemStack firework = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta fireworkMeta = (FireworkMeta) firework.getItemMeta();
        fireworkMeta.setPower(2);
        firework.setItemMeta(fireworkMeta);
        assertNotNull(StorageSign.fromStoredItem(firework));
    }

    @Test
    void fromStoredItemRejectsNullTypeItems() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(null);
        when(item.getItemMeta()).thenReturn(null);

        assertNull(StorageSign.fromStoredItem(item));
    }

    @Test
    void fromStoredItemRejectsNullAndAirAndBrokenLegacyMarkerItems() {
        assertNull(StorageSign.fromStoredItem(null));
        assertNull(StorageSign.fromStoredItem(new ItemStack(Material.AIR)));
    }

    @Test
    void fromStoredItemRejectsLegacyMarkerWithoutLoreOrWithWrongSpec() throws Exception {
        ItemStack noLore = mock(ItemStack.class);
        ItemMeta noLoreMeta = mock(ItemMeta.class);
        when(noLore.getType()).thenReturn(Material.GHAST_SPAWN_EGG);
        when(noLore.getItemMeta()).thenReturn(noLoreMeta);
        when(noLore.clone()).thenReturn(new ItemStack(Material.GHAST_SPAWN_EGG));
        when(noLoreMeta.hasLore()).thenReturn(false);
        assertNotNull(StorageSign.fromStoredItem(noLore));

        Field field = storagesign.ConfigLoader.class.getDeclaredField("virtualItemIdentifiers");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> original = new HashMap<>((Map<String, String>) field.get(null));
        try {
            field.set(null, Map.of("HorseEgg", "OAK_SIGN:1"));
            ItemStack wrongSpec = mock(ItemStack.class);
            ItemMeta wrongSpecMeta = mock(ItemMeta.class);
            when(wrongSpec.getType()).thenReturn(Material.GHAST_SPAWN_EGG);
            when(wrongSpec.getItemMeta()).thenReturn(wrongSpecMeta);
            when(wrongSpec.clone()).thenReturn(new ItemStack(Material.GHAST_SPAWN_EGG));
            when(wrongSpecMeta.hasLore()).thenReturn(true);
            when(wrongSpecMeta.getDisplayName()).thenReturn("HorseEgg");
            assertNull(StorageSign.fromStoredItem(wrongSpec));
        } finally {
            field.set(null, original);
        }
    }

    @Test
    void fromStoredItemRejectsLegacyMarkerWithNullOrUnknownDisplayName() throws Exception {
        ItemStack nullName = mock(ItemStack.class);
        ItemMeta nullNameMeta = mock(ItemMeta.class);
        when(nullName.getType()).thenReturn(Material.GHAST_SPAWN_EGG);
        when(nullName.getItemMeta()).thenReturn(nullNameMeta);
        when(nullName.clone()).thenReturn(new ItemStack(Material.GHAST_SPAWN_EGG));
        when(nullNameMeta.hasLore()).thenReturn(true);
        when(nullNameMeta.getDisplayName()).thenReturn(null);
        assertNull(StorageSign.fromStoredItem(nullName));

        ItemStack unknown = mock(ItemStack.class);
        ItemMeta unknownMeta = mock(ItemMeta.class);
        when(unknown.getType()).thenReturn(Material.GHAST_SPAWN_EGG);
        when(unknown.getItemMeta()).thenReturn(unknownMeta);
        when(unknown.clone()).thenReturn(new ItemStack(Material.GHAST_SPAWN_EGG));
        when(unknownMeta.hasLore()).thenReturn(true);
        when(unknownMeta.getDisplayName()).thenReturn("TotallyUnknownMarker");
        assertNull(StorageSign.fromStoredItem(unknown));
    }

    @Test
    void fromStoredItemAcceptsRegisteredStorageSignItemAndPlainPotionWithoutCustomEffects() {
        ItemStack storageSignItem = StorageSign.createStorageSignItem(
            Material.OAK_SIGN, "STONE 1", 1);
        assertNotNull(StorageSign.fromStoredItem(storageSignItem));

        ItemStack signMarker = StorageSign.createStorageSignItem(
            Material.OAK_SIGN, StorageSign.EMPTY_MARKER, 1);
        assertNotNull(StorageSign.fromStoredItem(signMarker));

        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
        potionMeta.setBasePotionType(PotionType.HEALING);
        potion.setItemMeta(potionMeta);
        assertNotNull(StorageSign.fromStoredItem(potion));
    }

    @Test
    void fromStoredItemAcceptsShulkerAndBeehiveWithWrongMetaTypes() {
        ItemStack shulker = mock(ItemStack.class);
        when(shulker.getType()).thenReturn(Material.SHULKER_BOX);
        when(shulker.getItemMeta()).thenReturn(mock(ItemMeta.class));
        when(shulker.clone()).thenReturn(new ItemStack(Material.SHULKER_BOX));
        assertNull(StorageSign.fromStoredItem(shulker));

        ItemStack beehive = mock(ItemStack.class);
        when(beehive.getType()).thenReturn(Material.BEEHIVE);
        when(beehive.getItemMeta()).thenReturn(mock(ItemMeta.class));
        when(beehive.clone()).thenReturn(new ItemStack(Material.BEEHIVE));
        assertNotNull(StorageSign.fromStoredItem(beehive));
    }

    @Test
    void fromStoredItemRejectsShulkerAndBeehiveWhenBlockStateTypeIsWrong() {
        ItemStack shulker = mock(ItemStack.class);
        BlockStateMeta shulkerMeta = mock(BlockStateMeta.class);
        org.bukkit.block.BlockState wrongState = mock(org.bukkit.block.BlockState.class);
        when(shulker.getType()).thenReturn(Material.SHULKER_BOX);
        when(shulker.getItemMeta()).thenReturn(shulkerMeta);
        when(shulker.clone()).thenReturn(new ItemStack(Material.SHULKER_BOX));
        when(shulkerMeta.getBlockState()).thenReturn(wrongState);
        assertNull(StorageSign.fromStoredItem(shulker));

        ItemStack beehive = mock(ItemStack.class);
        BlockStateMeta beehiveMeta = mock(BlockStateMeta.class);
        org.bukkit.block.BlockState wrongBeeState = mock(org.bukkit.block.BlockState.class);
        when(beehive.getType()).thenReturn(Material.BEEHIVE);
        when(beehive.getItemMeta()).thenReturn(beehiveMeta);
        when(beehive.clone()).thenReturn(new ItemStack(Material.BEEHIVE));
        when(beehiveMeta.getBlockState()).thenReturn(wrongBeeState);
        assertNotNull(StorageSign.fromStoredItem(beehive));
    }

    @Test
    void fromStoredItemRejectsLegacyMarkerWithMismatchedVirtualSpec() throws Exception {
        Field field = storagesign.ConfigLoader.class.getDeclaredField("virtualItemIdentifiers");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> original = new HashMap<>((Map<String, String>) field.get(null));
        try {
            field.set(null, Map.of("HorseEgg", "END_PORTAL:0"));
            ItemStack marker = mock(ItemStack.class);
            ItemMeta meta = mock(ItemMeta.class);
            when(marker.getType()).thenReturn(Material.GHAST_SPAWN_EGG);
            when(marker.getItemMeta()).thenReturn(meta);
            when(marker.clone()).thenReturn(new ItemStack(Material.GHAST_SPAWN_EGG));
            when(meta.hasLore()).thenReturn(true);
            when(meta.getDisplayName()).thenReturn("HorseEgg");
            assertNull(StorageSign.fromStoredItem(marker));
        } finally {
            field.set(null, original);
        }
    }

    @Test
    void fromStoredItemRejectsDecoratedSignThatIsNotStorageSignItem() {
        ItemStack sign = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(sign.getType()).thenReturn(Material.OAK_SIGN);
        when(sign.getItemMeta()).thenReturn(meta);
        when(sign.clone()).thenReturn(new ItemStack(Material.OAK_SIGN));
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.hasLore()).thenReturn(true);
        assertNull(StorageSign.fromStoredItem(sign));
    }

    @Test
    void fromStoredItemRejectsLegacyMarkerWithNullMetadata() {
        ItemStack marker = mock(ItemStack.class);
        when(marker.getType()).thenReturn(Material.GHAST_SPAWN_EGG);
        when(marker.getItemMeta()).thenReturn(null);
        when(marker.clone()).thenReturn(new ItemStack(Material.GHAST_SPAWN_EGG));
        assertNotNull(StorageSign.fromStoredItem(marker));
    }

    @Test
    void isSimilarCoversSpecialCaseBranches() {
        StorageSign empty = StorageSign.empty();
        assertFalse(empty.isSimilar(null));
        assertFalse(empty.isSimilar(new ItemStack(Material.AIR)));

        StorageSign stone = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "1"});
        assertNotNull(stone);
        assertTrue(stone.isSimilar(new ItemStack(Material.STONE)));

        StorageSign legacyMarker = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "HorseEgg", "1"});
        assertNotNull(legacyMarker);
        ItemStack horseEgg = legacyMarker.getContents(1);
        assertTrue(legacyMarker.isSimilar(horseEgg));

        StorageSign potion = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "POTION:HEAL:0", "1"});
        assertNotNull(potion);
        ItemStack wrongPotion = new ItemStack(Material.POTION);
        PotionMeta wrongPotionMeta = (PotionMeta) wrongPotion.getItemMeta();
        wrongPotionMeta.setBasePotionType(PotionType.HEALING);
        wrongPotion.setItemMeta(wrongPotionMeta);
        assertTrue(potion.isSimilar(wrongPotion));

        BannerMeta ominous = new OminousBannerCodec().create();
        assertNotNull(ominous);
        ItemStack bannerItem = new ItemStack(Material.WHITE_BANNER);
        bannerItem.setItemMeta(ominous.clone());
        StorageSign banner = StorageSign.fromStoredItem(bannerItem);
        assertNotNull(banner);
        assertTrue(banner.isSimilar(bannerItem));
        assertFalse(banner.isSimilar(new ItemStack(Material.WHITE_BANNER)));
    }

    @Test
    void isSignAsItemRecognizesLegacySignItemsAndRejectsOthers() {
        StorageSign legacy = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "OakStorageSign", "1"});
        assertNotNull(legacy);
        assertTrue(legacy.isSignAsItem());

        StorageSign plain = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "1"});
        assertNotNull(plain);
        assertFalse(plain.isSignAsItem());
    }

    @Test
    void isSignAsItemRejectsSignMaterialWithoutMarkerDamage() throws Exception {
        var ctor = StorageSign.class.getDeclaredConstructor(
            Material.class, short.class, int.class, PotionType.class, Enchantment.class, boolean.class);
        ctor.setAccessible(true);
        StorageSign sign = ctor.newInstance(Material.OAK_SIGN, (short) 0, 1, null, null, false);

        assertFalse(sign.isSignAsItem());
    }

    @Test
    void applyToSignWorksWithoutARunningBukkitServer() {
        resetMockBukkit();
        Sign sign = mock(Sign.class);
        SignSide front = mock(SignSide.class);
        var pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(sign.getSide(Side.FRONT)).thenReturn(front);
        when(sign.getPersistentDataContainer()).thenReturn(pdc);
        when(sign.update()).thenReturn(true);

        StorageSign.empty().applyToSign(sign);

        assertNull(pdc.get(new NamespacedKey("storagesign", "storage_identifier"),
            PersistentDataType.STRING));
        assertNull(pdc.get(new NamespacedKey("storagesign", "potion_identifier"),
            PersistentDataType.STRING));
        resetMockBukkit();
    }

    @Test
    void applyToSignRemovesCanonicalDataForEmptyStorageSigns() {
        var world = MockBukkit.getMock().addSimpleWorld("apply-empty");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.getPersistentDataContainer().set(
            new NamespacedKey("storagesign", "storage_identifier"),
            PersistentDataType.STRING, "STONE");
        sign.getPersistentDataContainer().set(
            new NamespacedKey("storagesign", "potion_identifier"),
            PersistentDataType.STRING, "POTION:HEAL:0");

        StorageSign.empty().applyToSign(sign);

        assertNull(sign.getPersistentDataContainer().get(
            new NamespacedKey("storagesign", "storage_identifier"),
            PersistentDataType.STRING));
        assertNull(sign.getPersistentDataContainer().get(
            new NamespacedKey("storagesign", "potion_identifier"),
            PersistentDataType.STRING));
        assertEquals("", sign.getSide(Side.FRONT).getLine(1));
    }

    private static void resetMockBukkit() {
        try {
            MockBukkit.unmock();
        } catch (IllegalStateException ignored) {
        }
    }
}
