package fr.smp.logger.items;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Canonicalizes an ItemStack to deterministic bytes (Bukkit's binary serialization
 * with amount=1) and produces:
 *   - the canonical serialized payload (for storage)
 *   - a 16-byte truncated SHA-256 (for the precious_items PK + per-event reference)
 */
public final class ItemHasher {

    private ItemHasher() {}

    public record Canonical(byte[] payload, byte[] hash) {}

    /** Serialize amount=1 so 1× and 64× of the same custom item collapse to one row. */
    public static Canonical canonical(ItemStack item) {
        try {
            ItemStack one = item.clone();
            one.setAmount(1);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos)) {
                out.writeObject(one);
            }
            byte[] payload = baos.toByteArray();
            byte[] hash = sha256_16(payload);
            return new Canonical(payload, hash);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize item", e);
        }
    }

    public static byte[] sha256_16(byte[] in) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] full = d.digest(in);
            byte[] out = new byte[16];
            System.arraycopy(full, 0, out, 0, 16);
            return out;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
