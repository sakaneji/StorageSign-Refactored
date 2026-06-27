package storagesign;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class StorageSignCommandTabCompleterTest {
    @Test
    void giveCompletesIdentifiersAndSignMaterialsInsteadOfPlayers() {
        ServerMock server = MockBukkit.mock();
        try {
            server.addPlayer("Steve");
            StorageSignCommandTabCompleter completer = new StorageSignCommandTabCompleter();
            Command command = mock(Command.class);
            when(command.getName()).thenReturn("storagesigngive");

            List<String> identifiers = completer.onTabComplete(
                server.getConsoleSender(), command, "ssgive", new String[] {""});
            List<String> signs = completer.onTabComplete(
                server.getConsoleSender(), command, "ssgive", new String[] {"STONE", "1", ""});

            assertTrue(identifiers.contains("STONE"));
            assertFalse(identifiers.contains("Steve"));
            assertTrue(signs.contains("OAK_SIGN"));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void indexCompletesRebuildTargetsAndWorldNames() {
        ServerMock server = MockBukkit.mock();
        try {
            server.addSimpleWorld("index-world");
            StorageSignCommandTabCompleter completer = new StorageSignCommandTabCompleter();
            Command command = mock(Command.class);
            when(command.getName()).thenReturn("storagesignindex");

            List<String> roots = completer.onTabComplete(
                server.getConsoleSender(), command, "ssindex", new String[] {""});
            List<String> rebuildTargets = completer.onTabComplete(
                server.getConsoleSender(), command, "ssindex", new String[] {"rebuild", ""});

            assertTrue(roots.contains("status"));
            assertTrue(roots.contains("rebuild"));
            assertTrue(rebuildTargets.contains("all"));
            assertTrue(rebuildTargets.contains("index-world"));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void searchCompletesItemIdentifiersAndWorldFlags() {
        ServerMock server = MockBukkit.mock();
        try {
            server.addSimpleWorld("search-world");
            StorageSignCommandTabCompleter completer = new StorageSignCommandTabCompleter();
            Command command = mock(Command.class);
            when(command.getName()).thenReturn("storagesignsearch");
            CommandSender sender = server.getConsoleSender();

            List<String> roots = completer.onTabComplete(sender, command, "sssearch", new String[] {""});
            List<String> identifiers = completer.onTabComplete(sender, command, "sssearch",
                new String[] {"item", ""});
            List<String> worldNames = completer.onTabComplete(sender, command, "sssearch",
                new String[] {"item", "STONE", "--world", ""});
            List<String> flags = completer.onTabComplete(sender, command, "sssearch",
                new String[] {"item", "STONE", ""});

            assertTrue(roots.contains("item"));
            assertTrue(identifiers.contains("STONE"));
            assertTrue(worldNames.contains("search-world"));
            assertTrue(flags.contains("--contains"));
            assertTrue(flags.contains("--page"));
        } finally {
            MockBukkit.unmock();
        }
    }
}
