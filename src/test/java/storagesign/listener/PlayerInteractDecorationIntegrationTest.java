package storagesign.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;

@Tag("integration")
class PlayerInteractDecorationIntegrationTest {
    private ServerMock server;
    private StorageSignPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(StorageSignPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void sneakingDyeImportUpdatesFrontColorAndStoredAmount() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.RED_DYE, 2));
        Block block = createSign("RED_DYE");

        interact(player, block);

        Sign sign = (Sign) block.getState();
        assertEquals(DyeColor.RED, sign.getSide(Side.FRONT).getColor());
        assertEquals(2, StorageSign.fromBlock(block).getAmount());
    }

    @Test
    void sneakingGlowInkImportUpdatesFrontGlowAndStoredAmount() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.GLOW_INK_SAC, 1));
        Block block = createSign("GLOW_INK_SAC");

        interact(player, block);

        Sign sign = (Sign) block.getState();
        assertTrue(sign.getSide(Side.FRONT).isGlowingText());
        assertEquals(1, StorageSign.fromBlock(block).getAmount());
    }

    private Block createSign(String identifier) {
        Block block = server.getWorlds().getFirst().getBlockAt(0, 64, 0);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.getSide(Side.FRONT).setLine(0, "StorageSign");
        sign.getSide(Side.FRONT).setLine(1, identifier);
        sign.getSide(Side.FRONT).setLine(2, "0");
        sign.update();
        return block;
    }

    private void interact(PlayerMock player, Block block) {
        PlayerInteractEvent event = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(),
            block, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND
        );
        new PlayerInteractListener(plugin).onPlayerInteract(event);
    }
}
