package com.tupu.protection.listeners;

import com.tupu.protection.TuPuProtection;
import com.tupu.protection.core.CoreManager;
import com.tupu.protection.core.ProtectionCore;
import com.tupu.protection.util.Keys;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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

        String coreType = getCoreItemType(event.getItemInHand());
        if (coreType != null) {
            handleCorePlacement(event, player, coreType);
            return;
        }

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

    /**
     * Al colocar un ítem de núcleo (core_tierN), en vez de dejarlo como un
     * bloque de oro cualquiera, se registra una ProtectionCore real en el
     * CoreManager centrada en ese bloque, usando el tamaño/HP configurados
     * para ese tier en config.yml.
     */
    private void handleCorePlacement(BlockPlaceEvent event, Player player, String coreType) {
        int tier;
        try {
            tier = Integer.parseInt(coreType.substring(coreType.length() - 1));
        } catch (NumberFormatException e) {
            return;
        }

        String tierKey = "tier" + tier;
        int sizeXZ = plugin.getConfig().getInt("protection-tiers." + tierKey + ".size-x-z", defaultSize(tier));
        double maxHp = plugin.getConfig().getDouble("protection-tiers." + tierKey + ".max-hp", defaultHp(tier));

        Location center = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        coreManager.createCore(player.getUniqueId(), tier, sizeXZ, maxHp, center);
        coreManager.save();

        player.sendMessage("§a[TuPu] ¡Pusiste tu protección Tier " + tier + "! (HP: " + (int) maxHp + ")");
    }

    private String getCoreItemType(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        String type = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_TYPE, PersistentDataType.STRING);
        return (type != null && type.startsWith("core_tier")) ? type : null;
    }

    private int defaultSize(int tier) {
        return switch (tier) {
            case 2 -> 50;
            case 3 -> 100;
            case 4 -> 200;
            default -> 25;
        };
    }

    private double defaultHp(int tier) {
        return switch (tier) {
            case 2 -> 3000;
            case 3 -> 7500;
            case 4 -> 15000;
            default -> 1000;
        };
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
