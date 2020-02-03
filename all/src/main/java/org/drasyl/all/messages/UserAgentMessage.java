/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.messages;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.function.Supplier;

/**
 * Represents a message that contains the user agent.
 */
@SuppressWarnings({"squid:S1444", "squid:ClassVariableVisibilityCheck"})
public class UserAgentMessage extends AbstractMessage implements Message {
    public static final Supplier<String> defaultUserAgentGenerator = () -> {
        Config conf = ConfigFactory.load();

        return conf.getString("drasyl.user-agent") + " (" + System.getProperty("os.name") + "; "
                + System.getProperty("os.arch") + "; Java/"
                + System.getProperty("java.vm.specification.version") + ":" + System.getProperty("java.version.date")
                + ")";
    };
    public static Supplier<String> userAgentGenerator = defaultUserAgentGenerator;

    private final String userAgent;

    UserAgentMessage(String userAgent) {
        this.userAgent = userAgent;
    }

    public UserAgentMessage() {
        this(userAgentGenerator.get());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [messageID=" + getMessageID() + ", User-Agent=" + getUserAgent() + "]";
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UserAgentMessage;
    }

    @Override
    public int hashCode() {
        return 42;
    }
}
