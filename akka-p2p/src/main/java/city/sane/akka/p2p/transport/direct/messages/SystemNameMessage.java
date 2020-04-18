package city.sane.akka.p2p.transport.direct.messages;

import java.io.Serializable;
import java.util.Objects;

public class SystemNameMessage implements Serializable {
    private String systemName;

    private SystemNameMessage() {
    }

    public SystemNameMessage(String systemName) {
        this.systemName = systemName;
    }

    public String getSystemName() {
        return systemName;
    }

    @Override
    public String toString() {
        return "SystemNameMessage{" +
                "systemName='" + systemName + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SystemNameMessage that = (SystemNameMessage) o;
        return Objects.equals(systemName, that.systemName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(systemName);
    }
}
