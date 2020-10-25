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
package org.drasyl.pipeline;

import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.pipeline.address.Address;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of address.
 *
 * @param <A> the type of the {@link Address}.
 */
public abstract class AddressHandlerAdapter<A> extends HandlerAdapter {
    private final TypeParameterMatcher matcherAddress;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter
     * of the class.
     */
    protected AddressHandlerAdapter() {
        matcherAddress = TypeParameterMatcher.find(this, AddressHandlerAdapter.class, "A");
    }

    /**
     * Create a new instance
     *
     * @param addressType the type of messages to match
     */
    protected AddressHandlerAdapter(final Class<? extends A> addressType) {
        matcherAddress = TypeParameterMatcher.get(addressType);
    }

    /**
     * Returns {@code true} if the given address should be handled, {@code false} otherwise.
     */
    protected boolean acceptAddress(final Address address) {
        return matcherAddress.match(address);
    }
}
