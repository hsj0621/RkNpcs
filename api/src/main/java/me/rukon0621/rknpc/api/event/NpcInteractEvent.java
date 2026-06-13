package me.rukon0621.rknpc.api.event;

import me.rukon0621.rknpc.api.npc.Npc;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NpcInteractEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Npc npc;
    private final Action action;
    private final EquipmentSlot hand;

    public NpcInteractEvent(Player player, Npc npc, Action action, EquipmentSlot hand) {
        this.player = player;
        this.npc = npc;
        this.action = action;
        this.hand = hand;
    }

    public Player player() {
        return player;
    }

    public Npc npc() {
        return npc;
    }

    public Action action() {
        return action;
    }

    @Nullable
    public EquipmentSlot hand() {
        return hand;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum Action {
        INTERACT,
        ATTACK
    }
}
