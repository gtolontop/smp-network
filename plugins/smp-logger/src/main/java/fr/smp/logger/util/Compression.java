package fr.smp.logger.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** DEFLATE wrappers — no native deps, ~2x smaller for typical NBT/YAML payloads. */
public final class Compression {

    private Compression() {}

    public static byte[] deflate(byte[] in) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, in.length / 2));
        Deflater def = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream out = new DeflaterOutputStream(baos, def)) {
            out.write(in);
        } catch (IOException e) {
            throw new RuntimeException("deflate failed", e);
        } finally {
            def.end();
        }
        return baos.toByteArray();
    }

    public static byte[] inflate(byte[] in) {
        try (InflaterInputStream is = new InflaterInputStream(new ByteArrayInputStream(in));
             ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, in.length * 2))) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("inflate failed", e);
        }
    }
}
