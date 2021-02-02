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
package org.drasyl.behaviour;

import org.drasyl.DrasylNode;

import java.util.List;
import java.util.function.Function;

public class DeferredBehavior extends Behavior {
    private final Function<DrasylNode, Behavior> factory;

    public DeferredBehavior(final Function<DrasylNode, Behavior> factory) {
        super(List.of());
        this.factory = factory;
    }

    public Behavior apply(final DrasylNode node) {
        return factory.apply(node);
    }
}
