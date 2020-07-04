package org.drasyl.peer.connection.direct;

import com.google.common.cache.CacheBuilder;
import org.drasyl.identity.CompressedPublicKey;

import java.time.Duration;
import java.util.concurrent.ConcurrentMap;

/**
 * This class caches all peers for which information has been requested. This cache deletes all
 * entries automatically after the time specified in <code>expireTime</code>. This class thus
 * ensures that the same peer is not asked for information too frequently.
 */
public class RequestPeerInformationCache {
    private final ConcurrentMap<CompressedPublicKey, Boolean> cache;

    /**
     * Creates a new cache which automatically removes any entry after the duration specified in
     * <code>expireTime</code>. The cache can contain maximum of <code>maximumSize</code> entries.
     * Evicts oldest entries if limit is exceeded.
     *
     * @param maximumSize maximum number of entries cache can contain
     * @param expireTime  time after newly added entries will be automatically removed
     */
    public RequestPeerInformationCache(int maximumSize, Duration expireTime) {
        cache = CacheBuilder.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireTime)
                .<CompressedPublicKey, Boolean>build()
                .asMap();
    }

    /**
     * Adds <code>publicKey</code> to the cache if it is not already cached. Returns
     * <code>true</code> if the key was not already cached. Otherwise <code>false</code> is
     * returned.
     *
     * @param publicKey
     * @return
     */
    public boolean add(CompressedPublicKey publicKey) {
        return cache.putIfAbsent(publicKey, Boolean.TRUE) == null;
    }
}