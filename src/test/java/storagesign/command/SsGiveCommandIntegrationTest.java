package storagesign.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import storagesign.StorageSign;
import storagesign.StorageSignPlugin;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void omittingSignTypeUsesDefaultOakSign() {
        assertTrue(server.dispatchCommand(player, "ssgive STONE 8"));

        ItemStack item = firstItem(Material.OAK_SIGN);
        assertNotNull(item);
        StorageSign sign = StorageSign.fromItemStack(item);
        assertNotNull(sign);
        assertEquals(Material.STONE, sign.getMaterial());
        assertEquals(8, sign.getAmount());
    }

    @Test
    void nonCreativePlayerIsRejectedWithoutReceivingAnItem() {
        player.setGameMode(GameMode.SURVIVAL);
        ItemStack[] before = inventorySnapshot();
        long dropsBefore = worldDropCount();

        assertTrue(server.dispatchCommand(player, "ssgive STONE 1"));

        assertInventoryUnchanged(before);
        assertEquals(dropsBefore, worldDropCount(), "Rejected command must not create world drops");
        assertTrue(player.nextMessage().contains("クリエイティブ"));
    }

    @Test
    void invalidAmountAndIdentifierAreRejected() {
        ItemStack[] before = inventorySnapshot();
        long dropsBefore = worldDropCount();
        assertTrue(server.dispatchCommand(player, "ssgive STONE nope"));
        assertTrue(player.nextMessage().contains("整数"));
        assertInventoryUnchanged(before);
        assertEquals(dropsBefore, worldDropCount());

        assertTrue(server.dispatchCommand(player, "ssgive UNKNOWN_IDENTIFIER 1"));
        assertTrue(player.nextMessage().contains("itemIdentifier"));
        assertInventoryUnchanged(before);
        assertEquals(dropsBefore, worldDropCount());
    }

    @Test
    void emptyMarkerIdentifierIsRejectedAsUnregisteredStorageSign() {
        ItemStack[] before = inventorySnapshot();
        long dropsBefore = worldDropCount();
        assertTrue(server.dispatchCommand(player, "ssgive Empty 1"));
        assertTrue(player.nextMessage().contains("itemIdentifier"));
        assertInventoryUnchanged(before);
        assertEquals(dropsBefore, worldDropCount());
    }

    @Test
    void invalidArgumentsPermissionAndSignTypeAreRejected() {
        ItemStack[] before = inventorySnapshot();
        long dropsBefore = worldDropCount();
        assertTrue(server.dispatchCommand(player, "ssgive STONE"));
        assertTrue(player.nextMessage().contains("使い方"));
        player.nextMessage(); // 使用例
        assertInventoryUnchanged(before);
        assertEquals(dropsBefore, worldDropCount());

        assertTrue(server.dispatchCommand(player, "ssgive STONE 1 OAK_SIGN extra"));
        assertTrue(player.nextMessage().contains("使い方"));
        player.nextMessage(); // 使用例
        assertInventoryUnchanged(before);
        assertEquals(dropsBefore, worldDropCount());

        assertTrue(server.dispatchCommand(player, "ssgive STONE -1"));
        assertTrue(player.nextMessage().contains("0 以上"));
        assertInventoryUnchanged(before);
        assertEquals(dropsBefore, worldDropCount());

        assertTrue(server.dispatchCommand(player, "ssgive STONE 1 STONE"));
        assertTrue(player.nextMessage().contains("看板種類"));
        assertInventoryUnchanged(before);
        assertEquals(dropsBefore, worldDropCount());

        assertTrue(server.dispatchCommand(player, "ssgive STONE 1 not-a-real-sign"));
        assertTrue(player.nextMessage().contains("看板種類"));
        assertInventoryUnchanged(before);
        assertEquals(dropsBefore, worldDropCount());

        assertTrue(server.dispatchCommand(player, "ssgive STONE 1 OAK_WALL_SIGN"));
        assertTrue(player.nextMessage().contains("看板種類"));
        assertInventoryUnchanged(before);
        assertEquals(dropsBefore, worldDropCount());

        player.addAttachment(plugin, "storagesign.give", false);
        assertTrue(server.dispatchCommand(player, "ssgive STONE 1"));
        assertTrue(player.nextMessage().contains("permission"));
        assertInventoryUnchanged(before);
        assertEquals(dropsBefore, worldDropCount());
    }

    @Test
    void consoleReceivesPlayerOnlyMessage() {
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "ssgive STONE 1"));
        assertTrue(server.getConsoleSender().nextMessage().contains("プレイヤー専用"));
    }

    @Test
    void resolveSignMaterialNormalizesNamesAndRejectsUnknownValues() throws Exception {
        Method method = SsGiveCommand.class.getDeclaredMethod("resolveSignMaterial", String.class);
        method.setAccessible(true);

        assertEquals(Material.OAK_SIGN, method.invoke(null, new Object[] {null}));
        assertEquals(Material.OAK_SIGN, method.invoke(null, " "));
        assertEquals(Material.SPRUCE_SIGN, method.invoke(null, "minecraft:spruce"));
        assertEquals(Material.BIRCH_SIGN, method.invoke(null, "birch_sign"));
        assertNull(method.invoke(null, "not-a-real-sign"));
    }

    @Test
    void leftoverItemsAreDroppedAtThePlayerLocation() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        Command command = mock(Command.class);
        SsGiveCommand give = new SsGiveCommand();
        ItemStack leftover = new ItemStack(Material.STONE, 1);

        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        when(player.hasPermission("storagesign.give")).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        leftovers.put(0, leftover);
        when(inventory.addItem(org.mockito.ArgumentMatchers.<ItemStack[]>any()))
            .thenReturn(leftovers);

        assertTrue(give.onCommand(player, command, "ssgive", new String[] {"STONE", "1"}));
        verify(world).dropItemNaturally(location, leftover);
    }

    private ItemStack firstItem(Material material) {
        return player.getInventory().all(material).values().stream().findFirst().orElse(null);
    }

    private ItemStack[] inventorySnapshot() {
        return Arrays.stream(player.getInventory().getContents())
            .map(item -> item == null ? null : item.clone())
            .toArray(ItemStack[]::new);
    }

    private void assertInventoryUnchanged(ItemStack[] before) {
        assertTrue(Arrays.equals(before, player.getInventory().getContents()),
            "Rejected command must not mutate the inventory");
    }

    private long worldDropCount() {
        return player.getWorld().getEntities().stream().filter(Item.class::isInstance).count();
    }
}
