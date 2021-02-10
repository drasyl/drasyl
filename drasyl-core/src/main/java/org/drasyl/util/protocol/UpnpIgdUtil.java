/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.util.protocol;

import org.drasyl.DrasylNode;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.regex.Pattern.DOTALL;

/**
 * Utility class for Universal Plug and Play (UPnP) Internet Gateway Device-related stuff.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6970">https://tools.ietf.org/html/rfc6970</a>
 */
@SuppressWarnings({ "java:S1192" })
public class UpnpIgdUtil {
    public static final InetSocketAddressWrapper SSDP_MULTICAST_ADDRESS = new InetSocketAddressWrapper("239.255.255.250", 1900);
    public static final Duration SSDP_MAX_WAIT_TIME = ofSeconds(3);
    public static final Pattern SSDP_DISCOVERY_RESPONSE_PATTERN = Pattern.compile("^HTTP/1\\.1 [0-9]+?");
    public static final Pattern SSDP_HEADER_PATTERN = Pattern.compile("(.*?):\\s*(.*)$");
    public static final Pattern UPNP_SERVICE_PATTERN = Pattern.compile("<serviceType>(urn:schemas-upnp-org:service:WANIPConnection:\\d+?)</serviceType>.*?<controlURL>(.+?)</controlURL>", DOTALL);
    public static final Pattern UPNP_EXTERNAL_IP_ADDRESS_PATTERN = Pattern.compile("<NewExternalIPAddress>(.+?)</NewExternalIPAddress>");
    public static final Pattern UPNP_ERROR_PATTERN = Pattern.compile("<errorCode>(\\d+?)</errorCode>");
    public static final Pattern UPNP_NEW_CONNECTION_STATUS_PATTERN = Pattern.compile("<NewConnectionStatus>(.+?)</NewConnectionStatus>");
    public static final Pattern UPNP_NEW_PORT_MAPPING_DESCRIPTION_PATTERN = Pattern.compile("<NewPortMappingDescription>(.+?)</NewPortMappingDescription>");
    public static final Pattern UPNP_NEW_INTERNAL_PORT_PATTERN = Pattern.compile("<NewInternalPort>(.+?)</NewInternalPort>");
    public static final Pattern UPNP_NEW_INTERNAL_CLIENT_PATTERN = Pattern.compile("<NewInternalClient>(.+?)</NewInternalClient>");
    public static final Pattern UPNP_NEW_LEASE_DURATION_PATTERN = Pattern.compile("<NewLeaseDuration>(.+?)</NewLeaseDuration>");
    private static final Logger LOG = LoggerFactory.getLogger(UpnpIgdUtil.class);

    private final HttpClient httpClient;
    private final Supplier<Socket> socketSupplier;

    UpnpIgdUtil(final HttpClient httpClient, final Supplier<Socket> socketSupplier) {
        this.httpClient = httpClient;
        this.socketSupplier = requireNonNull(socketSupplier);
    }

    public UpnpIgdUtil() {
        this(HttpClient.newHttpClient(), Socket::new);
    }

    public Service getUpnpService(final URI url) {
        try {
            // we use a raw tcp connection here, because we need the socket object to get our
            // local ip address
            try (final Socket socket = socketSupplier.get()) {
                LOG.debug("Request root xml from `{}` to get additional service information.", url);
                socket.connect(new InetSocketAddress(url.getHost(), url.getPort()), (int) ofSeconds(5).toMillis());
                final PrintWriter printWriter = new PrintWriter(socket.getOutputStream());
                printWriter.print("GET " + url.getPath() + " HTTP/1.1\r\n");
                printWriter.print("Host: " + url.getHost() + ":" + url.getPort() + "\r\n");
                printWriter.print("Connection: Close\r\n");
                printWriter.print("\r\n");
                printWriter.flush();
                final String response = new String(socket.getInputStream().readAllBytes());

                final String[] responseParts = response.split("\r\n\r\n", 2);
                if (responseParts.length == 2) {
                    final String[] headers = responseParts[0].split("\r\n");
                    final String body = responseParts[1];
                    if (headers.length > 0 && headers[0].endsWith(" 200 OK")) {
                        final Matcher matcher = UPNP_SERVICE_PATTERN.matcher(body);
                        if (matcher.find()) {
                            final String localAddress = socket.getLocalAddress().getHostAddress();
                            final String serviceType = matcher.group(1);
                            final URI controlUrl = url.resolve(matcher.group(2));

                            return new Service(serviceType, controlUrl, localAddress);
                        }
                    }
                }
            }
        }
        catch (final IOException e) {
            LOG.warn("Unable to request root xml.", e);
        }

        return null;
    }

    public MappingEntry getSpecificPortMappingEntry(final URI url, final String serviceType,
                                                    final Integer externalPort) throws InterruptedException {
        final Map<String, Object> args = Map.of(
                "NewRemoteHost", "",
                "NewExternalPort", externalPort,
                "NewProtocol", "UDP"
        );
        final String response = soapRequest(url, serviceType, "GetSpecificPortMappingEntry", args);
        if (response == null) {
            return null;
        }

        Matcher matcher = UPNP_ERROR_PATTERN.matcher(response);
        final int errorCode;
        if (matcher.find()) {
            errorCode = Integer.parseInt(matcher.group(1));
        }
        else {
            errorCode = 0;
        }

        matcher = UPNP_NEW_INTERNAL_PORT_PATTERN.matcher(response);
        final int internalPort;
        if (matcher.find()) {
            internalPort = Integer.parseInt(matcher.group(1));
        }
        else {
            internalPort = -1;
        }

        matcher = UPNP_NEW_INTERNAL_CLIENT_PATTERN.matcher(response);
        InetAddress internalClient;
        if (matcher.find()) {
            try {
                internalClient = InetAddress.getByName(matcher.group(1));
            }
            catch (final UnknownHostException e) {
                internalClient = null;
            }
        }
        else {
            internalClient = null;
        }

        matcher = UPNP_NEW_PORT_MAPPING_DESCRIPTION_PATTERN.matcher(response);
        final String description;
        if (matcher.find()) {
            description = matcher.group(1);
        }
        else {
            description = null;
        }

        matcher = UPNP_NEW_LEASE_DURATION_PATTERN.matcher(response);
        final int leaseDuration;
        if (matcher.find()) {
            leaseDuration = Integer.parseInt(matcher.group(1));
        }
        else {
            leaseDuration = -1;
        }

        return new MappingEntry(errorCode, internalPort, internalClient, description, leaseDuration);
    }

    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    String soapRequest(final URI url,
                       final String serviceType,
                       final String service,
                       final Map<String, Object> arguments) throws InterruptedException {
        final StringBuilder argumentsXml = new StringBuilder();
        for (final Map.Entry<String, Object> entry : arguments.entrySet()) {
            argumentsXml.append("<" + entry.getKey() + ">");
            argumentsXml.append(entry.getValue());
            argumentsXml.append("</" + entry.getKey() + ">");
        }

        try {
            final HttpRequest request = HttpRequest.newBuilder(url)
                    .header("SOAPAction", serviceType + "#" + service)
                    .header("Content-Type", "text/xml")
                    .POST(HttpRequest.BodyPublishers.ofString("<?xml version=\"1.0\"?>\n" +
                            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                            "<s:Body>" +
                            "<u:" + service + " xmlns:u=\"" + serviceType + "\">" +
                            argumentsXml.toString() +
                            "</u:" + service + ">" +
                            "</s:Body>" +
                            "</s:Envelope>")).build();

            LOG.debug("Do SOAP Request `{}` to `{}`.", service, url);
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }
        catch (final IOException e) {
            return null;
        }
    }

    public PortMapping addPortMapping(final URI url,
                                      final String serviceType,
                                      final Integer externalPort,
                                      final String localAddress,
                                      final String description) throws InterruptedException {
        final Map<String, Object> args = Map.of(
                "NewRemoteHost", "",
                "NewExternalPort", externalPort,
                "NewProtocol", "UDP",
                "NewInternalPort", externalPort,
                "NewInternalClient", localAddress,
                "NewEnabled", 1,
                "NewPortMappingDescription", description,
                "NewLeaseDuration", 0 // see rfc6886, section 9.5., third paragraph...
        );
        final String response = soapRequest(url, serviceType, "AddPortMapping", args);
        if (response == null) {
            return null;
        }

        final Matcher matcher = UPNP_ERROR_PATTERN.matcher(response);
        final int errorCode;
        if (matcher.find()) {
            errorCode = Integer.parseInt(matcher.group(1));
        }
        else {
            errorCode = 0;
        }

        return new PortMapping(errorCode);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean deletePortMapping(final URI url,
                                     final String serviceType,
                                     final int port) throws InterruptedException {
        final Map<String, Object> args = Map.of(
                "NewRemoteHost", "",
                "NewExternalPort", port,
                "NewProtocol", "UDP"
        );
        final String response = soapRequest(url, serviceType, "DeletePortMapping", args);
        return response != null;
    }

    public StatusInfo getStatusInfo(final URI url,
                                    final String serviceType) throws InterruptedException {
        final String response = soapRequest(url, serviceType, "GetStatusInfo", Map.of());
        if (response == null) {
            return null;
        }

        final String newConnectionStatus;
        final Matcher matcher = UPNP_NEW_CONNECTION_STATUS_PATTERN.matcher(response);
        if (matcher.find()) {
            newConnectionStatus = matcher.group(1);
        }
        else {
            newConnectionStatus = null;
        }

        return new StatusInfo(newConnectionStatus);
    }

    public ExternalIpAddress getExternalIpAddress(final URI url,
                                                  final String serviceType) throws InterruptedException {
        final String response = soapRequest(url, serviceType, "GetExternalIPAddress", Map.of());
        if (response == null) {
            return null;
        }

        final Matcher matcher = UPNP_EXTERNAL_IP_ADDRESS_PATTERN.matcher(response);
        InetAddress newExternalIpAddress;
        if (matcher.find()) {
            try {
                newExternalIpAddress = InetAddress.getByName(matcher.group(1));
            }
            catch (final UnknownHostException e) {
                newExternalIpAddress = null;
            }
        }
        else {
            newExternalIpAddress = null;
        }

        return new ExternalIpAddress(newExternalIpAddress);
    }

    public static byte[] buildDiscoveryMessage() {
        final String content = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: " + SSDP_MULTICAST_ADDRESS.getAddress().getHostAddress() + ":" + SSDP_MULTICAST_ADDRESS.getPort() + "\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: " + SSDP_MAX_WAIT_TIME.toSeconds() + "\r\n" +
                "USER-AGENT: drasyl/" + DrasylNode.getVersion() + "\r\n" +
                "ST: ssdp:all\r\n" +
                "\r\n";
        return content.getBytes(UTF_8);
    }

    public static Message readMessage(final byte[] content) {
        final String contentStr = new String(content);
        final String[] parts = contentStr.split("\r\n\r\n", 2);

        // read header
        if (parts.length > 0) {
            final String header = parts[0];
            final String[] headerLines = header.split("\r\n");
            if (headerLines.length > 0 && SSDP_DISCOVERY_RESPONSE_PATTERN.matcher(headerLines[0]).find()) {
                final Map<String, String> headerFields = Arrays
                        .stream(headerLines, 1, headerLines.length)
                        .map(SSDP_HEADER_PATTERN::matcher)
                        .filter(Matcher::matches)
                        .collect(Collectors.toMap(m -> m.group(1).toUpperCase(), m -> m.group(2)));
                final String serviceType = headerFields.get("ST");
                final String location = headerFields.get("LOCATION");

                return new DiscoveryResponseMessage(serviceType, location);
            }
        }

        return null;
    }

    public interface Message {
    }

    public static class Service {
        private final String serviceType;
        private final URI controlUrl;
        private final String localAddress;

        public Service(final String serviceType,
                       final URI controlUrl,
                       final String localAddress) {
            this.serviceType = requireNonNull(serviceType);
            this.controlUrl = requireNonNull(controlUrl);
            this.localAddress = requireNonNull(localAddress);
        }

        public String getServiceType() {
            return serviceType;
        }

        public URI getControlUrl() {
            return controlUrl;
        }

        public String getLocalAddress() {
            return localAddress;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Service service = (Service) o;
            return Objects.equals(serviceType, service.serviceType) && Objects.equals(controlUrl, service.controlUrl) && Objects.equals(localAddress, service.localAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceType, controlUrl, localAddress);
        }
    }

    @SuppressWarnings("unused")
    public static class MappingEntry {
        private final int internalPort;
        private final InetAddress internalClient;
        private final String description;
        private final int leaseDuration;
        private final int errorCode;

        public MappingEntry(final int errorCode,
                            final int internalPort,
                            final InetAddress internalClient,
                            final String description,
                            final int leaseDuration) {
            this.errorCode = errorCode;
            this.internalPort = internalPort;
            this.internalClient = internalClient;
            this.description = description;
            this.leaseDuration = leaseDuration;
        }

        public int getInternalPort() {
            return internalPort;
        }

        public InetAddress getInternalClient() {
            return internalClient;
        }

        public String getDescription() {
            return description;
        }

        public int getLeaseDuration() {
            return leaseDuration;
        }

        public int getErrorCode() {
            return errorCode;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MappingEntry that = (MappingEntry) o;
            return internalPort == that.internalPort && leaseDuration == that.leaseDuration && errorCode == that.errorCode && Objects.equals(internalClient, that.internalClient) && Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(internalPort, internalClient, description, leaseDuration, errorCode);
        }
    }

    @SuppressWarnings("unused")
    public static class StatusInfo {
        private final String newConnectionStatus;

        public StatusInfo(final String newConnectionStatus) {
            this.newConnectionStatus = newConnectionStatus;
        }

        public String getNewConnectionStatus() {
            return newConnectionStatus;
        }

        public boolean isConnected() {
            return newConnectionStatus.equals("Connected") || newConnectionStatus.equals("Up");
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final StatusInfo that = (StatusInfo) o;
            return Objects.equals(newConnectionStatus, that.newConnectionStatus);
        }

        @Override
        public int hashCode() {
            return Objects.hash(newConnectionStatus);
        }
    }

    @SuppressWarnings("unused")
    public static class ExternalIpAddress {
        private final InetAddress newExternalIpAddress;

        public ExternalIpAddress(final InetAddress newExternalIpAddress) {
            this.newExternalIpAddress = newExternalIpAddress;
        }

        public InetAddress getNewExternalIpAddress() {
            return newExternalIpAddress;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ExternalIpAddress that = (ExternalIpAddress) o;
            return Objects.equals(newExternalIpAddress, that.newExternalIpAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(newExternalIpAddress);
        }
    }

    @SuppressWarnings("unused")
    public static class DiscoveryResponseMessage implements Message {
        private final String serviceType;
        private final String location;

        public DiscoveryResponseMessage(final String serviceType, final String location) {
            this.serviceType = requireNonNull(serviceType);
            this.location = requireNonNull(location);
        }

        public String getServiceType() {
            return serviceType;
        }

        public String getLocation() {
            return location;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DiscoveryResponseMessage that = (DiscoveryResponseMessage) o;
            return Objects.equals(serviceType, that.serviceType) && Objects.equals(location, that.location);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceType, location);
        }
    }

    public static class PortMapping {
        private final int errorCode;

        public PortMapping(final int errorCode) {
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final PortMapping that = (PortMapping) o;
            return errorCode == that.errorCode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode);
        }
    }
}
