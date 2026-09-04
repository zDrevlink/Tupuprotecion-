package com.tupu.protection.listeners;

import com.tupu.protection.TuPuProtection;
import com.tupu.protection.core.CoreManager;
import com.tupu.protection.core.ProtectionCore;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;

/**
 * Aplica las reglas de la protección mientras el núcleo está VIVO:
 *  - Nadie ajeno puede romper ni colocar bloques dentro del área.
 *  - Nadie ajeno puede abrir cofres, hornos, shulkers, barriles, etc.
 *  - Nadie ajeno puede recoger ítems del suelo dentro del área.
 * En cuanto el núcleo llega a 0 HP (isAlive() == false), todas estas
 * restricciones se levantan automáticamente.
 */
public class ProtectionListener implements Listener {

    private final TuPuProtection plugin;
    private final CoreManager coreManager;

    public ProtectionListener(TuPuProtection plugin) {
        this.plugin = plugin;
        this.coreManager = plugin.getCoreManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("tupu.admin.bypass")) {
            return;
        }

        Optional<ProtectionCore> coreOpt = coreManager.getCoreAt(event.getBlock().getLocation());
        if (coreOpt.isEmpty()) {
            return;
        }

        ProtectionCore core = coreOpt.get();
        if (!core.isAlive()) {
            return; // protección caída: sin restricciones
        }

        if (!core.isAuthorized(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§c[TuPu] No tienes permiso para romper bloques en esta protección.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("tupu.admin.bypass")) {
            return;
        }

        Optional<ProtectionCore> coreOpt = coreManager.getCoreAt(event.getBlock().getLocation());
        if (coreOpt.isEmpty()) {
            return;
        }

        ProtectionCore core = coreOpt.get();
        if (!core.isAlive()) {
            return;
        }

        if (!core.isAuthorized(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§c[TuPu] No tienes permiso para construir en esta protección.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (player.hasPermission("tupu.admin.bypass")) {
            return;
        }
        if (!isProtectableContainer(event)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        Location loc = resolveHolderLocation(holder);
        if (loc == null) {
            return;
        }

        Optional<ProtectionCore> coreOpt = coreManager.getCoreAt(loc);
        if (coreOpt.isEmpty()) {
            return;
        }

        ProtectionCore core = coreOpt.get();
        if (!core.isAlive()) {
            return;
        }

        if (!core.isAuthorized(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§c[TuPu] No tienes permiso para abrir este contenedor.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.hasPermission("tupu.admin.bypass")) {
            return;
        }

        Optional<ProtectionCore> coreOpt = coreManager.getCoreAt(event.getItem().getLocation());
        if (coreOpt.isEmpty()) {
            return;
        }

        ProtectionCore core = coreOpt.get();
        if (!core.isAlive()) {
            return; // núcleo destruido: loot libre para todos
        }

        if (!core.isAuthorized(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private boolean isProtectableContainer(InventoryOpenEvent event) {
        return switch (event.getInventory().getType()) {
            case CHEST, FURNACE, BLAST_FURNACE, SMOKER,
                    SHULKER_BOX, BARREL, HOPPER, DISPENSER, DROPPER -> true;
            default -> false;
        };
    }

    private Location resolveHolderLocation(InventoryHolder holder) {
        if (holder instanceof BlockState state) {
            return state.getLocation();
        }
        return null;
    }
}
