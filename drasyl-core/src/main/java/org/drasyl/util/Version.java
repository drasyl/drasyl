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
package org.drasyl.util;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import static java.util.Objects.requireNonNull;

@SuppressWarnings("java:S1700")
public final class Version {
    private static final String PROPERTY_VERSION = ".version";
    private final String artifactId;
    private final String version;

    private Version(final String artifactId, final String version) {
        this.artifactId = requireNonNull(artifactId);
        this.version = requireNonNull(version);
    }

    public String artifactId() {
        return artifactId;
    }

    public String version() {
        return version;
    }

    @Override
    public String toString() {
        return artifactId + ' ' + version;
    }

    /**
     * Retrieves version information of all drasyl artifacts.
     *
     * @return A {@link Map} whose keys are Maven artifact IDs and whose values are {@link Version}s
     */
    @SuppressWarnings({ "java:S135", "java:S1141", "java:S1166", "java:S2221" })
    public static Map<String, Version> identify() {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        // collect properties
        Properties properties = new Properties();
        try {
            Enumeration<URL> resources = classLoader.getResources("META-INF/org.drasyl.versions.properties");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                InputStream in = url.openStream();
                try {
                    properties.load(in);
                }
                finally {
                    try {
                        in.close();
                    }
                    catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
        catch (Exception e) {
            // ignore
        }

        // collect artifact ids
        Set<String> artifactIds = new HashSet<>();
        for (String key : (Set<String>) (Set) properties.keySet()) {
            final int index = key.indexOf('.');
            if (index <= 0) {
                continue;
            }

            String artifactId = key.substring(0, index);

            // all properties present?
            if (!properties.containsKey(artifactId + PROPERTY_VERSION)) {
                continue;
            }

            artifactIds.add(artifactId);
        }

        // collect information
        Map<String, Version> versions = new TreeMap<>();
        for (String artifactId : artifactIds) {
            versions.put(
                    artifactId,
                    new Version(artifactId, properties.getProperty(artifactId + PROPERTY_VERSION))
            );
        }

        return versions;
    }

    @SuppressWarnings("java:S106")
    public static void main(String[] args) {
        for (Version version : identify().values()) {
            System.out.println(version);
        }
    }
}
