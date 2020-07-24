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

package org.drasyl.peer.connection.message;

/**
 * Messages of this type represent a response to a previously received {@link RequestMessage}.
 */
@SuppressWarnings("java:S2326")
public interface ResponseMessage<R extends RequestMessage> extends Message {
    /**
     * Returns the id of the {@link RequestMessage} to which this response corresponds.
     *
     * @return
     */
    MessageId getCorrespondingId();
}
