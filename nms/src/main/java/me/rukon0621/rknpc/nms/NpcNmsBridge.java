package me.rukon0621.rknpc.nms;

import me.rukon0621.rknpc.nms.packet.PacketNpcRenderer;
import me.rukon0621.rknpc.nms.packet.PacketNpcVisibilityController;
import me.rukon0621.rknpc.nms.packet.interact.PacketNpcInteractionInjector;
import me.rukon0621.rknpc.nms.skin.NpcSkinResolver;

import java.net.http.HttpClient;
import java.nio.file.Path;

/**
 * CORE 모듈이 NMS 패킷 기능에 접근하는 작은 진입점.
 */
public final class NpcNmsBridge {

    private final PacketNpcRenderer renderer;
    private final PacketNpcVisibilityController visibilityController;
    private final PacketNpcInteractionInjector interactionInjector;
    private final NpcSkinResolver skinResolver;

    public NpcNmsBridge(Path pluginFolder) {
        this.renderer = new PacketNpcRenderer();
        this.visibilityController = new PacketNpcVisibilityController(renderer);
        this.interactionInjector = new PacketNpcInteractionInjector();
        this.skinResolver = new NpcSkinResolver(HttpClient.newHttpClient(), pluginFolder);
    }

    public PacketNpcRenderer renderer() {
        return renderer;
    }

    public PacketNpcVisibilityController visibilityController() {
        return visibilityController;
    }

    public PacketNpcInteractionInjector interactionInjector() {
        return interactionInjector;
    }

    public NpcSkinResolver skinResolver() {
        return skinResolver;
    }
}
