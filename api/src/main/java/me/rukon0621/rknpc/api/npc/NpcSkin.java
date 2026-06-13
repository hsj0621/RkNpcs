package me.rukon0621.rknpc.api.npc;

import java.util.Objects;

/**
 * 스킨 소스 정의. MIRROR는 스폰 시점에 보는 플레이어의 프로필을 사용한다.
 */
public record NpcSkin(
        NpcSkinType type,
        String value
) {

    public static NpcSkin name(String name) {
        return new NpcSkin(NpcSkinType.NAME, name);
    }

    public static NpcSkin url(String url) {
        return new NpcSkin(NpcSkinType.URL, url);
    }

    public static NpcSkin image(String fileName) {
        return new NpcSkin(NpcSkinType.IMAGE, fileName);
    }

    public static NpcSkin mirror() {
        return new NpcSkin(NpcSkinType.MIRROR, "");
    }

    public NpcSkin {
        Objects.requireNonNull(type, "type");
        value = value == null ? "" : value;
    }
}
