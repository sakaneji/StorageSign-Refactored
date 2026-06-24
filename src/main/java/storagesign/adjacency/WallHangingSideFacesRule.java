package storagesign.adjacency;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;

/**
 * 壁付け吊り看板（*_WALL_HANGING_SIGN）を扱う。
 * 接続先は「看板の左右 2 方向」のブロック。
 */
public final class WallHangingSideFacesRule implements SsAdjacencyRule {

    private static final BlockFace[] NEIGHBOR_SCAN = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST
    };

    @Override
    public Optional<SsAdjacencyMatch> findFirstMatch(SsAdjacencyQuery query) {
        Block container = query.containerBlock();

        for (BlockFace offset : NEIGHBOR_SCAN) {
            Block signBlock = container.getRelative(offset);
            if (!AdjacencyRuleSupport.isWallHangingSign(signBlock.getType())) continue;
            if (!(signBlock.getBlockData() instanceof Directional directional)) continue;

            BlockFace facing = directional.getFacing();
            BlockFace left = leftOf(facing);
            BlockFace right = rightOf(facing);
            if (left == null || right == null) continue;

            Block leftBlock = signBlock.getRelative(left);
            Block rightBlock = signBlock.getRelative(right);
            if (!sameBlock(container, leftBlock) && !sameBlock(container, rightBlock)) continue;

            SsAdjacencyMatch match = AdjacencyRuleSupport.toMatchIfStorageSign(signBlock, query.item());
            if (match != null) return Optional.of(match);
        }

        return Optional.empty();
    }

    @Override
    public List<SsAdjacencyMatch> findMatches(SsAdjacencyQuery query) {
        List<SsAdjacencyMatch> matches = new ArrayList<>(2);
        Block container = query.containerBlock();

        for (BlockFace offset : NEIGHBOR_SCAN) {
            Block signBlock = container.getRelative(offset);
            if (!AdjacencyRuleSupport.isWallHangingSign(signBlock.getType())) continue;
            if (!(signBlock.getBlockData() instanceof Directional directional)) continue;

            BlockFace facing = directional.getFacing();
            BlockFace left = leftOf(facing);
            BlockFace right = rightOf(facing);
            if (left == null || right == null) continue;

            Block leftBlock = signBlock.getRelative(left);
            Block rightBlock = signBlock.getRelative(right);
            if (!sameBlock(container, leftBlock) && !sameBlock(container, rightBlock)) continue;

            SsAdjacencyMatch match = AdjacencyRuleSupport.toMatchIfStorageSign(signBlock, query.item());
            if (match != null) matches.add(match);
        }

        return matches;
    }

    private static boolean sameBlock(Block a, Block b) {
        return a.getWorld().equals(b.getWorld())
            && a.getX() == b.getX()
            && a.getY() == b.getY()
            && a.getZ() == b.getZ();
    }

    private static BlockFace leftOf(BlockFace facing) {
        if (facing == null) return null;
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            case WEST -> BlockFace.SOUTH;
            default -> null;
        };
    }

    private static BlockFace rightOf(BlockFace facing) {
        if (facing == null) return null;
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case SOUTH -> BlockFace.WEST;
            case EAST -> BlockFace.SOUTH;
            case WEST -> BlockFace.NORTH;
            default -> null;
        };
    }
}
