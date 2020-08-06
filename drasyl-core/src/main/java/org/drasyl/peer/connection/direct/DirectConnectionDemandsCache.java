package org.drasyl.peer.connection.direct;

import com.google.common.cache.CacheBuilder;
import org.drasyl.identity.CompressedPublicKey;

import java.time.Duration;
import java.util.concurrent.ConcurrentMap;

/**
 * This class caches all peers for a direct connection has been demanded. This cache deletes all
 * entries automatically after the time specified in <code>expireTime</code>. This class thus can be
 * used to track currently demanded direct connections.
 */
public class DirectConnectionDemandsCache {
    private final ConcurrentMap<CompressedPublicKey, Boolean> cache;

    /**
     * Creates a new cache which automatically removes any entry after the duration specified in
     * <code>expireTime</code>. The cache can contain maximum of <code>maximumSize</code> entries.
     * Evicts oldest entries if limit is exceeded.
     *
     * @param maximumSize maximum number of entries cache can contain
     * @param expireTime  time after newly added entries will be automatically removed
     */
    public DirectConnectionDemandsCache(int maximumSize, Duration expireTime) {
        cache = CacheBuilder.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireTime)
                .<CompressedPublicKey, Boolean>build()
                .asMap();
    }

    /**
     * Adds <code>publicKey</code> to the cache.
     *
     * @param publicKey public key to be added
     */
    public void add(CompressedPublicKey publicKey) {
        cache.put(publicKey, Boolean.TRUE);
    }

    /**
     * @param publicKey the public key to be checked
     * @return <code>true</code>, if <code>publicKey</code> is contained in cache. Otherwise
     * <code>false</code> is returned
     */
    public boolean contains(CompressedPublicKey publicKey) {
        return cache.containsKey(publicKey);
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        cache.clear();
    }
}