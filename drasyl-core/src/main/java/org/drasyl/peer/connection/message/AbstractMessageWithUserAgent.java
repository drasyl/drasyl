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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Represents a message that contains the user agent.
 */
@SuppressWarnings({ "squid:S1444", "squid:ClassVariableVisibilityCheck" })
public abstract class AbstractMessageWithUserAgent<T extends Message> extends AbstractMessage<T> {
    public static final Supplier<String> defaultUserAgentGenerator = () -> {
        // Fallback is required because of this problem: https://git.informatik.uni-hamburg.de/smartcity2019/sensornetz/issues/75
        Config conf = ConfigFactory.load()
                .withFallback(ConfigFactory.load(AbstractMessageWithUserAgent.class.getClassLoader()));

        return conf.getString("drasyl.user-agent") + " (" + System.getProperty("os.name") + "; "
                + System.getProperty("os.arch") + "; Java/"
                + System.getProperty("java.vm.specification.version") + ":" + System.getProperty("java.version.date")
                + ")";
    };
    public static Supplier<String> userAgentGenerator = defaultUserAgentGenerator;
    private final String userAgent;

    public AbstractMessageWithUserAgent() {
        this(userAgentGenerator.get());
    }

    AbstractMessageWithUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userAgent);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AbstractMessageWithUserAgent<?> that = (AbstractMessageWithUserAgent<?>) o;
        return Objects.equals(userAgent, that.userAgent);
    }

    @Override
    public String toString() {
        return "AbstractMessageWithUserAgent{" +
                "userAgent='" + userAgent + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }
}
