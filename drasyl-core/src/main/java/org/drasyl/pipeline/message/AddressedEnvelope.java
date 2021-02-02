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
package org.drasyl.pipeline.message;

import org.drasyl.pipeline.address.Address;

/**
 * A message with a sender address and a recipient address.
 *
 * @param <A> the type of the address
 */
public interface AddressedEnvelope<A extends Address, M> {
    /**
     * Returns this message's sender.
     *
     * @return this message's sender.
     */
    A getSender();

    /**
     * Returns this message's recipient.
     *
     * @return this message's recipient.
     */
    A getRecipient();

    /**
     * Returns the message wrapped by this envelope message.
     *
     * @return the message wrapped by this envelope message.
     */
    M getContent();
}
