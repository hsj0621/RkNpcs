package me.rukon0621.rknpc.nms.packet;

import com.mojang.authlib.properties.Property;
import me.rukon0621.rknpc.api.npc.NpcEquipmentSlot;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 서버 엔티티 시스템을 건드리지 않고 가짜 엔티티 ID를 할당한다.
 */
public final class PacketNpcFactory {

    private final AtomicInteger entityIdCounter;

    public PacketNpcFactory() {
        this(2_000_000);
    }

    public PacketNpcFactory(int initialEntityId) {
        this.entityIdCounter = new AtomicInteger(initialEntityId);
    }

    public PacketNpc create(
            Component displayName,
            Location location,
            Map<NpcEquipmentSlot, ItemStack> equipment,
            Property textures
    ) {
        return new PacketNpc(
                entityIdCounter.getAndIncrement(),
                UUID.randomUUID(),
                displayName,
                location,
                equipment,
                textures
        );
    }

    public PacketNpc rebuild(
            PacketNpc current,
            Component displayName,
            Location location,
            Map<NpcEquipmentSlot, ItemStack> equipment,
            Property textures
    ) {
        return new PacketNpc(
                current.entityId(),
                current.profileId(),
                displayName,
                location,
                equipment,
                textures
        );
    }
}
