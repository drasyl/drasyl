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
        this.groupsSet = Set.copyOf(requireNonNull(builder.groupsSet));
    }

    public GroupsClientConfig(final Config options) {
        groupsSet = Set.copyOf(getGroupOptions(options, GROUPS));
    }

    @SuppressWarnings("SameParameterValue")
    private static Set<GroupUri> getGroupOptions(final Config config, final String path) {
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
    public static Builder newBuilder() {
        return newBuilder(DEFAULT);
    }

    /**
     * Returns a specific builder for a {@link GroupsClientConfig}.
     *
     * @return {@link GroupsClientConfig} builder
     */
    public static Builder newBuilder(final GroupsClientConfig config) {
        return new Builder(config);
    }

    /**
     * Implements the builder-pattern for this configuration.
     */
    public static class Builder {
        Set<GroupUri> groupsSet;

        public Builder(final GroupsClientConfig config) {
            groupsSet = new HashSet<>(config.getGroups());
        }

        public Builder groups(final Set<GroupUri> groups) {
            this.groupsSet = groups;
            return this;
        }

        public Builder addGroup(final GroupUri group) {
            this.groupsSet.add(group);
            return this;
        }

        public GroupsClientConfig build() {
            return new GroupsClientConfig(this);
        }
    }
}
