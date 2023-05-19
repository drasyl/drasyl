/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.util.internal.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to visualize the {@link io.netty.channel.ChannelHandler} order of a given
 * {@link ChannelPipeline}.
 */
public class VisualPipeline {
    public static void print(final ChannelPipeline pipeline) {
        try {
            // make private fields accessible through reflection
            final Field headField = DefaultChannelPipeline.class.getDeclaredField("head");
            headField.setAccessible(true);
            Class<?> clazz = Class.forName("io.netty.channel.AbstractChannelHandlerContext");
            final Field nextField = clazz.getDeclaredField("next");
            nextField.setAccessible(true);

            // collect all pipeline members
            int maxLength = 0;
            final List<String> labels = new ArrayList<>();
            ChannelHandlerContext ctx = (ChannelHandlerContext) headField.get(pipeline);
            while (ctx != null) {
                final String label = ctx.name() + " (" + StringUtil.simpleClassName(ctx.handler()) + ")";
                if (label.length() > maxLength) {
                    maxLength = label.length();
                }
                labels.add(label);

                // next member
                ctx = (ChannelHandlerContext) nextField.get(ctx);
            }

            // print pipeline members
            for (int i = 0; i < labels.size(); i++) {
                // frst member?
                if (i != 0) {
                    printCenter("↓", maxLength);
                }

                String label = labels.get(i);
                printCenter(label, maxLength);
            }
        }
        catch (final ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void printCenter(final String s, final int width) {
        int padding = (width - s.length()) / 2;
        if (padding > 0) {
            System.out.printf("%" + padding + "s%s%" + padding + "s%n", "", s, "");
        }
        else {
            System.out.printf("%s%n", s);
        }
    }
}
