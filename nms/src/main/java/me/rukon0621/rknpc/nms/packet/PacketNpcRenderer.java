package me.rukon0621.rknpc.nms.packet;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import com.google.common.collect.ImmutableMultimap;
import io.netty.buffer.Unpooled;
import io.papermc.paper.adventure.PaperAdventure;
import me.rukon0621.rknpc.api.npc.NpcEquipmentSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 플레이어 타입 NPC를 보이게 하는 클라이언트 패킷을 직접 전송한다.
 */
public final class PacketNpcRenderer {

    /**
     * 1.21.10은 Entity 생성자만 공개하므로 raw entity id 패킷은 buffer 생성자를 사용한다.
     */
    private final Constructor<ClientboundRotateHeadPacket> rotateHeadConstructor;

    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> PLAYER_INFO_ACTIONS = EnumSet.of(
            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT
    );

    public PacketNpcRenderer() {
        try {
            rotateHeadConstructor = ClientboundRotateHeadPacket.class.getDeclaredConstructor(FriendlyByteBuf.class);
            rotateHeadConstructor.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize rotate-head packet constructor.", e);
        }
    }

    public void spawn(Player viewer, PacketNpc npc) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(npc, "npc");

        Location location = npc.location();
        GameProfile profile = createProfile(viewer, npc);
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                npc.profileId(),
                profile,
                false,
                0,
                GameType.DEFAULT_MODE,
                PaperAdventure.asVanilla(npc.displayName()),
                true,
                0,
                null
        );

        // 플레이어 엔티티는 add-entity 패킷 전에 임시 tab-list entry가 필요하다.
        send(viewer, new ClientboundPlayerInfoUpdatePacket(PLAYER_INFO_ACTIONS, entry));
        send(viewer, new ClientboundAddEntityPacket(
                npc.entityId(),
                npc.profileId(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getPitch(),
                location.getYaw(),
                EntityType.PLAYER,
                0,
                Vec3.ZERO,
                location.getYaw()
        ));
        List<SynchedEntityData.DataValue<?>> metadata = List.of(
                SynchedEntityData.DataValue.create(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 127)
        );
        ClientboundSetEntityDataPacket metadataPacket = new ClientboundSetEntityDataPacket(npc.entityId(), metadata);
        ((CraftPlayer) viewer).getHandle().connection.send(metadataPacket);
        sendEquipment(viewer, npc.entityId(), npc.equipment());
        rotate(viewer, npc.entityId(), location.getYaw(), location.getPitch(), true);
    }

    public void removeFromPlayerList(Player viewer, PacketNpc npc) {
        send(viewer, new ClientboundPlayerInfoRemovePacket(List.of(npc.profileId())));
    }

    public void destroy(Player viewer, PacketNpc npc) {
        send(viewer, new ClientboundRemoveEntitiesPacket(npc.entityId()));
        removeFromPlayerList(viewer, npc);
    }

    public void equip(Player viewer, PacketNpc npc, NpcEquipmentSlot slot, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            send(viewer, new ClientboundSetEquipmentPacket(npc.entityId(), List.of(Pair.of(toNmsSlot(slot), net.minecraft.world.item.ItemStack.EMPTY))));
            return;
        }
        sendEquipment(viewer, npc.entityId(), Map.of(slot, itemStack));
    }

    public void rotateToward(Player viewer, PacketNpc npc, Location target) {
        Location source = npc.location();
        double dx = target.getX() - source.getX();
        double dy = target.getY() - (source.getY() + 1.62D);
        double dz = target.getZ() - source.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        rotate(viewer, npc.entityId(), yaw, pitch, true);
    }

    public void rotate(Player viewer, int entityId, float yaw, float pitch, boolean onGround) {
        byte packedYaw = packRotation(yaw);
        byte packedPitch = packRotation(pitch);
        send(viewer, rotateHeadPacket(entityId, packedYaw));
        send(viewer, new ClientboundMoveEntityPacket.Rot(entityId, packedYaw, packedPitch, onGround));
    }

    private GameProfile createProfile(Player viewer, PacketNpc npc) {
        Property textures = npc.textures();
        if (textures == null) {
            // MIRROR 모드에서는 저장된 textures가 없으므로 보는 플레이어의 스킨을 복사한다.
            textures = ((CraftPlayer) viewer).getProfile().properties().get("textures").stream().findFirst().orElse(null);
        }
        PropertyMap properties = textures == null
                ? PropertyMap.EMPTY
                : new PropertyMap(ImmutableMultimap.of("textures", textures));
        return new GameProfile(npc.profileId(), plainName(npc.displayName()), properties);
    }

    private void sendEquipment(Player viewer, int entityId, Map<NpcEquipmentSlot, ItemStack> equipment) {
        List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> slots = new ArrayList<>(equipment.size());
        equipment.forEach((slot, itemStack) -> slots.add(Pair.of(toNmsSlot(slot), CraftItemStack.asNMSCopy(itemStack))));
        if (!slots.isEmpty()) {
            send(viewer, new ClientboundSetEquipmentPacket(entityId, slots));
        }
    }

    private EquipmentSlot toNmsSlot(NpcEquipmentSlot slot) {
        return switch (slot) {
            case HAND -> EquipmentSlot.MAINHAND;
            case OFF_HAND -> EquipmentSlot.OFFHAND;
            case HELMET -> EquipmentSlot.HEAD;
            case CHESTPLATE -> EquipmentSlot.CHEST;
            case LEGGINGS -> EquipmentSlot.LEGS;
            case BOOTS -> EquipmentSlot.FEET;
        };
    }

    private byte packRotation(float degrees) {
        return (byte) ((int) (degrees * 256.0F / 360.0F));
    }

    private ClientboundRotateHeadPacket rotateHeadPacket(int entityId, byte yHeadRot) {
        // 패킷 구조는 VarInt entity id + 압축된 head yaw 값이다.
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(5));
        buffer.writeVarInt(entityId);
        buffer.writeByte(yHeadRot);
        try {
            return rotateHeadConstructor.newInstance(buffer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create rotate-head packet.", e);
        } finally {
            buffer.release();
        }
    }

    private String plainName(net.kyori.adventure.text.Component component) {
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
        if (text.isBlank()) {
            return "NPC";
        }
        return text.length() > 16 ? text.substring(0, 16) : text;
    }

    private void send(Player viewer, Packet<?> packet) {
        ServerPlayer handle = ((CraftPlayer) viewer).getHandle();
        handle.connection.send(packet);
    }
}
