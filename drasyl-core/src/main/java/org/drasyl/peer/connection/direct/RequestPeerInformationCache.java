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
    public RequestPeerInformationCache(final int maximumSize, final Duration expireTime) {
        cache = CacheBuilder.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireTime)
                .<CompressedPublicKey, Boolean>build()
                .asMap();
    }

    /**
     * Adds <code>publicKey</code> to the cache if it is not already cached.
     *
     * @param publicKey the public key that should be added
     * @return <code>true</code> if the key was not already cached, otherwise <code>false</code> is
     * returned
     */
    public boolean add(final CompressedPublicKey publicKey) {
        return cache.putIfAbsent(publicKey, Boolean.TRUE) == null;
    }
}