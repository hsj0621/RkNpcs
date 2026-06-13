package me.rukon0621.rknpc.api.npc;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface Npc {

    /**
     * 설정과 명령어에서 사용하는 고정 ID.
     */
    String id();

    Component displayName();

    Location location();

    NpcSkin skin();

    NpcLookMode lookMode();

    Optional<ItemStack> equipment(NpcEquipmentSlot slot);

    /**
     * 현재 이 NPC의 스폰 패킷을 받은 플레이어 UUID 목록.
     */
    Set<UUID> visiblePlayerIds();

    boolean isVisibleTo(Player player);

    /**
     * 해당 플레이어가 이 NPC를 강제로 숨긴 상태인지 확인한다.
     */
    boolean isForcedHidden(UUID playerId);

    /**
     * 해당 플레이어가 이 NPC를 강제로 보이게 한 상태인지 확인한다.
     */
    boolean isForcedVisible(UUID playerId);

    /**
     * 해당 플레이어에게 클라이언트 전용 스폰 패킷을 보낸다.
     */
    void showTo(Player player);

    /**
     * 해당 플레이어에게 클라이언트 전용 제거 패킷을 보낸다.
     */
    void hideFrom(Player player);
}
