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
package org.drasyl.pipeline.skeleton;

import org.drasyl.pipeline.address.Address;
import org.drasyl.util.TypeParameterMatcher;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of address.
 *
 * @param <A> the type of the {@link Address}.
 */
@SuppressWarnings("java:S118")
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
