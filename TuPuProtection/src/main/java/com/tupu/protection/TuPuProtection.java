package com.tupu.protection;

import com.tupu.protection.commands.TupuCommand;
import com.tupu.protection.core.CoreManager;
import com.tupu.protection.discord.DiscordWebhookNotifier;
import com.tupu.protection.listeners.ExplosiveListener;
import com.tupu.protection.listeners.ProtectionListener;
import com.tupu.protection.util.Keys;
import org.bukkit.plugin.java.JavaPlugin;

public class TuPuProtection extends JavaPlugin {

    private static TuPuProtection instance;

    private CoreManager coreManager;
    private DiscordWebhookNotifier discordNotifier;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        Keys.init(this);

        this.coreManager = new CoreManager(this);
        this.coreManager.load();
        this.discordNotifier = new DiscordWebhookNotifier(this);

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ExplosiveListener(this), this);

        TupuCommand tupuCommand = new TupuCommand(this);
        if (getCommand("tupu") != null) {
            getCommand("tupu").setExecutor(tupuCommand);
            getCommand("tupu").setTabCompleter(tupuCommand);
        } else {
            getLogger().severe("No se encontró el comando 'tupu' declarado en plugin.yml.");
        }

        getLogger().info("TuPuProtection habilitado correctamente. Núcleos cargados: "
                + coreManager.getAllCores().size());
    }

    @Override
    public void onDisable() {
        if (coreManager != null) {
            coreManager.save();
        }
        getLogger().info("TuPuProtection deshabilitado. Núcleos guardados en cores.yml.");
    }

    public static TuPuProtection getInstance() {
        return instance;
    }

    public CoreManager getCoreManager() {
        return coreManager;
    }

    public DiscordWebhookNotifier getDiscordNotifier() {
        return discordNotifier;
    }
}
