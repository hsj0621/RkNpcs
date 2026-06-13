package me.rukon0621.rknpc.nms.packet;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * thread-safe set으로 플레이어별 패킷 표시 상태를 추적한다.
 */
public final class PacketNpcVisibilityController {

    private final PacketNpcRenderer renderer;
    private final Map<Integer, Set<UUID>> visiblePlayersByNpc = new ConcurrentHashMap<>();
    private final Map<Integer, Set<UUID>> hiddenPlayersByNpc = new ConcurrentHashMap<>();

    public PacketNpcVisibilityController(PacketNpcRenderer renderer) {
        this.renderer = renderer;
    }

    public boolean isVisible(PacketNpc npc, UUID playerId) {
        return visiblePlayersByNpc.getOrDefault(npc.entityId(), Set.of()).contains(playerId);
    }

    public Set<UUID> visiblePlayerIds(PacketNpc npc) {
        return Set.copyOf(visiblePlayersByNpc.getOrDefault(npc.entityId(), Set.of()));
    }

    public void setVisibility(PacketNpc npc, Player player, boolean visible) {
        Set<UUID> hiddenPlayers = hiddenPlayersByNpc.computeIfAbsent(npc.entityId(), ignored -> ConcurrentHashMap.newKeySet());
        if (visible) {
            hiddenPlayers.remove(player.getUniqueId());
            return;
        }
        hiddenPlayers.add(player.getUniqueId());
        hide(npc, player);
    }

    public void refresh(PacketNpc npc, Player player, Location playerLocation, double visibleDistanceSquared) {
        if (hiddenPlayersByNpc.getOrDefault(npc.entityId(), Set.of()).contains(player.getUniqueId())) {
            hide(npc, player);
            return;
        }
        Location npcLocation = npc.location();
        // 비동기 로직에서 호출할 때는 호출자가 위치 스냅샷을 넘겨야 한다.
        boolean sameWorld = npcLocation.getWorld() != null && npcLocation.getWorld().equals(playerLocation.getWorld());
        boolean inRange = sameWorld && npcLocation.distanceSquared(playerLocation) <= visibleDistanceSquared;
        if (inRange) {
            show(npc, player);
        } else {
            hide(npc, player);
        }
    }

    public void show(PacketNpc npc, Player player) {
        Set<UUID> visiblePlayers = visiblePlayersByNpc.computeIfAbsent(npc.entityId(), ignored -> ConcurrentHashMap.newKeySet());
        if (visiblePlayers.add(player.getUniqueId())) {
            renderer.spawn(player, npc);
        }
    }

    public void hide(PacketNpc npc, Player player) {
        Set<UUID> visiblePlayers = visiblePlayersByNpc.get(npc.entityId());
        if (visiblePlayers != null && visiblePlayers.remove(player.getUniqueId())) {
            renderer.destroy(player, npc);
        }
    }

    public void remove(PacketNpc npc) {
        visiblePlayersByNpc.remove(npc.entityId());
        hiddenPlayersByNpc.remove(npc.entityId());
    }

    public void forgetPlayer(UUID playerId) {
        visiblePlayersByNpc.values().forEach(players -> players.remove(playerId));
        hiddenPlayersByNpc.values().forEach(players -> players.remove(playerId));
    }
}
