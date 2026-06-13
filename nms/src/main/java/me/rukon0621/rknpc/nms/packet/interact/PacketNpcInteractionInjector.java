package me.rukon0621.rknpc.nms.packet.interact;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

public final class PacketNpcInteractionInjector {

    private static final String HANDLER_NAME = "rknpc_interact";
    private final Set<UUID> injectedPlayers = ConcurrentHashMap.newKeySet();

    public void inject(Player player, IntPredicate npcEntityId, PacketNpcInteractionCallback callback) {
        Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
        if (!injectedPlayers.add(player.getUniqueId())) {
            return;
        }
        channel.eventLoop().execute(() -> {
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                return;
            }
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (msg instanceof ServerboundInteractPacket packet && npcEntityId.test(packet.getEntityId())) {
                        callback.handle(player, packet.getEntityId(), readAction(packet));
                        return;
                    }
                    super.channelRead(ctx, msg);
                }
            });
        });
    }

    public void remove(Player player) {
        injectedPlayers.remove(player.getUniqueId());
        Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
        channel.eventLoop().execute(() -> {
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
        });
    }

    private PacketNpcInteractionAction readAction(ServerboundInteractPacket packet) {
        if (packet.isAttack()) {
            return PacketNpcInteractionAction.ATTACK;
        }
        PacketNpcInteractionAction[] action = {PacketNpcInteractionAction.INTERACT_MAIN_HAND};
        packet.dispatch(new Handler() {
            @Override
            public void onInteraction(@NonNull InteractionHand hand) {
                action[0] = toAction(hand);
            }

            @Override
            public void onInteraction(@NonNull InteractionHand hand, @NonNull Vec3 location) {
                action[0] = toAction(hand);
            }

            @Override
            public void onAttack() {
                action[0] = PacketNpcInteractionAction.ATTACK;
            }
        });
        return action[0];
    }

    private PacketNpcInteractionAction toAction(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND
                ? PacketNpcInteractionAction.INTERACT_OFF_HAND
                : PacketNpcInteractionAction.INTERACT_MAIN_HAND;
    }
}
