package me.rukon0621.rknpc.nms.packet.interact;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface PacketNpcInteractionCallback {

    void handle(Player player, int entityId, PacketNpcInteractionAction action);
}
