/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.pipeline.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.drasyl.DrasylConfig;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.util.JSONUtil;
import org.mockito.Answers;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.ArrayList;
import java.util.Random;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@State(Scope.Benchmark)
//@Fork(value = 1)
//@Warmup(iterations = 3)
//@Measurement(iterations = 3)
public class DefaultCodecBenchmark {
    private final HandlerContext ctx;
    private final String msg;
    private ObjectHolder msgEncoded;

    public DefaultCodecBenchmark() {
        ctx = mock(HandlerContext.class, Answers.RETURNS_DEEP_STUBS);
        when(ctx.validator()).thenReturn(TypeValidator.of(DrasylConfig.newBuilder().build()));
        byte[] bytes = new byte[1024];
        new Random().nextBytes(bytes);
        msg = new String(bytes);
        try {
            msgEncoded = ObjectHolder.of(msg.getClass(), JSONUtil.JACKSON_WRITER.writeValueAsBytes(msg));
        }
        catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void encode() {
        DefaultCodec.INSTANCE.encode(ctx, msg, new ArrayList<>());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void decode() {
        DefaultCodec.INSTANCE.decode(ctx, msgEncoded, new ArrayList<>());
    }
}
