package me.rukon0621.rknpc.api.npc;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;

import java.util.Objects;

/**
 * 명령어 또는 설정에서 NPC를 생성할 때 사용하는 불변 입력값.
 */
public record NpcCreateRequest(
        String id,
        Component displayName,
        Location location,
        NpcSkin skin,
        NpcLookMode lookMode
) {

    public NpcCreateRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(skin, "skin");
        Objects.requireNonNull(lookMode, "lookMode");
    }
}
