package com.tupu.protection.core;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Representa una protección activa: su dueño, su tier, su vida (HP) y el
 * área cúbica (X/Z completo, Y de -64 a 320) que defiende.
 */
public class ProtectionCore {

    private final UUID id;
    private final UUID owner;
    private final int tier;
    private final int sizeXZ;
    private final double maxHp;
    private double currentHp;
    private Location center;
    private final Set<UUID> members = new LinkedHashSet<>();

    public ProtectionCore(UUID id, UUID owner, int tier, int sizeXZ, double maxHp, Location center) {
        this.id = id;
        this.owner = owner;
        this.tier = tier;
        this.sizeXZ = sizeXZ;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.center = center.clone();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getTier() {
        return tier;
    }

    public int getSizeXZ() {
        return sizeXZ;
    }

    public double getMaxHp() {
        return maxHp;
    }

    public double getCurrentHp() {
        return currentHp;
    }

    public Location getCenter() {
        return center.clone();
    }

    public World getWorld() {
        return center.getWorld();
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isAlive() {
        return currentHp > 0;
    }

    public boolean isAuthorized(UUID playerId) {
        return playerId.equals(owner) || members.contains(playerId);
    }

    public void addMember(UUID playerId) {
        members.add(playerId);
    }

    public boolean removeMember(UUID playerId) {
        return members.remove(playerId);
    }

    /**
     * Aplica daño al núcleo. Devuelve true si el núcleo quedó destruido
     * (HP <= 0) como resultado de este impacto.
     */
    public boolean damage(double amount) {
        if (!isAlive()) {
            return false;
        }
        currentHp = Math.max(0, currentHp - amount);
        return currentHp <= 0;
    }

    /** Restaura el núcleo a su HP máximo (uso administrativo / al recrear la protección). */
    public void resetHp() {
        currentHp = maxHp;
    }

    /**
     * Comprueba si una ubicación cae dentro del área protegida por este
     * núcleo: un cuadrado en X/Z centrado en el núcleo, a lo largo de todo
     * el rango de altura construible (Y -64 a 320), tal como especifica el diseño.
     */
    public boolean contains(Location loc) {
        if (loc.getWorld() == null || center.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().equals(center.getWorld())) {
            return false;
        }

        int half = sizeXZ / 2;
        double minX = center.getX() - half;
        double maxX = center.getX() + half;
        double minZ = center.getZ() - half;
        double maxZ = center.getZ() + half;

        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getZ() >= minZ && loc.getZ() <= maxZ
                && loc.getY() >= -64 && loc.getY() <= 320;
    }
}
