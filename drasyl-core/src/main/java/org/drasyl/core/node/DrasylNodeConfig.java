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
package org.drasyl.core.node;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.drasyl.core.models.DrasylException;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DrasylNodeConfig {
    private final Config config;
    //======================================== Config Paths ========================================
    private static final String DRASYL_IDENTITY_PATH = "drasyl.identity.path";
    private static final String DRASYL_USER_AGENT = "drasyl.user-agent";
    //======================================= Config Values ========================================
    private final Path identityPath;
    private final String userAgent;

    /**
     * Creates a new config for a drasyl node.
     *
     * @param config config to be loaded
     * @throws ConfigException if the given config is invalid
     */
    public DrasylNodeConfig(Config config) throws DrasylException {
        this.config = config;
        config.checkValid(ConfigFactory.defaultReference(), "drasyl");

        // init
        this.userAgent = config.getString(DRASYL_USER_AGENT);

        var idPath = config.getString(DRASYL_IDENTITY_PATH);
        if(idPath.equals("")) {
            this.identityPath = Paths.get("drasylNodeIdentity.json");
        } else {
            this.identityPath = Paths.get(idPath);
        }
    }

    DrasylNodeConfig(Config config,
                            Path identityPath,
                            String userAgent) {
        this.config = config;
        this.identityPath = identityPath;
        this.userAgent = userAgent;
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    public Config getConfig() {
        return this.config;
    }

    public Path getIdentityPath() {
        return identityPath;
    }
}
