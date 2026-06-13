package me.rukon0621.rknpc.core;

import me.rukon0621.rknpc.api.NpcManager;
import me.rukon0621.rknpc.api.RkNpc;
import me.rukon0621.rknpc.api.RkNpcProvider;
import me.rukon0621.rknpc.api.event.NpcInteractEvent;
import me.rukon0621.rknpc.core.command.NpcCommand;
import me.rukon0621.rknpc.core.gui.NpcItemGui;
import me.rukon0621.rknpc.core.manager.CoreNpcManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class RkNpcPlugin extends JavaPlugin implements RkNpc {

    private CoreNpcManager npcManager;

    @Override
    public void onLoad() {
        RkNpcProvider.register(this);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        npcManager = new CoreNpcManager(this);
        npcManager.reload(getServer().getConsoleSender());

        NpcItemGui itemGui = new NpcItemGui(npcManager);
        NpcCommand command = new NpcCommand(npcManager, itemGui);
        registerCommand("npc", "RkNpc command", command);
        getServer().getPluginManager().registerEvents(npcManager, this);
        getServer().getPluginManager().registerEvents(itemGui, this);
        npcManager.start();
    }

    @Override
    public void onDisable() {
        if (npcManager != null) {
            npcManager.close();
        }
        RkNpcProvider.unregister(this);
    }

    @Override
    public NpcManager npcManager() {
        return npcManager;
    }
}
