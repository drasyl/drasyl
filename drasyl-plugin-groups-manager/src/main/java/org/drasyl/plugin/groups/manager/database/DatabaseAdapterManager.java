/*
 * Copyright (c) 2020-2021.
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
