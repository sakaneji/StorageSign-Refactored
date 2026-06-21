package storagesign.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.GameMode;
import org.bukkit.Material;
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
class SsGiveCommandIntegrationTest {

    private ServerMock server;
    private PlayerMock player;
    private StorageSignPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(StorageSignPlugin.class);
        player = server.addPlayer();
        player.setGameMode(GameMode.CREATIVE);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void validNamespacedSignCommandGivesRoundTrippableStorageSign() {
        assertTrue(server.dispatchCommand(player, "ssgive STONE 128 minecraft:spruce_sign"));

        ItemStack item = firstItem(Material.SPRUCE_SIGN);
        assertNotNull(item);
        StorageSign sign = StorageSign.fromItemStack(item);
        assertNotNull(sign);
        assertEquals(Material.STONE, sign.getMaterial());
        assertEquals(128, sign.getAmount());
    }

    @Test
    void nonCreativePlayerIsRejectedWithoutReceivingAnItem() {
        player.setGameMode(GameMode.SURVIVAL);

        assertTrue(server.dispatchCommand(player, "ssgive STONE 1"));

        assertEquals(0, player.getInventory().getContents().length == 0
            ? 0 : player.getInventory().all(Material.OAK_SIGN).size());
        assertTrue(player.nextMessage().contains("クリエイティブ"));
    }

    @Test
    void invalidAmountAndIdentifierAreRejected() {
        assertTrue(server.dispatchCommand(player, "ssgive STONE nope"));
        assertTrue(player.nextMessage().contains("整数"));

        assertTrue(server.dispatchCommand(player, "ssgive UNKNOWN_IDENTIFIER 1"));
        assertTrue(player.nextMessage().contains("itemIdentifier"));
    }

    @Test
    void invalidArgumentsPermissionAndSignTypeAreRejected() {
        assertTrue(server.dispatchCommand(player, "ssgive STONE"));
        assertTrue(player.nextMessage().contains("使い方"));
        player.nextMessage(); // 使用例

        assertTrue(server.dispatchCommand(player, "ssgive STONE 1 OAK_SIGN extra"));
        assertTrue(player.nextMessage().contains("使い方"));
        player.nextMessage(); // 使用例

        assertTrue(server.dispatchCommand(player, "ssgive STONE -1"));
        assertTrue(player.nextMessage().contains("0 以上"));

        assertTrue(server.dispatchCommand(player, "ssgive STONE 1 STONE"));
        assertTrue(player.nextMessage().contains("看板種類"));

        player.addAttachment(plugin, "storagesign.give", false);
        assertTrue(server.dispatchCommand(player, "ssgive STONE 1"));
        assertTrue(player.nextMessage().contains("permission"));
    }

    @Test
    void consoleReceivesPlayerOnlyMessage() {
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "ssgive STONE 1"));
        assertTrue(server.getConsoleSender().nextMessage().contains("プレイヤー専用"));
    }

    private ItemStack firstItem(Material material) {
        return player.getInventory().all(material).values().stream().findFirst().orElse(null);
    }
}
