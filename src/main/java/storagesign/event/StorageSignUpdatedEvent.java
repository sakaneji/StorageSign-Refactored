package storagesign.event;

import org.bukkit.block.Sign;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired after a physical StorageSign has been successfully written. */
public final class StorageSignUpdatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Sign sign;
    private final String identifier;
    private final int amount;
    private final boolean registered;

    public StorageSignUpdatedEvent(Sign sign, String identifier, int amount, boolean registered) {
        this.sign = sign;
        this.identifier = identifier;
        this.amount = amount;
        this.registered = registered;
    }

    public Sign getSign() { return sign; }
    public String getIdentifier() { return identifier; }
    public int getAmount() { return amount; }
    public boolean isRegistered() { return registered; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
