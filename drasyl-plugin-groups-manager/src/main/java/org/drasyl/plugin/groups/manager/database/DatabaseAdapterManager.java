package org.drasyl.plugin.groups.manager.database;

import org.drasyl.plugin.groups.manager.database.jdbc.JDBCDatabaseAdapter;
import org.drasyl.util.DrasylFunction;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class DatabaseAdapterManager {
    private static final Map<String, DrasylFunction<URI, DatabaseAdapter, DatabaseException>> ADAPTERS = new HashMap<>();

    static {
        addAdapter(JDBCDatabaseAdapter.SCHEME, JDBCDatabaseAdapter::new);
    }

    private DatabaseAdapterManager() {
        // util class
    }

    public static DatabaseAdapter initAdapter(final URI uri) throws DatabaseException {
        final String scheme = uri.getScheme();
        final DrasylFunction<URI, DatabaseAdapter, DatabaseException> adapter = ADAPTERS.get(scheme);

        return adapter.apply(uri);
    }

    public static void addAdapter(final String scheme,
                                  final DrasylFunction<URI, DatabaseAdapter, DatabaseException> adapter) {
        ADAPTERS.put(scheme, adapter);
    }
}
