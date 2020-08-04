package org.drasyl.util;

import com.google.common.base.Suppliers;
import com.offbynull.portmapper.PortMapperFactory;
import com.offbynull.portmapper.gateway.Bus;
import com.offbynull.portmapper.gateways.network.NetworkGateway;
import com.offbynull.portmapper.gateways.process.ProcessGateway;
import com.offbynull.portmapper.mapper.MappedPort;
import com.offbynull.portmapper.mapper.PortMapper;
import com.offbynull.portmapper.mapper.PortType;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Class for the creation of port mappings to make local services externally/publicly accessible.
 */
public class PortMappingUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PortMappingUtil.class);
    private static final Duration ROUTERS_CACHE_TIME = ofMinutes(10);
    private static final Duration PORT_LIFETIME = ofMinutes(5);
    private static final Duration RETRY_DELAY = ofSeconds(10);
    private static Supplier<List<PortMapper>> mappersSupplier;

    private PortMappingUtil() {
        // util class
    }

    /**
     * Exposes {@code address} via PCP, NAT-PMP, or UPNP-IGD. This operation scans the local network
     * for relevant routers. If no devices are discovered or no port forwardings can be created,
     * this method will return an empty set.
     * <p>
     * Note: This is a blocking method, because it connects to other devices that may react slowly
     * or not at all.
     *
     * @param address address to be exposed
     * @return List of port mappings.
     */
    public static Set<PortMapping> expose(InetSocketAddress address) {
        // discover available port mapping devices
        List<PortMapper> mappers = discoverMappers(address.getAddress());

        Set<PortMapping> mappings = new HashSet<>();
        for (PortMapper mapper : mappers) {
            try {
                mappings.add(new PortMapping(mapper, address));
            }
            catch (IllegalStateException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(e.getMessage());
                }
            }
        }

        return mappings;
    }

    /**
     * Searches for PCP, NAT-PMP, or UPNP-IGD enabled routers responsible for {@code address}.
     * <p>
     * Note: Because this is an expensive operation, the search result is cached for the time
     * defined in {@link #ROUTERS_CACHE_TIME}.
     *
     * @param address IP address for which routers will be searched for
     * @return list of routers that are in the same network as {@code address}.
     */
    private static synchronized List<PortMapper> discoverMappers(
            InetAddress address) {
        if (mappersSupplier == null) {
            Bus networkBus = NetworkGateway.create().getBus();
            Bus processBus = ProcessGateway.create().getBus();
            mappersSupplier = Suppliers.memoizeWithExpiration(() -> {
                try {
                    LOG.debug("Search for PCP, NAT-PMP, or UPNP-IGD enabled routers on all available network interfaces...");
                    List<PortMapper> mappers = PortMapperFactory.discover(networkBus, processBus);
                    LOG.debug("{} router(s) discovered.", mappers.size());
                    return mappers;
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return List.of();
                }
            }, ROUTERS_CACHE_TIME.toMillis(), MILLISECONDS);
        }

        // filter out mappers that are not in same network
        if (!address.isAnyLocalAddress()) {
            short networkPrefixLength = NetworkUtil.getNetworkPrefixLength(address);
            return mappersSupplier.get()
                    .stream().filter(m -> NetworkUtil.sameNetwork(address, m.getSourceAddress(), networkPrefixLength))
                    .collect(Collectors.toList());
        }
        else {
            return mappersSupplier.get();
        }
    }

    /**
     * Represents a port mapping.
     */
    public static class PortMapping {
        private final PortMapper mapper;
        private final InetSocketAddress address;
        private final Subject<Optional<InetSocketAddress>> externalAddressObservable;
        private final Scheduler scheduler;
        private MappedPort mappedPort;
        private Disposable refreshDisposable;

        PortMapping(PortMapper mapper,
                    InetSocketAddress address,
                    Subject<Optional<InetSocketAddress>> externalAddressObservable,
                    Scheduler scheduler,
                    MappedPort mappedPort,
                    Disposable refreshDisposable) {
            this.mapper = mapper;
            this.address = address;
            this.externalAddressObservable = externalAddressObservable;
            this.scheduler = scheduler;
            this.mappedPort = mappedPort;
            this.refreshDisposable = refreshDisposable;
        }

        PortMapping(PortMapper mapper,
                    InetSocketAddress address,
                    Subject<Optional<InetSocketAddress>> externalAddressObservable,
                    Scheduler scheduler) {
            this(mapper, address, externalAddressObservable, scheduler, null, null);

            try {
                createMapping();
            }
            catch (IllegalStateException e) {
                throw new IllegalStateException(mappingMethod() + " router " + mapper.getSourceAddress() + " was unable to create port mapping for " + address + ": " + e.getMessage());
            }
        }

        /**
         * Attempts to create a port mapping for {@code address} at router {@code mapper}.
         *
         * @param mapper  router where the port mapping will be requested.
         * @param address address to be exposed
         * @throws IllegalStateException if the port could not be mapped for any reason
         */
        public PortMapping(PortMapper mapper,
                           InetSocketAddress address) {
            this(mapper, address, BehaviorSubject.createDefault(Optional.empty()), DrasylScheduler.getInstanceLight());
        }

        /**
         * Closes this port mapping.
         */
        public synchronized void close() {
            if (mappedPort != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Close {} port mapping {} -> {} on {} router", mappedPort.getPortType(), currentExternalAddress(), address, mappingMethod());
                }
                try {
                    refreshDisposable.dispose();
                    refreshDisposable = null;
                    mapper.unmapPort(mappedPort);
                }
                catch (IllegalStateException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Unable to close port mapping on {} router for {}: {}", mappingMethod(), address, e.getMessage());
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                finally {
                    externalAddressObservable.onNext(Optional.empty());
                    externalAddressObservable.onComplete();
                    mappedPort = null;
                }
            }
        }

        /**
         * Returns the external address of the port mapping.
         *
         * @return external address or {@code null} if mapping has been closed
         */
        public InetSocketAddress currentExternalAddress() {
            return externalAddressObservable.blockingFirst().orElse(null);
        }

        private String mappingMethod() {
            return mapper.getClass().getSimpleName().replace("PortMapper", "");
        }

        @Override
        public String toString() {
            return "PortMapping{" +
                    "address=" + address +
                    ", currentExternalAddress=" + currentExternalAddress() +
                    ", mappingMethod=" + mappingMethod() +
                    '}';
        }

        /**
         * Returns an {@link Observable} which emits the current and every future external
         * addresses. Can be used to observe this port mapping for address changes made by the
         * exposing router.
         *
         * @return {@link Observable} with current and future external addresses
         */
        public Observable<Optional<InetSocketAddress>> externalAddress() {
            return externalAddressObservable.distinctUntilChanged().subscribeOn(scheduler);
        }

        private void scheduleMappingRefresh(Duration delay) {
            refreshDisposable = scheduler.scheduleDirect(() -> {
                try {
                    createMapping();
                }
                catch (IllegalStateException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Unable to refresh {} port mapping on {} router for {}. Retry in {}ms", mappedPort.getPortType(), mappingMethod(), address, RETRY_DELAY.toMillis());
                    }
                    externalAddressObservable.onNext(Optional.empty());
                    scheduleMappingRefresh(RETRY_DELAY);
                }
            }, delay.toMillis(), MILLISECONDS);
        }

        private void createMapping() {
            try {
                mappedPort = mapper.mapPort(PortType.TCP, address.getPort(), address.getPort(), PORT_LIFETIME.getSeconds());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} router has created {} port mapping {} -> {} (lifetime: {}s)", mappingMethod(), mappedPort.getPortType(), currentExternalAddress(), address, mappedPort.getLifetime());
                }
                externalAddressObservable.onNext(Optional.of(new InetSocketAddress(mappedPort.getExternalAddress().getHostName(), mappedPort.getExternalPort())));

                if (mappedPort.getLifetime() > 0) {
                    scheduleMappingRefresh(ofSeconds(mappedPort.getLifetime()).dividedBy(2));
                }
                else {
                    throw new IllegalStateException("Non-positive lifetime (" + mappedPort.getLifetime() + "s) received from " + mappingMethod() + " router for " + mappedPort.getPortType() + " port mapping " + currentExternalAddress() + " -> " + address);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}