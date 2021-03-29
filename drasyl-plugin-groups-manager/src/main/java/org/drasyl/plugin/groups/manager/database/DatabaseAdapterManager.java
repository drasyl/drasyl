/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.plugin.groups.manager.database;

import org.drasyl.plugin.groups.manager.database.jdbc.JDBCDatabaseAdapter;
import org.drasyl.util.ThrowingFunction;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public final class DatabaseAdapterManager {
    private static final Map<String, ThrowingFunction<URI, DatabaseAdapter, DatabaseException>> ADAPTERS = new HashMap<>();

    static {
        addAdapter(JDBCDatabaseAdapter.SCHEME, JDBCDatabaseAdapter::new);
    }

    private DatabaseAdapterManager() {
        // util class
    }

    public static DatabaseAdapter initAdapter(final URI uri) throws DatabaseException {
        final String scheme = uri.getScheme();
        final ThrowingFunction<URI, DatabaseAdapter, DatabaseException> adapter = ADAPTERS.get(scheme);

        return adapter.apply(uri);
    }

    public static void addAdapter(final String scheme,
                                  final ThrowingFunction<URI, DatabaseAdapter, DatabaseException> adapter) {
        ADAPTERS.put(scheme, adapter);
    }
}
