package miao.byusi.mc.fabric.tipua.util;

import miao.byusi.mc.fabric.tipua.TIPUAMod;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    public static String calculateSha1(File file) throws IOException {
        return calculateHash(file, "SHA-1");
    }

    public static String calculateSha512(File file) throws IOException {
        return calculateHash(file, "SHA-512");
    }

    public static String calculateHash(File file, String algorithm) throws IOException {
        if (!file.exists()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            TIPUAMod.LOGGER.error("Unsupported hash algorithm: {}", algorithm);
            throw new IOException("Unsupported hash algorithm: " + algorithm, e);
        }
    }

    public static boolean verifySha1(File file, String expectedHash) throws IOException {
        if (expectedHash == null || expectedHash.isEmpty()) {
            return true;
        }
        String actualHash = calculateSha1(file);
        boolean matches = actualHash.equalsIgnoreCase(expectedHash);
        if (!matches) {
            TIPUAMod.LOGGER.warn("SHA-1 verification failed: expected={}, actual={}, file={}",
                    expectedHash, actualHash, file.getName());
        }
        return matches;
    }

    public static boolean verifySha512(File file, String expectedHash) throws IOException {
        if (expectedHash == null || expectedHash.isEmpty()) {
            return true;
        }
        String actualHash = calculateSha512(file);
        boolean matches = actualHash.equalsIgnoreCase(expectedHash);
        if (!matches) {
            TIPUAMod.LOGGER.warn("SHA-512 verification failed: expected={}, actual={}, file={}",
                    expectedHash, actualHash, file.getName());
        }
        return matches;
    }

    public static boolean verifyAll(File file, String expectedSha1, String expectedSha512) throws IOException {
        return verifySha1(file, expectedSha1) && verifySha512(file, expectedSha512);
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }
}