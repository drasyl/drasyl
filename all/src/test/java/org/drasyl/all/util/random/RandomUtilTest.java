/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.util.random;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.UUID;

@Disabled(value = "Test exists only as an argument for using a real random string generator instead of UUID.")
@SuppressWarnings({"squid:S2699"})
class RandomUtilTest {
    private final static int ROUNDS = 1000000;

    @Test
    public void testPerformance() {
        // Warm-Up
        for (int i = 0; i < ROUNDS / 2; i++) {
            RandomUtil.randomNumber(16);
            UUID.randomUUID();
        }

        /**
         * UUID has an entropy of 122bit ~ 15 bytes
         */
        long start = System.currentTimeMillis();
        for (int i = 0; i < ROUNDS; i++) {
            UUID.randomUUID().toString();
        }
        long end = System.currentTimeMillis();
        System.out.println(MessageFormat.format("{0} needed {1}ms to generate {2} string with an entropy of {3} bit", "UUID",
                (end - start), ROUNDS, 122));

        /**
         * In this test RandomUtil has an entropy of 128bit = 16 bytes
         */
        start = System.currentTimeMillis();
        for (int i = 0; i < ROUNDS; i++) {
            RandomUtil.randomString(16);
        }
        end = System.currentTimeMillis();
        System.out.println(MessageFormat.format("{0} needed {1}ms to generate {2} string with an entropy of {3} bit", "RandomUtil",
                (end - start), ROUNDS, 128));
    }
}