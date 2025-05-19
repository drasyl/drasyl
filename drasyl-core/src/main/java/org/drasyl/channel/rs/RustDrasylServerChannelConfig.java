/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.channel.rs;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.WriteBufferWaterMark;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.ReadBufferWaterMark;
import org.drasyl.identity.IdentityPublicKey;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;

import static io.netty.channel.ChannelOption.valueOf;
import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.rs.Libdrasyl.MAX_PEERS_DEFAULT;
import static org.drasyl.channel.rs.Libdrasyl.RECV_BUF_CAP_DEFAULT;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * The {@link ChannelConfig} for {@link RustDrasylServerChannel}s.
 */
public class RustDrasylServerChannelConfig extends DefaultChannelConfig implements DrasylServerChannelConfig {
    private static final IllegalStateException CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION = new IllegalStateException("Can only changed before channel was registered");

    public static final ChannelOption<Integer> NETWORK_ID = valueOf("NETWORK_ID");
    public static final ChannelOption<Integer> UDP_PORT = valueOf("UDP_PORT");
    public static final ChannelOption<Boolean> ARM_MESSAGES = valueOf("ARM_MESSAGES");
    public static final ChannelOption<Long> MAX_PEERS = valueOf("MAX_PEERS");
    public static final ChannelOption<Byte> MIN_POW_DIFFICULTY = valueOf("MIN_POW_DIFFICULTY");
    public static final ChannelOption<Duration> HELLO_TIMEOUT = valueOf("HELLO_TIMEOUT");
    public static final ChannelOption<Duration> HELLO_MAX_AGE = valueOf("HELLO_MAX_AGE");
    public static final ChannelOption<Map<IdentityPublicKey, InetSocketAddress>> SUPER_PEERS = valueOf("SUPER_PEERS");
    public static final ChannelOption<Long> RECV_BUF_CAP = valueOf("RECV_BUF_CAP");
    public static final ChannelOption<Boolean> PROCESS_UNITES = valueOf("PROCESS_UNITES");
    public static final ChannelOption<String> HELLO_ENDPOINTS = valueOf("HELLO_ENDPOINTS");
    public static final ChannelOption<Duration> HOUSEKEEPING_DELAY = valueOf("HOUSEKEEPING_DELAY");
    public static final ChannelOption<Boolean> INTRA_VM_DISCOVERY_ENABLED = valueOf("INTRA_VM_DISCOVERY_ENABLED");
    public static final ChannelOption<WriteBufferWaterMark> READ_BUFFER_WATER_MARK = valueOf("READ_BUFFER_WATER_MARK");

    private volatile Integer networkId = 1;
    private volatile Integer udpPort;
    private volatile Boolean armMessages;
    private volatile Long maxPeers = MAX_PEERS_DEFAULT;
    private volatile Byte minPowDifficulty;
    private volatile Duration helloTimeout;
    private volatile Duration helloMaxAge;
    private volatile Map<IdentityPublicKey, InetSocketAddress> superPeers;
    private volatile Long recvBufCap = RECV_BUF_CAP_DEFAULT;
    private volatile Boolean processUnites;
    private volatile String helloEndpoints;
    private volatile Duration housekeepingDelay;
    private volatile Boolean intraVmDiscoveryEnabled = true;
    private volatile ReadBufferWaterMark readBufferWaterMark = ReadBufferWaterMark.DEFAULT;

    public RustDrasylServerChannelConfig(final Channel channel) {
        super(channel);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                NETWORK_ID,
                UDP_PORT,
                ARM_MESSAGES,
                MAX_PEERS,
                MIN_POW_DIFFICULTY,
                HELLO_TIMEOUT,
                HELLO_MAX_AGE,
                SUPER_PEERS,
                RECV_BUF_CAP,
                PROCESS_UNITES,
                HELLO_ENDPOINTS,
                HOUSEKEEPING_DELAY,
                INTRA_VM_DISCOVERY_ENABLED,
                READ_BUFFER_WATER_MARK
        );
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public <T> T getOption(final ChannelOption<T> option) {
        if (option == NETWORK_ID) {
            return (T) getNetworkId();
        }
        if (option == UDP_PORT) {
            return (T) getUdpPort();
        }
        if (option == ARM_MESSAGES) {
            return (T) isArmMessages();
        }
        if (option == MAX_PEERS) {
            return (T) getMaxPeers();
        }
        if (option == MIN_POW_DIFFICULTY) {
            return (T) getMinPowDifficulty();
        }
        if (option == HELLO_TIMEOUT) {
            return (T) getHelloTimeout();
        }
        if (option == HELLO_MAX_AGE) {
            return (T) getHelloMaxAge();
        }
        if (option == SUPER_PEERS) {
            return (T) getSuperPeers();
        }
        if (option == RECV_BUF_CAP) {
            return (T) getRecvBufCap();
        }
        if (option == PROCESS_UNITES) {
            return (T) isProcessUnites();
        }
        if (option == HELLO_ENDPOINTS) {
            return (T) getHelloEndpoints();
        }
        if (option == HOUSEKEEPING_DELAY) {
            return (T) getHousekeepingDelay();
        }
        if (option == INTRA_VM_DISCOVERY_ENABLED) {
            return (T) isIntraVmDiscoveryEnabled();
        }
        if (option == READ_BUFFER_WATER_MARK) {
            return (T) getReadBufferWaterMark();
        }
        return super.getOption(option);
    }

    public Integer getNetworkId() {
        return networkId;
    }

    public Integer getUdpPort() {
        return udpPort;
    }

    public Boolean isArmMessages() {
        return armMessages;
    }

    public Long getMaxPeers() {
        return maxPeers;
    }

    public Byte getMinPowDifficulty() {
        return minPowDifficulty;
    }

    public Duration getHelloTimeout() {
        return helloTimeout;
    }

    public Duration getHelloMaxAge() {
        return helloMaxAge;
    }

    @Override
    public Map<IdentityPublicKey, InetSocketAddress> getSuperPeers() {
        return superPeers;
    }

    public Long getRecvBufCap() {
        return recvBufCap;
    }

    public Boolean isProcessUnites() {
        return processUnites;
    }

    public String getHelloEndpoints() {
        return helloEndpoints;
    }

    public Duration getHousekeepingDelay() {
        return housekeepingDelay;
    }

    public Boolean isIntraVmDiscoveryEnabled() {
        return intraVmDiscoveryEnabled;
    }

    public ReadBufferWaterMark getReadBufferWaterMark() {
        return readBufferWaterMark;
    }

    @Override
    public <T> boolean setOption(final ChannelOption<T> option, final T value) {
        validate(option, value);

        if (option == NETWORK_ID) {
            setNetworkId((Integer) value);
        }
        else if (option == UDP_PORT) {
            setUdpPort((Integer) value);
        }
        else if (option == ARM_MESSAGES) {
            setArmMessages((Boolean) value);
        }
        else if (option == MAX_PEERS) {
            setMaxPeers((int) value);
        }
        else if (option == MIN_POW_DIFFICULTY) {
            setMinPowDifficulty((Byte) value);
        }
        else if (option == HELLO_TIMEOUT) {
            setHelloTimeout((Duration) value);
        }
        else if (option == HELLO_MAX_AGE) {
            setHelloMaxAge((Duration) value);
        }
        else if (option == SUPER_PEERS) {
            setSuperPeers((Map<IdentityPublicKey, InetSocketAddress>) value);
        }
        else if (option == RECV_BUF_CAP) {
            setRecvBufCap((Long) value);
        }
        else if (option == PROCESS_UNITES) {
            setProcessUnites((Boolean) value);
        }
        else if (option == HELLO_ENDPOINTS) {
            setHelloEndpoints((String) value);
        }
        else if (option == HOUSEKEEPING_DELAY) {
            setHousekeepingDelay((Duration) value);
        }
        else if (option == INTRA_VM_DISCOVERY_ENABLED) {
            setIntraVmDiscoveryEnabled((Boolean) value);
        }
        else if (option == READ_BUFFER_WATER_MARK) {
            setReadBufferWaterMark((ReadBufferWaterMark) value);
        }
        else {
            return super.setOption(option, value);
        }

        return true;
    }

    private void setNetworkId(final int networkId) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.networkId = networkId;
    }

    public void setUdpPort(final Integer udpPort) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.udpPort = udpPort;
    }

    private void setArmMessages(final boolean armMessages) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.armMessages = armMessages;
    }

    private void setMaxPeers(final long maxPeers) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.maxPeers = requireNonNegative(maxPeers);
    }

    private void setMinPowDifficulty(final byte minPowDifficulty) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.minPowDifficulty = requireNonNegative(minPowDifficulty);
    }

    public void setHelloTimeout(final Duration helloTimeout) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.helloTimeout = requireNonNull(helloTimeout);
    }

    public void setHelloMaxAge(final Duration helloMaxAge) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.helloMaxAge = requireNonNull(helloMaxAge);
    }

    public void setSuperPeers(final Map<IdentityPublicKey, InetSocketAddress> superPeers) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.superPeers = requireNonNull(superPeers);
    }

    private void setRecvBufCap(final Long recvBufCap) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.recvBufCap = requireNonNull(recvBufCap);
    }

    private void setProcessUnites(final Boolean processUnites) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.processUnites = requireNonNull(processUnites);
    }

    private void setHelloEndpoints(final String helloEndpoints) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.helloEndpoints = requireNonNull(helloEndpoints);
    }

    private void setHousekeepingDelay(final Duration housekeepingDelay) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.housekeepingDelay = requireNonNull(housekeepingDelay);
    }

    private void setIntraVmDiscoveryEnabled(final boolean intraVmDiscoveryEnabled) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
    }

    private void setReadBufferWaterMark(final ReadBufferWaterMark readBufferWaterMark) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.readBufferWaterMark = requireNonNull(readBufferWaterMark);
    }
}
