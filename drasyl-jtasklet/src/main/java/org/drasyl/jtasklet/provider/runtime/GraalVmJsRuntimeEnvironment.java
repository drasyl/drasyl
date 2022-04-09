/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.jtasklet.provider.runtime;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.List;

/**
 * A GraalVM-based runtime environment for JavaScript-based Tasklets.
 */
public class GraalVmJsRuntimeEnvironment extends AbstractRuntimeEnvironment {
    private static final String LANGUAGE = "js";
    private final Context.Builder contextBuilder;

    public GraalVmJsRuntimeEnvironment() {
        contextBuilder = Context.newBuilder(LANGUAGE)
                .allowExperimentalOptions(true)
                // Fix from https://github.com/oracle/graaljs/blob/e48d107db5a6b1b1e1a75aa7392bca351c45b6a4/graal-js/src/com.oracle.truffle.js.scriptengine.test/src/com/oracle/truffle/js/scriptengine/test/ScriptEngineArrayTypeMappingTest.java#L82
                .allowHostAccess(HostAccess.newBuilder(HostAccess.ALL).targetTypeMapping(List.class, Object.class, null, v -> v)
//                .option("sandbox.MaxCPUTime", "500ms")
//                .option("sandbox.MaxCPUTimeCheckInterval", "5ms")
//                .option("sandbox.MaxHeapMemory", memory + "KB")
                        .build());
    }

    @Override
    public Object[] execute(final CharSequence source, final Object... input) {
        try (final Context context = contextBuilder.build()) {
            final Value function = context.eval(LANGUAGE, source);
            final Value output = function.execute(input);
            return output.as(Object[].class);
        }
    }
}
