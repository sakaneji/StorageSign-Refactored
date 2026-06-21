package storagesign.command;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
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
}
