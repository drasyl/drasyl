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
package org.drasyl.plugins;

/**
 * This interface is implemented by all drasyl plugins.
 */
public interface DrasylPlugin {
    /**
     * The plugin name must be unique.
     *
     * @return the plugin name
     */
    String name();

    /**
     * This method gets called when the plugin was removed from drasyl.
     */
    void onRemove();

    /**
     * This method gets called when the plugin was added to drasyl.
     */
    void onAdded();
}