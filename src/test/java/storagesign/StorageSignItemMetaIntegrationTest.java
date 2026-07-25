package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockConstruction;

import java.util.List;
import java.lang.reflect.Field;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Beehive;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.FireworkEffect;
import org.bukkit.Color;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import storagesign.item.OminousBottleHelper;

@Tag("integration")
class StorageSignItemMetaIntegrationTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        MockBukkit.load(StorageSignPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rejectsMetadataThatCannotBeRestoredExactly() {
        ItemStack named = new ItemStack(Material.STONE);
        ItemMeta meta = named.getItemMeta();
        meta.setDisplayName("important");
        named.setItemMeta(meta);

        assertNull(StorageSign.fromStoredItem(named));

        ItemStack flagged = new ItemStack(Material.STONE);
        ItemMeta flaggedMeta = flagged.getItemMeta();
        flaggedMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        flagged.setItemMeta(flaggedMeta);
        assertNull(StorageSign.fromStoredItem(flagged));

        ItemStack enchanted = new ItemStack(Material.STONE);
        ItemMeta enchantedMeta = enchanted.getItemMeta();
        enchantedMeta.addEnchant(Enchantment.SHARPNESS, 1, true);
        enchanted.setItemMeta(enchantedMeta);
        assertNull(StorageSign.fromStoredItem(enchanted));

        ItemStack lore = new ItemStack(Material.STONE);
        ItemMeta loreMeta = lore.getItemMeta();
        loreMeta.setLore(List.of("important"));
        lore.setItemMeta(loreMeta);
        assertNull(StorageSign.fromStoredItem(lore));
    }

    @Test
    void acceptsDefaultItemAndPreservesDamage() {
        assertNotNull(StorageSign.fromStoredItem(new ItemStack(Material.STONE)));

        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        Damageable meta = (Damageable) tool.getItemMeta();
        meta.setDamage(123);
        tool.setItemMeta(meta);

        StorageSign stored = StorageSign.fromStoredItem(tool);
        assertNotNull(stored);
        ItemStack restored = stored.getContents(1);
        assertEquals(123, ((Damageable) restored.getItemMeta()).getDamage());
        Damageable differentMeta = (Damageable) tool.getItemMeta();
        differentMeta.setDamage(124);
        tool.setItemMeta(differentMeta);
        assertFalse(stored.isSimilar(tool));
    }

    @Test
    void acceptsEmptyShulkerButRejectsShulkerContents() {
        ItemStack empty = new ItemStack(Material.SHULKER_BOX);
        assertNotNull(StorageSign.fromStoredItem(empty));

        ItemStack filled = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) filled.getItemMeta();
        ShulkerBox box = (ShulkerBox) meta.getBlockState();
        box.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        meta.setBlockState(box);
        filled.setItemMeta(meta);

        assertNull(StorageSign.fromStoredItem(filled));
    }

    @Test
    void acceptsEmptyBeehiveButRejectsBeeEntityData() {
        assertNotNull(StorageSign.fromStoredItem(new ItemStack(Material.BEEHIVE)));

        ItemStack occupied = mock(ItemStack.class);
        BlockStateMeta meta = mock(BlockStateMeta.class);
        Beehive beehive = mock(Beehive.class);
        when(occupied.getType()).thenReturn(Material.BEEHIVE);
        when(occupied.getItemMeta()).thenReturn(meta);
        when(meta.getBlockState()).thenReturn(beehive);
        when(beehive.getEntityCount()).thenReturn(1);

        assertNull(StorageSign.fromStoredItem(occupied));
    }

    @Test
    void rejectsPlainWhiteBannerAndParsesLegacyHorseEggMarkerItem() {
        StorageSign whiteBanner = StorageSign.fromStoredItem(new ItemStack(Material.WHITE_BANNER));
        assertNotNull(whiteBanner);
        assertEquals(Material.WHITE_BANNER, whiteBanner.getMaterial());

        ItemStack marker = new ItemStack(Material.GHAST_SPAWN_EGG);
        ItemMeta meta = marker.getItemMeta();
        meta.setDisplayName("HorseEgg");
        meta.setLore(List.of("Empty"));
        marker.setItemMeta(meta);

        StorageSign parsed = StorageSign.fromStoredItem(marker);
        assertNotNull(parsed);
        assertEquals(Material.END_PORTAL, parsed.getMaterial());
        assertEquals(1, parsed.getDamage());
    }

    @Test
    void rejectsDecoratedWhiteBannerAndBlankLegacyMarkerName() {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        meta.addPattern(new org.bukkit.block.banner.Pattern(
            org.bukkit.DyeColor.BLACK,
            org.bukkit.Registry.BANNER_PATTERN.get(
                org.bukkit.NamespacedKey.minecraft("border"))));
        banner.setItemMeta(meta);
        assertNotNull(StorageSign.fromStoredItem(banner));

        ItemStack marker = new ItemStack(Material.GHAST_SPAWN_EGG);
        ItemMeta markerMeta = marker.getItemMeta();
        markerMeta.setDisplayName(" ");
        markerMeta.setLore(List.of("Empty"));
        marker.setItemMeta(markerMeta);
        assertNull(StorageSign.fromStoredItem(marker));
    }

    @Test
    void rejectsWhiteBannerAndShulkerWhenMetaIsMissing() {
        ItemStack banner = mock(ItemStack.class);
        when(banner.getType()).thenReturn(Material.WHITE_BANNER);
        when(banner.getItemMeta()).thenReturn(null);
        assertNull(StorageSign.fromStoredItem(banner));

        ItemStack shulker = mock(ItemStack.class);
        when(shulker.getType()).thenReturn(Material.SHULKER_BOX);
        when(shulker.getItemMeta()).thenReturn(null);
        assertNull(StorageSign.fromStoredItem(shulker));
    }

    @Test
    void rejectsWhiteBannerAndShulkerWhenMetaTypeIsWrong() {
        ItemStack banner = mock(ItemStack.class);
        when(banner.getType()).thenReturn(Material.WHITE_BANNER);
        when(banner.getItemMeta()).thenReturn(mock(ItemMeta.class));
        assertNull(StorageSign.fromStoredItem(banner));

        ItemStack shulker = mock(ItemStack.class);
        when(shulker.getType()).thenReturn(Material.SHULKER_BOX);
        when(shulker.getItemMeta()).thenReturn(mock(ItemMeta.class));
        assertNull(StorageSign.fromStoredItem(shulker));
    }

    @Test
    void rejectsPotionBookAndRocketWhenMetaIsMissing() {
        ItemStack potion = mock(ItemStack.class);
        when(potion.getType()).thenReturn(Material.POTION);
        when(potion.getItemMeta()).thenReturn(null);
        when(potion.clone()).thenReturn(new ItemStack(Material.POTION));
        assertNotNull(StorageSign.fromStoredItem(potion));

        ItemStack book = mock(ItemStack.class);
        when(book.getType()).thenReturn(Material.ENCHANTED_BOOK);
        when(book.getItemMeta()).thenReturn(null);
        when(book.clone()).thenReturn(new ItemStack(Material.ENCHANTED_BOOK));
        assertNotNull(StorageSign.fromStoredItem(book));

        ItemStack rocket = mock(ItemStack.class);
        when(rocket.getType()).thenReturn(Material.FIREWORK_ROCKET);
        when(rocket.getItemMeta()).thenReturn(null);
        when(rocket.clone()).thenReturn(new ItemStack(Material.FIREWORK_ROCKET));
        assertNotNull(StorageSign.fromStoredItem(rocket));
    }

    @Test
    void fromStoredItemRejectsOccupiedBeehive() {
        ItemStack occupied = mock(ItemStack.class);
        BlockStateMeta meta = mock(BlockStateMeta.class);
        Beehive beehive = mock(Beehive.class);
        when(occupied.getType()).thenReturn(Material.BEEHIVE);
        when(occupied.getItemMeta()).thenReturn(meta);
        when(meta.getBlockState()).thenReturn(beehive);
        when(beehive.getEntityCount()).thenReturn(2);
        when(occupied.clone()).thenReturn(new ItemStack(Material.BEEHIVE));

        assertNull(StorageSign.fromStoredItem(occupied));
    }

    @Test
    void fromStoredItemCoversWrongMetaTypeForBookAndPotion() {
        ItemStack book = mock(ItemStack.class);
        when(book.getType()).thenReturn(Material.ENCHANTED_BOOK);
        when(book.getItemMeta()).thenReturn(mock(ItemMeta.class));
        when(book.clone()).thenReturn(new ItemStack(Material.ENCHANTED_BOOK));
        assertNotNull(StorageSign.fromStoredItem(book));

        ItemStack potion = mock(ItemStack.class);
        when(potion.getType()).thenReturn(Material.POTION);
        when(potion.getItemMeta()).thenReturn(mock(ItemMeta.class));
        when(potion.clone()).thenReturn(new ItemStack(Material.POTION));
        assertNotNull(StorageSign.fromStoredItem(potion));
    }

    @Test
    void getContentsReturnsNullWhenOminousBannerTemplateIsUnavailable() {
        StorageSign banner = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "WHITE_BANNER:8", "1"});
        assertNotNull(banner);
        try (MockedStatic<StorageSignPlugin> mocked = mockStatic(StorageSignPlugin.class)) {
            mocked.when(StorageSignPlugin::getOminousBannerMeta).thenReturn(null);
            assertNull(banner.getContents(1));
        }
    }

    @Test
    void getContentsHandlesPotionAndBookItemsWithoutMutableMeta() {
        StorageSign potion = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "POTION:HEAL:0", "1"});
        assertNotNull(potion);
        StorageSign book = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "ENCHBOOK:sharp:5", "1"});
        assertNotNull(book);

        try (var mocked = mockConstruction(ItemStack.class, (stack, context) -> {
            when(stack.getItemMeta()).thenReturn(null);
        })) {
            assertNotNull(potion.getContents(1));
            assertNotNull(book.getContents(1));
            assertEquals(2, mocked.constructed().size());
        }
    }

    @Test
    void getContentsFallsBackWhenItemMetaCannotBeCreated() {
        StorageSign potion = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "POTION:HEAL:0", "1"});
        assertNotNull(potion);
        StorageSign book = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "ENCHBOOK:sharp:5", "1"});
        assertNotNull(book);
        StorageSign rocket = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "FIREWORK_ROCKET:3", "1"});
        assertNotNull(rocket);
        StorageSign stone = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "STONE:1", "1"});
        assertNotNull(stone);

        try (var mocked = mockConstruction(ItemStack.class, (stack, context) -> {
            when(stack.getItemMeta()).thenReturn(null);
        })) {
            assertNotNull(potion.getContents(1));
            assertNotNull(book.getContents(1));
            assertNotNull(rocket.getContents(1));
            assertNotNull(stone.getContents(1));
            assertEquals(4, mocked.constructed().size());
        }
    }

    @Test
    void getContentsEncodesFireworkPowerGreaterThanOne() {
        StorageSign rocket = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "FIREWORK_ROCKET:3", "1"});
        assertNotNull(rocket);

        ItemStack item = rocket.getContents(1);
        assertNotNull(item);
        assertEquals(Material.FIREWORK_ROCKET, item.getType());
        assertEquals(3, ((FireworkMeta) item.getItemMeta()).getPower());
    }

    @Test
    void createStorageSignItemAppliesConfiguredMaxStackSize() throws Exception {
        Field field = ConfigLoader.class.getDeclaredField("maxStackSize");
        field.setAccessible(true);
        int original = field.getInt(null);
        field.setInt(null, 8);
        try {
            ItemStack item = StorageSign.createStorageSignItem(
                Material.OAK_SIGN, "STONE 1", 1);
            assertEquals(8, item.getItemMeta().getMaxStackSize());
        } finally {
            field.setInt(null, original);
        }
    }

    @Test
    void createStorageSignItemUsesStorageSignDisplayName() {
        ItemStack item = StorageSign.createStorageSignItem(Material.OAK_SIGN, "STONE 1", 1);
        assertEquals("StorageSign", item.getItemMeta().getDisplayName());
    }

    @Test
    void createStorageSignItemWithContentsReturnsRawItemWhenMetaUnavailable() {
        StorageSign potion = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "POTION:HEAL:0", "1"});
        assertNotNull(potion);

        try (var mocked = mockConstruction(ItemStack.class, (stack, context) -> {
            when(stack.getItemMeta()).thenReturn(null);
        })) {
            ItemStack item = StorageSign.createStorageSignItem(Material.OAK_SIGN, potion, 1);
            assertNotNull(item);
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void signInSignAndHorseEggContentsRoundTripThroughGetContents() {
        StorageSign signItem = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "OakStorageSign", "4"});
        assertNotNull(signItem);
        assertTrue(signItem.isSignAsItem());
        ItemStack signStack = signItem.getContents(2);
        assertNotNull(signStack);
        assertEquals(Material.OAK_SIGN, signStack.getType());
        assertEquals(2, signStack.getAmount());

        StorageSign horseEgg = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "HorseEgg", "3"});
        assertNotNull(horseEgg);
        ItemStack marker = horseEgg.getContents(1);
        assertNotNull(marker);
        assertEquals(Material.GHAST_SPAWN_EGG, marker.getType());
        assertEquals("HorseEgg", marker.getItemMeta().getDisplayName());
    }

    @Test
    void ordinaryItemContentsRoundTripWithoutDamage() {
        StorageSign stored = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "12"});
        assertNotNull(stored);
        ItemStack item = stored.getContents(1);
        assertNotNull(item);
        assertEquals(Material.STONE, item.getType());
        assertEquals(1, item.getAmount());
    }

    @Test
    void legacyEndPortalSignUsesEmptySignContentsWhenNotMarker() {
        StorageSign endPortal = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "END_PORTAL:0", "1"});
        assertNotNull(endPortal);

        ItemStack contents = endPortal.getContents(1);
        assertNotNull(contents);
        assertEquals(Material.OAK_SIGN, contents.getType());
        assertEquals(Material.END_PORTAL, endPortal.getMaterial());
        assertEquals(0, endPortal.getDamage());
    }

    @Test
    void legacyMarkerSignCreatesHorseEggContentsAndSimilarity() {
        StorageSign horseEgg = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "HorseEgg", "1"});
        assertNotNull(horseEgg);

        ItemStack contents = horseEgg.getContents(1);
        assertNotNull(contents);
        assertEquals(Material.GHAST_SPAWN_EGG, contents.getType());
        assertTrue(horseEgg.isSimilar(contents));
    }

    @Test
    void legacyMarkerSimilarityRejectsMissingLore() {
        StorageSign horseEgg = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "HorseEgg", "1"});
        assertNotNull(horseEgg);

        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getType()).thenReturn(Material.GHAST_SPAWN_EGG);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getDisplayName()).thenReturn("HorseEgg");
        when(meta.hasLore()).thenReturn(false);

        assertFalse(horseEgg.isSimilar(item));
    }

    @Test
    void emptyStorageSignHasNoContents() {
        assertNull(StorageSign.empty().getContents(1));
    }

    @Test
    void canonicalPotionIdentifierExistsOnlyForPotionBackedSigns() {
        StorageSign potion = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "POTION:HEAL:0", "1"});
        assertNotNull(potion);
        assertNotNull(potion.getCanonicalPotionIdentifier());

        StorageSign stone = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "STONE", "1"});
        assertNotNull(stone);
        assertNull(stone.getCanonicalPotionIdentifier());
    }

    @Test
    void invalidLegacyEnchantBookSpecIsRejectedWhileValidOneRoundTrips() {
        assertNull(StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "ENCHBOOK:sharp:not-a-number", "1"}));

        StorageSign stored = StorageSign.fromSignLines(
            new String[] {StorageSign.HEADER_LINE, "ENCHBOOK:sharp:5", "1"});
        assertNotNull(stored);
        assertEquals(Material.ENCHANTED_BOOK, stored.getMaterial());
        assertEquals(5, stored.getDamage());
    }

    @Test
    void rejectsPotionWithCustomEffects() {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1), true);
        potion.setItemMeta(meta);

        assertNull(StorageSign.fromStoredItem(potion));
    }

    @Test
    void preservesSingleEnchantAndRejectsMultipleEnchants() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(Enchantment.SHARPNESS, 5, true);
        book.setItemMeta(meta);
        StorageSign stored = StorageSign.fromStoredItem(book);
        assertNotNull(stored);
        assertNotNull(stored.getContents(1));
        assertTrue(stored.isSimilar(book));
        ItemStack wrongBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta wrongMeta = (EnchantmentStorageMeta) wrongBook.getItemMeta();
        wrongMeta.addStoredEnchant(Enchantment.EFFICIENCY, 5, true);
        wrongBook.setItemMeta(wrongMeta);
        assertFalse(stored.isSimilar(wrongBook));

        meta.addStoredEnchant(Enchantment.EFFICIENCY, 3, true);
        book.setItemMeta(meta);
        assertNull(StorageSign.fromStoredItem(book));
    }

    @Test
    void preservesFireworkPowerAndRejectsUnrepresentableEffects() {
        ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta meta = (FireworkMeta) rocket.getItemMeta();
        meta.setPower(3);
        rocket.setItemMeta(meta);
        StorageSign stored = StorageSign.fromStoredItem(rocket);
        assertNotNull(stored);
        assertEquals(3, ((FireworkMeta) stored.getContents(1).getItemMeta()).getPower());

        meta.addEffect(FireworkEffect.builder().withColor(Color.RED).with(FireworkEffect.Type.BALL).build());
        rocket.setItemMeta(meta);
        assertNull(StorageSign.fromStoredItem(rocket));
    }

    @Test
    void similarChecksCoverShulkerAndBeehiveAndSignCases() {
        StorageSign emptyShulker = StorageSign.fromStoredItem(new ItemStack(Material.SHULKER_BOX));
        assertNotNull(emptyShulker);
        assertTrue(emptyShulker.isSimilar(new ItemStack(Material.SHULKER_BOX)));

        ItemStack filled = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta filledMeta = (BlockStateMeta) filled.getItemMeta();
        ShulkerBox box = (ShulkerBox) filledMeta.getBlockState();
        box.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        filledMeta.setBlockState(box);
        filled.setItemMeta(filledMeta);
        assertTrue(emptyShulker.isSimilar(filled));

        StorageSign emptyBeehive = StorageSign.fromStoredItem(new ItemStack(Material.BEEHIVE));
        assertNotNull(emptyBeehive);
        assertTrue(emptyBeehive.isSimilar(new ItemStack(Material.BEEHIVE)));

        ItemStack occupiedBeehive = mock(ItemStack.class);
        BlockStateMeta beehiveMeta = mock(BlockStateMeta.class);
        Beehive beehive = mock(Beehive.class);
        when(occupiedBeehive.getType()).thenReturn(Material.BEEHIVE);
        when(occupiedBeehive.getItemMeta()).thenReturn(beehiveMeta);
        when(beehiveMeta.getBlockState()).thenReturn(beehive);
        when(beehive.getEntityCount()).thenReturn(1);
        assertTrue(emptyBeehive.isSimilar(occupiedBeehive));

        StorageSign signItem = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "OakStorageSign", "4"});
        assertNotNull(signItem);
        assertTrue(signItem.isSimilar(StorageSign.createStorageSignItem(
            Material.OAK_SIGN, StorageSign.EMPTY_MARKER, 1)));
        ItemStack namedSign = StorageSign.createStorageSignItem(
            Material.OAK_SIGN, StorageSign.EMPTY_MARKER, 1);
        ItemMeta namedSignMeta = namedSign.getItemMeta();
        namedSignMeta.setDisplayName("custom");
        namedSign.setItemMeta(namedSignMeta);
        assertFalse(signItem.isSimilar(namedSign));

        ItemStack emptyBanner = new ItemStack(Material.WHITE_BANNER);
        assertFalse(signItem.isSimilar(emptyBanner));

        ItemStack potion = new ItemStack(Material.POTION);
        assertFalse(signItem.isSimilar(potion));
    }

    @Test
    void similarChecksCoverEnchantedBookPotionAndBannerRejections() {
        StorageSign book = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "ENCHBOOK:sharp:5", "1"});
        assertNotNull(book);
        StorageSign storedBook = StorageSign.fromStoredItem(
            StorageSign.fromSignLines(new String[]{StorageSign.HEADER_LINE, "ENCHBOOK:sharp:5", "1"})
                .getContents(1));
        assertNotNull(storedBook);
        assertTrue(book.isSimilar(storedBook.getContents(1)));

        ItemStack wrongMetaBook = new ItemStack(Material.ENCHANTED_BOOK);
        assertFalse(book.isSimilar(wrongMetaBook));

        StorageSign potion = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "POTION:HEAL:0", "1"});
        assertNotNull(potion);
        ItemStack wrongPotion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta wrongPotionMeta = (PotionMeta) wrongPotion.getItemMeta();
        wrongPotionMeta.setBasePotionType(PotionType.HEALING);
        wrongPotion.setItemMeta(wrongPotionMeta);
        assertFalse(potion.isSimilar(wrongPotion));

        StorageSign banner = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "WHITE_BANNER:8", "1"});
        assertNotNull(banner);
        assertFalse(banner.isSimilar(new ItemStack(Material.WHITE_BANNER)));
    }

    @Test
    void similarChecksCoverUnenchantedBookAndWrongPotionBodyRejections() {
        StorageSign plainBook = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "ENCHANTED_BOOK:5", "1"});
        assertNotNull(plainBook);
        assertFalse(plainBook.isSimilar(new ItemStack(Material.ENCHANTED_BOOK)));

        StorageSign potion = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "POTION:HEAL:0", "1"});
        assertNotNull(potion);
        ItemStack wrongPotion = new ItemStack(Material.POTION);
        PotionMeta wrongPotionMeta = (PotionMeta) wrongPotion.getItemMeta();
        wrongPotionMeta.setBasePotionType(PotionType.REGENERATION);
        wrongPotion.setItemMeta(wrongPotionMeta);
        assertFalse(potion.isSimilar(wrongPotion));
    }

    @Test
    void similarChecksCoverWrongMetaTypeForBannerBookAndPotion() {
        ItemStack banner = mock(ItemStack.class);
        when(banner.getType()).thenReturn(Material.WHITE_BANNER);
        when(banner.getItemMeta()).thenReturn(mock(ItemMeta.class));
        StorageSign ominous = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "WHITE_BANNER:8", "1"});
        assertNotNull(ominous);
        assertFalse(ominous.isSimilar(banner));

        ItemStack book = mock(ItemStack.class);
        when(book.getType()).thenReturn(Material.ENCHANTED_BOOK);
        when(book.getItemMeta()).thenReturn(mock(ItemMeta.class));
        StorageSign enchanted = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "ENCHBOOK:sharp:5", "1"});
        assertNotNull(enchanted);
        assertFalse(enchanted.isSimilar(book));

        ItemStack potion = mock(ItemStack.class);
        when(potion.getType()).thenReturn(Material.POTION);
        when(potion.getItemMeta()).thenReturn(mock(ItemMeta.class));
        StorageSign potionSign = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "POTION:HEAL:0", "1"});
        assertNotNull(potionSign);
        assertFalse(potionSign.isSimilar(potion));
    }

    @Test
    void fromStoredItemCoversTypeAndMetaMismatchRejections() {
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        assertNotNull(StorageSign.fromStoredItem(shulker));
        ItemStack plainShulker = mock(ItemStack.class);
        when(plainShulker.getType()).thenReturn(Material.SHULKER_BOX);
        when(plainShulker.getItemMeta()).thenReturn(null);
        assertNull(StorageSign.fromStoredItem(plainShulker));

        ItemStack whiteBanner = new ItemStack(Material.WHITE_BANNER);
        ItemMeta plainMeta = mock(ItemMeta.class);
        when(whiteBanner.getItemMeta()).thenReturn(plainMeta);
        assertNotNull(StorageSign.fromStoredItem(whiteBanner));

        ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta rocketMeta = (FireworkMeta) rocket.getItemMeta();
        rocketMeta.addEffect(FireworkEffect.builder().withColor(Color.BLUE).with(FireworkEffect.Type.BALL).build());
        rocket.setItemMeta(rocketMeta);
        assertNull(StorageSign.fromStoredItem(rocket));
    }

    @Test
    void lowPowerFireworkKeepsLegacyZeroEncoding() {
        ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta meta = (FireworkMeta) rocket.getItemMeta();
        meta.setPower(1);
        rocket.setItemMeta(meta);

        StorageSign stored = StorageSign.fromStoredItem(rocket);
        assertNotNull(stored);
        assertEquals(0, stored.getDamage());
        assertEquals(0, ((FireworkMeta) stored.getContents(1).getItemMeta()).getPower());
    }

    @Test
    void preservesOminousBottleAmplifierAndRoundsTripContents() {
        ItemStack bottle = OminousBottleHelper.toItemStack((short) 3, 1);
        StorageSign stored = StorageSign.fromStoredItem(bottle);
        assertNotNull(stored);
        assertEquals(Material.OMINOUS_BOTTLE, stored.getMaterial());
        assertEquals(3, stored.getDamage());
        assertEquals("OMINOUS_BOTTLE:3", stored.getIdentifier());
        assertTrue(stored.isSimilar(bottle));
        assertFalse(stored.isSimilar(OminousBottleHelper.toItemStack((short) 2, 1)));
        assertEquals(3, OminousBottleHelper.getAmplifier(stored.getContents(1).getItemMeta()));
    }

    @Test
    void preservesOminousBannerMetaWhenLoadedFromStorageSign() {
        BannerMeta ominous = StorageSignPlugin.getOminousBannerMeta();
        assertNotNull(ominous);

        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        banner.setItemMeta(ominous.clone());

        StorageSign stored = StorageSign.fromStoredItem(banner);
        assertNotNull(stored);
        assertEquals(Material.WHITE_BANNER, stored.getMaterial());
        assertEquals(8, stored.getDamage());
        assertTrue(stored.isSimilar(banner));
        assertFalse(stored.isSimilar(new ItemStack(Material.WHITE_BANNER)));
        ItemStack restored = stored.getContents(1);
        assertNotNull(restored);
        assertEquals(Material.WHITE_BANNER, restored.getType());
        assertTrue(StorageSignPlugin.isOminousBannerMeta((BannerMeta) restored.getItemMeta()));
    }

    @Test
    void existingSpecialStorageSignsRejectExtraMetadataThatWouldBeLost() {
        StorageSign bookSign = StorageSign.fromSignLines(new String[] {
            StorageSign.HEADER_LINE, "ENCHBOOK:sharp:5", "1"
        });
        ItemStack multiEnchantBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bookMeta =
            (EnchantmentStorageMeta) multiEnchantBook.getItemMeta();
        bookMeta.addStoredEnchant(Enchantment.SHARPNESS, 5, true);
        bookMeta.addStoredEnchant(Enchantment.MENDING, 1, true);
        multiEnchantBook.setItemMeta(bookMeta);
        assertFalse(bookSign.isSimilar(multiEnchantBook));

        StorageSign horseSign = StorageSign.fromSignLines(new String[] {
            StorageSign.HEADER_LINE, "HorseEgg", "1"
        });
        ItemStack customHorseEgg = horseSign.getContents(1);
        ItemMeta horseMeta = customHorseEgg.getItemMeta();
        horseMeta.setLore(List.of("custom"));
        customHorseEgg.setItemMeta(horseMeta);
        assertFalse(horseSign.isSimilar(customHorseEgg));

        StorageSign bannerSign = StorageSign.fromSignLines(new String[] {
            StorageSign.HEADER_LINE, "WHITE_BANNER:8", "1"
        });
        ItemStack customBanner = new ItemStack(Material.WHITE_BANNER);
        BannerMeta bannerMeta = (BannerMeta) StorageSignPlugin.getOminousBannerMeta().clone();
        bannerMeta.setLore(List.of("custom"));
        customBanner.setItemMeta(bannerMeta);
        assertFalse(bannerSign.isSimilar(customBanner));
        assertNull(StorageSign.fromStoredItem(customBanner));
    }

    @Test
    void ominousBannerCompatibilityRestoresMissingTooltipFlagWithoutAcceptingCustomLore() {
        StorageSign bannerSign = StorageSign.fromSignLines(new String[] {
            StorageSign.HEADER_LINE, "WHITE_BANNER:8", "1"
        });
        BannerMeta upgradedMeta = (BannerMeta) StorageSignPlugin.getOminousBannerMeta().clone();
        upgradedMeta.removeItemFlags(ItemFlag.values());
        ItemStack upgradedBanner = new ItemStack(Material.WHITE_BANNER);
        upgradedBanner.setItemMeta(upgradedMeta);

        assertTrue(StorageSignPlugin.isCompatibleOminousBannerMeta(upgradedMeta));
        assertTrue(bannerSign.isSimilar(upgradedBanner));
        assertNotNull(StorageSign.fromStoredItem(upgradedBanner));
    }

    @Test
    void ominousBannerRecoversFromValidatedItemWhenStartupTemplateIsMissing() {
        StorageSign banner = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "WHITE_BANNER:8", "1"});
        assertNotNull(banner);

        BannerMeta ominous = StorageSignPlugin.getOminousBannerMeta();
        assertNotNull(ominous);
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        item.setItemMeta(ominous.clone());

        StorageSignPlugin.setOminousBannerMeta(null);
        ItemStack custom = item.clone();
        BannerMeta customMeta = (BannerMeta) custom.getItemMeta();
        customMeta.setLore(List.of("custom"));
        custom.setItemMeta(customMeta);
        assertNull(StorageSign.fromStoredItem(custom));
        assertNull(StorageSignPlugin.getOminousBannerMeta());

        assertTrue(banner.isSimilar(item));
        StorageSign recovered = StorageSign.fromStoredItem(item);
        assertNotNull(recovered);
        assertNotNull(StorageSignPlugin.getOminousBannerMeta());
        assertTrue(StorageSignPlugin.isOminousBannerMeta(
            StorageSignPlugin.getOminousBannerMeta()));
    }

    @Test
    void ominousBannerSignContentsUseLoadedTemplateWhenRequested() {
        StorageSign stored = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "WHITE_BANNER:8", "2"});
        assertNotNull(stored);

        ItemStack item = stored.getContents(1);
        assertNotNull(item);
        assertEquals(Material.WHITE_BANNER, item.getType());
        assertTrue(StorageSignPlugin.isOminousBannerMeta((BannerMeta) item.getItemMeta()));
    }

    @Test
    void malformedAndAdditionalLoreAreHandledDeterministically() {
        ItemStack sign = StorageSign.createStorageSignItem(Material.OAK_SIGN, "STONE nope", 1);
        StorageSign parsed = StorageSign.fromItemStack(sign);
        assertNull(parsed);

        ItemMeta meta = sign.getItemMeta();
        meta.setLore(List.of("STONE 12", "ignored compatibility note"));
        sign.setItemMeta(meta);
        assertEquals(12, StorageSign.fromItemStack(sign).getAmount());

        meta.setLore(List.of("x".repeat(10_000)));
        sign.setItemMeta(meta);
        assertNull(StorageSign.fromItemStack(sign));
    }

    @Test
    void potionItemUsesCanonicalPdcAndKeepsShortReadableLore() {
        StorageSign potion = StorageSign.fromSignLines(new String[]{
            StorageSign.HEADER_LINE, "LPOTION:SPEED:2", "7"});
        assertNotNull(potion);

        ItemStack item = StorageSign.createStorageSignItem(Material.OAK_SIGN, potion, 1);
        ItemMeta meta = item.getItemMeta();
        assertEquals("LPOTION:SPEED:2 7", meta.getLore().getFirst());
        assertEquals("LPOTION:minecraft:strong_swiftness",
            meta.getPersistentDataContainer().get(
                new NamespacedKey("storagesign", "potion_identifier"), PersistentDataType.STRING));

        meta.setLore(List.of("POTION:HEAL:0 7"));
        item.setItemMeta(meta);
        StorageSign restored = StorageSign.fromItemStack(item);
        assertNotNull(restored);
        assertEquals(Material.LINGERING_POTION, restored.getMaterial());
        assertEquals(PotionType.STRONG_SWIFTNESS, restored.getPotionType());
    }

    @Test
    void physicalSignUsesCanonicalPdcWhenDisplayIdentifierIsShortened() {
        var world = MockBukkit.getMock().addSimpleWorld("display-pdc");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        var signState = (org.bukkit.block.Sign) block.getState();
        StorageSign stored = StorageSign.fromSignLines(new String[] {
            "StorageSign", "WAXED_WEATHERED_CUT_COPPER_STAIRS", "9"});
        assertNotNull(stored);

        stored.applyToSign(signState);

        assertFalse(signState.getSide(org.bukkit.block.sign.Side.FRONT).getLine(1)
            .equals(stored.getIdentifier()));
        assertEquals("WAXED_WEATHERED_CUT_COPPER_STAIRS",
            signState.getPersistentDataContainer().get(
                new NamespacedKey("storagesign", "storage_identifier"),
                PersistentDataType.STRING));
        StorageSign restored = StorageSign.fromSign(signState);
        assertNotNull(restored);
        assertEquals(Material.WAXED_WEATHERED_CUT_COPPER_STAIRS, restored.getMaterial());
        assertEquals(9, restored.getAmount());
    }

    @Test
    void physicalSignFallsBackWithoutCanonicalPdcAndRejectsCorruptCanonicalPdc() {
        var world = MockBukkit.getMock().addSimpleWorld("display-pdc-fallback");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        var signState = (org.bukkit.block.Sign) block.getState();
        var front = signState.getSide(org.bukkit.block.sign.Side.FRONT);
        front.setLine(0, "StorageSign");
        front.setLine(1, "STONE");
        front.setLine(2, "4");
        signState.update();

        StorageSign legacy = StorageSign.fromSign(signState);
        assertNotNull(legacy);
        assertEquals(Material.STONE, legacy.getMaterial());

        signState.getPersistentDataContainer().set(
            new NamespacedKey("storagesign", "storage_identifier"),
            PersistentDataType.STRING, "UNKNOWN_CORRUPT_IDENTIFIER");
        assertNull(StorageSign.fromSign(signState));
    }
}
