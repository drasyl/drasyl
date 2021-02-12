package org.drasyl.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ByteBufUtilTest {
    @Nested
    class Prepend {
        @Test
        void shouldAppendGivenBuffers() {
            final ByteBuf byteBufA = Unpooled.copiedBuffer(new byte[]{ 1, 2, 3 });
            final ByteBuf byteBufB = Unpooled.copiedBuffer(new byte[]{ 4, 5, 6 });
            final ByteBuf byteBufC = Unpooled.copiedBuffer(new byte[]{ 7, 8, 9 });

            final CompositeByteBuf result = ByteBufUtil.prepend(byteBufC, byteBufA, byteBufB);

            assertArrayEquals(new byte[]{
                    1,
                    2,
                    3,
                    4,
                    5,
                    6,
                    7,
                    8,
                    9
            }, io.netty.buffer.ByteBufUtil.getBytes(result));

            byteBufA.release();
            byteBufB.release();
            byteBufC.release();
        }
    }
}
