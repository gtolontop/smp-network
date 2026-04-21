package fr.smp.core.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing using PBKDF2WithHmacSHA256.
 *
 * Format stored in DB: {@code pbkdf2$<iterations>$<saltB64>$<hashB64>}
 *
 * Iterations are tunable per-record so we can raise the work factor over time
 * without invalidating existing hashes — verify() reads the iteration count
 * from the stored string.
 *
 * Verification uses {@link MessageDigest#isEqual} for constant-time comparison
 * to defeat timing side channels.
 */
public final class PasswordHasher {

    /** Current cost. ~100ms on modern hardware — appropriate for an interactive login. */
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String ALGO = "PBKDF2WithHmacSHA256";
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordHasher() {}

    public static String hash(String password) {
        if (password == null) throw new IllegalArgumentException("password");
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        return "pbkdf2$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * @return true iff {@code password} produces {@code stored} under the same
     *         parameters. Returns false (without throwing) if {@code stored}
     *         is malformed, so a corrupt record cannot escalate into a crash.
     */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) return false;
        int iter;
        byte[] salt;
        byte[] expected;
        try {
            iter = Integer.parseInt(parts[1]);
            salt = Base64.getDecoder().decode(parts[2]);
            expected = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (iter < 1 || iter > 10_000_000 || salt.length == 0 || expected.length == 0) return false;
        byte[] actual = pbkdf2(password.toCharArray(), salt, iter, expected.length * 8);
        return MessageDigest.isEqual(actual, expected);
    }

    /**
     * @return true if the stored hash uses fewer iterations than the current
     *         policy — the caller should rehash on next successful login.
     */
    public static boolean needsRehash(String stored) {
        if (stored == null) return true;
        String[] parts = stored.split("\\$");
        if (parts.length != 4) return true;
        try {
            return Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
            try {
                return SecretKeyFactory.getInstance(ALGO).generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (Exception e) {
            // PBKDF2WithHmacSHA256 is mandatory in every JDK ≥ 8 — failure here means
            // the JVM is broken and we cannot recover. Surface loudly.
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    /** Defense-in-depth: never log raw passwords, even on debug paths. */
    public static String redact(String password) {
        if (password == null) return "(null)";
        return "(" + password.length() + " chars)";
    }

    @SuppressWarnings("unused")
    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
