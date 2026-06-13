package me.rukon0621.rknpc.nms.packet;

import com.mojang.authlib.properties.Property;
import me.rukon0621.rknpc.api.npc.NpcEquipmentSlot;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 패킷 생성에 사용하는 클라이언트 전용 NPC 상태.
 */
public final class PacketNpc {

    private final int entityId;
    private final UUID profileId;
    private final Component displayName;
    private final Location location;
    private final Map<NpcEquipmentSlot, ItemStack> equipment;
    private final Property textures;

    public PacketNpc(
            int entityId,
            UUID profileId,
            Component displayName,
            Location location,
            Map<NpcEquipmentSlot, ItemStack> equipment,
            Property textures
    ) {
        this.entityId = entityId;
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.location = Objects.requireNonNull(location, "location").clone();
        this.equipment = new EnumMap<>(NpcEquipmentSlot.class);
        if (equipment != null) {
            equipment.forEach((slot, item) -> {
                if (slot != null && item != null && !item.getType().isAir()) {
                    this.equipment.put(slot, item.clone());
                }
            });
        }
        this.textures = textures;
    }

    public int entityId() {
        return entityId;
    }

    public UUID profileId() {
        return profileId;
    }

    public Component displayName() {
        return displayName;
    }

    public Location location() {
        return location.clone();
    }

    public Map<NpcEquipmentSlot, ItemStack> equipment() {
        // 비동기 가시성 로직이 원본 장비 상태를 바꾸지 못하도록 복사본을 반환한다.
        EnumMap<NpcEquipmentSlot, ItemStack> copy = new EnumMap<>(NpcEquipmentSlot.class);
        equipment.forEach((slot, item) -> copy.put(slot, item.clone()));
        return copy;
    }

    public Property textures() {
        return textures;
    }
}
