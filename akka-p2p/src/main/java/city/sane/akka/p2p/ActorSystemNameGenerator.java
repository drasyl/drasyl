package city.sane.akka.p2p;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * This class provides helper methods for generating random or unique Akka system names.
 */
public class ActorSystemNameGenerator {
    private ActorSystemNameGenerator() {
    }

    /**
     * Generates a Akka system name that is derived from the local machine's host name.<br>
     * Returns <code>null</code> if no suitable host name could be found.
     *
     * @return
     */
    public static String hostNameSystemName() {
        String hostName = null;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            // ignore
        }

        if (hostName != null && !hostName.isEmpty()) {
            // Remove all characters that Akka does not allow
            return hostName.replaceAll("[^a-zA-Z0-9-_]", "--");
        }
        else {
            return null;
        }
    }

    /**
     * Generates a random Akka system name consisting of <code>prefix</code> and a random string (e.g. Heiko--56f48e47).
     * If the local host name cannot be determined, only the random string is returned (e.g. 56f48e47).
     *
     * @return
     */
    public static String randomSystemName(String prefix) {
        return requireNonNull(prefix) + "--" + randomString();
    }

    /**
     * Generates a random Akka system name consisting of the local host name and a random string (e.g. Heikos-PC--fritz.box--56f48e47).
     * If the local host name cannot be determined, only the random string is returned (e.g. 56f48e47).
     *
     * @return
     */
    public static String randomSystemName() {
        String hostName = hostNameSystemName();

        if (hostName != null) {
            return hashByteArrToString(randomSystemName(hostName).getBytes(StandardCharsets.UTF_8));
        }
        else {
            return randomSystemName();
        }
    }

    /**
     * Generates a unique Akka system name that is derived from the local machine's mac address. The actual mac address is not
     * revealed, because a hash is used.<br>
     * Returns <code>null</code> if no suitable mac address was found or no hash could be generated.
     *
     * @return
     */
    public static String uniqueSystemName() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();

                if (!iface.isUp() || iface.isLoopback() || iface.isPointToPoint()) {
                    continue;
                }

                Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
                while (ifaceAddresses.hasMoreElements()) {
                    InetAddress ifaceAddress = ifaceAddresses.nextElement();
                    if (ifaceAddress.isLoopbackAddress() || ifaceAddress.isLinkLocalAddress() || ifaceAddress.isMulticastAddress()) {
                        continue;
                    }

                    byte[] hardwareAddress = iface.getHardwareAddress();
                    return hashByteArrToString(hardwareAddress);
                }
            }

            return randomSystemName();
        }
        catch (SocketException e) {
            return randomSystemName();
        }
    }

    private static String hashByteArrToString(byte[] bytes) {
        Formatter formatter = new Formatter();
        MessageDigest digestInstance = null;
        try {       // Prefer SHA-1 as encryption algorithm
            digestInstance = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {      // Get a random algorithm
            Object algorithms[] = Security.getAlgorithms("MessageDigest").toArray();
            try {
                digestInstance = MessageDigest.getInstance((String) algorithms[new Random().nextInt(algorithms.length)]);
            } catch (NoSuchAlgorithmException ex) {
                for (byte b : bytes) {
                    formatter.format("%02x", b);
                }
                return formatter.toString().substring(0, 8);
            }
        }

        for (byte b : digestInstance.digest(bytes)) {
            formatter.format("%02x", b);
        }
        return formatter.toString().substring(0, 8);
    }

    private static String randomString() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
