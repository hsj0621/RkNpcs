package me.rukon0621.rknpc.api;

import me.rukon0621.rknpc.api.npc.Npc;
import me.rukon0621.rknpc.api.npc.NpcCreateRequest;
import me.rukon0621.rknpc.api.npc.NpcEquipmentSlot;
import me.rukon0621.rknpc.api.npc.NpcLookMode;
import me.rukon0621.rknpc.api.npc.NpcSkin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

public interface NpcManager {

    /**
     * 패킷 기반 NPC를 생성하고 등록한다.
     */
    Npc createNpc(NpcCreateRequest request);

    /**
     * NPC를 제거하고 클라이언트에 보낸 표시 상태를 정리한다.
     */
    boolean removeNpc(String id);

    Optional<Npc> getNpc(String id);

    Collection<Npc> getNpcs();


    /**
     * 특정 플레이어에 대한 표시 여부를 강제로 설정한다.
     */
    void setVisible(String id, Player player, boolean visible);

    /**
     * 특정 플레이어에 대한 강제 표시 설정을 제거하고 거리 기반 판단으로 되돌린다.
     */
    void clearVisibilityOverride(String id, UUID playerId);

    /**
     * 플레이어가 강제로 숨긴 NPC ID 목록을 반환한다.
     */
    Set<String> getForcedHiddenNpcIds(UUID playerId);

    /**
     * 플레이어가 강제로 보이게 한 NPC ID 목록을 반환한다.
     */
    Set<String> getForcedVisibleNpcIds(UUID uuid);

    /**
     * 실제 서버 엔티티 없이 클라이언트에 보이는 장비만 갱신한다.
     */
    void setEquipment(String id, NpcEquipmentSlot slot, ItemStack itemStack);

    /**
     * 이후 스폰 패킷에 사용할 스킨 소스를 변경한다.
     */
    void setSkin(String id, NpcSkin skin);

    /**
     * 플레이어 바라보기 회전 기능을 켜거나 끈다.
     */
    void setLookMode(String id, NpcLookMode lookMode);

    /**
     * 구현체가 관리하는 설정과 데이터를 다시 불러온다.
     */
    void reload(CommandSender sender);
}
