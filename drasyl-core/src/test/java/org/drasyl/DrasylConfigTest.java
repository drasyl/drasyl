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
package org.drasyl;

import com.google.common.collect.ImmutableSet;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.plugin.DrasylPlugin;
import org.drasyl.serialization.Serializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import test.util.IdentityTestUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.drasyl.DrasylConfig.getEndpoint;
import static org.drasyl.DrasylConfig.getEndpointSet;
import static org.drasyl.DrasylConfig.getInetAddress;
import static org.drasyl.DrasylConfig.getInetSocketAddress;
import static org.drasyl.DrasylConfig.getKeyAgreementPublicKey;
import static org.drasyl.DrasylConfig.getKeyAgreementSecretKey;
import static org.drasyl.DrasylConfig.getPath;
import static org.drasyl.DrasylConfig.getPlugins;
import static org.drasyl.DrasylConfig.getIdentitySecretKey;
import static org.drasyl.DrasylConfig.getIdentityPublicKey;
import static org.drasyl.DrasylConfig.getSerializationBindings;
import static org.drasyl.DrasylConfig.getSerializationSerializers;
import static org.drasyl.DrasylConfig.getShort;
import static org.drasyl.DrasylConfig.getStaticRoutes;
import static org.drasyl.DrasylConfig.getURI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class DrasylConfigTest {
    @Nested
    class Of {
        @Test
        void shouldReturnConfig() {
            assertNotNull(DrasylConfig.of());
        }

        @Test
        void shouldReadNonNullIdentityProofOfWorkFromConfig() {
            final Config config = ConfigFactory.parseString("drasyl.identity.proof-of-work = 1337").withFallback(ConfigFactory.load());

            assertEquals(ProofOfWork.of(1337), DrasylConfig.of(config).getIdentityProofOfWork());
        }

        @Test
        void shouldReadNonNullIdentityPublicKeyFromConfig() {
            final Config config = ConfigFactory.parseString("drasyl.identity.public-key = " + IdentityTestUtil.ID_1.getIdentityPublicKey()).withFallback(ConfigFactory.load());

            assertEquals(IdentityTestUtil.ID_1.getIdentityPublicKey(), DrasylConfig.of(config).getIdentityPublicKey());
        }

        @Test
        void shouldReadNonNullIdentitySecretKeyFromConfig() {
            final Config config = ConfigFactory.parseString("drasyl.identity.secret-key = " + IdentityTestUtil.ID_1.getIdentitySecretKey().toUnmaskedString()).withFallback(ConfigFactory.load());

            assertEquals(IdentityTestUtil.ID_1.getIdentitySecretKey(), DrasylConfig.of(config).getIdentitySecretKey());
        }

        @Test
        void shouldReadNonNullKeyAgreementPublicKeyFromConfig() {
            final Config config = ConfigFactory.parseString("drasyl.identity.key-agreement.public-key = " + IdentityTestUtil.ID_1.getKeyAgreementPublicKey()).withFallback(ConfigFactory.load());

            assertEquals(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), DrasylConfig.of(config).getKeyAgreementPublicKey());
        }

        @Test
        void shouldReadNonNullKeyAgreementSecretKeyFromConfig() {
            final Config config = ConfigFactory.parseString("drasyl.identity.key-agreement.secret-key = " + IdentityTestUtil.ID_1.getKeyAgreementSecretKey().toUnmaskedString()).withFallback(ConfigFactory.load());

            assertEquals(IdentityTestUtil.ID_1.getKeyAgreementSecretKey(), DrasylConfig.of(config).getKeyAgreementSecretKey());
        }

        @Test
        void shouldReadNonNullRemoteLocalHostDiscoveryPathFromConfig() {
            final Config config = ConfigFactory.parseString("drasyl.remote.local-host-discovery.path = /foo/bar").withFallback(ConfigFactory.load());

            assertEquals(Paths.get("/foo/bar"), DrasylConfig.of(config).getRemoteLocalHostDiscoveryPath());
        }
    }

    @Nested
    class GetUri {
        @Test
        void shouldReturnUriAtPath() {
            final Config config = ConfigFactory.parseString("foo.bar = \"http://localhost.de\"");

            assertEquals(URI.create("http://localhost.de"), getURI(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = \"hallo world\"");

            assertThrows(DrasylConfigException.class, () -> getURI(config, "foo.bar"));
        }
    }

    @Nested
    class GetPublicKey {
        @Test
        void shouldThrowExceptionForInvalidValueIdentityKey() {
            final Config config = ConfigFactory.parseString("foo.bar = bla");

            assertThrows(DrasylConfigException.class, () -> getIdentityPublicKey(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForInvalidValueKeyAgreementKey() {
            final Config config = ConfigFactory.parseString("foo.bar = bla");

            assertThrows(DrasylConfigException.class, () -> getKeyAgreementPublicKey(config, "foo.bar"));
        }
    }

    @Nested
    class GetPrivateKey {
        @Test
        void shouldThrowExceptionForInvalidValueIdentityKey() {
            final Config config = ConfigFactory.parseString("foo.bar = bla");

            assertThrows(DrasylConfigException.class, () -> getIdentitySecretKey(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForInvalidValueKeyAgreementKey() {
            final Config config = ConfigFactory.parseString("foo.bar = bla");

            assertThrows(DrasylConfigException.class, () -> getKeyAgreementSecretKey(config, "foo.bar"));
        }
    }

    @Nested
    class GetPath {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("");

            assertThrows(DrasylConfigException.class, () -> getPath(config, "foo.bar"));
        }
    }

    @Nested
    class GetShort {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = 123456789");

            assertThrows(DrasylConfigException.class, () -> getShort(config, "foo.bar"));
        }
    }

    @Nested
    class GetEndpointList {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = [\"http://foo.bar\"]");

            assertThrows(DrasylConfigException.class, () -> getEndpointSet(config, "foo.bar"));
        }
    }

    @Nested
    class GetEndpoint {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = \"http://foo.bar\"");

            assertThrows(DrasylConfigException.class, () -> getEndpoint(config, "foo.bar"));
        }
    }

    @Nested
    class GetInetAddress {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = baz");

            assertThrows(DrasylConfigException.class, () -> getInetAddress(config, "foo.bar"));
        }
    }

    @Nested
    class GetInetSocketAddress {
        @Test
        void shouldParseIPv4Address() {
            final Config config = ConfigFactory.parseString("foo.bar = \"203.0.113.149:22527\"");

            assertEquals(InetSocketAddress.createUnresolved("203.0.113.149", 22527), getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldParseIPv6Address() {
            final Config config = ConfigFactory.parseString("foo.bar = \"[2001:db8::1]:8080\"");

            assertEquals(InetSocketAddress.createUnresolved("[2001:db8::1]", 8080), getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldParseHostname() {
            final Config config = ConfigFactory.parseString("foo.bar = \"production.env.drasyl.org:1234\"");

            assertEquals(InetSocketAddress.createUnresolved("production.env.drasyl.org", 1234), getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionAddressWithoutHostname() {
            final Config config = ConfigFactory.parseString("foo.bar = \"1234\"");

            assertThrows(DrasylConfigException.class, () -> getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionAddressWithoutPort() {
            final Config config = ConfigFactory.parseString("foo.bar = \"production.env.drasyl.org\"");

            assertThrows(DrasylConfigException.class, () -> getInetSocketAddress(config, "foo.bar"));
        }
    }

    @Nested
    class GetPlugins {
        @Test
        void shouldThrowExceptionForNonExistingClasses() {
            final Config config = ConfigFactory.parseString("foo.bar { \"non.existing.class\" { enabled = true } }");

            assertThrows(DrasylConfigException.class, () -> getPlugins(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForClassWithMissingMethod() {
            final Config config = ConfigFactory.parseString("foo.bar { \"" + MyPluginWithMissingMethod.class.getName() + "\" { enabled = true } }");

            assertThrows(DrasylConfigException.class, () -> getPlugins(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForClassWithInvocationTargetException() {
            final Config config = ConfigFactory.parseString("foo.bar { \"" + MyPluginWithInvocationTargetException.class.getName() + "\" { enabled = true } }");

            assertThrows(DrasylConfigException.class, () -> getPlugins(config, "foo.bar"));
        }
    }

    static class MyPlugin implements DrasylPlugin {
        @SuppressWarnings("unused")
        public MyPlugin(final Config config) {
        }
    }

    static class MyPluginWithMissingMethod implements DrasylPlugin {
    }

    static class MyPluginWithInvocationTargetException implements DrasylPlugin {
        @SuppressWarnings("unused")
        public MyPluginWithInvocationTargetException(final Config config) throws IllegalAccessException {
            throw new IllegalAccessException("boom");
        }
    }

    @Nested
    class GetSerializationSerializers {
        @Test
        void shouldThrowExceptionForNonExistingClasses() {
            final Config config = ConfigFactory.parseString("foo.bar { string = \"org.drasyl.serialization.NotExistingSerializer\" }");

            assertThrows(DrasylConfigException.class, () -> getSerializationSerializers(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForClassWithMissingMethod() {
            final Config config = ConfigFactory.parseString("foo.bar { string = \"" + MySerializerWithMissingMethod.class.getName() + "\" }");

            assertThrows(DrasylConfigException.class, () -> getSerializationSerializers(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForClassWithInvocationTargetException() {
            final Config config = ConfigFactory.parseString("foo.bar { string = \"" + MySerializerWithInvocationTargetException.class.getName() + "\" }");

            assertThrows(DrasylConfigException.class, () -> getSerializationSerializers(config, "foo.bar"));
        }
    }

    static class MySerializer implements Serializer {
        public MySerializer() {
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            return o != null && getClass() == o.getClass();
        }

        @Override
        public byte[] toByteArray(final Object o) {
            return new byte[0];
        }

        @Override
        public <T> T fromByteArray(final byte[] bytes, final Class<T> type) {
            return null;
        }
    }

    static class MySerializerWithMissingMethod implements Serializer {
        @SuppressWarnings("unused")
        public MySerializerWithMissingMethod(final String foo) {
        }

        @Override
        public byte[] toByteArray(final Object o) {
            return new byte[0];
        }

        @Override
        public <T> T fromByteArray(final byte[] bytes, final Class<T> type) {
            return null;
        }
    }

    static class MySerializerWithInvocationTargetException implements Serializer {
        public MySerializerWithInvocationTargetException() throws IllegalAccessException {
            throw new IllegalAccessException("boom");
        }

        @Override
        public byte[] toByteArray(final Object o) {
            return new byte[0];
        }

        @Override
        public <T> T fromByteArray(final byte[] bytes, final Class<T> type) {
            return null;
        }
    }

    @Nested
    class GetSerializationBindings {
        @Test
        void shouldThrowExceptionForNonExistingClasses() {
            final Config config = ConfigFactory.parseString("foo.bar { \"testing.NotExisting\" = string }");

            final Set<String> serializers = Set.of("string");
            assertThrows(DrasylConfigException.class, () -> getSerializationBindings(config, "foo.bar", serializers));
        }

        @Test
        void shouldThrowExceptionForNonExistingSerializer() {
            final Config config = ConfigFactory.parseString("foo.bar { \"" + String.class.getName() + "\" = string }");

            final Set<String> serializers = Set.of();
            assertThrows(DrasylConfigException.class, () -> getSerializationBindings(config, "foo.bar", serializers));
        }
    }

    @Nested
    class GetStaticRoutes {
        @Test
        void shouldReturnCorrectRoutes() {
            final Config config = ConfigFactory.parseString("foo.bar {" + IdentityTestUtil.ID_1.getIdentityPublicKey() + " = \"140.211.24.157:22527\" }");

            assertEquals(
                    Map.of(IdentityTestUtil.ID_1.getIdentityPublicKey(), new InetSocketAddress("140.211.24.157", 22527)),
                    getStaticRoutes(config, "foo.bar")
            );
        }

        @Test
        void shouldThrowExceptionForInvalidPublicKey() {
            final Config config = ConfigFactory.parseString("foo.bar { 033e8af97c541aee33ebe0b55aa068a936a4b = \"140.211.24.157:22527\" }");

            assertThrows(DrasylConfigException.class, () -> getStaticRoutes(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForInvalidAddress() {
            final Config config = ConfigFactory.parseString("foo.bar { " + IdentityTestUtil.ID_1.getIdentityPublicKey() + " = \"140.211.24.157\" }");

            assertThrows(DrasylConfigException.class, () -> getStaticRoutes(config, "foo.bar"));
        }
    }

    @Nested
    class ParseFile {
        @Test
        void shouldReadConfigFromFile(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "drasyl.conf");
            Files.writeString(path, "drasyl.network.id = 1337\ndrasyl.remote.super-peer.endpoints = [\"udp://example.org:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey() + "&networkId=1337\"]", StandardOpenOption.CREATE);

            assertEquals(1337, DrasylConfig.parseFile(path.toFile()).getNetworkId());
        }
    }

    @Nested
    class ParseString {
        @Test
        void shouldReadConfigFromString() {
            assertEquals(1337, DrasylConfig.parseString("drasyl.network.id = 1337\ndrasyl.remote.super-peer.endpoints = [\"udp://example.org:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey() + "&networkId=1337\"]").getNetworkId());
        }
    }

    @Nested
    class TestDrasylConfigBuilder {
        @Test
        void shouldRejectInvalidConfigs() {
            assertThrows(DrasylConfigException.class, DrasylConfig.newBuilder().messageBufferSize(-1)::build);
            assertThrows(DrasylConfigException.class, DrasylConfig.newBuilder().remotePingInterval(Duration.ZERO)::build);
            assertThrows(DrasylConfigException.class, DrasylConfig.newBuilder().remotePingTimeout(Duration.ZERO)::build);
            assertThrows(DrasylConfigException.class, DrasylConfig.newBuilder().remotePingCommunicationTimeout(Duration.ZERO)::build);
            assertThrows(DrasylConfigException.class, DrasylConfig.newBuilder().remotePingMaxPeers(-1)::build);
            assertThrows(DrasylConfigException.class, DrasylConfig.newBuilder().remoteUniteMinInterval(Duration.ofSeconds(-100))::build);
            assertThrows(DrasylConfigException.class, DrasylConfig.newBuilder().networkId(1).remoteSuperPeerEnabled(true).remoteSuperPeerEndpoints(ImmutableSet.of(Endpoint.of("udp://example.org:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey() + "&networkId=1337")))::build);
            assertThrows(DrasylConfigException.class, DrasylConfig.newBuilder().remoteLocalHostDiscoveryLeaseTime(Duration.ZERO)::build);
            assertThrows(DrasylConfigException.class, DrasylConfig.newBuilder().remoteMessageMtu(-1)::build);
            assertThrows(DrasylConfigException.class, DrasylConfig.newBuilder().remoteMessageMaxContentLength(-1)::build);
            assertThrows(DrasylConfigException.class, DrasylConfig.newBuilder().remoteMessageComposedMessageTransferTimeout(Duration.ZERO)::build);
        }
    }
}
