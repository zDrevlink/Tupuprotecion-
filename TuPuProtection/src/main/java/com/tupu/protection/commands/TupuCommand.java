package com.tupu.protection.commands;

import com.tupu.protection.TuPuProtection;
import com.tupu.protection.core.CoreManager;
import com.tupu.protection.core.ProtectionCore;
import com.tupu.protection.util.Keys;
import org.bukkit.ChatColor;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class TupuCommand implements CommandExecutor, TabCompleter {

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
            sender.sendMessage(ChatColor.YELLOW + "Uso: /tupu <prote|give> ...");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "prote" -> handleProte(sender, args);
            case "give" -> handleGive(sender, args);
            default -> sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /tupu prote o /tupu give.");
        }
        return true;
    }

    // -----------------------------------------------------------------
    // /tupu prote add|delete|list
    // -----------------------------------------------------------------

    private void handleProte(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede usarse en el juego.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Uso: /tupu prote <add|delete|list> [jugador]");
            return;
        }

        Optional<ProtectionCore> coreOpt =
                coreManager.getCoreOwnedByStandingAt(player.getUniqueId(), player.getLocation());
        if (coreOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No estás parado dentro de una protección de la que seas dueño.");
            return;
        }
        ProtectionCore core = coreOpt.get();

        switch (args[1].toLowerCase(Locale.ROOT)) {
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
            case "list" -> {
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
            default -> player.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa add, delete o list.");
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
                item = new ItemStack(Material.BEACON);
                displayName = "§a§lNúcleo TuPu - Tier 1";
            }
            case "core_tier2" -> {
                item = new ItemStack(Material.BEACON);
                displayName = "§e§lNúcleo TuPu - Tier 2";
            }
            case "core_tier3" -> {
                item = new ItemStack(Material.BEACON);
                displayName = "§c§lNúcleo TuPu - Tier 3";
            }
            case "core_tier4" -> {
                item = new ItemStack(Material.BEACON);
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
            return filter(List.of("prote", "give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("prote")) {
            return filter(List.of("add", "delete", "list"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(GIVE_ITEMS, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("prote")
                && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("delete"))) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
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
