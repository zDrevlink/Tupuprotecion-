package com.tupu.protection.listeners;

import com.tupu.protection.TuPuProtection;
import com.tupu.protection.core.CoreManager;
import com.tupu.protection.core.ProtectionCore;
import com.tupu.protection.util.Keys;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gestiona el ciclo de vida completo de las TNTs custom:
 *  1. Colocación/encendido con clic derecho sobre un bloque (variante normal o pegajosa).
 *  2. Detonación: aplica el efecto correspondiente según tupu:item_type
 *     (daño a núcleo, perforación limitada de bloques, o revelado por radar).
 *
 * Nota de diseño: en vez de depender del bloque de TNT vanilla + su ignición
 * (difícil de etiquetar de forma persistente), la TNT custom se coloca y
 * enciende en una sola acción, generando directamente la entidad TNTPrimed
 * ya etiquetada por PDC. Esto simplifica enormemente el seguimiento del tipo
 * de explosivo y es coherente con "al encenderse o lanzarse" del diseño.
 */
public class ExplosiveListener implements Listener {

    private static final int DEFAULT_FUSE_TICKS = 80;

    private final TuPuProtection plugin;
    private final CoreManager coreManager;

    public ExplosiveListener(TuPuProtection plugin) {
        this.plugin = plugin;
        this.coreManager = plugin.getCoreManager();
    }

    // ---------------------------------------------------------------
    // Colocación / encendido de las TNTs custom
    // ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceCustomTnt(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        String itemType = getItemType(item);
        if (itemType == null || !itemType.startsWith("tnt_")) {
            return; // no es una TNT custom de TuPu
        }

        event.setCancelled(true); // evita colocar el bloque de TNT vanilla

        Block clicked = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        if (clicked == null || face == null || clicked.getWorld() == null) {
            return;
        }

        boolean sticky = isSticky(item);

        Location spawnLoc = sticky
                ? clicked.getRelative(face).getLocation().add(0.5, 0.5, 0.5)
                : clicked.getLocation().add(0.5, 1.0, 0.5);

        Player player = event.getPlayer();

        TNTPrimed tnt = clicked.getWorld().spawn(spawnLoc, TNTPrimed.class);
        tnt.setFuseTicks(DEFAULT_FUSE_TICKS);
        tnt.setGravity(!sticky); // pegajosa: sin gravedad, queda adosada al bloque exacto
        tnt.setSource(player);
        tnt.setYield(0f); // el efecto sobre bloques lo gestionamos manualmente en la explosión

        PersistentDataContainer pdc = tnt.getPersistentDataContainer();
        pdc.set(Keys.ITEM_TYPE, PersistentDataType.STRING, itemType);
        pdc.set(Keys.STICKY_FLAG, PersistentDataType.BYTE, (byte) (sticky ? 1 : 0));
        pdc.set(Keys.ATTACKER, PersistentDataType.STRING, player.getUniqueId().toString());

        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        player.getWorld().playSound(spawnLoc, org.bukkit.Sound.ENTITY_TNT_PRIMED, 1f, sticky ? 1.3f : 1f);
    }

    // ---------------------------------------------------------------
    // Detonación
    // ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();

        if (!pdc.has(Keys.ITEM_TYPE, PersistentDataType.STRING)) {
            return; // TNT normal de vanilla: no la tocamos
        }

        String itemType = pdc.get(Keys.ITEM_TYPE, PersistentDataType.STRING);
        String attackerId = pdc.get(Keys.ATTACKER, PersistentDataType.STRING);
        Optional<ProtectionCore> coreOpt = coreManager.getCoreAt(entity.getLocation());

        switch (itemType == null ? "" : itemType) {
            case "tnt_heavy_core" -> {
                event.blockList().clear();
                coreOpt.ifPresentOrElse(
                        core -> applyCoreDamage(core, getDamage("heavy-core-buster", 1000),
                                "Destructor Pesado de Núcleo", attackerId, entity.getLocation()),
                        () -> {}
                );
            }
            case "tnt_light_core" -> {
                event.blockList().clear();
                coreOpt.ifPresent(core -> applyCoreDamage(core, getDamage("light-core-buster", 250),
                        "Destructor Ligero de Núcleo", attackerId, entity.getLocation()));
            }
            case "tnt_wall_breaker" -> {
                if (coreOpt.isEmpty()) {
                    event.blockList().clear();
                    return;
                }
                int maxBlocks = plugin.getConfig().getInt("explosives.wall-breaker.max-blocks-to-destroy", 4);
                limitBlockList(event, maxBlocks);
            }
            case "tnt_radar" -> {
                event.blockList().clear();
                coreOpt.ifPresent(core -> triggerRadar(core, attackerId));
            }
            default -> event.blockList().clear(); // tipo desconocido: por seguridad no destruye bloques
        }
    }

    private void applyCoreDamage(ProtectionCore core, double damage, String explosiveLabel,
                                  String attackerId, Location impact) {
        boolean destroyed = core.damage(damage);
        coreManager.save();

        if (destroyed) {
            core.getWorld().getPlayers().forEach(p ->
                    p.sendMessage("§4[TuPu] ¡Un núcleo ha sido destruido por un " + explosiveLabel + "!"));
        }

        plugin.getDiscordNotifier().notifyCoreDamaged(core, explosiveLabel);
    }

    private void limitBlockList(EntityExplodeEvent event, int maxBlocks) {
        Location origin = event.getLocation();
        List<Block> blocks = event.blockList();
        blocks.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(origin)));

        if (blocks.size() > maxBlocks) {
            // blockList() es una lista viva: recortarla la modifica in-place.
            blocks.subList(maxBlocks, blocks.size()).clear();
        }
    }

    private void triggerRadar(ProtectionCore core, String attackerId) {
        Location center = core.getCenter().add(0.5, 1.5, 0.5);
        int durationSeconds = plugin.getConfig().getInt("explosives.radar.reveal-duration-seconds", 15);

        Player attacker = null;
        if (attackerId != null) {
            attacker = plugin.getServer().getPlayer(UUID.fromString(attackerId));
        }

        final Player finalAttacker = attacker;

        new BukkitRunnable() {
            int elapsedTicks = 0;
            final int maxTicks = durationSeconds * 20;

            @Override
            public void run() {
                if (elapsedTicks >= maxTicks || core.getWorld() == null) {
                    cancel();
                    return;
                }
                // Partículas visibles a distancia/a través de bloques (aproximación):
                // se envían directamente al atacante si está en el mismo mundo.
                if (finalAttacker != null && finalAttacker.getWorld().equals(core.getWorld())) {
                    finalAttacker.spawnParticle(Particle.FLAME, center, 8, 0.4, 0.6, 0.4, 0.001);
                    finalAttacker.spawnParticle(Particle.END_ROD, center, 4, 0.3, 0.3, 0.3, 0.01);
                } else {
                    core.getWorld().spawnParticle(Particle.FLAME, center, 8, 0.4, 0.6, 0.4, 0.001);
                }
                elapsedTicks += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);

        if (finalAttacker != null) {
            Location c = core.getCenter();
            finalAttacker.sendMessage(String.format(
                    "§b[TuPu Radar] Núcleo localizado en X:%.0f Y:%.0f Z:%.0f",
                    c.getX(), c.getY(), c.getZ()));
        }
    }

    private double getDamage(String path, double def) {
        return plugin.getConfig().getDouble("explosives." + path + ".damage", def);
    }

    private String getItemType(ItemStack item) {
        if (item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_TYPE, PersistentDataType.STRING);
    }

    private boolean isSticky(ItemStack item) {
        if (item.getItemMeta() == null) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(Keys.STICKY_FLAG, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }
}
