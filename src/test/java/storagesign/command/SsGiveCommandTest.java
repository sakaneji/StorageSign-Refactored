package storagesign.command;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class SsGiveCommandTest {

    @Test
    void noPermissionIsRejectedBeforeAnyItemLogicRuns() {
        Player player = mock(Player.class);
        Command command = mock(Command.class);
        when(player.hasPermission("storagesign.give")).thenReturn(false);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);

        boolean handled = new SsGiveCommand().onCommand(
            player, command, "ssgive", new String[] {"STONE", "1"});

        assertTrue(handled);
        verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("permission"));
    }
}
