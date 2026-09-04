package com.tupu.protection.discord;

import com.tupu.protection.TuPuProtection;
import com.tupu.protection.core.ProtectionCore;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Envía una alerta a un webhook de Discord cada vez que un núcleo recibe
 * daño, respetando un cooldown anti-spam por núcleo (config: discord.alert-cooldown-seconds).
 * El envío HTTP se hace siempre en un hilo asíncrono para no bloquear el servidor.
 */
public class DiscordWebhookNotifier {

    private final TuPuProtection plugin;
    private final Map<UUID, Long> lastAlertMillis = new HashMap<>();

    public DiscordWebhookNotifier(TuPuProtection plugin) {
        this.plugin = plugin;
    }

    public void notifyCoreDamaged(ProtectionCore core, String explosiveLabel) {
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("TU_WEBHOOK_AQUI")) {
            return; // no configurado todavía en config.yml
        }

        int cooldownSeconds = plugin.getConfig().getInt("discord.alert-cooldown-seconds", 60);
        long now = System.currentTimeMillis();
        Long last = lastAlertMillis.get(core.getId());
        if (last != null && (now - last) < cooldownSeconds * 1000L) {
            return; // dentro del cooldown anti-spam
        }
        lastAlertMillis.put(core.getId(), now);

        String ownerName = Bukkit.getOfflinePlayer(core.getOwner()).getName();
        String coords = String.format("X:%.0f Y:%.0f Z:%.0f",
                core.getCenter().getX(), core.getCenter().getY(), core.getCenter().getZ());

        String json = buildEmbedJson(
                ownerName != null ? ownerName : "Desconocido",
                coords,
                core.getCurrentHp(),
                core.getMaxHp(),
                explosiveLabel
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> sendWebhook(webhookUrl, json));
    }

    private void sendWebhook(String webhookUrl, String json) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code >= 300) {
                plugin.getLogger().warning("El webhook de Discord respondió con código " + code);
            }
            conn.disconnect();
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo enviar la alerta a Discord: " + e.getMessage());
        }
    }

    private String buildEmbedJson(String owner, String coords, double currentHp, double maxHp, String explosiveLabel) {
        String description = String.format(
                "**Dueño:** %s\\n**Coordenadas:** %s\\n**HP restante:** %.0f / %.0f\\n**Explosivo usado:** %s",
                escape(owner), escape(coords), currentHp, maxHp, escape(explosiveLabel)
        );

        return "{"
                + "\"embeds\":[{"
                + "\"title\":\"\\u26A0\\uFE0F N\\u00FAcleo bajo ataque\","
                + "\"description\":\"" + description + "\","
                + "\"color\":15158332"
                + "}]"
                + "}";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
