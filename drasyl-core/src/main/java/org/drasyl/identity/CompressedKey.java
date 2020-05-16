package org.drasyl.identity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.crypto.CryptoException;

import java.util.Objects;

public abstract class CompressedKey<K> {
    @JsonValue
    protected final String compressedKey;
    @JsonIgnore
    protected K key;

    CompressedKey() {
        compressedKey = null;
        key = null;
    }

    protected CompressedKey(String compressedKey) throws CryptoException {
        this.compressedKey = compressedKey;
        this.key = toUncompressedKey();
    }

    protected CompressedKey(String compressedKey, K key) {
        this.compressedKey = compressedKey;
        this.key = key;
    }

    public abstract K toUncompressedKey() throws CryptoException;

    public String getCompressedKey() {
        return this.compressedKey;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(compressedKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CompressedKey that = (CompressedKey) o;
        return Objects.equals(compressedKey, that.compressedKey);
    }

    @Override
    public String toString() {
        return this.compressedKey;
    }
}
