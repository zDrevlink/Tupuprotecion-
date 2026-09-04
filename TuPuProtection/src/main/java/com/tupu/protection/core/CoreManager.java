package com.tupu.protection.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Mantiene todos los ProtectionCore activos y su persistencia en cores.yml.
 * (Si más adelante se prefiere SQLite, esta es la clase a sustituir/extender;
 * la firma pública de sus métodos puede mantenerse igual.)
 */
public class CoreManager {

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, ProtectionCore> cores = new HashMap<>();

    public CoreManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cores.yml");
    }

    public ProtectionCore createCore(UUID owner, int tier, int sizeXZ, double maxHp, Location center) {
        UUID id = UUID.randomUUID();
        ProtectionCore core = new ProtectionCore(id, owner, tier, sizeXZ, maxHp, center);
        cores.put(id, core);
        return core;
    }

    public void removeCore(UUID id) {
        cores.remove(id);
    }

    public Collection<ProtectionCore> getAllCores() {
        return cores.values();
    }

    /** Devuelve el núcleo cuya área contiene la ubicación dada, si existe. */
    public Optional<ProtectionCore> getCoreAt(Location location) {
        return cores.values().stream()
                .filter(c -> c.contains(location))
                .findFirst();
    }

    /** Devuelve el núcleo del que `owner` es dueño y en cuya área está parado. Usado por /tupu prote. */
    public Optional<ProtectionCore> getCoreOwnedByStandingAt(UUID owner, Location location) {
        return cores.values().stream()
                .filter(c -> c.getOwner().equals(owner) && c.contains(location))
                .findFirst();
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                UUID owner = UUID.fromString(Objects.requireNonNull(yml.getString(key + ".owner")));
                int tier = yml.getInt(key + ".tier");
                int sizeXZ = yml.getInt(key + ".sizeXZ");
                double maxHp = yml.getDouble(key + ".maxHp");
                double currentHp = yml.getDouble(key + ".currentHp");
                World world = Bukkit.getWorld(Objects.requireNonNull(yml.getString(key + ".world")));
                double x = yml.getDouble(key + ".x");
                double y = yml.getDouble(key + ".y");
                double z = yml.getDouble(key + ".z");

                if (world == null) {
                    plugin.getLogger().warning("Mundo no encontrado para el núcleo " + key + ", se omite.");
                    continue;
                }

                Location center = new Location(world, x, y, z);
                ProtectionCore core = new ProtectionCore(id, owner, tier, sizeXZ, maxHp, center);
                if (currentHp < maxHp) {
                    core.damage(maxHp - currentHp);
                }

                List<String> memberStrings = yml.getStringList(key + ".members");
                for (String m : memberStrings) {
                    core.addMember(UUID.fromString(m));
                }

                cores.put(id, core);
            } catch (Exception ex) {
                plugin.getLogger().warning("No se pudo cargar el núcleo " + key + ": " + ex.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (ProtectionCore core : cores.values()) {
            String key = core.getId().toString();
            yml.set(key + ".owner", core.getOwner().toString());
            yml.set(key + ".tier", core.getTier());
            yml.set(key + ".sizeXZ", core.getSizeXZ());
            yml.set(key + ".maxHp", core.getMaxHp());
            yml.set(key + ".currentHp", core.getCurrentHp());
            yml.set(key + ".world", core.getWorld() != null ? core.getWorld().getName() : "world");
            yml.set(key + ".x", core.getCenter().getX());
            yml.set(key + ".y", core.getCenter().getY());
            yml.set(key + ".z", core.getCenter().getZ());

            List<String> memberStrings = new ArrayList<>();
            for (UUID m : core.getMembers()) {
                memberStrings.add(m.toString());
            }
            yml.set(key + ".members", memberStrings);
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar cores.yml: " + e.getMessage());
        }
    }
}
