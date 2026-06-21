package storagesign.adjacency;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
