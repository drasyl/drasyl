package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class ThrowingBiConsumerTest {
    @Nested
    class Accept {
        @Test
        void shouldConsume() throws IOException {
            final ThrowingBiConsumer<LongAdder, Long, IOException> consumer = LongAdder::add;

            final LongAdder i = new LongAdder();
            consumer.accept(i, 1337L);

            assertEquals(1337, i.sum());
        }

        @Test
        void shouldThrowCheckedException(@Mock final IOException e) {
            final ThrowingBiConsumer<Integer, Integer, IOException> consumer = (i, j) -> {
                throw e;
            };

            assertThrows(IOException.class, () -> consumer.accept(0, 0));
        }
    }
}
