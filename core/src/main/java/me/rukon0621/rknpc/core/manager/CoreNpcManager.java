package me.rukon0621.rknpc.core.manager;

import com.mojang.authlib.properties.Property;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.rukon0621.rknpc.api.NpcManager;
import me.rukon0621.rknpc.api.event.NpcInteractEvent;
import me.rukon0621.rknpc.api.npc.Npc;
import me.rukon0621.rknpc.api.npc.NpcCreateRequest;
import me.rukon0621.rknpc.api.npc.NpcEquipmentSlot;
import me.rukon0621.rknpc.api.npc.NpcLookMode;
import me.rukon0621.rknpc.api.npc.NpcSkin;
import me.rukon0621.rknpc.api.npc.NpcSkinType;
import me.rukon0621.rknpc.core.RkNpcPlugin;
import me.rukon0621.rknpc.core.model.CoreNpc;
import me.rukon0621.rknpc.core.model.VisibilityOverride;
import me.rukon0621.rknpc.core.util.Components;
import me.rukon0621.rknpc.nms.NpcNmsBridge;
import me.rukon0621.rknpc.nms.packet.PacketNpcFactory;
import me.rukon0621.rknpc.nms.packet.interact.PacketNpcInteractionAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class CoreNpcManager implements NpcManager, Listener, AutoCloseable {

    private static final long INTERACTION_DEBOUNCE_NANOS = TimeUnit.MILLISECONDS.toNanos(50L);

    private final RkNpcPlugin plugin;
    private final NpcNmsBridge nmsBridge;
    private final PacketNpcFactory packetNpcFactory = new PacketNpcFactory();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, CoreNpc> npcs = new ConcurrentHashMap<>();
    private final Map<InteractionKey, Long> recentInteractions = new ConcurrentHashMap<>();
    private final File npcFile;
    private volatile int visibleDistance;
    private volatile int trackingDistance;
    private volatile int lookAtInterval;
    private volatile String defaultSkinName;
    private ScheduledTask visibilityTask;

    public CoreNpcManager(RkNpcPlugin plugin) {
        this.plugin = plugin;
        this.nmsBridge = new NpcNmsBridge(plugin.getDataFolder().toPath());
        this.npcFile = new File(plugin.getDataFolder(), "npcs.yml");
    }

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            injectInteraction(player);
        }
        long periodMillis = Math.max(1, lookAtInterval) * 50L;
        visibilityTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> refreshVisibility(),
                periodMillis,
                periodMillis,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void close() {
        if (visibilityTask != null) {
            visibilityTask.cancel();
        }
        for (CoreNpc npc : npcs.values()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                npc.hideFrom(player);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            nmsBridge.interactionInjector().remove(player);
        }
    }

    @Override
    public Npc createNpc(NpcCreateRequest request) {
        CoreNpc npc = new CoreNpc(
                request.id(),
                request.displayName(),
                request.location(),
                request.skin(),
                request.lookMode(),
                Map.of(),
                packetNpcFactory,
                nmsBridge.visibilityController()
        );
        npcs.put(request.id().toLowerCase(Locale.ROOT), npc);
        resolveSkin(npc);
        saveNpcs();
        return npc;
    }

    @Override
    public boolean removeNpc(String id) {
        CoreNpc npc = npcs.remove(normalize(id));
        if (npc == null) {
            return false;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            npc.hideFrom(player);
        }
        recentInteractions.keySet().removeIf(key -> key.entityId() == npc.packetNpc().entityId());
        nmsBridge.visibilityController().remove(npc.packetNpc());
        saveNpcs();
        return true;
    }

    @Override
    public Optional<Npc> getNpc(String id) {
        return Optional.ofNullable(npcs.get(normalize(id)));
    }

    public Optional<CoreNpc> getCoreNpc(String id) {
        return Optional.ofNullable(npcs.get(normalize(id)));
    }

    @Override
    public Collection<Npc> getNpcs() {
        return Collections.unmodifiableCollection(new ArrayList<>(npcs.values()));
    }

    public Collection<CoreNpc> getCoreNpcs() {
        return Collections.unmodifiableCollection(new ArrayList<>(npcs.values()));
    }

    @Override
    public void setVisible(String id, Player player, boolean visible) {
        CoreNpc npc = npcs.get(normalize(id));
        if (npc == null) {
            return;
        }
        npc.setVisibilityOverride(player.getUniqueId(), visible ? VisibilityOverride.FORCE_SHOW : VisibilityOverride.FORCE_HIDE);
        if (visible) {
            npc.showTo(player);
        } else {
            npc.hideFrom(player);
        }
    }

    @Override
    public void clearVisibilityOverride(String id, UUID playerId) {
        CoreNpc npc = npcs.get(normalize(id));
        if (npc != null) {
            npc.setVisibilityOverride(playerId, VisibilityOverride.AUTO);
        }
    }

    @Override
    public Set<String> getForcedHiddenNpcIds(UUID playerId) {
        Set<String> hidden = new LinkedHashSet<>();
        for (CoreNpc npc : npcs.values()) {
            if (npc.isForcedHidden(playerId)) {
                hidden.add(npc.id());
            }
        }
        return Collections.unmodifiableSet(hidden);
    }

    @Override
    public Set<String> getForcedVisibleNpcIds(UUID uuid) {
        Set<String> visible = new LinkedHashSet<>();
        for (CoreNpc npc : npcs.values()) {
            if (npc.isForcedVisible(uuid)) {
                visible.add(npc.id());
            }
        }
        return Collections.unmodifiableSet(visible);
    }

    @Override
    public void setEquipment(String id, NpcEquipmentSlot slot, ItemStack itemStack) {
        CoreNpc npc = npcs.get(normalize(id));
        if (npc == null) {
            return;
        }
        npc.setEquipment(slot, itemStack);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (npc.isVisibleTo(player)) {
                nmsBridge.renderer().equip(player, npc.packetNpc(), slot, itemStack);
            }
        }
        saveNpcs();
    }

    @Override
    public void setSkin(String id, NpcSkin skin) {
        CoreNpc npc = npcs.get(normalize(id));
        if (npc == null) {
            return;
        }
        hideVisibleViewers(npc);
        npc.setSkin(skin);
        resolveSkin(npc);
        saveNpcs();
    }

    public CompletableFuture<SkinDownloadResult> downloadAndApplySkin(String id, String fileName, String url) {
        if (!npcs.containsKey(normalize(id))) {
            return CompletableFuture.completedFuture(SkinDownloadResult.NPC_NOT_FOUND);
        }
        if (!isSafePngFileName(fileName)) {
            return CompletableFuture.completedFuture(SkinDownloadResult.INVALID_FILE_NAME);
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(SkinDownloadResult.INVALID_URL);
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            return CompletableFuture.completedFuture(SkinDownloadResult.INVALID_URL);
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return SkinDownloadResult.DOWNLOAD_FAILED;
                    }
                    try {
                        Path skinDirectory = plugin.getDataFolder().toPath().resolve("skins");
                        Files.createDirectories(skinDirectory);
                        Files.write(skinDirectory.resolve(fileName), response.body());
                    } catch (IOException e) {
                        return SkinDownloadResult.WRITE_FAILED;
                    }
                    Bukkit.getGlobalRegionScheduler().execute(plugin, () -> setSkin(id, NpcSkin.image(fileName)));
                    return SkinDownloadResult.SUCCESS;
                })
                .exceptionally(throwable -> SkinDownloadResult.DOWNLOAD_FAILED);
    }

    private boolean isSafePngFileName(String fileName) {
        return fileName.endsWith(".png")
                && !fileName.contains("/")
                && !fileName.contains("\\")
                && !fileName.contains("..")
                && !fileName.isBlank();
    }

    public void setDisplayName(String id, Component displayName) {
        CoreNpc npc = npcs.get(normalize(id));
        if (npc == null) {
            return;
        }
        hideVisibleViewers(npc);
        npc.setDisplayName(displayName);
        npc.rebuildPacket(npc.packetNpc().textures());
        refreshVisibility();
        saveNpcs();
    }

    @Override
    public void setLookMode(String id, NpcLookMode lookMode) {
        CoreNpc npc = npcs.get(normalize(id));
        if (npc != null) {
            npc.setLookMode(lookMode);
            saveNpcs();
        }
    }

    @Override
    public void reload(CommandSender sender) {
        plugin.reloadConfig();
        visibleDistance = plugin.getConfig().getInt("npc-settings.visible-distance", 32);
        trackingDistance = plugin.getConfig().getInt("npc-settings.tracking-distance", 7);
        lookAtInterval = plugin.getConfig().getInt("npc-settings.look-at-interval", 5);
        defaultSkinName = plugin.getConfig().getString("npc-settings.default-skin-name", "Steve");
        loadNpcs();
        sender.sendMessage(Component.text("[RkNpc] reload complete."));
    }

    public String nextId() {
        int index = npcs.size() + 1;
        while (npcs.containsKey("npc-" + index)) {
            index++;
        }
        return "npc-" + index;
    }

    public NpcSkin defaultSkin() {
        return NpcSkin.name(defaultSkinName);
    }

    public void runGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        injectInteraction(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID playerId = e.getPlayer().getUniqueId();
        nmsBridge.interactionInjector().remove(e.getPlayer());
        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
            for (CoreNpc npc : npcs.values()) {
                npc.clearPlayerCache(playerId);
            }
            recentInteractions.keySet().removeIf(key -> key.playerId().equals(playerId));
            nmsBridge.visibilityController().forgetPlayer(playerId);
        }, 1, TimeUnit.SECONDS);
    }

    private void injectInteraction(Player player) {
        nmsBridge.interactionInjector().inject(player, this::isNpcEntityId, this::handleInteraction);
    }

    private boolean isNpcEntityId(int entityId) {
        for (CoreNpc npc : npcs.values()) {
            if (npc.packetNpc().entityId() == entityId) {
                return true;
            }
        }
        return false;
    }

    private void handleInteraction(Player player, int entityId, PacketNpcInteractionAction action) {
        if (isDuplicateInteraction(player.getUniqueId(), entityId)) {
            return;
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            CoreNpc npc = findByEntityId(entityId).orElse(null);
            if (npc == null || !npc.isVisibleTo(player)) {
                return;
            }
            NpcInteractEvent event = new NpcInteractEvent(
                    player,
                    npc,
                    action == PacketNpcInteractionAction.ATTACK ? NpcInteractEvent.Action.ATTACK : NpcInteractEvent.Action.INTERACT,
                    toBukkitHand(action)
            );
            Bukkit.getPluginManager().callEvent(event);
        });
    }

    private boolean isDuplicateInteraction(UUID playerId, int entityId) {
        long now = System.nanoTime();
        InteractionKey key = new InteractionKey(playerId, entityId);
        Long previous = recentInteractions.put(key, now);
        return previous != null && now - previous < INTERACTION_DEBOUNCE_NANOS;
    }

    private Optional<CoreNpc> findByEntityId(int entityId) {
        for (CoreNpc npc : npcs.values()) {
            if (npc.packetNpc().entityId() == entityId) {
                return Optional.of(npc);
            }
        }
        return Optional.empty();
    }

    private EquipmentSlot toBukkitHand(PacketNpcInteractionAction action) {
        return switch (action) {
            case INTERACT_MAIN_HAND -> EquipmentSlot.HAND;
            case INTERACT_OFF_HAND -> EquipmentSlot.OFF_HAND;
            case ATTACK -> null;
        };
    }

    private void refreshVisibility() {
        double visibleDistanceSquared = (double) visibleDistance * visibleDistance;
        double trackingDistanceSquared = (double) trackingDistance * trackingDistance;
        for (CoreNpc npc : npcs.values()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOnline()) {
                    continue;
                }
                VisibilityOverride override = npc.visibilityOverride(player.getUniqueId());
                if (override == VisibilityOverride.FORCE_HIDE) {
                    npc.hideFrom(player);
                    continue;
                }
                if (!npc.isSpawnReady()) {
                    continue;
                }
                if (override == VisibilityOverride.FORCE_SHOW) {
                    npc.showTo(player);
                    rotateIfNeeded(npc, player, trackingDistanceSquared);
                    continue;
                }
                nmsBridge.visibilityController().refresh(npc.packetNpc(), player, player.getLocation().clone(), visibleDistanceSquared);
                rotateIfNeeded(npc, player, trackingDistanceSquared);
            }
        }
    }

    private void rotateIfNeeded(CoreNpc npc, Player player, double trackingDistanceSquared) {
        if (npc.lookMode() != NpcLookMode.TARGET || !npc.isVisibleTo(player)) {
            return;
        }
        Location npcLocation = npc.location();
        Location eyeLocation = player.getEyeLocation().clone();
        if (npcLocation.getWorld() != null
                && npcLocation.getWorld().equals(eyeLocation.getWorld())
                && npcLocation.distanceSquared(eyeLocation) <= trackingDistanceSquared) {
            nmsBridge.renderer().rotateToward(player, npc.packetNpc(), eyeLocation);
        }
    }

    private void resolveSkin(CoreNpc npc) {
        npc.setSpawnReady(npc.skin().type() == NpcSkinType.MIRROR);
        nmsBridge.skinResolver().resolve(npc.skin()).thenAccept(optional -> {
            Property textures = optional.orElse(null);
            hideVisibleViewers(npc);
            npc.rebuildPacket(textures);
            npc.setSpawnReady(true);
            refreshVisibility();
        });
    }

    private void hideVisibleViewers(CoreNpc npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (npc.isVisibleTo(player)) {
                npc.hideFrom(player);
            }
        }
        nmsBridge.visibilityController().remove(npc.packetNpc());
    }

    private void loadNpcs() {
        clearLoadedNpcs();
        npcs.clear();
        if (!npcFile.exists()) {
            saveNpcs();
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(npcFile);
        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            Location location = readLocation(section.getConfigurationSection("location"));
            if (location == null) {
                plugin.getLogger().warning("NPC " + id + " has invalid location.");
                continue;
            }
            CoreNpc npc = new CoreNpc(
                    id,
                    Components.parse(section.getString("display-name", id)),
                    location,
                    readSkin(section.getConfigurationSection("skin")),
                    NpcLookMode.valueOf(section.getString("look-mode", "TARGET").toUpperCase(Locale.ROOT)),
                    readEquipment(section.getConfigurationSection("equipment")),
                    packetNpcFactory,
                    nmsBridge.visibilityController()
            );
            npcs.put(normalize(id), npc);
            resolveSkin(npc);
        }
    }

    private void clearLoadedNpcs() {
        for (CoreNpc npc : npcs.values()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                npc.hideFrom(player);
            }
            nmsBridge.visibilityController().remove(npc.packetNpc());
        }
    }

    private void saveNpcs() {
        YamlConfiguration config = new YamlConfiguration();
        for (CoreNpc npc : npcs.values()) {
            String path = npc.id();
            config.set(path + ".display-name", MiniMessage.miniMessage().serialize(npc.displayName()));
            writeLocation(config, path + ".location", npc.location());
            config.set(path + ".skin.type", npc.skin().type().name());
            config.set(path + ".skin.value", npc.skin().value());
            config.set(path + ".look-mode", npc.lookMode().name());
            for (Map.Entry<NpcEquipmentSlot, ItemStack> entry : npc.equipmentCopy().entrySet()) {
                config.set(path + ".equipment." + entry.getKey().name(), entry.getValue());
            }
        }
        try {
            config.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save npcs.yml: " + e.getMessage());
        }
    }

    private Map<NpcEquipmentSlot, ItemStack> readEquipment(ConfigurationSection section) {
        Map<NpcEquipmentSlot, ItemStack> equipment = new EnumMap<>(NpcEquipmentSlot.class);
        if (section == null) {
            return equipment;
        }
        for (String key : section.getKeys(false)) {
            try {
                equipment.put(NpcEquipmentSlot.valueOf(key), section.getItemStack(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return equipment;
    }

    private NpcSkin readSkin(ConfigurationSection section) {
        if (section == null) {
            return defaultSkin();
        }
        NpcSkinType type = NpcSkinType.valueOf(section.getString("type", "NAME").toUpperCase(Locale.ROOT));
        String value = section.getString("value", "");
        return new NpcSkin(type, value);
    }

    private Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    private void writeLocation(YamlConfiguration config, String path, Location location) {
        config.set(path + ".world", location.getWorld() == null ? "" : location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());
    }

    private String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    public enum SkinDownloadResult {
        SUCCESS,
        NPC_NOT_FOUND,
        INVALID_FILE_NAME,
        INVALID_URL,
        DOWNLOAD_FAILED,
        WRITE_FAILED
    }

    private record InteractionKey(UUID playerId, int entityId) {
    }
}
