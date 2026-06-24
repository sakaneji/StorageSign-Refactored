package storagesign.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;
import storagesign.ConfigLoader;
import storagesign.StorageSignPlugin;
import storagesign.index.IndexedStorageSign;
import storagesign.index.StorageSignIndex;
import storagesign.index.StorageSignPosition;
import storagesign.search.StorageSignQueryService;
import storagesign.search.StorageSignSearchResult;

class StorageSignSearchCommandTest {
    @Test
    void disabledIndexIsExplainedWithoutStartingSearch() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("storagesign.search.admin")).thenReturn(true);
        StorageSignIndex index = new StorageSignIndex(null, false);

        new StorageSignSearchCommand(index, new StorageSignQueryService(null, index)).onCommand(
            sender, mock(Command.class), "sssearch", new String[] {"item", "STONE"});

        verify(sender).sendMessage(contains("disabled"));
    }

    @Test
    void permissionDeniedReturnsWithoutStartingSearch() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("storagesign.search.admin")).thenReturn(false);
        StorageSignIndex index = new StorageSignIndex(null, true);

        boolean handled = new StorageSignSearchCommand(index, new StorageSignQueryService(null, index))
            .onCommand(sender, mock(Command.class), "sssearch", new String[] {"item", "STONE"});

        assertTrue(handled);
        verify(sender).sendMessage(contains("permission"));
    }

    @Test
    void parseRejectsMissingPageValue() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            CommandSender sender = mock(CommandSender.class);
            StorageSignSearchCommand command = new StorageSignSearchCommand(
                new StorageSignIndex(null, true), new StorageSignQueryService(null, new StorageSignIndex(null, true)));
            Method parse = StorageSignSearchCommand.class.getDeclaredMethod(
                "parse", CommandSender.class, String[].class);
            parse.setAccessible(true);

            Object parsed = parse.invoke(command, sender, new String[] {"item", "STONE", "--page"});

            assertTrue(parsed == null);
            verify(sender).sendMessage(contains("--page requires a number"));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void missingItemArgumentsReturnUsageSignal() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("storagesign.search.admin")).thenReturn(true);
        StorageSignIndex index = new StorageSignIndex(null, true);

        boolean handled = new StorageSignSearchCommand(index, new StorageSignQueryService(null, index))
            .onCommand(sender, mock(Command.class), "sssearch", new String[] {"item"});

        assertFalse(handled);
    }

    @Test
    void unknownTopLevelCommandReturnsUsageSignal() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("storagesign.search.admin")).thenReturn(true);
        StorageSignIndex index = new StorageSignIndex(null, true);

        boolean handled = new StorageSignSearchCommand(index, new StorageSignQueryService(null, index))
            .onCommand(sender, mock(Command.class), "sssearch", new String[] {"wrong", "STONE"});

        assertFalse(handled);
    }

    @Test
    void saturatedSearchReportsCapacityLimitWithoutStartingAsyncWork() throws Exception {
        ServerMock server = MockBukkit.mock();
        StorageSignPlugin plugin = MockBukkit.load(StorageSignPlugin.class);
        try {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("storagesign.search.admin")).thenReturn(true);
            StorageSignIndex index = plugin.getStorageSignIndex();
            StorageSignQueryService queries = new StorageSignQueryService(plugin, index);

            Field maxConcurrent = ConfigLoader.class.getDeclaredField("adminSearchMaxConcurrent");
            maxConcurrent.setAccessible(true);
            int originalMaximum = maxConcurrent.getInt(null);
            Field activeField = StorageSignQueryService.class.getDeclaredField("active");
            activeField.setAccessible(true);
            AtomicInteger active = (AtomicInteger) activeField.get(queries);
            int originalActive = active.get();
            try {
                maxConcurrent.setInt(null, 1);
                active.set(1);

                boolean handled = new StorageSignSearchCommand(index, queries).onCommand(
                    sender, mock(Command.class), "sssearch", new String[] {"item", "STONE"});

                assertTrue(handled);
                verify(sender).sendMessage(contains("Searching"));
                verify(sender).sendMessage(contains("Too many"));
                assertEquals(1, active.get());
            } finally {
                active.set(originalActive);
                maxConcurrent.setInt(null, originalMaximum);
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void showDistinguishesLoadedAndCachedResultsAndRejectsMissingPage() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            CommandSender sender = mock(CommandSender.class);
            var world = server.addSimpleWorld("search-show");
            world.getChunkAt(0, 0).load();
            IndexedStorageSign loaded = new IndexedStorageSign(
                new StorageSignPosition(world.getUID(), 1, 64, 2), "STONE", 12, 1L);
            IndexedStorageSign cached = new IndexedStorageSign(
                new StorageSignPosition(UUID.randomUUID(), 3, 70, 4), "STONE", 8, 2L);
            StorageSignSearchResult result = new StorageSignSearchResult(List.of(loaded, cached), 20);
            StorageSignSearchCommand command = new StorageSignSearchCommand(
                new StorageSignIndex(null, true), new StorageSignQueryService(null, new StorageSignIndex(null, true)));
            Field pageSize = ConfigLoader.class.getDeclaredField("adminSearchPageSize");
            pageSize.setAccessible(true);
            int originalPageSize = pageSize.getInt(null);
            pageSize.setInt(null, 1);
            try {
                Method show = StorageSignSearchCommand.class.getDeclaredMethod(
                    "show", CommandSender.class, String.class, int.class, StorageSignSearchResult.class);
                show.setAccessible(true);
                try (MockedStatic<org.bukkit.Bukkit> mocked = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
                    mocked.when(() -> org.bukkit.Bukkit.getWorld(world.getUID())).thenReturn(world);
                    mocked.when(() -> org.bukkit.Bukkit.getWorld(cached.position().worldId())).thenReturn(null);

                    show.invoke(command, sender, "STONE", 1, result);
                    verify(sender).sendMessage(contains("matches=2"));
                    verify(sender).sendMessage(contains("loaded"));

                    show.invoke(command, sender, "STONE", 2, result);
                    verify(sender).sendMessage(contains("cached"));

                    show.invoke(command, sender, "STONE", 3, result);
                    verify(sender).sendMessage(contains("does not exist"));
                }
            } finally {
                pageSize.setInt(null, originalPageSize);
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void parseRecognizesContainsWorldAndPageOptions() throws Exception {
        ServerMock server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("search-parse");
            CommandSender sender = mock(CommandSender.class);
            StorageSignSearchCommand command = new StorageSignSearchCommand(
                new StorageSignIndex(null, true), new StorageSignQueryService(null, new StorageSignIndex(null, true)));
            Method parse = StorageSignSearchCommand.class.getDeclaredMethod(
                "parse", CommandSender.class, String[].class);
            parse.setAccessible(true);

            Object parsed = parse.invoke(command, sender, new String[] {
                "item", "STONE", "--contains", "--world", world.getName(), "--page", "2"});

            Method contains = parsed.getClass().getDeclaredMethod("contains");
            Method worldId = parsed.getClass().getDeclaredMethod("worldId");
            Method page = parsed.getClass().getDeclaredMethod("page");
            assertTrue((Boolean) contains.invoke(parsed));
            assertEquals(world.getUID(), worldId.invoke(parsed));
            assertEquals(2, page.invoke(parsed));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void searchFailureIsReportedToSender() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("storagesign.search.admin")).thenReturn(true);
        StorageSignIndex index = new StorageSignIndex(null, true);
        StorageSignQueryService queries = mock(StorageSignQueryService.class);
        when(queries.search(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<Throwable> failure = invocation.getArgument(2);
                failure.accept(new RuntimeException("boom"));
                return true;
            });

        boolean handled = new StorageSignSearchCommand(index, queries)
            .onCommand(sender, mock(Command.class), "sssearch", new String[] {"item", "STONE"});

        assertTrue(handled);
        verify(sender).sendMessage(contains("search failed"));
    }
}
