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
package org.drasyl.plugin.groups.client;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * This class represents the configuration for the {@link GroupsClientPlugin}.
 * <p>
 * This is an immutable object.
 */
public class GroupsClientConfig {
    static final GroupsClientConfig DEFAULT = new GroupsClientConfig(ConfigFactory.defaultReference().getConfig("drasyl.plugins.\"" + GroupsClientPlugin.class.getName() + "\""));
    //======================================== Config Paths ========================================
    static final String GROUPS = "groups";
    //======================================= Config Values ========================================
    private final Set<GroupUri> groupsSet;

    public GroupsClientConfig(final Builder builder) {
        this.groupsSet = requireNonNull(builder.groups);
    }

    public GroupsClientConfig(final Config options) {
        groupsSet = getGroupOptions(options, GROUPS);
    }

    private Set<GroupUri> getGroupOptions(final Config config, final String path) {
        final Set<GroupUri> options = new HashSet<>();

        for (final String groupsURL : config.getStringList(path)) {
            options.add(GroupUri.of(groupsURL));
        }

        return options;
    }

    public Set<GroupUri> getGroups() {
        return groupsSet;
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupsSet);
    }

    @Override
    public String toString() {
        return "GroupsClientConfig{" +
                "groups=" + groupsSet +
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
        final GroupsClientConfig that = (GroupsClientConfig) o;
        return Objects.equals(groupsSet, that.groupsSet);
    }

    //======================================= Config Builder =======================================

    /**
     * Returns a specific builder for a {@link GroupsClientConfig}.
     *
     * @return {@link GroupsClientConfig} builder
     */
    public static Builder builder() {
        return builder(DEFAULT);
    }

    /**
     * Returns a specific builder for a {@link GroupsClientConfig}.
     *
     * @return {@link GroupsClientConfig} builder
     */
    public static Builder builder(final GroupsClientConfig config) {
        return new Builder(config);
    }

    /**
     * Implements the builder-pattern for this configuration.
     */
    public static class Builder {
        Set<GroupUri> groups;

        public Builder(final GroupsClientConfig config) {
            groups = config.getGroups();
        }

        public Builder addGroupOptions(final GroupUri group) {
            this.groups.add(group);
            return this;
        }

        public GroupsClientConfig build() {
            return new GroupsClientConfig(this);
        }
    }
}
