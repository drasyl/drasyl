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
package org.drasyl.plugin.groups.client;

import com.typesafe.config.Config;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * This class models the options of the groups slave plugin.
 * <p>
 * This is an immutable object.
 */
public class GroupsClientConfig {
    //======================================== Config Paths ========================================
    static final String GROUPS = "groups";
    //======================================= Config Values ========================================
    private final Set<GroupURI> groupsSet;

    public GroupsClientConfig(final Builder builder) {
        this.groupsSet = requireNonNull(builder.groups);
    }

    public GroupsClientConfig(final Config options) {
        groupsSet = getGroupOptions(options, GROUPS);
    }

    private Set<GroupURI> getGroupOptions(final Config config, final String path) {
        final Set<GroupURI> options = new HashSet<>();

        for (final String groupsURL : config.getStringList(path)) {
            options.add(GroupURI.of(groupsURL));
        }

        return options;
    }

    public Set<GroupURI> getGroups() {
        return groupsSet;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groupsSet);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final GroupsClientConfig config = (GroupsClientConfig) o;
        return Objects.equals(groupsSet, config.groupsSet);
    }

    @Override
    public String toString() {
        return "GroupsClientConfig{" +
                "groups=" + groupsSet +
                '}';
    }

    //======================================= Config Builder =======================================

    /**
     * Returns a specific builder for a {@link GroupsClientConfig}.
     *
     * @return {@link GroupsClientConfig} builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Implements the builder-pattern for this configuration.
     */
    public static class Builder {
        final Set<GroupURI> groups;

        Builder() {
            groups = new HashSet<>();
        }

        public Builder addGroupOptions(final GroupURI group) {
            this.groups.add(group);
            return this;
        }

        public GroupsClientConfig build() {
            return new GroupsClientConfig(this);
        }
    }
}
