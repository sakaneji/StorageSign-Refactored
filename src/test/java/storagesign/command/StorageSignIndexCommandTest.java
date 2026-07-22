package storagesign.command;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Collection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import storagesign.StorageSignPlugin;
import storagesign.index.StorageSignIndex;

class StorageSignIndexCommandTest {
    @Test
    void rebuildExplainsWhenIndexIsDisabled() {
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        when(sender.hasPermission("storagesign.index.admin")).thenReturn(true);
        StorageSignIndex index = new StorageSignIndex(null, false);

        new StorageSignIndexCommand(index).onCommand(
            sender, command, "ssindex", new String[] {"rebuild", "all"});

        verify(sender).sendMessage(contains("disabled"));
    }

    @Test
    void permissionIsRequired() {
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        when(sender.hasPermission("storagesign.index.admin")).thenReturn(false);

        new StorageSignIndexCommand(new StorageSignIndex(null, false)).onCommand(
            sender, command, "ssindex", new String[] {"status"});

        verify(sender).sendMessage(anyString());
    }

    @Test
    void emptyArgsDefaultToStatus() {
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        when(sender.hasPermission("storagesign.index.admin")).thenReturn(true);
        StorageSignIndex index = mock(StorageSignIndex.class);
        when(index.isEnabled()).thenReturn(false);

        boolean handled = new StorageSignIndexCommand(index)
            .onCommand(sender, command, "ssindex", new String[0]);

        assertTrue(handled);
        verify(sender).sendMessage(contains("StorageSign index:"));
    }

    @Test
    void statusReportsIndexStateAndWorldCounts() {
        ServerMock server = MockBukkit.mock();
        StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
        try {
            var world = server.addSimpleWorld("index-status");
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("storagesign.index.admin")).thenReturn(true);

            new StorageSignIndexCommand(plugin.getStorageSignIndex()).onCommand(
                sender, mock(Command.class), "ssindex", new String[] {"status"});

            verify(sender).sendMessage(contains("StorageSign index:"));
            verify(sender).sendMessage(contains("Nearby display:"));
            verify(sender).sendMessage(contains(world.getName()));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void resolveWorldsRejectsConsoleWithoutWorldAndAcceptsPlayerWorld() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("index-world");
            StorageSignIndexCommand command = new StorageSignIndexCommand(new StorageSignIndex(null, true));
            Method resolve = StorageSignIndexCommand.class.getDeclaredMethod(
                "resolveWorlds", CommandSender.class, String[].class);
            resolve.setAccessible(true);

            CommandSender console = server.getConsoleSender();
            Object consoleResult = resolve.invoke(command, console, new String[] {"rebuild"});
            Method nextMessage = console.getClass().getMethod("nextMessage");
            assertTrue(((String) nextMessage.invoke(console)).contains("Console must specify"));
            assertTrue(consoleResult == null);

            var player = server.addPlayer();
            player.teleport(world.getSpawnLocation());
            @SuppressWarnings("unchecked")
            List<?> playerResult = (List<?>) resolve.invoke(command, player, new String[] {"rebuild"});
            assertEquals(world, playerResult.get(0));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void rebuildAllCompletesAndReportsSaveSchedulingFailure() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("index-rebuild");
            world.getChunkAt(0, 0).load();
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("storagesign.index.admin")).thenReturn(true);
            StorageSignIndex index = mock(StorageSignIndex.class);
            when(index.isEnabled()).thenReturn(true);
            when(index.isRebuilding()).thenReturn(false);
            when(index.getFormatVersion()).thenReturn(1);
            when(index.getLoadStatus()).thenReturn("loaded");
            when(index.isSaving()).thenReturn(false);
            when(index.getLastSavedCount()).thenReturn(0);
            when(index.getLastFileSize()).thenReturn(0L);
            when(index.getLastSavedAt()).thenReturn(0L);
            when(index.size()).thenReturn(0);
            when(index.size(world)).thenReturn(0);
            when(index.rebuild(anyCollection(), any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<StorageSignIndex.RebuildResult> completion =
                    invocation.getArgument(1);
                completion.accept(new StorageSignIndex.RebuildResult(1, 0, 1));
                return true;
            });
            when(index.saveAsync(any())).thenReturn(false);

            new StorageSignIndexCommand(index).onCommand(
                sender, mock(Command.class), "ssindex", new String[] {"rebuild", "all"});

            verify(sender).sendMessage(contains("loaded chunks=1"));
            verify(sender).sendMessage(contains("save could not be scheduled"));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void rebuildReportsAsynchronousSaveFailure() {
        ServerMock server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("index-save-failure");
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("storagesign.index.admin")).thenReturn(true);
            StorageSignIndex index = mock(StorageSignIndex.class);
            when(index.isEnabled()).thenReturn(true);
            when(index.rebuild(anyCollection(), any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<StorageSignIndex.RebuildResult> completion =
                    invocation.getArgument(1);
                completion.accept(new StorageSignIndex.RebuildResult(1, 0, 1));
                return true;
            });
            when(index.saveAsync(any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<StorageSignIndex.SaveResult> completion =
                    invocation.getArgument(0);
                completion.accept(new StorageSignIndex.SaveResult(false, 1, 0L, "disk full"));
                return true;
            });

            new StorageSignIndexCommand(index).onCommand(
                sender, mock(Command.class), "ssindex", new String[] {"rebuild", world.getName()});

            verify(sender).sendMessage(contains("completed but save failed: disk full"));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void statusReportsDisabledAndRebuildingIndexState() {
        ServerMock server = MockBukkit.mock();
        try {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("storagesign.index.admin")).thenReturn(true);
            StorageSignIndex index = mock(StorageSignIndex.class);
            when(index.isEnabled()).thenReturn(false);
            when(index.isRebuilding()).thenReturn(true);
            when(index.getRebuildRemaining()).thenReturn(7);
            when(index.size()).thenReturn(3);
            when(index.getFormatVersion()).thenReturn(1);
            when(index.getLoadStatus()).thenReturn("loaded");
            when(index.isSaving()).thenReturn(true);
            when(index.getLastSavedCount()).thenReturn(2);
            when(index.getLastFileSize()).thenReturn(4L);
            when(index.getLastSavedAt()).thenReturn(5L);
            new StorageSignIndexCommand(index).onCommand(
                sender, mock(Command.class), "ssindex", new String[] {"status"});
            verify(sender).sendMessage(contains("StorageSign index: disabled"));
            verify(sender).sendMessage(contains("Nearby display:"));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void statusReportsNearbyDisplayDisabledWhenGateIsOff() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("storagesign.index.admin")).thenReturn(true);
            StorageSignIndex index = mock(StorageSignIndex.class);
            when(index.isEnabled()).thenReturn(true);
            when(index.isRebuilding()).thenReturn(false);
            when(index.size()).thenReturn(0);
            when(index.getFormatVersion()).thenReturn(1);
            when(index.getLoadStatus()).thenReturn("loaded");
            when(index.isSaving()).thenReturn(false);
            when(index.getLastSavedCount()).thenReturn(0);
            when(index.getLastFileSize()).thenReturn(0L);
            when(index.getLastSavedAt()).thenReturn(0L);
            setStaticBoolean(storagesign.ConfigLoader.class, "nearbyDisplayEnabled", false);

            new StorageSignIndexCommand(index).onCommand(
                sender, mock(Command.class), "ssindex", new String[] {"status"});

            verify(sender).sendMessage(contains("Nearby display: disabled"));
        } finally {
            setStaticBoolean(storagesign.ConfigLoader.class, "nearbyDisplayEnabled", true);
            MockBukkit.unmock();
        }
    }

    @Test
    void statusReportsRebuildingProgressWhenIndexIsEnabled() {
        ServerMock server = MockBukkit.mock();
        try {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("storagesign.index.admin")).thenReturn(true);
            StorageSignIndex index = mock(StorageSignIndex.class);
            when(index.isEnabled()).thenReturn(true);
            when(index.isRebuilding()).thenReturn(true);
            when(index.getRebuildRemaining()).thenReturn(7);
            when(index.size()).thenReturn(3);
            when(index.getFormatVersion()).thenReturn(1);
            when(index.getLoadStatus()).thenReturn("loaded");
            when(index.isSaving()).thenReturn(true);
            when(index.getLastSavedCount()).thenReturn(2);
            when(index.getLastFileSize()).thenReturn(4L);
            when(index.getLastSavedAt()).thenReturn(5L);

            new StorageSignIndexCommand(index).onCommand(
                sender, mock(Command.class), "ssindex", new String[] {"status"});

            verify(sender).sendMessage(contains("rebuilding, remaining chunks=7"));
            verify(sender).sendMessage(contains("Persistence:"));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void resolveWorldsSupportsAllAndUnknownWorldBranches() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("index-world-all");
            StorageSignIndexCommand command = new StorageSignIndexCommand(new StorageSignIndex(null, true));
            Method resolve = StorageSignIndexCommand.class.getDeclaredMethod(
                "resolveWorlds", CommandSender.class, String[].class);
            resolve.setAccessible(true);
            CommandSender sender = mock(CommandSender.class);

            @SuppressWarnings("unchecked")
            Collection<?> all = (Collection<?>) resolve.invoke(command, sender, new String[] {"rebuild", "all"});
            assertTrue(all.stream().anyMatch(w -> w.equals(world)));

            Object unknown = resolve.invoke(command, sender, new String[] {"rebuild", "missing-world"});
            assertTrue(unknown == null);
            verify(sender).sendMessage(contains("Unknown world"));

            @SuppressWarnings("unchecked")
            Collection<?> selected = (Collection<?>) resolve.invoke(command, sender,
                new String[] {"rebuild", world.getName()});
            assertEquals(1, selected.size());
            assertTrue(selected.contains(world));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void rebuildRejectsTooManyArguments() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("storagesign.index.admin")).thenReturn(true);

        boolean handled = new StorageSignIndexCommand(new StorageSignIndex(null, true))
            .onCommand(sender, mock(Command.class), "ssindex", new String[] {"rebuild", "all", "extra"});

        assertTrue(!handled);
    }

    @Test
    void unknownSubcommandIsRejected() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("storagesign.index.admin")).thenReturn(true);

        boolean handled = new StorageSignIndexCommand(new StorageSignIndex(null, true))
            .onCommand(sender, mock(Command.class), "ssindex", new String[] {"bogus"});

        assertTrue(!handled);
    }

    @Test
    void rebuildFromPlayerUsesPlayerWorldAndReportsAlreadyRunning() {
        ServerMock server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("index-player-rebuild");
            var player = server.addPlayer();
            player.teleport(world.getSpawnLocation());
            player.setOp(true);
            StorageSignIndex index = mock(StorageSignIndex.class);
            when(index.isEnabled()).thenReturn(true);
            when(index.rebuild(anyCollection(), any())).thenReturn(false);

            new StorageSignIndexCommand(index).onCommand(
                player, mock(Command.class), "ssindex", new String[] {"rebuild"});

            assertTrue(player.nextMessage().contains("already running"));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void rebuildReportsSuccessfulSaveCompletion() {
        ServerMock server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("index-rebuild-success");
            world.getChunkAt(0, 0).load();
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("storagesign.index.admin")).thenReturn(true);
            StorageSignIndex index = mock(StorageSignIndex.class);
            when(index.isEnabled()).thenReturn(true);
            when(index.rebuild(anyCollection(), any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<StorageSignIndex.RebuildResult> completion =
                    invocation.getArgument(1);
                completion.accept(new StorageSignIndex.RebuildResult(1, 0, 2));
                return true;
            });
            when(index.saveAsync(any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<StorageSignIndex.SaveResult> completion =
                    invocation.getArgument(0);
                completion.accept(new StorageSignIndex.SaveResult(true, 2, 42L, "saved"));
                return true;
            });

            new StorageSignIndexCommand(index).onCommand(
                sender, mock(Command.class), "ssindex", new String[] {"rebuild", "all"});

            verify(sender).sendMessage(contains("rebuild and save complete"));
        } finally {
            MockBukkit.unmock();
        }
    }

    private static void setStaticBoolean(Class<?> type, String fieldName, boolean value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(null, value);
    }
}
