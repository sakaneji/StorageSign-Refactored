package storagesign;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.lang.reflect.Field;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.meta.BannerMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import org.mockbukkit.mockbukkit.MockBukkit;
import storagesign.compat.OminousBannerCodec;

class OminousBannerMetaTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void acceptsExactVanillaOminousBannerPatterns() {
        assertTrue(OminousBannerCodec.matches(standardColors(), standardKeys()));
    }

    @Test
    void rejectsArbitraryEightPatternWhiteBanner() {
        List<DyeColor> colors = new java.util.ArrayList<>(standardColors());
        colors.set(0, DyeColor.RED);
        assertFalse(OminousBannerCodec.matches(colors, standardKeys()));
    }

    @Test
    void rejectsBannerWithoutEightPatterns() {
        assertFalse(OminousBannerCodec.matches(List.of(), List.of()));
    }

    @Test
    void rejectsWrongColorOrKeyListSizes() {
        assertFalse(OminousBannerCodec.matches(standardColors(), standardKeys().subList(0, 7)));
        assertFalse(OminousBannerCodec.matches(standardColors().subList(0, 7), standardKeys()));
    }

    @Test
    void rejectsNullPatternOrKeyEntriesInListMatcher() {
        List<DyeColor> colors = new java.util.ArrayList<>(standardColors());
        List<NamespacedKey> keys = new java.util.ArrayList<>(standardKeys());
        colors.set(3, null);
        keys.set(4, null);
        assertFalse(OminousBannerCodec.matches(colors, standardKeys()));
        assertThrows(NullPointerException.class, () -> OminousBannerCodec.matches(standardColors(), keys));
    }

    @Test
    void rejectsMismatchedPatternKeysEvenWhenColorsMatch() {
        List<NamespacedKey> wrongKeys = new java.util.ArrayList<>(standardKeys());
        wrongKeys.set(0, NamespacedKey.minecraft("stripe_top"));
        assertFalse(OminousBannerCodec.matches(standardColors(), wrongKeys));
    }

    @Test
    void createProducesMatchingOminousBannerMeta() {
        OminousBannerCodec codec = new OminousBannerCodec();
        BannerMeta meta = codec.create();

        assertNotNull(meta);
        assertTrue(codec.matches(meta));
        assertTrue(meta.numberOfPatterns() == 8);
    }

    @Test
    void createReturnsNullWhenBannerMetaIsUnavailable() {
        try (var mocked = mockConstruction(org.bukkit.inventory.ItemStack.class, (stack, context) -> {
            when(stack.getItemMeta()).thenReturn(null);
        })) {
            assertNull(new OminousBannerCodec().create());
            assertTrue(mocked.constructed().size() == 1);
        }
    }

    @Test
    void createThrowsWhenARegistryPatternIsMissing() throws Exception {
        OminousBannerCodec codec = new OminousBannerCodec();
        Field field = Registry.class.getField("BANNER_PATTERN");
        field.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        Object original = unsafe.getObject(base, offset);
        Registry<PatternType> brokenRegistry = mock(Registry.class);
        when(brokenRegistry.get(NamespacedKey.minecraft("rhombus"))).thenReturn(null);

        try {
            unsafe.putObject(base, offset, brokenRegistry);
            assertThrows(IllegalStateException.class, codec::create);
        } finally {
            unsafe.putObject(base, offset, original);
        }
    }

    @Test
    void matchesKeyAcceptsRegistryPatternTypeAndRejectsNull() throws Exception {
        OminousBannerCodec codec = new OminousBannerCodec();
        java.lang.reflect.Method method = OminousBannerCodec.class.getDeclaredMethod(
            "matchesKey", PatternType.class, NamespacedKey.class);
        method.setAccessible(true);

        BannerMeta meta = codec.create();
        Pattern pattern = meta.getPatterns().getFirst();
        PatternType type = pattern.getPattern();
        NamespacedKey key = Registry.BANNER_PATTERN.getKey(type);

        assertTrue((Boolean) method.invoke(codec, type, key));
        assertFalse((Boolean) method.invoke(codec, null, key));
    }

    @Test
    void matchesKeyFallsBackToPatternTypeGetKeyWhenRegistryLookupIsUnavailable() throws Exception {
        OminousBannerCodec codec = new OminousBannerCodec();
        java.lang.reflect.Method method = OminousBannerCodec.class.getDeclaredMethod(
            "matchesKey", PatternType.class, NamespacedKey.class);
        method.setAccessible(true);

        PatternType patternType = mock(PatternType.class);
        when(patternType.getKey()).thenReturn(NamespacedKey.minecraft("rhombus"));

        assertTrue((Boolean) method.invoke(codec, patternType, NamespacedKey.minecraft("rhombus")));
    }

    @Test
    void matchesKeyFallbackRejectsDifferentKeyValues() throws Exception {
        OminousBannerCodec codec = new OminousBannerCodec();
        java.lang.reflect.Method method = OminousBannerCodec.class.getDeclaredMethod(
            "matchesKey", PatternType.class, NamespacedKey.class);
        method.setAccessible(true);

        PatternType patternType = mock(PatternType.class);
        when(patternType.getKey()).thenReturn(NamespacedKey.minecraft("stripe_top"));

        assertFalse((Boolean) method.invoke(codec, patternType, NamespacedKey.minecraft("rhombus")));
    }

    @Test
    void matchesRejectsWrongPatternCountsAndWrongColors() {
        OminousBannerCodec codec = new OminousBannerCodec();
        BannerMeta meta = codec.create();

        List<org.bukkit.block.banner.Pattern> patterns = new java.util.ArrayList<>(meta.getPatterns());
        patterns.removeLast();
        meta.setPatterns(patterns);
        assertFalse(codec.matches(meta));

        BannerMeta wrongColor = codec.create();
        wrongColor.setPatterns(java.util.List.of(
            new org.bukkit.block.banner.Pattern(DyeColor.RED, wrongColor.getPatterns().getFirst().getPattern())));
        assertFalse(codec.matches(wrongColor));
    }

    @Test
    void matchesRejectsWrongFirstColorAndWrongFirstKey() {
        OminousBannerCodec codec = new OminousBannerCodec();
        BannerMeta wrongColor = codec.create();
        List<Pattern> colorPatterns = new java.util.ArrayList<>(wrongColor.getPatterns());
        colorPatterns.set(0, new Pattern(DyeColor.RED, colorPatterns.get(0).getPattern()));
        wrongColor.setPatterns(colorPatterns);
        assertFalse(codec.matches(wrongColor));

        BannerMeta wrongKey = codec.create();
        List<Pattern> keyPatterns = new java.util.ArrayList<>(wrongKey.getPatterns());
        keyPatterns.set(0, new Pattern(DyeColor.CYAN, Registry.BANNER_PATTERN.get(NamespacedKey.minecraft("stripe_top"))));
        wrongKey.setPatterns(keyPatterns);
        assertFalse(codec.matches(wrongKey));
    }

    @Test
    void matchesRejectsNullMeta() {
        assertFalse(new OminousBannerCodec().matches((BannerMeta) null));
    }

    @Test
    void matchesKeyRejectsWhenPatternTypeGetKeyThrows() throws Exception {
        OminousBannerCodec codec = new OminousBannerCodec();
        java.lang.reflect.Method method = OminousBannerCodec.class.getDeclaredMethod(
            "matchesKey", PatternType.class, NamespacedKey.class);
        method.setAccessible(true);

        PatternType patternType = mock(PatternType.class);
        when(patternType.getKey()).thenThrow(new RuntimeException("boom"));

        assertFalse((Boolean) method.invoke(codec, patternType, NamespacedKey.minecraft("rhombus")));
    }

    @Test
    void matchesKeyCoversRegistryLookupFailureAndFallsBackToPatternTypeKey() throws Exception {
        OminousBannerCodec codec = new OminousBannerCodec();
        java.lang.reflect.Method method = OminousBannerCodec.class.getDeclaredMethod(
            "matchesKey", PatternType.class, NamespacedKey.class);
        method.setAccessible(true);

        PatternType patternType = mock(PatternType.class);
        when(patternType.getKey()).thenReturn(NamespacedKey.minecraft("rhombus"));

        Field field = Registry.class.getField("BANNER_PATTERN");
        field.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        Object original = unsafe.getObject(base, offset);
        Registry<PatternType> brokenRegistry = mock(Registry.class);
        when(brokenRegistry.getKey(patternType)).thenThrow(new RuntimeException("boom"));

        try {
            unsafe.putObject(base, offset, brokenRegistry);
            assertTrue((Boolean) method.invoke(codec, patternType, NamespacedKey.minecraft("rhombus")));
        } finally {
            unsafe.putObject(base, offset, original);
        }
    }

    @Test
    void matchesKeyUsesRegistryLookupWhenAvailable() throws Exception {
        OminousBannerCodec codec = new OminousBannerCodec();
        java.lang.reflect.Method method = OminousBannerCodec.class.getDeclaredMethod(
            "matchesKey", PatternType.class, NamespacedKey.class);
        method.setAccessible(true);

        PatternType patternType = mock(PatternType.class);
        NamespacedKey expected = NamespacedKey.minecraft("rhombus");

        Field field = Registry.class.getField("BANNER_PATTERN");
        field.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        Object original = unsafe.getObject(base, offset);
        Registry<PatternType> registry = mock(Registry.class);
        when(registry.getKey(patternType)).thenReturn(expected);

        try {
            unsafe.putObject(base, offset, registry);
            assertTrue((Boolean) method.invoke(codec, patternType, expected));
        } finally {
            unsafe.putObject(base, offset, original);
        }
    }

    @Test
    void matchesKeyRejectsDifferentRegistryLookupValue() throws Exception {
        OminousBannerCodec codec = new OminousBannerCodec();
        java.lang.reflect.Method method = OminousBannerCodec.class.getDeclaredMethod(
            "matchesKey", PatternType.class, NamespacedKey.class);
        method.setAccessible(true);

        PatternType patternType = mock(PatternType.class);
        NamespacedKey expected = NamespacedKey.minecraft("rhombus");

        Field field = Registry.class.getField("BANNER_PATTERN");
        field.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        Object original = unsafe.getObject(base, offset);
        Registry<PatternType> registry = mock(Registry.class);
        when(registry.getKey(patternType)).thenReturn(NamespacedKey.minecraft("stripe_top"));

        try {
            unsafe.putObject(base, offset, registry);
            assertFalse((Boolean) method.invoke(codec, patternType, expected));
        } finally {
            unsafe.putObject(base, offset, original);
        }
    }

    @Test
    void matchesRejectsBannerWithNullPatternEntry() {
        OminousBannerCodec codec = new OminousBannerCodec();
        BannerMeta meta = codec.create();
        java.util.List<Pattern> patterns = new java.util.ArrayList<>(meta.getPatterns());
        patterns.set(0, null);
        meta.setPatterns(patterns);
        assertThrows(NullPointerException.class, () -> codec.matches(meta));
    }

    private static List<DyeColor> standardColors() {
        return List.of(
            DyeColor.CYAN, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.LIGHT_GRAY,
            DyeColor.BLACK, DyeColor.LIGHT_GRAY, DyeColor.LIGHT_GRAY, DyeColor.BLACK
        );
    }

    private static List<NamespacedKey> standardKeys() {
        return List.of(
            key("rhombus"), key("stripe_bottom"), key("stripe_center"), key("border"),
            key("stripe_middle"), key("half_horizontal"), key("circle"), key("border")
        );
    }

    private static NamespacedKey key(String value) {
        return NamespacedKey.minecraft(value);
    }
}
