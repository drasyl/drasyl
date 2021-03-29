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

import org.drasyl.util.NetworkUtil;
import org.drasyl.util.UnsignedInteger;
import org.drasyl.util.UnsignedShort;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Utility class for Port Control Protocol (PCP)-related stuff.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6887">RFC 6887</a>
 */
public final class PcpPortUtil {
    public static final int PCP_PORT = 5351;
    public static final int PCP_VERSION = 2;
    public static final int MAP_OPCODE = 1;
    @SuppressWarnings("unused")
    public static final int PROTO_TCP = 6;
    public static final int PROTO_UDP = 17;
    public static final InetAddress ZERO_IPV6;
    public static final InetAddress ZERO_IPV4;
    public static final int LIFETIME_LENGTH = 4;
    public static final int EPOCH_TIME_LENGTH = 4;
    public static final int MAPPING_NONCE_LENGTH = 12;
    public static final int EXTERNAL_SUGGESTED_ADDRESS_LENGTH = 16;
    public static final int REQUEST_RESERVED1_LENGTH = 2;
    public static final int REQUEST_RESERVED2_LENGTH = 3;
    public static final int RESPONSE_RESERVED1_LENGTH = 1;
    public static final int RESPONSE_RESERVED2_LENGTH = 12;
    public static final int RESPONSE_RESERVED3_LENGTH = 3;

    static {
        try {
            ZERO_IPV6 = InetAddress.getByName("::");
            ZERO_IPV4 = InetAddress.getByName("0.0.0.0");
        }
        catch (final UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private PcpPortUtil() {
        // util class
    }

    /**
     * <pre>
     *      All MAP opcode requests have the following format:
     *
     *       0                   1                   2                   3
     *       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |  Version = 2  |R|   Opcode    |         Reserved              |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                 Requested Lifetime (32 bits)                  |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                                                               |
     *      |            PCP Client's IP Address (128 bits)                 |
     *      |                                                               |
     *      |                                                               |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                                                               |
     *      |                 Mapping Nonce (96 bits)                       |
     *      |                                                               |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |   Protocol    |          Reserved (24 bits)                   |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |        Internal Port          |    Suggested External Port    |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                                                               |
     *      |           Suggested External IP Address (128 bits)            |
     *      |                                                               |
     *      |                                                               |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     *    These fields are described below:
     *
     *    Version:  This document specifies protocol version 2.  PCP clients
     *       and servers compliant with this document use the value 2.  This
     *       field is used for version negotiation as described in Section 9.
     *
     *    R: Indicates Request (0) or Response (1).
     *
     *    Opcode:  A 7-bit value specifying the operation to be performed.  MAP
     *       and PEER Opcodes are defined in Sections 11 and 12.
     *
     *    Reserved:  16 reserved bits.  MUST be zero on transmission and MUST
     *       be ignored on reception.
     *
     *    Requested Lifetime:  An unsigned 32-bit integer, in seconds, ranging
     *       from 0 to 2^32-1 seconds.  Requested lifetime of this
     *       mapping, in seconds.  The value 0 indicates "delete".
     *
     *    PCP Client's IP Address:  The source IPv4 or IPv6 address in the IP
     *       header used by the PCP client when sending this PCP request.  An
     *       IPv4 address is represented using an IPv4-mapped IPv6 address.
     *       The PCP Client IP Address in the PCP message header is used to
     *       detect an unexpected NAT on the path between the PCP client and
     *       the PCP-controlled NAT or firewall device.  See Section 8.1.
     *
     *    Mapping Nonce:  Random value chosen by the PCP client.  See
     *       Section 11.2, "Generating a MAP Request".  Zero is a legal value
     *       (but unlikely, occurring in roughly one in 2^96 requests).
     *
     *    Protocol:  Upper-layer protocol associated with this Opcode.  Values
     *       are taken from the IANA protocol registry [proto_numbers].  For
     *       example, this field contains 6 (TCP) if the Opcode is intended to
     *       create a TCP mapping.  This field contains 17 (UDP) if the Opcode
     *       is intended to create a UDP mapping.  The value 0 has a special
     *       meaning for 'all protocols'.
     *
     *    Reserved:  24 reserved bits, MUST be sent as 0 and MUST be ignored
     *       when received.
     *
     *    Internal Port:  Internal port for the mapping.  The value 0 indicates
     *       'all ports', and is legal when the lifetime is zero (a delete
     *       request), if the protocol does not use 16-bit port numbers, or the
     *       client is requesting 'all ports'.  If the protocol is zero
     *       (meaning 'all protocols'), then internal port MUST be zero on
     *       transmission and MUST be ignored on reception.
     *
     *    Suggested External Port:  Suggested external port for the mapping.
     *       This is useful for refreshing a mapping, especially after the PCP
     *       server loses state.  If the PCP client does not know the external
     *       port, or does not have a preference, it MUST use 0.
     *
     *    Suggested External IP Address:  Suggested external IPv4 or IPv6
     *       address.  This is useful for refreshing a mapping, especially
     *       after the PCP server loses state.  If the PCP client does not know
     *       the external address, or does not have a preference, it MUST use
     *       the address-family-specific all-zeros address (see Section 5).
     * </pre>
     *
     * @throws IllegalArgumentException if {@code port} is not in range of [0, 2^16 - 1]
     */
    public static byte[] buildMappingRequestMessage(final Duration lifetime,
                                                    final InetAddress clientAddress,
                                                    final byte[] nonce,
                                                    final int protocol,
                                                    final int port,
                                                    final InetAddress externalAddress) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final DataOutputStream out = new DataOutputStream(byteArrayOutputStream)) {
            final byte[] unsignedPort = UnsignedShort.of(port).toBytes();

            // version
            out.write(PCP_VERSION);

            // op code
            out.write(MAP_OPCODE);

            // reserved
            out.write(new byte[REQUEST_RESERVED1_LENGTH]);

            // lifetime
            out.write(UnsignedInteger.of(lifetime.toSeconds()).toBytes());

            // client address
            out.write(NetworkUtil.getIpv4MappedIPv6AddressBytes(clientAddress));

            // mapping nonce
            out.write(nonce);

            // protocol
            out.write(protocol);

            // reserved
            out.write(new byte[REQUEST_RESERVED2_LENGTH]);

            // internal port
            out.write(unsignedPort);

            // suggested external port
            out.write(unsignedPort);

            // suggested external address
            out.write(NetworkUtil.getIpv4MappedIPv6AddressBytes(externalAddress));

            out.flush();

            return byteArrayOutputStream.toByteArray();
        }
        catch (final IOException e) {
            throw new IllegalStateException("Unable to build request mapping message: ", e);
        }
    }

    @SuppressWarnings({ "SwitchStatementWithTooFewBranches", "java:S1142", "java:S1301" })
    public static Message readMessage(final InputStream inputStream) throws IOException {
        try (final DataInputStream in = new DataInputStream(inputStream)) {
            // version
            final int version = in.readByte() & 0xFF;
            if (version != PCP_VERSION) {
                return null;
            }

            // opcode
            final int temp = in.readByte() & 0xFF;
            if ((temp & 128) != 128) { // response?
                return null;
            }
            final int opcode = temp & 0x7F;

            switch (opcode) {
                case MAP_OPCODE:
                    return readMappingMessage(in);

                default:
                    return null;
            }
        }
    }

    /**
     * <pre>
     *      All MAP opcode responses have the following format:
     *
     *       0                   1                   2                   3
     *       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |  Version = 2  |R|   Opcode    |   Reserved    |  Result Code  |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                      Lifetime (32 bits)                       |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                     Epoch Time (32 bits)                      |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                                                               |
     *      |                      Reserved (96 bits)                       |
     *      |                                                               |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                                                               |
     *      |                 Mapping Nonce (96 bits)                       |
     *      |                                                               |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |   Protocol    |          Reserved (24 bits)                   |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |        Internal Port          |    Suggested External Port    |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *      |                                                               |
     *      |           Suggested External IP Address (128 bits)            |
     *      |                                                               |
     *      |                                                               |
     *      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     *                   Figure 3: Common Response Packet Format
     *
     *    These fields are described below:
     *
     *    Version:  Responses from servers compliant with this specification
     *       MUST use version 2.  This is set by the server.
     *
     *    R: Indicates Request (0) or Response (1).  All Responses MUST use 1.
     *       This is set by the server.
     *
     *    Opcode:  The 7-bit Opcode value.  The server copies this value from
     *       the request.
     *
     *    Reserved:  8 reserved bits, MUST be sent as 0, MUST be ignored when
     *       received.  This is set by the server.
     *
     *    Result Code:  The result code for this response.  See Section 7.4 for
     *       values.  This is set by the server.
     *
     *    Lifetime:  An unsigned 32-bit integer, in seconds, ranging from 0 to
     *       2^32-1 seconds.  On an error response, this indicates how long
     *       clients should assume they'll get the same error response from
     *       that PCP server if they repeat the same request.  On a success
     *       response for the PCP Opcodes that create a mapping (MAP and PEER),
     *       the Lifetime field indicates the lifetime for this mapping.  This
     *       is set by the server.
     *
     *    Epoch Time:  The server's Epoch Time value.  See Section 8.5 for
     *       discussion.  This value is set by the server, in both success and
     *       error responses.
     *
     *    Reserved:  96 reserved bits.  For requests that were successfully
     *       parsed, this MUST be sent as 0, MUST be ignored when received.
     *       This is set by the server.  For requests that were not
     *       successfully parsed, the server copies the last 96 bits of the PCP
     *       Client's IP Address field from the request message into this
     *       corresponding 96-bit field of the response.
     *
     *    Mapping Nonce:  Random value chosen by the PCP client.  See
     *       Section 11.2, "Generating a MAP Request".  Zero is a legal value
     *       (but unlikely, occurring in roughly one in 2^96 requests).
     *
     *    Protocol:  Upper-layer protocol associated with this Opcode.  Values
     *       are taken from the IANA protocol registry [proto_numbers].  For
     *       example, this field contains 6 (TCP) if the Opcode is intended to
     *       create a TCP mapping.  This field contains 17 (UDP) if the Opcode
     *       is intended to create a UDP mapping.  The value 0 has a special
     *       meaning for 'all protocols'.
     *
     *    Reserved:  24 reserved bits, MUST be sent as 0 and MUST be ignored
     *       when received.
     *
     *    Internal Port:  Internal port for the mapping.  The value 0 indicates
     *       'all ports', and is legal when the lifetime is zero (a delete
     *       request), if the protocol does not use 16-bit port numbers, or the
     *       client is requesting 'all ports'.  If the protocol is zero
     *       (meaning 'all protocols'), then internal port MUST be zero on
     *       transmission and MUST be ignored on reception.
     *
     *    Suggested External Port:  Suggested external port for the mapping.
     *       This is useful for refreshing a mapping, especially after the PCP
     *       server loses state.  If the PCP client does not know the external
     *       port, or does not have a preference, it MUST use 0.
     *
     *    Suggested External IP Address:  Suggested external IPv4 or IPv6
     *       address.  This is useful for refreshing a mapping, especially
     *       after the PCP server loses state.  If the PCP client does not know
     *       the external address, or does not have a preference, it MUST use
     *       the address-family-specific all-zeros address (see Section 5).
     * </pre>
     */
    @SuppressWarnings({ "java:S1192" })
    private static Message readMappingMessage(final DataInputStream in) throws IOException {
        // reserved
        if (in.read(new byte[RESPONSE_RESERVED1_LENGTH]) != RESPONSE_RESERVED1_LENGTH) {
            throw new IOException("Message is incomplete.");
        }

        // result code
        final short number = (short) (in.readByte() & 0xFF);
        final ResultCode resultCode = ResultCode.from(number);
        if (resultCode == null) {
            throw new IOException("Unknown result code: " + number);
        }

        // lifetime
        final byte[] unsignedLifetime = new byte[LIFETIME_LENGTH];
        if (in.read(unsignedLifetime) != LIFETIME_LENGTH) {
            throw new IOException("Message is incomplete.");
        }
        final long lifetime = UnsignedInteger.of(unsignedLifetime).getValue();

        // epoch time
        final byte[] unsignedEpochTime = new byte[EPOCH_TIME_LENGTH];
        if (in.read(unsignedEpochTime) != EPOCH_TIME_LENGTH) {
            throw new IOException("Message is incomplete.");
        }
        final long epochTime = UnsignedInteger.of(unsignedEpochTime).getValue();

        // reserved
        if (in.read(new byte[RESPONSE_RESERVED2_LENGTH]) != RESPONSE_RESERVED2_LENGTH) {
            throw new IOException("Message is incomplete.");
        }

        // mapping nonce
        final byte[] mappingNonce = new byte[MAPPING_NONCE_LENGTH];
        if (in.read(mappingNonce) != MAPPING_NONCE_LENGTH) {
            throw new IOException("Message is incomplete.");
        }

        // protocol
        final int protocol = in.readByte() & 0xFF;

        // reserved
        if (in.read(new byte[RESPONSE_RESERVED3_LENGTH]) != RESPONSE_RESERVED3_LENGTH) {
            throw new IOException("Message is incomplete.");
        }

        // internal port
        final int internalPort = in.readUnsignedShort();

        // external suggested port
        final int externalSuggestedPort = in.readUnsignedShort();

        // external suggested address
        final byte[] externalAddressBytes = new byte[EXTERNAL_SUGGESTED_ADDRESS_LENGTH];
        if (in.read(externalAddressBytes) != EXTERNAL_SUGGESTED_ADDRESS_LENGTH) {
            throw new IOException("Message is incomplete.");
        }
        final InetAddress externalSuggestedAddress = InetAddress.getByAddress(externalAddressBytes);

        return new MappingResponseMessage(resultCode, lifetime, epochTime, mappingNonce, protocol, internalPort, externalSuggestedPort, externalSuggestedAddress);
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
    }

    @SuppressWarnings({ "unused", "java:S2972" })
    public static class MappingResponseMessage extends AbstractMessage {
        private final long lifetime;
        private final long epochTime;
        private final byte[] mappingNonce;
        private final int protocol;
        private final int internalPort;
        private final int externalSuggestedPort;
        private final InetAddress externalSuggestedAddress;

        @SuppressWarnings({ "java:S107", "java:S2384" })
        public MappingResponseMessage(final ResultCode resultCode,
                                      final long lifetime,
                                      final long epochTime,
                                      final byte[] mappingNonce,
                                      final int protocol,
                                      final int internalPort,
                                      final int externalSuggestedPort,
                                      final InetAddress externalSuggestedAddress) {
            super(resultCode);
            this.lifetime = lifetime;
            this.epochTime = epochTime;
            this.mappingNonce = mappingNonce;
            this.protocol = protocol;
            this.internalPort = internalPort;
            this.externalSuggestedPort = externalSuggestedPort;
            this.externalSuggestedAddress = requireNonNull(externalSuggestedAddress);
        }

        public long getLifetime() {
            return lifetime;
        }

        public long getEpochTime() {
            return epochTime;
        }

        public byte[] getMappingNonce() {
            return mappingNonce.clone();
        }

        public int getProtocol() {
            return protocol;
        }

        public int getInternalPort() {
            return internalPort;
        }

        public int getExternalSuggestedPort() {
            return externalSuggestedPort;
        }

        public InetAddress getExternalSuggestedAddress() {
            return externalSuggestedAddress;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MappingResponseMessage that = (MappingResponseMessage) o;
            return lifetime == that.lifetime && epochTime == that.epochTime && protocol == that.protocol && internalPort == that.internalPort && externalSuggestedPort == that.externalSuggestedPort && Arrays.equals(mappingNonce, that.mappingNonce) && Objects.equals(externalSuggestedAddress, that.externalSuggestedAddress);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(lifetime, epochTime, protocol, internalPort, externalSuggestedPort, externalSuggestedAddress);
            result = 31 * result + Arrays.hashCode(mappingNonce);
            return result;
        }
    }

    @SuppressWarnings("java:S2972")
    public enum ResultCode {
        SUCCESS((short) 0),
        UNSUPP_VERSION((short) 1),
        NOT_AUTHORIZED((short) 2),
        MALFORMED_REQUEST((short) 3),
        UNSUPP_OPCODE((short) 4),
        UNSUPP_OPTION((short) 5),
        MALFORMED_OPTION((short) 6),
        NETWORK_FAILURE((short) 7),
        NO_RESOURCES((short) 8),
        UNSUPP_PROTOCOL((short) 9),
        USER_EX_QUOTA((short) 10),
        CANNOT_PROVIDE_EXTERNAL((short) 11),
        ADDRESS_MISMATCH((short) 12),
        EXCESSIVE_REMOTE_PEERS((short) 13);
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
