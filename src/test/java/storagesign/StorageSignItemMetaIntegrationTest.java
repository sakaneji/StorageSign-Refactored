package storagesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
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
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.FireworkEffect;
import org.bukkit.Color;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

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
