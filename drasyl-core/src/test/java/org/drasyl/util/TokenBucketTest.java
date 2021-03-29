/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.util;

import org.drasyl.util.TokenBucket.TokenProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.function.Supplier;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBucketTest {
    @Nested
    class Constructor {
        @Test
        void shouldAcceptValidArguments() {
            assertThat(new TokenBucket(1, ofMillis(100), true), instanceOf(TokenBucket.class));
        }

        @Test
        void shouldRejectIllegalArguments(@Mock final TokenProvider tokenProvider) {
            final Duration oneSecond = ofSeconds(1);
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, oneSecond, false));
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, ZERO, false));
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, tokenProvider, () -> {
            }, 10));
        }
    }

    @Nested
    class Consume {
        @Test
        void shouldConsumeAvailableToken(@Mock final TokenProvider tokenProvider,
                                         @Mock final Runnable sleepStrategy) {
            final TokenBucket bucket = new TokenBucket(10, tokenProvider, sleepStrategy, 10);

            bucket.consume();

            assertEquals(9, bucket.availableTokens());
        }

        @Test
        void shouldGiveTokenProviderChanceToFillBucket(@Mock final TokenProvider tokenProvider,
                                                       @Mock final Runnable sleepStrategy) {
            when(tokenProvider.provide()).thenReturn(1L).thenReturn(0L);

            final TokenBucket bucket = new TokenBucket(10, tokenProvider, sleepStrategy, 5);

            bucket.consume();

            verify(tokenProvider).provide();
            assertEquals(5, bucket.availableTokens());
        }

        @Test
        void shouldApplyGivenSleepStrategy(@Mock final TokenProvider tokenProvider,
                                           @Mock final Runnable sleepStrategy) {
            when(tokenProvider.provide()).thenReturn(0L).thenReturn(1L);

            final TokenBucket bucket = new TokenBucket(1, tokenProvider, sleepStrategy, 0);

            bucket.consume();

            verify(sleepStrategy).run();
        }
    }

    @Nested
    class AvailableTokens {
        @Test
        void shouldReturnNumberOfAvailableTokens(@Mock final TokenProvider tokenProvider,
                                                 @Mock final Runnable sleepStrategy) {
            final TokenBucket bucket = new TokenBucket(10, tokenProvider, sleepStrategy, 10);

            assertEquals(10, bucket.availableTokens());
        }

        @Test
        void shouldGiveTokenProviderChanceToFillBucket(@Mock final TokenProvider tokenProvider,
                                                       @Mock final Runnable sleepStrategy) {
            when(tokenProvider.provide()).thenReturn(5L);

            final TokenBucket bucket = new TokenBucket(10, tokenProvider, sleepStrategy, 0);

            assertEquals(5, bucket.availableTokens());
        }
    }

    @Nested
    class TestTokenProvider {
        @Nested
        class Refill {
            @Mock
            private Supplier<Long> elapsedTimeProvider;

            @Test
            void shouldRefillProvideToken() {
                when(elapsedTimeProvider.get())
                        .thenReturn(50_000_000L)
                        .thenReturn(60_000_000L)
                        .thenReturn(150_000_000L)
                        .thenReturn(160_000_000L);

                final TokenProvider provider = new TokenProvider(100_000_000, elapsedTimeProvider);

                assertEquals(1, provider.provide());
                assertEquals(0, provider.provide());
                assertEquals(1, provider.provide());
                assertEquals(0, provider.provide());
            }
        }
    }
}
