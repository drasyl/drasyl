package org.drasyl.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * This class contains methods for SHA generation.
 */
public final class Sha {
    private static final MessageDigest SHA1;

    static {
        try {
            SHA1 = MessageDigest.getInstance("SHA1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new InternalError("SHA-1 not supported on this platform - Outdated?");
        }
    }

    private Sha() {
        // util class
    }

    public static byte[] sha1(final byte[] bytes) {
        return SHA1.digest(bytes);
    }
}
