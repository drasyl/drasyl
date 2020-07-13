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

import org.drasyl.DrasylNode;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Represents a message that contains the user agent.
 */
@SuppressWarnings({ "squid:S1444", "squid:ClassVariableVisibilityCheck" })
abstract class AbstractMessageWithUserAgent extends AbstractMessage {
    public static final Supplier<String> defaultUserAgentGenerator = () -> "drasyl/" + DrasylNode.getVersion() + " (" + System.getProperty("os.name") + "; "
            + System.getProperty("os.arch") + "; Java/"
            + System.getProperty("java.vm.specification.version") + ":" + System.getProperty("java.version.date")
            + ")";
    public static Supplier<String> userAgentGenerator = defaultUserAgentGenerator;
    private final String userAgent;

    public AbstractMessageWithUserAgent() {
        this(userAgentGenerator.get());
    }

    AbstractMessageWithUserAgent(String userAgent) {
        this.userAgent = requireNonNull(userAgent);
    }

    public AbstractMessageWithUserAgent(String id, String userAgent) {
        super(id);
        this.userAgent = requireNonNull(userAgent);
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }
}
