package storagesign.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import storagesign.index.StorageSignIndex;
import storagesign.search.StorageSignQueryService;

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
    void missingItemArgumentsReturnUsageSignal() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("storagesign.search.admin")).thenReturn(true);
        StorageSignIndex index = new StorageSignIndex(null, true);

        boolean handled = new StorageSignSearchCommand(index, new StorageSignQueryService(null, index))
            .onCommand(sender, mock(Command.class), "sssearch", new String[] {"item"});

        assertFalse(handled);
    }
}
