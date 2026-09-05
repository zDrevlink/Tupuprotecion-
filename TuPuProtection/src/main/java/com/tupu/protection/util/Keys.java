package com.tupu.protection.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Contenedor estático de todos los NamespacedKeys usados para etiquetar
 * ítems y entidades del plugin mediante PersistentDataContainer (PDC).
 * Deben inicializarse una única vez en TuPuProtection#onEnable().
 */
public final class Keys {

    /** Tipo de ítem/entidad custom: core_tier1..4, tnt_heavy_core, tnt_light_core, tnt_wall_breaker, tnt_radar. */
    public static NamespacedKey ITEM_TYPE;

    /** Marca si una TNT es la variante "pegajosa" (sin gravedad, adosada a la superficie). */
    public static NamespacedKey STICKY_FLAG;

    /** Tier (1-4) de un núcleo o de un ítem de núcleo. */
    public static NamespacedKey CORE_TIER;

    /** UUID del núcleo al que pertenece un ítem/entidad, cuando aplica. */
    public static NamespacedKey CORE_ID;

    /** UUID del jugador que encendió/lanzó una TNT custom (para logs, chat y webhook). */
    public static NamespacedKey ATTACKER;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        ITEM_TYPE = new NamespacedKey(plugin, "item_type");
        STICKY_FLAG = new NamespacedKey(plugin, "sticky_flag");
        CORE_TIER = new NamespacedKey(plugin, "core_tier");
        CORE_ID = new NamespacedKey(plugin, "core_id");
        ATTACKER = new NamespacedKey(plugin, "attacker");
    }
}
