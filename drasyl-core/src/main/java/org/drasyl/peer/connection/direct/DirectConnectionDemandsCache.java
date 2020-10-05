/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

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
    public DirectConnectionDemandsCache(final int maximumSize, final Duration expireTime) {
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
    public void add(final CompressedPublicKey publicKey) {
        cache.put(publicKey, Boolean.TRUE);
    }

    /**
     * @param publicKey the public key to be checked
     * @return <code>true</code>, if <code>publicKey</code> is contained in cache. Otherwise
     * <code>false</code> is returned
     */
    public boolean contains(final CompressedPublicKey publicKey) {
        return cache.containsKey(publicKey);
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        cache.clear();
    }
}