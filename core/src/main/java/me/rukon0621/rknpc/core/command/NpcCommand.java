package me.rukon0621.rknpc.core.command;

import me.rukon0621.rknpc.api.npc.NpcCreateRequest;
import me.rukon0621.rknpc.api.npc.NpcEquipmentSlot;
import me.rukon0621.rknpc.api.npc.NpcLookMode;
import me.rukon0621.rknpc.api.npc.NpcSkin;
import me.rukon0621.rknpc.api.npc.NpcSkinType;
import me.rukon0621.rknpc.core.manager.CoreNpcManager;
import me.rukon0621.rknpc.core.util.Components;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class NpcCommand implements BasicCommand {

    private final CoreNpcManager npcManager;

    public NpcCommand(CoreNpcManager npcManager) {
        this.npcManager = npcManager;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            help(sender);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "remove" -> remove(sender, args);
            case "visibility" -> visibility(sender, args);
            case "item" -> item(sender, args);
            case "skin" -> skin(sender, args);
            case "look" -> look(sender, args);
            case "name", "displayname" -> displayName(sender, args);
            case "reload" -> npcManager.reload(sender);
            default -> help(sender);
        }
    }

    @Override
    public @NonNull Collection<String> suggest(CommandSourceStack source, String[] args) {
        if(args.length == 0) {
            return List.of("create", "remove", "visibility", "item", "skin", "look", "name", "reload");
        }
        if (args.length == 1) {
            return filter(List.of("create", "remove", "visibility", "item", "skin", "look", "name", "reload"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ((sub.equals("remove") || sub.equals("visibility") || sub.equals("item") || sub.equals("skin") || sub.equals("look") || sub.equals("name") || sub.equals("displayname")) && args.length == 2) {
            if (sub.equals("skin")) {
                List<String> values = new ArrayList<>(npcIds());
                values.add("download");
                values.add("copy");
                return filter(values, args[1]);
            }
            return filter(npcIds(), args[1]);
        }
        if (sub.equals("visibility") && args.length == 3) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (sub.equals("visibility") && args.length == 4) {
            return filter(List.of("show", "hide", "auto"), args[3]);
        }
        if (sub.equals("item") && args.length == 3) {
            return filter(List.of("hand", "offhand", "helmet", "chestplate", "leggings", "boots"), args[2]);
        }
        if (sub.equals("skin") && args.length == 3) {
            if (args[1].equalsIgnoreCase("download") || args[1].equalsIgnoreCase("copy")) {
                return filter(npcIds(), args[2]);
            }
            return filter(List.of("name", "url", "image", "mirror"), args[2]);
        }
        if (sub.equals("skin") && args.length == 4 && args[1].equalsIgnoreCase("copy")) {
            return filter(npcIds(), args[3]);
        }
        if (sub.equals("skin") && args.length == 4 && args[1].equalsIgnoreCase("download")) {
            return filter(List.of("skin.png"), args[3]);
        }
        if (sub.equals("skin") && args.length == 5 && args[1].equalsIgnoreCase("download")) {
            return filter(List.of("https://"), args[4]);
        }
        if (sub.equals("look") && args.length == 3) {
            return filter(List.of("target", "off"), args[2]);
        }
        return List.of();
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length < 2) {
            send(sender, "/npc create <id> [name]");
            return;
        }
        String id = args[1];
        if (npcManager.getNpc(id).isPresent()) {
            send(sender, "이미 존재하는 NPC ID입니다.");
            return;
        }
        String name = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : id;
        npcManager.createNpc(new NpcCreateRequest(
                id,
                Components.parse(name),
                player.getLocation(),
                npcManager.defaultSkin(),
                NpcLookMode.TARGET
        ));
        send(sender, "NPC를 생성했습니다. id=" + id);
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "/npc remove <id>");
            return;
        }
        boolean removed = npcManager.removeNpc(args[1]);
        send(sender, removed ? "NPC를 삭제했습니다." : "NPC를 찾을 수 없습니다.");
    }

    private void visibility(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "/npc visibility <id> <player> <show|hide|auto>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            send(sender, "플레이어를 찾을 수 없습니다.");
            return;
        }
        switch (args[3].toLowerCase(Locale.ROOT)) {
            case "show" -> npcManager.setVisible(args[1], target, true);
            case "hide" -> npcManager.setVisible(args[1], target, false);
            case "auto" -> npcManager.clearVisibilityOverride(args[1], target.getUniqueId());
            default -> {
                send(sender, "/npc visibility <id> <player> <show|hide|auto>");
                return;
            }
        }
        send(sender, "visibility를 변경했습니다.");
    }

    private void item(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length < 3) {
            send(sender, "/npc item <id> <hand|offhand>");
            return;
        }
        NpcEquipmentSlot slot = parseSlot(args[2]);
        if (slot == null) {
            send(sender, "알 수 없는 장비 슬롯입니다.");
            return;
        }
        ItemStack itemStack = switch (slot) {
            case OFF_HAND -> player.getInventory().getItemInOffHand();
            default -> player.getInventory().getItemInMainHand();
        };
        npcManager.setEquipment(args[1], slot, itemStack);
        send(sender, "NPC 장비를 변경했습니다.");
    }

    private void skin(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("download")) {
            skinDownload(sender, args);
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("copy")) {
            skinCopy(sender, args);
            return;
        }
        if (args.length < 3) {
            skinHelp(sender);
            return;
        }
        NpcSkinType type;
        try {
            type = NpcSkinType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(sender, "알 수 없는 스킨 타입입니다.");
            return;
        }
        String value = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";
        if (type != NpcSkinType.MIRROR && value.isBlank()) {
            skinHelp(sender);
            return;
        }
        npcManager.setSkin(args[1], new NpcSkin(type, value));
        send(sender, "NPC 스킨을 변경했습니다.");
    }

    private void skinDownload(CommandSender sender, String[] args) {
        if (args.length < 5) {
            send(sender, "/npc skin download <npc> <파일이름.png> <링크>");
            return;
        }
        String url = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        send(sender, "스킨 이미지를 다운로드합니다.");
        npcManager.downloadAndApplySkin(args[2], args[3], url).thenAccept(result ->
                npcManager.runGlobal(() -> send(sender, switch (result) {
                    case SUCCESS -> "스킨 이미지를 다운로드하고 NPC에 적용했습니다.";
                    case NPC_NOT_FOUND -> "NPC를 찾을 수 없습니다.";
                    case INVALID_FILE_NAME -> "파일 이름은 하위 경로 없이 .png로 끝나야 합니다.";
                    case INVALID_URL -> "올바른 http/https 링크가 아닙니다.";
                    case DOWNLOAD_FAILED -> "스킨 이미지 다운로드에 실패했습니다.";
                    case WRITE_FAILED -> "스킨 이미지 파일 저장에 실패했습니다.";
                }))
        );
    }

    private void skinCopy(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "/npc skin copy <npc> <sourcenpc>");
            return;
        }
        var target = npcManager.getNpc(args[2]);
        if (target.isEmpty()) {
            send(sender, "대상 NPC를 찾을 수 없습니다.");
            return;
        }
        var source = npcManager.getNpc(args[3]);
        if (source.isEmpty()) {
            send(sender, "원본 NPC를 찾을 수 없습니다.");
            return;
        }
        NpcSkin sourceSkin = source.get().skin();
        npcManager.setSkin(args[2], new NpcSkin(sourceSkin.type(), sourceSkin.value()));
        send(sender, "NPC 스킨을 복사했습니다.");
    }

    private void look(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "/npc look <id> <target|off>");
            return;
        }
        NpcLookMode mode = args[2].equalsIgnoreCase("target") ? NpcLookMode.TARGET : NpcLookMode.OFF;
        npcManager.setLookMode(args[1], mode);
        send(sender, "NPC 회전 모드를 변경했습니다.");
    }

    private void displayName(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "/npc name <id> <name>");
            return;
        }
        if (npcManager.getNpc(args[1]).isEmpty()) {
            send(sender, "NPC를 찾을 수 없습니다.");
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        npcManager.setDisplayName(args[1], Components.parse(name));
        send(sender, "NPC displayName을 변경했습니다.");
    }

    private NpcEquipmentSlot parseSlot(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "hand" -> NpcEquipmentSlot.HAND;
            case "offhand", "off_hand" -> NpcEquipmentSlot.OFF_HAND;
            case "helmet" -> NpcEquipmentSlot.HELMET;
            case "chestplate" -> NpcEquipmentSlot.CHESTPLATE;
            case "leggings" -> NpcEquipmentSlot.LEGGINGS;
            case "boots" -> NpcEquipmentSlot.BOOTS;
            default -> null;
        };
    }

    private void help(CommandSender sender) {
        send(sender, "/npc create <id> [name]");
        send(sender, "/npc remove <id>");
        send(sender, "/npc visibility <id> <player> <show|hide|auto>");
        send(sender, "/npc item <id> <hand|offhand>");
        send(sender, "/npc skin <id> <name|url|image|mirror> <value>");
        send(sender, "/npc skin download <npc> <파일이름.png> <링크>");
        send(sender, "/npc skin copy <npc> <sourcenpc>");
        send(sender, "/npc look <id> <target|off>");
        send(sender, "/npc name <id> <name>");
        send(sender, "/npc reload");
    }

    private void skinHelp(CommandSender sender) {
        send(sender, "/npc skin <id> <name|url|image|mirror> <value>");
        send(sender, "/npc skin download <npc> <파일이름.png> <링크>");
        send(sender, "/npc skin copy <npc> <sourcenpc>");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message));
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }

    private List<String> npcIds() {
        return npcManager.getCoreNpcs().stream().map(npc -> npc.id()).toList();
    }
}
