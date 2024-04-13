/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.EventLoopGroupUtil;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import static io.netty.channel.ChannelOption.valueOf;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * The {@link ChannelConfig} for {@link DrasylServerChannel}s.
 */
public class DrasylServerChannelConfig extends DefaultChannelConfig {
    private static final IllegalStateException CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION = new IllegalStateException("Can only changed before channel was registered");
    private static final boolean UDP_SO_REUSEADDR = Boolean.getBoolean(System.getProperty("reuseAddress", "false"));
    public static final Map<IdentityPublicKey, InetSocketAddress> DEFAULT_SUPER_PEERS = Map.of(
            IdentityPublicKey.of("c0900bcfabc493d062ecd293265f571edb70b85313ba4cdda96c9f77163ba62d"), new InetSocketAddress("sp-fkb1.drasyl.org", 22527),
            IdentityPublicKey.of("5b4578909bf0ad3565bb5faf843a9f68b325dd87451f6cb747e49d82f6ce5f4c"), new InetSocketAddress("sp-rjl1.drasyl.org", 22527),
            IdentityPublicKey.of("bf3572dba7ebb6c5ccd037f3a978707b5d7c5a9b9b01b56b4b9bf059af56a4e0"), new InetSocketAddress("sp-nyc1.drasyl.org", 22527),
            IdentityPublicKey.of("ab7a1654d463f9986530bed00569cc895697827b802153b8ef1598579713045f"), new InetSocketAddress("sp-sgp1.drasyl.org", 22527)
    );

    public static final ChannelOption<Integer> NETWORK_ID = valueOf("NETWORK_ID");
    public static final ChannelOption<PeersManager> PEERS_MANAGER = valueOf("PEERS_MANAGER");
    public static final ChannelOption<Boolean> ARMING_ENABLED = valueOf("ARM_ENABLED");
    public static final ChannelOption<Integer> ARMING_SESSION_MAX_COUNT = valueOf("ARM_SESSION_MAX_COUNT");
    public static final ChannelOption<Duration> ARMING_SESSION_EXPIRE_AFTER = valueOf("ARM_SESSION_EXPIRE_AFTER");
    public static final ChannelOption<Duration> HELLO_INTERVAL = valueOf("HELLO_INTERVAL");
    public static final ChannelOption<Duration> HELLO_TIMEOUT = valueOf("HELLO_TIMEOUT");
    public static final ChannelOption<Integer> MAX_PEERS = valueOf("MAX_PEERS");
    public static final ChannelOption<Map<IdentityPublicKey, InetSocketAddress>> SUPER_PEERS = valueOf("SUPER_PEERS");
    public static final ChannelOption<InetSocketAddress> UDP_BIND = valueOf("UDP_BIND");
    public static final ChannelOption<Supplier<EventLoop>> UDP_EVENT_LOOP_SUPPLIER = valueOf("UDP_EVENT_LOOP_SUPPLIER");
    public static final ChannelOption<Class<? extends DatagramChannel>> UDP_CHANNEL_CLASS = valueOf("UDP_CHANNEL_CLASS");
    public static final ChannelOption<Bootstrap> UDP_BOOTSTRAP = valueOf("UDP_BOOTSTRAP");
    public static final ChannelOption<Duration> MAX_MESSAGE_AGE = valueOf("MAX_MESSAGE_AGE");
    public static final ChannelOption<Boolean> HOLE_PUNCHING_ENABLED = valueOf("HOLE_PUNCHING");
    public static final ChannelOption<Duration> PATH_IDLE_TIME = valueOf("PATH_IDLE_TIME");
    public static final ChannelOption<Byte> HOP_LIMIT = valueOf("HOP_LIMIT");

    private volatile int networkId = 1;
    private volatile PeersManager peersManager = new PeersManager();
    private volatile boolean armingEnabled = true;
    private volatile int armingSessionMaxCount = 100;
    private volatile Duration armingSessionExpireAfter = Duration.ZERO;
    private volatile Duration helloInterval = ofSeconds(5);
    private volatile Duration helloTimeout = ofSeconds(30);
    private volatile int maxPeers = 100;
    private volatile Map<IdentityPublicKey, InetSocketAddress> superPeers = DEFAULT_SUPER_PEERS;
    private volatile InetSocketAddress udpBind = new InetSocketAddress(22527);
    private volatile Supplier<EventLoop> udpEventLoopSupplier;
    private volatile Class<? extends DatagramChannel> udpChannelClass = EventLoopGroupUtil.getBestDatagramChannel();
    private volatile Bootstrap udpBootstrap = new Bootstrap()
            .option(ChannelOption.SO_BROADCAST, false)
            .option(ChannelOption.SO_REUSEADDR, UDP_SO_REUSEADDR)
            .option(ChannelOption.IP_TOS, 0xB8);
    private volatile Duration maxMessageAge = ofSeconds(60);
    private volatile boolean holePunchingEnabled = true;
    private volatile Duration pathIdleTime = ofSeconds(60);
    private volatile Byte hopLimit = 8;

    public DrasylServerChannelConfig(final Channel channel) {
        super(channel);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                NETWORK_ID,
                PEERS_MANAGER,
                ARMING_ENABLED,
                ARMING_SESSION_MAX_COUNT,
                ARMING_SESSION_EXPIRE_AFTER,
                HELLO_INTERVAL,
                HELLO_TIMEOUT,
                MAX_PEERS,
                SUPER_PEERS,
                UDP_BIND,
                UDP_EVENT_LOOP_SUPPLIER,
                UDP_CHANNEL_CLASS,
                UDP_BOOTSTRAP,
                MAX_MESSAGE_AGE,
                HOLE_PUNCHING_ENABLED,
                PATH_IDLE_TIME,
                HOP_LIMIT
        );
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public <T> T getOption(ChannelOption<T> option) {
        if (option == NETWORK_ID) {
            return (T) Integer.valueOf(getNetworkId());
        }
        if (option == PEERS_MANAGER) {
            return (T) getPeersManager();
        }
        if (option == ARMING_ENABLED) {
            return (T) Boolean.valueOf(isArmingEnabled());
        }
        if (option == ARMING_SESSION_MAX_COUNT) {
            return (T) Integer.valueOf(getArmingSessionMaxCount());
        }
        if (option == ARMING_SESSION_EXPIRE_AFTER) {
            return (T) getArmingSessionExpireAfter();
        }
        if (option == HELLO_INTERVAL) {
            return (T) getHelloInterval();
        }
        if (option == HELLO_TIMEOUT) {
            return (T) getHelloTimeout();
        }
        if (option == MAX_PEERS) {
            return (T) Integer.valueOf(getMaxPeers());
        }
        if (option == SUPER_PEERS) {
            return (T) getSuperPeers();
        }
        if (option == UDP_BIND) {
            return (T) getUdpBind();
        }
        if (option == UDP_EVENT_LOOP_SUPPLIER) {
            return (T) getUdpEventLoopSupplier();
        }
        if (option == UDP_CHANNEL_CLASS) {
            return (T) getUdpChannelClass();
        }
        if (option == UDP_BOOTSTRAP) {
            return (T) getUdpBootstrap();
        }
        if (option == MAX_MESSAGE_AGE) {
            return (T) getMaxMessageAge();
        }
        if (option == HOLE_PUNCHING_ENABLED) {
            return (T) Boolean.valueOf(isHolePunchingEnabled());
        }
        if (option == PATH_IDLE_TIME) {
            return (T) getPathIdleTime();
        }
        if (option == HOP_LIMIT) {
            return (T) Byte.valueOf(getHopLimit());
        }
        return super.getOption(option);
    }

    public int getNetworkId() {
        return networkId;
    }

    public PeersManager getPeersManager() {
        return peersManager;
    }

    public boolean isArmingEnabled() {
        return armingEnabled;
    }

    public int getArmingSessionMaxCount() {
        return armingSessionMaxCount;
    }

    public Duration getArmingSessionExpireAfter() {
        return armingSessionExpireAfter;
    }

    public Duration getHelloInterval() {
        return helloInterval;
    }

    public Duration getHelloTimeout() {
        return helloTimeout;
    }

    public int getMaxPeers() {
        return maxPeers;
    }

    public Map<IdentityPublicKey, InetSocketAddress> getSuperPeers() {
        return superPeers;
    }

    public InetSocketAddress getUdpBind() {
        return udpBind;
    }

    public Supplier<EventLoop> getUdpEventLoopSupplier() {
        if (udpEventLoopSupplier == null) {
            udpEventLoopSupplier = EventLoopGroupUtil.getBestEventLoopGroup(1)::next;
        }
        return udpEventLoopSupplier;
    }

    public Class<? extends DatagramChannel> getUdpChannelClass() { return udpChannelClass; }

    public Bootstrap getUdpBootstrap() {
        return udpBootstrap;
    }

    public Duration getMaxMessageAge() {
        return maxMessageAge;
    }

    public boolean isHolePunchingEnabled() {
        return holePunchingEnabled;
    }

    public Duration getPathIdleTime() {
        return pathIdleTime;
    }

    public byte getHopLimit() {
        return hopLimit;
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == NETWORK_ID) {
            setNetworkId((Integer) value);
        }
        else if (option == PEERS_MANAGER) {
            setPeersManager((PeersManager) value);
        }
        else if (option == ARMING_ENABLED) {
            setArmingEnabled((Boolean) value);
        }
        else if (option == ARMING_SESSION_MAX_COUNT) {
            setArmingSessionMaxCount((Integer) value);
        }
        else if (option == ARMING_SESSION_EXPIRE_AFTER) {
            setArmingSessionExpireAfter((Duration) value);
        }
        else if (option == HELLO_INTERVAL) {
            setHelloInterval((Duration) value);
        }
        else if (option == HELLO_TIMEOUT) {
            setHelloTimeout((Duration) value);
        }
        else if (option == MAX_PEERS) {
            setMaxPeers((int) value);
        }
        else if (option == SUPER_PEERS) {
            setSuperPeers((Map<IdentityPublicKey, InetSocketAddress>) value);
        }
        else if (option == UDP_BIND) {
            setUdpBind((InetSocketAddress) value);
        }
        else if (option == UDP_EVENT_LOOP_SUPPLIER) {
            setUdpEventLoopSupplier((Supplier<EventLoop>) value);
        }
        else if (option == UDP_CHANNEL_CLASS) {
            setUdpChannelClass((Class<? extends DatagramChannel>) value);
        }
        else if (option == UDP_BOOTSTRAP) {
            setUdpBootstrap((Bootstrap) value);
        }
        else if (option == MAX_MESSAGE_AGE) {
            setMaxMessageAge((Duration) value);
        }
        else if (option == HOLE_PUNCHING_ENABLED) {
            setHolePunchingEnabled((Boolean) value);
        }
        else if (option == PATH_IDLE_TIME) {
            setPathIdleTime((Duration) value);
        }
        else if (option == HOP_LIMIT) {
            setHopLimit((byte) value);
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

    private void setPeersManager(final PeersManager peersManager) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.peersManager = requireNonNull(peersManager);
    }

    private void setArmingEnabled(final boolean armingEnabled) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.armingEnabled = armingEnabled;
    }

    public void setArmingSessionMaxCount(final int armingSessionMaxCount) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.armingSessionMaxCount = armingSessionMaxCount;
    }

    public void setArmingSessionExpireAfter(final Duration armingSessionExpireAfter) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.armingSessionExpireAfter = requireNonNull(armingSessionExpireAfter);
    }

    private void setMaxPeers(final int maxPeers) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.maxPeers = requireNonNegative(maxPeers);
    }

    public void setHelloInterval(final Duration helloInterval) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.helloInterval = requireNonNull(helloInterval);
    }

    public void setHelloTimeout(final Duration helloTimeout) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.helloTimeout = requireNonNull(helloTimeout);
    }

    public void setSuperPeers(final Map<IdentityPublicKey, InetSocketAddress> superPeers) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.superPeers = requireNonNull(superPeers);
    }

    public void setUdpBind(final InetSocketAddress udpBind) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.udpBind = requireNonNull(udpBind);
    }

    public void setUdpEventLoopSupplier(final Supplier<EventLoop> udpEventLoopSupplier) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.udpEventLoopSupplier = requireNonNull(udpEventLoopSupplier);
    }

    public void setUdpChannelClass(final Class<? extends DatagramChannel> udpChannelClass) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.udpChannelClass = requireNonNull(udpChannelClass);
    }

    public void setUdpBootstrap(final Bootstrap udpBootstrap) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.udpBootstrap = requireNonNull(udpBootstrap);
    }

    public void setMaxMessageAge(final Duration maxMessageAge) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.maxMessageAge = requireNonNegative(maxMessageAge);
    }

    public void setHolePunchingEnabled(final Boolean holePunchingEnabled) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.holePunchingEnabled = holePunchingEnabled;
    }

    public void setPathIdleTime(final Duration pathIdleTime) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.pathIdleTime = requireNonNull(pathIdleTime);
    }

    public void setHopLimit(final byte hopLimit) {
        if (channel.isRegistered()) {
            throw CAN_ONLY_CHANGED_BEFORE_REGISTRATION_EXCEPTION;
        }
        this.hopLimit = hopLimit;
    }
}
