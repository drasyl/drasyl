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
package org.drasyl.plugin.groups.manager;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.drasyl.DrasylConfigException;
import org.drasyl.plugin.groups.manager.data.Group;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.DrasylConfig.getByte;
import static org.drasyl.DrasylConfig.getInetAddress;
import static org.drasyl.DrasylConfig.getURI;
import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This class represents the configuration for the {@link GroupsManagerPlugin}.
 * <p>
 * This is an immutable object.
 */
public class GroupsManagerConfig {
    static final GroupsManagerConfig DEFAULT = new GroupsManagerConfig(ConfigFactory.defaultReference().getConfig("drasyl.plugins.\"" + GroupsManagerPlugin.class.getName() + "\""));
    //======================================== Config Paths ========================================
    static final String GROUPS = "groups";
    static final String GROUP_SECRET = "secret";
    static final String GROUP_TIMEOUT = "timeout";
    static final String GROUP_MIN_DIFFICULTY = "min-difficulty";
    static final String DATABASE_URI = "database.uri";
    static final String API_ENABLED = "api.enabled";
    static final String API_BIND_HOST = "api.bind-host";
    static final String API_BIND_PORT = "api.bind-port";
    private final URI databaseUri;
    private final Map<String, Group> groupsMap;
    private final boolean apiEnabled;
    private final InetAddress apiBindHost;
    private final int apiBindPort;

    GroupsManagerConfig(final Builder builder) {
        this.databaseUri = requireNonNull(builder.databaseUri);
        this.groupsMap = Map.copyOf(requireNonNull(builder.groups));
        this.apiEnabled = builder.apiEnabled;
        this.apiBindHost = requireNonNull(builder.apiBindHost);
        this.apiBindPort = builder.apiBindPort;
    }

    public GroupsManagerConfig(final Config config) {
        databaseUri = getURI(config, DATABASE_URI);
        groupsMap = Map.copyOf(getGroups(config, GROUPS));
        this.apiEnabled = config.getBoolean(API_ENABLED);
        this.apiBindHost = getInetAddress(config, API_BIND_HOST);
        this.apiBindPort = config.getInt(API_BIND_PORT);
    }

    @SuppressWarnings("SameParameterValue")
    private static Map<String, Group> getGroups(final Config config, final String path) {
        final Map<String, Group> groups = new HashMap<>();

        for (final Map.Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
            final String name = entry.getKey();
            /*
             * Here a key is intentionally used and immediately deleted. atPath() could throw an
             * exception if the group name contains a $ character.
             */
            final Config groupConfig = entry.getValue().atKey("group").getConfig("group"); // NOSONAR

            final byte minDifficulty;
            if (groupConfig.hasPath(GROUP_MIN_DIFFICULTY)) {
                minDifficulty = getByte(groupConfig, GROUP_MIN_DIFFICULTY);
            }
            else {
                minDifficulty = Group.GROUP_DEFAULT_MIN_DIFFICULTY;
            }

            final Duration timeout;
            if (groupConfig.hasPath(GROUP_TIMEOUT)) {
                timeout = groupConfig.getDuration(GROUP_TIMEOUT);
            }
            else {
                timeout = Group.GROUP_DEFAULT_TIMEOUT;
            }

            try {
                groups.put(name, Group.of(
                        name,
                        groupConfig.getString(GROUP_SECRET),
                        minDifficulty,
                        timeout));
            }
            catch (final IllegalArgumentException e) {
                throw new DrasylConfigException(e);
            }
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

    public boolean isApiEnabled() {
        return apiEnabled;
    }

    public InetAddress getApiBindHost() {
        return apiBindHost;
    }

    public int getApiBindPort() {
        return apiBindPort;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupsManagerConfig that = (GroupsManagerConfig) o;
        return apiEnabled == that.apiEnabled &&
                apiBindPort == that.apiBindPort &&
                Objects.equals(databaseUri, that.databaseUri) &&
                Objects.equals(groupsMap, that.groupsMap) &&
                Objects.equals(apiBindHost, that.apiBindHost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseUri, groupsMap, apiEnabled, apiBindHost, apiBindPort);
    }

    //======================================= Config Builder =======================================

    /**
     * Returns a specific builder for a {@link GroupsManagerConfig}.
     *
     * @return {@link GroupsManagerConfig} builder
     */
    public static Builder newBuilder() {
        return newBuilder(DEFAULT);
    }

    /**
     * Returns a specific builder for a {@link GroupsManagerConfig}.
     *
     * @return {@link GroupsManagerConfig} builder
     */
    public static Builder newBuilder(final GroupsManagerConfig config) {
        return new Builder(config);
    }

    /**
     * Implements the builder-pattern for this configuration.
     */
    @SuppressWarnings("java:S2972")
    public static class Builder {
        Map<String, Group> groups;
        URI databaseUri;
        boolean apiEnabled;
        InetAddress apiBindHost;
        int apiBindPort;

        public Builder(final GroupsManagerConfig config) {
            groups = new HashMap<>(config.getGroups());
            databaseUri = config.getDatabaseUri();
            apiEnabled = config.isApiEnabled();
            apiBindHost = config.getApiBindHost();
            apiBindPort = config.getApiBindPort();
        }

        public Builder groups(final Map<String, Group> groups) {
            this.groups = groups;
            return this;
        }

        public Builder databaseUri(final URI databaseUri) {
            this.databaseUri = databaseUri;
            return this;
        }

        public Builder apiEnabled(final boolean apiEnabled) {
            this.apiEnabled = apiEnabled;
            return this;
        }

        public Builder apiBindHost(final InetAddress apiBindHost) {
            this.apiBindHost = apiBindHost;
            return this;
        }

        public Builder apiBindPort(final int apiBindPort) {
            this.apiBindPort = apiBindPort;
            return this;
        }

        public GroupsManagerConfig build() {
            return new GroupsManagerConfig(this);
        }
    }
}
