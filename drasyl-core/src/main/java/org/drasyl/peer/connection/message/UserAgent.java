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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.DrasylNode;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

/**
 * This UserAgent is attached to each message and allows the recipient to learn about the
 * capabilities and configuration of the sender.
 */
public class UserAgent {
    private static final Pattern USER_AGENT_PATTERN = Pattern.compile("^drasyl/([^)]++\\))(?: ?\\((.*)\\))?");
    @JsonValue
    private final String text;

    @JsonCreator
    UserAgent(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

    /**
     * Tries to extract the drasyl version from this user agent.
     *
     * @return the drasyl version or {@code null} if not found
     */
    public String getDrasylVersion() {
        final Matcher matcher = USER_AGENT_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        else {
            return null;
        }
    }

    /**
     * Tries to extract the comments from this user agent.
     *
     * @return the comments or an empty {@link Set} if not found
     */
    public Set<String> getComments() {
        final Matcher matcher = USER_AGENT_PATTERN.matcher(text);
        if (matcher.find() && matcher.group(2) != null) {
            return Arrays.stream(matcher.group(2).split(";")).map(String::trim).filter(not(String::isBlank)).collect(Collectors.toSet());
        }
        else {
            return Set.of();
        }
    }

    /**
     * Generates a new user agent with drasyl version, operation system type/version, system
     * architecture, and java version used by this node.
     *
     * @return the generated user agent
     */
    public static UserAgent generate() {
        return new UserAgent(String.format(
                "drasyl/%s (%s/%s; %s; Java/%s:%s)",
                DrasylNode.getVersion(),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                System.getProperty("java.vm.specification.version"),
                System.getProperty("java.version.date")
        ));
    }
}
