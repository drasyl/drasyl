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
package org.drasyl.plugins.pipeline;

import org.drasyl.plugins.handler.Handler;
import org.drasyl.plugins.handler.HandlerContext;

import java.util.NoSuchElementException;

public interface Pipeline {
    /**
     * Inserts a {@link Handler} at the first position of this pipeline.
     *
     * @param name    the name of the handler to insert first
     * @param handler the handler to insert first
     * @throws IllegalArgumentException if there's an entry with the same name already in the
     *                                  pipeline
     * @throws NullPointerException     if the specified handler is {@code null}
     */
    void addFirst(String name, Handler handler);

    /**
     * Appends a {@link Handler} at the last position of this pipeline.
     *
     * @param name    the name of the handler to append
     * @param handler the handler to append
     * @throws IllegalArgumentException if there's an entry with the same name already in the
     *                                  pipeline
     * @throws NullPointerException     if the specified handler is {@code null}
     */
    void addLast(String name, Handler handler);

    /**
     * Inserts a {@link Handler} before an existing handler of this pipeline.
     *
     * @param baseName the name of the existing handler
     * @param name     the name of the handler to insert before
     * @param handler  the handler to insert before
     * @throws NoSuchElementException   if there's no such entry with the specified {@code
     *                                  baseName}
     * @throws IllegalArgumentException if there's an entry with the same name already in the
     *                                  pipeline
     * @throws NullPointerException     if the specified baseName or handler is {@code null}
     */
    void addBefore(String baseName, String name, Handler handler);

    /**
     * Inserts a {@link Handler} after an existing handler of this pipeline.
     *
     * @param baseName the name of the existing handler
     * @param name     the name of the handler to insert after
     * @param handler  the handler to insert after
     * @throws NoSuchElementException   if there's no such entry with the specified {@code
     *                                  baseName}
     * @throws IllegalArgumentException if there's an entry with the same name already in the
     *                                  pipeline
     * @throws NullPointerException     if the specified baseName or handler is {@code null}
     */
    void addAfter(String baseName, String name, Handler handler);

    /**
     * Removes the {@link Handler} with the specified name from this pipeline.
     *
     * @param name the name under which the {@link Handler} was stored.
     * @throws NoSuchElementException if there's no such handler with the specified name in this
     *                                pipeline
     * @throws NullPointerException   if the specified name is {@code null}
     */
    void remove(String name);

    /**
     * Replaces the {@link Handler} of the specified name with a new handler in this pipeline.
     *
     * @param oldName    the name of the {@link Handler} to be replaced
     * @param newName    the name under which the replacement should be added
     * @param newHandler the {@link Handler} which is used as replacement
     * @throws NoSuchElementException   if the handler with the specified old name does not exist in
     *                                  this pipeline
     * @throws IllegalArgumentException if a handler with the specified new name already exists in
     *                                  this pipeline, except for the handler to be replaced
     * @throws NullPointerException     if the specified old handler or new handler is {@code null}
     */
    void replace(String oldName, String newName, Handler newHandler);

    /**
     * Returns the {@link Handler} with the specified name in this pipeline.
     *
     * @return the handler with the specified name. {@code null} if there's no such handler in this
     * pipeline.
     */
    Handler get(String name);

    /**
     * Returns the context object of the {@link Handler} with the specified name in this pipeline.
     *
     * @return the context object of the handler with the specified name. {@code null} if there's no
     * such handler in this pipeline.
     */
    HandlerContext context(String name);


}
