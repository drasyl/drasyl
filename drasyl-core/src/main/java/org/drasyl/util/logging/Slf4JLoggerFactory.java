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
package org.drasyl.util.logging;

import org.slf4j.helpers.NOPLoggerFactory;

/**
 * Logger factory which creates a <a href="https://www.slf4j.org/">SLF4J</a> logger.
 *
 * @see Slf4JLogger
 */
public final class Slf4JLoggerFactory extends LoggerFactory {
    public Slf4JLoggerFactory() {
        if (org.slf4j.LoggerFactory.getILoggerFactory() instanceof NOPLoggerFactory) {
            throw new NoClassDefFoundError("NOPLoggerFactory not supported");
        }
    }

    @Override
    protected Logger newLogger(final String name) {
        return new Slf4JLogger(org.slf4j.LoggerFactory.getLogger(name));
    }
}
