package city.sane.akka.p2p;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PairTest {
    @Test
    public void first() {
        Pair pair = new Pair<>(10, "beers");

        assertEquals(10, pair.first());
    }

    @Test
    public void second() {
        Pair pair = new Pair<>(10, "beers");

        assertEquals("beers", pair.second());
    }
}