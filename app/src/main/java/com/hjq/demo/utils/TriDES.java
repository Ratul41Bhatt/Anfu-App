package com.hjq.demo.utils;

import androidx.annotation.Keep;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * DES / triple-DES helpers.
 *
 * <p>Used to derive and check keys the terminal already holds in the clear -
 * chiefly {@code TPK = 3DES-decrypt(DE62, TMK)} and the KCV of that key, so the
 * logon can verify what the PIN pad did with the working key it was given.</p>
 *
 * <h3>Key lengths</h3>
 * <p>{@link #normalise} expands the key to the 24 bytes {@code DESede} requires:
 * an 8-byte single-length key becomes K1K1K1 (which is plain single DES, the
 * layout Hypercom spec 6.27.2 assumes), a 16-byte double-length key becomes
 * K1K2K1, and 24 bytes are used as they are.</p>
 *
 * <p>This used to copy 16 bytes unconditionally, so <b>any 8-byte key threw
 * ArrayIndexOutOfBoundsException</b>. Because the callers wrap these calls in a
 * broad catch, that surfaced as the working-key check silently doing nothing
 * rather than as a crash - which is worse, since a mis-derived PIN key then went
 * unnoticed until the acquirer rejected the PIN block.</p>
 */
@Keep
public class TriDES {

    /**
     * Expand a DES key to the 24 bytes {@code DESede} wants.
     *
     * @param key 8, 16 or 24 bytes
     * @throws IllegalArgumentException on any other length, rather than
     *         silently producing a key nobody can reproduce
     */
    public static byte[] normalise(byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }
        byte[] out = new byte[24];
        switch (key.length) {
            case 8:
                // Single length: K1 K1 K1 - equivalent to single DES.
                System.arraycopy(key, 0, out, 0, 8);
                System.arraycopy(key, 0, out, 8, 8);
                System.arraycopy(key, 0, out, 16, 8);
                return out;
            case 16:
                // Double length: K1 K2 K1.
                System.arraycopy(key, 0, out, 0, 16);
                System.arraycopy(key, 0, out, 16, 8);
                return out;
            case 24:
                System.arraycopy(key, 0, out, 0, 24);
                return out;
            default:
                throw new IllegalArgumentException(
                        "key must be 8, 16 or 24 bytes, got " + key.length);
        }
    }

    private static SecretKey secretKey(byte[] key) throws Exception {
        DESedeKeySpec spec = new DESedeKeySpec(normalise(key));
        return SecretKeyFactory.getInstance("desede").generateSecret(spec);
    }

    /** Data must be a whole number of 8-byte blocks - there is no padding. */
    private static void checkBlocks(byte[] data) {
        if (data == null || data.length == 0 || data.length % 8 != 0) {
            throw new IllegalArgumentException("data must be a non-empty multiple of 8 bytes, got "
                    + (data == null ? "null" : String.valueOf(data.length)));
        }
    }

    public static byte[] des3EncodeECB(byte[] key, byte[] data) throws Exception {
        checkBlocks(data);
        Cipher cipher = Cipher.getInstance("desede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(key));
        return cipher.doFinal(data);
    }

    public static byte[] des3DecodeECB(byte[] key, byte[] data) throws Exception {
        checkBlocks(data);
        Cipher cipher = Cipher.getInstance("desede/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey(key));
        return cipher.doFinal(data);
    }

    public static byte[] des3EncodeCBC(byte[] key, byte[] keyiv, byte[] data) throws Exception {
        checkBlocks(data);
        Cipher cipher = Cipher.getInstance("desede/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(key), new IvParameterSpec(iv(keyiv)));
        return cipher.doFinal(data);
    }

    public static byte[] des3DecodeCBC(byte[] key, byte[] keyiv, byte[] data) throws Exception {
        checkBlocks(data);
        // This used to cast the SecretKey to android.inputmethodservice.Keyboard.Key,
        // which throws ClassCastException the moment it is called.
        Cipher cipher = Cipher.getInstance("desede/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey(key), new IvParameterSpec(iv(keyiv)));
        return cipher.doFinal(data);
    }

    /** An IV is exactly 8 bytes; shorter input is zero filled, longer truncated. */
    private static byte[] iv(byte[] keyiv) {
        byte[] out = new byte[8];
        if (keyiv != null) {
            System.arraycopy(keyiv, 0, out, 0, Math.min(8, keyiv.length));
        }
        return out;
    }

    /**
     * Key Check Value: the first 3 bytes of the key encrypting eight zero bytes.
     *
     * <p>This is the value {@code posGetKcv} returns and the one printed on a
     * key ceremony sheet, so it is what a locally derived key must be compared
     * against.</p>
     *
     * @return 6 uppercase hex characters, or "" if the key is unusable
     */
    public static String kcv(byte[] key) {
        try {
            byte[] out = des3EncodeECB(key, new byte[8]);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 3 && i < out.length; i++) {
                b.append(String.format(java.util.Locale.US, "%02X", out[i] & 0xFF));
            }
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
