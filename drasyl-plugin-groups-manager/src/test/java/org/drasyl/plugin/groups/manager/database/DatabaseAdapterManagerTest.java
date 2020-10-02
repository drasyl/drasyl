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
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses />.
 */
package org.drasyl.plugin.groups.manager.database;

import org.drasyl.plugin.groups.manager.database.jdbc.JDBCDatabaseAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
class DatabaseAdapterManagerTest {
    @Test
    void shouldAddAdapter() {
        assertDoesNotThrow(() -> DatabaseAdapterManager.addAdapter("jdbc", JDBCDatabaseAdapter::new));
    }

    @Test
    void shouldInitAdapterAndReturn() throws DatabaseException {
        assertThat(DatabaseAdapterManager.initAdapter(URI.create("jdbc:sqlite:file:groups?mode=memory&cache=shared")), instanceOf(JDBCDatabaseAdapter.class));
    }
}