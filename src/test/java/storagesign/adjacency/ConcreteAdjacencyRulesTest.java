package storagesign.adjacency;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import storagesign.StorageSign;

class ConcreteAdjacencyRulesTest {

    @Test
    void standingSignAboveContainerMatches() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(BlockFace.UP)).thenReturn(signBlock);
        when(container.getRelative(BlockFace.DOWN)).thenReturn(air);
        assertMatches(new StandingAndCeilingHangingRule(), container, signBlock);
    }

    @Test
    void standingAndCeilingRuleCanMatchDownwardHangingSignsAndRejectOtherBlocks() {
        Block container = mock(Block.class);
        Block up = mock(Block.class);
        Block down = matchingSign(Material.OAK_HANGING_SIGN);
        Block air = mock(Block.class);
        when(up.getType()).thenReturn(Material.AIR);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(BlockFace.UP)).thenReturn(up);
        when(container.getRelative(BlockFace.DOWN)).thenReturn(down);
        assertMatches(new StandingAndCeilingHangingRule(), container, down);

        when(container.getRelative(BlockFace.UP)).thenReturn(air);
        when(container.getRelative(BlockFace.DOWN)).thenReturn(air);
        ItemStack item = mock(ItemStack.class);
        var rule = new StandingAndCeilingHangingRule();
        var match = rule.findFirstMatch(new SsAdjacencyQuery(container, item,
            SsAdjacencyPurpose.INVENTORY_TRANSFER));
        assertTrue(match.isEmpty());
    }

    @Test
    void standingAndCeilingRuleFindMatchesReturnsBothUpAndDownHits() {
        Block container = mock(Block.class);
        Block up = matchingSign(Material.OAK_SIGN);
        Block down = matchingSign(Material.OAK_HANGING_SIGN);
        when(container.getRelative(BlockFace.UP)).thenReturn(up);
        when(container.getRelative(BlockFace.DOWN)).thenReturn(down);
        ItemStack item = mock(ItemStack.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(storageSign.isSimilar(item)).thenReturn(true);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(org.mockito.ArgumentMatchers.any(Sign.class)))
                .thenReturn(storageSign);
            assertEquals(2, new StandingAndCeilingHangingRule().findMatches(
                new SsAdjacencyQuery(container, item, SsAdjacencyPurpose.INVENTORY_TRANSFER)).size());
        }
    }

    @Test
    void wallSignMatchesOnlyWhenBackFacesContainer() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.SOUTH)).thenReturn(signBlock);
        Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.SOUTH);
        when(signBlock.getBlockData()).thenReturn(data);
        assertMatches(new WallSignBackFaceRule(), container, signBlock);
    }

    @Test
    void wallSignRuleRejectsMismatchedFacingAndNonDirectionalBlocks() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.SOUTH)).thenReturn(signBlock);
        when(signBlock.getBlockData()).thenReturn(mock(Directional.class));
        ItemStack item = mock(ItemStack.class);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(org.mockito.ArgumentMatchers.any(Sign.class)))
                .thenReturn(mock(StorageSign.class));
            var rule = new WallSignBackFaceRule();
            var match = rule.findFirstMatch(new SsAdjacencyQuery(container, item,
                SsAdjacencyPurpose.INVENTORY_TRANSFER));
            assertTrue(match.isEmpty());
            assertTrue(rule.findMatches(new SsAdjacencyQuery(container, item,
                SsAdjacencyPurpose.INVENTORY_TRANSFER)).isEmpty());
        }
    }

    @Test
    void wallSignRuleFindMatchesReturnsMatchingFaces() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(BlockFace.SOUTH)).thenReturn(signBlock);
        when(container.getRelative(BlockFace.NORTH)).thenReturn(air);
        when(container.getRelative(BlockFace.EAST)).thenReturn(air);
        when(container.getRelative(BlockFace.WEST)).thenReturn(air);
        Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.SOUTH);
        when(signBlock.getBlockData()).thenReturn(data);
        ItemStack item = mock(ItemStack.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(storageSign.isSimilar(item)).thenReturn(true);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(org.mockito.ArgumentMatchers.any(Sign.class)))
                .thenReturn(storageSign);
            assertEquals(1, new WallSignBackFaceRule().findMatches(
                new SsAdjacencyQuery(container, item, SsAdjacencyPurpose.INVENTORY_TRANSFER)).size());
        }
    }

    @Test
    void wallHangingSignMatchesSideConnection() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_HANGING_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.UP)).thenReturn(signBlock);
        Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.NORTH);
        when(signBlock.getBlockData()).thenReturn(data);
        when(signBlock.getRelative(BlockFace.WEST)).thenReturn(container);
        when(signBlock.getRelative(BlockFace.EAST)).thenReturn(air);
        when(container.getWorld()).thenReturn(mock(World.class));
        assertMatches(new WallHangingSideFacesRule(), container, signBlock);
    }

    @Test
    void wallHangingRuleSupportsAllFacingDirections() {
        assertWallHangingMatch(BlockFace.NORTH, BlockFace.WEST);
        assertWallHangingMatch(BlockFace.SOUTH, BlockFace.EAST);
        assertWallHangingMatch(BlockFace.EAST, BlockFace.NORTH);
        assertWallHangingMatch(BlockFace.WEST, BlockFace.SOUTH);
    }

    @Test
    void wallHangingRuleRejectsNullFacingAndMissingContainerSide() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_HANGING_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.UP)).thenReturn(signBlock);
        when(signBlock.getBlockData()).thenReturn(mock(Directional.class));
        when(container.getWorld()).thenReturn(mock(World.class));

        var rule = new WallHangingSideFacesRule();
        var query = new SsAdjacencyQuery(container, mock(ItemStack.class),
            SsAdjacencyPurpose.INVENTORY_TRANSFER);
        assertTrue(rule.findFirstMatch(query).isEmpty());
        assertTrue(rule.findMatches(query).isEmpty());
    }

    @Test
    void wallHangingRuleRejectsNonDirectionalBlocks() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_HANGING_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.UP)).thenReturn(signBlock);
        when(signBlock.getBlockData()).thenReturn(mock(org.bukkit.block.data.BlockData.class));
        when(container.getWorld()).thenReturn(mock(World.class));

        var rule = new WallHangingSideFacesRule();
        var query = new SsAdjacencyQuery(container, mock(ItemStack.class),
            SsAdjacencyPurpose.INVENTORY_TRANSFER);
        assertTrue(rule.findFirstMatch(query).isEmpty());
        assertTrue(rule.findMatches(query).isEmpty());
    }

    @Test
    void wallHangingRuleSkipsWhenNeitherSideTouchesTheContainer() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_HANGING_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.UP)).thenReturn(signBlock);
        Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.NORTH);
        when(signBlock.getBlockData()).thenReturn(data);
        when(signBlock.getRelative(BlockFace.WEST)).thenReturn(air);
        when(signBlock.getRelative(BlockFace.EAST)).thenReturn(air);
        when(container.getWorld()).thenReturn(mock(World.class));

        var rule = new WallHangingSideFacesRule();
        var query = new SsAdjacencyQuery(container, mock(ItemStack.class),
            SsAdjacencyPurpose.INVENTORY_TRANSFER);
        assertTrue(rule.findFirstMatch(query).isEmpty());
        assertTrue(rule.findMatches(query).isEmpty());
    }

    @Test
    void wallHangingRuleSkipsUnsupportedFacingBeforeSideChecks() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_HANGING_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.UP)).thenReturn(signBlock);
        Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.UP);
        when(signBlock.getBlockData()).thenReturn(data);
        when(container.getWorld()).thenReturn(mock(World.class));

        var rule = new WallHangingSideFacesRule();
        var query = new SsAdjacencyQuery(container, mock(ItemStack.class),
            SsAdjacencyPurpose.INVENTORY_TRANSFER);
        assertTrue(rule.findFirstMatch(query).isEmpty());
        assertTrue(rule.findMatches(query).isEmpty());
    }

    @Test
    void wallHangingRulePrivateHelpersHandleUnsupportedFacingsAndBlockIdentity() throws Exception {
        Method leftOf = WallHangingSideFacesRule.class.getDeclaredMethod("leftOf", BlockFace.class);
        Method rightOf = WallHangingSideFacesRule.class.getDeclaredMethod("rightOf", BlockFace.class);
        Method sameBlock = WallHangingSideFacesRule.class.getDeclaredMethod(
            "sameBlock", Block.class, Block.class);
        leftOf.setAccessible(true);
        rightOf.setAccessible(true);
        sameBlock.setAccessible(true);

        assertNull(leftOf.invoke(null, BlockFace.UP));
        assertNull(rightOf.invoke(null, BlockFace.UP));

        Block a = mock(Block.class);
        Block b = mock(Block.class);
        World world = mock(World.class);
        when(a.getWorld()).thenReturn(world);
        when(b.getWorld()).thenReturn(world);
        when(a.getX()).thenReturn(1);
        when(a.getY()).thenReturn(2);
        when(a.getZ()).thenReturn(3);
        when(b.getX()).thenReturn(1);
        when(b.getY()).thenReturn(2);
        when(b.getZ()).thenReturn(3);
        assertTrue((boolean) sameBlock.invoke(null, a, b));
        when(b.getZ()).thenReturn(4);
        assertFalse((boolean) sameBlock.invoke(null, a, b));
    }

    @Test
    void wallHangingRuleFindMatchesRejectsSignsWithoutContainerAttachment() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_HANGING_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.UP)).thenReturn(signBlock);
        Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.NORTH);
        when(signBlock.getBlockData()).thenReturn(data);
        when(signBlock.getRelative(BlockFace.WEST)).thenReturn(air);
        when(signBlock.getRelative(BlockFace.EAST)).thenReturn(air);
        when(container.getWorld()).thenReturn(mock(World.class));

        ItemStack item = mock(ItemStack.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(storageSign.isSimilar(item)).thenReturn(true);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(org.mockito.ArgumentMatchers.any(Sign.class)))
                .thenReturn(storageSign);
            assertTrue(new WallHangingSideFacesRule().findMatches(
                new SsAdjacencyQuery(container, item, SsAdjacencyPurpose.INVENTORY_TRANSFER)).isEmpty());
        }
    }

    @Test
    void wallHangingRuleFindMatchesReturnsAttachedStorageSign() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_HANGING_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.UP)).thenReturn(signBlock);
        Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.NORTH);
        when(signBlock.getBlockData()).thenReturn(data);
        when(signBlock.getRelative(BlockFace.WEST)).thenReturn(container);
        when(signBlock.getRelative(BlockFace.EAST)).thenReturn(air);
        when(container.getWorld()).thenReturn(mock(World.class));

        ItemStack item = mock(ItemStack.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(storageSign.isSimilar(item)).thenReturn(true);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(org.mockito.ArgumentMatchers.any(Sign.class)))
                .thenReturn(storageSign);
            var matches = new WallHangingSideFacesRule().findMatches(
                new SsAdjacencyQuery(container, item, SsAdjacencyPurpose.INVENTORY_TRANSFER));
            assertEquals(1, matches.size());
            assertEquals(storageSign, matches.get(0).storageSign());
        }
    }

    @Test
    void wallHangingRuleFindMatchesRejectsAttachedNonStorageSigns() {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_HANGING_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.UP)).thenReturn(signBlock);
        Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.NORTH);
        when(signBlock.getBlockData()).thenReturn(data);
        when(signBlock.getRelative(BlockFace.WEST)).thenReturn(container);
        when(signBlock.getRelative(BlockFace.EAST)).thenReturn(air);
        when(container.getWorld()).thenReturn(mock(World.class));

        ItemStack item = mock(ItemStack.class);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(org.mockito.ArgumentMatchers.any(Sign.class)))
                .thenReturn(null);
            assertTrue(new WallHangingSideFacesRule().findMatches(
                new SsAdjacencyQuery(container, item, SsAdjacencyPurpose.INVENTORY_TRANSFER)).isEmpty());
        }
    }

    @Test
    void adjacencySupportClassifiesSignFamiliesAndBuildsMatches() {
        assertTrue(AdjacencyRuleSupport.isStandingSign(Material.OAK_SIGN));
        assertFalse(AdjacencyRuleSupport.isStandingSign(Material.OAK_WALL_HANGING_SIGN));
        assertTrue(AdjacencyRuleSupport.isWallStandingSign(Material.OAK_WALL_SIGN));
        assertFalse(AdjacencyRuleSupport.isWallStandingSign(Material.OAK_WALL_HANGING_SIGN));
        assertTrue(AdjacencyRuleSupport.isCeilingHangingSign(Material.OAK_HANGING_SIGN));
        assertFalse(AdjacencyRuleSupport.isCeilingHangingSign(Material.OAK_WALL_HANGING_SIGN));
    }

    @Test
    void adjacencySupportRejectsMissingAndMismatchedStorageSigns() {
        assertNull(AdjacencyRuleSupport.toMatchIfStorageSign(null, null));

        Block nonSign = mock(Block.class);
        when(nonSign.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));
        assertNull(AdjacencyRuleSupport.toMatchIfStorageSign(nonSign, null));

        Block signWithoutStorageSign = matchingSign(Material.OAK_SIGN);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(org.mockito.ArgumentMatchers.any(Sign.class)))
                .thenReturn(null);
            assertNull(AdjacencyRuleSupport.toMatchIfStorageSign(signWithoutStorageSign, null));
        }

        Block signBlock = matchingSign(Material.OAK_SIGN);
        StorageSign storageSign = mock(StorageSign.class);
        ItemStack item = mock(ItemStack.class);
        when(storageSign.isSimilar(item)).thenReturn(false);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(org.mockito.ArgumentMatchers.any(Sign.class)))
                .thenReturn(storageSign);
            assertNull(AdjacencyRuleSupport.toMatchIfStorageSign(signBlock, item));
        }
    }

    private static Block matchingSign(Material material) {
        Block block = mock(Block.class);
        Sign sign = mock(Sign.class);
        when(block.getType()).thenReturn(material);
        when(block.getState()).thenReturn(sign);
        return block;
    }

    private static void assertMatches(SsAdjacencyRule rule, Block container, Block expected) {
        ItemStack item = mock(ItemStack.class);
        StorageSign storageSign = mock(StorageSign.class);
        when(storageSign.isSimilar(item)).thenReturn(true);
        try (MockedStatic<StorageSign> signs = Mockito.mockStatic(StorageSign.class)) {
            signs.when(() -> StorageSign.fromSign(org.mockito.ArgumentMatchers.any(Sign.class)))
                .thenReturn(storageSign);
            var match = rule.findFirstMatch(
                new SsAdjacencyQuery(container, item, SsAdjacencyPurpose.INVENTORY_TRANSFER));
            assertTrue(match.isPresent());
            assertTrue(match.get().signBlock() == expected);
        }
    }

    private static void assertWallHangingMatch(BlockFace facing, BlockFace containerSide) {
        Block container = mock(Block.class);
        Block signBlock = matchingSign(Material.OAK_WALL_HANGING_SIGN);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(container.getRelative(org.mockito.ArgumentMatchers.any(BlockFace.class))).thenReturn(air);
        when(container.getRelative(BlockFace.UP)).thenReturn(signBlock);
        Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(facing);
        when(signBlock.getBlockData()).thenReturn(data);
        when(signBlock.getRelative(containerSide)).thenReturn(container);
        when(container.getWorld()).thenReturn(mock(World.class));
        assertMatches(new WallHangingSideFacesRule(), container, signBlock);
    }
}
