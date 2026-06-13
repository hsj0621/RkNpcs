package me.rukon0621.rknpc.core.gui;

import me.rukon0621.rknpc.api.npc.Npc;
import me.rukon0621.rknpc.api.npc.NpcEquipmentSlot;
import me.rukon0621.rknpc.core.manager.CoreNpcManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class NpcItemGui implements Listener {

    private static final int SIZE = 18;
    private static final Map<Integer, NpcEquipmentSlot> EDITABLE_SLOTS = Map.of(
            9, NpcEquipmentSlot.HAND,
            10, NpcEquipmentSlot.OFF_HAND,
            11, NpcEquipmentSlot.HELMET,
            12, NpcEquipmentSlot.CHESTPLATE,
            13, NpcEquipmentSlot.LEGGINGS,
            14, NpcEquipmentSlot.BOOTS
    );
    private static final Map<NpcEquipmentSlot, Integer> INVENTORY_SLOTS = new EnumMap<>(Map.of(
            NpcEquipmentSlot.HAND, 9,
            NpcEquipmentSlot.OFF_HAND, 10,
            NpcEquipmentSlot.HELMET, 11,
            NpcEquipmentSlot.CHESTPLATE, 12,
            NpcEquipmentSlot.LEGGINGS, 13,
            NpcEquipmentSlot.BOOTS, 14
    ));

    private final CoreNpcManager npcManager;

    public NpcItemGui(CoreNpcManager npcManager) {
        this.npcManager = npcManager;
    }

    public boolean open(Player player, String npcId) {
        Optional<Npc> optional = npcManager.getNpc(npcId);
        if (optional.isEmpty()) {
            return false;
        }
        Npc npc = optional.get();
        Holder holder = new Holder(npc.id());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("NPC 장비: " + npc.id()));
        holder.inventory(inventory);
        setLabel(inventory, 0, Material.IRON_SWORD, "메인핸드");
        setLabel(inventory, 1, Material.SHIELD, "보조핸드");
        setLabel(inventory, 2, Material.IRON_HELMET, "헬멧");
        setLabel(inventory, 3, Material.IRON_CHESTPLATE, "갑옷");
        setLabel(inventory, 4, Material.IRON_LEGGINGS, "바지");
        setLabel(inventory, 5, Material.IRON_BOOTS, "신발");
        for (Map.Entry<NpcEquipmentSlot, Integer> entry : INVENTORY_SLOTS.entrySet()) {
            npc.equipment(entry.getKey()).ifPresent(item -> inventory.setItem(entry.getValue(), item));
        }
        player.openInventory(inventory);
        player.sendMessage(Component.text("[RkNpc] ", NamedTextColor.AQUA)
                .append(Component.text("아래 줄에 장비를 배치하고 창을 닫으면 저장됩니다.", NamedTextColor.WHITE)));
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < SIZE && !EDITABLE_SLOTS.containsKey(rawSlot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < SIZE && !EDITABLE_SLOTS.containsKey(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        for (Map.Entry<Integer, NpcEquipmentSlot> entry : EDITABLE_SLOTS.entrySet()) {
            npcManager.setEquipment(holder.npcId(), entry.getValue(), event.getInventory().getItem(entry.getKey()));
        }
        event.getPlayer().sendMessage(Component.text("[RkNpc] ", NamedTextColor.AQUA)
                .append(Component.text("NPC 장비를 저장했습니다.", NamedTextColor.WHITE)));
    }

    private void setLabel(Inventory inventory, int slot, Material material, String name) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        itemStack.setItemMeta(meta);
        inventory.setItem(slot, itemStack);
    }

    private static final class Holder implements InventoryHolder {
        private final String npcId;
        private Inventory inventory;

        private Holder(String npcId) {
            this.npcId = npcId;
        }

        private String npcId() {
            return npcId;
        }

        private void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
