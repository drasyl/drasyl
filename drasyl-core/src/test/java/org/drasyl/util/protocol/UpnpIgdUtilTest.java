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

import org.drasyl.crypto.HexUtil;
import org.drasyl.util.protocol.UpnpIgdUtil.DiscoveryResponseMessage;
import org.drasyl.util.protocol.UpnpIgdUtil.ExternalIpAddress;
import org.drasyl.util.protocol.UpnpIgdUtil.MappingEntry;
import org.drasyl.util.protocol.UpnpIgdUtil.Message;
import org.drasyl.util.protocol.UpnpIgdUtil.Service;
import org.drasyl.util.protocol.UpnpIgdUtil.StatusInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.function.Function;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpnpIgdUtilTest {
    @Nested
    class GetUpnpService {
        @Test
        void shouldReturnService(@Mock(answer = RETURNS_DEEP_STUBS) final HttpClient httpClient,
                                 @Mock final Function<InetSocketAddress, InetAddress> remoteAddressProvider) throws IOException, InterruptedException {
            when(httpClient.send(any(), any()).statusCode()).thenReturn(200);
            when(httpClient.send(any(), any()).body()).thenReturn(
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/xml; charset=\"utf-8\"\r\n" +
                            "Connection: close\r\n" +
                            "Content-Length: 3335\r\n" +
                            "Server: AmpliFi/AmpliFi/ UPnP/1.1 MiniUPnPd/2.1\r\n" +
                            "Ext:\r\n" +
                            "\r\n" +
                            "<?xml version=\"1.0\"?>\r\n" +
                            "<root xmlns=\"urn:schemas-upnp-org:device-1-0\" configId=\"1337\"><specVersion><major>1</major><minor>1</minor></specVersion><device><deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:2</deviceType><friendlyName>AmpliFi router</friendlyName><manufacturer>AmpliFi</manufacturer><manufacturerURL>http://www.amplifi.com/</manufacturerURL><modelDescription>AmpliFi router</modelDescription><modelName>AmpliFi router</modelName><modelNumber>1</modelNumber><modelURL>http://www.amplifi.com/</modelURL><serialNumber>00000000</serialNumber><UDN>uuid:db70d2d7-3001-47cc-bc1b-e71e6cc5b573</UDN><serviceList><service><serviceType>urn:schemas-upnp-org:service:Layer3Forwarding:1</serviceType><serviceId>urn:upnp-org:serviceId:L3Forwarding1</serviceId><SCPDURL>/L3F.xml</SCPDURL><controlURL>/ctl/L3F</controlURL><eventSubURL>/evt/L3F</eventSubURL></service><service><serviceType>urn:schemas-upnp-org:service:DeviceProtection:1</serviceType><serviceId>urn:upnp-org:serviceId:DeviceProtection1</serviceId><SCPDURL>/DP.xml</SCPDURL><controlURL>/ctl/DP</controlURL><eventSubURL>/evt/DP</eventSubURL></service><service><serviceType>urn:nvidia-com:service:GeForceNow:1</serviceType><serviceId>urn:nvidia-com:serviceId:GeForceNow1</serviceId><SCPDURL>/NGN.xml</SCPDURL><controlURL>/ctl/NGN</controlURL><eventSubURL>/evt/NGN</eventSubURL></service></serviceList><deviceList><device><deviceType>urn:schemas-upnp-org:device:WANDevice:2</deviceType><friendlyName>WANDevice</friendlyName><manufacturer>MiniUPnP</manufacturer><manufacturerURL>http://miniupnp.free.fr/</manufacturerURL><modelDescription>WAN Device</modelDescription><modelName>WAN Device</modelName><modelNumber>20201022</modelNumber><modelURL>http://miniupnp.free.fr/</modelURL><serialNumber>00000000</serialNumber><UDN>uuid:db70d2d7-3001-47cc-bc1b-e71e6cc5b574</UDN><UPC>000000000000</UPC><serviceList><service><serviceType>urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1</serviceType><serviceId>urn:upnp-org:serviceId:WANCommonIFC1</serviceId><SCPDURL>/WANCfg.xml</SCPDURL><controlURL>/ctl/CmnIfCfg</controlURL><eventSubURL>/evt/CmnIfCfg</eventSubURL></service></serviceList><deviceList><device><deviceType>urn:schemas-upnp-org:device:WANConnectionDevice:2</deviceType><friendlyName>WANConnectionDevice</friendlyName><manufacturer>MiniUPnP</manufacturer><manufacturerURL>http://miniupnp.free.fr/</manufacturerURL><modelDescription>MiniUPnP daemon</modelDescription><modelName>MiniUPnPd</modelName><modelNumber>20201022</modelNumber><modelURL>http://miniupnp.free.fr/</modelURL><serialNumber>00000000</serialNumber><UDN>uuid:db70d2d7-3001-47cc-bc1b-e71e6cc5b575</UDN><UPC>000000000000</UPC><serviceList><service><serviceType>urn:schemas-upnp-org:service:WANIPConnection:2</serviceType><serviceId>urn:upnp-org:serviceId:WANIPConn1</serviceId><SCPDURL>/WANIPCn.xml</SCPDURL><controlURL>/ctl/IPConn</controlURL><eventSubURL>/evt/IPConn</eventSubURL></service><service><serviceType>urn:schemas-upnp-org:service:WANIPv6FirewallControl:1</serviceType><serviceId>urn:upnp-org:serviceId:WANIPv6Firewall1</serviceId><SCPDURL>/WANIP6FC.xml</SCPDURL><controlURL>/ctl/IP6FCtl</controlURL><eventSubURL>/evt/IP6FCtl</eventSubURL></service></serviceList></device></deviceList></device></deviceList><presentationURL>http://192.168.188.1/</presentationURL></device></root>");
            when(remoteAddressProvider.apply(any())).thenReturn(InetAddress.getByName("192.168.188.83"));
            final UpnpIgdUtil underTest = new UpnpIgdUtil(httpClient, remoteAddressProvider);

            final Service service = underTest.getUpnpService(URI.create("http://192.168.188.1:5000/rootDesc.xml"));

            assertEquals(new Service("urn:schemas-upnp-org:service:WANIPConnection:2", URI.create("http://192.168.188.1:5000/ctl/IPConn"), InetAddress.getByName("192.168.188.83")), service);
        }
    }

    @Nested
    class GetSpecificPortMappingEntry {
        @Test
        void shouldReturnMappingEntry() throws InterruptedException, UnknownHostException {
            final UpnpIgdUtil underTest = spy(new UpnpIgdUtil());
            doReturn("<?xml version=\"1.0\"?>\n" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:GetSpecificPortMappingEntryResponse xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:2\"><NewInternalPort>57142</NewInternalPort><NewInternalClient>192.168.188.91</NewInternalClient><NewEnabled>1</NewEnabled><NewPortMappingDescription>drasyl03620addca</NewPortMappingDescription><NewLeaseDuration>604096</NewLeaseDuration></u:GetSpecificPortMappingEntryResponse></s:Body></s:Envelope>")
                    .when(underTest)
                    .soapRequest(URI.create("http://192.168.188.1:5000/ctl/IPConn"), "urn:schemas-upnp-org:service:WANIPConnection:2", "GetSpecificPortMappingEntry", Map.of(
                            "NewRemoteHost", "",
                            "NewExternalPort", 57142,
                            "NewProtocol", "UDP"
                    ));

            final MappingEntry mapping = underTest.getSpecificPortMappingEntry(URI.create("http://192.168.188.1:5000/ctl/IPConn"), "urn:schemas-upnp-org:service:WANIPConnection:2", 57142);
            assertEquals(new MappingEntry(0, 57142, InetAddress.getByName("192.168.188.91"), "drasyl03620addca", 604096), mapping);
        }
    }

    @Nested
    class AddPortMapping {
        @Test
        void shouldReturnPortMapping() throws InterruptedException, UnknownHostException {
            final UpnpIgdUtil underTest = spy(new UpnpIgdUtil());
            doReturn("<?xml version=\"1.0\"?>\n" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:GetSpecificPortMappingEntryResponse xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:2\"><NewInternalPort>57142</NewInternalPort><NewInternalClient>192.168.188.91</NewInternalClient><NewEnabled>1</NewEnabled><NewPortMappingDescription>drasyl03620addca</NewPortMappingDescription><NewLeaseDuration>604096</NewLeaseDuration></u:GetSpecificPortMappingEntryResponse></s:Body></s:Envelope>")
                    .when(underTest)
                    .soapRequest(URI.create("http://192.168.188.1:5000/ctl/IPConn"), "urn:schemas-upnp-org:service:WANIPConnection:2", "AddPortMapping", Map.of(
                            "NewRemoteHost", "",
                            "NewExternalPort", 57142,
                            "NewProtocol", "UDP",
                            "NewInternalPort", 57142,
                            "NewInternalClient", "192.168.188.91",
                            "NewEnabled", 1,
                            "NewPortMappingDescription", "drasyl03620addca",
                            "NewLeaseDuration", 0
                    ));

            final UpnpIgdUtil.PortMapping mapping = underTest.addPortMapping(URI.create("http://192.168.188.1:5000/ctl/IPConn"), "urn:schemas-upnp-org:service:WANIPConnection:2", 57142, InetAddress.getByName("192.168.188.91"), "drasyl03620addca");
            assertEquals(new UpnpIgdUtil.PortMapping(0), mapping);
        }
    }

    @Nested
    class DeletePortMapping {
        @Test
        void shouldReturnTrue() throws InterruptedException {
            final UpnpIgdUtil underTest = spy(new UpnpIgdUtil());
            doReturn("<?xml version=\"1.0\"?>\n" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:DeletePortMappingResponse xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:2\"></u:DeletePortMappingResponse></s:Body></s:Envelope>")
                    .when(underTest)
                    .soapRequest(URI.create("http://192.168.188.1:5000/ctl/IPConn"), "urn:schemas-upnp-org:service:WANIPConnection:2", "DeletePortMapping", Map.of(
                            "NewRemoteHost", "",
                            "NewExternalPort", 57142,
                            "NewProtocol", "UDP"
                    ));

            final boolean deleted = underTest.deletePortMapping(URI.create("http://192.168.188.1:5000/ctl/IPConn"), "urn:schemas-upnp-org:service:WANIPConnection:2", 57142);
            assertTrue(deleted);
        }
    }

    @Nested
    class GetStatusInfo {
        @Test
        void shouldReturnCorrectStatus() throws InterruptedException {
            final UpnpIgdUtil underTest = spy(new UpnpIgdUtil());
            doReturn("<?xml version=\"1.0\"?>\n" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                    "<s:Body><u:GetStatusInfoResponse xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:2\"><NewConnectionStatus>Connected</NewConnectionStatus>" +
                    "<NewLastConnectionError>ERROR_NONE</NewLastConnectionError><NewUptime>1720267</NewUptime></u:GetStatusInfoResponse></s:Body></s:Envelope>\n")
                    .when(underTest)
                    .soapRequest(URI.create("http://192.168.188.1:5000/ctl/IPConn"), "urn:schemas-upnp-org:service:WANIPConnection:2", "GetStatusInfo", Map.of());
            final StatusInfo statusInfo = underTest.getStatusInfo(URI.create("http://192.168.188.1:5000/ctl/IPConn"), "urn:schemas-upnp-org:service:WANIPConnection:2");
            assertEquals(new StatusInfo("Connected"), statusInfo);
        }
    }

    @Nested
    class GetExternalIpAddress {
        @Test
        void shouldReturnCorrectAddress() throws InterruptedException, UnknownHostException {
            final UpnpIgdUtil underTest = spy(new UpnpIgdUtil());
            doReturn("<?xml version=\"1.0\"?>\n" +
                    "<?xml version=\"1.0\"?>\n" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                    "<s:Body><u:GetExternalIPAddressResponse xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:2\">" +
                    "<NewExternalIPAddress>192.168.178.2</NewExternalIPAddress></u:GetExternalIPAddressResponse></s:Body></s:Envelope>")
                    .when(underTest).soapRequest(URI.create("http://192.168.188.1:5000/ctl/IPConn"), "urn:schemas-upnp-org:service:WANIPConnection:2", "GetExternalIPAddress", Map.of());

            final ExternalIpAddress address = underTest.getExternalIpAddress(URI.create("http://192.168.188.1:5000/ctl/IPConn"), "urn:schemas-upnp-org:service:WANIPConnection:2");
            assertEquals(new ExternalIpAddress(InetAddress.getByName("192.168.178.2")), address);
        }
    }

    @Nested
    class ReadMessage {
        @Test
        void shouldReturnDiscoveryResponseMessage() {
            final byte[] content = HexUtil.fromString("485454502f312e3120323030204f4b0d0a43414348452d434f4e54524f4c3a206d61782d6167653d3132300d0a53543a2075726e3a736368656d61732d75706e702d6f72673a6465766963653a57414e436f6e6e656374696f6e4465766963653a320d0a55534e3a20757569643a64623730643264372d333030312d343763632d626331622d6537316536636335623537353a3a75726e3a736368656d61732d75706e702d6f72673a6465766963653a57414e436f6e6e656374696f6e4465766963653a320d0a4558543a0d0a5345525645523a20416d706c6946692f416d706c6946692f2055506e502f312e31204d696e6955506e50642f322e310d0a4c4f434154494f4e3a20687474703a2f2f3139322e3136382e3138382e313a353030302f726f6f74446573632e786d6c0d0a4f50543a2022687474703a2f2f736368656d61732e75706e702e6f72672f75706e702f312f302f223b206e733d30310d0a30312d4e4c533a20313630333337353039370d0a424f4f5449442e55504e502e4f52473a20313630333337353039370d0a434f4e46494749442e55504e502e4f52473a20313333370d0a0d0a");

            final Message message = UpnpIgdUtil.readMessage(content);

            assertEquals(new DiscoveryResponseMessage("urn:schemas-upnp-org:device:WANConnectionDevice:2", "http://192.168.188.1:5000/rootDesc.xml"), message);
        }
    }

    @Nested
    class BuildDiscoveryMessage {
        @Test
        void shouldReturnValidMessage() {
            final byte[] message = UpnpIgdUtil.buildDiscoveryMessage();

            assertThat(new String(message), startsWith("M-SEARCH * HTTP/1.1"));
        }
    }

    @Nested
    class SoapRequest {
        @Test
        void shouldCreateCorrectRequest(@Mock(answer = RETURNS_DEEP_STUBS) final HttpClient httpClient,
                                        @Mock final Function<InetSocketAddress, InetAddress> remoteAddressProvider) throws InterruptedException, IOException {
            when(httpClient.send(any(), any()).body()).thenReturn("response");

            final UpnpIgdUtil underTest = new UpnpIgdUtil(httpClient, remoteAddressProvider);
            final String response = underTest.soapRequest(URI.create("http://192.168.188.1:5000/ctl/IPConn"), "urn:schemas-upnp-org:service:WANIPConnection:2", "GetSpecificPortMappingEntry", Map.of(
                    "NewRemoteHost", "",
                    "NewExternalPort", 57142,
                    "NewProtocol", "UDP"
            ));

            verify(httpClient, times(2)).send(any(), any());
            assertEquals("response", response);
        }
    }

    @Nested
    class TestService {
        @Test
        void getterShouldReturnCorrectValues() throws UnknownHostException {
            final Service service = new Service("urn:schemas-upnp-org:service:WANIPConnection:2", URI.create("http://192.168.188.1:5000/ctl/IPConn"), InetAddress.getByName("192.168.188.83"));

            assertEquals("urn:schemas-upnp-org:service:WANIPConnection:2", service.getServiceType());
            assertEquals(URI.create("http://192.168.188.1:5000/ctl/IPConn"), service.getControlUrl());
            assertEquals(InetAddress.getByName("192.168.188.83"), service.getLocalAddress());
        }
    }

    @Nested
    class TestMappingEntry {
        @Test
        void getterShouldReturnCorrectValues() throws UnknownHostException {
            final MappingEntry mapping = new MappingEntry(0, 57142, InetAddress.getByName("192.168.188.91"), "drasyl03620addca", 604096);

            assertEquals(0, mapping.getErrorCode());
            assertEquals(57142, mapping.getInternalPort());
            assertEquals(InetAddress.getByName("192.168.188.91"), mapping.getInternalClient());
            assertEquals("drasyl03620addca", mapping.getDescription());
            assertEquals(604096, mapping.getLeaseDuration());
        }
    }

    @Nested
    class TestStatusInfo {
        @Test
        void getterShouldReturnCorrectValues() {
            final StatusInfo statusInfo = new StatusInfo("Connected");

            assertEquals("Connected", statusInfo.getNewConnectionStatus());
        }
    }

    @Nested
    class TestExternalIpAddress {
        @Test
        void getterShouldReturnCorrectValues() throws UnknownHostException {
            final ExternalIpAddress address = new ExternalIpAddress(InetAddress.getByName("192.168.178.2"));

            assertEquals(InetAddress.getByName("192.168.178.2"), address.getNewExternalIpAddress());
        }
    }

    @Nested
    class TestDiscoveryResponseMessage {
        @Test
        void getterShouldReturnCorrectValues() {
            final DiscoveryResponseMessage response = new DiscoveryResponseMessage("urn:schemas-upnp-org:device:WANConnectionDevice:2", "http://192.168.188.1:5000/rootDesc.xml");

            assertEquals("urn:schemas-upnp-org:device:WANConnectionDevice:2", response.getServiceType());
            assertEquals("http://192.168.188.1:5000/rootDesc.xml", response.getLocation());
        }
    }
}
