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
package org.drasyl.util.protocol;

import org.drasyl.util.UnsignedInteger;
import org.drasyl.util.UnsignedShort;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Utility class for NAT Port Mapping Protocol (NAT-PMP)-related stuff.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6886">RFC 6886</a>
 */
public final class NatPmpUtil {
    public static final int NAT_PMP_PORT = 5351;
    public static final int NAT_PMP_VERSION = 0;
    public static final int EXTERNAL_ADDRESS_REQUEST_OP = 0;
    public static final int EXTERNAL_ADDRESS_RESPONSE_OP = 128;
    public static final int MAPPING_UDP_REQUEST_OP = 1;
    public static final int MAPPING_UDP_RESPONSE_OP = 129;
    @SuppressWarnings("unused")
    public static final int MAPPING_TCP_REQUEST_OP = 2;
    @SuppressWarnings("unused")
    public static final int MAPPING_TCP_RESPONSE_OP = 130;
    public static final int RESERVED_LENGTH = 2;
    public static final int LIFETIME_LENGTH = 4;

    private NatPmpUtil() {
        // util class
    }

    /**
     * <pre>
     *    To determine the external address, the client behind the NAT sends
     *    the following UDP payload to port 5351 of the configured gateway
     *    address:
     *
     *     0                   1
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    | Vers = 0      | OP = 0        |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * </pre>
     */
    public static byte[] buildExternalAddressRequestMessage() {
        return new byte[]{
                NAT_PMP_VERSION,
                EXTERNAL_ADDRESS_REQUEST_OP
        };
    }

    /**
     * <pre>
     *    To create a mapping, the client sends a UDP packet to port 5351 of
     *    the gateway's internal IP address with the following format:
     *
     *     0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    | Vers = 0      | OP = x        | Reserved                      |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    | Internal Port                 | Suggested External Port       |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    | Requested Port Mapping Lifetime in Seconds                    |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     *    Opcodes supported:
     *    1 - Map UDP
     *    2 - Map TCP
     *
     *    The Reserved field MUST be set to zero on transmission and MUST be
     *    ignored on reception.
     *
     *    The Ports and Lifetime are transmitted in the traditional network
     *    byte order (i.e., most significant byte first).
     *
     *    The Internal Port is set to the local port on which the client is
     *    listening.
     * </pre>
     *
     * @throws IllegalArgumentException if {@code internalPort} or {@code externalPort} is not in
     *                                  range of [0, 2^16 - 1]
     */
    public static byte[] buildMappingRequestMessage(final int internalPort,
                                                    final int externalPort,
                                                    final Duration lifetime) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final DataOutputStream out = new DataOutputStream(byteArrayOutputStream)) {
            // version
            out.write(NAT_PMP_VERSION);

            // op code
            out.write(MAPPING_UDP_REQUEST_OP);

            // reserved
            out.write(new byte[RESERVED_LENGTH]);

            // internal port
            out.write(UnsignedShort.of(internalPort).toBytes());

            // external port
            out.write(UnsignedShort.of(externalPort).toBytes());

            // lifetime
            out.write(UnsignedInteger.of(lifetime.toSeconds()).toBytes());

            out.flush();

            return byteArrayOutputStream.toByteArray();
        }
        catch (final IOException e) {
            throw new IllegalStateException("Unable to build request mapping message: ", e);
        }
    }

    @SuppressWarnings("java:S1142")
    public static Message readMessage(final InputStream inputStream) throws IOException {
        try (final DataInputStream in = new DataInputStream(inputStream)) {
            // version
            final int version = in.readByte() & 0xFF;
            if (version != NAT_PMP_VERSION) {
                return null;
            }

            // op code
            final int op = in.readByte() & 0xFF;

            // result code
            final short number = (short) in.readUnsignedShort();
            final ResultCode resultCode = ResultCode.from(number);
            if (resultCode == null) {
                throw new IOException("Unknown result code: " + number);
            }

            switch (op) {
                case EXTERNAL_ADDRESS_RESPONSE_OP:
                    return readExternalAddressMessage(resultCode, in);

                case MAPPING_UDP_RESPONSE_OP:
                    return readMappingResponseMessage(resultCode, in);

                default:
                    return null;
            }
        }
    }

    /**
     * <pre>
     *    A compatible NAT gateway MUST generate a response with the following
     *    format:
     *
     *     0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    | Vers = 0      | OP = 128 + 0  | Result Code (net byte order)  |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    | Seconds Since Start of Epoch (in network byte order)          |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    | External IPv4 Address (a.b.c.d)                               |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     *    This response indicates that the NAT gateway implements this version
     *    of the protocol, and returns the external IPv4 address of the NAT
     *    gateway.  If the result code is non-zero, the value of the External
     *    IPv4 Address field is undefined (MUST be set to zero on transmission,
     *    and MUST be ignored on reception).
     *
     *    The NAT gateway MUST fill in the Seconds Since Start of Epoch field
     *    with the time elapsed since its port mapping table was initialized on
     *    startup, or reset for any other reason (see Section 3.6, "Seconds
     *    Since Start of Epoch").
     *
     *    Upon receiving a response packet, the client MUST check the source IP
     *    address, and silently discard the packet if the address is not the
     *    address of the gateway to which the request was sent.
     * </pre>
     */
    @SuppressWarnings("unused")
    private static ExternalAddressResponseMessage readExternalAddressMessage(final ResultCode resultCode,
                                                                             final DataInputStream in) throws IOException {
        final int secondsSinceStartOfEpoch = in.readInt();
        final InetAddress externalAddress = InetAddress.getByAddress(new byte[]{
                in.readByte(),
                in.readByte(),
                in.readByte(),
                in.readByte()
        });
        return new ExternalAddressResponseMessage(resultCode, secondsSinceStartOfEpoch, externalAddress);
    }

    /**
     * <pre>
     *    The NAT gateway responds with the following packet format:
     *
     *     0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    | Vers = 0      | OP = 128 + x  | Result Code                   |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    | Seconds Since Start of Epoch                                  |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    | Internal Port                 | Mapped External Port          |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    | Port Mapping Lifetime in Seconds                              |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     *    The epoch time, ports, and lifetime are transmitted in the
     *    traditional network byte order (i.e., most significant byte first).
     *
     *    The 'x' in the OP field MUST match what the client requested.  Some
     *    NAT gateways are incapable of creating a UDP port mapping without
     *    also creating a corresponding TCP port mapping, and vice versa, and
     *    these gateways MUST NOT implement NAT Port Mapping Protocol until
     *    this deficiency is fixed.  A NAT gateway that implements this
     *    protocol MUST be able to create TCP-only and UDP-only port mappings.
     *    If a NAT gateway silently creates a pair of mappings for a client
     *    that only requested one mapping, then it may expose that client to
     *    receiving inbound UDP packets or inbound TCP connection requests that
     *    it did not ask for and does not want.
     * </pre>
     */
    @SuppressWarnings("unused")
    private static MappingUdpResponseMessage readMappingResponseMessage(final ResultCode resultCode,
                                                                        final DataInputStream in) throws IOException {
        final int secondsSinceStartOfEpoch = in.readInt();
        final int internalPort = in.readUnsignedShort();
        final int externalPort = in.readUnsignedShort();
        final byte[] unsignedLifetime = new byte[LIFETIME_LENGTH];
        if (in.read(unsignedLifetime) != LIFETIME_LENGTH) {
            throw new IOException("Message is incomplete.");
        }
        final long lifetime = UnsignedInteger.of(unsignedLifetime).getValue();
        return new MappingUdpResponseMessage(resultCode, secondsSinceStartOfEpoch, internalPort, externalPort, lifetime);
    }

    public interface Message {
    }

    abstract static class AbstractMessage implements Message {
        private final ResultCode resultCode;

        protected AbstractMessage(final ResultCode resultCode) {
            this.resultCode = requireNonNull(resultCode);
        }

        public ResultCode getResultCode() {
            return resultCode;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final AbstractMessage that = (AbstractMessage) o;
            return resultCode == that.resultCode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(resultCode);
        }
    }

    @SuppressWarnings({ "unused", "java:S2972" })
    public static class MappingUdpResponseMessage extends AbstractMessage {
        private final int secondsSinceStartOfEpoch;
        private final int internalPort;
        private final int externalPort;
        private final long lifetime;

        public MappingUdpResponseMessage(final ResultCode resultCode,
                                         final int secondsSinceStartOfEpoch,
                                         final int internalPort,
                                         final int externalPort,
                                         final long lifetime) {
            super(resultCode);
            this.secondsSinceStartOfEpoch = secondsSinceStartOfEpoch;
            this.internalPort = internalPort;
            this.externalPort = externalPort;
            this.lifetime = lifetime;
        }

        public int getSecondsSinceStartOfEpoch() {
            return secondsSinceStartOfEpoch;
        }

        public int getInternalPort() {
            return internalPort;
        }

        public int getExternalPort() {
            return externalPort;
        }

        public long getLifetime() {
            return lifetime;
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
            final MappingUdpResponseMessage that = (MappingUdpResponseMessage) o;
            return secondsSinceStartOfEpoch == that.secondsSinceStartOfEpoch && internalPort == that.internalPort && externalPort == that.externalPort && lifetime == that.lifetime;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), secondsSinceStartOfEpoch, internalPort, externalPort, lifetime);
        }
    }

    @SuppressWarnings({ "unused", "java:S2972" })
    public static class ExternalAddressResponseMessage extends AbstractMessage {
        private final int secondsSinceStartOfEpoch;
        private final InetAddress externalAddress;

        public ExternalAddressResponseMessage(final ResultCode resultCode,
                                              final int secondsSinceStartOfEpoch,
                                              final InetAddress externalAddress) {
            super(resultCode);
            this.secondsSinceStartOfEpoch = secondsSinceStartOfEpoch;
            this.externalAddress = requireNonNull(externalAddress);
        }

        public int getSecondsSinceStartOfEpoch() {
            return secondsSinceStartOfEpoch;
        }

        public InetAddress getExternalAddress() {
            return externalAddress;
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
            final ExternalAddressResponseMessage that = (ExternalAddressResponseMessage) o;
            return secondsSinceStartOfEpoch == that.secondsSinceStartOfEpoch && Objects.equals(externalAddress, that.externalAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), secondsSinceStartOfEpoch, externalAddress);
        }
    }

    public enum ResultCode {
        SUCCESS((short) 0),
        UNSUPPORTED_VERSION((short) 1),
        NOT_AUTHORIZED((short) 2),
        NETWORK_FAILURE((short) 3),
        OUT_OF_RESOURCES((short) 4),
        UNSUPPORTED_OPCODE((short) 5);
        private static final Map<Short, ResultCode> codes = new HashMap<>();

        static {
            for (final ResultCode code : values()) {
                codes.put(code.getNumber(), code);
            }
        }

        private final short number;

        ResultCode(final short number) {
            this.number = number;
        }

        public short getNumber() {
            return number;
        }

        public static ResultCode from(final short number) {
            return codes.get(number);
        }
    }
}
