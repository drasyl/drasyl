package city.sane.akka.p2p;

import org.junit.Test;

import static city.sane.akka.p2p.ActorSystemNameGenerator.*;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.Assert.*;

public class ActorSystemNameGeneratorTest {
    private static final String VALID_SYSTEM_NAME = "^[a-zA-Z0-9][a-zA-Z0-9-_]*$";

    @Test
    public void testHostNameSystemName() {
        String systemName1 = hostNameSystemName();
        String systemName2 = hostNameSystemName();

        assertThat(systemName1, matchesPattern(VALID_SYSTEM_NAME));
        assertThat(systemName2, matchesPattern(VALID_SYSTEM_NAME));

        assertEquals(systemName1, systemName2);
    }

    // Generated names should be random
    @Test
    public void testRandomSystemName() {
        String systemName1 = randomSystemName();
        String systemName2 = randomSystemName();

        assertThat(systemName1, matchesPattern(VALID_SYSTEM_NAME));
        assertThat(systemName2, matchesPattern(VALID_SYSTEM_NAME));

        assertNotEquals(systemName1, systemName2);
    }

    // Generated names should be unique
    @Test
    public void testUniqueSystemName() {
        String systemName1 = uniqueSystemName();
        String systemName2 = uniqueSystemName();

        assertThat(systemName1, matchesPattern(VALID_SYSTEM_NAME));
        assertThat(systemName2, matchesPattern(VALID_SYSTEM_NAME));

        assertEquals(systemName1, systemName2);
    }
}