package me.rukon0621.rknpc.core.model;

import com.mojang.authlib.properties.Property;
import lombok.Getter;
import lombok.Setter;
import me.rukon0621.rknpc.api.npc.Npc;
import me.rukon0621.rknpc.api.npc.NpcEquipmentSlot;
import me.rukon0621.rknpc.api.npc.NpcLookMode;
import me.rukon0621.rknpc.api.npc.NpcSkin;
import me.rukon0621.rknpc.api.npc.NpcSkinType;
import me.rukon0621.rknpc.nms.packet.PacketNpc;
import me.rukon0621.rknpc.nms.packet.PacketNpcFactory;
import me.rukon0621.rknpc.nms.packet.PacketNpcVisibilityController;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CoreNpc implements Npc {

    private final String id;
    @Setter
    private volatile Component displayName;
    private final Location location;
    private final Map<NpcEquipmentSlot, ItemStack> equipment = new EnumMap<>(NpcEquipmentSlot.class);
    private final Map<UUID, VisibilityOverride> visibilityOverrides = new ConcurrentHashMap<>();
    private final PacketNpcVisibilityController visibilityController;
    private final PacketNpcFactory packetNpcFactory;
    private volatile NpcSkin skin;
    @Setter
    private volatile NpcLookMode lookMode;
    private volatile PacketNpc packetNpc;
    @Getter
    @Setter
    private volatile boolean spawnReady;

    public CoreNpc(
            String id,
            Component displayName,
            Location location,
            NpcSkin skin,
            NpcLookMode lookMode,
            Map<NpcEquipmentSlot, ItemStack> equipment,
            PacketNpcFactory packetNpcFactory,
            PacketNpcVisibilityController visibilityController
    ) {
        this.id = id;
        this.displayName = displayName;
        this.location = location.clone();
        this.skin = skin;
        this.lookMode = lookMode;
        this.spawnReady = skin.type() == NpcSkinType.MIRROR;
        if (equipment != null) {
            equipment.forEach(this::setEquipment);
        }
        this.packetNpcFactory = packetNpcFactory;
        this.visibilityController = visibilityController;
        rebuildPacket(null);
    }

    public PacketNpc packetNpc() {
        return packetNpc;
    }

    public void rebuildPacket(Property textures) {
        packetNpc = packetNpc == null
                ? packetNpcFactory.create(displayName, location, equipment, textures)
                : packetNpcFactory.rebuild(packetNpc, displayName, location, equipment, textures);
    }

    public void setSkin(NpcSkin skin) {
        this.skin = skin;
        this.spawnReady = skin.type() == NpcSkinType.MIRROR;
    }

    public void setEquipment(NpcEquipmentSlot slot, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            equipment.remove(slot);
            return;
        }
        equipment.put(slot, itemStack.clone());
    }

    public void setVisibilityOverride(UUID playerId, VisibilityOverride override) {
        if (override == VisibilityOverride.AUTO) {
            visibilityOverrides.remove(playerId);
            return;
        }
        visibilityOverrides.put(playerId, override);
    }

    public VisibilityOverride visibilityOverride(UUID playerId) {
        return visibilityOverrides.getOrDefault(playerId, VisibilityOverride.AUTO);
    }

    public void clearPlayerCache(UUID playerId) {
        visibilityOverrides.remove(playerId);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Component displayName() {
        return displayName;
    }

    @Override
    public Location location() {
        return location.clone();
    }

    @Override
    public NpcSkin skin() {
        return skin;
    }

    @Override
    public NpcLookMode lookMode() {
        return lookMode;
    }

    @Override
    public Optional<ItemStack> equipment(NpcEquipmentSlot slot) {
        ItemStack itemStack = equipment.get(slot);
        return itemStack == null ? Optional.empty() : Optional.of(itemStack.clone());
    }

    public Map<NpcEquipmentSlot, ItemStack> equipmentCopy() {
        EnumMap<NpcEquipmentSlot, ItemStack> copy = new EnumMap<>(NpcEquipmentSlot.class);
        equipment.forEach((slot, item) -> copy.put(slot, item.clone()));
        return copy;
    }

    @Override
    public Set<UUID> visiblePlayerIds() {
        return visibilityController.visiblePlayerIds(packetNpc);
    }

    @Override
    public boolean isVisibleTo(Player player) {
        return visibilityController.isVisible(packetNpc, player.getUniqueId());
    }

    @Override
    public boolean isForcedHidden(UUID playerId) {
        return visibilityOverride(playerId) == VisibilityOverride.FORCE_HIDE;
    }

    @Override
    public boolean isForcedVisible(UUID playerId) {
        return visibilityOverride(playerId) == VisibilityOverride.FORCE_SHOW;
    }

    @Override
    public void showTo(Player player) {
        if (!spawnReady) {
            return;
        }
        visibilityController.show(packetNpc, player);
    }

    @Override
    public void hideFrom(Player player) {
        visibilityController.hide(packetNpc, player);
    }
}
