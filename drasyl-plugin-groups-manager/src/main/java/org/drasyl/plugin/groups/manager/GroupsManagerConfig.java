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
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses />.
 */
package org.drasyl.plugin.groups.manager;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigValue;
import org.drasyl.plugin.groups.manager.data.Group;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.DrasylConfig.getShort;
import static org.drasyl.DrasylConfig.getURI;
import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This class represents the configuration for the {@link GroupsManagerPlugin}.
 * <p>
 * This is an immutable object.
 */
public class GroupsManagerConfig {
    //======================================== Config Paths ========================================
    static final String GROUPS = "groups";
    static final String GROUP_SECRET = "secret";
    static final String GROUP_TIMEOUT = "timeout";
    static final String GROUP_MIN_DIFFICULTY = "min-difficulty";
    static final String DATABASE_URI = "database.uri";
    private final URI databaseUri;
    private final Map<String, Group> groupsMap;

    GroupsManagerConfig(final Builder builder) {
        this.databaseUri = requireNonNull(builder.databaseUri);
        this.groupsMap = requireNonNull(builder.groups);
    }

    public GroupsManagerConfig(final Config config) {
        databaseUri = getURI(config, DATABASE_URI);
        groupsMap = getGroups(config, GROUPS);
    }

    private Map<String, Group> getGroups(final Config config, final String path) {
        final Map<String, Group> groups = new HashMap<>();

        for (final Map.Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
            final String name = entry.getKey();
            /*
             * Here a key is intentionally used and immediately deleted. atPath() could throw an
             * exception if the group name contains a $ character.
             */
            final Config groupConfig = entry.getValue().atKey("group").getConfig("group"); // NOSONAR

            short minDifficulty = 0;
            if (groupConfig.hasPath(GROUP_MIN_DIFFICULTY)) {
                minDifficulty = getShort(groupConfig, GROUP_MIN_DIFFICULTY);
                if (minDifficulty < 0) {
                    throw new ConfigException.WrongType(groupConfig.getValue(GROUP_MIN_DIFFICULTY).origin(), GROUP_MIN_DIFFICULTY, "non negative short", "out-of-range-value " + minDifficulty);
                }
            }

            Duration timeout = ofSeconds(60);
            if (groupConfig.hasPath(GROUP_TIMEOUT) && groupConfig.getDuration(GROUP_TIMEOUT).compareTo(timeout) > 0) {
                timeout = groupConfig.getDuration(GROUP_TIMEOUT);
            }

            groups.put(name, Group.of(
                    name,
                    groupConfig.getString(GROUP_SECRET),
                    minDifficulty,
                    timeout));
        }

        return groups;
    }

    public URI getDatabaseUri() {
        return databaseUri;
    }

    public Map<String, Group> getGroups() {
        return groupsMap;
    }

    @Override
    public String toString() {
        return "GroupsManagerConfig{" +
                "databaseUri=" + maskSecret(databaseUri) +
                ", groups=" + groupsMap +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupsManagerConfig config = (GroupsManagerConfig) o;
        return Objects.equals(databaseUri, config.databaseUri) &&
                Objects.equals(groupsMap, config.groupsMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseUri, groupsMap);
    }

    //======================================= Config Builder =======================================

    /**
     * Returns a specific builder for a {@link GroupsManagerConfig}.
     *
     * @return {@link GroupsManagerConfig} builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Implements the builder-pattern for this configuration.
     */
    public static class Builder {
        Map<String, Group> groups;
        URI databaseUri;

        public Builder groups(final Map<String, Group> groups) {
            this.groups = groups;
            return this;
        }

        public Builder databaseUri(final URI databaseUri) {
            this.databaseUri = databaseUri;
            return this;
        }

        public GroupsManagerConfig build() {
            return new GroupsManagerConfig(this);
        }
    }
}
