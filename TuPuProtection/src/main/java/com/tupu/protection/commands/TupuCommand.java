package com.tupu.protection.commands;

import com.tupu.protection.TuPuProtection;
import com.tupu.protection.core.CoreManager;
import com.tupu.protection.core.ProtectionCore;
import com.tupu.protection.util.Keys;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class TupuCommand implements CommandExecutor, TabCompleter {

    /** UUID reservado para las protecciones de prueba creadas con /tupu admin testprote (nadie está autorizado). */
    private static final UUID NO_OWNER = new UUID(0L, 0L);

    private static final List<String> GIVE_ITEMS = List.of(
            "core_tier1", "core_tier2", "core_tier3", "core_tier4",
            "tnt_heavy_core", "tnt_heavy_core_sticky",
            "tnt_light_core", "tnt_light_core_sticky",
            "tnt_wall_breaker", "tnt_wall_breaker_sticky",
            "tnt_radar", "tnt_radar_sticky"
    );

    private final TuPuProtection plugin;
    private final CoreManager coreManager;

    public TupuCommand(TuPuProtection plugin) {
        this.plugin = plugin;
        this.coreManager = plugin.getCoreManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /tupu <prote|give|admin> ...");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "prote" -> handleProte(sender, args);
            case "give" -> handleGive(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default -> sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /tupu prote, /tupu give o /tupu admin.");
        }
        return true;
    }

    // -----------------------------------------------------------------
    // /tupu prote add|delete|list|members
    // -----------------------------------------------------------------

    private void handleProte(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede usarse en el juego.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Uso: /tupu prote <add|delete|list|members> [jugador]");
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        // "list" no requiere estar parado dentro de una protección: muestra TODAS las tuyas.
        if (sub.equals("list")) {
            handleProteList(player);
            return;
        }

        Optional<ProtectionCore> coreOpt =
                coreManager.getCoreOwnedByStandingAt(player.getUniqueId(), player.getLocation());
        if (coreOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No estás parado dentro de una protección de la que seas dueño.");
            return;
        }
        ProtectionCore core = coreOpt.get();

        switch (sub) {
            case "add" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.YELLOW + "Uso: /tupu prote add <jugador>");
                    return;
                }
                OfflinePlayer target = resolvePlayer(args[2]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Jugador no encontrado.");
                    return;
                }
                core.addMember(target.getUniqueId());
                coreManager.save();
                player.sendMessage(ChatColor.GREEN + "Añadiste a " + args[2] + " a la protección.");
            }
            case "delete" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.YELLOW + "Uso: /tupu prote delete <jugador>");
                    return;
                }
                OfflinePlayer target = resolvePlayer(args[2]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Jugador no encontrado.");
                    return;
                }
                if (core.removeMember(target.getUniqueId())) {
                    coreManager.save();
                    player.sendMessage(ChatColor.GREEN + "Eliminaste a " + args[2] + " de la protección.");
                } else {
                    player.sendMessage(ChatColor.RED + "Ese jugador no estaba en la lista.");
                }
            }
            case "members" -> {
                if (core.getMembers().isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Esta protección no tiene miembros añadidos.");
                    return;
                }
                String names = core.getMembers().stream()
                        .map(uuid -> plugin.getServer().getOfflinePlayer(uuid).getName())
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(", "));
                player.sendMessage(ChatColor.AQUA + "Miembros autorizados: " + ChatColor.WHITE + names);
            }
            default -> player.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa add, delete, list o members.");
        }
    }

    /** /tupu prote list: todas las protecciones del jugador, con tier, ubicación y HP. */
    private void handleProteList(Player player) {
        List<ProtectionCore> owned = coreManager.getAllCores().stream()
                .filter(c -> c.getOwner().equals(player.getUniqueId()))
                .sorted(Comparator.comparingInt(ProtectionCore::getTier))
                .toList();

        if (owned.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No tienes ninguna protección registrada todavía.");
            return;
        }

        player.sendMessage(ChatColor.AQUA + "Tienes " + owned.size() + " protección(es):");
        int index = 1;
        for (ProtectionCore core : owned) {
            Location c = core.getCenter();
            String world = core.getWorld() != null ? core.getWorld().getName() : "?";
            player.sendMessage(ChatColor.WHITE + "" + index + ". " + ChatColor.GRAY + "Tier " + core.getTier()
                    + ChatColor.WHITE + " - " + world
                    + String.format(" (X:%.0f Y:%.0f Z:%.0f) ", c.getX(), c.getY(), c.getZ())
                    + ChatColor.GREEN + "HP: " + (int) core.getCurrentHp() + "/" + (int) core.getMaxHp());
            index++;
        }
    }

    private OfflinePlayer resolvePlayer(String name) {
        Server server = plugin.getServer();
        Player online = server.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = server.getOfflinePlayer(name);
        return (offline.hasPlayedBefore() || offline.isOnline()) ? offline : null;
    }

    // -----------------------------------------------------------------
    // /tupu give <jugador> <item> <cantidad>
    // -----------------------------------------------------------------

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tupu.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /tupu give <jugador> <item> <cantidad>");
            return;
        }

        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "El jugador " + args[1] + " no está conectado.");
            return;
        }

        String itemKey = args[2].toLowerCase(Locale.ROOT);
        if (!GIVE_ITEMS.contains(itemKey)) {
            sender.sendMessage(ChatColor.RED + "Ítem desconocido. Usa el tabulador para ver las opciones válidas.");
            return;
        }

        int amount;
        try {
            amount = Math.max(1, Integer.parseInt(args[3]));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "La cantidad debe ser un número.");
            return;
        }

        ItemStack item = buildItem(itemKey);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "No se pudo construir el ítem solicitado.");
            return;
        }
        item.setAmount(amount);

        target.getInventory().addItem(item);
        sender.sendMessage(ChatColor.GREEN + "Le diste " + amount + "x " + itemKey + " a " + target.getName() + ".");
        if (!sender.getName().equals(target.getName())) {
            target.sendMessage(ChatColor.GREEN + "Recibiste " + amount + "x " + itemKey + " de un administrador.");
        }
    }

    // -----------------------------------------------------------------
    // /tupu admin testprote|removeprote  (solo para pruebas)
    // -----------------------------------------------------------------

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tupu.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede usarse en el juego.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Uso: /tupu admin <testprote|removeprote> [tier]");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "testprote" -> handleTestProte(player, args);
            case "removeprote" -> handleRemoveProte(player);
            default -> player.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa testprote o removeprote.");
        }
    }

    /**
     * Crea una protección de prueba SIN dueño real (UUID reservado) en la
     * ubicación del admin, para poder atacarla con las TNTs como si fuera
     * "ajena" sin tener que pedirle la base a otro jugador.
     */
    private void handleTestProte(Player player, String[] args) {
        int tier = 1;
        if (args.length >= 3) {
            try {
                tier = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                // se queda en el tier 1 por defecto
            }
        }
        tier = Math.max(1, Math.min(4, tier));

        String tierKey = "tier" + tier;
        int sizeXZ = plugin.getConfig().getInt("protection-tiers." + tierKey + ".size-x-z", defaultSize(tier));
        double maxHp = plugin.getConfig().getDouble("protection-tiers." + tierKey + ".max-hp", defaultHp(tier));

        coreManager.createCore(NO_OWNER, tier, sizeXZ, maxHp, player.getLocation());
        coreManager.save();

        player.sendMessage(ChatColor.GREEN + "Protección de PRUEBA Tier " + tier + " creada en tu ubicación (HP: "
                + (int) maxHp + "). No tiene dueño real, así que ni tú ni nadie están autorizados: perfecta para "
                + "probar las TNTs desde fuera.");
    }

    /** Elimina cualquier protección (tuya, de prueba o ajena) en la ubicación actual del admin. */
    private void handleRemoveProte(Player player) {
        Optional<ProtectionCore> coreOpt = coreManager.getCoreAt(player.getLocation());
        if (coreOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No hay ninguna protección en tu ubicación actual.");
            return;
        }
        coreManager.removeCore(coreOpt.get().getId());
        coreManager.save();
        player.sendMessage(ChatColor.GREEN + "Protección eliminada en tu ubicación.");
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

    /**
     * Fábrica simplificada de ítems a partir de su clave de config (core_tierN,
     * tnt_xxx, con sufijo opcional _sticky). Una siguiente iteración debería
     * mover esto a una clase ItemFactory que lea nombres/lore/materiales desde
     * config.yml/recipes.yml en vez de tenerlos hardcodeados aquí.
     */
    private ItemStack buildItem(String key) {
        boolean sticky = key.endsWith("_sticky");
        String base = sticky ? key.substring(0, key.length() - "_sticky".length()) : key;

        ItemStack item;
        String displayName;

        switch (base) {
            case "core_tier1" -> {
                item = new ItemStack(Material.GOLD_BLOCK);
                displayName = "§a§lNúcleo TuPu - Tier 1";
            }
            case "core_tier2" -> {
                item = new ItemStack(Material.GOLD_BLOCK);
                displayName = "§e§lNúcleo TuPu - Tier 2";
            }
            case "core_tier3" -> {
                item = new ItemStack(Material.GOLD_BLOCK);
                displayName = "§c§lNúcleo TuPu - Tier 3";
            }
            case "core_tier4" -> {
                item = new ItemStack(Material.GOLD_BLOCK);
                displayName = "§5§lNúcleo TuPu - Tier 4";
            }
            case "tnt_heavy_core" -> {
                item = new ItemStack(Material.TNT);
                displayName = "§c§lTNT Destructor Pesado" + (sticky ? " (Pegajosa)" : "");
            }
            case "tnt_light_core" -> {
                item = new ItemStack(Material.TNT);
                displayName = "§e§lTNT Destructor Ligero" + (sticky ? " (Pegajosa)" : "");
            }
            case "tnt_wall_breaker" -> {
                item = new ItemStack(Material.TNT);
                displayName = "§6§lTNT Perforadora de Paredes" + (sticky ? " (Pegajosa)" : "");
            }
            case "tnt_radar" -> {
                item = new ItemStack(Material.TNT);
                displayName = "§b§lTNT Radar de Núcleos" + (sticky ? " (Pegajosa)" : "");
            }
            default -> {
                return null;
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.getPersistentDataContainer().set(Keys.ITEM_TYPE, PersistentDataType.STRING, base);
            if (base.startsWith("core_tier")) {
                int tier = Integer.parseInt(base.substring(base.length() - 1));
                meta.getPersistentDataContainer().set(Keys.CORE_TIER, PersistentDataType.INTEGER, tier);
            } else {
                meta.getPersistentDataContainer().set(Keys.STICKY_FLAG, PersistentDataType.BYTE, (byte) (sticky ? 1 : 0));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // -----------------------------------------------------------------
    // Tab completion
    // -----------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("prote", "give", "admin"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("prote")) {
            return filter(List.of("add", "delete", "list", "members"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(List.of("testprote", "removeprote"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(GIVE_ITEMS, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("prote")
                && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("delete"))) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("testprote")) {
            return filter(List.of("1", "2", "3", "4"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "4", "16", "64"), args[3]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
